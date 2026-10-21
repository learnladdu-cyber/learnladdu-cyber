package com.ashis.notesphare.ui.screens.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashis.notesphare.data.local.NoteEntity
import com.ashis.notesphare.ui.components.ConfirmDialog
import com.ashis.notesphare.ui.components.EmptyState
import com.ashis.notesphare.ui.components.NoteCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    viewModel: NotesListViewModel,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyMessage: String,
    onCreateNote: (() -> Unit)? = null,
    onTogglePin: ((NoteEntity) -> Unit)? = null,
    onArchive: ((NoteEntity) -> Unit)? = null,
    onUnarchive: ((NoteEntity) -> Unit)? = null,
    onTrash: ((NoteEntity) -> Unit)? = null,
    onRestore: ((NoteEntity) -> Unit)? = null,
    onDeleteForever: ((NoteEntity) -> Unit)? = null,
    onEmptyTrash: (() -> Unit)? = null,
    restoreActionInMenu: Boolean = false
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        val current = message
        if (current != null) {
            snackbarHostState.showSnackbar(current)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (onEmptyTrash != null && notes.isNotEmpty()) {
                        IconButton(onClick = { confirmEmptyTrash = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Empty trash")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (onCreateNote != null) {
                FloatingActionButton(onClick = onCreateNote) {
                    Icon(Icons.Filled.Add, contentDescription = "New note")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (notes.isEmpty()) {
                EmptyState(
                    icon = emptyIcon,
                    title = emptyTitle,
                    message = emptyMessage,
                    actionLabel = if (onCreateNote != null) "Create a note" else null,
                    onAction = onCreateNote
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            onTogglePin = onTogglePin?.let { action -> { action(note) } },
                            onArchive = onArchive?.let { action -> { action(note) } },
                            onUnarchive = onUnarchive?.let { action -> { action(note) } },
                            onTrash = onTrash?.let { action ->
                                {
                                    action(note)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Moved to trash",
                                            actionLabel = if (onRestore != null) "Undo" else null
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onRestore?.invoke(note)
                                        }
                                    }
                                }
                            },
                            onRestore = if (restoreActionInMenu) {
                                onRestore?.let { action -> { action(note) } }
                            } else {
                                null
                            },
                            onDeleteForever = onDeleteForever?.let { { noteToDelete = note } }
                        )
                    }
                }
            }
        }
    }

    noteToDelete?.let { note ->
        ConfirmDialog(
            title = "Delete permanently?",
            message = "\"${note.title.trim().ifEmpty { "Untitled" }}\" will be deleted forever. This cannot be undone.",
            confirmLabel = "Delete forever",
            destructive = true,
            onConfirm = {
                onDeleteForever?.invoke(note)
                noteToDelete = null
            },
            onDismiss = { noteToDelete = null }
        )
    }

    if (confirmEmptyTrash) {
        ConfirmDialog(
            title = "Empty trash?",
            message = "All notes in the trash will be permanently deleted. This cannot be undone.",
            confirmLabel = "Empty trash",
            destructive = true,
            onConfirm = {
                onEmptyTrash?.invoke()
                confirmEmptyTrash = false
            },
            onDismiss = { confirmEmptyTrash = false }
        )
    }
}
