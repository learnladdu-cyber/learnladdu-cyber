package com.ashis.notesphare.data.repository

import android.content.Context
import android.net.Uri
import com.ashis.notesphare.data.local.AttachmentDao
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.local.NoteDao
import com.ashis.notesphare.data.local.RecordingSessionDao
import com.ashis.notesphare.files.FileStorageManager
import com.ashis.notesphare.files.StorageException
import com.ashis.notesphare.sync.LocalSyncWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

data class AttachmentImportSummary(
    val added: List<AttachmentEntity> = emptyList(),
    val errors: List<String> = emptyList()
)

data class StorageStats(
    val attachmentBytes: Long = 0,
    val attachmentCount: Int = 0,
    val managedFileBytes: Long = 0,
    val missingCount: Int = 0,
    val orphanCount: Int = 0,
    val orphanBytes: Long = 0
)

data class CleanupReport(
    val deletedFiles: Int = 0,
    val deletedBytes: Long = 0,
    val failures: Int = 0
)

class AttachmentRepository(
    private val attachmentDao: AttachmentDao,
    private val recordingSessionDao: RecordingSessionDao,
    private val noteDao: NoteDao,
    val storage: FileStorageManager,
    private val sync: LocalSyncWriter? = null
) {

    fun observeAttachments(noteId: Long): Flow<List<AttachmentEntity>> =
        attachmentDao.observeForNote(noteId)

    fun observeAllAttachments(): Flow<List<AttachmentEntity>> = attachmentDao.observeAll()

    suspend fun getAttachment(attachmentId: Long): AttachmentEntity? = attachmentDao.getById(attachmentId)

    suspend fun getAllAttachments(): List<AttachmentEntity> = attachmentDao.getAll()

    fun fileFor(attachment: AttachmentEntity): File? = storage.resolveManagedFile(attachment.storedPath)

    fun isMissing(attachment: AttachmentEntity): Boolean = !storage.exists(attachment.storedPath)

    suspend fun importUris(noteId: Long, uris: List<Uri>): AttachmentImportSummary {
        if (noteDao.getById(noteId) == null) {
            return AttachmentImportSummary(errors = listOf("This note no longer exists."))
        }
        val added = mutableListOf<AttachmentEntity>()
        val errors = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            uris.distinct().forEach { uri ->
                try {
                    val imported = storage.importFromUri(noteId, uri)
                    try {
                        val entity = AttachmentEntity(
                            noteId = noteId,
                            displayName = imported.displayName,
                            mimeType = imported.mimeType,
                            kind = imported.kind.name,
                            storedPath = imported.relativePath,
                            sizeBytes = imported.sizeBytes,
                            ownerId = sync?.currentOwnerId()
                        )
                        val id = attachmentDao.insert(entity)
                        added += entity.copy(id = id)
                        noteDao.touch(noteId, System.currentTimeMillis())
                        sync?.enqueueNote(noteId)
                        sync?.enqueueAttachment(id)
                    } catch (throwable: Throwable) {
                        storage.deleteManagedFile(imported.relativePath)
                        throw throwable
                    }
                } catch (throwable: StorageException) {
                    errors += throwable.message ?: "Could not attach this file."
                } catch (throwable: Throwable) {
                    errors += throwable.message ?: "Could not attach this file."
                }
            }
        }
        return AttachmentImportSummary(added, errors)
    }

    /**
     * The file is deleted only when no row still points at the same stored path. This protects
     * against a retried import or a recovered session sharing the file.
     */
    suspend fun deleteAttachment(attachmentId: Long): Boolean = withContext(Dispatchers.IO) {
        val attachment = attachmentDao.getById(attachmentId) ?: return@withContext false
        val operation = suspend {
            attachmentDao.softDelete(attachmentId, System.currentTimeMillis())
            val remainingReferences = attachmentDao.countByPath(attachment.storedPath)
            if (remainingReferences == 0) storage.deleteManagedFile(attachment.storedPath)
            noteDao.touch(attachment.noteId, System.currentTimeMillis())
            sync?.enqueueNote(attachment.noteId)
            sync?.enqueueAttachment(attachmentId)
        }
        if (sync != null) sync.write(operation) else operation()
        true
    }

    suspend fun storageStats(): StorageStats = withContext(Dispatchers.IO) {
        val attachments = attachmentDao.getAll()
        val referenced = attachments.mapTo(mutableSetOf()) { it.storedPath }
        val now = System.currentTimeMillis()
        val orphanCandidates = storage.listOrphanCandidates(
            referencedPaths = referenced,
            olderThan = now - FileStorageManager.ORPHAN_GRACE_PERIOD_MS
        )
        StorageStats(
            attachmentBytes = attachmentDao.totalSizeBytes(),
            attachmentCount = attachments.size,
            managedFileBytes = storage.totalManagedBytes(),
            missingCount = attachments.count { !storage.exists(it.storedPath) },
            orphanCount = orphanCandidates.size,
            orphanBytes = orphanCandidates.sumOf { it.length() }
        )
    }

    suspend fun cleanupOrphans(): CleanupReport = withContext(Dispatchers.IO) {
        val referenced = attachmentDao.getAll().mapTo(mutableSetOf()) { it.storedPath }
        recordingSessionDao.getAll().forEach { referenced += it.storedPath }
        val candidates = storage.listOrphanCandidates(
            referencedPaths = referenced,
            olderThan = System.currentTimeMillis() - FileStorageManager.ORPHAN_GRACE_PERIOD_MS
        )
        var deleted = 0
        var bytes = 0L
        var failures = 0
        candidates.forEach { file ->
            val length = file.length()
            if (file.delete()) {
                deleted++
                bytes += length
            } else {
                failures++
            }
        }
        storage.cleanupEmptyDirectories()
        CleanupReport(deletedFiles = deleted, deletedBytes = bytes, failures = failures)
    }
}
