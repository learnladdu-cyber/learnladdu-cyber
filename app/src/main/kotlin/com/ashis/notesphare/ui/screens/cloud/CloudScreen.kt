package com.ashis.notesphare.ui.screens.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashis.notesphare.auth.AuthStatus
import com.ashis.notesphare.ui.screens.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onReviewConflicts: () -> Unit
) {
    val auth by viewModel.authState.collectAsStateWithLifecycle()
    val cloud by viewModel.cloud.collectAsStateWithLifecycle()
    val pending by viewModel.pendingCount.collectAsStateWithLifecycle()
    val conflictCount by viewModel.conflictCount.collectAsStateWithLifecycle()
    var confirmLink by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmLink) {
        AlertDialog(
            onDismissRequest = { confirmLink = false },
            title = { Text("Link local data?") },
            text = {
                Text(
                    "This uploads local-only notes, folders, tags, checklists, attachments and " +
                        "transcriptions to the signed-in account. Other accounts cannot see them. " +
                        "This action cannot automatically be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLink = false
                    viewModel.linkLocalData()
                }) { Text("Link and sync") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLink = false }) { Text("Cancel") }
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete cloud account?") },
            text = {
                Text(
                    "This deletes the cloud account and server data. Local notes and files remain " +
                        "on this device until you remove them. Type-safe recovery is not available " +
                        "after the account is deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteAccount()
                }) { Text("Delete account") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud & account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (auth.status == AuthStatus.INITIALIZING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (!auth.isSignedIn) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CloudOff, contentDescription = null)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Not signed in",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                        Text(
                            "Sign in to back up notes and recordings across devices. Local-only " +
                                "data is never uploaded automatically.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Button(
                            onClick = onSignIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                        ) {
                            Text("Sign in or create account")
                        }
                    }
                }
                if (cloud.message != null) {
                    Text(cloud.message!!, style = MaterialTheme.typography.bodySmall)
                }
                return@Column
            }

            Card {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                auth.session?.email ?: "Signed in",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Encrypted session on this device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    StatusRow("Changes waiting", pending.toString())
                    StatusRow("Conflicts", conflictCount.toString())
                    if (cloud.lastReport != null) {
                        StatusRow(
                            "Last sync",
                            "↑${cloud.lastReport!!.pushed}  ↓${cloud.lastReport!!.pulled}"
                        )
                    }
                    if (cloud.isSyncing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        }
                    }
                    cloud.error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    cloud.message?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Button(
                onClick = viewModel::syncNow,
                enabled = !cloud.isSyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Sync, contentDescription = null)
                Text("Sync now", modifier = Modifier.padding(start = 8.dp))
            }

            if (conflictCount > 0) {
                OutlinedButton(
                    onClick = onReviewConflicts,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Review $conflictCount conflict(s)")
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Local data", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Local-only rows are not linked automatically when you sign in. Link them " +
                            "explicitly if you want this device's existing notes in the cloud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    OutlinedButton(
                        onClick = { confirmLink = true },
                        enabled = !cloud.isSyncing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text("Link local data to this account")
                    }
                }
            }

            OutlinedButton(
                onClick = viewModel::signOut,
                enabled = !cloud.isSyncing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign out")
            }
            TextButton(
                onClick = { confirmDelete = true },
                enabled = !cloud.isSyncing
            ) {
                Text("Delete cloud account", color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Signing out or deleting the account does not delete local notes or files. " +
                    "Account deletion uses your authenticated session; no service-role key exists in the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
