package com.ashis.notesphare.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.local.NoteEntity
import com.ashis.notesphare.data.local.NoteSphereDatabase
import com.ashis.notesphare.domain.AttachmentKind
import com.ashis.notesphare.domain.RecordingSessionState
import com.ashis.notesphare.files.FileStorageManager
import com.ashis.notesphare.files.StorageException
import com.ashis.notesphare.recording.AudioMetadataReader
import kotlinx.coroutines.runBlocking
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordingRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: NoteSphereDatabase
    private lateinit var storage: FileStorageManager
    private lateinit var repository: RecordingRepository
    private var noteId: Long = 0L

    private val metadataReader = object : AudioMetadataReader {
        override fun durationMs(file: File): Long? = if (file.isFile && file.length() > 0) 4_500L else null
    }

    @Before
    fun setUp() = runBlocking<Unit> {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "recordings").deleteRecursively()
        File(context.filesDir, "attachments").deleteRecursively()
        storage = FileStorageManager(context)
        database = Room.inMemoryDatabaseBuilder(context, NoteSphereDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RecordingRepository(
            recordingSessionDao = database.recordingSessionDao(),
            attachmentDao = database.attachmentDao(),
            noteDao = database.noteDao(),
            storage = storage,
            audioMetadataReader = metadataReader
        )
        noteId = database.noteDao().insert(NoteEntity(title = "Recording"))
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "recordings").deleteRecursively()
        File(context.filesDir, "attachments").deleteRecursively()
    }

    @Test
    fun savesCompletedRecordingAndFinalizesFile() = runBlocking {
        val session = repository.createSession(noteId)
        val tempFile = repository.fileFor(session)!!
        tempFile.writeBytes(ByteArray(2_048) { 1 })
        repository.updateSession(
            session.id,
            durationMs = 4_000L,
            state = RecordingSessionState.STOPPED,
            waveform = "0.1,0.5"
        )

        val attachmentId = repository.saveSession(session.id)
        val attachment = database.attachmentDao().getById(attachmentId)!!

        assertEquals(AttachmentKind.AUDIO.name, attachment.kind)
        assertEquals(4_500L, attachment.durationMs)
        assertEquals("0.1,0.5", attachment.waveform)
        assertTrue(attachment.storedPath.endsWith(".m4a"))
        assertFalse(attachment.storedPath.endsWith(".part"))
        assertTrue(storage.resolveManagedFile(attachment.storedPath)!!.isFile)
        assertEquals(null, repository.getSession(session.id))
    }

    @Test
    fun interruptedProcessCanRecoverAndSaveSessionWithoutDuplication() = runBlocking {
        val session = repository.createSession(noteId)
        repository.fileFor(session)!!.writeBytes(ByteArray(2_048) { 2 })
        repository.updateSession(
            session.id,
            durationMs = 1_000L,
            state = RecordingSessionState.RECORDING,
            waveform = null
        )

        val recoverable = repository.recoverableSessions()
        assertEquals(listOf(session.id), recoverable.map { it.id })

        val finalPath = session.storedPath.removeSuffix(".part")
        val preExistingId = database.attachmentDao().insert(
            AttachmentEntity(
                noteId = noteId,
                displayName = "Recovered recording",
                mimeType = "audio/mp4",
                kind = AttachmentKind.AUDIO.name,
                storedPath = finalPath,
                sizeBytes = 2_048L
            )
        )
        val savedId = repository.saveSession(session.id)

        assertEquals(preExistingId, savedId)
        assertEquals(1, database.attachmentDao().count())
        assertTrue(repository.recoverableSessions().isEmpty())
    }

    @Test
    fun rejectsMissingEmptyAndCorruptRecordings() = runBlocking {
        val missing = repository.createSession(noteId)
        try {
            repository.saveSession(missing.id)
            failBecause("Expected missing recording to be rejected")
        } catch (expected: StorageException) {
            assertTrue(expected.message!!.contains("missing", ignoreCase = true))
        }

        val empty = repository.createSession(noteId)
        repository.fileFor(empty)!!.writeBytes(ByteArray(0))
        try {
            repository.saveSession(empty.id)
            failBecause("Expected empty recording to be rejected")
        } catch (expected: StorageException) {
            assertTrue(expected.message!!.contains("missing or empty", ignoreCase = true))
        }

        val corrupt = repository.createSession(noteId)
        repository.fileFor(corrupt)!!.writeBytes(ByteArray(2_048) { 3 })
        val strictRepository = RecordingRepository(
            recordingSessionDao = database.recordingSessionDao(),
            attachmentDao = database.attachmentDao(),
            noteDao = database.noteDao(),
            storage = storage,
            audioMetadataReader = object : AudioMetadataReader {
                override fun durationMs(file: File): Long? = null
            }
        )
        try {
            strictRepository.saveSession(corrupt.id)
            failBecause("Expected undecodable recording to be rejected")
        } catch (expected: StorageException) {
            assertTrue(expected.message!!.contains("incomplete", ignoreCase = true))
        }
        assertTrue(database.attachmentDao().getAll().isEmpty())
    }

    @Test
    fun cancelDiscardsFileAndSession() = runBlocking {
        val session = repository.createSession(noteId)
        val file = repository.fileFor(session)!!
        file.writeBytes(ByteArray(2_048) { 4 })

        repository.discardSession(session.id)

        assertFalse(file.exists())
        assertEquals(null, repository.getSession(session.id))
        assertEquals(0, repository.pendingCount())
    }

    @Test
    fun lowStoragePreventsStartingRecording() = runBlocking {
        val constrainedStorage = FileStorageManager(context, availableBytesProvider = { 0L })
        val constrained = RecordingRepository(
            recordingSessionDao = database.recordingSessionDao(),
            attachmentDao = database.attachmentDao(),
            noteDao = database.noteDao(),
            storage = constrainedStorage,
            audioMetadataReader = metadataReader
        )
        try {
            constrained.createSession(noteId)
            failBecause("Expected low storage to reject a new recording")
        } catch (expected: StorageException) {
            assertTrue(expected.message!!.contains("free storage", ignoreCase = true))
        }
        assertEquals(0, repository.pendingCount())
    }

    private fun failBecause(message: String): Nothing = throw AssertionError(message)
}
