package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.utils.FontUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Gemini API Management",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
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
        }

        item {
            Text(
                text = "General Preferences",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
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
        }

        item {
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
                            text = "Precise Playback Stop",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "توقف دقیق پخش تک لاین در ثانیه پایانی (در صورت غیرفعال بودن، توقف استاندارد قبلی انجام می‌شود)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val precisePlaybackStop by viewModel.precisePlaybackStop.collectAsState()
                    Switch(
                        checked = precisePlaybackStop,
                        onCheckedChange = { viewModel.setPrecisePlaybackStop(it) },
                        modifier = Modifier.testTag("precise_playback_stop_switch")
                    )
                }
            }
        }

        // --- Subtitle Reader Screen Customization ---
        item {
            val readerTextColor by viewModel.readerTextColor.collectAsState()
            val readerBgColor by viewModel.readerBgColor.collectAsState()
            val readerFontFamily by viewModel.readerFontFamily.collectAsState()
            val readerFontWeight by viewModel.readerFontWeight.collectAsState()

            var textColorInput by remember(readerTextColor) { mutableStateOf(readerTextColor) }
            var bgColorInput by remember(readerBgColor) { mutableStateOf(readerBgColor) }

            var expandedFontMenu by remember { mutableStateOf(false) }
            var expandedWeightMenu by remember { mutableStateOf(false) }

            val context = androidx.compose.ui.platform.LocalContext.current
            val customFonts = remember { 
        mutableStateOf(FontUtils.listCustomFonts(context)) 
    }

}
