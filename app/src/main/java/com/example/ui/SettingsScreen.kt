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

            val fontPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    val result = FontUtils.importFontFromUri(context, uri)
                    if (result.isSuccess) {
                        val newFont = result.getOrThrow()
                        customFonts.value = FontUtils.listCustomFonts(context)
                        viewModel.setReaderFontFamily("custom:${newFont.fileName}")
                expandedFontMenu = false
                    }
                }
            }

            val fontOptions = remember(customFonts.value) {
                val base = listOf(
                    "Default" to "پیش‌فرض سیستمی (Default)",
                    "SansSerif" to "سنس‌سریف مدرن (Sans-Serif)",
                    "Serif" to "سریف کلاسیک (Serif)",
                    "Monospace" to "مونو‌اسپیس (Monospace)",
                    "Cursive" to "دست‌نویس / فانتزی (Cursive)"
                )
                val custom = customFonts.value.map { "custom:${it.fileName}" to "فونت وارد شده: ${it.name}" }
                base + custom
            }

            val weightOptions = listOf(
                "Normal" to "عادی (Normal - 400)",
                "Medium" to "متوسط (Medium - 500)",
                "SemiBold" to "نیمه‌ضخیم (SemiBold - 600)",
                "Bold" to "ضخیم (Bold - 700)",
                "ExtraBold" to "بسیار ضخیم (ExtraBold - 800)"
            )

            val presetTextColors = listOf("#2D3A3A", "#000000", "#FFFFFF", "#1E293B", "#14532D", "#7C2D12", "#1E3A8A")
            val presetBgColors = listOf("#E8D8C8", "#F5EBE0", "#FFFFFF", "#1E1E1E", "#FEF3C7", "#E0F2FE", "#D1FAE5")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FormatPaint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "تنظیمات صفحه ریدر زیرنویس",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = {
                                viewModel.resetReaderStyleToDefault()
                            }
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("بازنشانی پیش‌فرض", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Text(
                        text = "تنظیم رنگ متن، رنگ پس‌زمینه و فونت صفحه اختصاصی نمایش زیرنویس در Reviewer Studio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // --- Live Preview Box ---
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "پیش‌نمایش زنده (Live Preview):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            ReviewerSubtitleReaderCard(
                                subtitleText = "نمونه نمایش متن زیرنویس در کادر ریدر\nSample Subtitle Text Preview",
                                textColorHex = readerTextColor,
                                bgColorHex = readerBgColor,
                                fontFamilyName = readerFontFamily,
                                fontWeightName = readerFontWeight,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    // --- Text Color Configuration ---
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "رنگ متن (Text Color):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(parseColorSafe(readerTextColor, Color(0xFF2D3A3A)))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = textColorInput,
                                onValueChange = {
                                    textColorInput = it
                                    if (it.startsWith("#") && (it.length == 7 || it.length == 9 || it.length == 4)) {
                                        viewModel.setReaderTextColor(it)
                                    }
                                },
                                label = { Text("کد رنگ هگز (مثلا #2D3A3A)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.setReaderTextColor(textColorInput) }
                            ) {
                                Text("ثبت")
                            }
                        }

                        // Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("پالت آماده:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            presetTextColors.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(parseColorSafe(hex, Color.Black))
                                        .border(
                                            width = if (readerTextColor.equals(hex, ignoreCase = true)) 2.5.dp else 1.dp,
                                            color = if (readerTextColor.equals(hex, ignoreCase = true)) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            textColorInput = hex
                                            viewModel.setReaderTextColor(hex)
                                        }
                                )
                            }
                        }
                    }

                    // --- Background Color Configuration ---
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "رنگ پس‌زمینه (Background Color):",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(parseColorSafe(readerBgColor, Color(0xFFE8D8C8)))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = bgColorInput,
                                onValueChange = {
                                    bgColorInput = it
                                    if (it.startsWith("#") && (it.length == 7 || it.length == 9 || it.length == 4)) {
                                        viewModel.setReaderBgColor(it)
                                    }
                                },
                                label = { Text("کد رنگ هگز (مثلا #E8D8C8)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.setReaderBgColor(bgColorInput) }
                            ) {
                                Text("ثبت")
                            }
                        }

                        // Presets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("پالت آماده:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            presetBgColors.forEach { hex ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(parseColorSafe(hex, Color.White))
                                        .border(
                                            width = if (readerBgColor.equals(hex, ignoreCase = true)) 2.5.dp else 1.dp,
                                            color = if (readerBgColor.equals(hex, ignoreCase = true)) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            bgColorInput = hex
                                            viewModel.setReaderBgColor(hex)
                                        }
                                )
                            }
                        }
                    }

                    // --- Font Family Selection ---
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "فونت متن (Font Family):",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        ExposedDropdownMenuBox(
                            expanded = expandedFontMenu,
                            onExpandedChange = { expandedFontMenu = it }
                        ) {
                            val currentFontLabel = fontOptions.find { it.first.equals(readerFontFamily, ignoreCase = true) }?.second
                                ?: if (readerFontFamily.startsWith("custom:")) {
                                    val fileName = readerFontFamily.removePrefix("custom:")
                                    "فونت: $fileName"
                                } else {
                                    readerFontFamily
                                }
                            OutlinedTextField(
                                value = currentFontLabel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFontMenu) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedFontMenu,
                                onDismissRequest = { expandedFontMenu = false }
                            ) {
                                fontOptions.forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                fontFamily = resolveFontFamily(key, context)
                                            )
                                        },
                                        onClick = {
                                            viewModel.setReaderFontFamily(key)
                                            expandedFontMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = { fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream")) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Font")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("افزودن فایل فونت جدید (TTF / OTF)", style = MaterialTheme.typography.labelLarge)
                        }

                        if (customFonts.value.isNotEmpty()) {
                            Text(
                                text = "فونت‌های وارد شده:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            customFonts.value.forEach { fontInfo ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = fontInfo.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = resolveFontFamily("custom:${fontInfo.fileName}", context),
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                if (readerFontFamily == "custom:${fontInfo.fileName}") {
                                                    viewModel.setReaderFontFamily("Default")
                                                }
                                                FontUtils.deleteCustomFont(context, fontInfo.fileName)
                                                customFonts.value = FontUtils.listCustomFonts(context)
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete Font",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- Font Weight Selection ---
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ضخامت قلم (Font Weight):",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        ExposedDropdownMenuBox(
                            expanded = expandedWeightMenu,
                            onExpandedChange = { expandedWeightMenu = it }
                        ) {
                            val currentWeightLabel = weightOptions.find { it.first.equals(readerFontWeight, ignoreCase = true) }?.second
                                ?: readerFontWeight
                            OutlinedTextField(
                                value = currentWeightLabel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedWeightMenu) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedWeightMenu,
                                onDismissRequest = { expandedWeightMenu = false }
                            ) {
                                weightOptions.forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = label,
                                                fontWeight = resolveFontWeight(key)
                                            )
                                        },
                                        onClick = {
                                            viewModel.setReaderFontWeight(key)
                                            expandedWeightMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No Gemini credentials registered yet. Please configure one above.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(configs) { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
