package com.ashis.notesphare.ui.screens.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ashis.notesphare.data.local.NoteSphereDatabase
import com.ashis.notesphare.data.repository.AttachmentRepository
import com.ashis.notesphare.data.repository.FolderRepository
import com.ashis.notesphare.data.repository.NoteRepository
import com.ashis.notesphare.data.repository.TagRepository
import com.ashis.notesphare.data.repository.TranscriptionRepository
import com.ashis.notesphare.domain.ThemeMode
import com.ashis.notesphare.files.FileStorageManager
import com.ashis.notesphare.reminders.ReminderScheduler
import com.ashis.notesphare.reminders.ScheduleAccuracy
import com.ashis.notesphare.transcription.TranscriptionEngine
import com.ashis.notesphare.transcription.TranscriptionEngineProvider
import com.ashis.notesphare.ui.theme.NoteSphereTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: NoteSphereDatabase
    private lateinit var noteRepository: NoteRepository
    private lateinit var viewModel: EditorViewModel
    private lateinit var externalScope: CoroutineScope

    private val scheduler = object : ReminderScheduler {
        override fun schedule(noteId: Long, triggerAtMillis: Long): ScheduleAccuracy =
            ScheduleAccuracy.EXACT

        override fun cancel(noteId: Long) = Unit

        override fun canScheduleExactAlarms(): Boolean = true
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NoteSphereDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        noteRepository = NoteRepository(
            noteDao = database.noteDao(),
            checklistDao = database.checklistDao(),
            reminderScheduler = scheduler
        )
        externalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val storage = FileStorageManager(context)
        viewModel = EditorViewModel(
            noteRepository = noteRepository,
            folderRepository = FolderRepository(database.folderDao()),
            tagRepository = TagRepository(database.tagDao(), database.noteTagDao()),
            attachmentRepository = AttachmentRepository(
                attachmentDao = database.attachmentDao(),
                recordingSessionDao = database.recordingSessionDao(),
                noteDao = database.noteDao(),
                storage = storage
            ),
            transcriptionRepository = TranscriptionRepository(
                attachmentDao = database.attachmentDao(),
                transcriptionDao = database.transcriptionDao(),
                storage = storage,
                engineProvider = object : TranscriptionEngineProvider {
                    override suspend fun engine(): TranscriptionEngine =
                        error("Transcription is not used by this test")
                }
            ),
            externalScope = externalScope,
            requestedNoteId = 0L,
            initialFolderId = null,
            savedStateHandle = SavedStateHandle()
        )
    }

    @After
    fun tearDown() {
        externalScope.cancel()
        database.close()
    }

    @Test
    fun createEditAndChecklistChangesArePersisted() {
        composeRule.setContent {
            NoteSphereTheme(themeMode = ThemeMode.LIGHT) {
                EditorScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onRecordAudio = {},
                    onOpenAttachment = {},
                    onOpenAudio = {},
                    onOpenTranscription = {},
                    onOpenStorage = {}
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.state.value.isLoading
        }
        val noteId = viewModel.state.value.noteId
        assertTrue(noteId > 0L)

        composeRule.onNodeWithTag("noteTitle").performTextInput("Groceries")
        composeRule.onNodeWithTag("noteContent").performTextInput("Milk and eggs")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            !viewModel.state.value.isDirty && !viewModel.state.value.isSaving
        }

        runBlocking {
            val note = noteRepository.getNote(noteId)
            assertEquals("Groceries", note?.title)
            assertEquals("Milk and eggs", note?.content)
        }

        composeRule.onNodeWithTag("checklistInput").performTextInput("Bread")
        composeRule.onNodeWithTag("addChecklistItem").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.checklist.size == 1
        }
        val itemId = viewModel.state.value.checklist.first().id

        composeRule.onNodeWithTag("checklistCheck_$itemId").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            viewModel.state.value.checklist.first().isChecked
        }

        runBlocking {
            val items = noteRepository.getChecklist(noteId)
            assertEquals(1, items.size)
            assertEquals("Bread", items.first().text)
            assertTrue(items.first().isChecked)
        }
    }
}
