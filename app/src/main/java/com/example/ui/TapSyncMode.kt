package com.example.ui

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.TapSession
import com.example.utils.SrtParser
import com.example.ui.theme.Radii
import com.example.ui.theme.Spacing

@Composable
fun TapSyncMode(
    viewModel: SubtitleStudioViewModel,
    onPickFile: () -> Unit,
    onPickTxt: () -> Unit,
    onImportSrt: () -> Unit,
    onSaveSrt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val tapAudioUri by viewModel.tapAudioFileUri.collectAsState()
    val tapAudioName by viewModel.tapAudioFileName.collectAsState()
    val tapMime by viewModel.tapAudioMimeType.collectAsState()
    val tapTxtUri by viewModel.tapSourceTxtFileUri.collectAsState()
    val tapTxtName by viewModel.tapSourceTxtFileName.collectAsState()
    val tapTxtLines by viewModel.tapSourceTxtLines.collectAsState()
    val tapSrtLines by viewModel.tapSrtLines.collectAsState()
    val tapActiveIndex by viewModel.tapActiveLineIndex.collectAsState()
    val tapIsRecording by viewModel.tapIsRecording.collectAsState()

    val tapPlayerIsPlaying by viewModel.tapPlayerIsPlaying.collectAsState()
    val tapPlayerPosMs by viewModel.tapPlayerCurrentPosMs.collectAsState()
    val tapPlayerDurationMs by viewModel.tapPlayerDuration.collectAsState()
    val tapPlaybackSpeed by viewModel.tapPlaybackSpeed.collectAsState()

    var sessionToDelete by remember { mutableStateOf<TapSession?>(null) }

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
                text = "Tap-to-Sync subtitler",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            if (tapAudioUri != null) {
                IconButton(onClick = { viewModel.clearTapSelectedAudio() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear selected file", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (tapAudioUri == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = Radii.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    EmptyState(
                        icon = Icons.Filled.Timer,
                        title = "Manual Sync Studio",
                        description = "Generate and sync subtitles manually. Simply load an Audio or Video file and optionally a text source to sync to, then 'tap' to record the timestamps live!",
                        ctaLabel = "Select Audio/Video File",
                        onCtaClick = onPickFile
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = Radii.md,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "پشتیبان‌گیری خودکار فعال است",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "سشن‌های شما در پوشه Download/.logs-sub ذخیره می‌شوند تا در صورت حذف برنامه بازیابی شوند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Button(onClick = {
                            viewModel.restoreSessionsFromBackup { _, message ->
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Icon(Icons.Filled.Replay, contentDescription = null)
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("بازیابی سشن‌ها", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                val tapSessions by viewModel.tapSessionsList.collectAsState()
                if (tapSessions.isNotEmpty()) {
                    Text(
                        text = "جلسه‌های اخیر (Recent Sessions)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        tapSessions.forEach { session ->
                            Card(
                                onClick = { viewModel.loadTapSession(session) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = Radii.md,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                        ) {
                                            Icon(
                                                imageVector = if (session.mediaMimeType.startsWith("video")) Icons.Filled.Movie else Icons.Filled.AudioFile,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = session.mediaName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        if (session.txtName.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(Spacing.xs))
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Description,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = session.txtName,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(Spacing.xs))

                                        val srtLinesCount = try {
                                            org.json.JSONArray(session.srtLinesJson).length()
                                        } catch (e: Exception) {
                                            0
                                        }
                                        Text(
                                            text = "تعداد لاین‌ها: $srtLinesCount",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { sessionToDelete = session }) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete Session",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Interactive Studio Layout
            val timelinesWeightFraction by viewModel.timelinesWeightFraction.collectAsState()
            Column(
                modifier = Modifier
                    .weight((2.0f - timelinesWeightFraction).coerceIn(0.2f, 1.8f))
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // 1. Unified Compact Studio Control Deck Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = Radii.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        // Row 1: Audio File Name and Live Recording Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Filled.AudioFile,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = tapAudioName ?: "Audio File",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            RecordingStatusPill(isRecording = tapIsRecording)
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        val showVideoPlayer by viewModel.showVideoPlayer.collectAsState()
                        val videoHeightDp by viewModel.videoHeightDp.collectAsState()
                        if (showVideoPlayer && isVideoFile(tapAudioName ?: "", tapMime)) {
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
                                        mediaPlayer = viewModel.tapMediaPlayer,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                SubtitleOverlay(
                                    currentText = tapSrtLines.find { tapPlayerPosMs >= it.startTimeMs && tapPlayerPosMs <= it.endTimeMs }?.text
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.xs))
                        }

                        // Playback Timeline & Controls
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = SrtParser.formatTime(tapPlayerPosMs),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.seekTapBackward(5000) }) {
                                        Icon(Icons.Filled.Replay, contentDescription = "-5 seconds", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { viewModel.toggleTapPlayback() }) {
                                        Icon(
                                            imageVector = if (tapPlayerIsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = "Play/Pause",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    IconButton(onClick = { viewModel.seekTapForward(5000) }) {
                                        Icon(Icons.Filled.FastForward, contentDescription = "+5 seconds", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                Text(
                                    text = SrtParser.formatTime(tapPlayerDurationMs),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Slider(
                                value = if (tapPlayerDurationMs > 0) tapPlayerPosMs.toFloat() / tapPlayerDurationMs.toFloat() else 0f,
                                onValueChange = {
                                    val target = (it * tapPlayerDurationMs.toFloat()).toLong()
                                    viewModel.seekTapPlayerToMs(target)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Playback Speed Controls Row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var showSpeedMenu by remember { mutableStateOf(false) }

                                Box {
                                    Surface(
                                        onClick = { showSpeedMenu = true },
                                        shape = Radii.md,
                                        role = Role.Button,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.heightIn(min = 32.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Speed,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "${if (tapPlaybackSpeed % 1.0f == 0f) tapPlaybackSpeed.toInt().toString() else tapPlaybackSpeed.toString()}x",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showSpeedMenu,
                                        onDismissRequest = { showSpeedMenu = false }
                                    ) {
                                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { speed ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = if (speed == 1.0f) "1.0x (Normal)" else "${speed}x",
                                                        fontWeight = if (tapPlaybackSpeed == speed) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (tapPlaybackSpeed == speed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                },
                                                onClick = {
                                                    viewModel.setTapPlaybackSpeed(speed)
                                                    showSpeedMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Quick Speed Chips
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                        val isSelected = (tapPlaybackSpeed == speed)
                                        Surface(
                                            onClick = { viewModel.setTapPlaybackSpeed(speed) },
                                            shape = Radii.md,
                                            role = Role.Button,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.heightIn(min = 32.dp)
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = if (speed == 1.0f) "1x" else if (speed == 2.0f) "2x" else "${speed}x",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // TXT source status row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Filled.TextFormat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (tapTxtUri != null) "TXT: $tapTxtName" else "No text script loaded",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (tapTxtUri != null) {
                                OutlinedButton(
                                    onClick = { viewModel.clearTapSourceTxtFile() },
                                    modifier = Modifier.heightIn(min = 36.dp),
                                    contentPadding = PaddingValues(horizontal = Spacing.sm)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text("Clear script", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onPickTxt,
                                    modifier = Modifier.heightIn(min = 36.dp),
                                    contentPadding = PaddingValues(horizontal = Spacing.sm)
                                ) {
                                    Text("Load text script", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // SRT source import row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Filled.Subtitles,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Sync / Import SRT File",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = onImportSrt,
                                modifier = Modifier.heightIn(min = 36.dp),
                                contentPadding = PaddingValues(horizontal = Spacing.sm)
                            ) {
                                Text("Import SRT", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // 2. Simple Unified Tapping Control Bar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = Radii.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Button(
                            onClick = { viewModel.tapTimingButton() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("tap_timing_live_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tapIsRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            shape = Radii.md
                        ) {
                            Icon(Icons.Filled.Timer, contentDescription = null)
                            Spacer(modifier = Modifier.width(Spacing.sm))
                            val label = if (!tapIsRecording) {
                                "Tap to Start Sync Session"
                            } else {
                                val nextNum = tapSrtLines.size + 1
                                val nextTxt = tapTxtLines.getOrNull(nextNum - 1)
                                if (nextTxt != null) "Tap (Line $nextNum: \"${nextTxt.take(15)}...\")" else "Tap for Next Line ($nextNum)"
                            }
                            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.undoLastTap() },
                                enabled = tapSrtLines.isNotEmpty(),
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                contentPadding = PaddingValues(horizontal = Spacing.xs)
                            ) {
                                Icon(Icons.Filled.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Undo last tap", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    viewModel.finishRecordingTiming()
                                    onSaveSrt(buildSrtFileName(tapAudioName))
                                },
                                enabled = tapSrtLines.isNotEmpty(),
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (tapIsRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                ),
                                contentPadding = PaddingValues(horizontal = Spacing.xs)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text(if (tapIsRecording) "Finish & Save" else "Save SRT File", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                LayoutSizeControls(viewModel = viewModel)
            }

            // Bottom Segment: Header and Lines list
            Column(
                modifier = Modifier
                    .weight(timelinesWeightFraction)
                    .fillMaxWidth()
            ) {
                ResizableListHeader(
                    title = "Timelines (${tapSrtLines.size} entries)",
                    timelinesWeightFraction = timelinesWeightFraction,
                    onWeightChange = { viewModel.setTimelinesWeightFraction(it) },
                    actions = {
                        OutlinedButton(
                            onClick = { viewModel.addNewTapLinePlaceholder() },
                            modifier = Modifier.heightIn(min = 36.dp),
                            contentPadding = PaddingValues(horizontal = Spacing.sm)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Add Line", style = MaterialTheme.typography.labelMedium)
                        }

                        if (tapSrtLines.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Button(
                                onClick = { onSaveSrt(buildSrtFileName(tapAudioName)) },
                                modifier = Modifier.heightIn(min = 36.dp),
                                contentPadding = PaddingValues(horizontal = Spacing.sm)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(Spacing.xs))
                                Text("Export SRT", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                )

                val tapListState = rememberLazyListState()
                LaunchedEffect(tapActiveIndex) {
                    if (tapIsRecording && tapActiveIndex >= 0 && tapActiveIndex < tapSrtLines.size) {
                        tapListState.animateScrollToItem(tapActiveIndex)
                    }
                }

                LazyColumn(
                    state = tapListState,
                    modifier = Modifier.fillMaxSize().padding(top = Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(tapSrtLines) { idx, item ->
                        val isCurrent = idx == tapActiveIndex
                        Card(
                            onClick = { viewModel.setTapActiveLineIndex(idx) },
                            modifier = Modifier.fillMaxWidth().testTag("tap_srt_item_$idx"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 4.dp else 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Block #${item.index}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${SrtParser.formatTime(item.startTimeMs)} ➔ ${SrtParser.formatTime(item.endTimeMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isCurrent) {
                                    // Inline editing controls when selected/tapped!
                                    Spacer(modifier = Modifier.height(Spacing.sm))

                                    val itemText = item.text
                                    val itemIsRtl = isRtlText(itemText)
                                    CompositionLocalProvider(LocalLayoutDirection provides (if (itemIsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                                        OutlinedTextField(
                                            value = itemText,
                                            onValueChange = { viewModel.updateTapLineText(idx, it) },
                                            label = { Text("Edit Line Content text") },
                                            modifier = Modifier.fillMaxWidth().testTag("tap_srt_text_input_$idx"),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content),
                                            singleLine = true
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(Spacing.sm))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                                    ) {
                                        TimingNudgeColumn(
                                            label = "Start Time",
                                            timeMs = item.startTimeMs,
                                            onDecrease = { viewModel.updateTapLineTiming(idx, (item.startTimeMs - 100).coerceAtLeast(0), item.endTimeMs) },
                                            onIncrease = { viewModel.updateTapLineTiming(idx, (item.startTimeMs + 100).coerceAtMost(item.endTimeMs), item.endTimeMs) },
                                            modifier = Modifier.weight(1f)
                                        )

                                        TimingNudgeColumn(
                                            label = "End Time",
                                            timeMs = item.endTimeMs,
                                            onDecrease = { viewModel.updateTapLineTiming(idx, item.startTimeMs, (item.endTimeMs - 100).coerceAtLeast(item.startTimeMs)) },
                                            onIncrease = { viewModel.updateTapLineTiming(idx, item.startTimeMs, item.endTimeMs + 100) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    // Standard display text for non-active entries
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    sessionToDelete?.let { session ->
        ConfirmDialog(
            title = "حذف سشن",
            message = "آیا از حذف سشن \"${session.mediaName}\" مطمئن هستید؟ این عمل قابل بازگشت نیست.",
            confirmLabel = "حذف",
            destructive = true,
            onConfirm = { viewModel.deleteTapSession(session) },
            onDismiss = { sessionToDelete = null }
        )
    }
}

private fun buildSrtFileName(audioName: String?): String {
    val rawName = audioName ?: "subtitles"
    val cleanName = rawName.substringBeforeLast(".").replace(" ", "_")
    return "${cleanName}_subbed.srt"
}

@Composable
private fun RecordingStatusPill(isRecording: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (isRecording) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (isRecording) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.heightIn(min = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            )
            Text(
                text = if (isRecording) "SYNCING LIVE" else "READY TO TAP",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
