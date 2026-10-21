package com.ashis.notesphare.data.repository

import com.ashis.notesphare.data.local.AttachmentDao
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.local.NoteDao
import com.ashis.notesphare.data.local.RecordingSessionDao
import com.ashis.notesphare.data.local.RecordingSessionEntity
import com.ashis.notesphare.domain.AttachmentKind
import com.ashis.notesphare.domain.RecordingSessionState
import com.ashis.notesphare.files.FileStorageManager
import com.ashis.notesphare.files.StorageException
import com.ashis.notesphare.recording.AudioMetadataReader
import com.ashis.notesphare.sync.LocalSyncWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class RecordingRepository(
    private val recordingSessionDao: RecordingSessionDao,
    private val attachmentDao: AttachmentDao,
    private val noteDao: NoteDao,
    private val storage: FileStorageManager,
    private val audioMetadataReader: AudioMetadataReader,
    private val sync: LocalSyncWriter? = null
) {

    fun observeSessions(): Flow<List<RecordingSessionEntity>> = recordingSessionDao.observeAll()

    suspend fun getSession(sessionId: String): RecordingSessionEntity? =
        recordingSessionDao.getById(sessionId)

    suspend fun recoverableSessions(): List<RecordingSessionEntity> =
        recordingSessionDao.getAll().filter { it.state != RecordingSessionState.SAVED.name }

    suspend fun createSession(noteId: Long): RecordingSessionEntity = withContext(Dispatchers.IO) {
        if (noteDao.getById(noteId) == null) throw StorageException("This note no longer exists.")
        val file = storage.newRecordingTempFile()
        val relative = storage.relativePath(file)
            ?: throw StorageException("Could not create a safe recording path.")
        val now = System.currentTimeMillis()
        val session = RecordingSessionEntity(
            id = UUID.randomUUID().toString(),
            noteId = noteId,
            storedPath = relative,
            startedAt = now,
            durationMs = 0L,
            state = RecordingSessionState.RECORDING.name,
            updatedAt = now
        )
        recordingSessionDao.upsert(session)
        session
    }

    suspend fun updateSession(
        sessionId: String,
        durationMs: Long,
        state: RecordingSessionState,
        waveform: String?
    ) = withContext(Dispatchers.IO) {
        recordingSessionDao.update(
            sessionId = sessionId,
            durationMs = durationMs.coerceAtLeast(0L),
            state = state.name,
            waveform = waveform,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Validates and finalises an interrupted or stopped recording, inserting the audio attachment
     * only once. The unique [AttachmentEntity.storedPath] index makes retries idempotent.
     */
    suspend fun saveSession(sessionId: String): Long = withContext(Dispatchers.IO) {
        if (sync != null) {
            sync.write { saveSessionInTransaction(sessionId) }
        } else {
            saveSessionInTransaction(sessionId)
        }
    }

    private suspend fun saveSessionInTransaction(sessionId: String): Long {
        val session = recordingSessionDao.getById(sessionId)
            ?: throw StorageException("The recording session no longer exists.")
        val tempFile = storage.resolveManagedFile(session.storedPath)
            ?: throw StorageException("The recording file path is invalid.")
        if (!tempFile.isFile || tempFile.length() <= 0L) {
            throw StorageException("The recording file is missing or empty.")
        }
        val measuredDuration = audioMetadataReader.durationMs(tempFile)
            ?: throw StorageException("The recording is incomplete and cannot be played.")
        val finalFile = storage.finalizeRecordingFile(tempFile)
        val relative = storage.relativePath(finalFile)
            ?: throw StorageException("The recording could not be stored safely.")
        val duration = measuredDuration.coerceAtLeast(session.durationMs)
        val readySession = session.copy(
            storedPath = relative,
            durationMs = duration,
            state = RecordingSessionState.SAVED.name,
            updatedAt = System.currentTimeMillis()
        )
        recordingSessionDao.upsert(readySession)
        val existing = attachmentDao.getByPath(relative)
        val attachmentId = if (existing != null) {
            existing.id
        } else {
            val attachment = AttachmentEntity(
                noteId = session.noteId,
                displayName = "Recording ${formatRecordingName(System.currentTimeMillis())}",
                mimeType = "audio/mp4",
                kind = AttachmentKind.AUDIO.name,
                storedPath = relative,
                sizeBytes = finalFile.length(),
                durationMs = duration,
                waveform = readySession.waveform,
                createdAt = System.currentTimeMillis(),
                ownerId = sync?.currentOwnerId()
            )
            attachmentDao.insert(attachment)
        }
        recordingSessionDao.deleteById(sessionId)
        noteDao.touch(session.noteId, System.currentTimeMillis())
        sync?.enqueueNote(session.noteId)
        sync?.enqueueAttachment(attachmentId)
        return attachmentId
    }

    suspend fun discardSession(sessionId: String) = withContext(Dispatchers.IO) {
        val session = recordingSessionDao.getById(sessionId) ?: return@withContext
        storage.deleteManagedFile(session.storedPath)
        recordingSessionDao.deleteById(sessionId)
    }

    suspend fun deleteFailedSession(sessionId: String) = discardSession(sessionId)

    suspend fun pendingCount(): Int = recoverableSessions().size

    fun fileFor(session: RecordingSessionEntity): File? = storage.resolveManagedFile(session.storedPath)

    private fun formatRecordingName(timestamp: Long): String =
        java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))
}
