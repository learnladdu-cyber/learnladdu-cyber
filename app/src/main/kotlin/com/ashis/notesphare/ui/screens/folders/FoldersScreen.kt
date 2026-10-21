package com.ashis.notesphare.ui.screens.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashis.notesphare.data.local.FolderWithCount
import com.ashis.notesphare.ui.components.ConfirmDialog
import com.ashis.notesphare.ui.components.EmptyState
import com.ashis.notesphare.ui.components.NameColorDialog
import com.ashis.notesphare.ui.components.noteColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoldersScreen(
    viewModel: FoldersViewModel,
    onBack: () -> Unit,
    onOpenFolder: (Long) -> Unit
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<FolderWithCount?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderWithCount?>(null) }

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
                title = { Text("Folders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New folder")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (folders.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Folder,
                    title = "No folders yet",
                    message = "Create folders to group related notes. Deleting a folder never deletes its notes.",
                    actionLabel = "Create a folder",
                    onAction = { showCreateDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(folders, key = { it.folder.id }) { folderWithCount ->
                        FolderRow(
                            folderWithCount = folderWithCount,
                            onClick = { onOpenFolder(folderWithCount.folder.id) },
                            onRename = { folderToRename = folderWithCount },
                            onDelete = { folderToDelete = folderWithCount }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        NameColorDialog(
            title = "New folder",
            initialName = "",
            initialColor = 0,
            confirmLabel = "Create",
            nameExists = { name -> folders.any { it.folder.name.equals(name, ignoreCase = true) } },
            onConfirm = { name, color ->
                viewModel.createFolder(name, color)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    folderToRename?.let { existing ->
        NameColorDialog(
            title = "Rename folder",
            initialName = existing.folder.name,
            initialColor = existing.folder.color,
            confirmLabel = "Save",
            nameExists = { name -> folders.any { it.folder.name.equals(name, ignoreCase = true) } },
            onConfirm = { name, color ->
                viewModel.updateFolder(existing.folder.id, name, color)
                folderToRename = null
            },
            onDismiss = { folderToRename = null }
        )
    }

    folderToDelete?.let { existing ->
        ConfirmDialog(
            title = "Delete folder?",
            message = "The folder \"${existing.folder.name}\" will be deleted. Its ${existing.noteCount} note(s) will be kept and moved to No folder.",
            confirmLabel = "Delete folder",
            destructive = true,
            onConfirm = {
                viewModel.deleteFolder(existing.folder.id)
                folderToDelete = null
            },
            onDismiss = { folderToDelete = null }
        )
    }
}

@Composable
private fun FolderRow(
    folderWithCount: FolderWithCount,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val folder = folderWithCount.folder

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(noteColor(folder.color, MaterialTheme.colorScheme.primary))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folderWithCount.noteCount} note" +
                        if (folderWithCount.noteCount == 1) "" else "s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Folder actions")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}
