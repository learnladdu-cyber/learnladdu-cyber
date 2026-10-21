package com.ashis.notesphare.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ashis.notesphare.MainActivity
import com.ashis.notesphare.NoteSphereApp
import com.ashis.notesphare.R
import com.ashis.notesphare.data.repository.RecordingRepository
import com.ashis.notesphare.domain.RecordingSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RecordingService : Service() {

    inner class LocalBinder : Binder() {
        fun service(): RecordingService = this@RecordingService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val machine = RecordingStateMachine()
    private val _state = MutableStateFlow(RecorderUiState())
    val state: StateFlow<RecorderUiState> = _state.asStateFlow()

    private lateinit var repository: RecordingRepository
    private var recorder: MediaRecorder? = null
    private var sessionFile: File? = null
    private var sessionStartedAt: Long = 0L
    private var elapsedBeforeResume: Long = 0L
    private var resumedAt: Long = 0L
    private var lastWaveform: List<Float> = emptyList()
    private var timerJob: Job? = null
    private var amplitudeJob: Job? = null
    private var persistJob: Job? = null
    private var foregroundStarted = false
    private var noisyReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        repository = (application as NoteSphereApp).container.recordingRepository
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
                ensureForeground(buildNotification(RecorderPhase.PREPARING))
                scope.launch { begin(noteId) }
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop()
            ACTION_SAVE -> save()
            ACTION_CANCEL -> cancel()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Recording intentionally continues in the foreground service when the task is removed.
    }

    override fun onDestroy() {
        timerJob?.cancel()
        amplitudeJob?.cancel()
        persistJob?.cancel()
        unregisterNoisyReceiver()
        if (_state.value.phase == RecorderPhase.RECORDING ||
            _state.value.phase == RecorderPhase.PAUSED
        ) {
            scope.launch {
                runCatching {
                    val duration = currentElapsed()
                    repository.updateSession(
                        sessionId = _state.value.sessionId ?: return@runCatching,
                        durationMs = duration,
                        state = RecordingSessionState.PAUSED,
                        waveform = lastWaveform.toCsv()
                    )
                }
            }
        }
        runCatching {
            recorder?.stop()
        }
        runCatching { recorder?.release() }
        recorder = null
        scope.cancel()
        super.onDestroy()
    }

    fun pause() {
        val active = recorder ?: return
        if (machine.phase != RecorderPhase.RECORDING) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            _state.update { it.copy(error = "Pause requires Android 7.0 or newer.") }
            return
        }
        val elapsed = currentElapsed()
        runCatching { active.pause() }
            .onSuccess {
                elapsedBeforeResume = elapsed
                machine.transition(RecorderEvent.PAUSE)
                timerJob?.cancel()
                amplitudeJob?.cancel()
                _state.update {
                    it.copy(phase = RecorderPhase.PAUSED, elapsedMs = elapsed, error = null)
                }
                persistSession(RecordingSessionState.PAUSED, elapsed)
                updateNotification()
            }
            .onFailure { fail(it.message ?: "Could not pause recording") }
    }

    fun resume() {
        val active = recorder ?: return
        if (machine.phase != RecorderPhase.PAUSED) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            _state.update { it.copy(error = "Resume requires Android 7.0 or newer.") }
            return
        }
        runCatching { active.resume() }
            .onSuccess {
                machine.transition(RecorderEvent.RESUME)
                resumedAt = System.currentTimeMillis()
                _state.update { it.copy(phase = RecorderPhase.RECORDING, error = null) }
                startTimers()
                persistSession(RecordingSessionState.RECORDING, elapsedBeforeResume)
                updateNotification()
            }
            .onFailure { fail(it.message ?: "Could not resume recording") }
    }

    fun stop() {
        if (machine.phase != RecorderPhase.RECORDING && machine.phase != RecorderPhase.PAUSED) return
        val duration = currentElapsed()
        timerJob?.cancel()
        amplitudeJob?.cancel()
        persistJob?.cancel()
        unregisterNoisyReceiver()
        var stopFailure: Throwable? = null
        runCatching { recorder?.stop() }.onFailure { stopFailure = it }
        runCatching { recorder?.release() }
        recorder = null
        machine.transition(RecorderEvent.STOP)
        _state.update {
            it.copy(
                phase = RecorderPhase.STOPPED,
                elapsedMs = duration,
                amplitudes = lastWaveform,
                error = stopFailure?.let { failure ->
                    "Recording stopped, but the audio may be incomplete. Try Save, or cancel and record again. ${failure.message.orEmpty()}".trim()
                }
            )
        }
        persistSession(RecordingSessionState.STOPPED, duration)
        updateNotification()
        stopForegroundCompat(false)
        if (stopFailure != null) {
            // Save still validates the container. The message lets the user retry.
        }
    }

    fun save() {
        if (machine.phase != RecorderPhase.STOPPED) return
        val sessionId = _state.value.sessionId ?: return
        machine.transition(RecorderEvent.SAVE)
        _state.update { it.copy(phase = RecorderPhase.SAVING, error = null) }
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { repository.saveSession(sessionId) } }
                .onSuccess { attachmentId ->
                    machine.markSaved()
                    _state.update {
                        it.copy(
                            phase = RecorderPhase.SAVED,
                            savedAttachmentId = attachmentId,
                            error = null
                        )
                    }
                    NotificationManagerCompat.from(this@RecordingService).cancel(NOTIFICATION_ID)
                    stopForegroundCompat(true)
                    stopSelf()
                }
                .onFailure { throwable ->
                    machine.transition(RecorderEvent.FAIL)
                    _state.update {
                        it.copy(
                            phase = RecorderPhase.ERROR,
                            error = throwable.message ?: "Could not save the recording"
                        )
                    }
                    updateNotification()
                }
        }
    }

    fun cancel() {
        if (machine.phase == RecorderPhase.SAVED || machine.phase == RecorderPhase.SAVING) return
        val sessionId = _state.value.sessionId
        timerJob?.cancel()
        amplitudeJob?.cancel()
        persistJob?.cancel()
        unregisterNoisyReceiver()
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        _state.update { it.copy(phase = RecorderPhase.IDLE, elapsedMs = 0L, amplitudes = emptyList()) }
        scope.launch {
            if (sessionId != null) {
                withContext(Dispatchers.IO) { runCatching { repository.discardSession(sessionId) } }
            }
            NotificationManagerCompat.from(this@RecordingService).cancel(NOTIFICATION_ID)
            stopForegroundCompat(true)
            stopSelf()
        }
    }

    private suspend fun begin(noteId: Long) {
        if (noteId <= 0L) {
            fail("This note no longer exists.")
            return
        }
        if (!hasMicrophonePermission(this)) {
            fail("Microphone permission is required to record audio.")
            return
        }
        if (!machine.canHandle(RecorderEvent.PREPARE)) {
            fail("Another recording is already in progress.")
            return
        }
        machine.transition(RecorderEvent.PREPARE)
        _state.update {
            it.copy(
                phase = RecorderPhase.PREPARING,
                noteId = noteId,
                elapsedMs = 0L,
                amplitudes = emptyList(),
                error = null
            )
        }
        val session = runCatching { withContext(Dispatchers.IO) { repository.createSession(noteId) } }
            .getOrElse { throwable ->
                fail(throwable.message ?: "Could not start recording.")
                return
            }
        val file = repository.fileFor(session)
        if (file == null) {
            withContext(Dispatchers.IO) { runCatching { repository.discardSession(session.id) } }
            fail("Could not create a safe recording file.")
            return
        }
        sessionFile = file
        sessionStartedAt = session.startedAt
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        runCatching {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioSamplingRate(44_100)
            newRecorder.setAudioEncodingBitRate(128_000)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.setOnErrorListener { _, what, extra ->
                fail("The microphone reported an error ($what/$extra).")
            }
            newRecorder.prepare()
            newRecorder.start()
        }.onFailure { throwable ->
            runCatching { newRecorder.release() }
            withContext(Dispatchers.IO) { runCatching { repository.discardSession(session.id) } }
            fail(throwable.message ?: "Could not start the microphone.")
            return
        }
        recorder = newRecorder
        machine.transition(RecorderEvent.START)
        resumedAt = System.currentTimeMillis()
        _state.update {
            it.copy(
                phase = RecorderPhase.RECORDING,
                sessionId = session.id,
                elapsedMs = 0L,
                amplitudes = emptyList(),
                error = null
            )
        }
        registerNoisyReceiver()
        startTimers()
        updateNotification()
        persistSession(RecordingSessionState.RECORDING, 0L)
    }

    private fun startTimers() {
        timerJob?.cancel()
        amplitudeJob?.cancel()
        persistJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(100L)
                if (machine.phase == RecorderPhase.RECORDING) {
                    _state.update { it.copy(elapsedMs = currentElapsed()) }
                }
            }
        }
        amplitudeJob = scope.launch {
            while (isActive) {
                delay(100L)
                if (machine.phase == RecorderPhase.RECORDING) {
                    val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    val normalised = (amplitude / 32_767f).coerceIn(0f, 1f)
                    val next = (lastWaveform + normalised).takeLast(MAX_WAVEFORM_POINTS)
                    lastWaveform = next
                    _state.update { it.copy(amplitudes = next) }
                }
            }
        }
        persistJob = scope.launch {
            while (isActive) {
                delay(2_000L)
                if (machine.phase == RecorderPhase.RECORDING) {
                    persistSession(RecordingSessionState.RECORDING, currentElapsed())
                }
            }
        }
    }

    private fun persistSession(state: RecordingSessionState, duration: Long) {
        val sessionId = _state.value.sessionId ?: return
        val waveform = lastWaveform.toCsv()
        scope.launch(Dispatchers.IO) {
            runCatching {
                repository.updateSession(sessionId, duration, state, waveform)
            }
        }
    }

    private fun currentElapsed(): Long = when (machine.phase) {
        RecorderPhase.RECORDING -> elapsedBeforeResume + (System.currentTimeMillis() - resumedAt)
        RecorderPhase.PAUSED, RecorderPhase.STOPPED -> elapsedBeforeResume
        else -> 0L
    }

    private fun fail(message: String) {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        timerJob?.cancel()
        amplitudeJob?.cancel()
        persistJob?.cancel()
        unregisterNoisyReceiver()
        if (machine.canHandle(RecorderEvent.FAIL)) {
            runCatching { machine.transition(RecorderEvent.FAIL) }
        }
        _state.update { it.copy(phase = RecorderPhase.ERROR, error = message) }
        updateNotification()
        stopForegroundCompat(false)
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    if (machine.phase == RecorderPhase.RECORDING) pause()
                }
            }
        }
        noisyReceiver = receiver
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun unregisterNoisyReceiver() {
        noisyReceiver?.let { receiver -> runCatching { unregisterReceiver(receiver) } }
        noisyReceiver = null
    }

    private fun ensureForeground(notification: Notification) {
        if (foregroundStarted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun updateNotification() {
        if (!foregroundStarted) return
        NotificationManagerCompat.from(this)
            .notify(NOTIFICATION_ID, buildNotification(machine.phase))
    }

    private fun stopForegroundCompat(removeNotification: Boolean) {
        if (!foregroundStarted) {
            if (removeNotification) {
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(removeNotification)
        }
        foregroundStarted = false
    }

    private fun buildNotification(phase: RecorderPhase): Notification {
        val title = when (phase) {
            RecorderPhase.PAUSED -> "Recording paused"
            RecorderPhase.STOPPED -> "Recording ready to save"
            RecorderPhase.SAVING -> "Saving recording\u2026"
            RecorderPhase.ERROR -> "Recording problem"
            else -> "Recording audio"
        }
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NOTE_ID, _state.value.noteId ?: 0L)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("NoteSphere keeps recording while you use another app.")
            .setContentIntent(pendingIntent)
            .setOngoing(phase != RecorderPhase.ERROR)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
        if (phase == RecorderPhase.RECORDING || phase == RecorderPhase.PAUSED) {
            builder.addAction(
                0,
                if (phase == RecorderPhase.RECORDING) "Pause" else "Resume",
                servicePendingIntent(if (phase == RecorderPhase.RECORDING) ACTION_PAUSE else ACTION_RESUME, 1)
            )
            builder.addAction(0, "Stop", servicePendingIntent(ACTION_STOP, 2))
        } else if (phase == RecorderPhase.STOPPED) {
            builder.addAction(0, "Save", servicePendingIntent(ACTION_SAVE, 3))
            builder.addAction(0, "Cancel", servicePendingIntent(ACTION_CANCEL, 4))
        }
        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voice recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while NoteSphere records audio in the background"
            setShowBadge(false)
        }
        manager?.createNotificationChannel(channel)
    }

    private fun immutableFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun List<Float>.toCsv(): String? =
        takeIf { it.isNotEmpty() }?.joinToString(",") { "%.3f".format(java.util.Locale.US, it) }

    companion object {
        const val ACTION_START = "com.ashis.notesphare.recording.START"
        const val ACTION_PAUSE = "com.ashis.notesphare.recording.PAUSE"
        const val ACTION_RESUME = "com.ashis.notesphare.recording.RESUME"
        const val ACTION_STOP = "com.ashis.notesphare.recording.STOP"
        const val ACTION_SAVE = "com.ashis.notesphare.recording.SAVE"
        const val ACTION_CANCEL = "com.ashis.notesphare.recording.CANCEL"
        const val EXTRA_NOTE_ID = "note_id"
        const val CHANNEL_ID = "voice_recording"
        private const val NOTIFICATION_ID = 3_001
        private const val MAX_WAVEFORM_POINTS = 72

        fun start(context: Context, noteId: Long) {
            val intent = Intent(context, RecordingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_NOTE_ID, noteId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
