package com.ashis.notesphare.sync

import com.ashis.notesphare.auth.HttpTransport
import com.ashis.notesphare.auth.SupabaseConfig
import com.ashis.notesphare.auth.UrlConnectionHttpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class RemoteRow(
    val table: String,
    val revision: Long,
    val json: JSONObject
)

data class PullPage(
    val rows: List<RemoteRow>
)

enum class PushStatus { APPLIED, CONFLICT }

data class PushResult(
    val status: PushStatus,
    val remote: JSONObject?,
    val version: Long?
)

interface SyncRemoteDataSource {
    suspend fun push(
        accessToken: String,
        entityType: String,
        entityId: String,
        operation: String,
        baseVersion: Long,
        payload: JSONObject
    ): PushResult

    suspend fun pull(
        accessToken: String,
        table: String,
        afterRevision: Long?,
        limit: Int
    ): PullPage
}

class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SupabaseSyncRemote(
    private val config: SupabaseConfig,
    private val transport: HttpTransport = UrlConnectionHttpTransport()
) : SyncRemoteDataSource {

    override suspend fun push(
        accessToken: String,
        entityType: String,
        entityId: String,
        operation: String,
        baseVersion: Long,
        payload: JSONObject
    ): PushResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("p_entity_type", entityType)
            .put("p_entity_id", entityId)
            .put("p_operation", operation)
            .put("p_base_version", baseVersion)
            .put("p_payload", payload)
        val result = call(
            method = "POST",
            path = "/rest/v1/rpc/sync_push",
            accessToken = accessToken,
            body = body
        )
        val json = parseObject(result.body)
        when (json.optString("status")) {
            "applied" -> PushResult(
                status = PushStatus.APPLIED,
                remote = null,
                version = json.optLong("version").takeIf { json.has("version") }
            )
            "conflict" -> PushResult(
                status = PushStatus.CONFLICT,
                remote = json.optJSONObject("remote"),
                version = null
            )
            else -> throw SyncException("The cloud sync response was not understood.")
        }
    }

    override suspend fun pull(
        accessToken: String,
        table: String,
        afterRevision: Long?,
        limit: Int
    ): PullPage = withContext(Dispatchers.IO) {
        require(table in PULL_TABLES) { "Unsupported sync table" }
        val query = buildList {
            add("select=*")
            if (afterRevision != null) add("server_revision=gt.$afterRevision")
            add("order=server_revision.asc")
            add("limit=$limit")
        }.joinToString("&")
        val result = call(
            method = "GET",
            path = "/rest/v1/$table?$query",
            accessToken = accessToken
        )
        val array = runCatching { JSONArray(result.body) }.getOrElse {
            throw SyncException("The cloud returned an invalid change page.")
        }
        val rows = buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                add(
                    RemoteRow(
                        table = table,
                        revision = json.optLong("server_revision"),
                        json = json
                    )
                )
            }
        }
        PullPage(rows)
    }

    private suspend fun call(
        method: String,
        path: String,
        accessToken: String,
        body: JSONObject? = null
    ) = withContext(Dispatchers.IO) {
        if (!config.isConfigured) throw SyncException("Cloud sync is not configured.")
        val headers = mutableMapOf(
            "apikey" to config.anonKey,
            "Authorization" to "Bearer $accessToken",
            "Accept" to "application/json"
        )
        if (body != null) headers["Content-Type"] = "application/json; charset=utf-8"
        val result = transport.request(
            method = method,
            url = config.normalizedUrl + path,
            headers = headers,
            body = body?.toString()?.toByteArray(Charsets.UTF_8)
        )
        if (!result.isSuccess) {
            val message = runCatching {
                val json = JSONObject(result.body)
                listOf("message", "error_description", "error", "hint", "details")
                    .map { json.optString(it) }
                    .firstOrNull { it.isNotBlank() }
            }.getOrNull()
            throw SyncException(
                when (result.statusCode) {
                    401, 403 -> "Cloud access was refused. Sign in again."
                    429 -> "Cloud sync is busy. It will retry shortly."
                    in 500..599 -> "Cloud sync is temporarily unavailable."
                    else -> message ?: "Cloud sync failed (${result.statusCode})."
                }
            )
        }
        result
    }

    private fun parseObject(body: String): JSONObject =
        runCatching { JSONObject(body) }
            .getOrElse { throw SyncException("The cloud returned an invalid response.") }

    companion object {
        val PULL_TABLES = listOf(
            "folders",
            "tags",
            "notes",
            "checklist_items",
            "attachments",
            "transcriptions",
            "note_tags"
        )
    }
}
