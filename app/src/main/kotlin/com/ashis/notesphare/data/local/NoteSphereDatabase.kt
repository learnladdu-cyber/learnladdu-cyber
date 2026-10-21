package com.ashis.notesphare.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        TagEntity::class,
        ChecklistItemEntity::class,
        NoteTagCrossRef::class,
        AttachmentEntity::class,
        TranscriptionEntity::class,
        RecordingSessionEntity::class,
        OutboxEntity::class,
        SyncConflictEntity::class,
        SyncCursorEntity::class,
        DeviceRegistrationEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class NoteSphereDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun noteTagDao(): NoteTagDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun transcriptionDao(): TranscriptionDao
    abstract fun recordingSessionDao(): RecordingSessionDao
    abstract fun outboxDao(): OutboxDao
    abstract fun conflictDao(): ConflictDao
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun deviceRegistrationDao(): DeviceRegistrationDao

    companion object {
        const val DATABASE_NAME = "notesphare.db"

        /**
         * Version 1 was the early notes-only prototype. Version 2 introduces folders, tags,
         * checklists and reminders. The migration is additive and never drops user notes.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `folders` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_name` ON `folders` (`name`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tags` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checklist_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`noteId` INTEGER NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`isChecked` INTEGER NOT NULL DEFAULT 0, " +
                        "`position` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checklist_items_noteId` " +
                        "ON `checklist_items` (`noteId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_checklist_items_noteId_position` " +
                        "ON `checklist_items` (`noteId`, `position`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `note_tag_cross_ref` (" +
                        "`noteId` INTEGER NOT NULL, " +
                        "`tagId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`noteId`, `tagId`), " +
                        "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_note_tag_cross_ref_tagId` " +
                        "ON `note_tag_cross_ref` (`tagId`)"
                )

                db.execSQL(
                    "ALTER TABLE `notes` ADD COLUMN `folderId` INTEGER " +
                        "REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
                )
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `isTrashed` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `trashedAt` INTEGER")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `reminderAt` INTEGER")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_isPinned` ON `notes` (`isPinned`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_isArchived` ON `notes` (`isArchived`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_isTrashed` ON `notes` (`isTrashed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_reminderAt` ON `notes` (`reminderAt`)")
            }
        }

        /**
         * Version 3 adds attachments, transcriptions and durable recording sessions.
         * It is additive: existing notes, folders, tags, checklists and reminders are untouched.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`noteId` INTEGER NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, " +
                        "`kind` TEXT NOT NULL, " +
                        "`storedPath` TEXT NOT NULL, " +
                        "`sizeBytes` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER, " +
                        "`waveform` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_noteId` ON `attachments` (`noteId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_storedPath` ON `attachments` (`storedPath`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_kind` ON `attachments` (`kind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_createdAt` ON `attachments` (`createdAt`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transcriptions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`noteId` INTEGER NOT NULL, " +
                        "`attachmentId` INTEGER NOT NULL, " +
                        "`language` TEXT NOT NULL, " +
                        "`engineId` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`errorMessage` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`attachmentId`) REFERENCES `attachments`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcriptions_noteId` ON `transcriptions` (`noteId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transcriptions_attachmentId` ON `transcriptions` (`attachmentId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_sessions` (" +
                        "`id` TEXT PRIMARY KEY NOT NULL, " +
                        "`noteId` INTEGER NOT NULL, " +
                        "`storedPath` TEXT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL DEFAULT 0, " +
                        "`state` TEXT NOT NULL, " +
                        "`waveform` TEXT, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recording_sessions_noteId` ON `recording_sessions` (`noteId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recording_sessions_state` ON `recording_sessions` (`state`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recording_sessions_startedAt` ON `recording_sessions` (`startedAt`)")
            }
        }

        /**
         * Version 4 adds stable client UUIDs, optimistic-concurrency versions, sync tombstones,
         * attachment cloud state, a durable outbox, conflict records and incremental cursors.
         * Existing rows receive generated UUIDs and remain fully usable offline.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val uuid = "lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || " +
                    "substr(hex(randomblob(2)), 2) || '-a' || substr(hex(randomblob(2)), 2) || " +
                    "'-' || hex(randomblob(6)))"

                db.execSQL("ALTER TABLE `folders` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `folders` ADD COLUMN `ownerId` TEXT")
                db.execSQL("ALTER TABLE `folders` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `folders` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `folders` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("UPDATE `folders` SET `uuid` = $uuid, `updatedAt` = `createdAt`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_uuid` ON `folders` (`uuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_ownerId` ON `folders` (`ownerId`)")

                db.execSQL("ALTER TABLE `tags` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `tags` ADD COLUMN `ownerId` TEXT")
                db.execSQL("ALTER TABLE `tags` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `tags` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tags` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("UPDATE `tags` SET `uuid` = $uuid, `updatedAt` = `createdAt`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_uuid` ON `tags` (`uuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_ownerId` ON `tags` (`ownerId`)")

                db.execSQL("ALTER TABLE `notes` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `ownerId` TEXT")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("UPDATE `notes` SET `uuid` = $uuid")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_notes_uuid` ON `notes` (`uuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_ownerId` ON `notes` (`ownerId`)")

                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `ownerId` TEXT")
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `checklist_items` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("UPDATE `checklist_items` SET `uuid` = $uuid, `updatedAt` = `createdAt`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_checklist_items_uuid` ON `checklist_items` (`uuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_checklist_items_ownerId` ON `checklist_items` (`ownerId`)")

                db.execSQL("ALTER TABLE `attachments` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `attachments` ADD COLUMN `ownerId` TEXT")
                db.execSQL("ALTER TABLE `attachments` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `attachments` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `attachments` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("ALTER TABLE `attachments` ADD COLUMN `cloudPath` TEXT")
                db.execSQL("ALTER TABLE `attachments` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("ALTER TABLE `attachments` ADD COLUMN `syncError` TEXT")
                db.execSQL("ALTER TABLE `attachments` ADD COLUMN `remoteUpdatedAt` INTEGER")
                db.execSQL("UPDATE `attachments` SET `uuid` = $uuid, `updatedAt` = `createdAt`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_ownerId` ON `attachments` (`ownerId`)")

                db.execSQL("ALTER TABLE `transcriptions` ADD COLUMN `uuid` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `transcriptions` ADD COLUMN `ownerId` TEXT")
                db.execSQL("ALTER TABLE `transcriptions` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `transcriptions` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL("UPDATE `transcriptions` SET `uuid` = $uuid")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transcriptions_uuid` ON `transcriptions` (`uuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transcriptions_ownerId` ON `transcriptions` (`ownerId`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_outbox` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ownerId` TEXT NOT NULL, `entityType` TEXT NOT NULL, " +
                        "`entityUuid` TEXT NOT NULL, `operation` TEXT NOT NULL, " +
                        "`payload` TEXT NOT NULL, `baseVersion` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `attempts` INTEGER NOT NULL, " +
                        "`nextAttemptAt` INTEGER NOT NULL, `lastError` TEXT)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_outbox_entityType_entityUuid` ON `sync_outbox` (`entityType`, `entityUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_ownerId_nextAttemptAt` ON `sync_outbox` (`ownerId`, `nextAttemptAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_createdAt` ON `sync_outbox` (`createdAt`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_conflicts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ownerId` TEXT NOT NULL, `entityType` TEXT NOT NULL, " +
                        "`entityUuid` TEXT NOT NULL, `localPayload` TEXT NOT NULL, " +
                        "`remotePayload` TEXT NOT NULL, `localUpdatedAt` INTEGER NOT NULL, " +
                        "`remoteUpdatedAt` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sync_conflicts_ownerId_entityType_entityUuid` ON `sync_conflicts` (`ownerId`, `entityType`, `entityUuid`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_createdAt` ON `sync_conflicts` (`createdAt`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_cursors` (" +
                        "`tableName` TEXT PRIMARY KEY NOT NULL, `cursor` TEXT, " +
                        "`lastPulledAt` INTEGER, `lastError` TEXT)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `device_registrations` (" +
                        "`id` TEXT PRIMARY KEY NOT NULL, `ownerId` TEXT NOT NULL, " +
                        "`deviceId` TEXT NOT NULL, `deviceName` TEXT NOT NULL, " +
                        "`platform` TEXT NOT NULL, `lastSeenAt` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_device_registrations_ownerId_deviceId` ON `device_registrations` (`ownerId`, `deviceId`)")
            }
        }

        /**
         * Version 5 scopes folder/tag names to an account. The previous global unique indexes
         * made it impossible for two accounts on one device to use the same folder or tag name.
         * Local-only rows have NULL ownerId; SQLite permits duplicate NULLs, while repository
         * validation still prevents accidental local duplicates.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_folders_name`")
                db.execSQL("DROP INDEX IF EXISTS `index_folders_ownerId`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_ownerId_name` ON `folders` (`ownerId`, `name`)")
                db.execSQL("DROP INDEX IF EXISTS `index_tags_name`")
                db.execSQL("DROP INDEX IF EXISTS `index_tags_ownerId`")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_ownerId_name` ON `tags` (`ownerId`, `name`)")
            }
        }

        /**
         * Version 6 makes pull cursors account-specific. A shared cursor let a second account
         * skip rows whose revisions were lower than the first account's last pull.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sync_cursors_new` (" +
                        "`ownerId` TEXT NOT NULL, `tableName` TEXT NOT NULL, `cursor` TEXT, " +
                        "`lastPulledAt` INTEGER, `lastError` TEXT, " +
                        "PRIMARY KEY(`ownerId`, `tableName`))"
                )
                db.execSQL(
                    "INSERT OR REPLACE INTO `sync_cursors_new` " +
                        "(ownerId, tableName, cursor, lastPulledAt, lastError) " +
                        "SELECT '', tableName, cursor, lastPulledAt, lastError FROM `sync_cursors`"
                )
                db.execSQL("DROP TABLE `sync_cursors`")
                db.execSQL("ALTER TABLE `sync_cursors_new` RENAME TO `sync_cursors`")
            }
        }

        fun build(context: Context): NoteSphereDatabase =
            Room.databaseBuilder(context, NoteSphereDatabase::class.java, DATABASE_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}
