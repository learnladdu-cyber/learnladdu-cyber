package com.ashis.notesphare.ui.screens.editor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashis.notesphare.ui.components.AttachmentSection
import com.ashis.notesphare.ui.components.ConfirmDialog
import com.ashis.notesphare.ui.components.EmptyState
import com.ashis.notesphare.ui.components.FolderPickerDialog
import com.ashis.notesphare.ui.components.RichTextView
import com.ashis.notesphare.ui.components.TagPickerDialog
import com.ashis.notesphare.ui.util.BOLD_MARKER
import com.ashis.notesphare.ui.util.CODE_MARKER
import com.ashis.notesphare.ui.util.ITALIC_MARKER
import com.ashis.notesphare.ui.util.STRIKE_MARKER
import com.ashis.notesphare.ui.util.UNDERLINE_MARKER
import com.ashis.notesphare.ui.util.applyInlineMarker
import com.ashis.notesphare.ui.util.canScheduleExactAlarms
import com.ashis.notesphare.ui.util.insertBullet
import com.ashis.notesphare.ui.util.showDateTimePicker
import com.ashis.notesphare.util.formatDateTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onRecordAudio: (Long) -> Unit,
    onOpenAttachment: (Long) -> Unit,
    onOpenAudio: (Long) -> Unit,
    onOpenTranscription: (Long) -> Unit,
    onOpenStorage: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var previewMode by rememberSaveable { mutableStateOf(false) }
    var checklistInput by rememberSaveable { mutableStateOf("") }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showTrashConfirm by remember { mutableStateOf(false) }
    var showDeleteForeverConfirm by remember { mutableStateOf(false) }
    var pendingAttachmentRemoval by remember { mutableStateOf<Long?>(null) }
    var pendingAttachmentName by remember { mutableStateOf("") }

    val handleBack: () -> Unit = {
        viewModel.markExplicitExit()
        onBack()
    }
    BackHandler(onBack = handleBack)

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.addAttachments(listOf(uri))
    }
    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addAttachments(uris)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Notifications are off. Enable them in Settings to see reminders."
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                EditorEvent.NavigateBack -> onBack()
                is EditorEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.onExit() }
    }

    val statusText = when {
        state.isLoading -> "Loading"
        state.isTrashed -> "In trash"
        state.isSaving -> "Saving\u2026"
        state.isDirty -> "Unsaved changes"
        state.lastSavedAt != null -> "Saved"
        else -> "Autosave on"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Note", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = handleBack,
                        modifier = Modifier.testTag("editorBack")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!state.isLoading && state.loadError == null) {
                        if (state.isTrashed) {
                            IconButton(onClick = viewModel::restore) {
                                Icon(Icons.Filled.Restore, contentDescription = "Restore note")
                            }
                            IconButton(onClick = { showDeleteForeverConfirm = true }) {
                                Icon(
                                    Icons.Filled.DeleteForever,
                                    contentDescription = "Delete forever"
                                )
                            }
                        } else {
                            IconButton(onClick = viewModel::togglePin) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = if (state.isPinned) "Unpin" else "Pin",
                                    tint = if (state.isPinned) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        LocalContentColor.current
                                    }
                                )
                            }
                            IconButton(onClick = viewModel::toggleArchive) {
                                Icon(
                                    imageVector = if (state.isArchived) {
                                        Icons.Filled.Unarchive
                                    } else {
                                        Icons.Filled.Archive
                                    },
                                    contentDescription = if (state.isArchived) {
                                        "Move out of archive"
                                    } else {
                                        "Archive"
                                    }
                                )
                            }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Move to folder") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Folder, contentDescription = null)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            showFolderPicker = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tags") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Label, contentDescription = null)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            showTagPicker = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (state.reminderAt == null) "Set reminder" else "Change reminder") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Notifications, contentDescription = null)
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            showDateTimePicker(context, state.reminderAt) { millis ->
                                                val now = System.currentTimeMillis()
                                                if (millis <= now) {
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            "Choose a time in the future"
                                                        )
                                                    }
                                                } else {
                                                    requestNotificationPermissionIfNeeded(
                                                        context = context,
                                                        launcher = notificationPermissionLauncher
                                                    )
                                                    viewModel.setReminder(millis)
                                                    if (!canScheduleExactAlarms(context)) {
                                                        scope.launch {
                                                            snackbarHostState.showSnackbar(
                                                                "Exact alarms are off, so the reminder may arrive a little late. You can change this in Settings."
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Move to trash") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.DeleteForever,
                                                contentDescription = null
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            showTrashConfirm = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            state.loadError != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    EmptyState(
                        icon = Icons.Filled.Warning,
                        title = "Note unavailable",
                        message = state.loadError ?: "This note could not be loaded.",
                        actionLabel = "Go back",
                        onAction = handleBack
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (state.isTrashed) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "This note is in the trash.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Row {
                                    TextButton(onClick = viewModel::restore) {
                                        Text("Restore")
                                    }
                                    TextButton(onClick = { showDeleteForeverConfirm = true }) {
                                        Text(
                                            text = "Delete forever",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("noteTitle"),
                        placeholder = { Text("Title") },
                        textStyle = MaterialTheme.typography.headlineSmall,
                        singleLine = true,
                        readOnly = state.isTrashed
                    )

                    if (!state.isTrashed) {
                        Spacer(Modifier.height(8.dp))
                        FormattingToolbar(
                            previewMode = previewMode,
                            onMarker = { marker ->
                                viewModel.onContentChange(
                                    applyInlineMarker(state.content, marker)
                                )
                            },
                            onBullet = {
                                viewModel.onContentChange(insertBullet(state.content))
                            },
                            onTogglePreview = { previewMode = !previewMode }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (previewMode && !state.isTrashed) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp)
                        ) {
                            Box(modifier = Modifier.padding(16.dp)) {
                                if (state.content.text.isBlank()) {
                                    Text(
                                        text = "Nothing to preview.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    RichTextView(text = state.content.text)
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = state.content,
                            onValueChange = viewModel::onContentChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 200.dp)
                                .testTag("noteContent"),
                            placeholder = { Text("Start writing\u2026") },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            readOnly = state.isTrashed
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Formatting: **bold**, *italic*, __underline__, ~~strikethrough~~, `code`, and - bullet lines. Use Preview to see the result.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(20.dp))
                    ChecklistSection(
                        state = state,
                        input = checklistInput,
                        onInputChange = { checklistInput = it },
                        onAdd = {
                            if (checklistInput.isNotBlank()) {
                                viewModel.addChecklistItem(checklistInput)
                                checklistInput = ""
                            }
                        },
                        onToggle = viewModel::toggleChecklistItem,
                        onTextChange = viewModel::onChecklistTextChange,
                        onDelete = viewModel::deleteChecklistItem
                    )

                    Spacer(Modifier.height(20.dp))
                    AttachmentSection(
                        attachments = state.attachments,
                        transcriptions = state.transcriptions,
                        missingIds = state.missingAttachmentIds,
                        readOnly = state.isTrashed,
                        onPickImage = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onPickDocument = {
                            documentPicker.launch(
                                arrayOf(
                                    "image/*",
                                    "application/pdf",
                                    "text/*",
                                    "application/msword",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/vnd.ms-excel",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "application/vnd.ms-powerpoint",
                                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                    "application/rtf"
                                )
                            )
                        },
                        onRecordAudio = { onRecordAudio(state.noteId) },
                        onOpenAttachment = onOpenAttachment,
                        onOpenAudio = onOpenAudio,
                        onTranscribe = onOpenTranscription,
                        onRemove = { attachmentId ->
                            pendingAttachmentName = state.attachments
                                .firstOrNull { it.id == attachmentId }
                                ?.displayName
                                .orEmpty()
                            pendingAttachmentRemoval = attachmentId
                        },
                        onOpenStorage = onOpenStorage
                    )

                    Spacer(Modifier.height(20.dp))
                    OrganizationSection(
                        state = state,
                        onFolderClick = { showFolderPicker = true },
                        onTagsClick = { showTagPicker = true },
                        onReminderClick = {
                            showDateTimePicker(context, state.reminderAt) { millis ->
                                val now = System.currentTimeMillis()
                                if (millis <= now) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Choose a time in the future")
                                    }
                                } else {
                                    requestNotificationPermissionIfNeeded(
                                        context = context,
                                        launcher = notificationPermissionLauncher
                                    )
                                    viewModel.setReminder(millis)
                                    if (!canScheduleExactAlarms(context)) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                "Exact alarms are off, so the reminder may arrive a little late."
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        onClearReminder = viewModel::clearReminder
                    )

                    Spacer(Modifier.height(16.dp))
                    if (state.createdAt > 0L) {
                        Text(
                            text = "Created ${formatDateTime(state.createdAt)}" +
                                if (state.updatedAt > 0L) {
                                    " \u2022 Updated ${formatDateTime(state.updatedAt)}"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showTrashConfirm) {
        ConfirmDialog(
            title = "Move to trash?",
            message = "You can restore this note from the trash later.",
            confirmLabel = "Move to trash",
            destructive = true,
            onConfirm = {
                showTrashConfirm = false
                viewModel.trash()
            },
            onDismiss = { showTrashConfirm = false }
        )
    }

    if (showDeleteForeverConfirm) {
        ConfirmDialog(
            title = "Delete permanently?",
            message = "This note, its checklist items, tags, attachments and transcription records will be deleted forever.",
            confirmLabel = "Delete forever",
            destructive = true,
            onConfirm = {
                showDeleteForeverConfirm = false
                viewModel.deleteForever()
            },
            onDismiss = { showDeleteForeverConfirm = false }
        )
    }

    val removalId = pendingAttachmentRemoval
    if (removalId != null) {
        ConfirmDialog(
            title = "Remove attachment?",
            message = "Remove \"$pendingAttachmentName\" from this note? The stored file will be deleted.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                pendingAttachmentRemoval = null
                viewModel.removeAttachment(removalId)
            },
            onDismiss = { pendingAttachmentRemoval = null }
        )
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            folders = state.folders,
            selectedFolderId = state.folderId,
            onSelect = { folderId ->
                viewModel.selectFolder(folderId)
                showFolderPicker = false
            },
            onDismiss = { showFolderPicker = false }
        )
    }

    if (showTagPicker) {
        TagPickerDialog(
            tags = state.allTags,
            selectedTagIds = state.selectedTagIds,
            onToggleTag = viewModel::toggleTag,
            onCreateTag = viewModel::createTag,
            onDismiss = { showTagPicker = false }
        )
    }
}

@Composable
private fun FormattingToolbar(
    previewMode: Boolean,
    onMarker: (String) -> Unit,
    onBullet: () -> Unit,
    onTogglePreview: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FormatButton(Icons.Filled.FormatBold, "Bold") { onMarker(BOLD_MARKER) }
        FormatButton(Icons.Filled.FormatItalic, "Italic") { onMarker(ITALIC_MARKER) }
        FormatButton(Icons.Filled.FormatUnderlined, "Underline") { onMarker(UNDERLINE_MARKER) }
        FormatButton(Icons.Filled.FormatStrikethrough, "Strikethrough") { onMarker(STRIKE_MARKER) }
        FormatButton(Icons.Filled.Code, "Code") { onMarker(CODE_MARKER) }
        FormatButton(Icons.Filled.FormatListBulleted, "Bullet", onBullet)
        IconButton(onClick = onTogglePreview) {
            Icon(
                imageVector = if (previewMode) Icons.Filled.Edit else Icons.Filled.Visibility,
                contentDescription = if (previewMode) "Edit text" else "Preview formatting"
            )
        }
    }
}

@Composable
private fun FormatButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@Composable
private fun ChecklistSection(
    state: EditorUiState,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (Long) -> Unit,
    onTextChange: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Checklist,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text("Checklist", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        if (state.checklist.isEmpty()) {
            Text(
                text = "No checklist items yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.checklist.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = { onToggle(item.id) },
                    enabled = !state.isTrashed,
                    modifier = Modifier.testTag("checklistCheck_${item.id}")
                )
                BasicTextField(
                    value = item.text,
                    onValueChange = { onTextChange(item.id, it) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("checklistText_${item.id}"),
                    enabled = !state.isTrashed,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (item.isChecked) {
                            TextDecoration.LineThrough
                        } else {
                            null
                        },
                        color = if (item.isChecked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (item.text.isEmpty()) {
                                Text(
                                    text = "Item",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (!state.isTrashed) {
                    IconButton(onClick = { onDelete(item.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Delete checklist item",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        if (!state.isTrashed) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("checklistInput"),
                    placeholder = { Text("Add checklist item") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd() })
                )
                IconButton(
                    onClick = onAdd,
                    enabled = input.isNotBlank(),
                    modifier = Modifier.testTag("addChecklistItem")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add checklist item")
                }
            }
        }
    }
}

@Composable
private fun OrganizationSection(
    state: EditorUiState,
    onFolderClick: () -> Unit,
    onTagsClick: () -> Unit,
    onReminderClick: () -> Unit,
    onClearReminder: () -> Unit
) {
    val folderName = state.folders.firstOrNull { it.id == state.folderId }?.name
    val selectedTagNames = state.allTags
        .filter { it.id in state.selectedTagIds }
        .joinToString(", ") { it.name }
    val reminderAt = state.reminderAt

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column {
            OrganizationRow(
                icon = Icons.Filled.Folder,
                title = "Folder",
                value = folderName ?: "No folder",
                enabled = !state.isTrashed,
                onClick = onFolderClick
            )
            HorizontalDivider()
            OrganizationRow(
                icon = Icons.Filled.Label,
                title = "Tags",
                value = selectedTagNames.ifEmpty { "No tags" },
                enabled = !state.isTrashed,
                onClick = onTagsClick
            )
            HorizontalDivider()
            OrganizationRow(
                icon = Icons.Filled.Notifications,
                title = "Reminder",
                value = reminderAt?.let { formatDateTime(it) } ?: "None",
                enabled = !state.isTrashed,
                onClick = onReminderClick
            )
            if (reminderAt != null && !state.isTrashed) {
                TextButton(
                    onClick = onClearReminder,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                ) {
                    Text("Remove reminder")
                }
            }
        }
    }
}

@Composable
private fun OrganizationRow(
    icon: ImageVector,
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(180.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1
        )
    }
}

private fun requestNotificationPermissionIfNeeded(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
