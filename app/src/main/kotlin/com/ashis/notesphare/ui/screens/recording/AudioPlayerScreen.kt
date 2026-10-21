package com.ashis.notesphare.ui.screens.recording

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ashis.notesphare.recording.AudioPlayerViewModel
import com.ashis.notesphare.ui.components.EmptyState
import com.ashis.notesphare.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    viewModel: AudioPlayerViewModel,
    onBack: () -> Unit,
    onOpenTranscription: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio player") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val attachment = state.attachment
                    if (attachment != null) {
                        IconButton(onClick = { onOpenTranscription(attachment.id) }) {
                            Icon(Icons.Filled.Subtitles, contentDescription = "Transcribe recording")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.isMissing || state.attachment == null -> EmptyState(
                icon = Icons.Filled.Warning,
                title = "Recording unavailable",
                message = state.error ?: "The recording file is missing.",
                modifier = Modifier.padding(padding)
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val attachment = state.attachment!!
                Icon(
                    Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${formatDuration(state.positionMs)} / ${formatDuration(state.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                WaveformProgress(
                    waveform = parseWaveform(attachment.waveform),
                    progress = if (state.durationMs > 0L) {
                        state.positionMs.toFloat() / state.durationMs.toFloat()
                    } else {
                        0f
                    }
                )
                Spacer(Modifier.height(12.dp))
                val sliderValue = if (dragging) dragPosition else state.positionMs.toFloat()
                Slider(
                    value = sliderValue.coerceIn(0f, state.durationMs.toFloat().coerceAtLeast(1f)),
                    onValueChange = {
                        dragging = true
                        dragPosition = it
                    },
                    onValueChangeFinished = {
                        dragging = false
                        viewModel.seekTo(dragPosition.toLong())
                    },
                    valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f)
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = viewModel::togglePlayPause,
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(if (state.isPlaying) "Pause" else "Play")
                }
                Spacer(Modifier.height(24.dp))
                Text("Playback speed", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        FilterChip(
                            selected = state.speed == speed,
                            onClick = { viewModel.setSpeed(speed) },
                            label = { Text("${speed}x") },
                            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        )
                    }
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    Text(
                        "Playback speed requires Android 6.0 or newer.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                state.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Transcripts are saved separately and never replace or delete the original recording.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WaveformProgress(waveform: List<Float>, progress: Float) {
    val playedColor = MaterialTheme.colorScheme.primary
    val remainingColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val points = waveform.ifEmpty { List(56) { 0.15f } }
        val step = size.width / points.size.coerceAtLeast(1)
        points.forEachIndexed { index, value ->
            val fraction = index.toFloat() / points.size.coerceAtLeast(1)
            val barHeight = (value.coerceIn(0f, 1f) * size.height).coerceAtLeast(4f)
            val x = step * index + step / 2f
            drawLine(
                color = if (fraction <= progress) playedColor else remainingColor,
                start = Offset(x, size.height / 2f - barHeight / 2f),
                end = Offset(x, size.height / 2f + barHeight / 2f),
                strokeWidth = (step * 0.55f).coerceAtLeast(2f),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun parseWaveform(raw: String?): List<Float> =
    raw?.split(',')
        ?.mapNotNull { it.trim().toFloatOrNull() }
        ?.filter { it.isFinite() }
        .orEmpty()
