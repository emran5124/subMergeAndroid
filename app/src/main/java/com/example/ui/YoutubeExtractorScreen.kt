package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import com.example.network.GeminiApiClient
import com.example.ui.theme.Radii
import com.example.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeExtractorScreen(
    viewModel: SubtitleStudioViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSettings: () -> Unit = {}
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
    val isBusy = extractionState is GeminiApiClient.CallStepState.Sending

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Text(
            text = "YouTube Subtitle Extractor & AI Refiner",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Provide a YouTube URL. The app will extract the SRV3 caption XML, clean and organize it with Gemini, and save an SRT file directly to /Downloads/yt-subs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // API warnings if none configured
        if (configs.isEmpty()) {
            StatusBanner(
                text = "You must register at least one Gemini API Key in the Settings tab first!",
                type = BannerType.ERROR,
                action = {
                    TextButton(onClick = onNavigateToSettings) {
                        Text("Go to Settings")
                    }
                }
            )
        }

        // Language Selector Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = "Target Language:",
                style = MaterialTheme.typography.labelLarge
            )
            Box {
                OutlinedButton(
                    onClick = { showLangMenu = true },
                    modifier = Modifier.heightIn(min = 40.dp)
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
            shape = Radii.lg,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { viewModel.setYoutubeUrlInput(it) },
                    label = { Text("YouTube URL or Video ID") },
                    placeholder = { Text("https://www.youtube.com/watch?v=...") },
                    leadingIcon = { Icon(Icons.Filled.Link, contentDescription = "URL Link") },
                    modifier = Modifier.fillMaxWidth().testTag("youtube_url_input"),
                    singleLine = true,
                    shape = Radii.md
                )

                Button(
                    onClick = { viewModel.startYoutubeSrv3ToSrtFlow() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("extract_button"),
                    enabled = urlInput.isNotBlank() && configs.isNotEmpty() && !isBusy,
                    shape = Radii.md
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current
                        )
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Working...")
                    } else {
                        Icon(Icons.Filled.CloudDownload, contentDescription = "Extract")
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Extract & Build Subtitle")
                    }
                }
            }
        }

        // Logs and execution reports
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Activity Log",
                style = MaterialTheme.typography.titleMedium
            )
            if (logText.isNotBlank() && !isBusy) {
                TextButton(onClick = { viewModel.clearYoutubeLog() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(Radii.md)
                .background(Color(0xFF1E293B))
                .padding(Spacing.md)
        ) {
            val logScrollState = rememberLazyListState()
            val logsList = logText.split("\n").filter { it.isNotBlank() }

            if (logsList.isEmpty()) {
                Text(
                    text = "No activity yet.\nExtract a subtitle to see progress here.",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Auto scroll to last log item
                LaunchedEffect(logsList.size) {
                    logScrollState.animateScrollToItem(logsList.size - 1)
                }

                LazyColumn(
                    state = logScrollState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
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
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        )
                    }
                }
            }
        }

        // Active State popups / banners for Gemini Api steps
        when (val state = extractionState) {
            is GeminiApiClient.CallStepState.Sending -> {
                StatusBanner(
                    text = "Querying Gemini API [${state.model}]...",
                    type = BannerType.INFO
                )
            }
            is GeminiApiClient.CallStepState.RetryingRateLimit -> {
                StatusBanner(
                    text = "Rate Limited (429). Retrying in ${state.delaySecondsLeft}s (Attempt ${state.attempt} of 4)...",
                    type = BannerType.WARNING
                )
            }
            is GeminiApiClient.CallStepState.VpnBlockPrompt -> {
                AlertDialog(
                    onDismissRequest = { /* No-op, forces action */ },
                    confirmButton = {
                        Button(onClick = { state.onContinuePressed() }) {
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
