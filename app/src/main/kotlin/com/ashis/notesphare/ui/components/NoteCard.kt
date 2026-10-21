package com.ashis.notesphare.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashis.notesphare.data.local.NoteEntity
import com.ashis.notesphare.util.formatRelativeTime
import com.ashis.notesphare.util.noteSnippet

@Composable
fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTogglePin: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onUnarchive: (() -> Unit)? = null,
    onTrash: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    onDeleteForever: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasMenu = onTogglePin != null || onArchive != null || onUnarchive != null ||
        onTrash != null || onRestore != null || onDeleteForever != null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (note.isPinned) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = note.title.trim().ifEmpty { "Untitled" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    val snippet = noteSnippet(note.content)
                    if (snippet.isNotEmpty()) {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = snippet,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.size(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formatRelativeTime(note.updatedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val reminderAt = note.reminderAt
                        if (reminderAt != null) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Reminder",
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatRelativeTime(reminderAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (hasMenu) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Note actions"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (onTogglePin != null) {
                            DropdownMenuItem(
                                text = { Text(if (note.isPinned) "Unpin" else "Pin") },
                                leadingIcon = {
                                    Icon(Icons.Filled.PushPin, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onTogglePin()
                                }
                            )
                        }
                        if (onArchive != null) {
                            DropdownMenuItem(
                                text = { Text("Archive") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Archive, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onArchive()
                                }
                            )
                        }
                        if (onUnarchive != null) {
                            DropdownMenuItem(
                                text = { Text("Move out of archive") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Unarchive, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onUnarchive()
                                }
                            )
                        }
                        if (onRestore != null) {
                            DropdownMenuItem(
                                text = { Text("Restore") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Restore, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRestore()
                                }
                            )
                        }
                        if (onTrash != null) {
                            if (onTogglePin != null || onArchive != null || onUnarchive != null) {
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text("Move to trash") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onTrash()
                                }
                            )
                        }
                        if (onDeleteForever != null) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete forever") },
                                leadingIcon = {
                                    Icon(Icons.Filled.DeleteForever, contentDescription = null)
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteForever()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
