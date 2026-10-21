package com.ashis.notesphare.sync

import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.local.ChecklistItemEntity
import com.ashis.notesphare.data.local.FolderEntity
import com.ashis.notesphare.data.local.NoteEntity
import com.ashis.notesphare.data.local.TagEntity
import com.ashis.notesphare.data.local.TranscriptionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** JSON payloads use the exact snake_case column names exposed by PostgREST. */
object SyncPayloads {

    fun folder(folder: FolderEntity): JSONObject = JSONObject()
        .put("id", folder.uuid)
        .put("owner_id", folder.ownerId)
        .put("name", folder.name)
        .put("color", folder.color)
        .put("created_at", iso(folder.createdAt))
        .put("updated_at", iso(folder.updatedAt))
        .put("version", folder.version)
        .putNullable("deleted_at", folder.deletedAt?.let(::iso))

    fun tag(tag: TagEntity): JSONObject = JSONObject()
        .put("id", tag.uuid)
        .put("owner_id", tag.ownerId)
        .put("name", tag.name)
        .put("color", tag.color)
        .put("created_at", iso(tag.createdAt))
        .put("updated_at", iso(tag.updatedAt))
        .put("version", tag.version)
        .putNullable("deleted_at", tag.deletedAt?.let(::iso))

    fun note(note: NoteEntity, folderUuid: String?): JSONObject = JSONObject()
        .put("id", note.uuid)
        .put("owner_id", note.ownerId)
        .put("title", note.title)
        .put("content", note.content)
        .putNullable("folder_id", folderUuid)
        .put("is_pinned", note.isPinned)
        .put("is_archived", note.isArchived)
        .put("is_trashed", note.isTrashed)
        .putNullable("trashed_at", note.trashedAt?.let(::iso))
        .putNullable("reminder_at", note.reminderAt?.let(::iso))
        .put("created_at", iso(note.createdAt))
        .put("updated_at", iso(note.updatedAt))
        .put("version", note.version)
        .putNullable("deleted_at", note.deletedAt?.let(::iso))

    fun noteTags(note: NoteEntity, tags: List<TagEntity>): JSONObject = JSONObject()
        .put("note_id", note.uuid)
        .put("owner_id", note.ownerId)
        .put("updated_at", iso(note.updatedAt))
        .put("tag_ids", JSONArray(tags.map { it.uuid }))

    fun checklist(item: ChecklistItemEntity, noteUuid: String): JSONObject = JSONObject()
        .put("id", item.uuid)
        .put("owner_id", item.ownerId)
        .put("note_id", noteUuid)
        .put("text", item.text)
        .put("is_checked", item.isChecked)
        .put("position", item.position)
        .put("created_at", iso(item.createdAt))
        .put("updated_at", iso(item.updatedAt))
        .put("version", item.version)
        .putNullable("deleted_at", item.deletedAt?.let(::iso))

    fun attachment(attachment: AttachmentEntity, noteUuid: String?): JSONObject = JSONObject()
        .put("id", attachment.uuid)
        .put("owner_id", attachment.ownerId)
        .putNullable("note_id", noteUuid)
        .put("display_name", attachment.displayName)
        .put("mime_type", attachment.mimeType)
        .put("kind", attachment.kind)
        .put("size_bytes", attachment.sizeBytes)
        .putNullable("duration_ms", attachment.durationMs)
        .putNullable("waveform", attachment.waveform)
        .put("created_at", iso(attachment.createdAt))
        .put("updated_at", iso(attachment.updatedAt))
        .put("version", attachment.version)
        .putNullable("deleted_at", attachment.deletedAt?.let(::iso))
        .putNullable("storage_path", attachment.cloudPath)

    fun transcription(transcription: TranscriptionEntity, noteUuid: String?, attachmentUuid: String?): JSONObject =
        JSONObject()
            .put("id", transcription.uuid)
            .put("owner_id", transcription.ownerId)
            .putNullable("note_id", noteUuid)
            .putNullable("attachment_id", attachmentUuid)
            .put("language", transcription.language)
            .put("engine_id", transcription.engineId)
            .put("text", transcription.text)
            .put("status", transcription.status)
            .putNullable("error_message", transcription.errorMessage)
            .put("created_at", iso(transcription.createdAt))
            .put("updated_at", iso(transcription.updatedAt))
            .put("version", transcription.version)
            .putNullable("deleted_at", transcription.deletedAt?.let(::iso))

    fun iso(epochMillis: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(epochMillis))
    }

    fun parseIso(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().let {
            when {
                it.endsWith("+00:00") -> it.removeSuffix("+00:00") + "Z"
                else -> it
            }
        }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(normalized)?.time
            }.getOrNull()
        }
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)
}
