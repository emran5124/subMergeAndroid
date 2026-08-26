package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.network.GeminiApiClient
import com.example.utils.SrtParser
import com.example.ui.theme.Radii
import com.example.ui.theme.Spacing

@Composable
fun AiTranscriptionMode(
    viewModel: SubtitleStudioViewModel,
    onPickFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val aiAudioUri by viewModel.aiAudioFileUri.collectAsState()
    val aiAudioName by viewModel.aiAudioFileName.collectAsState()
    val aiMime by viewModel.aiAudioMimeType.collectAsState()
    val aiCustomPrompt by viewModel.aiCustomPrompt.collectAsState()
    val aiSourceText by viewModel.aiSourceText.collectAsState()
    val aiTranscribeState by viewModel.aiTranscriptionState.collectAsState()
    val aiLines by viewModel.aiSrtLines.collectAsState()
    val aiActiveIndex by viewModel.aiActiveLineIndex.collectAsState()

    val aiPlayerIsPlaying by viewModel.aiPlayerIsPlaying.collectAsState()
    val aiPlayerPosMs by viewModel.aiPlayerCurrentPosMs.collectAsState()
    val aiPlayerDurationMs by viewModel.aiPlayerDuration.collectAsState()

    val isAudio = aiMime?.startsWith("audio/") == true

    var showClearConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Audio Transcription Studio",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (aiAudioUri != null) {
                IconButton(onClick = {
                    if (aiLines.isNotEmpty()) showClearConfirm = true else viewModel.clearAiSelectedAudio()
                }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear selected file", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        when {
            aiAudioUri == null -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = Radii.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    EmptyState(
                        icon = Icons.Filled.Audiotrack,
                        title = "No Media Selected",
                        description = "To start transcribing, please select an audio or video file from your system.",
                        ctaLabel = "Select Audio/Video File",
                        onCtaClick = onPickFile,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }

            aiLines.isEmpty() -> {
                val setupScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(setupScrollState),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = Radii.md,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isAudio) Icons.Filled.Mic else Icons.Filled.Movie,
                                contentDescription = "File indicator",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Column {
                                Text(text = aiAudioName ?: "File", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Type: $aiMime",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    if (!isAudio) {
                        StatusBanner(
                            text = "Warning: Option 1 is exclusively available for Audio files. Video transcription will be supported in coming options.",
                            type = BannerType.WARNING
                        )
                    } else {
                        val promptIsRtl = isRtlText(aiCustomPrompt)
                        CompositionLocalProvider(LocalLayoutDirection provides (if (promptIsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                            OutlinedTextField(
                                value = aiCustomPrompt,
                                onValueChange = { viewModel.setAiCustomPrompt(it) },
                                label = { Text("AI Instructions / System Prompt") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 220.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                                supportingText = { Text("Supports '[sourceTextPlaceholder]' placeholder which dynamically swaps with the lines below.") }
                            )
                        }

                        val sourceIsRtl = isRtlText(aiSourceText)
                        CompositionLocalProvider(LocalLayoutDirection provides (if (sourceIsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                            OutlinedTextField(
                                value = aiSourceText,
                                onValueChange = { viewModel.setAiSourceText(it) },
                                label = { Text("Source Text Lines (Optional)") },
                                placeholder = { Text("text1\ntext2\ntext3...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 200.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                                supportingText = { Text("If provided, Gemini forces each output SRT block line to align exactly with each source row.") }
                            )
                        }

                        TranscriptionStatusCard(
                            state = aiTranscribeState,
                            onResumeVpn = {
                                (aiTranscribeState as? GeminiApiClient.CallStepState.VpnBlockPrompt)?.onContinuePressed()
                            }
                        )

                        val isRunning = aiTranscribeState is GeminiApiClient.CallStepState.Sending ||
                            aiTranscribeState is GeminiApiClient.CallStepState.RetryingRateLimit

                        Button(
                            onClick = { viewModel.startAiTranscription() },
                            enabled = !isRunning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                            shape = Radii.md
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = LocalContentColor.current
                                )
                                Spacer(modifier = Modifier.width(Spacing.md))
                                Text("Uploading file & waiting response...")
                            } else {
                                Icon(Icons.Filled.VideoSettings, contentDescription = null)
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text("Generate Subtitles via Gemini")
                            }
                        }
                    }
                }
            }

            else -> {
                val timelinesWeightFraction by viewModel.timelinesWeightFraction.collectAsState()
                Column(
                    modifier = Modifier
                        .weight((2.0f - timelinesWeightFraction).coerceIn(0.2f, 1.8f))
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = Radii.md,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = aiAudioName ?: "Subtitles",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    if (aiLines.isNotEmpty()) showClearConfirm = true else viewModel.clearAiSelectedAudio()
                                },
                                modifier = Modifier.heightIn(min = 40.dp),
                                contentPadding = PaddingValues(horizontal = Spacing.md)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Change File", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    AiPlayerCard(
                        viewModel = viewModel,
                        mediaName = aiAudioName ?: "",
                        mimeType = aiMime,
                        lines = aiLines,
                        activeIndex = aiActiveIndex,
                        isPlaying = aiPlayerIsPlaying,
                        posMs = aiPlayerPosMs,
                        durationMs = aiPlayerDurationMs,
                        onTogglePlay = { viewModel.toggleAiPlayback() },
                        onPlaySegment = { viewModel.playAiCurrentLineSegment() },
                        onSeek = { viewModel.seekAiPlayerToMs(it) }
                    )

                    val activeLine = aiLines.getOrNull(aiActiveIndex)
                    if (activeLine != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = Radii.lg,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(Spacing.md),
                                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Editing Block #${activeLine.index}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Line ${aiActiveIndex + 1} of ${aiLines.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val lineText = activeLine.text
                                val lineIsRtl = isRtlText(lineText)
                                CompositionLocalProvider(LocalLayoutDirection provides (if (lineIsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                                    OutlinedTextField(
                                        value = lineText,
                                        onValueChange = { viewModel.updateAiLineText(aiActiveIndex, it) },
                                        label = { Text("Subtitle Text") },
                                        modifier = Modifier.fillMaxWidth().testTag("ai_srt_text_input"),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                ) {
                                    TimingNudgeColumn(
                                        label = "Start Time",
                                        timeMs = activeLine.startTimeMs,
                                        onDecrease = {
                                            viewModel.updateAiLineTiming(aiActiveIndex, (activeLine.startTimeMs - 100).coerceAtLeast(0), activeLine.endTimeMs)
                                        },
                                        onIncrease = {
                                            viewModel.updateAiLineTiming(aiActiveIndex, (activeLine.startTimeMs + 100).coerceAtMost(activeLine.endTimeMs), activeLine.endTimeMs)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )

                                    TimingNudgeColumn(
                                        label = "End Time",
                                        timeMs = activeLine.endTimeMs,
                                        onDecrease = {
                                            viewModel.updateAiLineTiming(aiActiveIndex, activeLine.startTimeMs, (activeLine.endTimeMs - 100).coerceAtLeast(activeLine.startTimeMs))
                                        },
                                        onIncrease = {
                                            viewModel.updateAiLineTiming(aiActiveIndex, activeLine.startTimeMs, activeLine.endTimeMs + 100)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Segment: resizable list of subtitle blocks
                Column(
                    modifier = Modifier
                        .weight(timelinesWeightFraction)
                        .fillMaxWidth()
                ) {
                    ResizableListHeader(
                        title = "Subtitle Blocks (${aiLines.size}) — tap row to edit, drag to resize",
                        timelinesWeightFraction = timelinesWeightFraction,
                        onWeightChange = { viewModel.setTimelinesWeightFraction(it) }
                    )

                    val listState = rememberLazyListState()
                    LaunchedEffect(aiActiveIndex) {
                        if (aiActiveIndex >= 0 && aiActiveIndex < aiLines.size) {
                            listState.animateScrollToItem(aiActiveIndex)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(top = Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        itemsIndexed(aiLines) { idx, item ->
                            val isCurrent = idx == aiActiveIndex
                            Card(
                                onClick = { viewModel.setAiActiveLineIndex(idx) },
                                modifier = Modifier.fillMaxWidth().testTag("ai_srt_item_$idx"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else
                                        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                                ),
                                border = if (isCurrent)
                                    androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                else
                                    null
                            ) {
                                Column(modifier = Modifier.padding(Spacing.md)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "#${item.index}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${SrtParser.formatTime(item.startTimeMs)} --> ${SrtParser.formatTime(item.endTimeMs)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Discard subtitles?",
            message = "Choosing another file will discard the ${aiLines.size} generated subtitle blocks. Continue?",
            confirmLabel = "Discard & Change File",
            destructive = true,
            onConfirm = { viewModel.clearAiSelectedAudio() },
            onDismiss = { showClearConfirm = false }
        )
    }
}

@Composable
private fun TranscriptionStatusCard(
    state: GeminiApiClient.CallStepState?,
    onResumeVpn: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = "Transcription Log:",
            style = MaterialTheme.typography.titleSmall
        )
        when (val s = state) {
            is GeminiApiClient.CallStepState.Idle ->
                StatusBanner("Ready to start transcription.", BannerType.INFO)
            is GeminiApiClient.CallStepState.Sending ->
                StatusBanner("Directing multimodal audio to Gemini with model [${s.model}]...", BannerType.INFO)
            is GeminiApiClient.CallStepState.RetryingRateLimit ->
                StatusBanner("Limited (429): Retrying in [${s.delaySecondsLeft}s]...", BannerType.WARNING)
            is GeminiApiClient.CallStepState.VpnBlockPrompt ->
                StatusBanner(
                    text = "Geo-blocked (400/403): Toggle/Check your VPN and click RESUME.",
                    type = BannerType.ERROR,
                    action = {
                        Button(onClick = onResumeVpn) { Text("Resume Flow") }
                    }
                )
            is GeminiApiClient.CallStepState.Success ->
                StatusBanner("Completed successfully.", BannerType.SUCCESS)
            is GeminiApiClient.CallStepState.OutOfOptions ->
                StatusBanner("Failed: ${s.error}", BannerType.ERROR)
            else ->
                StatusBanner("Waiting...", BannerType.INFO)
        }
    }
}

@Composable
internal fun AiPlayerCard(
    viewModel: SubtitleStudioViewModel,
    mediaName: String,
    mimeType: String?,
    lines: List<SrtParser.SrtLine>,
    activeIndex: Int,
    isPlaying: Boolean,
    posMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onPlaySegment: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Radii.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            val showVideoPlayer by viewModel.showVideoPlayer.collectAsState()
            val videoHeightDp by viewModel.videoHeightDp.collectAsState()
            if (showVideoPlayer && isVideoFile(mediaName, mimeType)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(videoHeightDp.dp)
                        .background(Color.Black)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableVideoBox(modifier = Modifier.fillMaxSize()) {
                        VideoSurfaceView(
                            mediaPlayer = viewModel.aiMediaPlayer,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    SubtitleOverlay(
                        currentText = (lines.find { posMs >= it.startTimeMs && posMs <= it.endTimeMs }
                            ?: lines.getOrNull(activeIndex))?.text
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Button(onClick = onPlaySegment, modifier = Modifier.heightIn(min = 40.dp)) {
                    Icon(Icons.Filled.PlayCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("Play Active Segment", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "${SrtParser.formatTime(posMs)} / ${SrtParser.formatTime(durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                value = if (durationMs > 0) posMs.toFloat() / durationMs.toFloat() else 0f,
                onValueChange = { onSeek((it * durationMs.toFloat()).toLong()) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
