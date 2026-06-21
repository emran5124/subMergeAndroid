package com.example.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.GeminiApiClient

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
