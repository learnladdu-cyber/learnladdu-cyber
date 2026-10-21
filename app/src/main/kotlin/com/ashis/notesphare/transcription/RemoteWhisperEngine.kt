package com.ashis.notesphare.transcription

import com.ashis.notesphare.domain.AppSettings
import com.ashis.notesphare.domain.TranscriptionLanguage
import com.ashis.notesphare.files.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * A real transcription engine for recorded audio using an OpenAI-compatible
 * `/audio/transcriptions` endpoint. The implementation streams a multipart request and never
 * logs the API key or the audio contents. Users must consent before this engine is called.
 */
class RemoteWhisperEngine(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String
) : TranscriptionEngine {

    override val id: String = "remote-openai-whisper"
    override val displayName: String = "OpenAI-compatible Whisper"
    override val isRemote: Boolean = true
    override val usesOnDeviceProcessing: Boolean = false

    override suspend fun transcribe(
        request: TranscriptionRequest,
        onProgress: (Float) -> Unit
    ): TranscriptionResult = withContext(Dispatchers.IO) {
        validateConfiguration()
        val file = request.audioFile
        if (!file.isFile || file.length() <= 0L) {
            throw TranscriptionException("The recording file is missing or empty.")
        }
        if (file.length() > MAX_AUDIO_BYTES) {
            throw TranscriptionException(
                "This recording is larger than the 25 MB limit accepted by the configured service."
            )
        }
        val boundary = "NoteSphere-${UUID.randomUUID()}"
        val safeName = file.name.replace("\"", "").replace("\r", "").replace("\n", "")
        val fields = linkedMapOf(
            "model" to model,
            "language" to request.language.apiCode,
            "response_format" to "json"
        )
        val prefix = buildString {
            fields.forEach { (name, value) ->
                append("--$boundary\r\n")
                append("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                append(value)
                append("\r\n")
            }
            append("--$boundary\r\n")
            append(
                "Content-Disposition: form-data; name=\"file\"; filename=\"$safeName\"\r\n"
            )
            append("Content-Type: ${request.mimeType}\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val totalBytes = prefix.size + file.length() + suffix.size

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            useCaches = false
            connectTimeout = 30_000
            readTimeout = 300_000
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setFixedLengthStreamingMode(totalBytes.toInt())
        }
        try {
            onProgress(0.05f)
            BufferedOutputStream(connection.outputStream).use { output ->
                output.write(prefix)
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var written = prefix.size.toLong()
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(0.05f + 0.5f * (written.toFloat() / totalBytes.toFloat()))
                    }
                }
                output.write(suffix)
                output.flush()
            }
            onProgress(0.65f)
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                throw TranscriptionException(errorMessage(status, body))
            }
            onProgress(0.92f)
            val text = runCatching { JSONObject(body).optString("text") }
                .getOrNull()
                ?.trim()
                .orEmpty()
            if (text.isBlank()) {
                throw TranscriptionException("The transcription service returned no text.")
            }
            onProgress(1f)
            TranscriptionResult(
                text = text,
                engineId = id,
                language = request.language,
                usedRemoteProcessing = true
            )
        } catch (throwable: TranscriptionException) {
            throw throwable
        } catch (throwable: Throwable) {
            currentCoroutineContext().ensureActive()
            throw TranscriptionException(
                throwable.message ?: "Could not reach the transcription service.",
                throwable
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun validateConfiguration() {
        if (apiKey.isBlank()) {
            throw TranscriptionConfigurationException(
                "Add an API key in transcription setup before transcribing."
            )
        }
        if (model.isBlank()) {
            throw TranscriptionConfigurationException("Choose a transcription model.")
        }
        val parsed = runCatching { URL(endpoint) }.getOrNull()
            ?: throw TranscriptionConfigurationException("The transcription endpoint is not a valid URL.")
        if (parsed.protocol != "https" && parsed.host !in setOf("localhost", "127.0.0.1", "::1")) {
            throw TranscriptionConfigurationException(
                "Use HTTPS for remote transcription, or a localhost endpoint for a local server."
            )
        }
    }

    private fun errorMessage(status: Int, body: String): String {
        val serviceMessage = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
                ?: JSONObject(body).optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() }
        val prefix = when (status) {
            401, 403 -> "The transcription service rejected the API key."
            404 -> "The transcription endpoint or model was not found."
            413 -> "The service rejected the recording because it is too large."
            429 -> "The transcription service is rate limiting requests. Try again shortly."
            in 500..599 -> "The transcription service is temporarily unavailable."
            else -> "Transcription failed (HTTP $status)."
        }
        return if (serviceMessage != null) "$prefix $serviceMessage" else prefix
    }

    companion object {
        const val MAX_AUDIO_BYTES = 25L * 1024L * 1024L

        fun fromSettings(settings: AppSettings): RemoteWhisperEngine = RemoteWhisperEngine(
            endpoint = settings.transcriptionEndpoint.ifBlank {
                AppSettings.DEFAULT_TRANSCRIPTION_ENDPOINT
            },
            apiKey = settings.transcriptionApiKey,
            model = settings.transcriptionModel.ifBlank {
                AppSettings.DEFAULT_TRANSCRIPTION_MODEL
            }
        )
    }
}
