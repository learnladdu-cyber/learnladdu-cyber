package com.ashis.notesphare.auth

data class SupabaseConfig(
    val url: String,
    val anonKey: String,
    val redirectUrl: String,
    val storageBucket: String
) {
    val isConfigured: Boolean
        get() = url.startsWith("https://") && anonKey.isNotBlank()

    val normalizedUrl: String
        get() = url.trim().trimEnd('/')
}

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String,
    val email: String
) {
    fun isExpired(nowEpochSeconds: Long = System.currentTimeMillis() / 1000L): Boolean =
        expiresAtEpochSeconds <= nowEpochSeconds + 60L
}

enum class AuthStatus { INITIALIZING, SIGNED_OUT, SIGNED_IN, ERROR }

data class AuthState(
    val status: AuthStatus = AuthStatus.INITIALIZING,
    val session: AuthSession? = null,
    val isBusy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val pendingVerificationEmail: String? = null,
    val recoveryMode: Boolean = false
) {
    val isSignedIn: Boolean get() = status == AuthStatus.SIGNED_IN && session != null
    val userId: String? get() = session?.userId
}

data class AuthCallbackResult(
    val session: AuthSession,
    val isRecovery: Boolean
)

open class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

class AuthNotConfiguredException :
    AuthException("Cloud accounts are not configured in this build.")

class AuthSessionExpiredException :
    AuthException("Your session has expired. Please sign in again.")
