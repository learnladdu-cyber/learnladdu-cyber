package com.ashis.notesphare.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpResult(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap()
) {
    val isSuccess: Boolean get() = statusCode in 200..299
}

interface HttpTransport {
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray? = null
    ): HttpResult
}

class UrlConnectionHttpTransport : HttpTransport {

    override suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: ByteArray?
    ): HttpResult = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 30_000
            instanceFollowRedirects = false
            useCaches = false
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
                outputStream.use { it.write(body) }
            }
        }
        try {
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val responseHeaders = connection.headerFields
                .filterKeys { it != null }
                .mapValues { (_, value) -> value.joinToString(";") }
            HttpResult(status, responseBody, responseHeaders)
        } catch (throwable: IOException) {
            throw AuthException("Network request failed. Check your connection.", throwable)
        } finally {
            connection.disconnect()
        }
    }
}
