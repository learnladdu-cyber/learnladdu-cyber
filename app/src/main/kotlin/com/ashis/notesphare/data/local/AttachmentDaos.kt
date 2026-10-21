package com.ashis.notesphare.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE noteId = :noteId AND deletedAt IS NULL ORDER BY createdAt ASC, id ASC")
    fun observeForNote(noteId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId AND deletedAt IS NULL ORDER BY createdAt ASC, id ASC")
    suspend fun getForNote(noteId: Long): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE deletedAt IS NULL ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE deletedAt IS NULL ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :attachmentId AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(attachmentId: Long): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE id = :attachmentId LIMIT 1")
    suspend fun getAnyById(attachmentId: Long): AttachmentEntity?

    @Query("SELECT * FROM attachments WHERE deletedAt IS NULL ORDER BY updatedAt ASC")
    suspend fun getAllForSync(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments ORDER BY updatedAt ASC")
    suspend fun getAllIncludingDeleted(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId")
    suspend fun getAllForNoteIncludingDeleted(noteId: Long): List<AttachmentEntity>

    @Query("UPDATE attachments SET ownerId = :ownerId, syncStatus = 'PENDING_UPLOAD' WHERE ownerId IS NULL AND deletedAt IS NULL")
    suspend fun claimUnowned(ownerId: String)

    @Query("SELECT * FROM attachments WHERE ownerId = :ownerId AND deletedAt IS NULL AND syncStatus != 'SYNCED'")
    suspend fun getPendingForOwner(ownerId: String): List<AttachmentEntity>

    @Query(
        "SELECT * FROM attachments WHERE ownerId = :ownerId AND deletedAt IS NULL " +
            "AND syncStatus IN ('LOCAL_ONLY','PENDING_UPLOAD','FAILED') ORDER BY updatedAt ASC"
    )
    suspend fun getForUpload(ownerId: String): List<AttachmentEntity>

    @Query(
        "SELECT * FROM attachments WHERE ownerId = :ownerId AND deletedAt IS NULL " +
            "AND syncStatus IN ('PENDING_DOWNLOAD','MISSING_REMOTE') ORDER BY updatedAt ASC"
    )
    suspend fun getForDownload(ownerId: String): List<AttachmentEntity>

    @Update
    suspend fun update(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE storedPath = :storedPath AND deletedAt IS NULL LIMIT 1")
    suspend fun getByPath(storedPath: String): AttachmentEntity?

    @Query("SELECT COUNT(*) FROM attachments WHERE storedPath = :storedPath AND deletedAt IS NULL")
    suspend fun countByPath(storedPath: String): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM attachments WHERE deletedAt IS NULL")
    suspend fun totalSizeBytes(): Long

    @Query("SELECT COUNT(*) FROM attachments WHERE deletedAt IS NULL")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM attachments WHERE ownerId = :ownerId AND deletedAt IS NULL AND syncStatus = 'FAILED'")
    suspend fun countFailed(ownerId: String): Int

    @Insert
    suspend fun insert(attachment: AttachmentEntity): Long

    @Query("UPDATE attachments SET deletedAt = :now, updatedAt = :now, version = version + 1 WHERE id = :attachmentId")
    suspend fun softDelete(attachmentId: Long, now: Long)

    @Query("UPDATE attachments SET deletedAt = :now, updatedAt = :now, version = version + 1 WHERE noteId = :noteId")
    suspend fun softDeleteForNote(noteId: Long, now: Long)

    @Query("DELETE FROM attachments WHERE id = :attachmentId")
    suspend fun deleteById(attachmentId: Long)

    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}

@Dao
interface TranscriptionDao {

    @Query("SELECT * FROM transcriptions WHERE noteId = :noteId AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeForNote(noteId: Long): Flow<List<TranscriptionEntity>>

    @Query("SELECT * FROM transcriptions WHERE attachmentId = :attachmentId AND deletedAt IS NULL LIMIT 1")
    fun observeForAttachment(attachmentId: Long): Flow<TranscriptionEntity?>

    @Query("SELECT * FROM transcriptions WHERE attachmentId = :attachmentId AND deletedAt IS NULL LIMIT 1")
    suspend fun getForAttachment(attachmentId: Long): TranscriptionEntity?

    @Query("SELECT * FROM transcriptions WHERE id = :transcriptionId AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(transcriptionId: Long): TranscriptionEntity?

    @Query("SELECT * FROM transcriptions WHERE attachmentId = :attachmentId LIMIT 1")
    suspend fun getAnyForAttachment(attachmentId: Long): TranscriptionEntity?

    @Query("SELECT * FROM transcriptions WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): TranscriptionEntity?

    @Query("SELECT * FROM transcriptions WHERE deletedAt IS NULL ORDER BY updatedAt ASC")
    suspend fun getAllForSync(): List<TranscriptionEntity>

    @Query("SELECT * FROM transcriptions ORDER BY updatedAt ASC")
    suspend fun getAllIncludingDeleted(): List<TranscriptionEntity>

    @Query("SELECT * FROM transcriptions WHERE noteId = :noteId")
    suspend fun getAllForNoteIncludingDeleted(noteId: Long): List<TranscriptionEntity>

    @Query("UPDATE transcriptions SET deletedAt = :now, updatedAt = :now, version = version + 1 WHERE noteId = :noteId")
    suspend fun softDeleteForNote(noteId: Long, now: Long)

    @Query("UPDATE transcriptions SET ownerId = :ownerId WHERE ownerId IS NULL")
    suspend fun claimUnowned(ownerId: String)

    @Update
    suspend fun update(transcription: TranscriptionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transcription: TranscriptionEntity): Long

    @Query(
        "UPDATE transcriptions SET language = :language, engineId = :engineId, text = :text, " +
            "status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt, " +
            "version = version + 1 WHERE attachmentId = :attachmentId AND deletedAt IS NULL"
    )
    suspend fun updateForAttachment(
        attachmentId: Long,
        language: String,
        engineId: String,
        text: String,
        status: String,
        errorMessage: String?,
        updatedAt: Long
    )

    @Query("UPDATE transcriptions SET text = :text, updatedAt = :updatedAt, version = version + 1 WHERE attachmentId = :attachmentId AND deletedAt IS NULL")
    suspend fun updateText(attachmentId: Long, text: String, updatedAt: Long)

    @Query("UPDATE transcriptions SET deletedAt = :now, updatedAt = :now, version = version + 1 WHERE attachmentId = :attachmentId")
    suspend fun softDeleteForAttachment(attachmentId: Long, now: Long)

    @Query("DELETE FROM transcriptions WHERE attachmentId = :attachmentId")
    suspend fun deleteForAttachment(attachmentId: Long)
}

@Dao
interface RecordingSessionDao {

    @Query("SELECT * FROM recording_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions ORDER BY startedAt DESC")
    suspend fun getAll(): List<RecordingSessionEntity>

    @Query("SELECT * FROM recording_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getById(sessionId: String): RecordingSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: RecordingSessionEntity)

    @Query(
        "UPDATE recording_sessions SET durationMs = :durationMs, state = :state, " +
            "waveform = :waveform, updatedAt = :updatedAt WHERE id = :sessionId"
    )
    suspend fun update(
        sessionId: String,
        durationMs: Long,
        state: String,
        waveform: String?,
        updatedAt: Long
    )

    @Query("DELETE FROM recording_sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: String)
}
