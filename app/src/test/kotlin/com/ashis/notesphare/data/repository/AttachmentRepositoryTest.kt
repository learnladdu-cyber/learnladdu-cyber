package com.ashis.notesphare.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.local.NoteEntity
import com.ashis.notesphare.data.local.NoteSphereDatabase
import com.ashis.notesphare.domain.AttachmentKind
import com.ashis.notesphare.files.FileStorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttachmentRepositoryTest {

    private lateinit var context: Context
    private lateinit var database: NoteSphereDatabase
    private lateinit var storage: FileStorageManager
    private lateinit var repository: AttachmentRepository
    private var noteId: Long = 0L

    @Before
    fun setUp() = runBlocking<Unit> {
        context = ApplicationProvider.getApplicationContext()
        storage = FileStorageManager(context)
        database = Room.inMemoryDatabaseBuilder(context, NoteSphereDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AttachmentRepository(
            attachmentDao = database.attachmentDao(),
            recordingSessionDao = database.recordingSessionDao(),
            noteDao = database.noteDao(),
            storage = storage
        )
        noteId = database.noteDao().insert(NoteEntity(title = "Attachments"))
        File(context.filesDir, "attachments").deleteRecursively()
        File(context.filesDir, "recordings").deleteRecursively()
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "attachments").deleteRecursively()
        File(context.filesDir, "recordings").deleteRecursively()
    }

    @Test
    fun importsValidPdfCopiesItAndPersistsMetadata() = runBlocking {
        val summary = repository.importUris(noteId, listOf(fileUri("sample.pdf", PDF_BYTES)))

        assertTrue(summary.errors.toString(), summary.errors.isEmpty())
        assertEquals(1, summary.added.size)
        val attachment = summary.added.single()
        assertEquals(AttachmentKind.PDF.name, attachment.kind)
        assertEquals("sample.pdf", attachment.displayName)
        assertEquals(PDF_BYTES.size.toLong(), attachment.sizeBytes)
        val file = storage.resolveManagedFile(attachment.storedPath)
        assertNotNull(file)
        assertTrue(file!!.isFile)
        assertEquals(PDF_BYTES.size.toLong(), file.length())
        assertEquals(listOf(attachment.id), repository.observeAllAttachments().firstIds())
    }

    @Test
    fun rejectsUnsupportedEmptyAndInsufficientStorageFiles() = runBlocking {
        val unsupported = repository.importUris(
            noteId,
            listOf(fileUri("notes.exe", byteArrayOf(1, 2, 3, 4)))
        )
        assertEquals(1, unsupported.errors.size)
        assertTrue(unsupported.errors.single().contains("Unsupported"))

        val empty = repository.importUris(
            noteId,
            listOf(fileUri("empty.pdf", ByteArray(0)))
        )
        assertEquals(1, empty.errors.size)

        val constrainedStorage = FileStorageManager(
            context,
            availableBytesProvider = { 0L }
        )
        val constrainedRepository = AttachmentRepository(
            attachmentDao = database.attachmentDao(),
            recordingSessionDao = database.recordingSessionDao(),
            noteDao = database.noteDao(),
            storage = constrainedStorage
        )
        val noSpace = constrainedRepository.importUris(
            noteId,
            listOf(fileUri("sample.pdf", PDF_BYTES))
        )
        assertEquals(1, noSpace.errors.size)
        assertTrue(noSpace.errors.single().contains("free storage", ignoreCase = true))
        assertTrue(repository.getAllAttachments().isEmpty())
    }

    @Test
    fun rejectsInvalidPdfHeader() = runBlocking {
        val summary = repository.importUris(
            noteId,
            listOf(fileUri("fake.pdf", "not a pdf".toByteArray()))
        )
        assertEquals(1, summary.errors.size)
        assertTrue(summary.errors.single().contains("valid PDF"))
        assertTrue(repository.getAllAttachments().isEmpty())
    }

    @Test
    fun deletingAttachmentRemovesFileAndLeavesStorageClean() = runBlocking {
        val attachment = repository.importUris(
            noteId,
            listOf(fileUri("sample.pdf", PDF_BYTES))
        ).added.single()
        val file = storage.resolveManagedFile(attachment.storedPath)!!

        assertTrue(repository.deleteAttachment(attachment.id))

        assertFalse(file.exists())
        assertNull(repository.getAttachment(attachment.id))
    }

    @Test
    fun cleanupDeletesOnlyOldUnreferencedFiles() = runBlocking {
        val referenced = repository.importUris(
            noteId,
            listOf(fileUri("sample.pdf", PDF_BYTES))
        ).added.single()
        val referencedFile = storage.resolveManagedFile(referenced.storedPath)!!
        val orphan = storage.importStream(
            noteId,
            ByteArrayInputStream(PDF_BYTES),
            "orphan.pdf",
            "application/pdf"
        )
        val orphanFile = orphan.file
        val old = System.currentTimeMillis() - FileStorageManager.ORPHAN_GRACE_PERIOD_MS - 60_000
        orphanFile.setLastModified(old)
        referencedFile.setLastModified(old)

        val report = repository.cleanupOrphans()

        assertEquals(1, report.deletedFiles)
        assertFalse(orphanFile.exists())
        assertTrue(referencedFile.exists())
        assertNotNull(repository.getAttachment(referenced.id))
    }

    @Test
    fun unsafeManagedPathsAreRejected() {
        assertNull(storage.resolveManagedFile("../outside.txt"))
        assertNull(storage.resolveManagedFile("/data/local/tmp/outside.txt"))
        assertNull(storage.resolveManagedFile("recordings/../attachments/../../outside.txt"))
    }

    @Test
    fun attachmentCanBeStoredDirectlyForPreviewAndSharedPathSafety() = runBlocking {
        val imported = storage.importStream(
            noteId,
            ByteArrayInputStream(PDF_BYTES),
            "direct.pdf",
            "application/pdf"
        )
        val id = database.attachmentDao().insert(
            AttachmentEntity(
                noteId = noteId,
                displayName = imported.displayName,
                mimeType = imported.mimeType,
                kind = imported.kind.name,
                storedPath = imported.relativePath,
                sizeBytes = imported.sizeBytes
            )
        )
        val loaded = repository.getAttachment(id)
        assertNotNull(loaded)
        assertFalse(repository.isMissing(loaded!!))
        assertTrue(repository.fileFor(loaded)!!.exists())
    }

    private fun fileUri(name: String, bytes: ByteArray) = run {
        val file = File(context.cacheDir, name).apply { writeBytes(bytes) }
        android.net.Uri.fromFile(file)
    }

    private suspend fun kotlinx.coroutines.flow.Flow<List<AttachmentEntity>>.firstIds(): List<Long> =
        first().map { it.id }

    companion object {
        private val PDF_BYTES = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".toByteArray()
    }
}
