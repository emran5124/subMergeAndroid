package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                    val timelinesWeightFraction by viewModel.timelinesWeightFraction.collectAsState()
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top segment: Player and controls - Scrollable with proportional weight
                        Column(
                            modifier = Modifier
                                .weight(if (listExpanded) (2.0f - timelinesWeightFraction).coerceIn(0.2f, 1.8f) else 1.0f)
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
                            Box(modifier = Modifier.weight(timelinesWeightFraction).fillMaxWidth()) {
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
