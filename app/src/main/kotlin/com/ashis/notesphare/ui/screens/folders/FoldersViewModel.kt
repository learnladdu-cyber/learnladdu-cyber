package com.ashis.notesphare.ui.screens.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashis.notesphare.data.local.FolderWithCount
import com.ashis.notesphare.data.repository.FolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoldersViewModel(private val folderRepository: FolderRepository) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val folders: StateFlow<List<FolderWithCount>> = folderRepository.observeFoldersWithCounts()
        .catch { throwable ->
            _message.value = throwable.message ?: "Could not load folders"
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun createFolder(name: String, color: Int) {
        viewModelScope.launch {
            runCatching { folderRepository.createFolder(name, color) }
                .onFailure { _message.value = it.message ?: "Could not create folder" }
        }
    }

    fun updateFolder(folderId: Long, name: String, color: Int) {
        viewModelScope.launch {
            runCatching { folderRepository.updateFolder(folderId, name, color) }
                .onFailure { _message.value = it.message ?: "Could not rename folder" }
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            runCatching { folderRepository.deleteFolder(folderId) }
                .onSuccess { _message.value = "Folder deleted. Its notes were kept." }
                .onFailure { _message.value = it.message ?: "Could not delete folder" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
