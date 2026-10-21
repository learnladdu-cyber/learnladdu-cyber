package com.ashis.notesphare.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Query(
        "SELECT * FROM sync_outbox WHERE ownerId = :ownerId AND nextAttemptAt <= :now " +
            "ORDER BY CASE entityType " +
            "WHEN 'FOLDER' THEN 0 WHEN 'TAG' THEN 1 WHEN 'NOTE' THEN 2 " +
            "WHEN 'NOTE_TAGS' THEN 3 WHEN 'CHECKLIST' THEN 4 " +
            "WHEN 'ATTACHMENT' THEN 5 WHEN 'TRANSCRIPTION' THEN 6 ELSE 7 END, " +
            "createdAt ASC LIMIT :limit"
    )
    suspend fun due(ownerId: String, now: Long, limit: Int = 50): List<OutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE entityType = :type AND entityUuid = :uuid LIMIT 1")
    suspend fun getForEntity(type: String, uuid: String): OutboxEntity?

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE ownerId = :ownerId")
    suspend fun count(ownerId: String): Int

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE ownerId = :ownerId")
    fun observeCount(ownerId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observeAllCount(): Flow<Int>

    @Insert
    suspend fun insert(outbox: OutboxEntity): Long

    @Query("DELETE FROM sync_outbox WHERE entityType = :type AND entityUuid = :uuid")
    suspend fun deleteForEntity(type: String, uuid: String)

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query(
        "UPDATE sync_outbox SET attempts = :attempts, nextAttemptAt = :nextAttemptAt, " +
            "lastError = :error WHERE id = :id"
    )
    suspend fun markAttempt(id: Long, attempts: Int, nextAttemptAt: Long, error: String?)

    @Query("DELETE FROM sync_outbox WHERE ownerId = :ownerId")
    suspend fun clearForOwner(ownerId: String)
}

@Dao
interface ConflictDao {

    @Query("SELECT * FROM sync_conflicts WHERE ownerId = :ownerId ORDER BY createdAt DESC")
    fun observeForOwner(ownerId: String): Flow<List<SyncConflictEntity>>

    @Query("SELECT * FROM sync_conflicts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SyncConflictEntity?

    @Query(
        "SELECT * FROM sync_conflicts WHERE ownerId = :ownerId AND entityType = :type " +
            "AND entityUuid = :uuid LIMIT 1"
    )
    suspend fun getForEntity(ownerId: String, type: String, uuid: String): SyncConflictEntity?

    @Query("SELECT COUNT(*) FROM sync_conflicts WHERE ownerId = :ownerId")
    fun observeCount(ownerId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conflict: SyncConflictEntity): Long

    @Query("DELETE FROM sync_conflicts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_conflicts WHERE ownerId = :ownerId")
    suspend fun clearForOwner(ownerId: String)
}

@Dao
interface SyncCursorDao {

    @Query("SELECT * FROM sync_cursors WHERE ownerId = :ownerId AND tableName = :table LIMIT 1")
    suspend fun get(ownerId: String, table: String): SyncCursorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cursor: SyncCursorEntity)

    @Query(
        "INSERT INTO sync_cursors (ownerId, tableName, cursor, lastPulledAt, lastError) " +
            "VALUES (:ownerId, :table, :cursor, :lastPulledAt, NULL) " +
            "ON CONFLICT(ownerId, tableName) DO UPDATE SET cursor = excluded.cursor, " +
            "lastPulledAt = excluded.lastPulledAt, lastError = NULL"
    )
    suspend fun advance(ownerId: String, table: String, cursor: String?, lastPulledAt: Long)

    @Query("UPDATE sync_cursors SET lastError = :error WHERE ownerId = :ownerId AND tableName = :table")
    suspend fun setError(ownerId: String, table: String, error: String?)

    @Query("SELECT MAX(lastPulledAt) FROM sync_cursors WHERE ownerId = :ownerId")
    suspend fun lastPull(ownerId: String): Long?

    @Query("DELETE FROM sync_cursors WHERE ownerId = :ownerId")
    suspend fun clear(ownerId: String)
}

@Dao
interface DeviceRegistrationDao {

    @Query("SELECT * FROM device_registrations WHERE ownerId = :ownerId ORDER BY lastSeenAt DESC")
    fun observeForOwner(ownerId: String): Flow<List<DeviceRegistrationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceRegistrationEntity)

    @Query("DELETE FROM device_registrations WHERE id = :id")
    suspend fun deleteById(id: String)
}
