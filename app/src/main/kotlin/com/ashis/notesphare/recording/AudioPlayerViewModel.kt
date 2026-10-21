package com.ashis.notesphare.recording

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.repository.AttachmentRepository
import com.ashis.notesphare.domain.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AudioPlayerUiState(
    val isLoading: Boolean = true,
    val attachment: AttachmentEntity? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
    val error: String? = null,
    val isMissing: Boolean = false
)

class AudioPlayerViewModel(
    private val attachmentId: Long,
    private val attachmentRepository: AttachmentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AudioPlayerUiState())
    val state: StateFlow<AudioPlayerUiState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val attachment = attachmentRepository.getAttachment(attachmentId)
            if (attachment == null || attachment.kind != AttachmentKind.AUDIO.name) {
                _state.update {
                    it.copy(isLoading = false, error = "This recording is unavailable.")
                }
                return@launch
            }
            val file = attachmentRepository.fileFor(attachment)
            if (file == null || !file.isFile) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        attachment = attachment,
                        isMissing = true,
                        error = "The recording file is missing."
                    )
                }
                return@launch
            }
            runCatching { withContext(Dispatchers.IO) { createPlayer(file) } }
                .onSuccess { created ->
                    player = created
                    _state.update {
                        it.copy(
                            isLoading = false,
                            attachment = attachment,
                            durationMs = created.duration.toLong().coerceAtLeast(
                                attachment.durationMs ?: 0L
                            )
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            attachment = attachment,
                            error = throwable.message ?: "This recording could not be opened."
                        )
                    }
                }
        }
    }

    private fun createPlayer(file: File): MediaPlayer {
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        player.setDataSource(file.absolutePath)
        player.prepare()
        player.setOnCompletionListener {
            _state.update { current ->
                current.copy(
                    isPlaying = false,
                    positionMs = current.durationMs
                )
            }
            stopProgressUpdates()
        }
        player.setOnErrorListener { _, what, extra ->
            _state.update {
                it.copy(
                    isPlaying = false,
                    error = "Playback failed ($what/$extra)."
                )
            }
            stopProgressUpdates()
            true
        }
        return player
    }

    fun togglePlayPause() {
        val player = player ?: return
        runCatching {
            if (player.isPlaying) {
                player.pause()
                _state.update { it.copy(isPlaying = false) }
                stopProgressUpdates()
            } else {
                if (player.currentPosition >= player.duration) {
                    player.seekTo(0)
                }
                player.start()
                _state.update { it.copy(isPlaying = true, error = null) }
                startProgressUpdates()
            }
        }.onFailure { throwable ->
            _state.update {
                it.copy(isPlaying = false, error = throwable.message ?: "Playback failed.")
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val player = player ?: return
        val bounded = positionMs.coerceIn(0L, _state.value.durationMs)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                player.seekTo(bounded, MediaPlayer.SEEK_CLOSEST)
            } else {
                player.seekTo(bounded.toInt())
            }
            _state.update { it.copy(positionMs = bounded) }
        }.onFailure { throwable ->
            _state.update { it.copy(error = throwable.message ?: "Could not seek.") }
        }
    }

    fun setSpeed(speed: Float) {
        val target = speed.coerceIn(0.5f, 2f)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            _state.update { it.copy(error = "Playback speed requires Android 6.0 or newer.") }
            return
        }
        val player = player ?: return
        runCatching {
            player.playbackParams = player.playbackParams.setSpeed(target)
            _state.update { it.copy(speed = target, error = null) }
        }.onFailure { throwable ->
            _state.update {
                it.copy(error = throwable.message ?: "This recording does not support speed changes.")
            }
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val current = player ?: break
                val playing = runCatching { current.isPlaying }.getOrDefault(false)
                val position = runCatching { current.currentPosition.toLong() }.getOrDefault(0L)
                _state.update { it.copy(positionMs = position, isPlaying = playing) }
                if (!playing) break
                delay(200L)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun onCleared() {
        stopProgressUpdates()
        runCatching { player?.release() }
        player = null
        super.onCleared()
    }
}
