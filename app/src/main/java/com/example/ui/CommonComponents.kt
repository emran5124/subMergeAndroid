package com.example.ui

import android.media.MediaPlayer
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.SubtitleRepository
import com.example.utils.SrtParser

@Composable
fun ActiveMediaPlayerComponent(
    viewModel: SubtitleStudioViewModel,
    playerIsPlaying: Boolean,
    playerPosMs: Long,
    playerDurationMs: Long,
    autoPlay: Boolean,
    mediaName: String,
    onTogglePlay: () -> Unit,
    onPlaySegment: () -> Unit,
    onToggleAutoPlay: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val showVideoPlayer by viewModel.showVideoPlayer.collectAsState()
            val videoHeightDp by viewModel.videoHeightDp.collectAsState()
            val isVideo = isVideoFile(mediaName, null)

            if (showVideoPlayer && isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(videoHeightDp.dp)
                        .background(Color.Black)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableVideoBox(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        VideoSurfaceView(
                            mediaPlayer = viewModel.mediaPlayer,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Subtitle Overlay
                    val lines by viewModel.srtLines.collectAsState()
                    val activeIdx by viewModel.activeLineIndex.collectAsState()
                    val currentLine = lines.find { playerPosMs >= it.startTimeMs && playerPosMs <= it.endTimeMs } 
                        ?: lines.getOrNull(activeIdx)

                    if (currentLine != null) {
                        val subtitleText = currentLine.selectedTranslationText ?: currentLine.nativeText
                        if (subtitleText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(4.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Audiotrack, contentDescription = "Audio Track", tint = MaterialTheme.colorScheme.primary)
                Text(
                     text = mediaName,
                     style = MaterialTheme.typography.bodyMedium,
                     fontWeight = FontWeight.Bold,
                     maxLines = 1
                )
            }

            // Simple progress row
            LinearProgressIndicator(
                progress = {
                    if (playerDurationMs > 0) playerPosMs.toFloat() / playerDurationMs.toFloat() else 0f
                },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = SrtParser.formatTime(playerPosMs), style = MaterialTheme.typography.bodySmall)
                Text(text = SrtParser.formatTime(playerDurationMs), style = MaterialTheme.typography.bodySmall)
            }

            // Controllers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AutoPlay Switch
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = autoPlay, onCheckedChange = { onToggleAutoPlay(it) })
                    Text(text = "Auto Play", style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Play segment
                    OutlinedButton(onClick = onPlaySegment) {
                        Icon(Icons.Filled.Segment, contentDescription = "Play segment")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Segment", fontSize = 11.sp)
                    }

                    // Toggle Play / Pause
                    Button(onClick = onTogglePlay, modifier = Modifier.size(height = 40.dp, width = 96.dp)) {
                        Icon(
                            if (playerIsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play pause"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            LayoutSizeControls(viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveLineEditingPanel(
    combinedLines: List<SubtitleRepository.CombinedSrtLine>,
    activeIndex: Int,
    onTimingAdjust: (startOffset: Long, endOffset: Long) -> Unit,
    onNativeEdit: (String) -> Unit,
    onTranslationSelect: (fileName: String, text: String) -> Unit,
    onTranslationEdit: (String) -> Unit
) {
    if (activeIndex < 0 || activeIndex >= combinedLines.size) return
    val line = combinedLines[activeIndex]

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Timing controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Timing (main.srt):",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${SrtParser.formatTime(line.startTimeMs)} --> ${SrtParser.formatTime(line.endTimeMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Start", style = MaterialTheme.typography.bodySmall, fontSize = 9.sp)
                        Row {
                            IconButton(onClick = { onTimingAdjust(-100, 0) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Remove, contentDescription = "-100", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onTimingAdjust(100, 0) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = "+100", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("End", style = MaterialTheme.typography.bodySmall, fontSize = 9.sp)
                        Row {
                            IconButton(onClick = { onTimingAdjust(0, -100) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Remove, contentDescription = "-100", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onTimingAdjust(0, 100) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = "+100", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Display current reviewer text
            OutlinedTextField(
                value = line.selectedTranslationText ?: "",
                onValueChange = { onTranslationEdit(it) },
                label = { Text("Reviewed / Selected translation") },
                placeholder = { Text("Click translate alternative below or type correct translations here...") },
                modifier = Modifier.fillMaxWidth().testTag("trans_srt_input"),
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 3
            )

            // Native subtitle Text
            OutlinedTextField(
                value = line.nativeText,
                onValueChange = { onNativeEdit(it) },
                label = { Text("Native Text (main.srt)") },
                modifier = Modifier.fillMaxWidth().testTag("native_srt_input"),
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )

            // Alternative translations list shows actual corresponding lines from other translation files
            Text(
                text = "Alternative Translations (Click to Select / Compare):",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                line.alternatives.forEach { alt ->
                    val isSelected = line.selectedTranslationFileName == alt.fileName
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTranslationSelect(alt.fileName, alt.text) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            }
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = alt.fileName,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = alt.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Custom Edit and review selection card
                val isCustomSelected = line.selectedTranslationFileName == "custom" || line.selectedTranslationFileName == null
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTranslationSelect("custom", line.selectedTranslationText ?: "") },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCustomSelected) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isCustomSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Custom Review / Free Edit Input",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isCustomSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isCustomSelected) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editing",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!line.selectedTranslationText.isNullOrBlank() && isCustomSelected) {
                                line.selectedTranslationText
                            } else {
                                "Tap to select Custom Review and type below..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = if (line.selectedTranslationText.isNullOrBlank() && isCustomSelected) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitleLinesListView(
    lines: List<SubtitleRepository.CombinedSrtLine>,
    activeIdx: Int,
    onLineSelect: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    // Smooth scroll to selected active line whenever activeLine changes
    LaunchedEffect(activeIdx) {
        if (activeIdx >= 0 && activeIdx < lines.size) {
            listState.animateScrollToItem(activeIdx)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(lines) { idx, item ->
            val isActive = idx == activeIdx
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLineSelect(idx) }
                    .testTag("line_item_$idx"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    }
                ),
                border = if (isActive) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)) else null
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${item.index}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "${SrtParser.formatTime(item.startTimeMs)} --> ${SrtParser.formatTime(item.endTimeMs)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "Native: ${item.nativeText}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    if (!item.selectedTranslationText.isNullOrBlank()) {
                        Text(
                            text = "Review: ${item.selectedTranslationText}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

fun isVideoFile(fileName: String?, mimeType: String?): Boolean {
    if (mimeType?.lowercase()?.startsWith("video/") == true) return true
    val name = fileName?.lowercase() ?: return false
    return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") || 
           name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".3gp") || 
           name.endsWith(".flv") || name.endsWith(".mpeg") || name.endsWith(".mpg")
}

@Composable
fun ZoomableVideoBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale == 1f) {
                        offset = Offset.Zero
                    } else {
                        offset += pan
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        ) {
            content()
        }
        
        if (scale > 1f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${"%.1f".format(scale)}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun VideoSurfaceView(
    mediaPlayer: MediaPlayer?,
    modifier: Modifier = Modifier
) {
    if (mediaPlayer == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Keep track of active MediaPlayer and its binding to the SurfaceHolder
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        try {
                            mediaPlayer.setDisplay(holder)
                            mediaPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                        } catch (e: Exception) {
                            Log.e("VideoSurfaceView", "Error setting display holder", e)
                        }
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        try {
                            mediaPlayer.setDisplay(null)
                        } catch (e: Exception) {
                            Log.e("VideoSurfaceView", "Error removing display holder", e)
                        }
                    }
                })
            }
        },
        modifier = modifier
    )
}

@Composable
fun LayoutSizeControls(
    viewModel: SubtitleStudioViewModel,
    modifier: Modifier = Modifier
) {
    val videoHeightDp by viewModel.videoHeightDp.collectAsState()
    val timelinesWeightFraction by viewModel.timelinesWeightFraction.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Resize View",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "ابعاد صفحه و سایز ویدیو / Layout & Video Sizing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse"
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Video Height Control
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ارتفاع ویدیو / Video Height: ${videoHeightDp.toInt()} dp",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { viewModel.setVideoHeightDp(videoHeightDp - 20f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease Video", modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { viewModel.setVideoHeightDp(videoHeightDp + 20f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase Video", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Slider(
                        value = videoHeightDp,
                        onValueChange = { viewModel.setVideoHeightDp(it) },
                        valueRange = 80f..400f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Timelines Height / Weight Control
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ارتفاع منوی خطوط زمان / Timelines Weight: ${"%.2f".format(timelinesWeightFraction)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { viewModel.setTimelinesWeightFraction(timelinesWeightFraction - 0.1f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Decrease weight", modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { viewModel.setTimelinesWeightFraction(timelinesWeightFraction + 0.1f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Increase weight", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Slider(
                        value = timelinesWeightFraction,
                        onValueChange = { viewModel.setTimelinesWeightFraction(it) },
                        valueRange = 0.2f..1.8f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewerActiveMediaPlayerComponent(
    viewModel: ReviewerViewModel,
    playerIsPlaying: Boolean,
    playerPosMs: Long,
    playerDurationMs: Long,
    autoPlay: Boolean,
    mediaName: String,
    onTogglePlay: () -> Unit,
    onPlaySegment: () -> Unit,
    onToggleAutoPlay: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val showVideoPlayer = true
            val videoHeightDp = 200f
            val isVideo = isVideoFile(mediaName, null)

            if (showVideoPlayer && isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(videoHeightDp.dp)
                        .background(Color.Black)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableVideoBox(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        VideoSurfaceView(
                            mediaPlayer = viewModel.mediaPlayer,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Subtitle Overlay
                    val lines by viewModel.srtLines.collectAsState()
                    val activeIdx by viewModel.activeLineIndex.collectAsState()
                    val currentLine = lines.find { playerPosMs >= it.startTimeMs && playerPosMs <= it.endTimeMs } 
                        ?: lines.getOrNull(activeIdx)

                    if (currentLine != null) {
                        val subtitleText = currentLine.selectedTranslationText ?: currentLine.nativeText
                        if (subtitleText.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
                                    .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(4.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Audiotrack, contentDescription = "Audio Track", tint = MaterialTheme.colorScheme.primary)
                Text(
                     text = mediaName,
                     style = MaterialTheme.typography.bodyMedium,
                     fontWeight = FontWeight.Bold,
                     maxLines = 1
                )
            }

            // Simple progress row
            LinearProgressIndicator(
                progress = {
                    if (playerDurationMs > 0) playerPosMs.toFloat() / playerDurationMs.toFloat() else 0f
                },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = SrtParser.formatTime(playerPosMs), style = MaterialTheme.typography.bodySmall)
                Text(text = SrtParser.formatTime(playerDurationMs), style = MaterialTheme.typography.bodySmall)
            }

            // Controllers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // AutoPlay Switch
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = autoPlay, onCheckedChange = { onToggleAutoPlay(it) })
                    Text(text = "Auto Play", style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Play segment
                    OutlinedButton(onClick = onPlaySegment) {
                        Icon(Icons.Filled.Segment, contentDescription = "Play segment")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Segment", fontSize = 11.sp)
                    }

                    // Toggle Play / Pause
                    Button(onClick = onTogglePlay, modifier = Modifier.size(height = 40.dp, width = 96.dp)) {
                        Icon(
                            if (playerIsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play pause"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ReviewerLayoutSizeControls(viewModel = viewModel)
        }
    }
}

@Composable
fun ReviewerLayoutSizeControls(
    viewModel: ReviewerViewModel,
    modifier: Modifier = Modifier
) {
    val timelinesWeightFraction by viewModel.timelinesWeightFraction.collectAsState()
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Resize View",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "ابعاد صفحه / Layout Sizing",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse"
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Timelines Height / Weight Control
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ارتفاع منوی خطوط زمان / Timelines Weight: ${"%.2f".format(timelinesWeightFraction)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { viewModel.setTimelinesWeightFraction(timelinesWeightFraction - 0.1f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Decrease weight", modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { viewModel.setTimelinesWeightFraction(timelinesWeightFraction + 0.1f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Increase weight", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    Slider(
                        value = timelinesWeightFraction,
                        onValueChange = { viewModel.setTimelinesWeightFraction(it) },
                        valueRange = 0.2f..1.8f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
