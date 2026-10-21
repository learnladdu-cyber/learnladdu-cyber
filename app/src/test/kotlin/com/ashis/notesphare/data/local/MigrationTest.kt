package com.ashis.notesphare.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private lateinit var context: Context
    private val databaseName = "migration-2-3-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migration2To3PreservesNotesAndAddsPhaseTwoTables() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `notes` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`title` TEXT NOT NULL, `content` TEXT NOT NULL, " +
                                "`folderId` INTEGER, `isPinned` INTEGER NOT NULL DEFAULT 0, " +
                                "`isArchived` INTEGER NOT NULL DEFAULT 0, " +
                                "`isTrashed` INTEGER NOT NULL DEFAULT 0, " +
                                "`trashedAt` INTEGER, `reminderAt` INTEGER, " +
                                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "INSERT INTO notes (title, content, createdAt, updatedAt) " +
                                "VALUES ('Existing note', 'Keep me', 10, 10)"
                        )
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int
                    ) = Unit
                })
                .build()
        )
        val db = helper.writableDatabase

        NoteSphereDatabase.MIGRATION_2_3.migrate(db)

        assertEquals(1, db.query("SELECT * FROM notes WHERE title = 'Existing note'").use { it.count })
        assertEquals("Keep me", db.query("SELECT content FROM notes").use {
            it.moveToFirst()
            it.getString(0)
        })
        assertTrue(tableExists(db, "attachments"))
        assertTrue(tableExists(db, "transcriptions"))
        assertTrue(tableExists(db, "recording_sessions"))

        db.execSQL(
            "INSERT INTO attachments " +
                "(noteId, displayName, mimeType, kind, storedPath, sizeBytes, createdAt) " +
                "VALUES (1, 'photo.jpg', 'image/jpeg', 'IMAGE', 'attachments/1/photo.jpg', 42, 20)"
        )
        assertEquals(1, db.query("SELECT * FROM attachments").use { it.count })
        duplicatePathFails(db)
        db.close()
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table)
        ).use { it.count == 1 }

    private fun duplicatePathFails(db: SupportSQLiteDatabase) {
        try {
            db.execSQL(
                "INSERT INTO attachments " +
                    "(noteId, displayName, mimeType, kind, storedPath, sizeBytes, createdAt) " +
                    "VALUES (1, 'copy.jpg', 'image/jpeg', 'IMAGE', 'attachments/1/photo.jpg', 42, 21)"
            )
            throw AssertionError("storedPath should be unique")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            // Expected.
        }
    }
}
