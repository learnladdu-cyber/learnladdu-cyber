package com.ashis.notesphare.domain

enum class AttachmentKind {
    IMAGE,
    PDF,
    DOCUMENT,
    AUDIO;

    companion object {
        fun fromStorage(value: String?): AttachmentKind =
            entries.firstOrNull { it.name == value } ?: DOCUMENT
    }
}

enum class RecordingSessionState {
    RECORDING,
    PAUSED,
    STOPPED,
    SAVED,
    FAILED;

    companion object {
        fun fromStorage(value: String?): RecordingSessionState =
            entries.firstOrNull { it.name == value } ?: RECORDING
    }
}

enum class TranscriptionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    companion object {
        fun fromStorage(value: String?): TranscriptionStatus =
            entries.firstOrNull { it.name == value } ?: PENDING
    }
}

enum class TranscriptionLanguage(val apiCode: String, val label: String) {
    ENGLISH("en", "English"),
    HINDI("hi", "Hindi");

    companion object {
        fun fromStorage(value: String?): TranscriptionLanguage =
            entries.firstOrNull { it.name == value } ?: ENGLISH
    }
}
