package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
