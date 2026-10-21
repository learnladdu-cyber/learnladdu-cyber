package com.ashis.notesphare.ui.screens.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.local.RecordingSessionEntity
import com.ashis.notesphare.data.repository.AttachmentRepository
import com.ashis.notesphare.data.repository.CleanupReport
import com.ashis.notesphare.data.repository.RecordingRepository
import com.ashis.notesphare.data.repository.StorageStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageManagerUiState(
    val isLoading: Boolean = true,
    val attachments: List<AttachmentEntity> = emptyList(),
    val sessions: List<RecordingSessionEntity> = emptyList(),
    val stats: StorageStats = StorageStats(),
    val message: String? = null,
    val error: String? = null,
    val isCleaning: Boolean = false,
    val deletingId: Long? = null
)

class StorageManagerViewModel(
    private val attachmentRepository: AttachmentRepository,
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StorageManagerUiState())
    val state: StateFlow<StorageManagerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            attachmentRepository.observeAllAttachments().collect { attachments ->
                _state.update { it.copy(attachments = attachments, isLoading = false) }
                refreshStats()
            }
        }
        viewModelScope.launch {
            recordingRepository.observeSessions().collect { sessions ->
                _state.update { it.copy(sessions = sessions) }
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            runCatching { attachmentRepository.storageStats() }
                .onSuccess { stats -> _state.update { it.copy(stats = stats) } }
                .onFailure { throwable ->
                    _state.update { it.copy(error = throwable.message ?: "Could not read storage") }
                }
        }
    }

    fun deleteAttachment(attachmentId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(deletingId = attachmentId) }
            runCatching { attachmentRepository.deleteAttachment(attachmentId) }
                .onSuccess {
                    _state.update {
                        it.copy(deletingId = null, message = "Attachment removed")
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            deletingId = null,
                            error = throwable.message ?: "Could not remove attachment"
                        )
                    }
                }
        }
    }

    fun cleanupOrphans() {
        if (_state.value.isCleaning) return
        viewModelScope.launch {
            _state.update { it.copy(isCleaning = true, error = null) }
            runCatching { attachmentRepository.cleanupOrphans() }
                .onSuccess { report ->
                    _state.update {
                        it.copy(
                            isCleaning = false,
                            message = cleanupMessage(report)
                        )
                    }
                    refreshStats()
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isCleaning = false,
                            error = throwable.message ?: "Cleanup failed"
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun cleanupMessage(report: CleanupReport): String = when {
        report.deletedFiles == 0 -> "No orphaned files needed cleanup"
        report.failures > 0 -> "Removed ${report.deletedFiles} files; ${report.failures} could not be removed"
        else -> "Removed ${report.deletedFiles} orphaned files"
    }
}
