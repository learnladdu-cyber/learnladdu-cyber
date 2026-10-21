package com.ashis.notesphare.ui

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ashis.notesphare.AppContainer
import com.ashis.notesphare.domain.AppSettings
import com.ashis.notesphare.ui.navigation.Routes
import com.ashis.notesphare.ui.screens.attachments.AttachmentPreviewScreen
import com.ashis.notesphare.ui.screens.auth.AuthScreen
import com.ashis.notesphare.ui.screens.auth.ResetPasswordScreen
import com.ashis.notesphare.ui.screens.cloud.CloudScreen
import com.ashis.notesphare.ui.screens.cloud.ConflictsScreen
import com.ashis.notesphare.ui.screens.editor.EditorScreen
import com.ashis.notesphare.ui.screens.editor.EditorViewModel
import com.ashis.notesphare.ui.screens.recording.AudioPlayerScreen
import com.ashis.notesphare.ui.screens.recording.RecordingRecoveryScreen
import com.ashis.notesphare.ui.screens.recording.RecordingScreen
import com.ashis.notesphare.ui.screens.recording.StorageManagerScreen
import com.ashis.notesphare.ui.screens.transcription.TranscriptionScreen
import com.ashis.notesphare.ui.screens.folders.FoldersScreen
import com.ashis.notesphare.ui.screens.home.HomeScreen
import com.ashis.notesphare.ui.screens.notes.NoteListMode
import com.ashis.notesphare.ui.screens.notes.NotesListScreen
import com.ashis.notesphare.ui.screens.notes.NotesListViewModel
import com.ashis.notesphare.ui.screens.onboarding.OnboardingScreen
import com.ashis.notesphare.ui.screens.search.SearchScreen
import com.ashis.notesphare.ui.screens.settings.SettingsScreen
import com.ashis.notesphare.ui.screens.splash.SplashScreen
import com.ashis.notesphare.ui.screens.tags.TagsScreen
import com.ashis.notesphare.recording.AudioPlayerViewModel
import com.ashis.notesphare.ui.theme.NoteSphereTheme
import kotlinx.coroutines.launch

