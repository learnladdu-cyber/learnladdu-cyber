package com.ashis.notesphare.sync

import com.ashis.notesphare.data.local.AttachmentDao
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.local.AttachmentSyncStatus
import com.ashis.notesphare.data.local.ChecklistDao
import com.ashis.notesphare.data.local.ChecklistItemEntity
import com.ashis.notesphare.data.local.ConflictDao
import com.ashis.notesphare.data.local.FolderDao
import com.ashis.notesphare.data.local.FolderEntity
import com.ashis.notesphare.data.local.NoteDao
import com.ashis.notesphare.data.local.NoteEntity
import com.ashis.notesphare.data.local.NoteTagCrossRef
import com.ashis.notesphare.data.local.NoteTagDao
import com.ashis.notesphare.data.local.OutboxDao
import com.ashis.notesphare.data.local.SyncEntityType
import com.ashis.notesphare.data.local.TagDao
import com.ashis.notesphare.data.local.TagEntity
import com.ashis.notesphare.data.local.TranscriptionDao
import com.ashis.notesphare.data.local.TranscriptionEntity
import org.json.JSONObject

/**
 * Applies server rows to local Room data without creating outbox entries. Rows with a pending
 * local change are deliberately skipped; the pending push will either win or create a conflict.
 */
class LocalSyncApplier(
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val tagDao: TagDao,
    private val noteTagDao: NoteTagDao,
    private val checklistDao: ChecklistDao,
    private val attachmentDao: AttachmentDao,
    private val transcriptionDao: TranscriptionDao,
    private val outboxDao: OutboxDao,
    private val conflictDao: ConflictDao
) {

    suspend fun applyRemote(entityType: String, row: JSONObject, ownerId: String): Boolean {
        val table = when (SyncEntityType.valueOf(entityType)) {
            SyncEntityType.FOLDER -> "folders"
            SyncEntityType.TAG -> "tags"
            SyncEntityType.NOTE -> "notes"
            SyncEntityType.CHECKLIST -> "checklist_items"
            SyncEntityType.ATTACHMENT -> "attachments"
            SyncEntityType.TRANSCRIPTION -> "transcriptions"
            SyncEntityType.NOTE_TAGS -> "note_tags"
        }
        return apply(table, row, ownerId)
    }

    suspend fun apply(table: String, row: JSONObject, ownerId: String): Boolean {
        val uuid = row.optString("id")
            .ifBlank { row.optString("note_id") }
            .ifBlank { return false }
        val type = typeFor(table) ?: return false
        if (outboxDao.getForEntity(type.name, uuid) != null) return false
        if (conflictDao.getForEntity(ownerId, type.name, uuid) != null) return false
        if (row.optString("owner_id").takeIf { it.isNotBlank() } != ownerId) return false
        return when (table) {
            "folders" -> applyFolder(row, ownerId)
            "tags" -> applyTag(row, ownerId)
            "notes" -> applyNote(row, ownerId)
            "checklist_items" -> applyChecklist(row, ownerId)
            "attachments" -> applyAttachment(row, ownerId)
            "transcriptions" -> applyTranscription(row, ownerId)
            "note_tags" -> applyNoteTag(row)
            else -> false
        }
    }

    private suspend fun applyFolder(row: JSONObject, ownerId: String): Boolean {
        val uuid = row.optString("id")
        val existing = folderDao.getByUuid(uuid)
        val entity = FolderEntity(
            id = existing?.id ?: 0L,
            name = row.optString("name", existing?.name ?: "Folder"),
            color = row.optInt("color", existing?.color ?: 0),
            createdAt = time(row, "created_at", existing?.createdAt),
            uuid = uuid,
            ownerId = ownerId,
            version = row.optLong("version", existing?.version ?: 1L),
            updatedAt = time(row, "updated_at", existing?.updatedAt),
            deletedAt = nullableTime(row, "deleted_at")
        )
        if (existing == null) folderDao.insert(entity) else folderDao.updateEntity(entity)
        return true
    }

    private suspend fun applyTag(row: JSONObject, ownerId: String): Boolean {
        val uuid = row.optString("id")
        val existing = tagDao.getByUuid(uuid)
        val entity = TagEntity(
            id = existing?.id ?: 0L,
            name = row.optString("name", existing?.name ?: "Tag"),
            color = row.optInt("color", existing?.color ?: 0),
            createdAt = time(row, "created_at", existing?.createdAt),
            uuid = uuid,
            ownerId = ownerId,
            version = row.optLong("version", existing?.version ?: 1L),
            updatedAt = time(row, "updated_at", existing?.updatedAt),
            deletedAt = nullableTime(row, "deleted_at")
        )
        if (existing == null) tagDao.insert(entity) else tagDao.updateEntity(entity)
        return true
    }

    private suspend fun applyNote(row: JSONObject, ownerId: String): Boolean {
        val uuid = row.optString("id")
        val existing = noteDao.getByUuid(uuid)
        val folderUuid = row.optString("folder_id").takeIf { it.isNotBlank() }
        val folderId = folderUuid?.let { folderDao.getByUuid(it)?.id }
        val entity = NoteEntity(
            id = existing?.id ?: 0L,
            title = row.optString("title", existing?.title ?: ""),
            content = row.optString("content", existing?.content ?: ""),
            folderId = folderId,
            isPinned = row.optBoolean("is_pinned", existing?.isPinned ?: false),
            isArchived = row.optBoolean("is_archived", existing?.isArchived ?: false),
            isTrashed = row.optBoolean("is_trashed", existing?.isTrashed ?: false),
            trashedAt = nullableTime(row, "trashed_at"),
            reminderAt = nullableTime(row, "reminder_at"),
            createdAt = time(row, "created_at", existing?.createdAt),
            updatedAt = time(row, "updated_at", existing?.updatedAt),
            uuid = uuid,
            ownerId = ownerId,
            version = row.optLong("version", existing?.version ?: 1L),
            deletedAt = nullableTime(row, "deleted_at")
        )
        if (existing == null) noteDao.insert(entity) else noteDao.update(entity)
        return true
    }

    private suspend fun applyChecklist(row: JSONObject, ownerId: String): Boolean {
        val noteUuid = row.optString("note_id")
        val note = noteDao.getByUuid(noteUuid) ?: return false
        val uuid = row.optString("id")
        val existing = checklistDao.getByUuid(uuid)
        val entity = ChecklistItemEntity(
            id = existing?.id ?: 0L,
            noteId = note.id,
            text = row.optString("text", existing?.text ?: ""),
            isChecked = row.optBoolean("is_checked", existing?.isChecked ?: false),
            position = row.optInt("position", existing?.position ?: 0),
            createdAt = time(row, "created_at", existing?.createdAt),
            uuid = uuid,
            ownerId = ownerId,
            version = row.optLong("version", existing?.version ?: 1L),
            updatedAt = time(row, "updated_at", existing?.updatedAt),
            deletedAt = nullableTime(row, "deleted_at")
        )
        if (existing == null) checklistDao.insert(entity) else checklistDao.update(entity)
        return true
    }

    private suspend fun applyAttachment(row: JSONObject, ownerId: String): Boolean {
        val uuid = row.optString("id")
        val existing = attachmentDao.getByUuid(uuid)
        val noteUuid = row.optString("note_id").takeIf { it.isNotBlank() }
        val noteId = noteUuid?.let { noteDao.getByUuid(it)?.id }
        val cloudPath = row.optString("storage_path").takeIf { it.isNotBlank() }
        val deletedAt = nullableTime(row, "deleted_at")
        val status = when {
            deletedAt != null -> existing?.syncStatus ?: AttachmentSyncStatus.SYNCED.name
            existing == null && cloudPath != null -> AttachmentSyncStatus.PENDING_DOWNLOAD.name
            existing == null -> AttachmentSyncStatus.MISSING_REMOTE.name
            else -> existing.syncStatus
        }
        val entity = AttachmentEntity(
            id = existing?.id ?: 0L,
            noteId = noteId ?: existing?.noteId ?: return false,
            displayName = row.optString("display_name", existing?.displayName ?: "Attachment"),
            mimeType = row.optString("mime_type", existing?.mimeType ?: "application/octet-stream"),
            kind = row.optString("kind", existing?.kind ?: "DOCUMENT"),
            storedPath = existing?.storedPath ?: "attachments/remote/$uuid",
            sizeBytes = row.optLong("size_bytes", existing?.sizeBytes ?: 0L),
            durationMs = nullableLong(row, "duration_ms") ?: existing?.durationMs,
            waveform = row.optString("waveform").takeIf { it.isNotBlank() } ?: existing?.waveform,
            createdAt = time(row, "created_at", existing?.createdAt),
            uuid = uuid,
            ownerId = ownerId,
            version = row.optLong("version", existing?.version ?: 1L),
            updatedAt = time(row, "updated_at", existing?.updatedAt),
            deletedAt = deletedAt,
            cloudPath = cloudPath,
            syncStatus = status,
            syncError = existing?.syncError,
            remoteUpdatedAt = System.currentTimeMillis()
        )
        if (existing == null) attachmentDao.insert(entity) else attachmentDao.update(entity)
        return true
    }

    private suspend fun applyTranscription(row: JSONObject, ownerId: String): Boolean {
        val uuid = row.optString("id")
        val existing = transcriptionDao.getByUuid(uuid)
        val noteUuid = row.optString("note_id").takeIf { it.isNotBlank() }
        val attachmentUuid = row.optString("attachment_id").takeIf { it.isNotBlank() }
        val noteId = noteUuid?.let { noteDao.getByUuid(it)?.id }
        val attachmentId = attachmentUuid?.let { attachmentDao.getByUuid(it)?.id }
        val entity = TranscriptionEntity(
            id = existing?.id ?: 0L,
            noteId = noteId ?: existing?.noteId ?: return false,
            attachmentId = attachmentId ?: existing?.attachmentId ?: return false,
            language = row.optString("language", existing?.language ?: "ENGLISH"),
            engineId = row.optString("engine_id", existing?.engineId ?: "unknown"),
            text = row.optString("text", existing?.text ?: ""),
            status = row.optString("status", existing?.status ?: "PENDING"),
            errorMessage = row.optString("error_message").takeIf { it.isNotBlank() },
            createdAt = time(row, "created_at", existing?.createdAt),
            updatedAt = time(row, "updated_at", existing?.updatedAt),
            uuid = uuid,
            ownerId = ownerId,
            version = row.optLong("version", existing?.version ?: 1L),
            deletedAt = nullableTime(row, "deleted_at")
        )
        if (existing == null) transcriptionDao.insertIgnore(entity) else transcriptionDao.update(entity)
        return true
    }

    private suspend fun applyNoteTag(row: JSONObject): Boolean {
        val note = noteDao.getByUuid(row.optString("note_id")) ?: return false
        val tag = tagDao.getByUuid(row.optString("tag_id")) ?: return false
        if (!row.isNull("deleted_at")) {
            noteTagDao.deleteRelation(note.id, tag.id)
        } else {
            noteTagDao.insertCrossRefs(listOf(NoteTagCrossRef(note.id, tag.id)))
        }
        return true
    }

    private fun typeFor(table: String): SyncEntityType? = when (table) {
        "folders" -> SyncEntityType.FOLDER
        "tags" -> SyncEntityType.TAG
        "notes" -> SyncEntityType.NOTE
        "checklist_items" -> SyncEntityType.CHECKLIST
        "attachments" -> SyncEntityType.ATTACHMENT
        "transcriptions" -> SyncEntityType.TRANSCRIPTION
        "note_tags" -> SyncEntityType.NOTE_TAGS
        else -> null
    }

    private fun time(row: JSONObject, key: String, fallback: Long?): Long =
        SyncPayloads.parseIso(row.optString(key)) ?: fallback ?: System.currentTimeMillis()

    private fun nullableTime(row: JSONObject, key: String): Long? {
        if (row.isNull(key)) return null
        return SyncPayloads.parseIso(row.optString(key))
    }

    private fun nullableLong(row: JSONObject, key: String): Long? {
        if (row.isNull(key) || !row.has(key)) return null
        return row.optLong(key)
    }
}
