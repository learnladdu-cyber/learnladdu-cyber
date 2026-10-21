package com.ashis.notesphare.ui.screens.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.local.ChecklistItemEntity
import com.ashis.notesphare.data.local.FolderEntity
import com.ashis.notesphare.data.local.TagEntity
import com.ashis.notesphare.data.local.TranscriptionEntity
import com.ashis.notesphare.data.repository.AttachmentRepository
import com.ashis.notesphare.data.repository.FolderRepository
import com.ashis.notesphare.data.repository.NoteRepository
import com.ashis.notesphare.data.repository.ReminderScheduleResult
import com.ashis.notesphare.data.repository.TagRepository
import com.ashis.notesphare.data.repository.TranscriptionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ChecklistUiItem(
    val id: Long,
    val text: String,
    val isChecked: Boolean,
    val position: Int
)

data class EditorUiState(
    val isLoading: Boolean = true,
    val noteId: Long = 0L,
    val title: String = "",
    val content: TextFieldValue = TextFieldValue(""),
    val folderId: Long? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val reminderAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val checklist: List<ChecklistUiItem> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val selectedTagIds: Set<Long> = emptySet(),
    val attachments: List<AttachmentEntity> = emptyList(),
    val transcriptions: Map<Long, TranscriptionEntity> = emptyMap(),
    val missingAttachmentIds: Set<Long> = emptySet(),
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val lastSavedAt: Long? = null,
    val loadError: String? = null
)

sealed interface EditorEvent {
    data object NavigateBack : EditorEvent
    data class ShowMessage(val text: String) : EditorEvent
}

