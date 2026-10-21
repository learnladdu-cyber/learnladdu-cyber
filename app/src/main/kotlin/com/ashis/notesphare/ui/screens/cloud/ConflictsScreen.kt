package com.ashis.notesphare.ui.screens.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashis.notesphare.data.local.SyncConflictEntity
import com.ashis.notesphare.ui.screens.auth.AuthViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictsScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync conflicts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (conflicts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("No conflicts", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Every synced change has been accepted by the cloud.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Both versions are kept until you choose. Keeping this device re-queues " +
                        "the local change against the current server version. Keeping cloud " +
                        "replaces the local row with the saved server copy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(conflicts, key = { it.id }) { conflict ->
                ConflictCard(
                    conflict = conflict,
                    onKeepLocal = { viewModel.keepLocal(conflict) },
                    onKeepRemote = { viewModel.keepRemote(conflict) },
                    onDismiss = { viewModel.dismissConflict(conflict) }
                )
            }
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: SyncConflictEntity,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                conflict.entityType.replace('_', ' ').lowercase()
                    .replaceFirstChar { it.uppercase() },
                fontWeight = FontWeight.SemiBold
            )
            Text(
                conflict.entityUuid,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "This device: ${format(conflict.localUpdatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                "Cloud: ${format(conflict.remoteUpdatedAt)}",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onKeepLocal, modifier = Modifier.weight(1f)) {
                    Text("Keep device")
                }
                OutlinedButton(onClick = onKeepRemote, modifier = Modifier.weight(1f)) {
                    Text("Keep cloud")
                }
            }
            TextButton(onClick = onDismiss) { Text("Dismiss without changing") }
        }
    }
}

private fun format(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(epochMillis))
