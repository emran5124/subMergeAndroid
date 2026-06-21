package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.data.ApiKeyConfig
import com.example.data.SubtitleRepository
import com.example.network.GeminiApiClient
import com.example.utils.SrtParser
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import android.view.SurfaceView
import android.view.SurfaceHolder
import android.media.MediaPlayer
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeExtractorScreen(
    viewModel: SubtitleStudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val urlInput by viewModel.youtubeUrlInput.collectAsState()
    val extractionState by viewModel.youtubeExtractionState.collectAsState()
    val logText by viewModel.youtubeLog.collectAsState()
    val preferredLanguage by viewModel.preferredLanguage.collectAsState()

    val configs by viewModel.apiKeyConfigs.collectAsState()

    var showLangMenu by remember { mutableStateOf(false) }
    val commonLanguages = listOf(
        "ar" to "Arabic (العربية)",
        "fa" to "Persian (فارسی)",
        "en" to "English",
        "es" to "Spanish (Español)",
        "fr" to "French (Français)"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "YouTube Subtitle Extractor & AI Refiner",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Provide a YouTube URL. The app will extract the SRV3 caption XML, clean and organize it with Gemini, and save an SRT file directly to /Downloads/yt-subs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // API warnings if none configured
        if (configs.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = "You must register at least one Gemini API Key in the Settings tab first!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Language Selector Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Target Language:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Box {
                Button(
                    onClick = { showLangMenu = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Text(text = commonLanguages.firstOrNull { it.first == preferredLanguage }?.second ?: preferredLanguage)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Dropdown")
                }
                DropdownMenu(
                    expanded = showLangMenu,
                    onDismissRequest = { showLangMenu = false }
                ) {
                    commonLanguages.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                viewModel.setPreferredLanguage(code)
                                showLangMenu = false
                            }
                        )
                    }
                }
            }
        }

        // URL input card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { viewModel.setYoutubeUrlInput(it) },
                    label = { Text("YouTube URL or Video ID") },
                    placeholder = { Text("https://www.youtube.com/watch?v=...") },
                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = "URL Link") },
                    modifier = Modifier.fillMaxWidth().testTag("youtube_url_input"),
                    singleLine = true
                )

                Button(
                    onClick = { viewModel.startYoutubeSrv3ToSrtFlow() },
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("extract_button"),
                    enabled = urlInput.isNotBlank() && configs.isNotEmpty() && extractionState !is GeminiApiClient.CallStepState.Sending,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = "Extract")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Extract & Build Subtitle")
                }
            }
        }

        // Logs and execution reports
        Text(
            text = "Activity Log:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E293B))
                .padding(12.dp)
        ) {
            val logScrollState = rememberLazyListState()
            val logsList = logText.split("\n")
            
            // Auto scroll to last log item
            LaunchedEffect(logsList.size) {
                if (logsList.isNotEmpty()) {
                    logScrollState.animateScrollToItem(logsList.size - 1)
                }
            }

            LazyColumn(
                state = logScrollState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(logsList) { _, logLine ->
                    Text(
                        text = logLine,
                        color = when {
                            logLine.startsWith("❌") -> Color(0xFFF87171)
                            logLine.startsWith("✓") || logLine.startsWith("🎉") -> Color(0xFF34D399)
                            logLine.startsWith("⚠️") || logLine.startsWith("🛑") -> Color(0xFFFBBF24)
                            else -> Color(0xFFE2E8F0)
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        // Active State popups / banners for Gemini Api steps
        when (val state = extractionState) {
            is GeminiApiClient.CallStepState.Sending -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(
                            text = "Querying Gemini API [${state.model}]...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            is GeminiApiClient.CallStepState.RetryingRateLimit -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            progress = { (30f - state.delaySecondsLeft) / 30f },
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Rate Limited (429). Retrying in ${state.delaySecondsLeft}s (Attempt ${state.attempt} of 4)...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            is GeminiApiClient.CallStepState.VpnBlockPrompt -> {
                AlertDialog(
                    onDismissRequest = { /* No-op, forces action */ },
                    confirmButton = {
                        TextButton(onClick = { state.onContinuePressed() }) {
                            Text("Continue")
                        }
                    },
                    title = { Text("VPN Block Detected (400/403)") },
                    text = { Text("Gemini is geo-blocked or forbidden. Please enable or change your VPN server, and then press Continue.") },
                    icon = { Icon(Icons.Filled.VpnLock, contentDescription = "VPN") }
                )
            }
            is GeminiApiClient.CallStepState.ServerErrorOptionPrompt -> {
                AlertDialog(
                    onDismissRequest = { /* No-op */ },
                    confirmButton = {
                        Button(onClick = { state.onRetryCurrentTenTimes() }) {
                            Text("Retry 10 more times")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { state.onSkipToNext() }) {
                            Text("Skip to next model / API Key")
                        }
                    },
                    title = { Text("Server Error Detected") },
                    text = { Text("Message: ${state.message}\n\nWould you like to skip directly to the next configuration, or attempt 10 automatic retries?") },
                    icon = { Icon(Icons.Filled.Report, contentDescription = "Report") }
                )
            }
            else -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewerScreen(
    viewModel: SubtitleStudioViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val activePickerFolder by viewModel.activeProjectFolderUri.collectAsState()
    val subpList by viewModel.availableSubProjects.collectAsState()
    val activeSubpId by viewModel.selectedProjectSubFolderId.collectAsState()
    val activeSubpName by viewModel.selectedProjectSubFolderName.collectAsState()

    if (activeSubpId != null) {
        androidx.activity.compose.BackHandler {
            viewModel.closeSelectedProject()
        }
    }

    val combinedLines by viewModel.srtLines.collectAsState()
    val activeLineIdx by viewModel.activeLineIndex.collectAsState()
    val metadata by viewModel.projectMetadata.collectAsState()

    val playerIsPlaying by viewModel.playerIsPlaying.collectAsState()
    val playerPosMs by viewModel.playerCurrentPosMs.collectAsState()
    val playerDurationMs by viewModel.playerDuration.collectAsState()
    val autoPlayOnNextLine by viewModel.autoPlayOnNextLine.collectAsState()

    // SAF Directory launcher
    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.selectActiveMainFolder(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (activeSubpName != null) "Reviewer: $activeSubpName" else "Subtitle Reviewer Studio",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    if (activeSubpId != null) {
                        IconButton(onClick = { viewModel.closeSelectedProject() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    var trFileNameToExport by remember { mutableStateOf<String?>(null) }
                    
                    if (combinedLines.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.exportOutputSrt { fileName ->
                                trFileNameToExport = fileName
                            }
                        }) {
                            Icon(Icons.Filled.Save, contentDescription = "Save and export")
                        }
                    }

                    trFileNameToExport?.let { fileSaved ->
                        AlertDialog(
                            onDismissRequest = { trFileNameToExport = null },
                            confirmButton = {
                                TextButton(onClick = { trFileNameToExport = null }) {
                                    Text("Got it")
                                }
                            },
                            title = { Text("SRT File Exported!") },
                            text = { Text("Successfully built & saved:\n$fileSaved\ninside the current sub-project folder.") },
                            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = "Success", tint = Color(0xFF34D399)) }
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isExpanded = maxWidth > 680.dp

            if (activePickerFolder == null) {
                // Initial State: Prompt folder selection
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = "Project folder",
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Project Directory Selected",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Select your main workspace folder. Inside this folder, you will have subdirectories (e.g. project1, project2) containing your media files, main.srt, and translation SRTs.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { directoryPicker.launch(null) },
                        modifier = Modifier.height(48.dp).widthIn(min = 200.dp)
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = "Pick Folder")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Main Workspace Folder")
                    }
                }
            } else if (activeSubpId == null) {
                // Secondary State: Workspace folder selected, choose project
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Select Sub-Project Folder:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Workspace: Selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        OutlinedButton(onClick = { directoryPicker.launch(null) }) {
                            Text("Change Workspace")
                        }
                    }

                    if (subpList.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No project sub-directories (e.g. folders containing main.srt) were found inside this workspace folder.",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(subpList) { _, project ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.loadSubProject(project) },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Movie, contentDescription = "Project", tint = MaterialTheme.colorScheme.primary)
                                        Column {
                                            Text(text = project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(text = "ID: ${project.documentId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                        Icon(Icons.Filled.ChevronRight, contentDescription = "Open")
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Tertiary State: Subproject opened! Render editor layout (Tablet Landscape vs Phone vertical flow split)
                if (isExpanded) {
                    val tabletScrollState = rememberScrollState()
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left: Player and Controls (350.dp or weight 0.4) - Scrollable to prevent low-height screen cuts
                        Column(
                            modifier = Modifier
                                .weight(0.4f)
                                .fillMaxHeight()
                                .verticalScroll(tabletScrollState)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ActiveMediaPlayerComponent(
                                viewModel = viewModel,
                                playerIsPlaying = playerIsPlaying,
                                playerPosMs = playerPosMs,
                                playerDurationMs = playerDurationMs,
                                autoPlay = autoPlayOnNextLine,
                                mediaName = metadata?.mediaFileName ?: "No media file found",
                                onTogglePlay = { viewModel.togglePlayback() },
                                onPlaySegment = { viewModel.playCurrentLineSegment() },
                                onToggleAutoPlay = { viewModel.setAutoPlay(it) }
                            )

                            // Unified Prev/Next navigation on tablet
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { viewModel.selectActiveLineIndex(activeLineIdx - 1) },
                                        enabled = activeLineIdx > 0
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Line")
                                    }
                                    
                                    Text(
                                        text = "Line ${activeLineIdx + 1} of ${combinedLines.size}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    IconButton(
                                        onClick = { viewModel.selectActiveLineIndex(activeLineIdx + 1) },
                                        enabled = activeLineIdx < combinedLines.size - 1
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Line")
                                    }
                                }
                            }

                            ActiveLineEditingPanel(
                                combinedLines = combinedLines,
                                activeIndex = activeLineIdx,
                                onTimingAdjust = { start, end -> viewModel.updateActiveLineTiming(start, end) },
                                onNativeEdit = { viewModel.editActiveLineNativeText(it) },
                                onTranslationSelect = { filename, text -> viewModel.selectActiveLineTranslation(filename, text) },
                                onTranslationEdit = { viewModel.editActiveLineTranslationText(it) }
                            )

                            Spacer(modifier = Modifier.weight(1f))
                            
                             OutlinedButton(
                                 onClick = { viewModel.closeSelectedProject() }, // Force re-scan / Back to subdir list
                                 modifier = Modifier.fillMaxWidth()
                             ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Back to Subproject List")
                            }
                        }

                        // Right: List of srt lines (weight 0.6)
                        Column(
                            modifier = Modifier
                                .weight(0.6f)
                                .fillMaxHeight()
                                .padding(vertical = 16.dp, horizontal = 8.dp)
                        ) {
                            SubtitleLinesListView(
                                lines = combinedLines,
                                activeIdx = activeLineIdx,
                                onLineSelect = { viewModel.selectActiveLineIndex(it) }
                            )
                        }
                    }
                } else {
                    // Mobile Split Vertical Flow - Scrollable Top Form & Fixed Bottom List
                    var listExpanded by remember { mutableStateOf(false) }
                    val mobileScrollState = rememberScrollState()
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top segment: Player and controls - Scrollable with proportional weight
                        Column(
                            modifier = Modifier
                                .weight(if (listExpanded) 1.2f else 1.0f)
                                .fillMaxWidth()
                                .verticalScroll(mobileScrollState)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ActiveMediaPlayerComponent(
                                viewModel = viewModel,
                                playerIsPlaying = playerIsPlaying,
                                playerPosMs = playerPosMs,
                                playerDurationMs = playerDurationMs,
                                autoPlay = autoPlayOnNextLine,
                                mediaName = metadata?.mediaFileName ?: "No media file found",
                                onTogglePlay = { viewModel.togglePlayback() },
                                onPlaySegment = { viewModel.playCurrentLineSegment() },
                                onToggleAutoPlay = { viewModel.setAutoPlay(it) }
                            )

                            ActiveLineEditingPanel(
                                combinedLines = combinedLines,
                                activeIndex = activeLineIdx,
                                onTimingAdjust = { start, end -> viewModel.updateActiveLineTiming(start, end) },
                                onNativeEdit = { viewModel.editActiveLineNativeText(it) },
                                onTranslationSelect = { filename, text -> viewModel.selectActiveLineTranslation(filename, text) },
                                onTranslationEdit = { viewModel.editActiveLineTranslationText(it) }
                            )
                        }

                        // Divider with line index navigation controls
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { viewModel.selectActiveLineIndex(activeLineIdx - 1) },
                                enabled = activeLineIdx > 0,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Line")
                            }

                            Row(
                                modifier = Modifier
                                    .clickable { listExpanded = !listExpanded }
                                    .padding(vertical = 4.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Line ${activeLineIdx + 1} of ${combinedLines.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (listExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Toggle List Visibility",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { viewModel.selectActiveLineIndex(activeLineIdx + 1) },
                                enabled = activeLineIdx < combinedLines.size - 1,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next Line")
                            }
                        }

                        // Bottom: List of subtitles
                        if (listExpanded) {
                            Box(modifier = Modifier.weight(0.8f).fillMaxWidth()) {
                                SubtitleLinesListView(
                                    lines = combinedLines,
                                    activeIdx = activeLineIdx,
                                    onLineSelect = { viewModel.selectActiveLineIndex(it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
            val isVideo = isVideoFile(mediaName, null)

            if (showVideoPlayer && isVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.Black)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    VideoSurfaceView(
                        mediaPlayer = viewModel.mediaPlayer,
                        modifier = Modifier.fillMaxSize()
                    )

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
    val coroutineScope = rememberCoroutineScope()

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SubtitleStudioViewModel,
    modifier: Modifier = Modifier
) {
    val configs by viewModel.apiKeyConfigs.collectAsState()
    
    var apiKeyInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf("gemini-3.1-flash-lite") }
    var descInput by remember { mutableStateOf("") }

    var expandedModelMenu by remember { mutableStateOf(false) }
    val geminiModels = listOf(
        "gemini-3.1-flash-lite",
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-2.5-flash",
        "gemini-1.0-pro"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Gemini API Management",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Add New Gemini API Config:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key") },
                    placeholder = { Text("AlzaSy...") },
                    modifier = Modifier.fillMaxWidth().testTag("api_key_input"),
                    singleLine = true
                )

                Box {
                    OutlinedTextField(
                        value = modelInput,
                        onValueChange = {},
                        label = { Text("Model Name") },
                        modifier = Modifier.fillMaxWidth(),
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { expandedModelMenu = true }) {
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = expandedModelMenu,
                        onDismissRequest = { expandedModelMenu = false }
                    ) {
                        geminiModels.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    modelInput = m
                                    expandedModelMenu = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = descInput,
                    onValueChange = { descInput = it },
                    label = { Text("Short Description") },
                    placeholder = { Text("Project Key, Free Key, Work Key...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Button(
                    onClick = {
                        viewModel.addApiKeyConfig(apiKeyInput.trim(), modelInput, descInput.trim())
                        apiKeyInput = ""
                        descInput = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = apiKeyInput.isNotBlank()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Register Configuration")
                }
            }
        }

        Text(
            text = "General Preferences",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show Video Player when Available",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "نمایش ویدیو پلیر هنگام اجرای فایل‌های ویدیویی",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val showVideoPlayer by viewModel.showVideoPlayer.collectAsState()
                Switch(
                    checked = showVideoPlayer,
                    onCheckedChange = { viewModel.setShowVideoPlayer(it) },
                    modifier = Modifier.testTag("show_video_player_switch")
                )
            }
        }

        Text(
            text = "Configured API Keys (Ordered by Priority):",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (configs.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No Gemini credentials registered yet. Please configure one above.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(configs) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (item.description.isNotBlank()) item.description else "Key ID: ${item.id}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Model: ${item.modelName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Key: ${item.apiKey.take(6)}...${item.apiKey.takeLast(4)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Priority ordering buttons (Move up / down)
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val prev = configs[index - 1]
                                        viewModel.updateApiKeyConfig(item.copy(priority = prev.priority))
                                        viewModel.updateApiKeyConfig(prev.copy(priority = item.priority))
                                    }
                                },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                            }

                            IconButton(
                                onClick = {
                                    if (index < configs.size - 1) {
                                        val next = configs[index + 1]
                                        viewModel.updateApiKeyConfig(item.copy(priority = next.priority))
                                        viewModel.updateApiKeyConfig(next.copy(priority = item.priority))
                                    }
                                },
                                enabled = index < configs.size - 1
                            ) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                            }

                            IconButton(onClick = { viewModel.deleteApiKeyConfig(item) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

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
                        val isVideo = isVideoFile(aiAudioName ?: "", aiMime)
                        if (showVideoPlayer && isVideo) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(Color.Black)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                VideoSurfaceView(
                                    mediaPlayer = viewModel.aiMediaPlayer,
                                    modifier = Modifier.fillMaxSize()
                                )

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

                Text(
                    text = "Subtitle Blocks (Tap row to edit and jump player timing)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val listState = rememberLazyListState()
                
                LaunchedEffect(aiActiveIndex) {
                    if (aiActiveIndex >= 0 && aiActiveIndex < aiLines.size) {
                        listState.animateScrollToItem(aiActiveIndex)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                            val isVideo = isVideoFile(tapAudioName ?: "", tapMime)
                            if (showVideoPlayer && isVideo) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Color.Black)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    VideoSurfaceView(
                                        mediaPlayer = viewModel.tapMediaPlayer,
                                        modifier = Modifier.fillMaxSize()
                                    )

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

                            // Row 2: Playback Timeline & Sliders
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

                            // Row 3: Text source status and clear action
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

                    // 4. Timelines Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Timelines (${tapSrtLines.size} entries)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

                    // 5. Scrollable Lines List (LazyColumn with Dynamic Inline Editors)
                    val tapListState = rememberLazyListState()

                    LaunchedEffect(tapActiveIndex) {
                        if (tapIsRecording && tapActiveIndex >= 0 && tapActiveIndex < tapSrtLines.size) {
                            tapListState.animateScrollToItem(tapActiveIndex)
                        }
                    }

                    LazyColumn(
                        state = tapListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
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
}

fun isVideoFile(fileName: String?, mimeType: String?): Boolean {
    if (mimeType?.lowercase()?.startsWith("video/") == true) return true
    val name = fileName?.lowercase() ?: return false
    return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") || 
           name.endsWith(".avi") || name.endsWith(".mov") || name.endsWith(".3gp") || 
           name.endsWith(".flv") || name.endsWith(".mpeg") || name.endsWith(".mpg")
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
                            // Optional: adjust video scaling if needed
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

