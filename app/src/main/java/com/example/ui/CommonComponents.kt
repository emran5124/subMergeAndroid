package com.example.ui

import android.media.MediaPlayer
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
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
                    val currentLine = lines.find { playerPosMs >= it.startTimeMs && playerPosMs <= it.endTimeMs }

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

fun isRtlText(text: String): Boolean {
    for (char in text) {
        val code = char.code
        // Arabic, Persian, Hebrew, and other RTL ranges
        if (code in 0x0590..0x05FF || code in 0x0600..0x06FF || code in 0x0750..0x077F || code in 0x08A0..0x08FF || code in 0xFB50..0xFDFF || code in 0xFE70..0xFEFF) {
            return true
        }
    }
    return false
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
            val transText = line.selectedTranslationText ?: ""
            val isRtlAlternative = line.alternatives.any { isRtlText(it.text) } || isRtlText(line.nativeText)
            val transIsRtl = isRtlText(transText) || (transText.isEmpty() && isRtlAlternative)
            val transLayoutDirection = if (transIsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides transLayoutDirection) {
                OutlinedTextField(
                    value = transText,
                    onValueChange = { onTranslationEdit(it) },
                    label = { Text("Reviewed / Selected translation") },
                    placeholder = { Text("Click translate alternative below or type correct translations here...") },
                    modifier = Modifier.fillMaxWidth().testTag("trans_srt_input"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = TextDirection.Content
                    ),
                    maxLines = 3
                )
            }

            // Native subtitle Text
            val nativeText = line.nativeText
            val nativeIsRtl = isRtlText(nativeText)
            val nativeLayoutDirection = if (nativeIsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides nativeLayoutDirection) {
                OutlinedTextField(
                    value = nativeText,
                    onValueChange = { onNativeEdit(it) },
                    label = { Text("Native Text (main.srt)") },
                    modifier = Modifier.fillMaxWidth().testTag("native_srt_input"),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        textDirection = TextDirection.Content
                    ),
                    maxLines = 2
                )
            }

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
                            val altIsRtl = isRtlText(alt.text)
                            CompositionLocalProvider(LocalLayoutDirection provides (if (altIsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                                Text(
                                    text = alt.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        textDirection = TextDirection.Content
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
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
                        val customText = if (!line.selectedTranslationText.isNullOrBlank() && isCustomSelected) {
                            line.selectedTranslationText ?: ""
                        } else {
                            "Tap to select Custom Review and type below..."
                        }
                        val customIsRtl = isRtlText(customText)
                        CompositionLocalProvider(LocalLayoutDirection provides (if (customIsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                            Text(
                                text = customText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDirection = TextDirection.Content
                                ),
                                fontStyle = if (line.selectedTranslationText.isNullOrBlank() && isCustomSelected) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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

                    val nativeIsRtl = isRtlText(item.nativeText)
                    CompositionLocalProvider(LocalLayoutDirection provides (if (nativeIsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                        Text(
                            text = "Native: ${item.nativeText}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDirection = TextDirection.Content
                            ),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (!item.selectedTranslationText.isNullOrBlank()) {
                        val reviewText = item.selectedTranslationText ?: ""
                        val reviewIsRtl = isRtlText(reviewText)
                        CompositionLocalProvider(LocalLayoutDirection provides (if (reviewIsRtl) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                            Text(
                                text = "Review: $reviewText",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDirection = TextDirection.Content
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
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

fun resolveFontFamily(familyStr: String, context: android.content.Context? = null): FontFamily {
    return com.example.utils.FontUtils.resolveFontFamily(familyStr, context)
}

fun resolveFontWeight(weightStr: String): FontWeight {
    return when (weightStr.lowercase().trim()) {
        "normal" -> FontWeight.Normal
        "medium" -> FontWeight.Medium
        "semibold", "semi-bold" -> FontWeight.SemiBold
        "bold" -> FontWeight.Bold
        "extrabold", "extra-bold" -> FontWeight.ExtraBold
        else -> FontWeight.Bold
    }
}

fun parseColorSafe(hexStr: String, fallback: Color): Color {
    return try {
        val clean = hexStr.trim().removePrefix("#")
        when (clean.length) {
            6 -> Color((0xFF000000 or clean.toLong(16)).toInt())
            8 -> Color(clean.toLong(16).toInt())
            3 -> {
                val r = clean[0].toString().repeat(2)
                val g = clean[1].toString().repeat(2)
                val b = clean[2].toString().repeat(2)
                Color((0xFF000000 or "$r$g$b".toLong(16)).toInt())
            }
            else -> fallback
        }
    } catch (e: Exception) {
        fallback
    }
}

@Composable
fun AutoResizeSubtitleText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF2D3A3A),
    fontFamily: FontFamily = FontFamily.Default,
    fontWeight: FontWeight = FontWeight.Bold,
    textAlign: TextAlign = TextAlign.Center,
    maxFontSize: TextUnit = 48.sp,
    minFontSize: TextUnit = 12.sp,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()

        val maxPxWidth = with(density) { (maxWidth - 24.dp).toPx().coerceAtLeast(10f) }
        val maxPxHeight = with(density) { (maxHeight - 24.dp).toPx().coerceAtLeast(10f) }

        val targetFontSize = remember(text, maxWidth, maxHeight, fontFamily, fontWeight) {
            if (text.isBlank()) return@remember 24.sp

            var low = minFontSize.value
            var high = maxFontSize.value
            var best = low

            repeat(10) {
                val mid = (low + high) / 2f
                val style = TextStyle(
                    fontSize = mid.sp,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    textAlign = textAlign,
                    lineHeight = (mid * 1.32f).sp
                )
                val result = textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = style,
                    constraints = Constraints(maxWidth = maxPxWidth.toInt())
                )

                if (result.size.height <= maxPxHeight && result.size.width <= maxPxWidth && !result.hasVisualOverflow) {
                    best = mid
                    low = mid + 0.5f
                } else {
                    high = mid - 0.5f
                }
            }
            best.sp
        }

        Text(
            text = text,
            color = color,
            style = TextStyle(
                fontSize = targetFontSize,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                textAlign = textAlign,
                lineHeight = targetFontSize * 1.32f
            ),
            textAlign = textAlign,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ReviewerSubtitleReaderCard(
    subtitleText: String,
    textColorHex: String = "#2D3A3A",
    bgColorHex: String = "#E8D8C8",
    fontFamilyName: String = "Default",
    fontWeightName: String = "Bold",
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bgColor = parseColorSafe(bgColorHex, Color(0xFFE8D8C8))
    val textColor = parseColorSafe(textColorHex, Color(0xFF2D3A3A))
    val fontFamily = resolveFontFamily(fontFamilyName, context)
    val fontWeight = resolveFontWeight(fontWeightName)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (subtitleText.isNotBlank()) {
                AutoResizeSubtitleText(
                    text = subtitleText,
                    color = textColor,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "",
                    color = textColor.copy(alpha = 0.35f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = fontFamily,
                    fontWeight = fontWeight,
                    textAlign = TextAlign.Center
                )
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
    val coroutineScope = rememberCoroutineScope()
    val isVideo = isVideoFile(mediaName, null)
    val videoHeightDp by viewModel.videoHeightDp.collectAsState()

    val readerTextColor by viewModel.readerTextColor.collectAsState()
    val readerBgColor by viewModel.readerBgColor.collectAsState()
    val readerFontFamily by viewModel.readerFontFamily.collectAsState()
    val readerFontWeight by viewModel.readerFontWeight.collectAsState()

    val lines by viewModel.srtLines.collectAsState()
    val currentLine = lines.find { playerPosMs >= it.startTimeMs && playerPosMs <= it.endTimeMs }
    val subtitleText = currentLine?.let { it.selectedTranslationText ?: it.nativeText } ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isVideo) {
                val selectedPage by viewModel.selectedPlayerPage.collectAsState()
                val pagerState = rememberPagerState(initialPage = selectedPage, pageCount = { 2 })

                LaunchedEffect(selectedPage) {
                    if (pagerState.currentPage != selectedPage) {
                        pagerState.scrollToPage(selectedPage)
                    }
                }

                LaunchedEffect(pagerState.currentPage) {
                    if (pagerState.currentPage != selectedPage) {
                        viewModel.setSelectedPlayerPage(pagerState.currentPage)
                    }
                }

                // Top Header Switcher: Video vs Subtitle Reader Page
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Segmented Switch Pills
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Tab 0: Video
                        Surface(
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (pagerState.currentPage == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (pagerState.currentPage == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.Videocam, contentDescription = "ویدیو", modifier = Modifier.size(16.dp))
                                Text("ویدیو", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Tab 1: Reader
                        Surface(
                            onClick = {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (pagerState.currentPage == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Filled.Subtitles, contentDescription = "نمایشگر متن", modifier = Modifier.size(16.dp))
                                Text("نمایشگر متن", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Quick Toggle Flip Button
                    IconButton(
                        onClick = {
                            val target = if (pagerState.currentPage == 0) 1 else 0
                            coroutineScope.launch { pagerState.animateScrollToPage(target) }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (pagerState.currentPage == 0) Icons.Filled.SwapHoriz else Icons.Filled.Videocam,
                            contentDescription = "تغییر حالت نمایش",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Swipeable / Slidable Horizontal Pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(videoHeightDp.dp)
                ) { page ->
                    if (page == 0) {
                        // Page 0: Clean Video View (WITHOUT subtitle overlay text on top of the video)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
                        }
                    } else {
                        // Page 1: Dedicated Subtitle Reader Screen (with custom text & bg color, auto-size centered)
                        ReviewerSubtitleReaderCard(
                            subtitleText = subtitleText,
                            textColorHex = readerTextColor,
                            bgColorHex = readerBgColor,
                            fontFamilyName = readerFontFamily,
                            fontWeightName = readerFontWeight,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Page Indicator Dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(2) { index ->
                        val isSelected = (pagerState.currentPage == index)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (isSelected) 18.dp else 7.dp, 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                .clickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                }
                        )
                    }
                }
            } else {
                // Audio Only Media (No Video Stream): Default directly to Subtitle Reader Card!
                ReviewerSubtitleReaderCard(
                    subtitleText = subtitleText,
                    textColorHex = readerTextColor,
                    bgColorHex = readerBgColor,
                    fontFamilyName = readerFontFamily,
                    fontWeightName = readerFontWeight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(videoHeightDp.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (isVideo) Icons.Filled.Videocam else Icons.Filled.Audiotrack,
                    contentDescription = if (isVideo) "Video Track" else "Audio Track",
                    tint = MaterialTheme.colorScheme.primary
                )
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
                        text = "ابعاد صفحه و سایز کادر پخش / Layout Sizing",
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

                // Video/Reader Box Height Control
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ارتفاع کادر تصویر یا متن: ${videoHeightDp.toInt()} dp",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { viewModel.setVideoHeightDp(videoHeightDp - 20f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease Height", modifier = Modifier.size(14.dp))
                            }
                            IconButton(
                                onClick = { viewModel.setVideoHeightDp(videoHeightDp + 20f) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Increase Height", modifier = Modifier.size(14.dp))
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
