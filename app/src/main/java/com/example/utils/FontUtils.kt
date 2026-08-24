package com.example.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import java.io.FileOutputStream

object FontUtils {
    private const val TAG = "FontUtils"
    private val fontCache = mutableMapOf<String, FontFamily>()

    data class CustomFontInfo(
        val name: String,
        val fileName: String,
        val filePath: String,
        val sizeBytes: Long
    )

    fun getFontsDir(context: Context): File {
        val dir = File(context.filesDir, "custom_fonts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun listCustomFonts(context: Context): List<CustomFontInfo> {
        val dir = getFontsDir(context)
        val files = dir.listFiles { file ->
            val ext = file.extension.lowercase()
            ext == "ttf" || ext == "otf" || ext == "ttc"
        } ?: return emptyList()

        return files.map { file ->
            val displayName = file.nameWithoutExtension.replace('_', ' ')
            CustomFontInfo(
                name = displayName,
                fileName = file.name,
                filePath = file.absolutePath,
                sizeBytes = file.length()
            )
        }.sortedBy { it.name }
    }

    fun importFontFromUri(context: Context, uri: Uri): Result<CustomFontInfo> {
        return runCatching {
            var originalName: String? = null
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            originalName = cursor.getString(nameIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not resolve file name from cursor", e)
            }

            if (originalName.isNullOrBlank()) {
                originalName = uri.lastPathSegment ?: "custom_font.ttf"
            }

            // Ensure proper font extension
            var safeName = originalName!!.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            if (!safeName.endsWith(".ttf", ignoreCase = true) && !safeName.endsWith(".otf", ignoreCase = true)) {
                safeName = "$safeName.ttf"
            }

            val fontsDir = getFontsDir(context)
            val destFile = File(fontsDir, safeName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            } ?: throw IllegalStateException("Could not open input stream for URI: $uri")

            // Clear cached font for this key
            fontCache.remove(safeName)
            fontCache.remove(destFile.absolutePath)

            val displayName = destFile.nameWithoutExtension.replace('_', ' ')
            CustomFontInfo(
                name = displayName,
                fileName = destFile.name,
                filePath = destFile.absolutePath,
                sizeBytes = destFile.length()
            )
        }
    }

    fun deleteCustomFont(context: Context, fileName: String): Boolean {
        return try {
            val file = File(getFontsDir(context), fileName)
            fontCache.remove(fileName)
            fontCache.remove(file.absolutePath)
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete font $fileName", e)
            false
        }
    }

    fun resolveFontFamily(familyStr: String, context: Context? = null): FontFamily {
        val clean = familyStr.trim()
        when (clean.lowercase()) {
            "sans-serif", "sansserif" -> return FontFamily.SansSerif
            "serif" -> return FontFamily.Serif
            "monospace" -> return FontFamily.Monospace
            "cursive" -> return FontFamily.Cursive
            "default", "" -> return FontFamily.Default
        }

        // Custom font file resolution
        if (context != null) {
            val key = clean
            if (fontCache.containsKey(key)) {
                return fontCache[key] ?: FontFamily.Default
            }

            try {
                val fontFile = when {
                    clean.startsWith("custom:") -> {
                        val fileName = clean.removePrefix("custom:")
                        File(getFontsDir(context), fileName)
                    }
                    clean.startsWith("/") -> File(clean)
                    else -> File(getFontsDir(context), clean)
                }

                if (fontFile.exists() && fontFile.canRead()) {
                    val font = Font(file = fontFile, weight = FontWeight.Normal, style = FontStyle.Normal)
                    val family = FontFamily(font)
                    fontCache[key] = family
                    return family
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load custom font: $clean", e)
            }
        }

        return FontFamily.Default
    }
}