class EditorViewModel(
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val tagRepository: TagRepository,
    private val attachmentRepository: AttachmentRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val externalScope: CoroutineScope,
    private val requestedNoteId: Long,
    private val initialFolderId: Long?,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val isNewNote = requestedNoteId == 0L
    private val checklistMutex = Mutex()

    private var saveInFlight = false
    private var saveRequested = false

    @Volatile
    private var exited = false

    @Volatile
    private var discardIfEmptyOnExit = false

    fun markExplicitExit() {
        discardIfEmptyOnExit = true
    }

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            runCatching {
                val noteId = resolveNoteId()
                val note = noteRepository.getNote(noteId)
                    ?: error("This note no longer exists")
                val checklist = noteRepository.getChecklist(noteId)
                val folders = folderRepository.getFolders()
                val tags = tagRepository.getTags()
                val selectedTagIds = tagRepository.getTagIdsForNote(noteId)
                Triple(note, checklist, folders) to (tags to selectedTagIds)
            }.onSuccess { (noteData, tagsData) ->
                val (note, checklist, folders) = noteData
                val (tags, selectedTagIds) = tagsData
                if (exited) {
                    if (isNewNote && discardIfEmptyOnExit &&
                        note.title.isBlank() && note.content.isBlank()
                    ) {
                        runCatching { noteRepository.deleteNotePermanently(note.id) }
                    }
                    return@onSuccess
                }
                _state.value = EditorUiState(
                    isLoading = false,
                    noteId = note.id,
                    title = note.title,
                    content = TextFieldValue(note.content, TextRange(note.content.length)),
                    folderId = note.folderId,
                    isPinned = note.isPinned,
                    isArchived = note.isArchived,
                    isTrashed = note.isTrashed,
                    reminderAt = note.reminderAt,
                    createdAt = note.createdAt,
                    updatedAt = note.updatedAt,
                    checklist = checklist.map { it.toUi() },
                    folders = folders,
                    allTags = tags,
                    selectedTagIds = selectedTagIds
                )
                observeAttachments(note.id)
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        loadError = throwable.message ?: "Could not load this note"
                    )
                }
            }
        }
    }

    fun onTitleChange(value: String) {
        if (_state.value.isTrashed) return
        _state.update { it.copy(title = value, isDirty = true) }
        requestSave()
    }

    fun onContentChange(value: TextFieldValue) {
        if (_state.value.isTrashed) return
        _state.update { it.copy(content = value, isDirty = true) }
        requestSave()
    }

    private fun requestSave() {
        if (saveInFlight) {
            saveRequested = true
            return
        }
        saveInFlight = true
        viewModelScope.launch {
            try {
                do {
                    saveRequested = false
                    persistLatest()
                } while (saveRequested)
            } finally {
                saveInFlight = false
                if (saveRequested) requestSave()
            }
        }
    }

    private suspend fun persistLatest() {
        val snapshot = _state.value
        if (snapshot.noteId == 0L || snapshot.isTrashed) return
        _state.update { it.copy(isSaving = true) }
        try {
            noteRepository.saveNote(snapshot.noteId, snapshot.title, snapshot.content.text)
            val now = System.currentTimeMillis()
            _state.update { current ->
                if (current.title == snapshot.title && current.content.text == snapshot.content.text) {
                    current.copy(
                        isDirty = false,
                        isSaving = false,
                        lastSavedAt = now,
                        updatedAt = now
                    )
                } else {
                    current.copy(isSaving = false)
                }
            }
        } catch (throwable: Throwable) {
            _state.update { it.copy(isSaving = false) }
            _events.trySend(
                EditorEvent.ShowMessage(throwable.message ?: "Could not save note")
            )
        }
    }

    fun onExit() {
        exited = true
        val snapshot = _state.value
        val noteId = snapshot.noteId
        if (noteId == 0L) return
        externalScope.launch {
            runCatching {
                val isEmptyNewNote = isNewNote &&
                    discardIfEmptyOnExit &&
                    snapshot.title.isBlank() &&
                    snapshot.content.text.isBlank() &&
                    snapshot.checklist.isEmpty() &&
                    snapshot.selectedTagIds.isEmpty() &&
                    snapshot.attachments.isEmpty() &&
                    snapshot.transcriptions.isEmpty()
                if (isEmptyNewNote) {
                    noteRepository.deleteNotePermanently(noteId)
                } else {
                    noteRepository.saveNote(noteId, snapshot.title, snapshot.content.text)
                }
            }
        }
    }

    private suspend fun resolveNoteId(): Long {
        if (!isNewNote) return requestedNoteId
        val savedNoteId = savedStateHandle.get<Long>(KEY_CREATED_NOTE_ID)
        if (savedNoteId != null && savedNoteId > 0L) return savedNoteId
        val createdNoteId = noteRepository.createNote(folderId = initialFolderId)
        savedStateHandle[KEY_CREATED_NOTE_ID] = createdNoteId
        return createdNoteId
    }

    fun togglePin() {
        val target = !_state.value.isPinned
        val noteId = _state.value.noteId
        if (noteId == 0L) return
        _state.update { it.copy(isPinned = target) }
        viewModelScope.launch {
            runCatching { noteRepository.setPinned(noteId, target) }
                .onFailure {
                    _state.update { current -> current.copy(isPinned = !target) }
                    _events.trySend(
                        EditorEvent.ShowMessage(it.message ?: "Could not update pin")
                    )
                }
        }
    }

    fun toggleArchive() {
        val target = !_state.value.isArchived
        val noteId = _state.value.noteId
        if (noteId == 0L) return
        _state.update { it.copy(isArchived = target) }
        viewModelScope.launch {
            runCatching { noteRepository.setArchived(noteId, target) }
                .onSuccess {
                    _events.trySend(
                        EditorEvent.ShowMessage(
                            if (target) "Note archived" else "Note moved out of archive"
                        )
                    )
                }
                .onFailure {
                    _state.update { current -> current.copy(isArchived = !target) }
                    _events.trySend(
                        EditorEvent.ShowMessage(it.message ?: "Could not update archive")
                    )
                }
        }
    }

    fun selectFolder(folderId: Long?) {
        val noteId = _state.value.noteId
        if (noteId == 0L) return
        val previous = _state.value.folderId
        _state.update { it.copy(folderId = folderId) }
        viewModelScope.launch {
            runCatching { noteRepository.setFolder(noteId, folderId) }
                .onFailure {
                    _state.update { current -> current.copy(folderId = previous) }
                    _events.trySend(
                        EditorEvent.ShowMessage(it.message ?: "Could not move note")
                    )
                }
        }
    }

    fun toggleTag(tagId: Long) {
        val noteId = _state.value.noteId
        if (noteId == 0L) return
        val previous = _state.value.selectedTagIds
        val updated = if (tagId in previous) previous - tagId else previous + tagId
        _state.update { it.copy(selectedTagIds = updated) }
        viewModelScope.launch {
            runCatching { tagRepository.setNoteTags(noteId, updated) }
                .onFailure {
                    _state.update { current -> current.copy(selectedTagIds = previous) }
                    _events.trySend(
                        EditorEvent.ShowMessage(it.message ?: "Could not update tags")
                    )
                }
        }
    }

    fun createTag(name: String) {
        val noteId = _state.value.noteId
        if (noteId == 0L) return
        viewModelScope.launch {
            runCatching { tagRepository.createTag(name) }
                .onSuccess { tagId ->
                    val tags = tagRepository.getTags()
                    val selected = _state.value.selectedTagIds + tagId
                    _state.update { it.copy(allTags = tags, selectedTagIds = selected) }
                    runCatching { tagRepository.setNoteTags(noteId, selected) }
                        .onFailure {
                            _events.trySend(
                                EditorEvent.ShowMessage(it.message ?: "Could not update tags")
                            )
                        }
                }
                .onFailure {
                    _events.trySend(
                        EditorEvent.ShowMessage(it.message ?: "Could not create tag")
                    )
                }
        }
    }

    fun addChecklistItem(text: String) {
        val noteId = _state.value.noteId
        val trimmed = text.trim()
        if (noteId == 0L || trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching { noteRepository.addChecklistItem(noteId, trimmed) }
                .onSuccess { item ->
                    if (item != null) {
                        _state.update { current ->
                            current.copy(
                                checklist = current.checklist + item.toUi(),
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                    }
                }
                .onFailure {
                    _events.trySend(
                        EditorEvent.ShowMessage(it.message ?: "Could not add checklist item")
                    )
                }
        }
    }

    fun onChecklistTextChange(itemId: Long, text: String) {
        if (_state.value.isTrashed) return
        _state.update { current ->
            current.copy(
                checklist = current.checklist.map {
                    if (it.id == itemId) it.copy(text = text) else it
                }
            )
        }
        persistChecklistText(itemId)
    }

    private fun persistChecklistText(itemId: Long) {
        val noteId = _state.value.noteId
        if (noteId == 0L) return
        viewModelScope.launch {
            checklistMutex.withLock {
                val latest = _state.value.checklist.firstOrNull { it.id == itemId }?.text ?: return@withLock
                runCatching { noteRepository.updateChecklistItemText(noteId, itemId, latest) }
                    .onFailure {
                        _events.trySend(
                            EditorEvent.ShowMessage(it.message ?: "Could not save checklist item")
                        )
                    }
            }
        }
    }

    fun toggleChecklistItem(itemId: Long) {
        if (_state.value.isTrashed) return
        val currentItem = _state.value.checklist.firstOrNull { it.id == itemId } ?: return
        val target = !currentItem.isChecked
        _state.update { current ->
            current.copy(
                checklist = current.checklist.map {
                    if (it.id == itemId) it.copy(isChecked = target) else it
                }
            )
        }
        val noteId = _state.value.noteId
        viewModelScope.launch {
            checklistMutex.withLock {
                runCatching { noteRepository.setChecklistItemChecked(noteId, itemId, target) }
                    .onFailure {
                        _state.update { current ->
                            current.copy(
                                checklist = current.checklist.map {
                                    if (it.id == itemId) it.copy(isChecked = !target) else it
                                }
                            )
                        }
                        _events.trySend(
                            EditorEvent.ShowMessage(it.message ?: "Could not update checklist item")
                        )
                    }
            }
        }
    }

    fun deleteChecklistItem(itemId: Long) {
        val noteId = _state.value.noteId
        if (noteId == 0L) return
        val removed = _state.value.checklist.firstOrNull { it.id == itemId } ?: return
        _state.update { current ->
            current.copy(checklist = current.checklist.filterNot { it.id == itemId })
        }
        viewModelScope.launch {
            runCatching { noteRepository.deleteChecklistItem(noteId, itemId) }
                .onFailure {
                    _state.update { current ->
                        current.copy(checklist = (current.checklist + removed).sortedBy { it.position })
                    }
                    _events.trySend(
                        EditorEvent.ShowMessage(it.message ?: "Could not delete checklist item")
                    )
                }
        }
    }

    fun setReminder(triggerAtMillis: Long) {
        val noteId = _state.value.noteId
        if (noteId == 0L) return
        viewModelScope.launch {
            runCatching { noteRepository.setReminder(noteId, triggerAtMillis) }
                .onSuccess { result ->
                    _state.update { it.copy(reminderAt = triggerAtMillis) }
                    _events.trySend(EditorEvent.ShowMessage(reminderMessage(result)))
                }
                .onFailure {
                    _events.trySend(
                        EditorEvent.ShowMessage(it.message ?: "Could not set reminder")
                    )
                }
        }
    }

    fun clearReminder() {
        val noteId = _state.value.noteId
        if (noteId == 0L) return
        viewModelScope.launch {
            runCatching { noteRepository.setReminder(noteId, null) }
                .onSuccess {
                    _state.update { it.copy(reminderAt = null) }
                    _events.trySend(EditorEvent.ShowMessage("Reminder removed"))
                }
                .onFailure {
                    _events.trySend(
                        EditorEvent.ShowMessage(it.message ?: "Could not remove reminder")
                    )
                }
        }
    }

    private fun observeAttachments(noteId: Long) {
        viewModelScope.launch {
            combine(
                attachmentRepository.observeAttachments(noteId),
                transcriptionRepository.observeForNote(noteId)
            ) { attachments, transcriptions ->
                attachments to transcriptions.associateBy { it.attachmentId }
            }.collect { (attachments, transcriptions) ->
                val missing = attachments.filter { attachmentRepository.isMissing(it) }
                    .mapTo(mutableSetOf()) { it.id }
                _state.update {
                    it.copy(
                        attachments = attachments,
                        transcriptions = transcriptions,
                        missingAttachmentIds = missing
                    )
                }
            }
        }
    }

    fun addAttachments(uris: List<Uri>) {
        val noteId = _state.value.noteId
        if (noteId == 0L || _state.value.isTrashed || uris.isEmpty()) return
        viewModelScope.launch {
            val summary = attachmentRepository.importUris(noteId, uris)
            val message = when {
                summary.added.isNotEmpty() && summary.errors.isEmpty() ->
                    if (summary.added.size == 1) "Attachment added" else
                        "${summary.added.size} attachments added"
                summary.added.isNotEmpty() && summary.errors.isNotEmpty() ->
                    "${summary.added.size} added, ${summary.errors.size} failed: " +
                        summary.errors.first()
                summary.errors.isNotEmpty() -> summary.errors.first()
                else -> "No attachments were added"
            }
            _events.send(EditorEvent.ShowMessage(message))
        }
    }

    fun removeAttachment(attachmentId: Long) {
        viewModelScope.launch {
            runCatching { attachmentRepository.deleteAttachment(attachmentId) }
                .onSuccess { _events.send(EditorEvent.ShowMessage("Attachment removed")) }
                .onFailure {
                    _events.send(
                        EditorEvent.ShowMessage(it.message ?: "Could not remove attachment")
                    )
                }
        }
    }

    fun restore() {
        val noteId = _state.value.noteId
        if (noteId == 0L) return
        viewModelScope.launch {
            runCatching { noteRepository.restoreNote(noteId) }
                .onSuccess {
                    _state.update { it.copy(isTrashed = false) }
                    _events.trySend(EditorEvent.ShowMessage("Note restored"))
                }
                .onFailure {
                    _events.trySend(
                        EditorEvent.ShowMessage(it.message ?: "Could not restore note")
                    )
                }
        }
    }

    fun trash() {
        val snapshot = _state.value
        if (snapshot.noteId == 0L) return
        viewModelScope.launch {
            runCatching {
                if (isNewNote && snapshot.title.isBlank() && snapshot.content.text.isBlank()) {
                    noteRepository.deleteNotePermanently(snapshot.noteId)
                } else {
                    noteRepository.saveNote(snapshot.noteId, snapshot.title, snapshot.content.text)
                    noteRepository.trashNote(snapshot.noteId)
                }
            }.onSuccess {
                _state.update { it.copy(isTrashed = true) }
                _events.trySend(EditorEvent.NavigateBack)
            }.onFailure {
                _events.trySend(
                    EditorEvent.ShowMessage(it.message ?: "Could not move note to trash")
                )
            }
        }
    }

    fun deleteForever() {
        val noteId = _state.value.noteId
        if (noteId == 0L) return
        viewModelScope.launch {
            runCatching { noteRepository.deleteNotePermanently(noteId) }
                .onSuccess { _events.trySend(EditorEvent.NavigateBack) }
                .onFailure {
                    _events.trySend(
                        EditorEvent.ShowMessage(it.message ?: "Could not delete note")
                    )
                }
        }
    }

    private fun reminderMessage(result: ReminderScheduleResult): String = when (result) {
        ReminderScheduleResult.EXACT -> "Reminder set"
        ReminderScheduleResult.INEXACT ->
            "Reminder set, but Android may deliver it slightly late. Allow exact alarms in Settings for precision."
        ReminderScheduleResult.CLEARED -> "Reminder removed"
        ReminderScheduleResult.FAILED -> "Reminder saved, but it could not be scheduled"
    }

    private fun ChecklistItemEntity.toUi() = ChecklistUiItem(
        id = id,
        text = text,
        isChecked = isChecked,
        position = position
    )

    private companion object {
        const val KEY_CREATED_NOTE_ID = "created_note_id"
    }
}
