package com.ashis.notesphare.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ashis.notesphare.data.local.NoteSphereDatabase
import com.ashis.notesphare.domain.DuplicateNameException
import com.ashis.notesphare.reminders.ReminderScheduler
import com.ashis.notesphare.reminders.ScheduleAccuracy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoteRepositoryTest {

    private lateinit var database: NoteSphereDatabase
    private lateinit var noteRepository: NoteRepository
    private lateinit var folderRepository: FolderRepository
    private lateinit var tagRepository: TagRepository
    private lateinit var scheduler: FakeReminderScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteSphereDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduler = FakeReminderScheduler()
        noteRepository = NoteRepository(
            noteDao = database.noteDao(),
            checklistDao = database.checklistDao(),
            reminderScheduler = scheduler
        )
        folderRepository = FolderRepository(database.folderDao())
        tagRepository = TagRepository(database.tagDao(), database.noteTagDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createSaveAndSearchNotesByTitleAndContent() = runBlocking {
        val noteId = noteRepository.createNote()
        assertTrue(noteRepository.saveNote(noteId, "Groceries", "Milk **and** eggs"))

        val note = noteRepository.getNote(noteId)
        assertNotNull(note)
        assertEquals("Groceries", note?.title)
        assertEquals("Milk **and** eggs", note?.content)

        assertEquals(listOf(noteId), noteRepository.searchNotes("groc").first().map { it.id })
        assertEquals(listOf(noteId), noteRepository.searchNotes("eggs").first().map { it.id })
        assertTrue(noteRepository.searchNotes("missing").first().isEmpty())

        noteRepository.trashNote(noteId)
        assertTrue(noteRepository.searchNotes("groc").first().isEmpty())
    }

    @Test
    fun pinArchiveTrashAndRestoreChangeLists() = runBlocking {
        val noteId = noteRepository.createNote(title = "Plan", content = "Details")

        noteRepository.setPinned(noteId, true)
        assertEquals(listOf(noteId), noteRepository.observePinnedNotes().first().map { it.id })
        assertTrue(noteRepository.observeRecentNotes().first().isEmpty())

        noteRepository.setArchived(noteId, true)
        assertEquals(listOf(noteId), noteRepository.observeArchivedNotes().first().map { it.id })
        assertTrue(noteRepository.observeActiveNotes().first().isEmpty())

        noteRepository.setArchived(noteId, false)
        assertEquals(listOf(noteId), noteRepository.observeActiveNotes().first().map { it.id })

        noteRepository.trashNote(noteId)
        assertEquals(listOf(noteId), noteRepository.observeTrashedNotes().first().map { it.id })
        assertTrue(noteRepository.observeActiveNotes().first().isEmpty())

        noteRepository.restoreNote(noteId)
        assertTrue(noteRepository.observeTrashedNotes().first().isEmpty())
        assertEquals(listOf(noteId), noteRepository.observeActiveNotes().first().map { it.id })
    }

    @Test
    fun checklistItemsAreStoredOrderedUpdatedAndDeleted() = runBlocking {
        val noteId = noteRepository.createNote()
        val milk = noteRepository.addChecklistItem(noteId, "  Milk  ")
        val eggs = noteRepository.addChecklistItem(noteId, "Eggs")
        assertNotNull(milk)
        assertNotNull(eggs)
        assertEquals(0, milk?.position)
        assertEquals(1, eggs?.position)

        noteRepository.setChecklistItemChecked(noteId, milk!!.id, true)
        noteRepository.updateChecklistItemText(noteId, eggs!!.id, "Bread")

        val items = noteRepository.getChecklist(noteId)
        assertEquals(2, items.size)
        assertEquals("Milk", items[0].text)
        assertTrue(items[0].isChecked)
        assertEquals("Bread", items[1].text)

        noteRepository.deleteChecklistItem(noteId, milk.id)
        assertEquals(listOf("Bread"), noteRepository.getChecklist(noteId).map { it.text })
    }

    @Test
    fun remindersAreScheduledClearedAndRejectedWhenInThePast() = runBlocking {
        val noteId = noteRepository.createNote()
        val future = System.currentTimeMillis() + 120_000

        assertEquals(
            ReminderScheduleResult.EXACT,
            noteRepository.setReminder(noteId, future)
        )
        assertEquals(future, noteRepository.getNote(noteId)?.reminderAt)
        assertEquals(future, scheduler.scheduled[noteId])

        assertEquals(
            ReminderScheduleResult.CLEARED,
            noteRepository.setReminder(noteId, null)
        )
        assertNull(noteRepository.getNote(noteId)?.reminderAt)
        assertTrue(noteId in scheduler.cancelled)

        var rejected = false
        try {
            noteRepository.setReminder(noteId, System.currentTimeMillis() - 1_000)
        } catch (exception: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun inexactSchedulingIsReportedWhenExactAlarmsAreUnavailable() = runBlocking {
        scheduler.exactAlarmsAvailable = false
        val noteId = noteRepository.createNote()
        val future = System.currentTimeMillis() + 120_000

        assertEquals(
            ReminderScheduleResult.INEXACT,
            noteRepository.setReminder(noteId, future)
        )
    }

    @Test
    fun trashingANoteCancelsItsReminder() = runBlocking {
        val noteId = noteRepository.createNote()
        noteRepository.setReminder(noteId, System.currentTimeMillis() + 120_000)
        scheduler.cancelled.clear()

        noteRepository.trashNote(noteId)

        assertTrue(noteId in scheduler.cancelled)
    }

    @Test
    fun deletingFolderKeepsNotesAndClearsFolderReference() = runBlocking {
        val noteId = noteRepository.createNote()
        val folderId = folderRepository.createFolder("Work", color = 1)
        noteRepository.setFolder(noteId, folderId)
        assertEquals(folderId, noteRepository.getNote(noteId)?.folderId)

        folderRepository.deleteFolder(folderId)

        assertNull(noteRepository.getNote(noteId)?.folderId)
        assertNotNull(noteRepository.getNote(noteId))
    }

    @Test
    fun tagsCanBeAttachedAndRemovedWithoutDeletingNotes() = runBlocking {
        val noteId = noteRepository.createNote()
        val tagId = tagRepository.createTag("Urgent")

        tagRepository.setNoteTags(noteId, setOf(tagId))
        assertEquals(setOf(tagId), tagRepository.getTagIdsForNote(noteId))
        assertEquals(
            listOf(noteId),
            noteRepository.observeNotesWithTag(tagId).first().map { it.id }
        )

        tagRepository.deleteTag(tagId)
        assertTrue(tagRepository.getTagIdsForNote(noteId).isEmpty())
        assertNotNull(noteRepository.getNote(noteId))
    }

    @Test
    fun duplicateFolderNamesAreRejectedCaseInsensitively() = runBlocking {
        folderRepository.createFolder("Work")
        try {
            folderRepository.createFolder("work")
            fail("Expected DuplicateNameException")
        } catch (expected: DuplicateNameException) {
            assertEquals("folder", expected.kind)
        }
    }

    @Test
    fun emptyTrashDeletesOnlyTrashedNotes() = runBlocking {
        val trashed = noteRepository.createNote(title = "Old")
        val active = noteRepository.createNote(title = "Current")
        noteRepository.setReminder(trashed, System.currentTimeMillis() + 120_000)
        noteRepository.trashNote(trashed)
        noteRepository.trashNote(active)
        noteRepository.restoreNote(active)
        scheduler.cancelled.clear()

        noteRepository.emptyTrash()

        assertTrue(noteRepository.observeTrashedNotes().first().isEmpty())
        assertNull(noteRepository.getNote(trashed))
        assertNotNull(noteRepository.getNote(active))
        assertTrue(trashed in scheduler.cancelled)
    }
}

private class FakeReminderScheduler : ReminderScheduler {
    val scheduled = mutableMapOf<Long, Long>()
    val cancelled = mutableSetOf<Long>()
    var exactAlarmsAvailable: Boolean = true

    override fun schedule(noteId: Long, triggerAtMillis: Long): ScheduleAccuracy {
        scheduled[noteId] = triggerAtMillis
        return if (exactAlarmsAvailable) ScheduleAccuracy.EXACT else ScheduleAccuracy.INEXACT
    }

    override fun cancel(noteId: Long) {
        cancelled += noteId
        scheduled.remove(noteId)
    }

    override fun canScheduleExactAlarms(): Boolean = exactAlarmsAvailable
}
