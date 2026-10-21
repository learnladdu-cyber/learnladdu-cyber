package com.ashis.notesphare.data.repository

import com.ashis.notesphare.data.local.FolderDao
import com.ashis.notesphare.data.local.FolderEntity
import com.ashis.notesphare.data.local.FolderWithCount
import com.ashis.notesphare.domain.DuplicateNameException
import com.ashis.notesphare.sync.LocalSyncWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

class FolderRepository(
    private val folderDao: FolderDao,
    private val sync: LocalSyncWriter? = null
) {

    fun observeFoldersWithCounts(): Flow<List<FolderWithCount>> =
        folderDao.observeWithCounts().visible { it.folder.ownerId }

    fun observeFolders(): Flow<List<FolderEntity>> =
        folderDao.observeAll().visible { it.ownerId }

    suspend fun getFolders(): List<FolderEntity> =
        folderDao.getAll().filter { canAccess(it.ownerId) }

    suspend fun getFolder(folderId: Long): FolderEntity? =
        folderDao.getById(folderId)?.takeIf { canAccess(it.ownerId) }

    suspend fun createFolder(name: String, color: Int = 0): Long = write {
        val trimmed = validatedName(name)
        val ownerId = sync?.currentOwnerId()
        if (folderDao.findByNameScoped(trimmed, ownerId) != null) {
            throw DuplicateNameException("folder", trimmed)
        }
        val now = System.currentTimeMillis()
        val id = folderDao.insert(
            FolderEntity(
                name = trimmed,
                color = color,
                createdAt = now,
                updatedAt = now,
                ownerId = ownerId
            )
        )
        sync?.enqueueFolder(id)
        id
    }

    suspend fun updateFolder(folderId: Long, name: String, color: Int) = write {
        val folder = folderDao.getById(folderId) ?: return@write
        if (!canAccess(folder.ownerId)) return@write
        val trimmed = validatedName(name)
        val duplicate = folderDao.findByNameScoped(trimmed, sync?.currentOwnerId())
        if (duplicate != null && duplicate.id != folderId) {
            throw DuplicateNameException("folder", trimmed)
        }
        folderDao.update(folderId, trimmed, color, System.currentTimeMillis())
        sync?.enqueueFolder(folderId)
    }

    suspend fun deleteFolder(folderId: Long) = write {
        val folder = folderDao.getById(folderId) ?: return@write
        if (!canAccess(folder.ownerId)) return@write
        val affectedNotes = sync?.notesInFolder(folderId).orEmpty()
        folderDao.softDeleteAndClearNotes(folderId, System.currentTimeMillis())
        sync?.enqueueFolder(folderId)
        affectedNotes.forEach { sync?.enqueueNote(it.id) }
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
        require(trimmed.isNotEmpty()) { "Folder name cannot be empty" }
        return trimmed
    }
}
