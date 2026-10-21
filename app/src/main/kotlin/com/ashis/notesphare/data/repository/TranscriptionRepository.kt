package com.ashis.notesphare.data.repository

import com.ashis.notesphare.data.local.AttachmentDao
import com.ashis.notesphare.data.local.TranscriptionDao
import com.ashis.notesphare.data.local.TranscriptionEntity
import com.ashis.notesphare.domain.AttachmentKind
import com.ashis.notesphare.domain.TranscriptionLanguage
import com.ashis.notesphare.domain.TranscriptionStatus
import com.ashis.notesphare.files.FileStorageManager
import com.ashis.notesphare.sync.LocalSyncWriter
import com.ashis.notesphare.transcription.RemoteWhisperEngine
import com.ashis.notesphare.transcription.TranscriptionConfigurationException
import com.ashis.notesphare.transcription.TranscriptionConsentRequiredException
import com.ashis.notesphare.transcription.TranscriptionEngine
import com.ashis.notesphare.transcription.TranscriptionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

interface TranscriptionEngineProvider {
    suspend fun engine(): TranscriptionEngine
}

class SettingsTranscriptionEngineProvider(
    private val settingsRepository: SettingsRepository
) : TranscriptionEngineProvider {
    override suspend fun engine(): TranscriptionEngine {
        val settings = settingsRepository.settings.first()
        if (!settings.hasTranscriptionConfig) {
            throw TranscriptionConfigurationException(
                "Add a transcription API key and endpoint before transcribing."
            )
        }
        return RemoteWhisperEngine.fromSettings(settings)
    }
}

class TranscriptionRepository(
    private val attachmentDao: AttachmentDao,
    private val transcriptionDao: TranscriptionDao,
    private val storage: FileStorageManager,
    private val engineProvider: TranscriptionEngineProvider,
    private val sync: LocalSyncWriter? = null
) {

    fun observeForNote(noteId: Long): Flow<List<TranscriptionEntity>> =
        transcriptionDao.observeForNote(noteId)

    fun observeForAttachment(attachmentId: Long): Flow<TranscriptionEntity?> =
        transcriptionDao.observeForAttachment(attachmentId)

    suspend fun getForAttachment(attachmentId: Long): TranscriptionEntity? =
        transcriptionDao.getForAttachment(attachmentId)

    suspend fun transcribe(
        attachmentId: Long,
        language: TranscriptionLanguage,
        remoteConsent: Boolean,
        onProgress: (Float) -> Unit
    ): TranscriptionEntity = withContext(Dispatchers.IO) {
        val attachment = attachmentDao.getById(attachmentId)
            ?: throw TranscriptionConfigurationException("This recording no longer exists.")
        if (attachment.kind != AttachmentKind.AUDIO.name) {
            throw TranscriptionConfigurationException("Only recordings can be transcribed.")
        }
        val file = storage.resolveManagedFile(attachment.storedPath)
            ?: throw TranscriptionConfigurationException("The recording path is not valid.")
        if (!file.isFile || file.length() <= 0L) {
            throw TranscriptionConfigurationException("The recording file is missing.")
        }
        val engine = engineProvider.engine()
        if (engine.isRemote && !remoteConsent) throw TranscriptionConsentRequiredException()
        if (file.length() > RemoteWhisperEngine.MAX_AUDIO_BYTES) {
            throw TranscriptionConfigurationException(
                "This recording is larger than the 25 MB transcription limit."
            )
        }

        val existing = ensureRow(attachment.noteId, attachmentId, language, engine, attachment.ownerId)
        transcriptionDao.updateForAttachment(
            attachmentId = attachmentId,
            language = language.name,
            engineId = engine.id,
            text = existing.text,
            status = TranscriptionStatus.RUNNING.name,
            errorMessage = null,
            updatedAt = System.currentTimeMillis()
        )
        sync?.enqueueTranscription(attachmentId)
        try {
            val result = engine.transcribe(
                TranscriptionRequest(
                    audioFile = file,
                    mimeType = attachment.mimeType.ifBlank { "audio/mp4" },
                    language = language
                ),
                onProgress
            )
            val now = System.currentTimeMillis()
            transcriptionDao.updateForAttachment(
                attachmentId = attachmentId,
                language = language.name,
                engineId = result.engineId,
                text = result.text,
                status = TranscriptionStatus.COMPLETED.name,
                errorMessage = null,
                updatedAt = now
            )
            sync?.enqueueTranscription(attachmentId)
            transcriptionDao.getForAttachment(attachmentId)
                ?: error("Transcription was saved but could not be read back")
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable) {
                transcriptionDao.updateForAttachment(
                    attachmentId = attachmentId,
                    language = language.name,
                    engineId = engine.id,
                    text = existing.text,
                    status = TranscriptionStatus.CANCELLED.name,
                    errorMessage = "Transcription cancelled",
                    updatedAt = System.currentTimeMillis()
                )
                sync?.enqueueTranscription(attachmentId)
            }
            throw cancellation
        } catch (throwable: Throwable) {
            currentCoroutineContext().ensureActive()
            transcriptionDao.updateForAttachment(
                attachmentId = attachmentId,
                language = language.name,
                engineId = engine.id,
                text = existing.text,
                status = TranscriptionStatus.FAILED.name,
                errorMessage = throwable.message ?: "Transcription failed",
                updatedAt = System.currentTimeMillis()
            )
            sync?.enqueueTranscription(attachmentId)
            throw throwable
        }
    }

    suspend fun saveEditedText(attachmentId: Long, text: String): Boolean =
        withContext(Dispatchers.IO) {
            val attachment = attachmentDao.getById(attachmentId) ?: return@withContext false
            val now = System.currentTimeMillis()
            val existing = transcriptionDao.getForAttachment(attachmentId)
            if (existing == null) {
                transcriptionDao.insertIgnore(
                    TranscriptionEntity(
                        noteId = attachment.noteId,
                        attachmentId = attachmentId,
                        language = TranscriptionLanguage.ENGLISH.name,
                        engineId = "manual",
                        text = text,
                        status = TranscriptionStatus.COMPLETED.name,
                        createdAt = now,
                        updatedAt = now,
                        ownerId = attachment.ownerId
                    )
                )
            } else {
                transcriptionDao.updateText(attachmentId, text, now)
            }
            sync?.enqueueTranscription(attachmentId)
            true
        }

    private suspend fun ensureRow(
        noteId: Long,
        attachmentId: Long,
        language: TranscriptionLanguage,
        engine: TranscriptionEngine,
        ownerId: String?
    ): TranscriptionEntity {
        transcriptionDao.getForAttachment(attachmentId)?.let { return it }
        val now = System.currentTimeMillis()
        transcriptionDao.insertIgnore(
            TranscriptionEntity(
                noteId = noteId,
                attachmentId = attachmentId,
                language = language.name,
                engineId = engine.id,
                text = "",
                status = TranscriptionStatus.PENDING.name,
                createdAt = now,
                updatedAt = now,
                ownerId = ownerId
            )
        )
        sync?.enqueueTranscription(attachmentId)
        return transcriptionDao.getForAttachment(attachmentId)
            ?: error("Could not create a transcription record")
    }
}
