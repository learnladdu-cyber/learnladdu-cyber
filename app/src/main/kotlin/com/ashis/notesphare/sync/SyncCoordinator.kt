package com.ashis.notesphare.sync

import com.ashis.notesphare.data.local.AttachmentDao

/**
 * Runs metadata sync around attachment transfer. Metadata is pushed first, uploaded attachment
 * paths are pushed again, and downloads happen only after the pull has produced local metadata.
 */
class SyncCoordinator(
    private val engine: SyncEngine,
    private val attachments: AttachmentSyncManager,
    private val attachmentDao: AttachmentDao,
    private val tokenProvider: suspend () -> String?,
    private val ownerProvider: suspend () -> String?,
    private val refreshReminders: suspend () -> Unit = {}
) {

    suspend fun syncNow(): SyncReport {
        val first = engine.sync()
        if (!first.signedIn) return first
        val token = tokenProvider() ?: return first
        val owner = ownerProvider() ?: return first

        val uploaded = runCatching { attachments.uploadPending(owner, token) }
            .getOrDefault(emptyList())
        var second = SyncReport(signedIn = true)
        if (uploaded.isNotEmpty()) {
            second = engine.sync()
            if (second.failed == 0) {
                runCatching { attachments.confirmUploaded(uploaded) }
            }
        }
        runCatching { attachments.downloadPending(owner, token) }
        if (first.pulled + second.pulled > 0) {
            runCatching { refreshReminders() }
        }
        val failedAttachments = runCatching { attachmentDao.countFailed(owner) }.getOrDefault(0)
        val failed = first.failed + second.failed + failedAttachments
        return SyncReport(
            signedIn = true,
            pushed = first.pushed + second.pushed,
            pulled = first.pulled + second.pulled,
            conflicts = first.conflicts + second.conflicts,
            failed = failed,
            message = when {
                failed > 0 -> "Some changes could not sync. They will retry."
                first.conflicts + second.conflicts > 0 ->
                    "Some changes conflict with another device."
                else -> null
            }
        )
    }
}
