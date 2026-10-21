package com.ashis.notesphare.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.data.local.TranscriptionEntity
import com.ashis.notesphare.domain.AttachmentKind
import com.ashis.notesphare.domain.TranscriptionStatus
import com.ashis.notesphare.util.formatDuration
import com.ashis.notesphare.util.formatFileSize

@Composable
fun AttachmentSection(
    attachments: List<AttachmentEntity>,
    transcriptions: Map<Long, TranscriptionEntity>,
    missingIds: Set<Long>,
    readOnly: Boolean,
    onPickImage: () -> Unit,
    onPickDocument: () -> Unit,
    onRecordAudio: () -> Unit,
    onOpenAttachment: (Long) -> Unit,
    onOpenAudio: (Long) -> Unit,
    onTranscribe: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onOpenStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Attachments & recordings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenStorage) {
                Icon(
                    Icons.Filled.Storage,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Storage")
            }
        }

        if (!readOnly) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPickImage) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Photo")
                }
                TextButton(onClick = onPickDocument) {
                    Icon(Icons.Filled.AttachFile, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Document")
                }
                TextButton(onClick = onRecordAudio) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Record")
                }
            }
        }

        if (attachments.isEmpty()) {
            Text(
                text = if (readOnly) {
                    "No attachments on this note."
                } else {
                    "Attach images, PDFs or documents, or add a voice recording from the note menu."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            attachments.forEach { attachment ->
                AttachmentRow(
                    attachment = attachment,
                    transcription = transcriptions[attachment.id],
                    missing = attachment.id in missingIds,
                    readOnly = readOnly,
                    onOpen = {
                        if (attachment.kind == AttachmentKind.AUDIO.name) {
                            onOpenAudio(attachment.id)
                        } else {
                            onOpenAttachment(attachment.id)
                        }
                    },
                    onTranscribe = { onTranscribe(attachment.id) },
                    onRemove = { onRemove(attachment.id) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: AttachmentEntity,
    transcription: TranscriptionEntity?,
    missing: Boolean,
    readOnly: Boolean,
    onOpen: () -> Unit,
    onTranscribe: () -> Unit,
    onRemove: () -> Unit
) {
    val kind = AttachmentKind.fromStorage(attachment.kind)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (missing) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            }
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = attachmentIcon(kind),
                contentDescription = null,
                tint = if (missing) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val details = buildString {
                    if (kind == AttachmentKind.AUDIO) {
                        append(formatDuration(attachment.durationMs ?: 0L))
                    } else {
                        append(formatFileSize(attachment.sizeBytes))
                    }
                    if (missing) append(" \u2022 file missing")
                }
                Text(
                    text = details,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (missing) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                transcription?.let { transcript ->
                    val status = TranscriptionStatus.fromStorage(transcript.status)
                    val label = when (status) {
                        TranscriptionStatus.COMPLETED -> "Transcription saved"
                        TranscriptionStatus.RUNNING -> "Transcribing\u2026"
                        TranscriptionStatus.PENDING -> "Transcription queued"
                        TranscriptionStatus.FAILED -> "Transcription failed"
                        TranscriptionStatus.CANCELLED -> "Transcription cancelled"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (status) {
                            TranscriptionStatus.FAILED -> MaterialTheme.colorScheme.error
                            TranscriptionStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            if (missing) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = "File missing",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (kind == AttachmentKind.AUDIO) {
                IconButton(onClick = onTranscribe) {
                    Icon(Icons.Filled.Subtitles, contentDescription = "Transcribe recording")
                }
            }
            if (!readOnly) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove attachment")
                }
            }
        }
    }
}

private fun attachmentIcon(kind: AttachmentKind): ImageVector = when (kind) {
    AttachmentKind.IMAGE -> Icons.Filled.Image
    AttachmentKind.PDF -> Icons.Filled.PictureAsPdf
    AttachmentKind.AUDIO -> Icons.Filled.AudioFile
    AttachmentKind.DOCUMENT -> Icons.Filled.Description
}
