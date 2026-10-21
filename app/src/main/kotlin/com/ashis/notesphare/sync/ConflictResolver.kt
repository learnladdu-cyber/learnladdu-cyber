package com.ashis.notesphare.sync

import com.ashis.notesphare.data.local.ConflictDao
import com.ashis.notesphare.data.local.OutboxDao
import com.ashis.notesphare.data.local.OutboxEntity
import com.ashis.notesphare.data.local.OutboxOperation
import com.ashis.notesphare.data.local.SyncConflictEntity
import org.json.JSONObject

/**
 * Resolves a stored conflict without guessing. Keeping this device re-queues the exact local
 * payload against the server's current version; keeping cloud applies the saved server row.
 */
class ConflictResolver(
    private val conflictDao: ConflictDao,
    private val outboxDao: OutboxDao,
    private val applier: LocalSyncApplier
) {

    suspend fun keepLocal(conflict: SyncConflictEntity) {
        val remote = runCatching { JSONObject(conflict.remotePayload) }.getOrNull()
        val baseVersion = remote?.optLong("version", 0L) ?: 0L
        val local = JSONObject(conflict.localPayload)
        val operation = if (local.isNull("deleted_at")) {
            OutboxOperation.UPSERT
        } else {
            OutboxOperation.DELETE
        }
        outboxDao.deleteForEntity(conflict.entityType, conflict.entityUuid)
        outboxDao.insert(
            OutboxEntity(
                ownerId = conflict.ownerId,
                entityType = conflict.entityType,
                entityUuid = conflict.entityUuid,
                operation = operation.name,
                payload = conflict.localPayload,
                baseVersion = baseVersion
            )
        )
        conflictDao.deleteById(conflict.id)
    }

    suspend fun keepRemote(conflict: SyncConflictEntity) {
        val remote = runCatching { JSONObject(conflict.remotePayload) }.getOrNull()
        conflictDao.deleteById(conflict.id)
        if (remote != null && remote.length() > 0) {
            applier.applyRemote(conflict.entityType, remote, conflict.ownerId)
        }
    }

    suspend fun dismiss(conflict: SyncConflictEntity) {
        conflictDao.deleteById(conflict.id)
    }
}
