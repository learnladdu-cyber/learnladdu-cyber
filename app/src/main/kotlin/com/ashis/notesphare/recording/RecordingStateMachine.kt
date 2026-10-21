package com.ashis.notesphare.recording

enum class RecorderPhase {
    IDLE,
    PREPARING,
    RECORDING,
    PAUSED,
    STOPPED,
    SAVING,
    SAVED,
    ERROR
}

enum class RecorderEvent {
    PREPARE,
    START,
    PAUSE,
    RESUME,
    STOP,
    SAVE,
    CANCEL,
    FAIL
}

/**
 * Pure state machine used by the foreground service. Keeping the transitions separate means
 * process-lifecycle and permission paths can be unit tested without an audio device.
 */
class RecordingStateMachine(initial: RecorderPhase = RecorderPhase.IDLE) {

    var phase: RecorderPhase = initial
        private set

    fun canHandle(event: RecorderEvent): Boolean = when (event) {
        RecorderEvent.PREPARE -> phase == RecorderPhase.IDLE || phase == RecorderPhase.SAVED ||
            phase == RecorderPhase.ERROR
        RecorderEvent.START -> phase == RecorderPhase.PREPARING
        RecorderEvent.PAUSE -> phase == RecorderPhase.RECORDING
        RecorderEvent.RESUME -> phase == RecorderPhase.PAUSED
        RecorderEvent.STOP -> phase == RecorderPhase.RECORDING || phase == RecorderPhase.PAUSED
        RecorderEvent.SAVE -> phase == RecorderPhase.STOPPED
        RecorderEvent.CANCEL -> phase != RecorderPhase.SAVED && phase != RecorderPhase.SAVING
        RecorderEvent.FAIL -> phase != RecorderPhase.SAVED && phase != RecorderPhase.SAVING
    }

    fun transition(event: RecorderEvent): RecorderPhase {
        if (!canHandle(event)) {
            throw IllegalStateException("Cannot handle $event while in $phase")
        }
        phase = when (event) {
            RecorderEvent.PREPARE -> RecorderPhase.PREPARING
            RecorderEvent.START -> RecorderPhase.RECORDING
            RecorderEvent.PAUSE -> RecorderPhase.PAUSED
            RecorderEvent.RESUME -> RecorderPhase.RECORDING
            RecorderEvent.STOP -> RecorderPhase.STOPPED
            RecorderEvent.SAVE -> RecorderPhase.SAVING
            RecorderEvent.CANCEL -> RecorderPhase.IDLE
            RecorderEvent.FAIL -> RecorderPhase.ERROR
        }
        return phase
    }

    fun markSaved() {
        if (phase != RecorderPhase.SAVING) {
            throw IllegalStateException("Cannot save while in $phase")
        }
        phase = RecorderPhase.SAVED
    }

    fun reset() {
        phase = RecorderPhase.IDLE
    }
}

data class RecorderUiState(
    val phase: RecorderPhase = RecorderPhase.IDLE,
    val noteId: Long? = null,
    val sessionId: String? = null,
    val elapsedMs: Long = 0L,
    val amplitudes: List<Float> = emptyList(),
    val error: String? = null,
    val savedAttachmentId: Long? = null,
    val pauseSupported: Boolean = android.os.Build.VERSION.SDK_INT >= 24
)
