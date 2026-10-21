package com.ashis.notesphare.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SEARCH = "search"
    const val FOLDERS = "folders"
    const val TAGS = "tags"
    const val ARCHIVE = "archive"
    const val TRASH = "trash"
    const val SETTINGS = "settings"
    const val STORAGE_MANAGER = "storage-manager"
    const val RECORDING_RECOVERY = "recording-recovery"
    const val AUTH = "auth"
    const val AUTH_RESET = "auth-reset"
    const val CLOUD = "cloud"
    const val CONFLICTS = "conflicts"

    const val FOLDER = "folder/{folderId}"
    const val TAG = "tag/{tagId}"
    const val EDITOR = "editor/{noteId}?folderId={folderId}"
    const val RECORDING = "recording/{noteId}"
    const val ATTACHMENT_PREVIEW = "attachment/{attachmentId}"
    const val AUDIO_PLAYER = "audio/{attachmentId}"
    const val TRANSCRIPTION = "transcription/{attachmentId}"

    fun folder(folderId: Long): String = "folder/$folderId"

    fun tag(tagId: Long): String = "tag/$tagId"

    fun editor(noteId: Long, folderId: Long? = null): String =
        "editor/$noteId?folderId=${folderId ?: -1L}"

    fun recording(noteId: Long): String = "recording/$noteId"

    fun attachmentPreview(attachmentId: Long): String = "attachment/$attachmentId"

    fun audioPlayer(attachmentId: Long): String = "audio/$attachmentId"

    fun transcription(attachmentId: Long): String = "transcription/$attachmentId"
}
