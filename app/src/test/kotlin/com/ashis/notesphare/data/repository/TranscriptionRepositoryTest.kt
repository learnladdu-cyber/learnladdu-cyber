package com.ashis.notesphare.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.local.NoteEntity
import com.ashis.notesphare.data.local.NoteSphereDatabase
import com.ashis.notesphare.domain.AttachmentKind
import com.ashis.notesphare.domain.TranscriptionLanguage
import com.ashis.notesphare.domain.TranscriptionStatus
import com.ashis.notesphare.files.FileStorageManager
import com.ashis.notesphare.transcription.TranscriptionConsentRequiredException
import com.ashis.notesphare.transcription.TranscriptionEngine
import com.ashis.notesphare.transcription.TranscriptionException
import com.ashis.notesphare.transcription.TranscriptionRequest
import com.ashis.notesphare.transcription.TranscriptionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class TranscriptionRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: NoteSphereDatabase
    private lateinit var storage: FileStorageManager
    private lateinit var repository: TranscriptionRepository
    private lateinit var engine: FakeEngine
    private var attachmentId: Long = 0L
    private var audioFile: File? = null

    @Before
    fun setUp() = runBlocking<Unit> {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "recordings").deleteRecursively()
        File(context.filesDir, "attachments").deleteRecursively()
        storage = FileStorageManager(context)
        database = Room.inMemoryDatabaseBuilder(context, NoteSphereDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = FakeEngine()
        repository = TranscriptionRepository(
            attachmentDao = database.attachmentDao(),
            transcriptionDao = database.transcriptionDao(),
            storage = storage,
            engineProvider = object : TranscriptionEngineProvider {
                override suspend fun engine(): TranscriptionEngine = engine
            }
        )
        val noteId = database.noteDao().insert(NoteEntity(title = "Transcription"))
        val file = File(storage.recordingsRoot, "meeting.m4a").apply {
            writeBytes(ByteArray(4_096) { 7 })
        }
        audioFile = file
        attachmentId = database.attachmentDao().insert(
            AttachmentEntity(
                noteId = noteId,
                displayName = "meeting.m4a",
                mimeType = "audio/mp4",
                kind = AttachmentKind.AUDIO.name,
                storedPath = storage.relativePath(file)!!,
                sizeBytes = file.length()
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "recordings").deleteRecursively()
        File(context.filesDir, "attachments").deleteRecursively()
    }

    @Test
    fun successfulTranscriptionPersistsHindiTextAndProgress() = runBlocking {
        engine.resultText = "नमस्ते, यह एक परीक्षण है।"
        val progress = mutableListOf<Float>()

        val row = repository.transcribe(
            attachmentId,
            TranscriptionLanguage.HINDI,
            remoteConsent = true,
            onProgress = { progress += it }
        )

        assertEquals(TranscriptionStatus.COMPLETED.name, row.status)
        assertEquals("नमस्ते, यह एक परीक्षण है।", row.text)
        assertEquals(TranscriptionLanguage.HINDI.name, row.language)
        assertTrue(progress.isNotEmpty())
        assertEquals(1, engine.calls)
        assertNotNull(audioFile)
        assertTrue(audioFile!!.isFile)
    }

    @Test
    fun failedTranscriptionCanRetryWithoutDuplicatingRow() = runBlocking {
        engine.failuresRemaining = 1
        engine.resultText = "Recovered transcript"

        try {
            repository.transcribe(attachmentId, TranscriptionLanguage.ENGLISH, true) {}
            throw AssertionError("Expected first transcription to fail")
        } catch (expected: TranscriptionException) {
            assertEquals("network unavailable", expected.message)
        }
        val failed = repository.getForAttachment(attachmentId)!!
        assertEquals(TranscriptionStatus.FAILED.name, failed.status)
        assertEquals("network unavailable", failed.errorMessage)

        val retried = repository.transcribe(attachmentId, TranscriptionLanguage.ENGLISH, true) {}

        assertEquals(failed.id, retried.id)
        assertEquals(TranscriptionStatus.COMPLETED.name, retried.status)
        assertEquals("Recovered transcript", retried.text)
        assertEquals(2, engine.calls)
        assertEquals(1, database.transcriptionDao().observeForNote(retried.noteId).first().size)
    }

    @Test
    fun repeatedTranscriptionReusesTheUniqueAttachmentRow() = runBlocking {
        engine.resultText = "First"
        val first = repository.transcribe(attachmentId, TranscriptionLanguage.ENGLISH, true) {}
        engine.resultText = "Second"
        val second = repository.transcribe(attachmentId, TranscriptionLanguage.ENGLISH, true) {}

        assertEquals(first.id, second.id)
        assertEquals("Second", second.text)
        assertEquals(1, database.transcriptionDao().observeForNote(second.noteId).first().size)
    }

    @Test
    fun remoteEngineRequiresExplicitConsentBeforeSendingAudio() = runBlocking {
        try {
            repository.transcribe(attachmentId, TranscriptionLanguage.ENGLISH, remoteConsent = false) {}
            throw AssertionError("Expected consent to be required")
        } catch (expected: TranscriptionConsentRequiredException) {
            assertEquals(0, engine.calls)
        }
        assertEquals(null, repository.getForAttachment(attachmentId))
    }

    @Test
    fun editedTranscriptIsSavedWithoutTouchingAudio() = runBlocking {
        engine.resultText = "Original"
        repository.transcribe(attachmentId, TranscriptionLanguage.ENGLISH, true) {}

        assertTrue(repository.saveEditedText(attachmentId, "Edited transcript"))

        assertEquals("Edited transcript", repository.getForAttachment(attachmentId)!!.text)
        assertTrue(audioFile!!.isFile)
    }

    @Test
    fun cancellationMarksTranscriptionCancelled() = runBlocking {
        engine.cancel = true
        try {
            repository.transcribe(attachmentId, TranscriptionLanguage.ENGLISH, true) {}
            throw AssertionError("Expected cancellation")
        } catch (expected: CancellationException) {
            // Expected.
        }
        assertEquals(
            TranscriptionStatus.CANCELLED.name,
            repository.getForAttachment(attachmentId)!!.status
        )
    }

    private class FakeEngine : TranscriptionEngine {
        override val id = "fake-engine"
        override val displayName = "Fake engine"
        override val isRemote = true
        override val usesOnDeviceProcessing = false
        var calls = 0
        var failuresRemaining = 0
        var cancel = false
        var resultText = "Transcript"

        override suspend fun transcribe(
            request: TranscriptionRequest,
            onProgress: (Float) -> Unit
        ): TranscriptionResult {
            calls++
            onProgress(0.5f)
            if (cancel) throw CancellationException("cancelled by test")
            if (failuresRemaining > 0) {
                failuresRemaining--
                throw TranscriptionException("network unavailable")
            }
            return TranscriptionResult(
                text = resultText,
                engineId = id,
                language = request.language,
                usedRemoteProcessing = true
            )
        }
    }
}
