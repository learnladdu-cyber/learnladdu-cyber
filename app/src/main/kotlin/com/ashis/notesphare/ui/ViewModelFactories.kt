package com.ashis.notesphare.ui

import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ashis.notesphare.AppContainer
import com.ashis.notesphare.ui.screens.editor.EditorViewModel
import com.ashis.notesphare.ui.screens.folders.FoldersViewModel
import com.ashis.notesphare.ui.screens.home.HomeViewModel
import com.ashis.notesphare.ui.screens.notes.NoteListMode
import com.ashis.notesphare.ui.screens.notes.NotesListViewModel
import com.ashis.notesphare.ui.screens.search.SearchViewModel
import com.ashis.notesphare.ui.screens.auth.AuthViewModel
import com.ashis.notesphare.ui.screens.settings.SettingsViewModel
import com.ashis.notesphare.ui.screens.tags.TagsViewModel
import com.ashis.notesphare.ui.screens.attachments.AttachmentPreviewViewModel
import com.ashis.notesphare.ui.screens.recording.RecordingRecoveryViewModel
import com.ashis.notesphare.ui.screens.recording.StorageManagerViewModel
import com.ashis.notesphare.ui.screens.transcription.TranscriptionViewModel
import com.ashis.notesphare.recording.AudioPlayerViewModel

fun homeViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { HomeViewModel(container.noteRepository) }
}

fun searchViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { SearchViewModel(container.noteRepository) }
}

fun foldersViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { FoldersViewModel(container.folderRepository) }
}

fun tagsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { TagsViewModel(container.tagRepository) }
}

fun settingsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer { SettingsViewModel(container.settingsRepository) }
}

fun authViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        AuthViewModel(
            authRepository = container.authRepository,
            syncCoordinator = container.syncCoordinator,
            syncWriter = container.syncWriter,
            conflictResolver = container.conflictResolver,
            outboxDao = container.database.outboxDao(),
            conflictDao = container.database.conflictDao()
        )
    }
}

fun notesListViewModelFactory(
    container: AppContainer,
    mode: NoteListMode,
    entityId: Long
) = viewModelFactory {
    initializer {
        NotesListViewModel(
            noteRepository = container.noteRepository,
            folderRepository = container.folderRepository,
            tagRepository = container.tagRepository,
            mode = mode,
            entityId = entityId
        )
    }
}

fun editorViewModelFactory(
    container: AppContainer,
    noteId: Long,
    folderId: Long?
) = viewModelFactory {
    initializer {
        EditorViewModel(
            noteRepository = container.noteRepository,
            folderRepository = container.folderRepository,
            tagRepository = container.tagRepository,
            attachmentRepository = container.attachmentRepository,
            transcriptionRepository = container.transcriptionRepository,
            externalScope = container.applicationScope,
            requestedNoteId = noteId,
            initialFolderId = folderId,
            savedStateHandle = createSavedStateHandle()
        )
    }
}

fun attachmentPreviewViewModelFactory(
    container: AppContainer,
    attachmentId: Long
) = viewModelFactory {
    initializer {
        AttachmentPreviewViewModel(
            attachmentId = attachmentId,
            attachmentRepository = container.attachmentRepository
        )
    }
}

fun audioPlayerViewModelFactory(
    container: AppContainer,
    attachmentId: Long
) = viewModelFactory {
    initializer {
        AudioPlayerViewModel(
            attachmentId = attachmentId,
            attachmentRepository = container.attachmentRepository
        )
    }
}

fun transcriptionViewModelFactory(
    container: AppContainer,
    attachmentId: Long
) = viewModelFactory {
    initializer {
        TranscriptionViewModel(
            attachmentId = attachmentId,
            attachmentRepository = container.attachmentRepository,
            transcriptionRepository = container.transcriptionRepository,
            settingsRepository = container.settingsRepository
        )
    }
}

fun recordingRecoveryViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        RecordingRecoveryViewModel(container.recordingRepository)
    }
}

fun storageManagerViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        StorageManagerViewModel(
            attachmentRepository = container.attachmentRepository,
            recordingRepository = container.recordingRepository
        )
    }
}
