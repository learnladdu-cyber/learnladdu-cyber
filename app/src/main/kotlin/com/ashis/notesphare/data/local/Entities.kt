package com.ashis.notesphare.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["ownerId", "name"], unique = true)
    ]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    val ownerId: String? = null,
    @ColumnInfo(defaultValue = "1") val version: Long = 1L,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["ownerId", "name"], unique = true)
    ]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    val ownerId: String? = null,
    @ColumnInfo(defaultValue = "1") val version: Long = 1L,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("folderId"),
        Index("isPinned"),
        Index("isArchived"),
        Index("isTrashed"),
        Index("updatedAt"),
        Index("reminderAt"),
        Index(value = ["uuid"], unique = true),
        Index("ownerId")
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val folderId: Long? = null,
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val reminderAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    val ownerId: String? = null,
    @ColumnInfo(defaultValue = "1") val version: Long = 1L,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "checklist_items",
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
        Index(value = ["noteId", "position"]),
        Index(value = ["uuid"], unique = true),
        Index("ownerId")
    ]
)
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val text: String,
    @ColumnInfo(defaultValue = "0") val isChecked: Boolean = false,
    val position: Int,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "''") val uuid: String = UUID.randomUUID().toString(),
    val ownerId: String? = null,
    @ColumnInfo(defaultValue = "1") val version: Long = 1L,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "note_tag_cross_ref",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tagId")]
)
data class NoteTagCrossRef(
    val noteId: Long,
    val tagId: Long
)
