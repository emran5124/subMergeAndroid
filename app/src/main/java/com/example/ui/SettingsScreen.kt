package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ApiKeyConfig
import com.example.ui.theme.Radii
import com.example.ui.theme.Spacing

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

    var configToDelete by remember { mutableStateOf<ApiKeyConfig?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        item {
            Text(
                text = "Gemini API Management",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = Radii.lg,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
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
                        leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
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
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Register Configuration")
                    }
                }
            }
        }

        item {
            Text(
                text = "General Preferences",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            val showVideoPlayer by viewModel.showVideoPlayer.collectAsState()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setShowVideoPlayer(!showVideoPlayer) },
                shape = Radii.md,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.lg).fillMaxWidth(),
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
                    Switch(
                        checked = showVideoPlayer,
                        onCheckedChange = { viewModel.setShowVideoPlayer(it) },
                        modifier = Modifier.testTag("show_video_player_switch")
                    )
                }
            }
        }

        item {
            val precisePlaybackStop by viewModel.precisePlaybackStop.collectAsState()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setPrecisePlaybackStop(!precisePlaybackStop) },
                shape = Radii.md,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.lg).fillMaxWidth(),
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
            ReaderStyleSettings(viewModel = viewModel)
        }

        item {
            Text(
                text = "Configured API Keys (Ordered by Priority):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (configs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Gemini credentials registered yet. Please configure one above.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            itemsIndexed(configs) { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = Radii.md,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "#${index + 1} ${if (item.description.isNotBlank()) item.description else "Key ID: ${item.id}"}",
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

                        IconButton(onClick = { configToDelete = item }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    configToDelete?.let { config ->
        ConfirmDialog(
            title = "Delete API configuration?",
            message = "This will permanently remove \"${if (config.description.isNotBlank()) config.description else config.modelName}\" from your registered keys.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.deleteApiKeyConfig(config) },
            onDismiss = { configToDelete = null }
        )
    }
}