@Composable
fun NoteSphereRoot(
    container: AppContainer,
    pendingNoteId: Long?,
    onPendingNoteConsumed: () -> Unit,
    authCallbackUri: Uri? = null,
    onAuthCallbackConsumed: () -> Unit = {}
) {
    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())
    val navController = rememberNavController()

    LaunchedEffect(authCallbackUri) {
        val uri = authCallbackUri ?: return@LaunchedEffect
        val result = container.authRepository.completeAuthCallback(uri)
        onAuthCallbackConsumed()
        val destination = when {
            result == null -> Routes.AUTH
            result.isRecovery -> Routes.AUTH_RESET
            else -> Routes.CLOUD
        }
        navController.navigate(destination) {
            popUpTo(Routes.SPLASH) { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(pendingNoteId) {
        val noteId = pendingNoteId ?: return@LaunchedEffect
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.SPLASH) { inclusive = true }
            launchSingleTop = true
        }
        navController.navigate(Routes.editor(noteId)) {
            launchSingleTop = true
        }
        onPendingNoteConsumed()
    }

    NoteSphereTheme(themeMode = settings.themeMode) {
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    settingsRepository = container.settingsRepository,
                    onFinished = { onboardingCompleted ->
                        val target = if (onboardingCompleted) Routes.HOME else Routes.ONBOARDING
                        navController.navigate(target) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.ONBOARDING) {
                val scope = rememberCoroutineScope()
                OnboardingScreen(
                    onComplete = {
                        scope.launch {
                            container.settingsRepository.setOnboardingCompleted(true)
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel(factory = homeViewModelFactory(container)),
                    onOpenNote = { navController.navigate(Routes.editor(it)) },
                    onNewNote = { navController.navigate(Routes.editor(0L)) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenFolders = { navController.navigate(Routes.FOLDERS) },
                    onOpenTags = { navController.navigate(Routes.TAGS) },
                    onOpenArchive = { navController.navigate(Routes.ARCHIVE) },
                    onOpenTrash = { navController.navigate(Routes.TRASH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenRecovery = { navController.navigate(Routes.RECORDING_RECOVERY) }
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    viewModel = viewModel(factory = searchViewModelFactory(container)),
                    onBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.editor(it)) }
                )
            }

            composable(Routes.FOLDERS) {
                FoldersScreen(
                    viewModel = viewModel(factory = foldersViewModelFactory(container)),
                    onBack = { navController.popBackStack() },
                    onOpenFolder = { navController.navigate(Routes.folder(it)) }
                )
            }

            composable(Routes.TAGS) {
                TagsScreen(
                    viewModel = viewModel(factory = tagsViewModelFactory(container)),
                    onBack = { navController.popBackStack() },
                    onOpenTag = { navController.navigate(Routes.tag(it)) }
                )
            }

            composable(
                route = Routes.FOLDER,
                arguments = listOf(navArgument("folderId") { type = NavType.LongType })
            ) { entry ->
                val folderId = entry.arguments?.getLong("folderId") ?: return@composable
                val notesViewModel: NotesListViewModel = viewModel(
                    factory = notesListViewModelFactory(container, NoteListMode.FOLDER, folderId)
                )
                NotesListScreen(
                    viewModel = notesViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.editor(it)) },
                    emptyIcon = Icons.Filled.Folder,
                    emptyTitle = "No notes in this folder",
                    emptyMessage = "Create a note here, or move an existing note into this folder from its editor.",
                    onCreateNote = { navController.navigate(Routes.editor(0L, folderId)) },
                    onTogglePin = notesViewModel::togglePin,
                    onArchive = notesViewModel::archive,
                    onTrash = notesViewModel::trash,
                    onRestore = notesViewModel::restore
                )
            }

            composable(
                route = Routes.TAG,
                arguments = listOf(navArgument("tagId") { type = NavType.LongType })
            ) { entry ->
                val tagId = entry.arguments?.getLong("tagId") ?: return@composable
                val notesViewModel: NotesListViewModel = viewModel(
                    factory = notesListViewModelFactory(container, NoteListMode.TAG, tagId)
                )
                NotesListScreen(
                    viewModel = notesViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.editor(it)) },
                    emptyIcon = Icons.Filled.Label,
                    emptyTitle = "No notes with this tag",
                    emptyMessage = "Add this tag to a note from the note editor.",
                    onTogglePin = notesViewModel::togglePin,
                    onArchive = notesViewModel::archive,
                    onTrash = notesViewModel::trash,
                    onRestore = notesViewModel::restore
                )
            }

            composable(Routes.ARCHIVE) {
                val notesViewModel: NotesListViewModel = viewModel(
                    factory = notesListViewModelFactory(
                        container,
                        NoteListMode.ARCHIVE,
                        0L
                    )
                )
                NotesListScreen(
                    viewModel = notesViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.editor(it)) },
                    emptyIcon = Icons.Filled.Archive,
                    emptyTitle = "Nothing archived",
                    emptyMessage = "Notes you archive are kept here and stay out of your main list.",
                    onUnarchive = notesViewModel::unarchive,
                    onTrash = notesViewModel::trash,
                    onRestore = notesViewModel::restore
                )
            }

            composable(Routes.TRASH) {
                val notesViewModel: NotesListViewModel = viewModel(
                    factory = notesListViewModelFactory(container, NoteListMode.TRASH, 0L)
                )
                NotesListScreen(
                    viewModel = notesViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenNote = { navController.navigate(Routes.editor(it)) },
                    emptyIcon = Icons.Filled.Delete,
                    emptyTitle = "Trash is empty",
                    emptyMessage = "Notes moved to trash appear here. You can restore them or delete them forever.",
                    onRestore = notesViewModel::restore,
                    onDeleteForever = notesViewModel::deleteForever,
                    onEmptyTrash = notesViewModel::emptyTrash,
                    restoreActionInMenu = true
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel = viewModel(factory = settingsViewModelFactory(container)),
                    onBack = { navController.popBackStack() },
                    onOpenStorageManager = { navController.navigate(Routes.STORAGE_MANAGER) },
                    onOpenRecovery = { navController.navigate(Routes.RECORDING_RECOVERY) },
                    onOpenCloud = { navController.navigate(Routes.CLOUD) }
                )
            }

            composable(Routes.AUTH) {
                AuthScreen(
                    viewModel = viewModel(factory = authViewModelFactory(container)),
                    onBack = { navController.popBackStack() },
                    onSignedIn = {
                        navController.navigate(Routes.CLOUD) {
                            popUpTo(Routes.AUTH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.AUTH_RESET) {
                ResetPasswordScreen(
                    viewModel = viewModel(factory = authViewModelFactory(container)),
                    onBack = { navController.popBackStack() },
                    onComplete = {
                        navController.navigate(Routes.CLOUD) {
                            popUpTo(Routes.AUTH_RESET) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.CLOUD) {
                CloudScreen(
                    viewModel = viewModel(factory = authViewModelFactory(container)),
                    onBack = { navController.popBackStack() },
                    onSignIn = { navController.navigate(Routes.AUTH) },
                    onReviewConflicts = { navController.navigate(Routes.CONFLICTS) }
                )
            }

            composable(Routes.CONFLICTS) {
                ConflictsScreen(
                    viewModel = viewModel(factory = authViewModelFactory(container)),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.EDITOR,
                arguments = listOf(
                    navArgument("noteId") { type = NavType.LongType },
                    navArgument("folderId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) { entry ->
                val noteId = entry.arguments?.getLong("noteId") ?: 0L
                val folderId = entry.arguments?.getLong("folderId")
                    ?.takeIf { it > 0L }
                val editorViewModel: EditorViewModel = viewModel(
                    factory = editorViewModelFactory(container, noteId, folderId)
                )
                EditorScreen(
                    viewModel = editorViewModel,
                    onBack = { navController.popBackStack() },
                    onRecordAudio = { navController.navigate(Routes.recording(it)) },
                    onOpenAttachment = { navController.navigate(Routes.attachmentPreview(it)) },
                    onOpenAudio = { navController.navigate(Routes.audioPlayer(it)) },
                    onOpenTranscription = { navController.navigate(Routes.transcription(it)) },
                    onOpenStorage = { navController.navigate(Routes.STORAGE_MANAGER) }
                )
            }

            composable(
                route = Routes.RECORDING,
                arguments = listOf(navArgument("noteId") { type = NavType.LongType })
            ) { entry ->
                val noteId = entry.arguments?.getLong("noteId") ?: return@composable
                RecordingScreen(
                    noteId = noteId,
                    onBack = { navController.popBackStack() },
                    onRecordingSaved = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.ATTACHMENT_PREVIEW,
                arguments = listOf(navArgument("attachmentId") { type = NavType.LongType })
            ) { entry ->
                val attachmentId = entry.arguments?.getLong("attachmentId") ?: return@composable
                AttachmentPreviewScreen(
                    viewModel = viewModel(
                        factory = attachmentPreviewViewModelFactory(container, attachmentId)
                    ),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Routes.AUDIO_PLAYER,
                arguments = listOf(navArgument("attachmentId") { type = NavType.LongType })
            ) { entry ->
                val attachmentId = entry.arguments?.getLong("attachmentId") ?: return@composable
                val playerViewModel: AudioPlayerViewModel = viewModel(
                    factory = audioPlayerViewModelFactory(container, attachmentId)
                )
                AudioPlayerScreen(
                    viewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenTranscription = {
                        navController.navigate(Routes.transcription(it))
                    }
                )
            }

            composable(
                route = Routes.TRANSCRIPTION,
                arguments = listOf(navArgument("attachmentId") { type = NavType.LongType })
            ) { entry ->
                val attachmentId = entry.arguments?.getLong("attachmentId") ?: return@composable
                TranscriptionScreen(
                    viewModel = viewModel(
                        factory = transcriptionViewModelFactory(container, attachmentId)
                    ),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.RECORDING_RECOVERY) {
                RecordingRecoveryScreen(
                    viewModel = viewModel(factory = recordingRecoveryViewModelFactory(container)),
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.STORAGE_MANAGER) {
                StorageManagerScreen(
                    viewModel = viewModel(factory = storageManagerViewModelFactory(container)),
                    onBack = { navController.popBackStack() },
                    onOpenRecovery = { navController.navigate(Routes.RECORDING_RECOVERY) }
                )
            }
        }
    }
}
