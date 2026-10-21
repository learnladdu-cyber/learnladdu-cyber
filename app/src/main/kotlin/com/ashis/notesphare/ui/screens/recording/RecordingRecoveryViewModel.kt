package com.ashis.notesphare.ui.screens.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashis.notesphare.data.local.RecordingSessionEntity
import com.ashis.notesphare.data.repository.RecordingRepository
import com.ashis.notesphare.domain.RecordingSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecordingRecoveryUiState(
    val isLoading: Boolean = true,
    val sessions: List<RecordingSessionEntity> = emptyList(),
    val busySessionId: String? = null,
    val message: String? = null,
    val error: String? = null
)

class RecordingRecoveryViewModel(
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RecordingRecoveryUiState())
    val state: StateFlow<RecordingRecoveryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            recordingRepository.observeSessions().collect { sessions ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        sessions = sessions.filter { session ->
                            session.state != RecordingSessionState.SAVED.name
                        }
                    )
                }
            }
        }
    }

    fun recover(sessionId: String) {
        if (_state.value.busySessionId != null) return
        viewModelScope.launch {
            _state.update { it.copy(busySessionId = sessionId, error = null) }
            runCatching { recordingRepository.saveSession(sessionId) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            busySessionId = null,
                            message = "Recording recovered and attached to the note"
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            busySessionId = null,
                            error = throwable.message
                                ?: "This recording is incomplete and cannot be recovered."
                        )
                    }
                }
        }
    }

    fun discard(sessionId: String) {
        if (_state.value.busySessionId != null) return
        viewModelScope.launch {
            _state.update { it.copy(busySessionId = sessionId, error = null) }
            runCatching { recordingRepository.discardSession(sessionId) }
                .onSuccess {
                    _state.update {
                        it.copy(busySessionId = null, message = "Incomplete recording discarded")
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            busySessionId = null,
                            error = throwable.message ?: "Could not discard the recording"
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }
}
