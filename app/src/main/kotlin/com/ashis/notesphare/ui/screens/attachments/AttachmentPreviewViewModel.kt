package com.ashis.notesphare.ui.screens.attachments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.repository.AttachmentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AttachmentPreviewUiState(
    val isLoading: Boolean = true,
    val attachment: AttachmentEntity? = null,
    val file: File? = null,
    val isMissing: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null
)

class AttachmentPreviewViewModel(
    private val attachmentId: Long,
    private val attachmentRepository: AttachmentRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AttachmentPreviewUiState())
    val state: StateFlow<AttachmentPreviewUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val attachment = attachmentRepository.getAttachment(attachmentId)
            if (attachment == null) {
                _state.update {
                    it.copy(isLoading = false, error = "This attachment no longer exists.")
                }
                return@launch
            }
            val file = attachmentRepository.fileFor(attachment)
            val missing = file == null || !file.isFile
            _state.update {
                it.copy(
                    isLoading = false,
                    attachment = attachment,
                    file = file,
                    isMissing = missing,
                    error = if (missing) "The file is missing from NoteSphere storage." else null
                )
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            runCatching { attachmentRepository.deleteAttachment(attachmentId) }
                .onSuccess {
                    _state.update { it.copy(deleted = true) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(error = throwable.message ?: "Could not remove this attachment.")
                    }
                }
        }
    }

    fun showMessage(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
