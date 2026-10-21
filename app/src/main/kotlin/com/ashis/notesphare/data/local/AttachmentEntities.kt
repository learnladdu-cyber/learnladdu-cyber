package com.ashis.notesphare.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("noteId"),
        Index(value = ["storedPath"], unique = true),
        Index("kind"),
        Index("createdAt"),
        Index(value = ["uuid"], unique = true),
        Index("ownerId")
    ]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val displayName: String,
    val mimeType: String,
    val kind: String,
    val storedPath: String,
    val sizeBytes: Long,
    val durationMs: Long? = null,
    val waveform: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    val ownerId: String? = null,
    @ColumnInfo(defaultValue = "1") val version: Long = 1L,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    val cloudPath: String? = null,
    @ColumnInfo(defaultValue = "'LOCAL_ONLY'") val syncStatus: String = "LOCAL_ONLY",
    val syncError: String? = null,
    val remoteUpdatedAt: Long? = null
)

@Entity(
    tableName = "transcriptions",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AttachmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["attachmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("noteId"),
        Index(value = ["attachmentId"], unique = true),
        Index(value = ["uuid"], unique = true),
        Index("ownerId")
    ]
)
data class TranscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val attachmentId: Long,
    val language: String,
    val engineId: String,
    val text: String = "",
    val status: String = "PENDING",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    val ownerId: String? = null,
    @ColumnInfo(defaultValue = "1") val version: Long = 1L,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "recording_sessions",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("noteId"),
        Index("state"),
        Index("startedAt")
    ]
)
data class RecordingSessionEntity(
    @PrimaryKey val id: String,
    val noteId: Long,
    val storedPath: String,
    val startedAt: Long,
    @ColumnInfo(defaultValue = "0") val durationMs: Long = 0,
    val state: String = "RECORDING",
    val waveform: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
