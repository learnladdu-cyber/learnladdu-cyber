package com.ashis.notesphare.sync

import androidx.room.withTransaction
import com.ashis.notesphare.data.local.AttachmentDao
import com.ashis.notesphare.data.local.AttachmentSyncStatus
import com.ashis.notesphare.data.local.ChecklistDao
import com.ashis.notesphare.data.local.FolderDao
import com.ashis.notesphare.data.local.NoteDao
import com.ashis.notesphare.data.local.NoteSphereDatabase
import com.ashis.notesphare.data.local.NoteTagDao
import com.ashis.notesphare.data.local.OutboxDao
import com.ashis.notesphare.data.local.OutboxEntity
import com.ashis.notesphare.data.local.OutboxOperation
import com.ashis.notesphare.data.local.SyncEntityType
import com.ashis.notesphare.data.local.TagDao
import com.ashis.notesphare.data.local.TranscriptionDao
import com.ashis.notesphare.files.StorageException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject

/**
 * Writes local changes and their outbox entries in one Room transaction. A row owned by a
 * different account is never queued, which prevents accidental cross-account uploads.
 */
class LocalSyncWriter(
    private val database: NoteSphereDatabase,
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val tagDao: TagDao,
    private val noteTagDao: NoteTagDao,
    private val checklistDao: ChecklistDao,
    private val attachmentDao: AttachmentDao,
    private val transcriptionDao: TranscriptionDao,
    private val outboxDao: OutboxDao,
    private val currentUserId: suspend () -> String?,
    val ownerScope: Flow<String?> = flowOf(null)
) {

    var onQueued: (() -> Unit)? = null

    suspend fun <T> write(block: suspend () -> T): T {
        val result = database.withTransaction { block() }
        onQueued?.invoke()
        return result
    }

    suspend fun currentOwnerId(): String? = currentUserId()

    suspend fun enqueueNote(noteId: Long) {
        val note = noteDao.getAnyById(noteId) ?: return
        val owner = note.ownerId ?: return
        if (owner != currentUserId()) return
        val folderUuid = note.folderId?.let { folderDao.getById(it)?.uuid }
        enqueue(
            owner = owner,
            type = SyncEntityType.NOTE,
            uuid = note.uuid,
            operation = if (note.deletedAt != null) OutboxOperation.DELETE else OutboxOperation.UPSERT,
            version = note.version,
            payload = SyncPayloads.note(note, folderUuid)
        )
    }

    suspend fun enqueueNoteTags(noteId: Long) {
        val note = noteDao.getAnyById(noteId) ?: return
        val owner = note.ownerId ?: return
        if (owner != currentUserId()) return
        val tags = noteTagDao.getTagsForNote(noteId)
        enqueue(
            owner = owner,
            type = SyncEntityType.NOTE_TAGS,
            uuid = note.uuid,
            operation = OutboxOperation.UPSERT,
            version = note.version,
            payload = SyncPayloads.noteTags(note, tags)
        )
    }

    suspend fun enqueueFolder(folderId: Long) {
        val folder = folderDao.getById(folderId) ?: folderDao.getAllForSync().firstOrNull { it.id == folderId }
        folder ?: return
        val owner = folder.ownerId ?: return
        if (owner != currentUserId()) return
        enqueue(
            owner = owner,
            type = SyncEntityType.FOLDER,
            uuid = folder.uuid,
            operation = if (folder.deletedAt != null) OutboxOperation.DELETE else OutboxOperation.UPSERT,
            version = folder.version,
            payload = SyncPayloads.folder(folder)
        )
    }

    suspend fun enqueueTag(tagId: Long) {
        val tag = tagDao.getByIdIncludingDeleted(tagId) ?: return
        val owner = tag.ownerId ?: return
        if (owner != currentUserId()) return
        enqueue(
            owner = owner,
            type = SyncEntityType.TAG,
            uuid = tag.uuid,
            operation = if (tag.deletedAt != null) OutboxOperation.DELETE else OutboxOperation.UPSERT,
            version = tag.version,
            payload = SyncPayloads.tag(tag)
        )
    }

    suspend fun enqueueChecklist(itemId: Long) {
        val item = checklistDao.getByUuidById(itemId) ?: return
        val owner = item.ownerId ?: return
        if (owner != currentUserId()) return
        val noteUuid = noteDao.getAnyById(item.noteId)?.uuid ?: return
        enqueue(
            owner = owner,
            type = SyncEntityType.CHECKLIST,
            uuid = item.uuid,
            operation = if (item.deletedAt != null) OutboxOperation.DELETE else OutboxOperation.UPSERT,
            version = item.version,
            payload = SyncPayloads.checklist(item, noteUuid)
        )
    }

    suspend fun enqueueAttachment(attachmentId: Long) {
        val attachment = attachmentDao.getAnyById(attachmentId) ?: return
        val owner = attachment.ownerId ?: return
        if (owner != currentUserId()) return
        val noteUuid = noteDao.getAnyById(attachment.noteId)?.uuid
        enqueue(
            owner = owner,
            type = SyncEntityType.ATTACHMENT,
            uuid = attachment.uuid,
            operation = if (attachment.deletedAt != null) OutboxOperation.DELETE else OutboxOperation.UPSERT,
            version = attachment.version,
            payload = SyncPayloads.attachment(attachment, noteUuid)
        )
    }

    suspend fun enqueueTranscription(attachmentId: Long) {
        val transcription = transcriptionDao.getForAttachment(attachmentId)
            ?: transcriptionDao.getAnyForAttachment(attachmentId)
            ?: return
        val owner = transcription.ownerId ?: return
        if (owner != currentUserId()) return
        val noteUuid = noteDao.getAnyById(transcription.noteId)?.uuid
        val attachmentUuid = attachmentDao.getAnyById(transcription.attachmentId)?.uuid
        enqueue(
            owner = owner,
            type = SyncEntityType.TRANSCRIPTION,
            uuid = transcription.uuid,
            operation = if (transcription.deletedAt != null) OutboxOperation.DELETE else OutboxOperation.UPSERT,
            version = transcription.version,
            payload = SyncPayloads.transcription(transcription, noteUuid, attachmentUuid)
        )
    }

    /**
     * Explicit user action: attach local-only data to the signed-in account. Nothing is linked
     * automatically on login, so switching accounts cannot silently upload another user's notes.
     */
    suspend fun claimLocalData(): Int {
        val owner = currentUserId() ?: throw StorageException("Sign in before linking local data.")
        var claimed = 0
        database.withTransaction {
            val notes = noteDao.getAllForSync().filter { it.ownerId == null }
            val folders = folderDao.getAllForSync().filter { it.ownerId == null }
            val tags = tagDao.getAllForSync().filter { it.ownerId == null }
            val checklist = checklistDao.getAllForSync().filter { it.ownerId == null }
            val attachments = attachmentDao.getAllIncludingDeleted().filter { it.ownerId == null }
            val transcriptions = transcriptionDao.getAllIncludingDeleted().filter { it.ownerId == null }

            noteDao.claimUnowned(owner)
            folderDao.claimUnowned(owner)
            tagDao.claimUnowned(owner)
            checklistDao.claimUnowned(owner)
            attachmentDao.claimUnowned(owner)
            transcriptionDao.claimUnowned(owner)

            notes.forEach {
                enqueueNote(it.id)
                enqueueNoteTags(it.id)
            }
            folders.forEach { enqueueFolder(it.id) }
            tags.forEach { enqueueTag(it.id) }
            checklist.forEach { enqueueChecklist(it.id) }
            attachments.filter { it.deletedAt == null }.forEach { enqueueAttachment(it.id) }
            transcriptions.filter { it.deletedAt == null }.forEach {
                enqueueTranscription(it.attachmentId)
            }
            claimed = notes.size + folders.size + tags.size + checklist.size +
                attachments.count { it.deletedAt == null }
        }
        onQueued?.invoke()
        return claimed
    }

    suspend fun pendingCount(): Int = currentUserId()?.let { outboxDao.count(it) } ?: 0

    suspend fun notesInFolder(folderId: Long) = noteDao.getByFolder(folderId)

    suspend fun attachmentsForNote(noteId: Long) = attachmentDao.getAllForNoteIncludingDeleted(noteId)

    suspend fun transcriptionsForNote(noteId: Long) =
        transcriptionDao.getAllForNoteIncludingDeleted(noteId)

    suspend fun softDeleteAttachmentsForNote(noteId: Long, now: Long) {
        attachmentDao.softDeleteForNote(noteId, now)
    }

    suspend fun softDeleteTranscriptionsForNote(noteId: Long, now: Long) {
        transcriptionDao.softDeleteForNote(noteId, now)
    }

    private suspend fun enqueue(
        owner: String,
        type: SyncEntityType,
        uuid: String,
        operation: OutboxOperation,
        version: Long,
        payload: JSONObject
    ) {
        outboxDao.deleteForEntity(type.name, uuid)
        outboxDao.insert(
            OutboxEntity(
                ownerId = owner,
                entityType = type.name,
                entityUuid = uuid,
                operation = operation.name,
                payload = payload.toString(),
                baseVersion = (version - 1L).coerceAtLeast(0L)
            )
        )
    }
}
