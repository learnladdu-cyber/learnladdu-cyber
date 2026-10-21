package com.ashis.notesphare.ui.screens.recording

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.domain.AttachmentKind
import com.ashis.notesphare.ui.components.ConfirmDialog
import com.ashis.notesphare.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageManagerScreen(
    viewModel: StorageManagerViewModel,
    onBack: () -> Unit,
    onOpenRecovery: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<AttachmentEntity?>(null) }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.clearMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage manager") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Storage, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = formatFileSize(state.stats.attachmentBytes),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${state.stats.attachmentCount} attachments \u2022 " +
                                "${formatFileSize(state.stats.managedFileBytes)} on disk",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (state.stats.missingCount > 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${state.stats.missingCount} records point at missing files",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Clean up", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (state.stats.orphanCount == 0) {
                                "No orphaned files older than 24 hours."
                            } else {
                                "${state.stats.orphanCount} orphaned files " +
                                    "(${formatFileSize(state.stats.orphanBytes)}) can be removed. " +
                                    "Referenced files are never deleted."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = viewModel::cleanupOrphans,
                            enabled = !state.isCleaning
                        ) {
                            if (state.isCleaning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Filled.CleaningServices, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text("Clean orphaned files")
                        }
                        if (state.sessions.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onOpenRecovery) {
                                Icon(Icons.Filled.Warning, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("${state.sessions.size} unfinished recording(s)")
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text("Attachments", style = MaterialTheme.typography.titleMedium)
            }
            if (state.attachments.isEmpty()) {
                item {
                    Text(
                        text = "No attachments or recordings yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(state.attachments, key = { it.id }) { attachment ->
                AttachmentStorageRow(
                    attachment = attachment,
                    isDeleting = state.deletingId == attachment.id,
                    onDelete = { pendingDelete = attachment }
                )
                HorizontalDivider()
            }
        }
    }

    val attachment = pendingDelete
    if (attachment != null) {
        ConfirmDialog(
            title = "Remove attachment?",
            message = "Remove \"${attachment.displayName}\" from NoteSphere storage?",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                viewModel.deleteAttachment(attachment.id)
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun AttachmentStorageRow(
    attachment: AttachmentEntity,
    isDeleting: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = attachmentIcon(attachment),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${formatFileSize(attachment.sizeBytes)} \u2022 note ${attachment.noteId}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isDeleting) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove attachment")
            }
        }
    }
}

private fun attachmentIcon(attachment: AttachmentEntity): ImageVector = when (
    AttachmentKind.fromStorage(attachment.kind)
) {
    AttachmentKind.IMAGE -> Icons.Filled.Image
    AttachmentKind.PDF -> Icons.Filled.PictureAsPdf
    AttachmentKind.AUDIO -> Icons.Filled.AudioFile
    AttachmentKind.DOCUMENT -> Icons.Filled.Description
}
