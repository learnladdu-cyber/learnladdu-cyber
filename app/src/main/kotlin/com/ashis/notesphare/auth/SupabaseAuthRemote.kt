package com.ashis.notesphare.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

data class SignUpResponse(
    val session: AuthSession?,
    val userId: String?,
    val email: String,
    val requiresEmailVerification: Boolean
)

interface AuthRemoteDataSource {
    suspend fun signUp(
        email: String,
        password: String,
        codeChallenge: String
    ): SignUpResponse

    suspend fun signIn(email: String, password: String): AuthSession
    suspend fun refresh(refreshToken: String): AuthSession
    suspend fun exchangeCode(authCode: String, verifier: String): AuthSession
    suspend fun sendPasswordReset(email: String, codeChallenge: String)
    suspend fun resendVerification(email: String, codeChallenge: String)
    suspend fun updatePassword(accessToken: String, newPassword: String)
    suspend fun logout(accessToken: String)
    suspend fun deleteAccount(accessToken: String)
    suspend fun fetchUser(accessToken: String): Pair<String, String>
}

class SupabaseAuthRemote(
    private val config: SupabaseConfig,
    private val transport: HttpTransport = UrlConnectionHttpTransport()
) : AuthRemoteDataSource {

    override suspend fun signUp(
        email: String,
        password: String,
        codeChallenge: String
    ): SignUpResponse = withContext(Dispatchers.IO) {
        requireConfigured()
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("code_challenge", codeChallenge)
            .put("code_challenge_method", "s256")
        val result = call(
            method = "POST",
            path = "/auth/v1/signup",
            query = mapOf("redirect_to" to config.redirectUrl),
            body = body
        )
        val json = parseJson(result.body)
        val user = json.optJSONObject("user")
        val userId = user?.optString("id")?.takeIf { it.isNotBlank() }
        val parsedEmail = user?.optString("email")?.takeIf { it.isNotBlank() } ?: email
        val session = parseSession(json)
        SignUpResponse(
            session = session,
            userId = userId,
            email = parsedEmail,
            requiresEmailVerification = session == null
        )
    }

    override suspend fun signIn(email: String, password: String): AuthSession =
        withContext(Dispatchers.IO) {
            requireConfigured()
            val result = call(
                method = "POST",
                path = "/auth/v1/token",
                query = mapOf("grant_type" to "password"),
                body = JSONObject().put("email", email).put("password", password)
            )
            parseSession(parseJson(result.body))
                ?: throw AuthException("Sign in succeeded but no session was returned.")
        }

    override suspend fun refresh(refreshToken: String): AuthSession =
        withContext(Dispatchers.IO) {
            requireConfigured()
            val result = call(
                method = "POST",
                path = "/auth/v1/token",
                query = mapOf("grant_type" to "refresh_token"),
                body = JSONObject().put("refresh_token", refreshToken)
            )
            parseSession(parseJson(result.body))
                ?: throw AuthSessionExpiredException()
        }

    override suspend fun exchangeCode(authCode: String, verifier: String): AuthSession =
        withContext(Dispatchers.IO) {
            requireConfigured()
            val result = call(
                method = "POST",
                path = "/auth/v1/token",
                query = mapOf("grant_type" to "pkce"),
                body = JSONObject()
                    .put("auth_code", authCode)
                    .put("code_verifier", verifier)
            )
            parseSession(parseJson(result.body))
                ?: throw AuthException("The sign-in link could not be completed.")
        }

    override suspend fun sendPasswordReset(email: String, codeChallenge: String) {
        withContext(Dispatchers.IO) {
            requireConfigured()
            call(
                method = "POST",
                path = "/auth/v1/recover",
                query = mapOf("redirect_to" to config.redirectUrl),
                body = JSONObject().put("email", email)
                    .put("code_challenge", codeChallenge)
                    .put("code_challenge_method", "s256")
            )
        }
    }

    override suspend fun resendVerification(email: String, codeChallenge: String) {
        withContext(Dispatchers.IO) {
            requireConfigured()
            call(
                method = "POST",
                path = "/auth/v1/resend",
                query = mapOf("redirect_to" to config.redirectUrl),
                body = JSONObject()
                    .put("type", "signup")
                    .put("email", email)
                    .put("code_challenge", codeChallenge)
                    .put("code_challenge_method", "s256")
            )
        }
    }

    override suspend fun updatePassword(accessToken: String, newPassword: String) {
        withContext(Dispatchers.IO) {
            requireConfigured()
            call(
                method = "PUT",
                path = "/auth/v1/user",
                accessToken = accessToken,
                body = JSONObject().put("password", newPassword)
            )
        }
    }

    override suspend fun logout(accessToken: String) {
        withContext(Dispatchers.IO) {
            requireConfigured()
            call(
                method = "POST",
                path = "/auth/v1/logout",
                accessToken = accessToken
            )
        }
    }

    override suspend fun deleteAccount(accessToken: String) {
        withContext(Dispatchers.IO) {
            requireConfigured()
            call(
                method = "DELETE",
                path = "/auth/v1/user",
                accessToken = accessToken
            )
        }
    }

    override suspend fun fetchUser(accessToken: String): Pair<String, String> =
        withContext(Dispatchers.IO) {
            requireConfigured()
            val result = call(
                method = "GET",
                path = "/auth/v1/user",
                accessToken = accessToken
            )
            val json = parseJson(result.body)
            val id = json.optString("id")
            val email = json.optString("email")
            if (id.isBlank() || email.isBlank()) {
                throw AuthException("The account details could not be verified.")
            }
            id to email
        }

    private suspend fun call(
        method: String,
        path: String,
        query: Map<String, String> = emptyMap(),
        accessToken: String? = null,
        body: JSONObject? = null
    ): HttpResult {
        val queryString = if (query.isEmpty()) "" else query.entries.joinToString("&", prefix = "?") {
            "${encode(it.key)}=${encode(it.value)}"
        }
        val headers = mutableMapOf(
            "apikey" to config.anonKey,
            "Accept" to "application/json"
        )
        if (accessToken != null) headers["Authorization"] = "Bearer $accessToken"
        if (body != null) headers["Content-Type"] = "application/json; charset=utf-8"
        val result = transport.request(
            method = method,
            url = config.normalizedUrl + path + queryString,
            headers = headers,
            body = body?.toString()?.toByteArray(Charsets.UTF_8)
        )
        if (!result.isSuccess) throw authError(result)
        return result
    }

    private fun parseSession(json: JSONObject): AuthSession? {
        val sessionJson = json.optJSONObject("session") ?: json
        val accessToken = sessionJson.optString("access_token")
        val refreshToken = sessionJson.optString("refresh_token")
        if (accessToken.isBlank() || refreshToken.isBlank()) return null
        val user = sessionJson.optJSONObject("user") ?: json.optJSONObject("user")
        val userId = user?.optString("id").orEmpty()
        val email = user?.optString("email").orEmpty()
        if (userId.isBlank() || email.isBlank()) {
            throw AuthException("The authentication response was incomplete.")
        }
        val nowSeconds = System.currentTimeMillis() / 1000L
        val expiresAt = sessionJson.optLong("expires_at").takeIf { it > 0 }
            ?: (nowSeconds + sessionJson.optLong("expires_in", 3600L))
        return AuthSession(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAtEpochSeconds = expiresAt,
            userId = userId,
            email = email
        )
    }

    private fun authError(result: HttpResult): AuthException {
        val message = runCatching {
            val json = JSONObject(result.body)
            listOf("msg", "error_description", "message", "error")
                .map { json.optString(it) }
                .firstOrNull { it.isNotBlank() }
        }.getOrNull()
        return when (result.statusCode) {
            401, 403 -> AuthException(message ?: "Those credentials were not accepted.")
            422 -> AuthException(message ?: "Check the email address and password requirements.")
            429 -> AuthException("Too many attempts. Wait a moment and try again.")
            in 500..599 -> AuthException("The cloud service is temporarily unavailable.")
            else -> AuthException(message ?: "The account request failed.")
        }
    }

    private fun parseJson(body: String): JSONObject = runCatching { JSONObject(body) }
        .getOrElse { throw AuthException("The cloud service returned an invalid response.") }

    private fun requireConfigured() {
        if (!config.isConfigured) throw AuthNotConfiguredException()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
