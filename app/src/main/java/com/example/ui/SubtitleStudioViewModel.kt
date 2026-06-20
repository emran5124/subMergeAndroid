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

    // --- AI Audio Studio States ---
    private val defaultAiPromptMain = """
You are an expert subtitle transcriber. Listen to the provided audio file carefully, and transcribe it into a standard SRT subtitle format.

FORMAT RULES:
1. Each subtitle block must follow the standard SRT format:
[Index]
[HH:MM:SS,mmm] --> [HH:MM:SS,mmm]
[Text]

Example:
1
00:00:01,250 --> 00:00:04,800
Hello and welcome.

2. Ensure the timing is in the EXACT format 'HH:MM:SS,mmm' or 'HH:MM:SS.mmm' where mmm is milliseconds (e.g. 01:24:10,542). Note the dot or comma before the milliseconds.

ALIGNMENT RULES:
If a SOURCE TEXT is provided below, you MUST align the transcription lines EXACTLY with the SOURCE TEXT lines. 
- Each line in the SOURCE TEXT corresponds to exactly one SRT block. 
- Do NOT break a single source line across multiple SRT blocks.
- Do NOT combine multiple source lines into a single SRT block.
- Keep the words of each source line completely intact in its block. Do NOT split a word across lines (e.g., do NOT turn "text1" into "te" on one line and "xt1" on the next).
- The audio might contain extra sounds, background noise, or other spoken words before, in between, or after. You can add extra subtitle blocks for these, but the lines that correspond to the SOURCE TEXT must remain intact and aligned as individual complete lines.

SOURCE TEXT:
[sourceTextPlaceholder]

Please output ONLY the standard SRT content. Do NOT include any explanations, introduction, markdown blocks (like ```) or comments. Start directly with the first subtitle block.
""".trimIndent()

    private val _aiAudioFileUri = MutableStateFlow<Uri?>(null)
    val aiAudioFileUri: StateFlow<Uri?> = _aiAudioFileUri.asStateFlow()

    private val _aiAudioFileName = MutableStateFlow<String?>(null)
    val aiAudioFileName: StateFlow<String?> = _aiAudioFileName.asStateFlow()

    private val _aiAudioMimeType = MutableStateFlow<String?>(null)
    val aiAudioMimeType: StateFlow<String?> = _aiAudioMimeType.asStateFlow()

    private val _aiCustomPrompt = MutableStateFlow("")
    val aiCustomPrompt: StateFlow<String> = _aiCustomPrompt.asStateFlow()

    private val _aiSourceText = MutableStateFlow("")
    val aiSourceText: StateFlow<String> = _aiSourceText.asStateFlow()

    private val _aiTranscriptionState = MutableStateFlow<GeminiApiClient.CallStepState>(GeminiApiClient.CallStepState.Idle)
    val aiTranscriptionState: StateFlow<GeminiApiClient.CallStepState> = _aiTranscriptionState.asStateFlow()

    private val _aiSrtLines = MutableStateFlow<List<SrtParser.SrtLine>>(emptyList())
    val aiSrtLines: StateFlow<List<SrtParser.SrtLine>> = _aiSrtLines.asStateFlow()

    private val _aiActiveLineIndex = MutableStateFlow(0)
    val aiActiveLineIndex: StateFlow<Int> = _aiActiveLineIndex.asStateFlow()

    // AI Player state
    private var aiMediaPlayer: MediaPlayer? = null
    private var aiPlayerTrackingJob: Job? = null

    private val _aiPlayerIsPlaying = MutableStateFlow(false)
    val aiPlayerIsPlaying: StateFlow<Boolean> = _aiPlayerIsPlaying.asStateFlow()

    private val _aiPlayerCurrentPosMs = MutableStateFlow(0L)
    val aiPlayerCurrentPosMs: StateFlow<Long> = _aiPlayerCurrentPosMs.asStateFlow()

    private val _aiPlayerDuration = MutableStateFlow(0L)
    val aiPlayerDuration: StateFlow<Long> = _aiPlayerDuration.asStateFlow()

    private val _aiIsSeeking = MutableStateFlow(false)
    val aiIsSeeking: StateFlow<Boolean> = _aiIsSeeking.asStateFlow()

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

            // Load AI Transcriber Settings
            val cachedPrompt = repository.getSettingValue("ai_custom_prompt", "")
            _aiCustomPrompt.value = if (cachedPrompt.isEmpty()) defaultAiPromptMain else cachedPrompt

            _aiSourceText.value = repository.getSettingValue("ai_source_text", "")

            val cachedAudioUriStr = repository.getSettingValue("ai_selected_audio_uri", "")
            if (cachedAudioUriStr.isNotEmpty()) {
                try {
                    val uri = Uri.parse(cachedAudioUriStr)
                    _aiAudioFileUri.value = uri
                    _aiAudioFileName.value = repository.getSettingValue("ai_selected_audio_name", "Selected Audio")
                    val mime = repository.getSettingValue("ai_selected_audio_mime", "audio/*")
                    _aiAudioMimeType.value = mime

                    // Load SRT lines for this specific audio
                    val linesJson = repository.getSettingValue("ai_srt_lines_$cachedAudioUriStr", "")
                    if (linesJson.isNotEmpty()) {
                        _aiSrtLines.value = aiLinesFromJson(linesJson)
                    }
                    initializeAiMediaPlayer(uri)
                } catch (e: Exception) {
                    Log.e("SubtitleStudioViewModel", "Error loading cached AI audio settings", e)
                }
            }
        }
    }

    private fun aiLinesToJson(lines: List<SrtParser.SrtLine>): String {
        val array = org.json.JSONArray()
        for (line in lines) {
            val obj = org.json.JSONObject()
            obj.put("index", line.index)
            obj.put("startTimeMs", line.startTimeMs)
            obj.put("endTimeMs", line.endTimeMs)
            obj.put("text", line.text)
            array.put(obj)
        }
        return array.toString()
    }

    private fun aiLinesFromJson(jsonStr: String): List<SrtParser.SrtLine> {
        val list = mutableListOf<SrtParser.SrtLine>()
        try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SrtParser.SrtLine(
                        index = obj.getInt("index"),
                        startTimeMs = obj.getLong("startTimeMs"),
                        endTimeMs = obj.getLong("endTimeMs"),
                        text = obj.getString("text")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("SubtitleStudioViewModel", "Failed to parse AI srt lines Json", e)
        }
        return list
    }

    fun setAiCustomPrompt(prompt: String) {
        _aiCustomPrompt.value = prompt
        viewModelScope.launch {
            repository.saveSetting("ai_custom_prompt", prompt)
        }
    }

    fun setAiSourceText(text: String) {
        _aiSourceText.value = text
        viewModelScope.launch {
            repository.saveSetting("ai_source_text", text)
        }
    }

    fun setAiSelectedAudio(uri: Uri, name: String, mimeType: String) {
        _aiAudioFileUri.value = uri
        _aiAudioFileName.value = name
        _aiAudioMimeType.value = mimeType
        _aiActiveLineIndex.value = 0
        _aiSrtLines.value = emptyList()

        viewModelScope.launch {
            repository.saveSetting("ai_selected_audio_uri", uri.toString())
            repository.saveSetting("ai_selected_audio_name", name)
            repository.saveSetting("ai_selected_audio_mime", mimeType)

            // Try loading cached subtitles for this specific URI
            val cachedJson = repository.getSettingValue("ai_srt_lines_$uri", "")
            if (cachedJson.isNotEmpty()) {
                _aiSrtLines.value = aiLinesFromJson(cachedJson)
            }
            initializeAiMediaPlayer(uri)
        }
    }

    fun clearAiSelectedAudio() {
        stopCleanAiMediaPlayer()
        _aiAudioFileUri.value = null
        _aiAudioFileName.value = null
        _aiAudioMimeType.value = null
        _aiSrtLines.value = emptyList()
        _aiActiveLineIndex.value = 0
        viewModelScope.launch {
            repository.saveSetting("ai_selected_audio_uri", "")
            repository.saveSetting("ai_selected_audio_name", "")
            repository.saveSetting("ai_selected_audio_mime", "")
        }
    }

    fun startAiTranscription() {
        val uri = _aiAudioFileUri.value ?: return
        val mime = _aiAudioMimeType.value ?: "audio/mp3"
        val rawPrompt = _aiCustomPrompt.value
        val sourceText = _aiSourceText.value

        val customPrompt = if (sourceText.isNotBlank()) {
            rawPrompt.replace("[sourceTextPlaceholder]", sourceText)
        } else {
            rawPrompt.replace("If a SOURCE TEXT is provided below, you MUST align the transcription lines EXACTLY with the SOURCE TEXT lines. \n- Each line in the SOURCE TEXT corresponds to exactly one SRT block. \n- Do NOT break a single source line across multiple SRT blocks. \n- Do NOT combine multiple source lines into a single SRT block. \n- Keep the words of each source line completely intact in its block. Do NOT split a word across lines (e.g., do NOT turn \"text1\" into \"te\" on one line and \"xt1\" on the next). \n- The audio might contain extra sounds, background noise, or other spoken words before, in between, or after. You can add extra subtitle blocks for these, but the lines that correspond to the SOURCE TEXT must remain intact and aligned as individual complete lines.\n\nSOURCE TEXT:\n[sourceTextPlaceholder]", "")
        }

        _aiTranscriptionState.value = GeminiApiClient.CallStepState.Sending("Preparing audio file...", "Initializing")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() }
                if (bytes == null || bytes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        _aiTranscriptionState.value = GeminiApiClient.CallStepState.OutOfOptions("Unable to read the audio file data.")
                    }
                    return@launch
                }

                val apiConfigs = repository.apiKeyConfigsFlow.firstOrNull() ?: emptyList()
                val listener = object : GeminiApiClient.StatusListener {
                    override fun onStateChanged(state: GeminiApiClient.CallStepState) {
                        viewModelScope.launch(Dispatchers.Main) {
                            _aiTranscriptionState.value = state
                        }
                    }
                }

                val resultText = GeminiApiClient.transcribeAudio(
                    audioBytes = bytes,
                    mimeType = mime,
                    customPrompt = customPrompt,
                    apiConfigs = apiConfigs,
                    listener = listener
                )

                if (resultText != null) {
                    val parsedLines = SrtParser.parse(resultText)
                    withContext(Dispatchers.Main) {
                        if (parsedLines.isNotEmpty()) {
                            _aiSrtLines.value = parsedLines
                            _aiActiveLineIndex.value = 0
                            _aiTranscriptionState.value = GeminiApiClient.CallStepState.Success("Successfully parsed ${parsedLines.size} lines.")
                            saveCurrentAiSrtLines()
                        } else {
                            _aiTranscriptionState.value = GeminiApiClient.CallStepState.OutOfOptions("Gemini generated text, but we couldn't parse it into SRT blocks. Output:\n\n$resultText")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _aiTranscriptionState.value = GeminiApiClient.CallStepState.OutOfOptions("Exception occurred: ${e.message}")
                }
            }
        }
    }

    fun saveCurrentAiSrtLines() {
        val uriStr = _aiAudioFileUri.value?.toString() ?: return
        val current = _aiSrtLines.value
        viewModelScope.launch {
            repository.saveSetting("ai_srt_lines_$uriStr", aiLinesToJson(current))
        }
    }

    fun setAiActiveLineIndex(index: Int) {
        val currentLines = _aiSrtLines.value
        if (index >= 0 && index < currentLines.size) {
            _aiActiveLineIndex.value = index
            seekAiPlayerToLineIndex(index)
        }
    }

    fun updateAiLineText(index: Int, newText: String) {
        val current = _aiSrtLines.value.toMutableList()
        if (index >= 0 && index < current.size) {
            val oldLine = current[index]
            current[index] = oldLine.copy(text = newText)
            _aiSrtLines.value = current
            saveCurrentAiSrtLines()
        }
    }

    fun updateAiLineTiming(index: Int, startMs: Long, endMs: Long) {
        val current = _aiSrtLines.value.toMutableList()
        if (index >= 0 && index < current.size) {
            val oldLine = current[index]
            current[index] = oldLine.copy(startTimeMs = startMs, endTimeMs = endMs)
            _aiSrtLines.value = current
            saveCurrentAiSrtLines()
        }
    }

    private fun initializeAiMediaPlayer(uri: Uri) {
        stopCleanAiMediaPlayer()
        try {
            aiMediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                _aiPlayerDuration.value = duration.toLong()
            }
            _aiPlayerIsPlaying.value = false
            _aiPlayerCurrentPosMs.value = 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AI Media Player for URI $uri", e)
        }
    }

    fun toggleAiPlayback() {
        val player = aiMediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _aiPlayerIsPlaying.value = false
            stopAiPlayerTracking()
        } else {
            player.start()
            _aiPlayerIsPlaying.value = true
            startAiPlayerTracking()
        }
    }

    fun playAiCurrentLineSegment() {
        val player = aiMediaPlayer ?: return
        val index = _aiActiveLineIndex.value
        val lines = _aiSrtLines.value
        if (index < 0 || index >= lines.size) return

        val line = lines[index]
        _aiIsSeeking.value = true
        _aiPlayerIsPlaying.value = false
        stopAiPlayerTracking()

        player.setOnSeekCompleteListener {
            _aiIsSeeking.value = false
            _aiPlayerCurrentPosMs.value = line.startTimeMs
            if (!player.isPlaying) {
                player.start()
            }
            _aiPlayerIsPlaying.value = true
            startAiPlayerTracking(stopTimeMs = line.endTimeMs, initialPosOverride = line.startTimeMs)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            player.seekTo(line.startTimeMs, MediaPlayer.SEEK_CLOSEST)
        } else {
            player.seekTo(line.startTimeMs.toInt())
        }
    }

    private fun seekAiPlayerToLineIndex(index: Int) {
        val player = aiMediaPlayer ?: return
        val lines = _aiSrtLines.value
        if (index < 0 || index >= lines.size) return

        val line = lines[index]
        _aiIsSeeking.value = true
        _aiPlayerCurrentPosMs.value = line.startTimeMs
        stopAiPlayerTracking()

        player.setOnSeekCompleteListener {
            _aiIsSeeking.value = false
            _aiPlayerCurrentPosMs.value = line.startTimeMs
            if (player.isPlaying) {
                startAiPlayerTracking(stopTimeMs = line.endTimeMs, initialPosOverride = line.startTimeMs)
            } else {
                _aiPlayerIsPlaying.value = false
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            player.seekTo(line.startTimeMs, MediaPlayer.SEEK_CLOSEST)
        } else {
            player.seekTo(line.startTimeMs.toInt())
        }
    }

    private fun startAiPlayerTracking(stopTimeMs: Long? = null, initialPosOverride: Long? = null) {
        stopAiPlayerTracking()
        aiPlayerTrackingJob = viewModelScope.launch(Dispatchers.Main) {
            val startTime = java.lang.System.currentTimeMillis()
            val initialPlayerPos = initialPosOverride ?: aiMediaPlayer?.currentPosition?.toLong() ?: 0L
            
            while (isActive) {
                val player = aiMediaPlayer
                if (player != null && player.isPlaying && !_aiIsSeeking.value) {
                    val elapsedRealtime = java.lang.System.currentTimeMillis() - startTime
                    val estimatedPos = initialPlayerPos + elapsedRealtime
                    
                    val actualPos = player.currentPosition.toLong()
                    val drift = Math.abs(estimatedPos - actualPos)
                    val finalPos = if (drift > 250) actualPos else estimatedPos
                    _aiPlayerCurrentPosMs.value = finalPos

                    if (stopTimeMs != null && finalPos >= stopTimeMs) {
                        player.pause()
                        _aiPlayerIsPlaying.value = false
                        _aiPlayerCurrentPosMs.value = stopTimeMs
                        break
                    }
                    
                    if (stopTimeMs == null) {
                        matchAiActiveLineWithTime(finalPos)
                    }
                }
                delay(16)
            }
        }
    }

    private fun stopAiPlayerTracking() {
        aiPlayerTrackingJob?.cancel()
        aiPlayerTrackingJob = null
    }

    private fun matchAiActiveLineWithTime(timeMs: Long) {
        val lines = _aiSrtLines.value
        for ((idx, line) in lines.withIndex()) {
            if (timeMs >= line.startTimeMs && timeMs <= line.endTimeMs) {
                if (_aiActiveLineIndex.value != idx) {
                    _aiActiveLineIndex.value = idx
                }
                break
            }
        }
    }

    fun seekAiPlayerToMs(timeMs: Long) {
        val player = aiMediaPlayer ?: return
        _aiIsSeeking.value = true
        _aiPlayerCurrentPosMs.value = timeMs
        stopAiPlayerTracking()
        player.setOnSeekCompleteListener {
            _aiIsSeeking.value = false
            _aiPlayerCurrentPosMs.value = timeMs
            if (player.isPlaying) {
                startAiPlayerTracking()
            }
        }
        player.seekTo(timeMs.toInt())
    }

    fun stopCleanAiMediaPlayer() {
        stopAiPlayerTracking()
        try {
            aiMediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            // ignore
        }
        aiMediaPlayer = null
        _aiPlayerIsPlaying.value = false
        _aiPlayerCurrentPosMs.value = 0L
        _aiPlayerDuration.value = 0L
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
        stopCleanAiMediaPlayer()
    }
}
