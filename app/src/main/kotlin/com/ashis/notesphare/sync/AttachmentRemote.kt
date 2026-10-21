package com.ashis.notesphare.sync

import com.ashis.notesphare.auth.SupabaseConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

interface StorageRemoteDataSource {
    suspend fun upload(
        accessToken: String,
        bucket: String,
        path: String,
        file: File,
        mimeType: String
    )

    suspend fun download(
        accessToken: String,
        bucket: String,
        path: String,
        target: File
    )
}

class SupabaseStorageRemote(
    private val config: SupabaseConfig
) : StorageRemoteDataSource {

    override suspend fun upload(
        accessToken: String,
        bucket: String,
        path: String,
        file: File,
        mimeType: String
    ) = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() <= 0L) throw SyncException("The attachment file is missing.")
        val connection = open(
            method = "POST",
            url = objectUrl(bucket, path, authenticated = false),
            accessToken = accessToken
        )
        connection.setRequestProperty("Content-Type", mimeType)
        connection.setRequestProperty("x-upsert", "true")
        connection.setFixedLengthStreamingMode(file.length())
        connection.doOutput = true
        try {
            file.inputStream().use { input ->
                connection.outputStream.use { output -> input.copyTo(output) }
            }
            ensureSuccess(connection)
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun download(
        accessToken: String,
        bucket: String,
        path: String,
        target: File
    ) = withContext(Dispatchers.IO) {
        val parent = target.parentFile ?: throw SyncException("The attachment path is invalid.")
        if (!parent.exists() && !parent.mkdirs()) throw SyncException("Unable to create attachment storage.")
        val temporary = File(parent, ".${target.name}.download")
        val connection = open(
            method = "GET",
            url = objectUrl(bucket, path, authenticated = true),
            accessToken = accessToken
        )
        try {
            ensureSuccess(connection)
            connection.inputStream.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (temporary.length() <= 0L) throw SyncException("The downloaded attachment was empty.")
            if (target.exists() && !target.delete()) throw SyncException("Unable to replace the local attachment.")
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        } catch (throwable: Throwable) {
            temporary.delete()
            throw throwable
        } finally {
            connection.disconnect()
        }
    }

    private fun open(
        method: String,
        url: String,
        accessToken: String
    ): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 30_000
        readTimeout = 120_000
        instanceFollowRedirects = false
        useCaches = false
        setRequestProperty("apikey", config.anonKey)
        setRequestProperty("Authorization", "Bearer $accessToken")
        setRequestProperty("Accept", "application/json")
    }

    private fun objectUrl(bucket: String, path: String, authenticated: Boolean): String {
        val prefix = if (authenticated) "object/authenticated" else "object"
        val encodedPath = path.split('/').joinToString("/") { encode(it) }
        return "${config.normalizedUrl}/storage/v1/$prefix/${encode(bucket)}/$encodedPath"
    }

    private fun ensureSuccess(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status in 200..299) return
        val body = runCatching {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(500)
        }.getOrDefault("")
        val message = when (status) {
            401, 403 -> "Cloud storage access was refused. Sign in again."
            404 -> "The attachment is no longer in cloud storage."
            413 -> "The attachment is too large for cloud storage."
            429 -> "Cloud storage is busy. It will retry shortly."
            in 500..599 -> "Cloud storage is temporarily unavailable."
            else -> "Cloud storage request failed ($status)."
        }
        throw SyncException(if (body.isBlank()) message else "$message ${body.take(160)}")
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
