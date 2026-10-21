package com.ashis.notesphare.ui.screens.tags

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
import androidx.compose.material.icons.filled.Label
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
import com.ashis.notesphare.data.local.TagWithCount
import com.ashis.notesphare.ui.components.ConfirmDialog
import com.ashis.notesphare.ui.components.EmptyState
import com.ashis.notesphare.ui.components.NameColorDialog
import com.ashis.notesphare.ui.components.noteColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    viewModel: TagsViewModel,
    onBack: () -> Unit,
    onOpenTag: (Long) -> Unit
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var tagToRename by remember { mutableStateOf<TagWithCount?>(null) }
    var tagToDelete by remember { mutableStateOf<TagWithCount?>(null) }

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
                title = { Text("Tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New tag")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (tags.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Label,
                    title = "No tags yet",
                    message = "Tags let a note belong to more than one group at once.",
                    actionLabel = "Create a tag",
                    onAction = { showCreateDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(tags, key = { it.tag.id }) { tagWithCount ->
                        TagRow(
                            tagWithCount = tagWithCount,
                            onClick = { onOpenTag(tagWithCount.tag.id) },
                            onRename = { tagToRename = tagWithCount },
                            onDelete = { tagToDelete = tagWithCount }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        NameColorDialog(
            title = "New tag",
            initialName = "",
            initialColor = 0,
            confirmLabel = "Create",
            nameExists = { name -> tags.any { it.tag.name.equals(name, ignoreCase = true) } },
            onConfirm = { name, color ->
                viewModel.createTag(name, color)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    tagToRename?.let { existing ->
        NameColorDialog(
            title = "Rename tag",
            initialName = existing.tag.name,
            initialColor = existing.tag.color,
            confirmLabel = "Save",
            nameExists = { name -> tags.any { it.tag.name.equals(name, ignoreCase = true) } },
            onConfirm = { name, color ->
                viewModel.updateTag(existing.tag.id, name, color)
                tagToRename = null
            },
            onDismiss = { tagToRename = null }
        )
    }

    tagToDelete?.let { existing ->
        ConfirmDialog(
            title = "Delete tag?",
            message = "The tag \"${existing.tag.name}\" will be removed from all notes. The notes themselves will be kept.",
            confirmLabel = "Delete tag",
            destructive = true,
            onConfirm = {
                viewModel.deleteTag(existing.tag.id)
                tagToDelete = null
            },
            onDismiss = { tagToDelete = null }
        )
    }
}

@Composable
private fun TagRow(
    tagWithCount: TagWithCount,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tag = tagWithCount.tag

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
                    .background(noteColor(tag.color, MaterialTheme.colorScheme.primary))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tag.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tagWithCount.noteCount} note" +
                        if (tagWithCount.noteCount == 1) "" else "s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Tag actions")
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
