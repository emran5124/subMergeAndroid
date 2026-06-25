package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.utils.SafHelper
import com.example.utils.SrtParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class SubtitleRepository(private val context: Context, private val database: AppDatabase) {
    private val TAG = "SubtitleRepository"

    val apiKeyConfigsFlow: Flow<List<ApiKeyConfig>> = database.apiKeyConfigDao().getAllConfigsFlow()
    val projectsFlow: Flow<List<ProjectState>> = database.projectStateDao().getAllProjectsFlow()

    fun getSettingFlow(key: String): Flow<String?> = database.generalSettingDao().getSettingFlow(key)

    suspend fun getSettingValue(key: String, defaultValue: String): String {
        return database.generalSettingDao().getSetting(key)?.value ?: defaultValue
    }

    suspend fun saveSetting(key: String, value: String) {
        database.generalSettingDao().insertSetting(GeneralSetting(key, value))
    }

    suspend fun saveApiKeyConfig(config: ApiKeyConfig) {
        if (config.id == 0L) {
            database.apiKeyConfigDao().insertConfig(config)
        } else {
            database.apiKeyConfigDao().updateConfig(config)
        }
    }

    suspend fun deleteApiKeyConfig(config: ApiKeyConfig) {
        database.apiKeyConfigDao().deleteConfig(config)
    }

    suspend fun insertProject(project: ProjectState) {
        database.projectStateDao().insertProject(project)
    }

    suspend fun deleteProject(folderUri: String) {
        database.projectStateDao().deleteProject(folderUri)
        database.srtLineStateDao().deleteLineStatesForProject(folderUri)
    }

    suspend fun clearSrtLineStates(folderUri: String) {
        database.srtLineStateDao().deleteLineStatesForProject(folderUri)
    }

    suspend fun getProjectByUri(folderUri: String): ProjectState? {
        return database.projectStateDao().getProjectByUri(folderUri)
    }

    suspend fun updateProjectState(project: ProjectState) {
        database.projectStateDao().updateProject(project)
    }

    // Secondary model for review lines
    data class AlternativeTranslation(
        val fileName: String,
        val text: String
    )

    data class CombinedSrtLine(
        val index: Int,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val nativeText: String,
        val selectedTranslationFileName: String?,
        val selectedTranslationText: String?,
        val alternatives: List<AlternativeTranslation>
    )

    data class ProjectMetadata(
        val mediaFileUri: Uri?,
        val mediaFileName: String?,
        val mainSrtUri: Uri?,
        val translationFiles: List<SafHelper.SafFileItem>
    )

    /**
     * Resolves the media (video/audio) files, the main.srt, and translation SRTs in the project folder.
     */
    fun resolveProjectMetadata(folderTreeUri: String, childDocId: String): ProjectMetadata {
        val treeUri = Uri.parse(folderTreeUri)
        val files = SafHelper.listGrandChildren(context, treeUri, childDocId)

        var mediaUri: Uri? = null
        var mediaName: String? = null
        var mainSrtUri: Uri? = null
        val trFiles = mutableListOf<SafHelper.SafFileItem>()

        val mediaExtensions = setOf("mp4", "mkv", "mp3", "ogg", "wav")

        for (file in files) {
            val lowercaseName = file.name.lowercase()
            val ext = lowercaseName.substringAfterLast('.', "")

            if (ext in mediaExtensions) {
                mediaUri = file.uri
                mediaName = file.name
            } else if (lowercaseName == "main.srt") {
                mainSrtUri = file.uri
            } else if (ext == "srt") {
                trFiles.add(file)
            }
        }

        return ProjectMetadata(mediaUri, mediaName, mainSrtUri, trFiles)
    }

    /**
     * Reads, parses and merges main.srt and translations inside a project with Room caching.
     */
    suspend fun loadAndMergeProjectLines(
        folderTreeUri: String,
        childDocId: String,
        projectMetadata: ProjectMetadata
    ): List<CombinedSrtLine> {
        val mainSrtUri = projectMetadata.mainSrtUri
        if (mainSrtUri == null) {
            Log.w(TAG, "No main.srt found in project folder.")
            return emptyList()
        }

        val projectUniqueKey = "${folderTreeUri}_$childDocId"

        // 1. Parse main.srt
        val mainSrtContent = SafHelper.readTextFromUri(context, mainSrtUri)
        val mainSrtLines = SrtParser.parse(mainSrtContent)

        // 2. Parse translation files
        // A map of translationFileName -> parsedSrtLineList
        val trMap = mutableMapOf<String, List<SrtParser.SrtLine>>()
        for (trFile in projectMetadata.translationFiles) {
            val content = SafHelper.readTextFromUri(context, trFile.uri)
            val trLines = SrtParser.parse(content)
            trMap[trFile.name] = trLines
        }

        // 3. Load DB changes
        val dbLinesMap = database.srtLineStateDao().getLineStatesForProject(projectUniqueKey)
            .associateBy { it.lineIndex }

        // 4. Merge
        val combined = mutableListOf<CombinedSrtLine>()
        val initialDbInserts = mutableListOf<SrtLineState>()

        for (line in mainSrtLines) {
            val cached = dbLinesMap[line.index]

            val finalStartTime = cached?.startTimeMs ?: line.startTimeMs
            val finalEndTime = cached?.endTimeMs ?: line.endTimeMs
            val finalNativeText = cached?.editedNativeText ?: line.text

            // Build Alternatives (matching line index)
            val alternatives = mutableListOf<AlternativeTranslation>()
            for ((fileName, fileLines) in trMap) {
                // Find matching index line in translation file
                val trLine = fileLines.firstOrNull { it.index == line.index }
                if (trLine != null) {
                    alternatives.add(AlternativeTranslation(fileName, trLine.text))
                }
            }

            val finalSelFile = cached?.selectedTranslationFileName
            val finalSelText = cached?.editedTranslationText

            combined.add(
                CombinedSrtLine(
                    index = line.index,
                    startTimeMs = finalStartTime,
                    endTimeMs = finalEndTime,
                    nativeText = finalNativeText,
                    selectedTranslationFileName = finalSelFile,
                    selectedTranslationText = finalSelText,
                    alternatives = alternatives
                )
            )
        }

        return combined
    }

    /**
     * Saves changes of a line in Room and updates the actual physical main.srt file.
     */
    suspend fun saveSrtLineChange(
        folderTreeUri: String,
        childDocId: String,
        lineIndex: Int,
        startTimeMs: Long,
        endTimeMs: Long,
        editedNativeText: String?,
        selectedTranslationFile: String?,
        editedTranslationText: String?,
        mainSrtUri: Uri?
    ) {
        val projectUniqueKey = "${folderTreeUri}_$childDocId"
        val dbId = "${projectUniqueKey}_${lineIndex}"
        val state = SrtLineState(
            id = dbId,
            folderUri = projectUniqueKey,
            lineIndex = lineIndex,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            editedNativeText = editedNativeText,
            selectedTranslationFileName = selectedTranslationFile,
            editedTranslationText = editedTranslationText
        )
        database.srtLineStateDao().insertLineState(state)

        // Write directly/dynamically to main.srt if available
        if (mainSrtUri != null) {
            updatePhysicalMainSrt(mainSrtUri, lineIndex, startTimeMs, endTimeMs, editedNativeText)
        }
    }

    /**
     * Helper to load main.srt, modify the specific line index's timing and native text in-place, and re-write back.
     */
    private fun updatePhysicalMainSrt(
        mainSrtUri: Uri,
        lineIndex: Int,
        startTimeMs: Long,
        endTimeMs: Long,
        editedNativeText: String?
    ) {
        val content = SafHelper.readTextFromUri(context, mainSrtUri)
        val srtLines = SrtParser.parse(content)

        val updatedLines = srtLines.map { line ->
            if (line.index == lineIndex) {
                SrtParser.SrtLine(
                    index = line.index,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    text = editedNativeText ?: line.text
                )
            } else {
                line
            }
        }
        val updatedSrtContent = SrtParser.buildSrt(updatedLines)
        SafHelper.writeTextToUri(context, mainSrtUri, updatedSrtContent)
    }

    /**
     * Combines selections and generates the output SRT file named:
     * output_YYYY-MM-DD-HH-mm-ss.srt inside the project folder.
     */
    suspend fun exportProjectOutputSrt(
        folderTreeUri: String,
        childDocId: String,
        projectMetadata: ProjectMetadata,
        combinedLines: List<CombinedSrtLine>
    ): String? {
        val formatLines = combinedLines.map { line ->
            SrtParser.SrtLine(
                index = line.index,
                startTimeMs = line.startTimeMs,
                endTimeMs = line.endTimeMs,
                text = line.selectedTranslationText ?: line.nativeText // Fallback to native text if no translation chosen
            )
        }

        val srtContent = SrtParser.buildSrt(formatLines)

        // Format Date
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", java.util.Locale.US)
        val dateString = sdf.format(java.util.Date())
        val fileName = "output_$dateString.srt"

        val treeUri = Uri.parse(folderTreeUri)
        val fileUri = SafHelper.createFile(context, treeUri, childDocId, fileName, "application/x-subrip")
        if (fileUri != null) {
            val writeSuccess = SafHelper.writeTextToUri(context, fileUri, srtContent)
            if (writeSuccess) {
                return fileName
            }
        }
        return null
    }

    /**
     * Saves direct text helper to save raw downloaded and refined YouTube transcripts
     */
    fun saveYoutubeSrtToDownloads(fileName: String, content: String): Boolean {
        return try {
            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val ytSubsDir = java.io.File(downloadDir, "yt-subs")
            if (!ytSubsDir.exists()) {
                ytSubsDir.mkdirs()
            }
            val destinationFile = java.io.File(ytSubsDir, fileName)
            destinationFile.writeText(content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving refined youtube subtitles", e)
            false
        }
    }

    val tapSessionsFlow: Flow<List<TapSession>> = database.tapSessionDao().getAllSessionsFlow()

    suspend fun saveTapSession(session: TapSession) {
        database.tapSessionDao().insertSession(session)
    }

    suspend fun getTapSessionByUri(mediaUri: String): TapSession? {
        return database.tapSessionDao().getSessionByUri(mediaUri)
    }

    suspend fun deleteTapSession(mediaUri: String) {
        database.tapSessionDao().deleteSessionByUri(mediaUri)
    }
}
