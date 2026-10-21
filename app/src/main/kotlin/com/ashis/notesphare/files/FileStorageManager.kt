package com.ashis.notesphare.files

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.ashis.notesphare.domain.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

data class ImportedFile(
    val file: File,
    val relativePath: String,
    val displayName: String,
    val mimeType: String,
    val kind: AttachmentKind,
    val sizeBytes: Long
)

class StorageException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Owns every byte NoteSphere writes. Database rows only ever contain paths relative to
 * [Context.getFilesDir], and all access is canonicalised so a malformed database row cannot
 * escape app-private storage.
 */
class FileStorageManager(
    private val context: Context,
    private val availableBytesProvider: () -> Long = { context.filesDir.usableSpace },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val filesRoot: File
        get() = context.filesDir.canonicalFile

    val attachmentsRoot: File
        get() = File(filesRoot, ATTACHMENTS_DIR).also { ensureDirectory(it) }

    val recordingsRoot: File
        get() = File(filesRoot, RECORDINGS_DIR).also { ensureDirectory(it) }

    fun resolveManagedFile(relativePath: String): File? {
        if (relativePath.isBlank() || relativePath.startsWith("/") ||
            relativePath.contains("..") || relativePath.contains('\u0000')
        ) {
            return null
        }
        return runCatching {
            val file = File(filesRoot, relativePath).canonicalFile
            val allowedRoots = listOf(attachmentsRoot.canonicalFile, recordingsRoot.canonicalFile)
            if (allowedRoots.any { file.path.startsWith(it.path + File.separator) }) file else null
        }.getOrNull()
    }

    fun importFromUri(noteId: Long, uri: Uri): ImportedFile {
        val resolver = context.contentResolver
        var name = "Attachment"
        var declaredSize: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            declaredSize = cursor.getLong(sizeIndex)
                        }
                    }
                }
        }
        val mime = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        if (declaredSize != null && declaredSize!! > MAX_ATTACHMENT_BYTES) {
            throw StorageException("This file is larger than the 50 MB attachment limit.")
        }
        val input = resolver.openInputStream(uri)
            ?: throw StorageException("The selected file could not be opened.")
        return input.use { importStream(noteId, it, name, mime) }
    }

    fun importStream(
        noteId: Long,
        input: InputStream,
        originalName: String,
        declaredMime: String?
    ): ImportedFile {
        val safeName = sanitizeDisplayName(originalName)
        val mime = normaliseMime(declaredMime, safeName)
        val kind = kindFor(mime, safeName)
            ?: throw StorageException("Unsupported file type. Choose an image, PDF or document.")
        val extension = extensionFor(safeName, mime)
        val targetDir = File(attachmentsRoot, noteId.toString()).also { ensureDirectory(it) }
        val target = File(targetDir, "${UUID.randomUUID()}.$extension")
        val temp = File(targetDir, "${target.name}.tmp")
        val required = MIN_FREE_AFTER_WRITE_BYTES
        if (availableBytesProvider() < required) {
            throw StorageException("Not enough free storage to add this file.")
        }
        var copied = 0L
        try {
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > MAX_ATTACHMENT_BYTES) {
                        throw StorageException("This file is larger than the 50 MB attachment limit.")
                    }
                    output.write(buffer, 0, read)
                }
            }
            if (copied == 0L) throw StorageException("The selected file is empty.")
            if (availableBytesProvider() < MIN_FREE_AFTER_WRITE_BYTES) {
                throw StorageException("Not enough free storage to add this file.")
            }
            validateContent(temp, kind)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            val relative = relativePath(target)
                ?: throw StorageException("The file could not be stored safely.")
            return ImportedFile(
                file = target,
                relativePath = relative,
                displayName = safeName,
                mimeType = mime,
                kind = kind,
                sizeBytes = copied
            )
        } catch (throwable: Throwable) {
            temp.delete()
            target.delete()
            throw if (throwable is StorageException) throwable
            else StorageException(throwable.message ?: "The file could not be copied.", throwable)
        }
    }

    fun newRecordingTempFile(): File {
        if (availableBytesProvider() < MIN_FREE_AFTER_WRITE_BYTES) {
            throw StorageException("Not enough free storage to start recording.")
        }
        return File(recordingsRoot, "recording_${UUID.randomUUID()}.m4a.part")
    }

    fun finalizeRecordingFile(tempFile: File): File {
        val canonical = tempFile.canonicalFile
        if (!canonical.path.startsWith(recordingsRoot.canonicalFile.path + File.separator)) {
            throw StorageException("The recording path is not managed by NoteSphere.")
        }
        if (!canonical.exists() || canonical.length() <= 0L) {
            throw StorageException("The recording file is empty or unavailable.")
        }
        val targetName = canonical.name.removeSuffix(".part")
        val target = File(canonical.parentFile, targetName)
        if (canonical == target) return canonical
        if (!canonical.renameTo(target)) {
            canonical.copyTo(target, overwrite = false)
            canonical.delete()
        }
        return target
    }

    fun relativePath(file: File): String? {
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return null
        val root = filesRoot.path + File.separator
        if (!canonical.path.startsWith(root)) return null
        return canonical.path.removePrefix(root)
    }

    fun fileSize(relativePath: String): Long =
        resolveManagedFile(relativePath)?.takeIf { it.isFile }?.length() ?: 0L

    fun exists(relativePath: String): Boolean =
        resolveManagedFile(relativePath)?.isFile == true

    fun deleteManagedFile(relativePath: String): Boolean {
        val file = resolveManagedFile(relativePath) ?: return false
        return file.delete()
    }

    fun listManagedFiles(): List<File> {
        val roots = listOf(attachmentsRoot, recordingsRoot)
        return roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile }.toList()
        }
    }

    fun listOrphanCandidates(referencedPaths: Set<String>, olderThan: Long): List<File> =
        listManagedFiles().filter { file ->
            val relative = relativePath(file) ?: return@filter false
            relative !in referencedPaths && file.lastModified() < olderThan
        }

    fun cleanupEmptyDirectories() {
        listOf(attachmentsRoot, recordingsRoot).forEach { root ->
            root.walkBottomUp().filter { it.isDirectory && it != root }.forEach { directory ->
                if (directory.listFiles()?.isEmpty() == true) directory.delete()
            }
        }
    }

    suspend fun totalManagedBytes(): Long = withContext(Dispatchers.IO) {
        listManagedFiles().sumOf { it.length() }
    }

    fun sanitizeDisplayName(original: String): String {
        val leaf = original.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
            .trim()
            .trim('.')
        val safe = leaf.ifBlank { "Attachment" }.take(MAX_NAME_LENGTH)
        return safe.ifBlank { "Attachment" }
    }

    private fun normaliseMime(declaredMime: String?, name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        val fromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        val normalised = declaredMime?.lowercase()?.substringBefore(';')?.trim()
        if (normalised == null || normalised.isBlank() || normalised == "application/octet-stream") {
            return fromExtension ?: "application/octet-stream"
        }
        if (normalised == "image/jpg") return "image/jpeg"
        return normalised
    }

    private fun kindFor(mime: String, name: String): AttachmentKind? {
        val extension = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") && extension in IMAGE_EXTENSIONS -> AttachmentKind.IMAGE
            mime == "application/pdf" || extension == "pdf" -> AttachmentKind.PDF
            mime in DOCUMENT_MIME_TYPES || extension in DOCUMENT_EXTENSIONS -> AttachmentKind.DOCUMENT
            else -> null
        }
    }

    private fun extensionFor(name: String, mime: String): String {
        val fromName = name.substringAfterLast('.', "").lowercase()
            .filter { it.isLetterOrDigit() }
            .take(10)
        if (fromName.isNotBlank()) return fromName
        return when {
            mime.startsWith("image/") -> mime.substringAfter('/').substringBefore('+')
            mime == "application/pdf" -> "pdf"
            mime == "text/plain" -> "txt"
            else -> "bin"
        }
    }

    private fun validateContent(file: File, kind: AttachmentKind) {
        when (kind) {
            AttachmentKind.IMAGE -> {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    throw StorageException("This image is damaged or unsupported.")
                }
            }
            AttachmentKind.PDF -> {
                file.inputStream().use { input ->
                    val header = ByteArray(5)
                    val read = input.read(header)
                    if (read < 5 || String(header, Charsets.US_ASCII) != "%PDF-") {
                        throw StorageException("This file is not a valid PDF.")
                    }
                }
            }
            AttachmentKind.DOCUMENT, AttachmentKind.AUDIO -> Unit
        }
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw StorageException("NoteSphere could not create its storage directory.")
        }
    }

    companion object {
        const val MAX_ATTACHMENT_BYTES = 50L * 1024L * 1024L
        const val MIN_FREE_AFTER_WRITE_BYTES = 5L * 1024L * 1024L
        const val ORPHAN_GRACE_PERIOD_MS = 24L * 60L * 60L * 1000L
        const val ATTACHMENTS_DIR = "attachments"
        const val RECORDINGS_DIR = "recordings"
        private const val MAX_NAME_LENGTH = 120

        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
        private val DOCUMENT_EXTENSIONS = setOf(
            "pdf", "txt", "md", "csv", "json", "xml", "rtf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
        )
        private val DOCUMENT_MIME_TYPES = setOf(
            "text/plain",
            "text/markdown",
            "text/csv",
            "text/rtf",
            "application/json",
            "application/xml",
            "text/xml",
            "application/rtf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        )
    }
}
