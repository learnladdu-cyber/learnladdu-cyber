package com.ashis.notesphare.ui.screens.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashis.notesphare.data.local.AttachmentEntity
import com.ashis.notesphare.domain.AttachmentKind
import com.ashis.notesphare.ui.components.ConfirmDialog
import com.ashis.notesphare.ui.components.EmptyState
import com.ashis.notesphare.ui.util.openAttachment
import com.ashis.notesphare.ui.util.shareAttachment
import com.ashis.notesphare.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentPreviewScreen(
    viewModel: AttachmentPreviewViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val attachment = state.attachment
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = attachment?.displayName ?: "Attachment",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (attachment != null) {
                            Text(
                                text = formatFileSize(attachment.sizeBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("attachmentBack")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (attachment != null && !state.isMissing) {
                        val file = state.file
                        if (file != null) {
                            IconButton(onClick = {
                                if (!openAttachment(context, attachment, file)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("No app can open this file")
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.OpenInNew, contentDescription = "Open with another app")
                            }
                            IconButton(onClick = {
                                if (!shareAttachment(context, attachment, file)) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("No app can share this file")
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share")
                            }
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove attachment")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.isMissing || attachment == null -> EmptyState(
                    icon = Icons.Filled.Warning,
                    title = "File unavailable",
                    message = state.error ?: "The file could not be found in NoteSphere storage.",
                    actionLabel = if (attachment != null) "Remove record" else null,
                    onAction = {
                        if (attachment != null) {
                            showDeleteConfirm = true
                        } else {
                            onBack()
                        }
                    }
                )
                attachment.kind == AttachmentKind.IMAGE.name -> {
                    val file = state.file
                    if (file != null) {
                        ImageAttachmentPreview(file = file, modifier = Modifier.fillMaxSize())
                    }
                }
                attachment.kind == AttachmentKind.PDF.name -> {
                    val file = state.file
                    if (file != null) {
                        PdfAttachmentPreview(file = file, modifier = Modifier.fillMaxSize())
                    }
                }
                else -> DocumentAttachmentPreview(
                    attachment = attachment,
                    onOpen = {
                        val file = state.file
                        if (file != null && !openAttachment(context, attachment, file)) {
                            scope.launch {
                                snackbarHostState.showSnackbar("No app can open this file")
                            }
                        }
                    }
                )
            }
        }
    }

    if (showDeleteConfirm && attachment != null) {
        ConfirmDialog(
            title = "Remove attachment?",
            message = "The file will be deleted from NoteSphere storage. The note itself is unchanged.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun ImageAttachmentPreview(file: File, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) { decodeScaledImage(file) }
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))) {
        val image = bitmap
        if (image == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Image attachment preview",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun PdfAttachmentPreview(file: File, modifier: Modifier = Modifier) {
    var pageCount by remember(file) { mutableIntStateOf(0) }
    var pageIndex by remember(file) { mutableIntStateOf(0) }
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var failure by remember(file) { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        failure = withContext(Dispatchers.IO) {
            runCatching {
                openPdf(file).use { renderer -> pageCount = renderer.pageCount }
                null
            }.getOrElse { it.message ?: "This PDF could not be opened." }
        }
    }

    LaunchedEffect(file, pageIndex, pageCount) {
        if (pageCount <= 0) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching { renderPdfPage(file, pageIndex) }.getOrElse {
                failure = it.message ?: "This page could not be rendered."
                null
            }
        }
    }

    Column(modifier = modifier) {
        when {
            failure != null -> EmptyState(
                icon = Icons.Filled.Warning,
                title = "PDF preview unavailable",
                message = failure ?: "The PDF could not be opened.",
                actionLabel = null,
                onAction = {}
            )
            pageCount == 0 -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(32.dp)
            )
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp)
                ) {
                    val image = bitmap
                    if (image == null) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        Surface(
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = image.asImageBitmap(),
                                contentDescription = "PDF page ${pageIndex + 1}",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) },
                        enabled = pageIndex > 0
                    ) { Text("Previous") }
                    Text(
                        text = "Page ${pageIndex + 1} of $pageCount",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium
                    )
                    OutlinedButton(
                        onClick = { pageIndex = (pageIndex + 1).coerceAtMost(pageCount - 1) },
                        enabled = pageIndex < pageCount - 1
                    ) { Text("Next") }
                }
            }
        }
    }
}

@Composable
private fun DocumentAttachmentPreview(
    attachment: AttachmentEntity,
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = attachment.displayName,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${attachment.mimeType} \u2022 ${formatFileSize(attachment.sizeBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onOpen) { Text("Open with another app") }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "In-app preview is available for images and PDFs.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun openPdf(file: File): PdfRenderer {
    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    return PdfRenderer(descriptor)
}

private fun renderPdfPage(file: File, pageIndex: Int): Bitmap {
    openPdf(file).use { renderer ->
        if (pageIndex !in 0 until renderer.pageCount) {
            error("Page not found")
        }
        renderer.openPage(pageIndex).use { page ->
            val maxWidth = 1_600
            val scale = (maxWidth.toFloat() / page.width.toFloat()).coerceAtMost(1.5f)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }
}

private fun decodeScaledImage(file: File): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(file)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.isMutableRequired = false
            val width = info.size.width
            val height = info.size.height
            val longest = maxOf(width, height)
            if (longest > 2_048) {
                val scale = 2_048f / longest.toFloat()
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1)
                )
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2_048) {
            sample *= 2
        }
        BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }
}.getOrNull()
