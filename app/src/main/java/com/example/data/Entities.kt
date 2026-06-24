package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_key_configs")
data class ApiKeyConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apiKey: String,
    val modelName: String = "gemini-3.1-flash-lite",
    val priority: Int = 0,
    val description: String = ""
)

@Entity(tableName = "project_states")
data class ProjectState(
    @PrimaryKey val folderUri: String, // Room ID is folder URI
    val projectName: String,
    val lastActiveLineIndex: Int = 0,
    val selectedTranslationSrtFileName: String = "", // e.g. "persian.srt"
    val isAutoPlayEnabled: Boolean = true,
    val lastModifiedTimeMs: Long = 0
)

@Entity(tableName = "srt_line_states")
data class SrtLineState(
    @PrimaryKey val id: String, // Combine: folderUri + "_" + lineIndex
    val folderUri: String,
    val lineIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val editedNativeText: String?, // Overrides main.srt native text in memory if not null
    val selectedTranslationFileName: String?, // name of translated file, or "custom"
    val editedTranslationText: String? // the translation text chosen or typed
)

@Entity(tableName = "general_settings")
data class GeneralSetting(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(tableName = "tap_sessions")
data class TapSession(
    @PrimaryKey val mediaUri: String, // Media file URI as key
    val mediaName: String,
    val mediaMimeType: String,
    val txtUri: String = "",
    val txtName: String = "",
    val srtLinesJson: String = "",
    val lastActiveTimeMs: Long = 0
)
