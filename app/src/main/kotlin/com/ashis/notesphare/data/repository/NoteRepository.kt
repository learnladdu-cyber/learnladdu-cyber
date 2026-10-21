package com.ashis.notesphare.data.repository

import com.ashis.notesphare.data.local.ChecklistDao
import com.ashis.notesphare.data.local.ChecklistItemEntity
import com.ashis.notesphare.data.local.NoteDao
import com.ashis.notesphare.data.local.NoteEntity
import com.ashis.notesphare.reminders.ReminderScheduler
import com.ashis.notesphare.reminders.ScheduleAccuracy
import com.ashis.notesphare.sync.LocalSyncWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

enum class ReminderScheduleResult { EXACT, INEXACT, CLEARED, FAILED }

class NoteRepository(
    private val noteDao: NoteDao,
    private val checklistDao: ChecklistDao,
    private val reminderScheduler: ReminderScheduler,
    private val sync: LocalSyncWriter? = null
) {

    fun observeActiveNotes(): Flow<List<NoteEntity>> = noteDao.observeActive().visible()

    fun observePinnedNotes(): Flow<List<NoteEntity>> = noteDao.observePinned().visible()

    fun observeRecentNotes(): Flow<List<NoteEntity>> = noteDao.observeRecent().visible()

    fun observeArchivedNotes(): Flow<List<NoteEntity>> = noteDao.observeArchived().visible()

    fun observeTrashedNotes(): Flow<List<NoteEntity>> = noteDao.observeTrashed().visible()

    fun observeNotesInFolder(folderId: Long): Flow<List<NoteEntity>> =
        noteDao.observeInFolder(folderId).visible()

    fun observeNotesWithTag(tagId: Long): Flow<List<NoteEntity>> =
        noteDao.observeWithTag(tagId).visible()

    fun searchNotes(query: String): Flow<List<NoteEntity>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return flowOf(emptyList())
        return noteDao.search(escapeLike(trimmed)).visible()
    }

    suspend fun getNote(noteId: Long): NoteEntity? =
        noteDao.getById(noteId)?.takeIf { canAccess(it.ownerId) }

    suspend fun createNote(
        title: String = "",
        content: String = "",
        folderId: Long? = null
    ): Long = write {
        val now = System.currentTimeMillis()
        val id = noteDao.insert(
            NoteEntity(
                title = title,
                content = content,
                folderId = folderId,
                createdAt = now,
                updatedAt = now,
                ownerId = sync?.currentOwnerId()
            )
        )
        sync?.enqueueNote(id)
        id
    }

    suspend fun saveNote(noteId: Long, title: String, content: String): Boolean = write {
        val note = noteDao.getById(noteId) ?: return@write false
        if (!canAccess(note.ownerId)) return@write false
        noteDao.updateContent(noteId, title, content, System.currentTimeMillis())
        sync?.enqueueNote(noteId)
        true
    }

    suspend fun setPinned(noteId: Long, pinned: Boolean) = write {
        if (!canAccessNote(noteId)) return@write
        noteDao.updatePinned(noteId, pinned, System.currentTimeMillis())
        sync?.enqueueNote(noteId)
    }

    suspend fun setArchived(noteId: Long, archived: Boolean) = write {
        if (!canAccessNote(noteId)) return@write
        noteDao.updateArchived(noteId, archived, System.currentTimeMillis())
        sync?.enqueueNote(noteId)
    }

    suspend fun setFolder(noteId: Long, folderId: Long?) = write {
        if (!canAccessNote(noteId)) return@write
        noteDao.updateFolder(noteId, folderId, System.currentTimeMillis())
        sync?.enqueueNote(noteId)
    }

    suspend fun trashNote(noteId: Long) = write {
        val note = noteDao.getById(noteId) ?: return@write
        if (!canAccess(note.ownerId)) return@write
        noteDao.markTrashed(noteId, System.currentTimeMillis())
        reminderScheduler.cancel(noteId)
        sync?.enqueueNote(noteId)
    }

    suspend fun restoreNote(noteId: Long) = write {
        val note = noteDao.getById(noteId) ?: return@write
        if (!canAccess(note.ownerId)) return@write
        noteDao.markRestored(noteId, System.currentTimeMillis())
        val triggerAt = note.reminderAt
        if (triggerAt != null) {
            if (triggerAt > System.currentTimeMillis()) {
                reminderScheduler.schedule(noteId, triggerAt)
            } else {
                noteDao.updateReminderAt(noteId, null, System.currentTimeMillis())
            }
        }
        sync?.enqueueNote(noteId)
    }

    suspend fun deleteNotePermanently(noteId: Long) = write {
        if (!canAccessNote(noteId)) return@write
        reminderScheduler.cancel(noteId)
        softDeleteNoteAndChildren(noteId)
    }

    suspend fun emptyTrash() = write {
        noteDao.getTrashedNotes().filter { canAccess(it.ownerId) }.forEach { note ->
            reminderScheduler.cancel(note.id)
            softDeleteNoteAndChildren(note.id)
        }
    }

    suspend fun getChecklist(noteId: Long): List<ChecklistItemEntity> =
        if (canAccessNote(noteId)) checklistDao.getForNote(noteId) else emptyList()

    suspend fun addChecklistItem(noteId: Long, text: String): ChecklistItemEntity? = write {
        if (!canAccessNote(noteId)) return@write null
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@write null
        val now = System.currentTimeMillis()
        val position = checklistDao.maxPosition(noteId) + 1
        val item = ChecklistItemEntity(
            noteId = noteId,
            text = trimmed,
            position = position,
            createdAt = now,
            updatedAt = now,
            ownerId = sync?.currentOwnerId()
        )
        val id = checklistDao.insert(item)
        noteDao.touch(noteId, now)
        sync?.enqueueNote(noteId)
        sync?.enqueueChecklist(id)
        item.copy(id = id)
    }

    suspend fun updateChecklistItemText(noteId: Long, itemId: Long, text: String) = write {
        if (!canAccessNote(noteId)) return@write
        checklistDao.updateText(itemId, text, System.currentTimeMillis())
        noteDao.touch(noteId, System.currentTimeMillis())
        sync?.enqueueChecklist(itemId)
        sync?.enqueueNote(noteId)
    }

    suspend fun setChecklistItemChecked(noteId: Long, itemId: Long, checked: Boolean) = write {
        if (!canAccessNote(noteId)) return@write
        checklistDao.updateChecked(itemId, checked, System.currentTimeMillis())
        noteDao.touch(noteId, System.currentTimeMillis())
        sync?.enqueueChecklist(itemId)
        sync?.enqueueNote(noteId)
    }

    suspend fun deleteChecklistItem(noteId: Long, itemId: Long) = write {
        if (!canAccessNote(noteId)) return@write
        checklistDao.softDelete(itemId, System.currentTimeMillis())
        noteDao.touch(noteId, System.currentTimeMillis())
        sync?.enqueueChecklist(itemId)
        sync?.enqueueNote(noteId)
    }

    suspend fun setReminder(noteId: Long, triggerAtMillis: Long?): ReminderScheduleResult = write {
        val note = noteDao.getById(noteId) ?: return@write ReminderScheduleResult.FAILED
        if (!canAccess(note.ownerId)) return@write ReminderScheduleResult.FAILED
        if (triggerAtMillis == null) {
            noteDao.updateReminderAt(note.id, null, System.currentTimeMillis())
            reminderScheduler.cancel(note.id)
            sync?.enqueueNote(noteId)
            return@write ReminderScheduleResult.CLEARED
        }
        require(triggerAtMillis > System.currentTimeMillis()) {
            "Reminder time must be in the future"
        }
        noteDao.updateReminderAt(note.id, triggerAtMillis, System.currentTimeMillis())
        sync?.enqueueNote(noteId)
        when (reminderScheduler.schedule(note.id, triggerAtMillis)) {
            ScheduleAccuracy.EXACT -> ReminderScheduleResult.EXACT
            ScheduleAccuracy.INEXACT -> ReminderScheduleResult.INEXACT
            ScheduleAccuracy.FAILED -> ReminderScheduleResult.FAILED
        }
    }

    suspend fun markReminderFired(noteId: Long) = write {
        if (!canAccessNote(noteId)) return@write
        noteDao.updateReminderAt(noteId, null, System.currentTimeMillis())
        sync?.enqueueNote(noteId)
    }

    suspend fun rescheduleUpcomingReminders() {
        val now = System.currentTimeMillis()
        noteDao.getWithReminders().filter { canAccess(it.ownerId) }.forEach { note ->
            val triggerAt = note.reminderAt ?: return@forEach
            val target = if (triggerAt <= now) now + 1_500L else triggerAt
            reminderScheduler.schedule(note.id, target)
        }
    }

    private suspend fun softDeleteNoteAndChildren(noteId: Long) {
        val now = System.currentTimeMillis()
        val checklists = checklistDao.getAllForNoteIncludingDeleted(noteId)
        val attachments = sync?.attachmentsForNote(noteId).orEmpty()
        val transcriptions = sync?.transcriptionsForNote(noteId).orEmpty()
        noteDao.softDelete(noteId, now)
        checklistDao.softDeleteForNote(noteId, now)
        sync?.softDeleteAttachmentsForNote(noteId, now)
        sync?.softDeleteTranscriptionsForNote(noteId, now)
        sync?.enqueueNote(noteId)
        checklists.forEach { sync?.enqueueChecklist(it.id) }
        attachments.forEach { sync?.enqueueAttachment(it.id) }
        transcriptions.forEach { sync?.enqueueTranscription(it.attachmentId) }
    }

    private suspend fun canAccess(ownerId: String?): Boolean {
        val owner = sync?.currentOwnerId() ?: return true
        return ownerId == null || ownerId == owner
    }

    private suspend fun canAccessNote(noteId: Long): Boolean =
        noteDao.getAnyById(noteId)?.let { canAccess(it.ownerId) } ?: false

    private fun Flow<List<NoteEntity>>.visible(): Flow<List<NoteEntity>> =
        combine(this, sync?.ownerScope ?: flowOf(null)) { notes, owner ->
            if (owner == null) notes else notes.filter { it.ownerId == null || it.ownerId == owner }
        }

    private suspend fun <T> write(block: suspend () -> T): T =
        sync?.write(block) ?: block()

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
