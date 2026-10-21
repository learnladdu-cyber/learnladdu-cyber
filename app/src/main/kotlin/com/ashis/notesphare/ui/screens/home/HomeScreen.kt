package com.ashis.notesphare.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashis.notesphare.data.local.NoteEntity
import com.ashis.notesphare.ui.components.EmptyState
import com.ashis.notesphare.ui.components.NoteCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenNote: (Long) -> Unit,
    onNewNote: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecovery: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        if (drawerState.currentValue != DrawerValue.Closed || drawerState.isAnimationRunning) {
            drawerState.snapTo(DrawerValue.Closed)
        }
    }

    val closeDrawerThen: (() -> Unit) -> Unit = { action ->
        scope.launch {
            drawerState.close()
            action()
        }
    }

    LaunchedEffect(message) {
        val current = message
        if (current != null) {
            snackbarHostState.showSnackbar(current)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(modifier = Modifier.fillMaxHeight()) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = "NoteSphere",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Your notes stay on this device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    NavigationDrawerItem(
                        label = { Text("Home") },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        selected = true,
                        onClick = {
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Folders") },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        selected = false,
                        onClick = { closeDrawerThen(onOpenFolders) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Tags") },
                        icon = { Icon(Icons.Filled.Label, contentDescription = null) },
                        selected = false,
                        onClick = { closeDrawerThen(onOpenTags) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Archive") },
                        icon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                        selected = false,
                        onClick = { closeDrawerThen(onOpenArchive) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Trash") },
                        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        selected = false,
                        onClick = { closeDrawerThen(onOpenTrash) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Recording recovery") },
                        icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                        selected = false,
                        onClick = { closeDrawerThen(onOpenRecovery) },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    Spacer(Modifier.weight(1f))
                    HorizontalDivider()
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        selected = false,
                        onClick = { closeDrawerThen(onOpenSettings) },
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("NoteSphere", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (drawerState.isClosed) {
                                    scope.launch { drawerState.open() }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "Open navigation")
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onNewNote) {
                    Icon(Icons.Filled.Add, contentDescription = "New note")
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    state.pinnedNotes.isEmpty() && state.recentNotes.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Filled.Edit,
                            title = "No notes yet",
                            message = "Capture a thought, a list or a reminder. Your notes are stored privately on this device.",
                            actionLabel = "Create your first note",
                            onAction = onNewNote
                        )
                    }
                    else -> {
                        NoteList(
                            pinnedNotes = state.pinnedNotes,
                            recentNotes = state.recentNotes,
                            onOpenNote = onOpenNote,
                            onTogglePin = viewModel::togglePin,
                            onArchive = viewModel::archive,
                            onTrash = { note ->
                                viewModel.trash(note)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Moved to trash",
                                        actionLabel = "Undo"
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.restore(note)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteList(
    pinnedNotes: List<NoteEntity>,
    recentNotes: List<NoteEntity>,
    onOpenNote: (Long) -> Unit,
    onTogglePin: (NoteEntity) -> Unit,
    onArchive: (NoteEntity) -> Unit,
    onTrash: (NoteEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (pinnedNotes.isNotEmpty()) {
            item(key = "pinned-header") {
                SectionHeader(icon = Icons.Filled.PushPin, label = "Pinned")
            }
            items(pinnedNotes, key = { "pinned-${it.id}" }) { note ->
                NoteCard(
                    note = note,
                    onClick = { onOpenNote(note.id) },
                    onTogglePin = { onTogglePin(note) },
                    onArchive = { onArchive(note) },
                    onTrash = { onTrash(note) }
                )
            }
        }
        if (recentNotes.isNotEmpty()) {
            item(key = "recent-header") {
                SectionHeader(
                    label = if (pinnedNotes.isEmpty()) "All notes" else "Recent"
                )
            }
            items(recentNotes, key = { "recent-${it.id}" }) { note ->
                NoteCard(
                    note = note,
                    onClick = { onOpenNote(note.id) },
                    onTogglePin = { onTogglePin(note) },
                    onArchive = { onArchive(note) },
                    onTrash = { onTrash(note) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
