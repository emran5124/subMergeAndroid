package com.example.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.utils.FontUtils
import com.example.ui.theme.Radii
import com.example.ui.theme.Spacing

private fun isValidHex(input: String): Boolean {
    val clean = input.removePrefix("#")
    return input.startsWith("#") && clean.length in listOf(3, 6, 8) &&
        clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderStyleSettings(
    viewModel: SubtitleStudioViewModel,
    modifier: Modifier = Modifier
) {
    val readerTextColor by viewModel.readerTextColor.collectAsState()
    val readerBgColor by viewModel.readerBgColor.collectAsState()
    val readerFontFamily by viewModel.readerFontFamily.collectAsState()
    val readerFontWeight by viewModel.readerFontWeight.collectAsState()

    var textColorInput by remember(readerTextColor) { mutableStateOf(readerTextColor) }
    var bgColorInput by remember(readerBgColor) { mutableStateOf(readerBgColor) }

    var expandedFontMenu by remember { mutableStateOf(false) }
    var expandedWeightMenu by remember { mutableStateOf(false) }
    var fontToDelete by remember { mutableStateOf<FontUtils.CustomFontInfo?>(null) }

    val context = LocalContext.current
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
                Toast.makeText(context, "Font imported: ${newFont.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to import font file", Toast.LENGTH_SHORT).show()
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
        modifier = modifier.fillMaxWidth(),
        shape = Radii.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
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
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("بازنشانی پیش‌فرض", style = MaterialTheme.typography.labelMedium)
                }
            }

            Text(
                text = "تنظیم رنگ متن، رنگ پس‌زمینه و فونت صفحه اختصاصی نمایش زیرنویس در Reviewer Studio",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // --- Live Preview Box ---
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = "پیش‌نمایش زنده (Live Preview):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(Radii.md)
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
            ColorConfigSection(
                label = "رنگ متن (Text Color):",
                currentHex = readerTextColor,
                inputValue = textColorInput,
                presets = presetTextColors,
                onInputChange = {
                    textColorInput = it
                    if (isValidHex(it)) viewModel.setReaderTextColor(it)
                },
                onSelectPreset = { hex ->
                    textColorInput = hex
                    viewModel.setReaderTextColor(hex)
                },
                hexLabel = "کد رنگ هگز (مثلا #2D3A3A)"
            )

            // --- Background Color Configuration ---
            ColorConfigSection(
                label = "رنگ پس‌زمینه (Background Color):",
                currentHex = readerBgColor,
                inputValue = bgColorInput,
                presets = presetBgColors,
                onInputChange = {
                    bgColorInput = it
                    if (isValidHex(it)) viewModel.setReaderBgColor(it)
                },
                onSelectPreset = { hex ->
                    bgColorInput = hex
                    viewModel.setReaderBgColor(hex)
                },
                hexLabel = "کد رنگ هگز (مثلا #E8D8C8)"
            )

            // --- Font Family Selection ---
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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

                Spacer(modifier = Modifier.height(Spacing.xs))
                Button(
                    onClick = { fontPickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-otf", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("افزودن فایل فونت جدید (TTF / OTF)", style = MaterialTheme.typography.labelLarge)
                }

                if (customFonts.value.isNotEmpty()) {
                    Text(
                        text = "فونت‌های وارد شده:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                    customFonts.value.forEach { fontInfo ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = fontInfo.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = resolveFontFamily("custom:${fontInfo.fileName}", context),
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { fontToDelete = fontInfo },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(Spacing.xs))
                                    Text("حذف", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // --- Font Weight Selection ---
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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

    fontToDelete?.let { font ->
        ConfirmDialog(
            title = "حذف فونت",
            message = "آیا از حذف \"${font.name}\" مطمئن هستید؟ این عمل قابل بازگشت نیست.",
            confirmLabel = "حذف",
            destructive = true,
            onConfirm = {
                if (viewModel.readerFontFamily.value == "custom:${font.fileName}") {
                    viewModel.setReaderFontFamily("Default")
                }
                FontUtils.deleteCustomFont(context, font.fileName)
                customFonts.value = FontUtils.listCustomFonts(context)
            },
            onDismiss = { fontToDelete = null }
        )
    }
}

@Composable
private fun ColorConfigSection(
    label: String,
    currentHex: String,
    inputValue: String,
    presets: List<String>,
    onInputChange: (String) -> Unit,
    onSelectPreset: (String) -> Unit,
    hexLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(parseColorSafe(currentHex, Color.Gray))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        }

        val inputInvalid = inputValue.isNotBlank() && !isValidHex(inputValue)
        Column {
            OutlinedTextField(
                value = inputValue,
                onValueChange = onInputChange,
                label = { Text(hexLabel) },
                singleLine = true,
                isError = inputInvalid,
                supportingText = if (inputInvalid) {
                    { Text("Invalid hex format. Use #RRGGBB") }
                } else null,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "پالت آماده:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            presets.forEach { hex ->
                val isSelected = currentHex.equals(hex, ignoreCase = true)
                Surface(
                    onClick = { onSelectPreset(hex) },
                    shape = CircleShape,
                    color = parseColorSafe(hex, Color.Black),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.size(32.dp)
                ) {}
            }
        }
    }
}
