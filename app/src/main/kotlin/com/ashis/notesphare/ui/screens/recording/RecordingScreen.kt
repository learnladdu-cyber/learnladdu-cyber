package com.ashis.notesphare.ui.screens.recording

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ashis.notesphare.recording.RecorderPhase
import com.ashis.notesphare.recording.RecorderUiState
import com.ashis.notesphare.recording.RecordingService
import com.ashis.notesphare.ui.components.ConfirmDialog
import com.ashis.notesphare.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    noteId: Long,
    onBack: () -> Unit,
    onRecordingSaved: () -> Unit
) {
    val context = LocalContext.current
    var service by remember { mutableStateOf<RecordingService?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? RecordingService.LocalBinder)?.service()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        val bound = runCatching {
            context.bindService(
                Intent(context, RecordingService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
        }.getOrDefault(false)
        onDispose {
            if (bound) runCatching { context.unbindService(connection) }
            service = null
        }
    }

    val recorderState by produceState(
        initialValue = RecorderUiState(),
        service
    ) {
        val current = service
        if (current == null) {
            value = RecorderUiState()
        } else {
            current.state.collect { value = it }
        }
    }

    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted) {
            RecordingService.start(context, noteId)
        }
    }

    val startRecording = {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            RecordingService.start(context, noteId)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(recorderState.phase, recorderState.savedAttachmentId) {
        if (recorderState.phase == RecorderPhase.SAVED) {
            onRecordingSaved()
        }
    }

    val isActive = recorderState.phase in setOf(
        RecorderPhase.PREPARING,
        RecorderPhase.RECORDING,
        RecorderPhase.PAUSED,
        RecorderPhase.STOPPED,
        RecorderPhase.SAVING
    )
    BackHandler {
        if (isActive) {
            showDiscardConfirm = true
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice recording") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isActive) showDiscardConfirm = true else onBack()
                        },
                        modifier = Modifier.testTag("recordingBack")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .background(
                                color = if (recorderState.phase == RecorderPhase.RECORDING) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                                shape = RoundedCornerShape(50)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                            tint = if (recorderState.phase == RecorderPhase.RECORDING) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = formatDuration(recorderState.elapsedMs),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("recordingDuration")
                    )
                    Text(
                        text = phaseLabel(recorderState),
                        color = if (recorderState.phase == RecorderPhase.RECORDING) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(16.dp))
                    Waveform(
                        amplitudes = recorderState.amplitudes,
                        active = recorderState.phase == RecorderPhase.RECORDING
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            recorderState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            when {
                permissionDenied && recorderState.phase == RecorderPhase.IDLE -> {
                    Text(
                        text = "Microphone permission was denied. NoteSphere cannot record without it.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    }) { Text("Open app settings") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = startRecording) {
                        Text("Ask again")
                    }
                }

                recorderState.phase == RecorderPhase.IDLE ||
                    recorderState.phase == RecorderPhase.ERROR -> {
                    Button(
                        onClick = startRecording,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (recorderState.phase == RecorderPhase.ERROR) {
                                "Start a new recording"
                            } else {
                                "Start recording"
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Recording stays active in the foreground when you switch apps. An ongoing notification is required by Android.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (recorderState.sessionId != null) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { service?.cancel() }) {
                            Text("Discard unfinished session")
                        }
                    }
                }

                recorderState.phase == RecorderPhase.PREPARING -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Preparing microphone\u2026")
                }

                recorderState.phase == RecorderPhase.RECORDING ||
                    recorderState.phase == RecorderPhase.PAUSED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (recorderState.phase == RecorderPhase.RECORDING) {
                            OutlinedButton(
                                onClick = {
                                    if (recorderState.pauseSupported) {
                                        service?.pause()
                                    }
                                },
                                enabled = recorderState.pauseSupported,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Pause, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Pause")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { service?.resume() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text("Resume")
                            }
                        }
                        Button(
                            onClick = { service?.stop() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Stop")
                        }
                    }
                    if (!recorderState.pauseSupported) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Pause and resume require Android 7.0 or newer; recording and stopping still work.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                recorderState.phase == RecorderPhase.STOPPED -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { service?.save() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = { showDiscardConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Cancel")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Save attaches this recording to the note and makes it available for playback and transcription.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                recorderState.phase == RecorderPhase.SAVING -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Saving recording\u2026")
                }

                recorderState.phase == RecorderPhase.SAVED -> {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Recording saved")
                }
            }
        }
    }

    if (showDiscardConfirm) {
        ConfirmDialog(
            title = "Discard recording?",
            message = "The audio recorded in this session will be deleted. This cannot be undone.",
            confirmLabel = "Discard",
            destructive = true,
            onConfirm = {
                showDiscardConfirm = false
                if (recorderState.phase == RecorderPhase.STOPPED) {
                    service?.cancel()
                } else if (recorderState.phase == RecorderPhase.IDLE ||
                    recorderState.phase == RecorderPhase.ERROR
                ) {
                    onBack()
                } else {
                    service?.cancel()
                }
            },
            onDismiss = { showDiscardConfirm = false }
        )
    }
}

@Composable
private fun Waveform(amplitudes: List<Float>, active: Boolean) {
    val color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        val points = amplitudes.ifEmpty { List(48) { 0.04f } }
        val step = size.width / points.size.coerceAtLeast(1)
        points.forEachIndexed { index, value ->
            val barHeight = (value.coerceIn(0f, 1f) * size.height).coerceAtLeast(3f)
            val x = step * index + step / 2f
            drawLine(
                color = color,
                start = Offset(x, size.height / 2f - barHeight / 2f),
                end = Offset(x, size.height / 2f + barHeight / 2f),
                strokeWidth = (step * 0.55f).coerceAtLeast(2f),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun phaseLabel(state: RecorderUiState): String = when (state.phase) {
    RecorderPhase.IDLE -> "Ready to record"
    RecorderPhase.PREPARING -> "Preparing"
    RecorderPhase.RECORDING -> "Recording"
    RecorderPhase.PAUSED -> "Paused"
    RecorderPhase.STOPPED -> "Stopped \u2014 save or cancel"
    RecorderPhase.SAVING -> "Saving"
    RecorderPhase.SAVED -> "Saved"
    RecorderPhase.ERROR -> "Recording error"
}
