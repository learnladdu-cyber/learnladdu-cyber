package com.ashis.notesphare.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashis.notesphare.data.local.NoteEntity
import com.ashis.notesphare.data.repository.NoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val pinnedNotes: List<NoteEntity> = emptyList(),
    val recentNotes: List<NoteEntity> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class HomeViewModel(private val noteRepository: NoteRepository) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        noteRepository.observePinnedNotes(),
        noteRepository.observeRecentNotes()
    ) { pinned, recent ->
        HomeUiState(
            pinnedNotes = pinned,
            recentNotes = recent,
            isLoading = false
        )
    }
        .catch { throwable ->
            emit(HomeUiState(isLoading = false, errorMessage = throwable.message))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState()
        )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            runCatching { noteRepository.setPinned(note.id, !note.isPinned) }
                .onFailure { _message.value = it.message ?: "Could not update note" }
        }
    }

    fun archive(note: NoteEntity) {
        viewModelScope.launch {
            runCatching { noteRepository.setArchived(note.id, true) }
                .onSuccess { _message.value = "Note archived" }
                .onFailure { _message.value = it.message ?: "Could not archive note" }
        }
    }

    fun trash(note: NoteEntity) {
        viewModelScope.launch {
            runCatching { noteRepository.trashNote(note.id) }
                .onFailure { _message.value = it.message ?: "Could not move note to trash" }
        }
    }

    fun restore(note: NoteEntity) {
        viewModelScope.launch {
            runCatching { noteRepository.restoreNote(note.id) }
                .onSuccess { _message.value = "Note restored" }
                .onFailure { _message.value = it.message ?: "Could not restore note" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
