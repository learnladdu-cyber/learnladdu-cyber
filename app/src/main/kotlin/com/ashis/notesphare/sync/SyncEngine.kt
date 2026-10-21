package com.ashis.notesphare.sync

import androidx.room.withTransaction
import com.ashis.notesphare.data.local.ConflictDao
import com.ashis.notesphare.data.local.NoteSphereDatabase
import com.ashis.notesphare.data.local.OutboxDao
import com.ashis.notesphare.data.local.SyncConflictEntity
import com.ashis.notesphare.data.local.SyncCursorDao
import org.json.JSONObject

data class SyncReport(
    val signedIn: Boolean,
    val pushed: Int = 0,
    val pulled: Int = 0,
    val conflicts: Int = 0,
    val failed: Int = 0,
    val message: String? = null
)

/**
 * Drains the outbox, then pulls every server table using a per-table revision cursor.
 * Local mutations are never sent while signed out and failed pushes use capped backoff.
 */
class SyncEngine(
    private val database: NoteSphereDatabase,
    private val outboxDao: OutboxDao,
    private val conflictDao: ConflictDao,
    private val cursorDao: SyncCursorDao,
    private val remote: SyncRemoteDataSource,
    private val applier: LocalSyncApplier,
    private val tokenProvider: suspend () -> String?,
    private val ownerProvider: suspend () -> String?,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun sync(): SyncReport {
        val token = tokenProvider()
            ?: return SyncReport(signedIn = false, message = "Sign in to sync.")
        val owner = ownerProvider()
            ?: return SyncReport(signedIn = false, message = "The session has expired.")
        var pushed = 0
        var pulled = 0
        var conflicts = 0
        var failed = 0

        var batch = 0
        while (batch < PUSH_BATCHES) {
            val due = outboxDao.due(owner, clock(), PUSH_BATCH_SIZE)
            if (due.isEmpty()) break
            due.forEach { item ->
                try {
                    val result = remote.push(
                        accessToken = token,
                        entityType = item.entityType,
                        entityId = item.entityUuid,
                        operation = item.operation,
                        baseVersion = item.baseVersion,
                        payload = JSONObject(item.payload)
                    )
                    when (result.status) {
                        PushStatus.APPLIED -> {
                            outboxDao.deleteById(item.id)
                            pushed++
                        }
                        PushStatus.CONFLICT -> {
                            recordConflict(item, result.remote)
                            outboxDao.deleteById(item.id)
                            conflicts++
                        }
                    }
                } catch (throwable: Throwable) {
                    failed++
                    markFailure(item.id, item.attempts, throwable)
                }
            }
            batch++
        }

        for (table in SupabaseSyncRemote.PULL_TABLES) {
            try {
                var cursor = cursorDao.get(owner, table)?.cursor?.toLongOrNull()
                var pages = 0
                while (pages < MAX_PULL_PAGES) {
                    val page = remote.pull(token, table, cursor, PULL_BATCH_SIZE)
                    if (page.rows.isEmpty()) break
                    val maxRevision = page.rows.maxOf { it.revision }
                    database.withTransaction {
                        page.rows.forEach { row ->
                            runCatching { applier.apply(table, row.json, owner) }
                                .onSuccess { if (it) pulled++ }
                                .onFailure { failed++ }
                        }
                    }
                    cursor = maxRevision
                    cursorDao.advance(owner, table, cursor.toString(), clock())
                    if (page.rows.size < PULL_BATCH_SIZE) break
                    pages++
                }
            } catch (throwable: Throwable) {
                failed++
                runCatching { cursorDao.setError(owner, table, throwable.message) }
                break
            }
        }

        return SyncReport(
            signedIn = true,
            pushed = pushed,
            pulled = pulled,
            conflicts = conflicts,
            failed = failed,
            message = when {
                failed > 0 -> "Some changes could not sync. They will retry."
                conflicts > 0 -> "Some changes conflict with another device."
                else -> null
            }
        )
    }

    private suspend fun recordConflict(item: com.ashis.notesphare.data.local.OutboxEntity, remote: JSONObject?) {
        val payload = runCatching { JSONObject(item.payload) }.getOrNull()
        val remoteUpdatedAt = SyncPayloads.parseIso(remote?.optString("updated_at"))
            ?: clock()
        conflictDao.upsert(
            SyncConflictEntity(
                ownerId = item.ownerId,
                entityType = item.entityType,
                entityUuid = item.entityUuid,
                localPayload = item.payload,
                remotePayload = remote?.toString() ?: "{}",
                localUpdatedAt = SyncPayloads.parseIso(payload?.optString("updated_at")) ?: clock(),
                remoteUpdatedAt = remoteUpdatedAt
            )
        )
    }

    private suspend fun markFailure(outboxId: Long, attempts: Int, throwable: Throwable) {
        val nextAttempts = attempts + 1
        val exponent = (nextAttempts - 1).coerceIn(0, 7)
        val delay = (30_000L * (1L shl exponent)).coerceAtMost(3_600_000L)
        outboxDao.markAttempt(
            id = outboxId,
            attempts = nextAttempts,
            nextAttemptAt = clock() + delay,
            error = throwable.message?.take(500)
        )
    }

    private companion object {
        const val PUSH_BATCH_SIZE = 100
        const val PUSH_BATCHES = 20
        const val PULL_BATCH_SIZE = 200
        const val MAX_PULL_PAGES = 20
    }
}
