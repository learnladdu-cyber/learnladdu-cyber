package com.ashis.notesphare.domain

enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingCompleted: Boolean = false,
    val transcriptionEndpoint: String = DEFAULT_TRANSCRIPTION_ENDPOINT,
    val transcriptionApiKey: String = "",
    val transcriptionModel: String = DEFAULT_TRANSCRIPTION_MODEL,
    val transcriptionLanguage: TranscriptionLanguage = TranscriptionLanguage.ENGLISH
) {
    val hasTranscriptionConfig: Boolean
        get() = transcriptionApiKey.isNotBlank() && transcriptionEndpoint.isNotBlank()

    companion object {
        const val DEFAULT_TRANSCRIPTION_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
        const val DEFAULT_TRANSCRIPTION_MODEL = "whisper-1"
    }
}

class DuplicateNameException(val kind: String, val name: String) :
    Exception("A $kind named \"$name\" already exists")
