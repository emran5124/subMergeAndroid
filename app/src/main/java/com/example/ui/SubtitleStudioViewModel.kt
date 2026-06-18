package com.example.ui

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.GeminiApiClient
import com.example.network.YoutubeScraper
import com.example.utils.SafHelper
import com.example.utils.SrtParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class SubtitleStudioViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SubtitleStudioViewModel"
    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    val repository = SubtitleRepository(context, database)

    // Flow exports
    val apiKeyConfigs: StateFlow<List<ApiKeyConfig>> = repository.apiKeyConfigsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projectsList: StateFlow<List<ProjectState>> = repository.projectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Language setting "ar" as default
    private val _preferredLanguage = MutableStateFlow("ar")
    val preferredLanguage: StateFlow<String> = _preferredLanguage.asStateFlow()

    // Active project state
    private val _activeProjectFolderUri = MutableStateFlow<String?>(null)
    val activeProjectFolderUri: StateFlow<String?> = _activeProjectFolderUri.asStateFlow()

    // Sublists inside active folders represents actual folders
    private val _selectedProjectSubFolderId = MutableStateFlow<String?>(null)
    val selectedProjectSubFolderId: StateFlow<String?> = _selectedProjectSubFolderId.asStateFlow()

    private val _selectedProjectSubFolderName = MutableStateFlow<String?>(null)
    val selectedProjectSubFolderName: StateFlow<String?> = _selectedProjectSubFolderName.asStateFlow()

    data class SubDirectoryProject(val name: String, val documentId: String)
    private val _availableSubProjects = MutableStateFlow<List<SubDirectoryProject>>(emptyList())
    val availableSubProjects: StateFlow<List<SubDirectoryProject>> = _availableSubProjects.asStateFlow()

    // Combined lines & indices flow
    private val _srtLines = MutableStateFlow<List<SubtitleRepository.CombinedSrtLine>>(emptyList())
    val srtLines: StateFlow<List<SubtitleRepository.CombinedSrtLine>> = _srtLines.asStateFlow()

    private val _activeLineIndex = MutableStateFlow(0)
    val activeLineIndex: StateFlow<Int> = _activeLineIndex.asStateFlow()

    private val _projectMetadata = MutableStateFlow<SubtitleRepository.ProjectMetadata?>(null)
    val projectMetadata: StateFlow<SubtitleRepository.ProjectMetadata?> = _projectMetadata.asStateFlow()

    // Youtube extractor states
    private val _youtubeUrlInput = MutableStateFlow("")
    val youtubeUrlInput: StateFlow<String> = _youtubeUrlInput.asStateFlow()

    private val _youtubeExtractionState = MutableStateFlow<GeminiApiClient.CallStepState>(GeminiApiClient.CallStepState.Idle)
    val youtubeExtractionState: StateFlow<GeminiApiClient.CallStepState> = _youtubeExtractionState.asStateFlow()

    private val _youtubeLog = MutableStateFlow("")
    val youtubeLog: StateFlow<String> = _youtubeLog.asStateFlow()

    // Media Player state
    private var mediaPlayer: MediaPlayer? = null
    private var playerTrackingJob: Job? = null

    private val _playerIsPlaying = MutableStateFlow(false)
    val playerIsPlaying: StateFlow<Boolean> = _playerIsPlaying.asStateFlow()

    private val _playerCurrentPos = MutableStateFlow(0L)
    val playerCurrentPos: StateFlow<Boolean> = _playerIsPlaying.asStateFlow() // wait, position can be Long

    private val _playerCurrentPosMs = MutableStateFlow(0L)
    val playerCurrentPosMs: StateFlow<Long> = _playerCurrentPosMs.asStateFlow()

    private val _playerDuration = MutableStateFlow(0L)
    val playerDuration: StateFlow<Long> = _playerDuration.asStateFlow()

    private val _autoPlayOnNextLine = MutableStateFlow(true)
    val autoPlayOnNextLine: StateFlow<Boolean> = _autoPlayOnNextLine.asStateFlow()

    private val _isSeeking = MutableStateFlow(false)
    val isSeeking: StateFlow<Boolean> = _isSeeking.asStateFlow()

    init {
        viewModelScope.launch {
            // Load preferred Language
            val lang = repository.getSettingValue("preferred_language", "ar")
            _preferredLanguage.value = lang

            // Load last active folder tree
            val lastFolder = repository.getSettingValue("last_folder_tree_uri", "")
            if (lastFolder.isNotEmpty()) {
                _activeProjectFolderUri.value = lastFolder
                scanTreeForSubdirs(lastFolder)
            }
        }
    }

    fun setPreferredLanguage(lang: String) {
        _preferredLanguage.value = lang
        viewModelScope.launch {
            repository.saveSetting("preferred_language", lang)
        }
    }

    fun setYoutubeUrlInput(url: String) {
        _youtubeUrlInput.value = url
    }

    fun setAutoPlay(enabled: Boolean) {
        _autoPlayOnNextLine.value = enabled
    }

    /**
     * Settings database operations
     */
    fun addApiKeyConfig(apiKey: String, model: String, description: String) {
        viewModelScope.launch {
            val list = apiKeyConfigs.value
            val priority = if (list.isEmpty()) 0 else list.maxOf { it.priority } + 1
            repository.saveApiKeyConfig(ApiKeyConfig(apiKey = apiKey, modelName = model, description = description, priority = priority))
        }
    }

    fun updateApiKeyConfig(config: ApiKeyConfig) {
        viewModelScope.launch {
            repository.saveApiKeyConfig(config)
        }
    }

    fun deleteApiKeyConfig(config: ApiKeyConfig) {
        viewModelScope.launch {
            repository.deleteApiKeyConfig(config)
        }
    }

    /**
     * Folder & SAF selection
     */
    fun selectActiveMainFolder(treeUri: String) {
        _activeProjectFolderUri.value = treeUri
        viewModelScope.launch {
            repository.saveSetting("last_folder_tree_uri", treeUri)
            scanTreeForSubdirs(treeUri)
        }
    }

    fun closeSelectedProject() {
        _selectedProjectSubFolderId.value = null
        _selectedProjectSubFolderName.value = null
        _srtLines.value = emptyList()
        _projectMetadata.value = null
        _activeLineIndex.value = 0
        stopMediaPlayer()
    }

    private fun scanTreeForSubdirs(treeUriStr: String) {
        try {
            val treeUri = Uri.parse(treeUriStr)
            val children = SafHelper.listChildren(context, treeUri)
            // Filter child folders
            val subdirs = children.filter {
                it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR
            }.map {
                SubDirectoryProject(name = it.name, documentId = it.documentId)
            }
            _availableSubProjects.value = subdirs
            
            // Clear current sub project selection
            _selectedProjectSubFolderId.value = null
            _selectedProjectSubFolderName.value = null
            _srtLines.value = emptyList()
            _projectMetadata.value = null
            stopMediaPlayer()
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning subdirectories", e)
        }
    }

    fun loadSubProject(subp: SubDirectoryProject) {
        val folderUri = _activeProjectFolderUri.value ?: return
        _selectedProjectSubFolderId.value = subp.documentId
        _selectedProjectSubFolderName.value = subp.name

        viewModelScope.launch {
            logStatus("Loading sub-project '${subp.name}'...")
            
            // Create or query existing local project state in Room
            var projectState = repository.getProjectByUri("${folderUri}_${subp.documentId}")
            if (projectState == null) {
                projectState = ProjectState(
                    folderUri = "${folderUri}_${subp.documentId}",
                    projectName = subp.name,
                    lastActiveLineIndex = 0
                )
                repository.insertProject(projectState)
            }
            _activeLineIndex.value = projectState.lastActiveLineIndex

            // Resolve files inside project folder
            val meta = repository.resolveProjectMetadata(folderUri, subp.documentId)
            _projectMetadata.value = meta

            // Initialize Player
            initializeMediaPlayer(meta.mediaFileUri)

            // Load and combined SRT lines
            val lines = repository.loadAndMergeProjectLines(folderUri, subp.documentId, meta)
            _srtLines.value = lines

            if (lines.isNotEmpty() && _activeLineIndex.value >= lines.size) {
                _activeLineIndex.value = 0
            }

            logStatus("Successfully loaded ${lines.size} subtitle lines.")
            
            // Auto play if available
            if (lines.isNotEmpty() && _autoPlayOnNextLine.value) {
                seekPlayerToLineIndex(_activeLineIndex.value)
            }
        }
    }

    /**
     * Line item updates (persistent)
     */
    fun selectActiveLineIndex(index: Int) {
        if (index < 0 || index >= _srtLines.value.size) return
        _activeLineIndex.value = index
        
        // Save to ProjectState Room Database
        val folderUri = _activeProjectFolderUri.value ?: return
        val subId = _selectedProjectSubFolderId.value ?: return
        viewModelScope.launch {
            val state = repository.getProjectByUri("${folderUri}_$subId")
            if (state != null) {
                repository.insertProject(state.copy(lastActiveLineIndex = index, lastModifiedTimeMs = java.lang.System.currentTimeMillis()))
            }
        }

        // Auto play
        if (_autoPlayOnNextLine.value) {
            seekPlayerToLineIndex(index)
        }
    }

    fun updateActiveLineTiming(startOffsetMs: Long, endOffsetMs: Long) {
        val folderUri = _activeProjectFolderUri.value ?: return
        val subId = _selectedProjectSubFolderId.value ?: return
        val index = _activeLineIndex.value
        val currentLines = _srtLines.value
        if (index < 0 || index >= currentLines.size) return

        val line = currentLines[index]
        val newStart = (line.startTimeMs + startOffsetMs).coerceAtLeast(0L)
        val newEnd = (line.endTimeMs + endOffsetMs).coerceAtLeast(newStart)

        val updated = currentLines.toMutableList()
        updated[index] = line.copy(startTimeMs = newStart, endTimeMs = newEnd)
        _srtLines.value = updated

        viewModelScope.launch {
            repository.saveSrtLineChange(
                folderTreeUri = folderUri,
                childDocId = subId,
                lineIndex = line.index,
                startTimeMs = newStart,
                endTimeMs = newEnd,
                editedNativeText = line.nativeText,
                selectedTranslationFile = line.selectedTranslationFileName,
                editedTranslationText = line.selectedTranslationText,
                mainSrtUri = _projectMetadata.value?.mainSrtUri
            )
        }
    }

    fun editActiveLineNativeText(text: String) {
        val folderUri = _activeProjectFolderUri.value ?: return
        val subId = _selectedProjectSubFolderId.value ?: return
        val index = _activeLineIndex.value
        val currentLines = _srtLines.value
        if (index < 0 || index >= currentLines.size) return

        val line = currentLines[index]
        val updated = currentLines.toMutableList()
        updated[index] = line.copy(nativeText = text)
        _srtLines.value = updated

        viewModelScope.launch {
            repository.saveSrtLineChange(
                folderTreeUri = folderUri,
                childDocId = subId,
                lineIndex = line.index,
                startTimeMs = line.startTimeMs,
                endTimeMs = line.endTimeMs,
                editedNativeText = text,
                selectedTranslationFile = line.selectedTranslationFileName,
                editedTranslationText = line.selectedTranslationText,
                mainSrtUri = _projectMetadata.value?.mainSrtUri
            )
        }
    }

    fun selectActiveLineTranslation(fileName: String, text: String) {
        val folderUri = _activeProjectFolderUri.value ?: return
        val subId = _selectedProjectSubFolderId.value ?: return
        val index = _activeLineIndex.value
        val currentLines = _srtLines.value
        if (index < 0 || index >= currentLines.size) return

        val line = currentLines[index]
        val updated = currentLines.toMutableList()
        updated[index] = line.copy(
            selectedTranslationFileName = fileName,
            selectedTranslationText = text
        )
        _srtLines.value = updated

        viewModelScope.launch {
            repository.saveSrtLineChange(
                folderTreeUri = folderUri,
                childDocId = subId,
                lineIndex = line.index,
                startTimeMs = line.startTimeMs,
                endTimeMs = line.endTimeMs,
                editedNativeText = line.nativeText,
                selectedTranslationFile = fileName,
                editedTranslationText = text,
                mainSrtUri = _projectMetadata.value?.mainSrtUri
            )
        }
    }

    fun editActiveLineTranslationText(text: String) {
        val folderUri = _activeProjectFolderUri.value ?: return
        val subId = _selectedProjectSubFolderId.value ?: return
        val index = _activeLineIndex.value
        val currentLines = _srtLines.value
        if (index < 0 || index >= currentLines.size) return

        val line = currentLines[index]
        val currentFile = line.selectedTranslationFileName ?: "custom"
        val updated = currentLines.toMutableList()
        updated[index] = line.copy(
            selectedTranslationFileName = currentFile,
            selectedTranslationText = text
        )
        _srtLines.value = updated

        viewModelScope.launch {
            repository.saveSrtLineChange(
                folderTreeUri = folderUri,
                childDocId = subId,
                lineIndex = line.index,
                startTimeMs = line.startTimeMs,
                endTimeMs = line.endTimeMs,
                editedNativeText = line.nativeText,
                selectedTranslationFile = currentFile,
                editedTranslationText = text,
                mainSrtUri = _projectMetadata.value?.mainSrtUri
            )
        }
    }

    /**
     * Document Generation/Export
     */
    fun exportOutputSrt(onFinished: (String?) -> Unit) {
        val folderUri = _activeProjectFolderUri.value ?: return onFinished(null)
        val subId = _selectedProjectSubFolderId.value ?: return onFinished(null)
        val meta = _projectMetadata.value ?: return onFinished(null)
        val lines = _srtLines.value

        viewModelScope.launch {
            val fileName = repository.exportProjectOutputSrt(folderUri, subId, meta, lines)
            onFinished(fileName)
        }
    }

    /**
     * Media Player section
     */
    private fun initializeMediaPlayer(uri: Uri?) {
        stopMediaPlayer()
        if (uri == null) {
            _playerDuration.value = 0L
            return
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                _playerDuration.value = duration.toLong()
            }
            _playerIsPlaying.value = false
            _playerCurrentPosMs.value = 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize media player for URI $uri", e)
        }
    }

    fun togglePlayback() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _playerIsPlaying.value = false
            stopPlayerTracking()
        } else {
            player.start()
            _playerIsPlaying.value = true
            startPlayerTracking()
        }
    }

    fun playCurrentLineSegment() {
        val player = mediaPlayer ?: return
        val index = _activeLineIndex.value
        val lines = _srtLines.value
        if (index < 0 || index >= lines.size) return

        val line = lines[index]
        _isSeeking.value = true
        _playerIsPlaying.value = false
        stopPlayerTracking()

        player.setOnSeekCompleteListener {
            _isSeeking.value = false
            _playerCurrentPosMs.value = line.startTimeMs
            if (!player.isPlaying) {
                player.start()
            }
            _playerIsPlaying.value = true
            startPlayerTracking(stopTimeMs = line.endTimeMs, initialPosOverride = line.startTimeMs)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            player.seekTo(line.startTimeMs, MediaPlayer.SEEK_CLOSEST)
        } else {
            player.seekTo(line.startTimeMs.toInt())
        }
    }

    private fun seekPlayerToLineIndex(index: Int) {
        val player = mediaPlayer ?: return
        val lines = _srtLines.value
        if (index < 0 || index >= lines.size) return

        val line = lines[index]
        _isSeeking.value = true
        _playerCurrentPosMs.value = line.startTimeMs
        stopPlayerTracking()

        player.setOnSeekCompleteListener {
            _isSeeking.value = false
            _playerCurrentPosMs.value = line.startTimeMs
            
            // If auto play is active, let's start playing this line segment
            if (_autoPlayOnNextLine.value) {
                if (!player.isPlaying) {
                    player.start()
                }
                _playerIsPlaying.value = true
                startPlayerTracking(stopTimeMs = line.endTimeMs, initialPosOverride = line.startTimeMs)
            } else {
                if (player.isPlaying) {
                    player.pause()
                }
                _playerIsPlaying.value = false
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            player.seekTo(line.startTimeMs, MediaPlayer.SEEK_CLOSEST)
        } else {
            player.seekTo(line.startTimeMs.toInt())
        }
    }

    private fun startPlayerTracking(stopTimeMs: Long? = null, initialPosOverride: Long? = null) {
        stopPlayerTracking()
        playerTrackingJob = viewModelScope.launch(Dispatchers.Main) {
            val startTime = java.lang.System.currentTimeMillis()
            val initialPlayerPos = initialPosOverride ?: mediaPlayer?.currentPosition?.toLong() ?: 0L
            
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying && !_isSeeking.value) {
                    val elapsedRealtime = java.lang.System.currentTimeMillis() - startTime
                    val estimatedPos = initialPlayerPos + elapsedRealtime
                    
                    val actualPos = player.currentPosition.toLong()
                    _playerCurrentPosMs.value = actualPos
                    
                    // Predict highly precise stop using max of estimated or native position
                    val pos = kotlin.math.max(estimatedPos, actualPos)

                    if (stopTimeMs != null && pos >= stopTimeMs) {
                        player.pause()
                        _playerIsPlaying.value = false
                        _playerCurrentPosMs.value = stopTimeMs
                        break
                    }
                } else if (player != null && _isSeeking.value) {
                    // Do nothing, wait for seek to finish
                } else {
                    _playerIsPlaying.value = false
                    break
                }
                delay(10)
            }
        }
    }

    private fun stopPlayerTracking() {
        playerTrackingJob?.cancel()
        playerTrackingJob = null
    }

    fun stopMediaPlayer() {
        stopPlayerTracking()
        mediaPlayer?.release()
        mediaPlayer = null
        _playerIsPlaying.value = false
        _playerCurrentPosMs.value = 0L
        _playerDuration.value = 0L
    }

    /**
     * YouTube extraction & AI refinement
     */
    fun startYoutubeSrv3ToSrtFlow() {
        val youtubeUrl = _youtubeUrlInput.value.trim()
        val configs = apiKeyConfigs.value
        val lang = _preferredLanguage.value

        if (youtubeUrl.isEmpty()) {
            _youtubeLog.value = "Error: Please enter a YouTube video URL."
            _youtubeExtractionState.value = GeminiApiClient.CallStepState.OutOfOptions("Missing input URL")
            return
        }

        _youtubeLog.value = "Initializing subtitle extraction sequence...\n"
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Scraping
                updateLog("Connecting to YouTube watch page...")
                val rawSrv3Xml = YoutubeScraper.fetchSrv3Subtitles(youtubeUrl, lang)
                if (rawSrv3Xml.isNullOrBlank()) {
                    updateLog("❌ Error: No subtitle configurations or auto-transcripts found for language code '$lang' on this video.")
                    _youtubeExtractionState.value = GeminiApiClient.CallStepState.OutOfOptions("No transcripts found")
                    return@launch
                }

                updateLog("✓ Successfully scraped raw SRV3 subtitles. Length: ${rawSrv3Xml.length} characters.")
                updateLog("Initiating AI refinement with Gemini client...")

                // 2. Refined AI response
                val refinedSrt = GeminiApiClient.refineSubtitles(
                    rawSrv3Xml = rawSrv3Xml,
                    preferredLanguageCode = lang,
                    apiConfigs = configs,
                    listener = object : GeminiApiClient.StatusListener {
                        override fun onStateChanged(state: GeminiApiClient.CallStepState) {
                            _youtubeExtractionState.value = state
                            handleGeminiClientState(state)
                        }
                    }
                )

                if (refinedSrt != null) {
                    // 3. Save automatically to public Downloads/yt-subs
                    val vId = YoutubeScraper.extractVideoId(youtubeUrl) ?: "download"
                    val srtFileName = "yt_sub_${vId}_$lang.srt"
                    val saveSuccess = repository.saveYoutubeSrtToDownloads(srtFileName, refinedSrt)

                    if (saveSuccess) {
                        updateLog("\n🎉 SUCCESS! Refined subtitle saved automatically to public external memory:")
                        updateLog("📁 Location: /Downloads/yt-subs/$srtFileName")
                    } else {
                        updateLog("\n⚠️ Subtitle refined successfully but folder write to public download failed. Writing to log instead.")
                        updateLog("\n--- RECORDED OUTLINE ---")
                        updateLog(refinedSrt)
                    }
                }
            } catch (e: Exception) {
                updateLog("❌ Critical error in YouTube workflow: ${e.message}")
                _youtubeExtractionState.value = GeminiApiClient.CallStepState.OutOfOptions(e.message ?: "Task exception")
            }
        }
    }

    private fun handleGeminiClientState(state: GeminiApiClient.CallStepState) {
        when (state) {
            is GeminiApiClient.CallStepState.Sending -> {
                updateLog("🔄 Querying Gemini API utilizing ${state.keyDesc} with model [${state.model}]...")
            }
            is GeminiApiClient.CallStepState.RetryingRateLimit -> {
                updateLog("⚠️ Rate Limit (429) / Host Busy: Retrying in ${state.delaySecondsLeft}s using ${state.keyDesc} (Attempt ${state.attempt}...)")
            }
            is GeminiApiClient.CallStepState.VpnBlockPrompt -> {
                updateLog("🛑 VPN CHANGE REQUIRED: Authentication/Forbidden blocked (Code 400/403). Waiting for user VPN click.")
            }
            is GeminiApiClient.CallStepState.ServerErrorOptionPrompt -> {
                updateLog("❌ Server error on current API: ${state.message}. Waiting for retry choice.")
            }
            is GeminiApiClient.CallStepState.Success -> {
                updateLog("✓ Gemini model response successfully processed and compiled.")
            }
            is GeminiApiClient.CallStepState.OutOfOptions -> {
                updateLog("❌ Failed: ${state.error}")
            }
            else -> {}
        }
    }

    private fun updateLog(message: String) {
        _youtubeLog.value = _youtubeLog.value + message + "\n"
    }

    private fun logStatus(msg: String) {
        Log.d(TAG, msg)
    }

    override fun onCleared() {
        super.onCleared()
        stopMediaPlayer()
    }
}
