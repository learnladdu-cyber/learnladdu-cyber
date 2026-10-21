package com.ashis.notesphare.sync

import com.ashis.notesphare.data.local.AttachmentDao
import com.ashis.notesphare.data.local.AttachmentSyncStatus
import com.ashis.notesphare.data.local.ConflictDao
import com.ashis.notesphare.data.local.OutboxDao
import com.ashis.notesphare.data.local.SyncEntityType
import com.ashis.notesphare.files.FileStorageManager

data class AttachmentSyncReport(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val failed: Int = 0
)

class AttachmentSyncManager(
    private val attachmentDao: AttachmentDao,
    private val outboxDao: OutboxDao,
    private val conflictDao: ConflictDao,
    private val storage: FileStorageManager,
    private val remote: StorageRemoteDataSource,
    private val writer: LocalSyncWriter,
    private val bucket: String,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun uploadPending(ownerId: String, token: String): List<Long> {
        val uploadedIds = mutableListOf<Long>()
        attachmentDao.getForUpload(ownerId).forEach { attachment ->
            val file = storage.resolveManagedFile(attachment.storedPath)
            if (file == null || !file.isFile || file.length() <= 0L) {
                attachmentDao.update(
                    attachment.copy(
                        syncStatus = AttachmentSyncStatus.FAILED.name,
                        syncError = "The local attachment file is missing.",
                        updatedAt = clock()
                    )
                )
                return@forEach
            }
            val path = attachment.cloudPath ?: objectPath(ownerId, attachment)
            try {
                remote.upload(token, bucket, path, file, attachment.mimeType)
                writer.write {
                    attachmentDao.update(
                        attachment.copy(
                            cloudPath = path,
                            syncStatus = AttachmentSyncStatus.PENDING_UPLOAD.name,
                            syncError = null,
                            updatedAt = clock(),
                            version = attachment.version + 1
                        )
                    )
                    writer.enqueueAttachment(attachment.id)
                }
                uploadedIds += attachment.id
            } catch (throwable: Throwable) {
                attachmentDao.update(
                    attachment.copy(
                        syncStatus = AttachmentSyncStatus.FAILED.name,
                        syncError = throwable.message?.take(300),
                        updatedAt = clock()
                    )
                )
            }
        }
        return uploadedIds
    }

    /**
     * Called only after a successful metadata flush. A row is marked SYNCED only when its
     * attachment outbox entry is gone and no conflict remains.
     */
    suspend fun confirmUploaded(attachmentIds: List<Long>) {
        attachmentIds.forEach { id ->
            val attachment = attachmentDao.getAnyById(id) ?: return@forEach
            val uuid = attachment.uuid
            if (outboxDao.getForEntity(SyncEntityType.ATTACHMENT.name, uuid) != null) return@forEach
            if (conflictDao.getForEntity(attachment.ownerId ?: return@forEach, SyncEntityType.ATTACHMENT.name, uuid) != null) {
                return@forEach
            }
            attachmentDao.update(
                attachment.copy(
                    syncStatus = AttachmentSyncStatus.SYNCED.name,
                    syncError = null,
                    remoteUpdatedAt = clock()
                )
            )
        }
    }

    suspend fun downloadPending(ownerId: String, token: String): Int {
        var downloaded = 0
        attachmentDao.getForDownload(ownerId).forEach { attachment ->
            val cloudPath = attachment.cloudPath ?: return@forEach
            val target = storage.resolveManagedFile(attachment.storedPath)
                ?: return@forEach
            try {
                if (target.isFile && target.length() > 0L &&
                    (attachment.sizeBytes <= 0L || target.length() == attachment.sizeBytes)
                ) {
                    attachmentDao.update(
                        attachment.copy(
                            syncStatus = AttachmentSyncStatus.SYNCED.name,
                            syncError = null,
                            remoteUpdatedAt = clock()
                        )
                    )
                    downloaded++
                    return@forEach
                }
                remote.download(token, bucket, cloudPath, target)
                if (attachment.sizeBytes > 0L && target.length() != attachment.sizeBytes) {
                    throw SyncException("The downloaded attachment size did not match its metadata.")
                }
                attachmentDao.update(
                    attachment.copy(
                        syncStatus = AttachmentSyncStatus.SYNCED.name,
                        syncError = null,
                        sizeBytes = target.length(),
                        remoteUpdatedAt = clock()
                    )
                )
                downloaded++
            } catch (throwable: Throwable) {
                attachmentDao.update(
                    attachment.copy(
                        syncError = throwable.message?.take(300),
                        remoteUpdatedAt = clock()
                    )
                )
            }
        }
        return downloaded
    }

    private fun objectPath(ownerId: String, attachment: com.ashis.notesphare.data.local.AttachmentEntity): String {
        val safeName = storage.sanitizeDisplayName(attachment.displayName)
        return "$ownerId/${attachment.uuid}/$safeName"
    }
}
