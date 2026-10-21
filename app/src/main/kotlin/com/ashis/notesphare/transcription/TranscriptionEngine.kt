package com.ashis.notesphare.transcription

import com.ashis.notesphare.domain.TranscriptionLanguage
import java.io.File

data class TranscriptionRequest(
    val audioFile: File,
    val mimeType: String,
    val language: TranscriptionLanguage
)

data class TranscriptionResult(
    val text: String,
    val engineId: String,
    val language: TranscriptionLanguage,
    val usedRemoteProcessing: Boolean
)

/**
 * Replaceable engine boundary. A future on-device engine can implement this interface without
 * changing the repository, UI or database. [isRemote] controls the mandatory consent gate.
 */
interface TranscriptionEngine {
    val id: String
    val displayName: String
    val isRemote: Boolean
    val usesOnDeviceProcessing: Boolean

    suspend fun transcribe(
        request: TranscriptionRequest,
        onProgress: (Float) -> Unit
    ): TranscriptionResult
}

class TranscriptionConfigurationException(message: String) : Exception(message)

class TranscriptionConsentRequiredException :
    Exception("This engine uploads audio. Review the remote-processing notice and consent first.")

class TranscriptionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
