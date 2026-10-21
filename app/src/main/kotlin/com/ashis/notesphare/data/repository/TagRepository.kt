package com.ashis.notesphare.data.repository

import com.ashis.notesphare.data.local.NoteDao
import com.ashis.notesphare.data.local.NoteTagDao
import com.ashis.notesphare.data.local.TagDao
import com.ashis.notesphare.data.local.TagEntity
import com.ashis.notesphare.data.local.TagWithCount
import com.ashis.notesphare.domain.DuplicateNameException
import com.ashis.notesphare.sync.LocalSyncWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class TagRepository(
    private val tagDao: TagDao,
    private val noteTagDao: NoteTagDao,
    private val noteDao: NoteDao? = null,
    private val sync: LocalSyncWriter? = null
) {

    fun observeTagsWithCounts(): Flow<List<TagWithCount>> =
        tagDao.observeWithCounts().visible { it.tag.ownerId }

    fun observeTags(): Flow<List<TagEntity>> =
        tagDao.observeAll().visible { it.ownerId }

    suspend fun getTags(): List<TagEntity> =
        tagDao.getAll().filter { canAccess(it.ownerId) }

    suspend fun createTag(name: String, color: Int = 0): Long = write {
        val trimmed = validatedName(name)
        val ownerId = sync?.currentOwnerId()
        if (tagDao.findByNameScoped(trimmed, ownerId) != null) {
            throw DuplicateNameException("tag", trimmed)
        }
        val now = System.currentTimeMillis()
        val id = tagDao.insert(
            TagEntity(
                name = trimmed,
                color = color,
                createdAt = now,
                updatedAt = now,
                ownerId = ownerId
            )
        )
        sync?.enqueueTag(id)
        id
    }

    suspend fun updateTag(tagId: Long, name: String, color: Int) = write {
        val tag = tagDao.getByIdIncludingDeleted(tagId) ?: return@write
        if (tag.deletedAt != null || !canAccess(tag.ownerId)) return@write
        val trimmed = validatedName(name)
        val duplicate = tagDao.findByNameScoped(trimmed, sync?.currentOwnerId())
        if (duplicate != null && duplicate.id != tagId) {
            throw DuplicateNameException("tag", trimmed)
        }
        tagDao.update(tagId, trimmed, color, System.currentTimeMillis())
        sync?.enqueueTag(tagId)
    }

    suspend fun deleteTag(tagId: Long) = write {
        val tag = tagDao.getByIdIncludingDeleted(tagId) ?: return@write
        if (!canAccess(tag.ownerId)) return@write
        val affectedNotes = noteTagDao.getNoteIdsForTag(tagId)
        val now = System.currentTimeMillis()
        tagDao.softDeleteAndDetach(tagId, now)
        sync?.enqueueTag(tagId)
        affectedNotes.forEach { noteId ->
            noteDao?.touch(noteId, now)
            sync?.enqueueNoteTags(noteId)
            sync?.enqueueNote(noteId)
        }
    }

    suspend fun getTagIdsForNote(noteId: Long): Set<Long> =
        noteTagDao.getTagIdsForNote(noteId).toSet()

    suspend fun getTagsForNote(noteId: Long): List<TagEntity> =
        noteTagDao.getTagsForNote(noteId)

    suspend fun setNoteTags(noteId: Long, tagIds: Set<Long>) = write {
        noteDao?.getAnyById(noteId)?.let { if (!canAccess(it.ownerId)) return@write }
        noteTagDao.setTagsForNote(noteId, tagIds.toList())
        noteDao?.touch(noteId, System.currentTimeMillis())
        sync?.enqueueNoteTags(noteId)
        sync?.enqueueNote(noteId)
    }

    private suspend fun canAccess(ownerId: String?): Boolean {
        val owner = sync?.currentOwnerId() ?: return true
        return ownerId == null || ownerId == owner
    }

    private fun <T> Flow<List<T>>.visible(ownerOf: (T) -> String?): Flow<List<T>> =
        combine(this, sync?.ownerScope ?: flowOf(null)) { items, owner ->
            if (owner == null) items else items.filter { ownerOf(it)?.let { it == owner } ?: true }
        }

    private suspend fun <T> write(block: suspend () -> T): T =
        sync?.write(block) ?: block()

    private fun validatedName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Tag name cannot be empty" }
        return trimmed
    }
}
