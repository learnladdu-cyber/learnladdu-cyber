package com.ashis.notesphare.auth

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLDecoder

class AuthRepository(
    val config: SupabaseConfig,
    private val store: AuthSessionStore,
    private val scope: CoroutineScope,
    private val remote: AuthRemoteDataSource = SupabaseAuthRemote(config)
) {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val refreshMutex = Mutex()

    val isConfigured: Boolean get() = config.isConfigured

    init {
        scope.launch { restoreSession() }
    }

    suspend fun restoreSession() {
        val session = runCatching { store.readSession() }.getOrNull()
        if (session == null) {
            _state.value = AuthState(status = AuthStatus.SIGNED_OUT)
            return
        }
        val recovery = runCatching { store.readRecoveryMode() }.getOrDefault(false)
        if (session.isExpired()) {
            val refreshed = runCatching { refreshLocked(session) }
            refreshed.onSuccess { fresh ->
                _state.value = AuthState(
                    status = AuthStatus.SIGNED_IN,
                    session = fresh,
                    recoveryMode = recovery
                )
            }.onFailure { throwable ->
                _state.value = AuthState(
                    status = AuthStatus.SIGNED_OUT,
                    message = throwable.userMessage(),
                    recoveryMode = recovery
                )
            }
        } else {
            _state.value = AuthState(
                status = AuthStatus.SIGNED_IN,
                session = session,
                recoveryMode = recovery
            )
        }
    }

    suspend fun signUp(email: String, password: String): Boolean {
        val normalized = normalizeEmail(email)
        if (!validateCredentials(normalized, password)) return false
        setBusy()
        return try {
            if (!config.isConfigured) throw AuthNotConfiguredException()
            val verifier = Pkce.verifier()
            store.savePendingVerifier(verifier)
            store.savePendingEmail(normalized)
            val response = remote.signUp(normalized, password, Pkce.challenge(verifier))
            if (response.session != null) {
                persistSession(response.session, recovery = false)
                _state.update {
                    it.copy(
                        status = AuthStatus.SIGNED_IN,
                        session = response.session,
                        isBusy = false,
                        message = "Account created and signed in.",
                        error = null,
                        pendingVerificationEmail = null
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        status = AuthStatus.SIGNED_OUT,
                        session = null,
                        isBusy = false,
                        message = "Check ${response.email} to verify your account.",
                        error = null,
                        pendingVerificationEmail = response.email
                    )
                }
            }
            true
        } catch (throwable: Throwable) {
            fail(throwable)
            false
        }
    }

    suspend fun signIn(email: String, password: String): Boolean {
        val normalized = normalizeEmail(email)
        if (!validateCredentials(normalized, password)) return false
        setBusy()
        return try {
            if (!config.isConfigured) throw AuthNotConfiguredException()
            val session = remote.signIn(normalized, password)
            persistSession(session, recovery = false)
            _state.update {
                it.copy(
                    status = AuthStatus.SIGNED_IN,
                    session = session,
                    isBusy = false,
                    message = "Signed in. Your local notes are still available.",
                    error = null,
                    pendingVerificationEmail = null
                )
            }
            true
        } catch (throwable: Throwable) {
            fail(throwable)
            false
        }
    }

    suspend fun sendPasswordReset(email: String): Boolean {
        val normalized = normalizeEmail(email)
        if (!EMAIL_PATTERN.matches(normalized)) {
            _state.update { it.copy(error = "Enter a valid email address.", isBusy = false) }
            return false
        }
        setBusy()
        return try {
            if (!config.isConfigured) throw AuthNotConfiguredException()
            val verifier = Pkce.verifier()
            store.savePendingVerifier(verifier)
            store.savePendingEmail(normalized)
            remote.sendPasswordReset(normalized, Pkce.challenge(verifier))
            _state.update {
                it.copy(
                    isBusy = false,
                    message = "Password reset instructions were sent to $normalized.",
                    error = null,
                    pendingVerificationEmail = normalized
                )
            }
            true
        } catch (throwable: Throwable) {
            fail(throwable)
            false
        }
    }

    suspend fun resendVerification(email: String? = null): Boolean {
        val normalized = normalizeEmail(
            email ?: _state.value.pendingVerificationEmail ?: run {
                _state.update { it.copy(error = "Enter the email address used for signup.") }
                return false
            }
        )
        setBusy()
        return try {
            if (!config.isConfigured) throw AuthNotConfiguredException()
            val verifier = runCatching { store.readPendingVerifier() }.getOrNull()
                ?: Pkce.verifier().also { store.savePendingVerifier(it) }
            store.savePendingEmail(normalized)
            remote.resendVerification(normalized, Pkce.challenge(verifier))
            _state.update {
                it.copy(
                    isBusy = false,
                    message = "Verification email sent to $normalized.",
                    error = null,
                    pendingVerificationEmail = normalized
                )
            }
            true
        } catch (throwable: Throwable) {
            fail(throwable)
            false
        }
    }

    /**
     * Handles both PKCE links (?code=...) and implicit-flow links (#access_token=...).
     * The token is validated against /auth/v1/user before being saved.
     */
    suspend fun completeAuthCallback(uri: Uri): AuthCallbackResult? {
        setBusy()
        return try {
            val params = parseCallback(uri)
            if (params.error != null) throw AuthException(params.error)
            val isRecovery = params.type == "recovery"
            val session = if (params.code != null) {
                val verifier = store.readPendingVerifier()
                    ?: throw AuthException("This link was opened on another device or has expired.")
                remote.exchangeCode(params.code, verifier)
            } else {
                val access = params.accessToken
                    ?: throw AuthException("This link does not contain a valid session.")
                val refresh = params.refreshToken
                    ?: throw AuthException("This link does not contain a refresh token.")
                val (userId, email) = remote.fetchUser(access)
                AuthSession(
                    accessToken = access,
                    refreshToken = refresh,
                    expiresAtEpochSeconds = System.currentTimeMillis() / 1000L + params.expiresIn,
                    userId = userId,
                    email = email
                )
            }
            val (userId, email) = remote.fetchUser(session.accessToken)
            val validated = session.copy(userId = userId, email = email)
            persistSession(validated, recovery = isRecovery)
            runCatching { store.savePendingEmail(email) }
            _state.update {
                it.copy(
                    status = AuthStatus.SIGNED_IN,
                    session = validated,
                    isBusy = false,
                    error = null,
                    recoveryMode = isRecovery,
                    message = if (isRecovery) "Choose a new password." else "Email verified."
                )
            }
            AuthCallbackResult(validated, isRecovery)
        } catch (throwable: Throwable) {
            fail(throwable)
            null
        }
    }

    suspend fun updatePassword(newPassword: String): Boolean {
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            _state.update {
                it.copy(error = "Use at least $MIN_PASSWORD_LENGTH characters.", isBusy = false)
            }
            return false
        }
        setBusy()
        return try {
            val token = validAccessToken() ?: throw AuthSessionExpiredException()
            remote.updatePassword(token, newPassword)
            runCatching { store.setRecoveryMode(false) }
            _state.update {
                it.copy(
                    isBusy = false,
                    recoveryMode = false,
                    message = "Password updated.",
                    error = null
                )
            }
            true
        } catch (throwable: Throwable) {
            fail(throwable)
            false
        }
    }

    suspend fun signOut() {
        val token = _state.value.session?.accessToken
        setBusy()
        if (token != null && config.isConfigured) {
            runCatching { remote.logout(token) }
        }
        runCatching { store.clearSession() }
        _state.value = AuthState(
            status = AuthStatus.SIGNED_OUT,
            message = "Signed out. Local notes and recordings remain on this device."
        )
    }

    suspend fun deleteAccount(): Boolean {
        setBusy()
        return try {
            val token = validAccessToken() ?: throw AuthSessionExpiredException()
            remote.deleteAccount(token)
            runCatching { store.clearSession() }
            _state.value = AuthState(
                status = AuthStatus.SIGNED_OUT,
                message = "Cloud account deleted. Local files remain until you remove them."
            )
            true
        } catch (throwable: Throwable) {
            fail(throwable)
            false
        }
    }

    suspend fun validAccessToken(): String? {
        if (!config.isConfigured) return null
        val session = _state.value.session ?: runCatching { store.readSession() }.getOrNull()
            ?: throw AuthSessionExpiredException()
        if (!session.isExpired()) return session.accessToken
        return refreshLocked(session).accessToken
    }

    suspend fun currentSession(): AuthSession? =
        _state.value.session ?: runCatching { store.readSession() }.getOrNull()

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun refreshLocked(stale: AuthSession): AuthSession = refreshMutex.withLock {
        val latest = runCatching { store.readSession() }.getOrNull() ?: stale
        if (!latest.isExpired()) return@withLock latest
        if (!config.isConfigured) throw AuthNotConfiguredException()
        val refreshed = remote.refresh(latest.refreshToken)
        persistSession(refreshed, recovery = _state.value.recoveryMode)
        _state.update {
            it.copy(status = AuthStatus.SIGNED_IN, session = refreshed, error = null)
        }
        refreshed
    }

    private suspend fun persistSession(session: AuthSession, recovery: Boolean) {
        store.saveSession(session)
        store.setRecoveryMode(recovery)
    }

    private fun setBusy() {
        _state.update { it.copy(isBusy = true, error = null, message = null) }
    }

    private fun fail(throwable: Throwable) {
        _state.update { current ->
            current.copy(
                status = if (current.session == null) AuthStatus.ERROR else AuthStatus.SIGNED_IN,
                isBusy = false,
                error = throwable.userMessage()
            )
        }
    }

    private fun validateCredentials(email: String, password: String): Boolean {
        val message = when {
            !EMAIL_PATTERN.matches(email) -> "Enter a valid email address."
            password.length < MIN_PASSWORD_LENGTH ->
                "Use at least $MIN_PASSWORD_LENGTH characters for your password."
            else -> null
        }
        if (message != null) {
            _state.update { it.copy(isBusy = false, error = message) }
            return false
        }
        return true
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    private fun parseCallback(uri: Uri): CallbackParams {
        val values = linkedMapOf<String, String>()
        fun addQuery(raw: String?) {
            if (raw.isNullOrBlank()) return
            raw.split('&').forEach { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) return@forEach
                val key = URLDecoder.decode(pair.substring(0, index), "UTF-8")
                val value = URLDecoder.decode(pair.substring(index + 1), "UTF-8")
                values.putIfAbsent(key, value)
            }
        }
        addQuery(uri.encodedQuery)
        addQuery(uri.encodedFragment)
        uri.queryParameterNames.forEach { name ->
            uri.getQueryParameter(name)?.let { values.putIfAbsent(name, it) }
        }
        return CallbackParams(
            accessToken = values["access_token"],
            refreshToken = values["refresh_token"],
            code = values["code"],
            type = values["type"],
            error = values["error_description"] ?: values["error"],
            expiresIn = values["expires_in"]?.toLongOrNull()?.coerceAtLeast(60L) ?: 3600L
        )
    }

    private fun Throwable.userMessage(): String = when (this) {
        is AuthException -> message ?: "Authentication failed."
        is java.net.UnknownHostException -> "You are offline. Local notes still work."
        else -> message?.takeIf { it.isNotBlank() }?.let { "Authentication failed. Try again." }
            ?: "Authentication failed. Try again."
    }

    private data class CallbackParams(
        val accessToken: String?,
        val refreshToken: String?,
        val code: String?,
        val type: String?,
        val error: String?,
        val expiresIn: Long
    )

    companion object {
        const val MIN_PASSWORD_LENGTH = 8
        private val EMAIL_PATTERN =
            Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    }
}
