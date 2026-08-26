package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Segment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Radii
import com.example.ui.theme.Spacing

@Composable
fun AiSubtitleScreen(
    viewModel: SubtitleStudioViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Option 1 (AI) state needed for launchers & BackHandler
    val aiAudioUri by viewModel.aiAudioFileUri.collectAsState()

    // Option 2 (Tap to Sync) state needed for BackHandler
    val studioOption by viewModel.studioOptionSetting.collectAsState()
    val tapAudioUri by viewModel.tapAudioFileUri.collectAsState()

    androidx.activity.compose.BackHandler(enabled = (studioOption == 1 && aiAudioUri != null) || (studioOption == 2 && tapAudioUri != null)) {
        if (studioOption == 1) {
            viewModel.clearAiSelectedAudio()
        } else {
            viewModel.clearTapSelectedAudio()
        }
    }

    fun queryDisplayName(uri: Uri, fallback: String): String {
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) cursor.getString(nameIndex) else fallback
                    } else fallback
                } ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryDisplayName(uri, "Selected File")
            val mimeType = context.contentResolver.getType(uri) ?: "audio/mp3"
            viewModel.setAiSelectedAudio(uri, name, mimeType)
        }
    }

    val tapFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryDisplayName(uri, "Selected File")
            val mimeType = context.contentResolver.getType(uri) ?: "audio/mp3"
            viewModel.setTapSelectedAudio(uri, name, mimeType)
        }
    }

    val txtFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = queryDisplayName(uri, "Selected Text")
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
        contract = ActivityResultContracts.CreateDocument("*/*")
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
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        // Mode selector tabs
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            SegmentedButton(
                selected = studioOption == 1,
                onClick = { viewModel.setStudioOption(1) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {},
                label = {
                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                    Text("AI Gemini", style = MaterialTheme.typography.labelLarge)
                }
            )
            SegmentedButton(
                selected = studioOption == 2,
                onClick = { viewModel.setStudioOption(2) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {},
                label = {
                    Icon(Icons.Filled.Segment, contentDescription = null, modifier = Modifier.padding(end = Spacing.xs))
                    Text("Tap-to-Sync", style = MaterialTheme.typography.labelLarge)
                }
            )
        }

        if (studioOption == 1) {
            AiTranscriptionMode(
                viewModel = viewModel,
                onPickFile = { fileLauncher.launch(arrayOf("audio/*", "video/*")) },
                modifier = Modifier.weight(1f)
            )
        } else {
            TapSyncMode(
                viewModel = viewModel,
                onPickFile = { tapFileLauncher.launch(arrayOf("audio/*", "video/*")) },
                onPickTxt = { txtFileLauncher.launch(arrayOf("text/plain")) },
                onImportSrt = { srtFileLauncher.launch(arrayOf("*/*")) },
                onSaveSrt = { defaultFileName -> saveSrtLauncher.launch(defaultFileName) },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showSrtImportDialog && pendingSrtUri != null) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    showSrtImportDialog = false
                    pendingSrtUri = null
                },
                title = {
                    Text(
                        text = "ورود فایل SRT (Import SRT)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "لطفا یکی از حالت‌های زیر را برای وارد کردن فایل زیرنویس انتخاب کنید:",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Button(
                            onClick = {
                                val uri = pendingSrtUri
                                if (uri != null) {
                                    viewModel.importSrtToTapLines(uri, mode = 1) { _, message ->
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Button(
                            onClick = {
                                val uri = pendingSrtUri
                                if (uri != null) {
                                    viewModel.importSrtToTapLines(uri, mode = 2) { _, message ->
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
