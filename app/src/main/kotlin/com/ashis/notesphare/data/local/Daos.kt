package com.ashis.notesphare.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND isArchived = 0 AND isPinned = 1 ORDER BY updatedAt DESC")
    fun observePinned(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND isArchived = 0 AND isPinned = 0 ORDER BY updatedAt DESC")
    fun observeRecent(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND isArchived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun observeTrashed(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isTrashed = 0 AND isArchived = 0 AND folderId = :folderId ORDER BY isPinned DESC, updatedAt DESC")
    fun observeInFolder(folderId: Long): Flow<List<NoteEntity>>

    @Query(
        "SELECT n.* FROM notes n INNER JOIN note_tag_cross_ref r ON n.id = r.noteId " +
            "WHERE r.tagId = :tagId AND n.deletedAt IS NULL AND n.isTrashed = 0 AND n.isArchived = 0 " +
            "ORDER BY n.isPinned DESC, n.updatedAt DESC"
    )
    fun observeWithTag(tagId: Long): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes WHERE deletedAt IS NULL AND isTrashed = 0 AND isArchived = 0 " +
            "AND (title LIKE '%' || :escapedQuery || '%' ESCAPE '\\' " +
            "OR content LIKE '%' || :escapedQuery || '%' ESCAPE '\\') " +
            "ORDER BY isPinned DESC, updatedAt DESC"
    )
    fun search(escapedQuery: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :noteId AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(noteId: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND reminderAt IS NOT NULL AND isTrashed = 0")
    suspend fun getWithReminders(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND isTrashed = 1")
    suspend fun getTrashedNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :noteId LIMIT 1")
    suspend fun getAnyById(noteId: Long): NoteEntity?

    @Query("SELECT * FROM notes ORDER BY updatedAt ASC")
    suspend fun getAllForSync(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE ownerId = :ownerId AND deletedAt IS NULL")
    suspend fun getOwned(ownerId: String): List<NoteEntity>

    @Query("UPDATE notes SET ownerId = :ownerId WHERE ownerId IS NULL")
    suspend fun claimUnowned(ownerId: String)

    @Query("SELECT * FROM notes WHERE folderId = :folderId")
    suspend fun getByFolder(folderId: Long): List<NoteEntity>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("UPDATE notes SET title = :title, content = :content, updatedAt = :now, version = version + 1 WHERE id = :noteId AND deletedAt IS NULL")
    suspend fun updateContent(noteId: Long, title: String, content: String, now: Long)

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :now, version = version + 1 WHERE id = :noteId AND deletedAt IS NULL")
    suspend fun updatePinned(noteId: Long, pinned: Boolean, now: Long)

    @Query("UPDATE notes SET isArchived = :archived, updatedAt = :now, version = version + 1 WHERE id = :noteId AND deletedAt IS NULL")
    suspend fun updateArchived(noteId: Long, archived: Boolean, now: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :now, version = version + 1 WHERE id = :noteId AND deletedAt IS NULL")
    suspend fun updateFolder(noteId: Long, folderId: Long?, now: Long)

    @Query("UPDATE notes SET isTrashed = 1, trashedAt = :now, updatedAt = :now, version = version + 1 WHERE id = :noteId AND deletedAt IS NULL")
    suspend fun markTrashed(noteId: Long, now: Long)

    @Query("UPDATE notes SET isTrashed = 0, trashedAt = NULL, updatedAt = :now, version = version + 1 WHERE id = :noteId AND deletedAt IS NULL")
    suspend fun markRestored(noteId: Long, now: Long)

    @Query("UPDATE notes SET reminderAt = :triggerAt, updatedAt = :now, version = version + 1 WHERE id = :noteId AND deletedAt IS NULL")
    suspend fun updateReminderAt(noteId: Long, triggerAt: Long?, now: Long)

    @Query("UPDATE notes SET updatedAt = :now, version = version + 1 WHERE id = :noteId AND deletedAt IS NULL")
    suspend fun touch(noteId: Long, now: Long)

    @Query("UPDATE notes SET deletedAt = :now, isTrashed = 1, updatedAt = :now, version = version + 1 WHERE id = :noteId")
    suspend fun softDelete(noteId: Long, now: Long)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteById(noteId: Long)

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun deleteAllTrashed()

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL AND isTrashed = 1")
    suspend fun trashedCount(): Int
}

@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId AND deletedAt IS NULL ORDER BY position ASC, id ASC")
    suspend fun getForNote(noteId: Long): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): ChecklistItemEntity?

    @Query("SELECT * FROM checklist_items WHERE id = :itemId LIMIT 1")
    suspend fun getByUuidById(itemId: Long): ChecklistItemEntity?

    @Query("SELECT * FROM checklist_items ORDER BY updatedAt ASC")
    suspend fun getAllForSync(): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE noteId = :noteId")
    suspend fun getAllForNoteIncludingDeleted(noteId: Long): List<ChecklistItemEntity>

    @Query("UPDATE checklist_items SET deletedAt = :now, updatedAt = :now, version = version + 1 WHERE noteId = :noteId")
    suspend fun softDeleteForNote(noteId: Long, now: Long)

    @Query("UPDATE checklist_items SET ownerId = :ownerId WHERE ownerId IS NULL")
    suspend fun claimUnowned(ownerId: String)

    @Update
    suspend fun update(item: ChecklistItemEntity)

    @Query("SELECT COALESCE(MAX(position), -1) FROM checklist_items WHERE noteId = :noteId AND deletedAt IS NULL")
    suspend fun maxPosition(noteId: Long): Int

    @Insert
    suspend fun insert(item: ChecklistItemEntity): Long

    @Query("UPDATE checklist_items SET text = :text, updatedAt = :now, version = version + 1 WHERE id = :itemId AND deletedAt IS NULL")
    suspend fun updateText(itemId: Long, text: String, now: Long)

    @Query("UPDATE checklist_items SET isChecked = :checked, updatedAt = :now, version = version + 1 WHERE id = :itemId AND deletedAt IS NULL")
    suspend fun updateChecked(itemId: Long, checked: Boolean, now: Long)

    @Query("UPDATE checklist_items SET deletedAt = :now, updatedAt = :now, version = version + 1 WHERE id = :itemId")
    suspend fun softDelete(itemId: Long, now: Long)

    @Query("DELETE FROM checklist_items WHERE id = :itemId")
    suspend fun deleteById(itemId: Long)
}

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): FolderEntity?

    @Query("SELECT * FROM folders ORDER BY updatedAt ASC")
    suspend fun getAllForSync(): List<FolderEntity>

    @Query("UPDATE folders SET ownerId = :ownerId WHERE ownerId IS NULL")
    suspend fun claimUnowned(ownerId: String)

    @Update
    suspend fun updateEntity(folder: FolderEntity)

    @Query(
        "SELECT f.*, (SELECT COUNT(*) FROM notes n WHERE n.folderId = f.id " +
            "AND n.isTrashed = 0 AND n.isArchived = 0) AS noteCount " +
            "FROM folders f WHERE f.deletedAt IS NULL ORDER BY f.name COLLATE NOCASE ASC"
    )
    fun observeWithCounts(): Flow<List<FolderWithCount>>

    @Query("SELECT * FROM folders WHERE id = :folderId AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(folderId: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE name = :name COLLATE NOCASE AND deletedAt IS NULL LIMIT 1")
    suspend fun findByName(name: String): FolderEntity?

    @Query(
        "SELECT * FROM folders WHERE name = :name COLLATE NOCASE AND deletedAt IS NULL " +
            "AND (ownerId IS NULL OR :ownerId IS NULL OR ownerId = :ownerId) LIMIT 1"
    )
    suspend fun findByNameScoped(name: String, ownerId: String?): FolderEntity?

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name, color = :color, updatedAt = :now, version = version + 1 WHERE id = :folderId AND deletedAt IS NULL")
    suspend fun update(folderId: Long, name: String, color: Int, now: Long)

    @Query("UPDATE folders SET deletedAt = :now, updatedAt = :now, version = version + 1, name = name || ' (deleted ' || substr(uuid, 1, 8) || ')' WHERE id = :folderId")
    suspend fun softDelete(folderId: Long, now: Long)

    @Query("UPDATE notes SET folderId = NULL, updatedAt = :now, version = version + 1 WHERE folderId = :folderId AND deletedAt IS NULL")
    suspend fun clearNotesInFolder(folderId: Long, now: Long)

    @Transaction
    suspend fun softDeleteAndClearNotes(folderId: Long, now: Long) {
        clearNotesInFolder(folderId, now)
        softDelete(folderId, now)
    }

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun delete(folderId: Long)
}

@Dao
interface TagDao {

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :tagId LIMIT 1")
    suspend fun getByIdIncludingDeleted(tagId: Long): TagEntity?

    @Query("SELECT * FROM tags ORDER BY updatedAt ASC")
    suspend fun getAllForSync(): List<TagEntity>

    @Query("UPDATE tags SET ownerId = :ownerId WHERE ownerId IS NULL")
    suspend fun claimUnowned(ownerId: String)

    @Update
    suspend fun updateEntity(tag: TagEntity)

    @Query(
        "SELECT t.*, (SELECT COUNT(*) FROM note_tag_cross_ref r INNER JOIN notes n ON n.id = r.noteId " +
            "WHERE r.tagId = t.id AND n.isTrashed = 0 AND n.isArchived = 0) AS noteCount " +
            "FROM tags t WHERE t.deletedAt IS NULL ORDER BY t.name COLLATE NOCASE ASC"
    )
    fun observeWithCounts(): Flow<List<TagWithCount>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE AND deletedAt IS NULL LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Query(
        "SELECT * FROM tags WHERE name = :name COLLATE NOCASE AND deletedAt IS NULL " +
            "AND (ownerId IS NULL OR :ownerId IS NULL OR ownerId = :ownerId) LIMIT 1"
    )
    suspend fun findByNameScoped(name: String, ownerId: String?): TagEntity?

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("UPDATE tags SET name = :name, color = :color, updatedAt = :now, version = version + 1 WHERE id = :tagId AND deletedAt IS NULL")
    suspend fun update(tagId: Long, name: String, color: Int, now: Long)

    @Query("UPDATE tags SET deletedAt = :now, updatedAt = :now, version = version + 1, name = name || ' (deleted ' || substr(uuid, 1, 8) || ')' WHERE id = :tagId")
    suspend fun softDelete(tagId: Long, now: Long)

    @Query("DELETE FROM note_tag_cross_ref WHERE tagId = :tagId")
    suspend fun clearCrossRefs(tagId: Long)

    @Transaction
    suspend fun softDeleteAndDetach(tagId: Long, now: Long) {
        clearCrossRefs(tagId)
        softDelete(tagId, now)
    }

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun delete(tagId: Long)
}

@Dao
interface NoteTagDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tag_cross_ref WHERE noteId = :noteId")
    suspend fun clearForNote(noteId: Long)

    @Query("DELETE FROM note_tag_cross_ref WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun deleteRelation(noteId: Long, tagId: Long)

    @Query("SELECT tagId FROM note_tag_cross_ref WHERE noteId = :noteId")
    suspend fun getTagIdsForNote(noteId: Long): List<Long>

    @Query("SELECT noteId FROM note_tag_cross_ref WHERE tagId = :tagId")
    suspend fun getNoteIdsForTag(tagId: Long): List<Long>

    @Query(
        "SELECT t.* FROM tags t INNER JOIN note_tag_cross_ref r ON t.id = r.tagId " +
            "WHERE r.noteId = :noteId AND t.deletedAt IS NULL ORDER BY t.name COLLATE NOCASE ASC"
    )
    suspend fun getTagsForNote(noteId: Long): List<TagEntity>

    @Transaction
    suspend fun setTagsForNote(noteId: Long, tagIds: List<Long>) {
        clearForNote(noteId)
        if (tagIds.isNotEmpty()) {
            insertCrossRefs(tagIds.distinct().map { NoteTagCrossRef(noteId = noteId, tagId = it) })
        }
    }
}
