package com.ashis.notesphare.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.authDataStore by preferencesDataStore(name = "notesphare_auth")

interface AuthSessionStore {
    suspend fun readSession(): AuthSession?
    suspend fun saveSession(session: AuthSession)
    suspend fun clearSession()
    suspend fun readPendingVerifier(): String?
    suspend fun savePendingVerifier(verifier: String)
    suspend fun readPendingEmail(): String?
    suspend fun savePendingEmail(email: String)
    suspend fun readRecoveryMode(): Boolean
    suspend fun setRecoveryMode(enabled: Boolean)
}

class DataStoreAuthSessionStore(
    context: Context,
    private val cipher: TokenCipher = KeystoreTokenCipher()
) : AuthSessionStore {

    private val dataStore = context.applicationContext.authDataStore

    private val preferences = dataStore.data.catch { throwable ->
        if (throwable is IOException) emit(emptyPreferences()) else throw throwable
    }

    override suspend fun readSession(): AuthSession? {
        val prefs = preferences.first()
        val access = decryptOrNull(prefs[ACCESS_TOKEN]) ?: return null
        val refresh = decryptOrNull(prefs[REFRESH_TOKEN]) ?: return null
        val userId = prefs[USER_ID] ?: return null
        val email = prefs[EMAIL] ?: return null
        return AuthSession(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = prefs[EXPIRES_AT] ?: 0L,
            userId = userId,
            email = email
        )
    }

    override suspend fun saveSession(session: AuthSession) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = cipher.encrypt(session.accessToken)
            prefs[REFRESH_TOKEN] = cipher.encrypt(session.refreshToken)
            prefs[EXPIRES_AT] = session.expiresAtEpochSeconds
            prefs[USER_ID] = session.userId
            prefs[EMAIL] = session.email
        }
    }

    override suspend fun clearSession() {
        dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN)
            prefs.remove(REFRESH_TOKEN)
            prefs.remove(EXPIRES_AT)
            prefs.remove(USER_ID)
            prefs.remove(EMAIL)
            prefs.remove(PENDING_VERIFIER)
            prefs.remove(PENDING_EMAIL)
            prefs.remove(RECOVERY_MODE)
        }
    }

    override suspend fun readPendingVerifier(): String? {
        val encrypted = preferences.first()[PENDING_VERIFIER] ?: return null
        return decryptOrNull(encrypted)
    }

    override suspend fun savePendingVerifier(verifier: String) {
        dataStore.edit { it[PENDING_VERIFIER] = cipher.encrypt(verifier) }
    }

    override suspend fun readPendingEmail(): String? = preferences.first()[PENDING_EMAIL]

    override suspend fun savePendingEmail(email: String) {
        dataStore.edit { it[PENDING_EMAIL] = email }
    }

    override suspend fun readRecoveryMode(): Boolean = preferences.first()[RECOVERY_MODE] ?: false

    override suspend fun setRecoveryMode(enabled: Boolean) {
        dataStore.edit { it[RECOVERY_MODE] = enabled }
    }

    private fun decryptOrNull(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return runCatching { cipher.decrypt(value) }.getOrNull()
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val PENDING_VERIFIER = stringPreferencesKey("pending_verifier")
        val PENDING_EMAIL = stringPreferencesKey("pending_email")
        val RECOVERY_MODE = booleanPreferencesKey("recovery_mode")
    }
}

interface TokenCipher {
    fun encrypt(plainText: String): String
    fun decrypt(cipherText: String): String
}

/**
 * Tokens are encrypted with an AES-GCM key that never leaves Android Keystore. The DataStore
 * itself is app-private, but encrypting the bearer/refresh tokens adds protection if a backup
 * or filesystem copy is exposed.
 */
class KeystoreTokenCipher : TokenCipher {

    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decrypt(cipherText: String): String {
        val payload = Base64.decode(cipherText, Base64.NO_WRAP)
        if (payload.size <= IV_SIZE) throw AuthException("Stored session is invalid.")
        val iv = payload.copyOfRange(0, IV_SIZE)
        val encrypted = payload.copyOfRange(IV_SIZE, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "notesphare_auth_tokens"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
