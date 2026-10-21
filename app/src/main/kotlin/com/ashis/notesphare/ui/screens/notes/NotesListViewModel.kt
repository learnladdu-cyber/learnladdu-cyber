package com.ashis.notesphare.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ashis.notesphare.data.local.NoteEntity
import com.ashis.notesphare.data.repository.FolderRepository
import com.ashis.notesphare.data.repository.NoteRepository
import com.ashis.notesphare.data.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NoteListMode { FOLDER, TAG, ARCHIVE, TRASH }

class NotesListViewModel(
    private val noteRepository: NoteRepository,
    folderRepository: FolderRepository,
    tagRepository: TagRepository,
    private val mode: NoteListMode,
    private val entityId: Long
) : ViewModel() {

    private val _title = MutableStateFlow(
        when (mode) {
            NoteListMode.FOLDER -> "Folder"
            NoteListMode.TAG -> "Tag"
            NoteListMode.ARCHIVE -> "Archive"
            NoteListMode.TRASH -> "Trash"
        }
    )
    val title: StateFlow<String> = _title.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val notesFlow: Flow<List<NoteEntity>> = when (mode) {
        NoteListMode.FOLDER -> noteRepository.observeNotesInFolder(entityId)
        NoteListMode.TAG -> noteRepository.observeNotesWithTag(entityId)
        NoteListMode.ARCHIVE -> noteRepository.observeArchivedNotes()
        NoteListMode.TRASH -> noteRepository.observeTrashedNotes()
    }

    val notes: StateFlow<List<NoteEntity>> = notesFlow
        .catch { throwable ->
            _message.value = throwable.message ?: "Could not load notes"
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        if (mode == NoteListMode.FOLDER) {
            viewModelScope.launch {
                runCatching { folderRepository.getFolder(entityId) }
                    .onSuccess { folder -> if (folder != null) _title.value = folder.name }
                    .onFailure { _message.value = it.message ?: "Could not load folder" }
            }
        } else if (mode == NoteListMode.TAG) {
            viewModelScope.launch {
                runCatching { tagRepository.getTags().firstOrNull { it.id == entityId } }
                    .onSuccess { tag -> if (tag != null) _title.value = tag.name }
                    .onFailure { _message.value = it.message ?: "Could not load tag" }
            }
        }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            runCatching { noteRepository.setPinned(note.id, !note.isPinned) }
                .onFailure { _message.value = it.message ?: "Could not update note" }
        }
    }

    fun archive(note: NoteEntity) {
        viewModelScope.launch {
            runCatching { noteRepository.setArchived(note.id, true) }
                .onFailure { _message.value = it.message ?: "Could not archive note" }
        }
    }

    fun unarchive(note: NoteEntity) {
        viewModelScope.launch {
            runCatching { noteRepository.setArchived(note.id, false) }
                .onFailure { _message.value = it.message ?: "Could not unarchive note" }
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
                .onFailure { _message.value = it.message ?: "Could not restore note" }
        }
    }

    fun deleteForever(note: NoteEntity) {
        viewModelScope.launch {
            runCatching { noteRepository.deleteNotePermanently(note.id) }
                .onSuccess { _message.value = "Note deleted permanently" }
                .onFailure { _message.value = it.message ?: "Could not delete note" }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            runCatching { noteRepository.emptyTrash() }
                .onSuccess { _message.value = "Trash emptied" }
                .onFailure { _message.value = it.message ?: "Could not empty trash" }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
