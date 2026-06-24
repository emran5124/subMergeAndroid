package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.GeminiApiClient
import com.example.utils.SrtParser

@Composable
fun AiSubtitleScreen(
    viewModel: SubtitleStudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
            var name = "Selected File"
            try {
                val cursor = context.contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            name = it.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            val mimeType = context.contentResolver.getType(uri) ?: "audio/mp3"
            viewModel.setAiSelectedAudio(uri, name, mimeType)
        }
    }

    val isAudio = aiMime?.startsWith("audio/") == true

    // Option 2 Tap to Sync State Collection
    val studioOption by viewModel.studioOptionSetting.collectAsState()

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

    androidx.activity.compose.BackHandler(enabled = (studioOption == 1 && aiAudioUri != null) || (studioOption == 2 && tapAudioUri != null)) {
        if (studioOption == 1) {
            viewModel.clearAiSelectedAudio()
        } else {
            viewModel.clearTapSelectedAudio()
        }
    }

    val tapFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
            var name = "Selected File"
            try {
                val cursor = context.contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            name = it.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            val mimeType = context.contentResolver.getType(uri) ?: "audio/mp3"
            viewModel.setTapSelectedAudio(uri, name, mimeType)
        }
    }

    val txtFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
            var name = "Selected Text"
            try {
                val cursor = context.contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            name = it.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
            viewModel.setTapSourceTxtFile(uri, name)
        }
    }

    var pendingSrtUri by remember { mutableStateOf<Uri?>(null) }
    var showSrtImportDialog by remember { mutableStateOf(false) }

    val srtFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingSrtUri = uri
            showSrtImportDialog = true
        }
    }

    val saveSrtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            val success = viewModel.writeTapSrtToUri(context, uri)
            if (success) {
                android.widget.Toast.makeText(context, "Saved successfully!", android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(context, "Save failed. Please try again.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Option Selector tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.setStudioOption(1) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (studioOption == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (studioOption == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "AI Option")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Option 1: AI Gemini")
            }

            Button(
                onClick = { viewModel.setStudioOption(2) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (studioOption == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (studioOption == 2) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height(48.dp)
            ) {
                Icon(Icons.Filled.Segment, contentDescription = "Manual Option")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Option 2: Tap-to-Sync")
            }
        }

        if (studioOption == 1) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
            Text(
                text = "AI Audio Transcription Studio",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            if (aiAudioUri != null) {
                IconButton(onClick = { viewModel.clearAiSelectedAudio() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear selected file", tint = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (aiAudioUri == null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Audiotrack,
                        contentDescription = "Audio track",
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "To start transcribing, please select an audio or video file from your system.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { fileLauncher.launch(arrayOf("audio/*", "video/*")) },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = "Pick")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Audio/Video File")
                    }
                }
            }
        } else if (aiLines.isEmpty()) {
            val setupScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(setupScrollState),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                     modifier = Modifier.fillMaxWidth(),
                     colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isAudio) Icons.Filled.Mic else Icons.Filled.Movie,
                            contentDescription = "File indicator",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Column {
                            Text(text = aiAudioName ?: "File", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = "Type: $aiMime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                if (!isAudio) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = "Warning: Option 1 is exclusively available for Audio files. Video transcription will be supported in coming options.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Option 1: Audio SRT Transcriber via Gemini",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    OutlinedTextField(
                        value = aiCustomPrompt,
                        onValueChange = { viewModel.setAiCustomPrompt(it) },
                        label = { Text("AI Instructions / System Prompt") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 220.dp),
                        supportingText = { Text("Supports '[sourceTextPlaceholder]' placeholder which dynamically swaps with the lines below.") }
                    )

                    OutlinedTextField(
                        value = aiSourceText,
                        onValueChange = { viewModel.setAiSourceText(it) },
                        label = { Text("Source Text Lines (Optional)") },
                        placeholder = { Text("text1\ntext2\ntext3...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 200.dp),
                        supportingText = { Text("If provided, Gemini forces each output SRT block line to align exactly with each source row.") }
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "Transcription Log:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            val statusLogStr = when (val s = aiTranscribeState) {
                                is GeminiApiClient.CallStepState.Idle -> "Ready to start transcription."
                                is GeminiApiClient.CallStepState.Sending -> "🔄 Directing multimodal audio to Gemini utilizing key ${s.keyDesc} with model [${s.model}]..."
                                is GeminiApiClient.CallStepState.RetryingRateLimit -> "⚠️ Limited (429): Retrying in [${s.delaySecondsLeft}s] (Key ${s.keyDesc})..."
                                is GeminiApiClient.CallStepState.VpnBlockPrompt -> "🛑 Geo-blocked (400/403): Toggle/Check your VPN and click RESUME."
                                is GeminiApiClient.CallStepState.Success -> "✓ Completed: ${s.responseText.take(150)}..."
                                is GeminiApiClient.CallStepState.OutOfOptions -> "❌ Failed: ${s.error}"
                                else -> "Waiting..."
                            }

                            Text(
                                text = statusLogStr,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (aiTranscribeState is GeminiApiClient.CallStepState.VpnBlockPrompt) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { (aiTranscribeState as GeminiApiClient.CallStepState.VpnBlockPrompt).onContinuePressed() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Resume Flow")
                                }
                            }
                        }
                    }

                    val isRunning = aiTranscribeState is GeminiApiClient.CallStepState.Sending || 
                                    aiTranscribeState is GeminiApiClient.CallStepState.RetryingRateLimit

                    Button(
                        onClick = { viewModel.startAiTranscription() },
                        enabled = !isRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (isRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Uploading file & waiting response...")
                        } else {
                            Icon(Icons.Filled.VideoSettings, contentDescription = "Transcribe")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Subtitles via Gemini")
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Segment: Player, Change File, and Editing Form (Scrollable to prevent crowding when showing video)
                val aiScrollState = rememberScrollState()
                val timelinesWeightFraction by viewModel.timelinesWeightFraction.collectAsState()
                Column(
                    modifier = Modifier
                        .weight((2.0f - timelinesWeightFraction).coerceIn(0.2f, 1.8f))
                        .fillMaxWidth()
                        .verticalScroll(aiScrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.AudioFile, contentDescription = "Audio track", tint = MaterialTheme.colorScheme.primary)
                                Text(text = aiAudioName ?: "Subtitles", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            }
                            
                            OutlinedButton(
                                onClick = { viewModel.clearAiSelectedAudio() },
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Reset/Change file", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Change File", fontSize = 12.sp)
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val showVideoPlayer by viewModel.showVideoPlayer.collectAsState()
                            val videoHeightDp by viewModel.videoHeightDp.collectAsState()
                            val isVideo = isVideoFile(aiAudioName ?: "", aiMime)
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
                                            mediaPlayer = viewModel.aiMediaPlayer,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    // Subtitle Overlay for AI Screen
                                    val currentLine = aiLines.find { aiPlayerPosMs >= it.startTimeMs && aiPlayerPosMs <= it.endTimeMs } 
                                        ?: aiLines.getOrNull(aiActiveIndex)

                                    if (currentLine != null) {
                                        val subtitleText = currentLine.text
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.toggleAiPlayback() }) {
                                    Icon(
                                        imageVector = if (aiPlayerIsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Button(
                                    onClick = { viewModel.playAiCurrentLineSegment() },
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    Icon(Icons.Filled.PlayCircle, contentDescription = "Play line segment")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play Active Segment")
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Text(
                                    text = "${SrtParser.formatTime(aiPlayerPosMs)} / ${SrtParser.formatTime(aiPlayerDurationMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Slider(
                                value = if (aiPlayerDurationMs > 0) aiPlayerPosMs.toFloat() / aiPlayerDurationMs.toFloat() else 0f,
                                onValueChange = {
                                    val target = (it * aiPlayerDurationMs.toFloat()).toLong()
                                    viewModel.seekAiPlayerToMs(target)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(4.dp))
                            LayoutSizeControls(viewModel = viewModel)
                        }
                    }

                    val activeLine = aiLines.getOrNull(aiActiveIndex)
                    if (activeLine != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Editing Block #${activeLine.index}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Line ${aiActiveIndex + 1} of ${aiLines.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                OutlinedTextField(
                                    value = activeLine.text,
                                    onValueChange = { viewModel.updateAiLineText(aiActiveIndex, it) },
                                    label = { Text("Subtitle Text") },
                                    modifier = Modifier.fillMaxWidth().testTag("ai_srt_text_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Start Time: ${SrtParser.formatTime(activeLine.startTimeMs)}", style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            OutlinedButton(
                                                onClick = { viewModel.updateAiLineTiming(aiActiveIndex, (activeLine.startTimeMs - 100).coerceAtLeast(0), activeLine.endTimeMs) },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(0.dp)
                                            ) { Text("-100ms", fontSize = 11.sp) }
                                            OutlinedButton(
                                                onClick = { viewModel.updateAiLineTiming(aiActiveIndex, (activeLine.startTimeMs + 100).coerceAtMost(activeLine.endTimeMs), activeLine.endTimeMs) },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(0.dp)
                                            ) { Text("+100ms", fontSize = 11.sp) }
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "End Time: ${SrtParser.formatTime(activeLine.endTimeMs)}", style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            OutlinedButton(
                                                onClick = { viewModel.updateAiLineTiming(aiActiveIndex, activeLine.startTimeMs, (activeLine.endTimeMs - 100).coerceAtLeast(activeLine.startTimeMs)) },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(0.dp)
                                            ) { Text("-100ms", fontSize = 11.sp) }
                                            OutlinedButton(
                                                onClick = { viewModel.updateAiLineTiming(aiActiveIndex, activeLine.startTimeMs, activeLine.endTimeMs + 100) },
                                                modifier = Modifier.weight(1f),
                                                contentPadding = PaddingValues(0.dp)
                                            ) { Text("+100ms", fontSize = 11.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Segment: Title and Scrollable list of subtitle blocks
                Column(
                    modifier = Modifier
                        .weight(timelinesWeightFraction)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Draggable Subtitles Header Block
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), shape = RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    change.consume()
                                    viewModel.setTimelinesWeightFraction(
                                        (timelinesWeightFraction - dragAmount * 0.002f).coerceIn(0.2f, 1.8f)
                                    )
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Top Drag handle pill
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )

                        Text(
                            text = "Subtitle Blocks (Tap row to edit & drag to resize)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }

                    val listState = rememberLazyListState()
                    
                    LaunchedEffect(aiActiveIndex) {
                        if (aiActiveIndex >= 0 && aiActiveIndex < aiLines.size) {
                            listState.animateScrollToItem(aiActiveIndex)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            ),
                            border = if (isCurrent) 
                                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                            else 
                                null
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
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
                                Spacer(modifier = Modifier.height(4.dp))
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
} else {
            // --- OPTION 2 CONTENT (TAP-TO-SYNC MANUAL MODE) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tap-to-Sync subtitler",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                if (tapAudioUri != null) {
                    IconButton(onClick = { viewModel.clearTapSelectedAudio() }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear selected file", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (tapAudioUri == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = "Manual Sync Track",
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Generate and sync subtitles manually. Simply load an Audio or Video file and optionally a text source to sync to, then 'tap' to record the timestamps live!",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { tapFileLauncher.launch(arrayOf("audio/*", "video/*")) },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = "Pick")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Audio/Video File")
                        }
                    }
                }
            } else {
                // Interactive Studio Layout
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Top Segment: Controls & Deck (Scrollable so it has plenty of space when video is enabled)
                    val tapTopScrollState = rememberScrollState()
                    val timelinesWeightFraction by viewModel.timelinesWeightFraction.collectAsState()
                    Column(
                        modifier = Modifier
                            .weight((2.0f - timelinesWeightFraction).coerceIn(0.2f, 1.8f))
                            .fillMaxWidth()
                            .verticalScroll(tapTopScrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Unified Compact Studio Control Deck Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Row 1: Audio File Name and Live Recording Status
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Filled.AudioFile,
                                            contentDescription = "Audio track",
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
                                    
                                    Text(
                                        text = if (tapIsRecording) "🔴 SYNCING LIVE" else "READY TO TAP",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                        color = if (tapIsRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                val showVideoPlayer by viewModel.showVideoPlayer.collectAsState()
                                val videoHeightDp by viewModel.videoHeightDp.collectAsState()
                                val isVideo = isVideoFile(tapAudioName ?: "", tapMime)
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
                                                mediaPlayer = viewModel.tapMediaPlayer,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        // Subtitle Overlay for Tap Screen (live display of tap subtitles)
                                        val currentLine = tapSrtLines.find { tapPlayerPosMs >= it.startTimeMs && tapPlayerPosMs <= it.endTimeMs }
                                        if (currentLine != null) {
                                            val subtitleText = currentLine.text
                                            if (subtitleText.isNotBlank()) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .padding(bottom = 12.dp, start = 12.dp, end = 12.dp)
                                                        .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 10.dp, vertical = 5.dp)
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

                                // Row 1.5: Playback Timeline & Sliders
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
                                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.seekTapBackward(5000) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Filled.Remove, contentDescription = "-5s", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                            }

                                            IconButton(
                                                onClick = { viewModel.toggleTapPlayback() },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (tapPlayerIsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                    contentDescription = "Play/Pause",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.seekTapForward(5000) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Filled.Add, contentDescription = "+5s", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                // Row 1.8: Text source status and clear action
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Filled.TextFormat,
                                            contentDescription = "TXT lines source",
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
                                        IconButton(
                                            onClick = { viewModel.clearTapSourceTxtFile() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Close,
                                                contentDescription = "Clear script",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = { txtFileLauncher.launch(arrayOf("text/plain")) },
                                            modifier = Modifier.height(24.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp)
                                        ) {
                                            Text("Load text script", fontSize = 10.sp)
                                        }
                                    }
                                }

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                // Row 1.9: SRT source import
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Filled.Subtitles,
                                            contentDescription = "SRT import",
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
                                        onClick = { srtFileLauncher.launch(arrayOf("*/*")) },
                                        modifier = Modifier.height(24.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("Import SRT", fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        // 2. Simple Unified Tapping Control Bar
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.tapTimingButton() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                        .testTag("tap_timing_live_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (tapIsRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(Icons.Filled.Timer, contentDescription = "Tap Timing")
                                    Spacer(modifier = Modifier.width(8.dp))
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
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.undoLastTap() },
                                        enabled = tapSrtLines.isNotEmpty(),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Undo last tap", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Undo last tap", fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.finishRecordingTiming()
                                            val rawName = tapAudioName ?: "subtitles"
                                            val cleanName = rawName.substringBeforeLast(".").replace(" ", "_")
                                            val defaultFileName = "${cleanName}_subbed.srt"
                                            saveSrtLauncher.launch(defaultFileName)
                                        },
                                        enabled = tapSrtLines.isNotEmpty(),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (tapIsRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.Save, contentDescription = "Finish and save", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (tapIsRecording) "Finish & Save" else "Save SRT File", fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LayoutSizeControls(viewModel = viewModel)
                    }

                    // Bottom Segment: Header and Lines list
                    Column(
                        modifier = Modifier
                            .weight(timelinesWeightFraction)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 4. Draggable Timelines Header Block
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), shape = RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures { change, dragAmount ->
                                        change.consume()
                                        viewModel.setTimelinesWeightFraction(
                                            (timelinesWeightFraction - dragAmount * 0.002f).coerceIn(0.2f, 1.8f)
                                        )
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Top Drag handle pill
                            Box(
                                modifier = Modifier
                                    .width(48.dp)
                                    .height(4.dp)
                                    .background(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Timelines (${tapSrtLines.size} entries)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.addNewTapLinePlaceholder() },
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "Add Line", modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Line", fontSize = 10.sp)
                                    }

                                    if (tapSrtLines.isNotEmpty()) {
                                        Button(
                                            onClick = {
                                                val rawName = tapAudioName ?: "subtitles"
                                                val cleanName = rawName.substringBeforeLast(".").replace(" ", "_")
                                                val defaultFileName = "${cleanName}_subbed.srt"
                                                saveSrtLauncher.launch(defaultFileName)
                                            },
                                            modifier = Modifier.height(28.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                            contentPadding = PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Icon(Icons.Filled.Save, contentDescription = "Export SRT", modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Export SRT", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Scrollable Lines List (LazyColumn with Dynamic Inline Editors)
                        val tapListState = rememberLazyListState()

                        LaunchedEffect(tapActiveIndex) {
                            if (tapIsRecording && tapActiveIndex >= 0 && tapActiveIndex < tapSrtLines.size) {
                                tapListState.animateScrollToItem(tapActiveIndex)
                            }
                        }

                        LazyColumn(
                            state = tapListState,
                            modifier = Modifier.fillMaxSize(),
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
                                Column(modifier = Modifier.padding(10.dp)) {
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
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (isCurrent) {
                                        // Inline editing controls when selected/tapped!
                                        Spacer(modifier = Modifier.height(8.dp))

                                        OutlinedTextField(
                                            value = item.text,
                                            onValueChange = { viewModel.updateTapLineText(idx, it) },
                                            label = { Text("Edit Line Content text") },
                                            modifier = Modifier.fillMaxWidth().testTag("tap_srt_text_input_$idx"),
                                            textStyle = MaterialTheme.typography.bodyMedium,
                                            singleLine = true
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(text = "Start: ${SrtParser.formatTime(item.startTimeMs)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    OutlinedButton(
                                                        onClick = { viewModel.updateTapLineTiming(idx, (item.startTimeMs - 100).coerceAtLeast(0), item.endTimeMs) },
                                                        modifier = Modifier.weight(1f).height(28.dp),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) { Text("-100ms", fontSize = 10.sp) }
                                                    OutlinedButton(
                                                        onClick = { viewModel.updateTapLineTiming(idx, (item.startTimeMs + 100).coerceAtMost(item.endTimeMs), item.endTimeMs) },
                                                        modifier = Modifier.weight(1f).height(28.dp),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) { Text("+100ms", fontSize = 10.sp) }
                                                }
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(text = "End: ${SrtParser.formatTime(item.endTimeMs)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    OutlinedButton(
                                                        onClick = { viewModel.updateTapLineTiming(idx, item.startTimeMs, (item.endTimeMs - 100).coerceAtLeast(item.startTimeMs)) },
                                                        modifier = Modifier.weight(1f).height(28.dp),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) { Text("-100ms", fontSize = 10.sp) }
                                                    OutlinedButton(
                                                        onClick = { viewModel.updateTapLineTiming(idx, item.startTimeMs, item.endTimeMs + 100) },
                                                        modifier = Modifier.weight(1f).height(28.dp),
                                                        contentPadding = PaddingValues(0.dp)
                                                    ) { Text("+100ms", fontSize = 10.sp) }
                                                }
                                            }
                                        }
                                    } else {
                                        // Standard display text for non-active entries
                                        Spacer(modifier = Modifier.height(4.dp))
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
    }

    if (showSrtImportDialog && pendingSrtUri != null) {
        AlertDialog(
            onDismissRequest = {
                showSrtImportDialog = false
                pendingSrtUri = null
            },
            title = {
                Text(
                    text = "ورود فایل SRT (Import SRT)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "لطفا یکی از حالت‌های زیر را برای وارد کردن فایل زیرنویس انتخاب کنید:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Button(
                        onClick = {
                            val uri = pendingSrtUri
                            if (uri != null) {
                                viewModel.importSrtToTapLines(uri, mode = 1) { success, message ->
                                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                            showSrtImportDialog = false
                            pendingSrtUri = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("حالت ۱: هماهنگ‌سازی فقط زمان‌بندی", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Text(
                        text = "توضیح: فقط زمان‌بندی لاین‌های جدا شده فعلی با فایل SRT ورودی هماهنگ می‌شود و متن‌های شما حفظ می‌گردد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    Button(
                        onClick = {
                            val uri = pendingSrtUri
                            if (uri != null) {
                                viewModel.importSrtToTapLines(uri, mode = 2) { success, message ->
                                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                            showSrtImportDialog = false
                            pendingSrtUri = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("حالت ۲: جایگزینی کامل (زمان + متن)", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Text(
                        text = "توضیح: تمام لاین‌های فعلی به همراه زمان‌بندی و متن‌ها با اطلاعات فایل SRT ورودی کاملاً جایگزین می‌شوند.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showSrtImportDialog = false
                        pendingSrtUri = null
                    }
                ) {
                    Text("انصراف (Cancel)")
                }
            }
        )
    }
}
}
