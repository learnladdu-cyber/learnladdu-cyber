package com.ashis.notesphare.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ashis.notesphare.domain.AppSettings
import com.ashis.notesphare.domain.ThemeMode
import com.ashis.notesphare.domain.TranscriptionLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "notesphare_settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            AppSettings(
                themeMode = ThemeMode.fromStorage(preferences[THEME_MODE]),
                onboardingCompleted = preferences[ONBOARDING_COMPLETED] ?: false,
                transcriptionEndpoint = preferences[TRANSCRIPTION_ENDPOINT]
                    ?: AppSettings.DEFAULT_TRANSCRIPTION_ENDPOINT,
                transcriptionApiKey = preferences[TRANSCRIPTION_API_KEY] ?: "",
                transcriptionModel = preferences[TRANSCRIPTION_MODEL]
                    ?: AppSettings.DEFAULT_TRANSCRIPTION_MODEL,
                transcriptionLanguage = TranscriptionLanguage.fromStorage(
                    preferences[TRANSCRIPTION_LANGUAGE]
                )
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode.storageValue
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setTranscriptionEndpoint(endpoint: String) {
        dataStore.edit { preferences ->
            preferences[TRANSCRIPTION_ENDPOINT] = endpoint.trim()
        }
    }

    suspend fun setTranscriptionApiKey(apiKey: String) {
        dataStore.edit { preferences ->
            preferences[TRANSCRIPTION_API_KEY] = apiKey.trim()
        }
    }

    suspend fun setTranscriptionModel(model: String) {
        dataStore.edit { preferences ->
            preferences[TRANSCRIPTION_MODEL] = model.trim().ifBlank {
                AppSettings.DEFAULT_TRANSCRIPTION_MODEL
            }
        }
    }

    suspend fun setTranscriptionLanguage(language: TranscriptionLanguage) {
        dataStore.edit { preferences ->
            preferences[TRANSCRIPTION_LANGUAGE] = language.name
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val TRANSCRIPTION_ENDPOINT = stringPreferencesKey("transcription_endpoint")
        val TRANSCRIPTION_API_KEY = stringPreferencesKey("transcription_api_key")
        val TRANSCRIPTION_MODEL = stringPreferencesKey("transcription_model")
        val TRANSCRIPTION_LANGUAGE = stringPreferencesKey("transcription_language")
    }
}
