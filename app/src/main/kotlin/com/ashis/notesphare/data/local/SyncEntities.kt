package com.ashis.notesphare.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["entityType", "entityUuid"], unique = true),
        Index(value = ["ownerId", "nextAttemptAt"]),
        Index("createdAt")
    ]
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: String,
    val entityType: String,
    val entityUuid: String,
    val operation: String,
    val payload: String,
    val baseVersion: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0L,
    val lastError: String? = null
)

@Entity(
    tableName = "sync_conflicts",
    indices = [
        Index(value = ["ownerId", "entityType", "entityUuid"], unique = true),
        Index("createdAt")
    ]
)
data class SyncConflictEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: String,
    val entityType: String,
    val entityUuid: String,
    val localPayload: String,
    val remotePayload: String,
    val localUpdatedAt: Long,
    val remoteUpdatedAt: Long,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sync_cursors", primaryKeys = ["ownerId", "tableName"])
data class SyncCursorEntity(
    val ownerId: String,
    val tableName: String,
    val cursor: String?,
    val lastPulledAt: Long?,
    val lastError: String? = null
)

@Entity(
    tableName = "device_registrations",
    indices = [Index(value = ["ownerId", "deviceId"], unique = true)]
)
data class DeviceRegistrationEntity(
    @PrimaryKey val id: String,
    val ownerId: String,
    val deviceId: String,
    val deviceName: String,
    val platform: String = "android",
    val lastSeenAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class OutboxOperation {
    UPSERT,
    DELETE
}

enum class SyncEntityType {
    NOTE,
    FOLDER,
    TAG,
    CHECKLIST,
    NOTE_TAGS,
    ATTACHMENT,
    TRANSCRIPTION
}

enum class AttachmentSyncStatus {
    LOCAL_ONLY,
    PENDING_UPLOAD,
    UPLOADING,
    SYNCED,
    PENDING_DOWNLOAD,
    DOWNLOADING,
    FAILED,
    MISSING_REMOTE
}
