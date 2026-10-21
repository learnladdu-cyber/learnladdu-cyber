package com.ashis.notesphare.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationSyncTest {

    private lateinit var context: Context
    private lateinit var databaseName: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration3To4AddsSyncMetadataAndPreservesRows() {
        databaseName = "migration-3-4-test.db"
        context.deleteDatabase(databaseName)
        val db = openV3()

        NoteSphereDatabase.MIGRATION_3_4.migrate(db)

        assertTrue(columnExists(db, "notes", "uuid"))
        assertTrue(columnExists(db, "notes", "version"))
        assertTrue(columnExists(db, "notes", "deletedAt"))
        assertTrue(columnExists(db, "attachments", "syncStatus"))
        assertTrue(columnExists(db, "attachments", "cloudPath"))
        assertTrue(tableExists(db, "sync_outbox"))
        assertTrue(tableExists(db, "sync_conflicts"))
        assertTrue(tableExists(db, "sync_cursors"))
        assertTrue(tableExists(db, "device_registrations"))

        db.query("SELECT title, uuid, version, deletedAt FROM notes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Keep me", cursor.getString(0))
            assertTrue(cursor.getString(1).isNotBlank())
            assertEquals(1L, cursor.getLong(2))
            assertTrue(cursor.isNull(3))
        }
        db.query("SELECT uuid, updatedAt, ownerId FROM folders").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getString(0).isNotBlank())
            assertEquals(10L, cursor.getLong(1))
            assertTrue(cursor.isNull(2))
        }
        db.query("SELECT syncStatus FROM attachments").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("LOCAL_ONLY", cursor.getString(0))
        }
        db.close()
    }

    @Test
    fun migration4To5AllowsSameFolderAndTagNameAcrossAccounts() {
        databaseName = "migration-4-5-test.db"
        context.deleteDatabase(databaseName)
        val db = openV3()
        NoteSphereDatabase.MIGRATION_3_4.migrate(db)
        NoteSphereDatabase.MIGRATION_4_5.migrate(db)

        db.execSQL("INSERT INTO folders (name, color, createdAt, uuid, updatedAt) VALUES ('Work', 0, 1, 'folder-local', 1)")
        db.execSQL("INSERT INTO folders (name, color, createdAt, uuid, updatedAt, ownerId) VALUES ('Work', 0, 2, 'folder-a', 2, 'account-a')")
        db.execSQL("INSERT INTO tags (name, color, createdAt, uuid, updatedAt) VALUES ('Urgent', 0, 1, 'tag-local', 1)")
        db.execSQL("INSERT INTO tags (name, color, createdAt, uuid, updatedAt, ownerId) VALUES ('Urgent', 0, 2, 'tag-b', 2, 'account-b')")

        assertEquals(2, count(db, "SELECT * FROM folders WHERE name = 'Work'"))
        assertEquals(2, count(db, "SELECT * FROM tags WHERE name = 'Urgent'"))
        assertFalse(indexExists(db, "index_folders_name"))
        assertFalse(indexExists(db, "index_tags_name"))
        assertTrue(indexExists(db, "index_folders_ownerId_name"))
        assertTrue(indexExists(db, "index_tags_ownerId_name"))
        db.close()
    }

    @Test
    fun migration5To6ScopesCursorsByOwner() {
        databaseName = "migration-5-6-test.db"
        context.deleteDatabase(databaseName)
        val db = openV3()
        NoteSphereDatabase.MIGRATION_3_4.migrate(db)
        db.execSQL(
            "INSERT INTO sync_cursors (tableName, cursor, lastPulledAt, lastError) " +
                "VALUES ('notes', '41', 100, 'old error')"
        )

        NoteSphereDatabase.MIGRATION_5_6.migrate(db)

        assertTrue(columnExists(db, "sync_cursors", "ownerId"))
        db.query(
            "SELECT ownerId, tableName, cursor, lastPulledAt, lastError FROM sync_cursors"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
            assertEquals("notes", cursor.getString(1))
            assertEquals("41", cursor.getString(2))
            assertEquals(100L, cursor.getLong(3))
            assertEquals("old error", cursor.getString(4))
        }

        db.execSQL(
            "INSERT INTO sync_cursors (ownerId, tableName, cursor, lastPulledAt, lastError) " +
                "VALUES ('account-a', 'notes', '99', 200, NULL)"
        )
        assertEquals(2, count(db, "SELECT * FROM sync_cursors WHERE tableName = 'notes'"))
        assertNotNull(
            db.query(
                "SELECT * FROM sync_cursors WHERE ownerId = 'account-a' AND tableName = 'notes'"
            ).use { if (it.moveToFirst()) it.getString(2) else null }
        )
        db.close()
    }

    private fun openV3(): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createV3Schema(db)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        return helper.writableDatabase
    }

    private fun createV3Schema(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `notes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL DEFAULT '', `content` TEXT NOT NULL DEFAULT '', " +
                "`folderId` INTEGER, `isPinned` INTEGER NOT NULL DEFAULT 0, " +
                "`isArchived` INTEGER NOT NULL DEFAULT 0, `isTrashed` INTEGER NOT NULL DEFAULT 0, " +
                "`trashedAt` INTEGER, `reminderAt` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE `folders` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `color` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX `index_folders_name` ON `folders` (`name`)")
        db.execSQL(
            "CREATE TABLE `tags` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `color` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX `index_tags_name` ON `tags` (`name`)")
        db.execSQL(
            "CREATE TABLE `checklist_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`noteId` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                "`isChecked` INTEGER NOT NULL DEFAULT 0, `position` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE `attachments` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteId` INTEGER NOT NULL, " +
                "`displayName` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`storedPath` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `durationMs` INTEGER, " +
                "`waveform` TEXT, `createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX `index_attachments_storedPath` ON `attachments` (`storedPath`)")
        db.execSQL(
            "CREATE TABLE `transcriptions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteId` INTEGER NOT NULL, " +
                "`attachmentId` INTEGER NOT NULL, `language` TEXT NOT NULL, " +
                "`engineId` TEXT NOT NULL, `text` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                "`errorMessage` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX `index_transcriptions_attachmentId` ON `transcriptions` (`attachmentId`)")
        db.execSQL(
            "CREATE TABLE `recording_sessions` (" +
                "`id` TEXT PRIMARY KEY NOT NULL, `noteId` INTEGER NOT NULL, " +
                "`storedPath` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, " +
                "`durationMs` INTEGER NOT NULL DEFAULT 0, `state` TEXT NOT NULL, " +
                "`waveform` TEXT, `updatedAt` INTEGER NOT NULL)"
        )

        db.execSQL(
            "INSERT INTO notes (title, content, createdAt, updatedAt) " +
                "VALUES ('Keep me', 'Body', 10, 10)"
        )
        db.execSQL("INSERT INTO folders (name, color, createdAt) VALUES ('Personal', 0, 10)")
        db.execSQL("INSERT INTO tags (name, color, createdAt) VALUES ('Home', 0, 10)")
        db.execSQL(
            "INSERT INTO checklist_items (noteId, text, position, createdAt) " +
                "VALUES (1, 'Item', 0, 10)"
        )
        db.execSQL(
            "INSERT INTO attachments (noteId, displayName, mimeType, kind, storedPath, sizeBytes, createdAt) " +
                "VALUES (1, 'photo.jpg', 'image/jpeg', 'IMAGE', 'attachments/1/photo.jpg', 42, 10)"
        )
        db.execSQL(
            "INSERT INTO transcriptions (noteId, attachmentId, language, engineId, text, status, createdAt, updatedAt) " +
                "VALUES (1, 1, 'ENGLISH', 'test', 'hello', 'COMPLETED', 10, 10)"
        )
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { it.count == 1 }

    private fun indexExists(db: SupportSQLiteDatabase, index: String): Boolean =
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(index)
        ).use { it.count == 1 }

    private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return true
            }
            false
        }

    private fun count(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { it.count }
}
