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
    var mediaPlayer: MediaPlayer? = null
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

    // --- Dynamic Sizing and Panel Layout States ---
    private val _videoHeightDp = MutableStateFlow(200f)
    val videoHeightDp: StateFlow<Float> = _videoHeightDp.asStateFlow()

    private val _timelinesWeightFraction = MutableStateFlow(0.8f)
    val timelinesWeightFraction: StateFlow<Float> = _timelinesWeightFraction.asStateFlow()

    fun setVideoHeightDp(height: Float) {
        _videoHeightDp.value = height.coerceIn(80f, 400f)
    }

    fun setTimelinesWeightFraction(fraction: Float) {
        _timelinesWeightFraction.value = fraction.coerceIn(0.2f, 1.8f)
    }

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
    var aiMediaPlayer: MediaPlayer? = null
    private var aiPlayerTrackingJob: Job? = null

    private val _aiPlayerIsPlaying = MutableStateFlow(false)
    val aiPlayerIsPlaying: StateFlow<Boolean> = _aiPlayerIsPlaying.asStateFlow()

    private val _aiPlayerCurrentPosMs = MutableStateFlow(0L)
    val aiPlayerCurrentPosMs: StateFlow<Long> = _aiPlayerCurrentPosMs.asStateFlow()

    private val _aiPlayerDuration = MutableStateFlow(0L)
    val aiPlayerDuration: StateFlow<Long> = _aiPlayerDuration.asStateFlow()

    private val _aiIsSeeking = MutableStateFlow(false)
    val aiIsSeeking: StateFlow<Boolean> = _aiIsSeeking.asStateFlow()

    // --- Tap SRT (Option 2) States ---
    private val _tapAudioFileUri = MutableStateFlow<Uri?>(null)
    val tapAudioFileUri: StateFlow<Uri?> = _tapAudioFileUri.asStateFlow()

    private val _tapAudioFileName = MutableStateFlow<String?>(null)
    val tapAudioFileName: StateFlow<String?> = _tapAudioFileName.asStateFlow()

    private val _tapAudioMimeType = MutableStateFlow<String?>(null)
    val tapAudioMimeType: StateFlow<String?> = _tapAudioMimeType.asStateFlow()

    private val _tapSourceTxtFileUri = MutableStateFlow<Uri?>(null)
    val tapSourceTxtFileUri: StateFlow<Uri?> = _tapSourceTxtFileUri.asStateFlow()

    private val _tapSourceTxtFileName = MutableStateFlow<String?>(null)
    val tapSourceTxtFileName: StateFlow<String?> = _tapSourceTxtFileName.asStateFlow()

    private val _tapSourceTxtLines = MutableStateFlow<List<String>>(emptyList())
    val tapSourceTxtLines: StateFlow<List<String>> = _tapSourceTxtLines.asStateFlow()

    private val _tapSrtLines = MutableStateFlow<List<SrtParser.SrtLine>>(emptyList())
    val tapSrtLines: StateFlow<List<SrtParser.SrtLine>> = _tapSrtLines.asStateFlow()

    private val _tapActiveLineIndex = MutableStateFlow(0)
    val tapActiveLineIndex: StateFlow<Int> = _tapActiveLineIndex.asStateFlow()

    private val _tapIsRecording = MutableStateFlow(false)
    val tapIsRecording: StateFlow<Boolean> = _tapIsRecording.asStateFlow()

    private val _tapCurrentRecordingStartMs = MutableStateFlow(0L)
    val tapCurrentRecordingStartMs: StateFlow<Long> = _tapCurrentRecordingStartMs.asStateFlow()

    // Tap Media Player state
    var tapMediaPlayer: MediaPlayer? = null
    private var tapPlayerTrackingJob: Job? = null

    private val _tapPlayerIsPlaying = MutableStateFlow(false)
    val tapPlayerIsPlaying: StateFlow<Boolean> = _tapPlayerIsPlaying.asStateFlow()

    private val _tapPlayerCurrentPosMs = MutableStateFlow(0L)
    val tapPlayerCurrentPosMs: StateFlow<Long> = _tapPlayerCurrentPosMs.asStateFlow()

    private val _tapPlayerDuration = MutableStateFlow(0L)
    val tapPlayerDuration: StateFlow<Long> = _tapPlayerDuration.asStateFlow()

    val tapSessionsList: StateFlow<List<TapSession>> = repository.tapSessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _tapIsSeeking = MutableStateFlow(false)
    val tapIsSeeking: StateFlow<Boolean> = _tapIsSeeking.asStateFlow()

    private val _studioOptionSetting = MutableStateFlow(1)
    val studioOptionSetting: StateFlow<Int> = _studioOptionSetting.asStateFlow()

    private val _showVideoPlayer = MutableStateFlow(true)
    val showVideoPlayer: StateFlow<Boolean> = _showVideoPlayer.asStateFlow()

    private val _precisePlaybackStop = MutableStateFlow(true)
    val precisePlaybackStop: StateFlow<Boolean> = _precisePlaybackStop.asStateFlow()

    init {
        viewModelScope.launch {
            // Load show video player setting
            val showVideo = repository.getSettingValue("show_video_player", "true") == "true"
            _showVideoPlayer.value = showVideo

            // Load precise playback stop setting
            val preciseStop = repository.getSettingValue("precise_playback_stop", "true") == "true"
            _precisePlaybackStop.value = preciseStop

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
                    try {
                        val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (permEx: Exception) {
                        Log.e("SubtitleStudioViewModel", "Could not re-take persistable permission for AI audio: $uri", permEx)
                    }
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

            // Load Tap Subtitle Settings for Option 2
            val cachedTapAudioUriStr = repository.getSettingValue("tap_selected_audio_uri", "")
            if (cachedTapAudioUriStr.isNotEmpty()) {
                val dbSession = repository.getTapSessionByUri(cachedTapAudioUriStr)
                if (dbSession != null) {
                    loadTapSession(dbSession)
                } else {
                    try {
                        val uri = Uri.parse(cachedTapAudioUriStr)
                        try {
                            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                        } catch (permEx: Exception) {
                            Log.e("SubtitleStudioViewModel", "Could not re-take permission", permEx)
                        }
                        _tapAudioFileUri.value = uri
                        _tapAudioFileName.value = repository.getSettingValue("tap_selected_audio_name", "Selected Audio")
                        _tapAudioMimeType.value = repository.getSettingValue("tap_selected_audio_mime", "audio/*")

                        val linesJson = repository.getSettingValue("tap_srt_lines_$cachedTapAudioUriStr", "")
                        if (linesJson.isNotEmpty()) {
                            _tapSrtLines.value = aiLinesFromJson(linesJson)
                        }

                        initializeTapMediaPlayer(uri)

                        val cachedTxtUriStr = repository.getSettingValue("tap_txt_file_uri", "")
                        if (cachedTxtUriStr.isNotEmpty()) {
                            try {
                                val txtUri = Uri.parse(cachedTxtUriStr)
                                try {
                                    val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    context.contentResolver.takePersistableUriPermission(txtUri, takeFlags)
                                } catch (permEx: Exception) {
                                    Log.e("SubtitleStudioViewModel", "Could not re-take txt permission", permEx)
                                }
                                _tapSourceTxtFileUri.value = txtUri
                                _tapSourceTxtFileName.value = repository.getSettingValue("tap_txt_file_name", "Selected TXT")
                                val rawTxtData = repository.getSettingValue("tap_source_txt", "")
                                if (rawTxtData.isNotEmpty()) {
                                    _tapSourceTxtLines.value = rawTxtData.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                                }
                            } catch (e: Exception) {
                                Log.e("SubtitleStudioViewModel", "Error loading cached Txt settings", e)
                            }
                        }
                        saveCurrentTapSession()
                    } catch (e: Exception) {
                        Log.e("SubtitleStudioViewModel", "Error loading cached Tap audio settings", e)
                    }
                }
            } else {
                _tapSourceTxtFileUri.value = null
                _tapSourceTxtFileName.value = null
                _tapSourceTxtLines.value = emptyList()
            }

            _tapIsRecording.value = repository.getSettingValue("tap_is_recording", "false") == "true"
            _tapCurrentRecordingStartMs.value = repository.getSettingValue("tap_current_rec_start_ms", "0").toLongOrNull() ?: 0L
            _tapActiveLineIndex.value = repository.getSettingValue("tap_active_line_index", "0").toIntOrNull() ?: 0
            _studioOptionSetting.value = repository.getSettingValue("studio_option_setting", "1").toIntOrNull() ?: 1
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
        try {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable URI permission for AI audio: $uri", e)
        }

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
                        try {
                            player.pause()
                            if (_precisePlaybackStop.value) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    player.seekTo(stopTimeMs, MediaPlayer.SEEK_CLOSEST)
                                } else {
                                    player.seekTo(stopTimeMs.toInt())
                                }
                            }
                        } catch (e: Exception) {}
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
                        try {
                            player.pause()
                            if (_precisePlaybackStop.value) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    player.seekTo(stopTimeMs, MediaPlayer.SEEK_CLOSEST)
                                } else {
                                    player.seekTo(stopTimeMs.toInt())
                                }
                            }
                        } catch (e: Exception) {}
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

    // --- Option 2 Tap to Sync Functions ---

    fun setTapSelectedAudio(uri: Uri, name: String, mimeType: String) {
        try {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable URI permission for audio: $uri", e)
        }

        _tapAudioFileUri.value = uri
        _tapAudioFileName.value = name
        _tapAudioMimeType.value = mimeType
        _tapActiveLineIndex.value = 0
        _tapSrtLines.value = emptyList()
        _tapIsRecording.value = false
        _tapCurrentRecordingStartMs.value = 0L

        // Clear TXT file state on new media select to prevent overlap/interference
        _tapSourceTxtFileUri.value = null
        _tapSourceTxtFileName.value = null
        _tapSourceTxtLines.value = emptyList()

        viewModelScope.launch {
            repository.saveSetting("tap_selected_audio_uri", uri.toString())
            repository.saveSetting("tap_selected_audio_name", name)
            repository.saveSetting("tap_selected_audio_mime", mimeType)
            repository.saveSetting("tap_is_recording", "false")
            repository.saveSetting("tap_current_rec_start_ms", "0")
            repository.saveSetting("tap_active_line_index", "0")
            repository.saveSetting("tap_txt_file_uri", "")
            repository.saveSetting("tap_txt_file_name", "")
            repository.saveSetting("tap_source_txt", "")

            // Try loading cached subtitles for this specific URI
            val cachedJson = repository.getSettingValue("tap_srt_lines_$uri", "")
            if (cachedJson.isNotEmpty()) {
                _tapSrtLines.value = aiLinesFromJson(cachedJson)
            } else {
                // Since _tapSourceTxtLines is cleared, this will correctly populate with default 10 lines
                val list = mutableListOf<SrtParser.SrtLine>()
                for (i in 1..10) {
                    list.add(SrtParser.SrtLine(
                        index = i,
                        startTimeMs = 0L,
                        endTimeMs = 0L,
                        text = "$i"
                    ))
                }
                _tapSrtLines.value = list
                saveCurrentTapSrtLines()
            }
            saveCurrentTapSession()
            initializeTapMediaPlayer(uri)
        }
    }

    fun saveCurrentTapSession() {
        val audioUri = _tapAudioFileUri.value ?: return
        val audioName = _tapAudioFileName.value ?: "Selected Audio"
        val audioMime = _tapAudioMimeType.value ?: "audio/*"
        val txtUriStr = _tapSourceTxtFileUri.value?.toString() ?: ""
        val txtNameStr = _tapSourceTxtFileName.value ?: ""
        val srtJson = aiLinesToJson(_tapSrtLines.value)
        
        viewModelScope.launch(Dispatchers.IO) {
            val session = TapSession(
                mediaUri = audioUri.toString(),
                mediaName = audioName,
                mediaMimeType = audioMime,
                txtUri = txtUriStr,
                txtName = txtNameStr,
                srtLinesJson = srtJson,
                lastActiveTimeMs = System.currentTimeMillis()
            )
            repository.saveTapSession(session)
            backupSessionsToDownloadFolder()
        }
    }

    fun backupSessionsToDownloadFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val backupDir = java.io.File(downloadsDir, ".logs-sub")
                if (!backupDir.exists()) {
                    backupDir.mkdirs()
                }

                // 1. Save all sessions as a combined JSON
                val sessions = repository.tapSessionsFlow.firstOrNull() ?: emptyList()
                val sessionsJsonArray = org.json.JSONArray()
                for (session in sessions) {
                    val obj = org.json.JSONObject()
                    obj.put("mediaUri", session.mediaUri)
                    obj.put("mediaName", session.mediaName)
                    obj.put("mediaMimeType", session.mediaMimeType)
                    obj.put("txtUri", session.txtUri)
                    obj.put("txtName", session.txtName)
                    obj.put("srtLinesJson", session.srtLinesJson)
                    obj.put("lastActiveTimeMs", session.lastActiveTimeMs)
                    sessionsJsonArray.put(obj)
                }
                val mainBackupFile = java.io.File(backupDir, "sessions_metadata_backup.json")
                mainBackupFile.writeText(sessionsJsonArray.toString(4), Charsets.UTF_8)

                // 2. Save individual SRT files and JSON files for each session so they are safe!
                for (session in sessions) {
                    val safeMediaName = session.mediaName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    
                    // Save JSON backup
                    val sessionJsonFile = java.io.File(backupDir, "${safeMediaName}_backup.json")
                    val sessionObj = org.json.JSONObject()
                    sessionObj.put("mediaUri", session.mediaUri)
                    sessionObj.put("mediaName", session.mediaName)
                    sessionObj.put("mediaMimeType", session.mediaMimeType)
                    sessionObj.put("txtUri", session.txtUri)
                    sessionObj.put("txtName", session.txtName)
                    sessionObj.put("srtLinesJson", session.srtLinesJson)
                    sessionObj.put("lastActiveTimeMs", session.lastActiveTimeMs)
                    sessionJsonFile.writeText(sessionObj.toString(4), Charsets.UTF_8)

                    // Save SRT backup
                    val srtList = aiLinesFromJson(session.srtLinesJson)
                    if (srtList.isNotEmpty()) {
                        val srtContent = buildSrtString(srtList)
                        val srtFile = java.io.File(backupDir, "${safeMediaName}_backup.srt")
                        srtFile.writeText(srtContent, Charsets.UTF_8)
                    }
                }
                Log.d("SubtitleStudioViewModel", "Backup successfully saved to Download/.logs-sub")
            } catch (e: Exception) {
                Log.e("SubtitleStudioViewModel", "Failed to backup sessions to Download folder", e)
            }
        }
    }

    private fun buildSrtString(lines: List<SrtParser.SrtLine>): String {
        val sb = StringBuilder()
        for (line in lines) {
            sb.append(line.index).append("\n")
            sb.append(formatTimeMsToSrt(line.startTimeMs))
                .append(" --> ")
                .append(formatTimeMsToSrt(line.endTimeMs))
                .append("\n")
            sb.append(line.text).append("\n\n")
        }
        return sb.toString()
    }

    private fun formatTimeMsToSrt(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000
        val milliseconds = ms % 1000
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, milliseconds)
    }

    fun restoreSessionsFromBackup(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val backupDir = java.io.File(downloadsDir, ".logs-sub")
                val mainBackupFile = java.io.File(backupDir, "sessions_metadata_backup.json")
                if (!mainBackupFile.exists()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "هیچ فایل پشتیبانی پیدا نشد.")
                    }
                    return@launch
                }

                val jsonStr = mainBackupFile.readText(Charsets.UTF_8)
                val array = org.json.JSONArray(jsonStr)
                var restoredCount = 0
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val mediaUri = obj.getString("mediaUri")
                    val mediaName = obj.getString("mediaName")
                    val mediaMimeType = obj.getString("mediaMimeType")
                    val txtUri = obj.optString("txtUri", "")
                    val txtName = obj.optString("txtName", "")
                    val srtLinesJson = obj.optString("srtLinesJson", "")
                    val lastActiveTimeMs = obj.optLong("lastActiveTimeMs", System.currentTimeMillis())

                    val existing = repository.getTapSessionByUri(mediaUri)
                    if (existing == null) {
                        repository.saveTapSession(
                            TapSession(
                                mediaUri = mediaUri,
                                mediaName = mediaName,
                                mediaMimeType = mediaMimeType,
                                txtUri = txtUri,
                                txtName = txtName,
                                srtLinesJson = srtLinesJson,
                                lastActiveTimeMs = lastActiveTimeMs
                            )
                        )
                        restoredCount++
                    }
                }
                withContext(Dispatchers.Main) {
                    onResult(true, "تعداد $restoredCount جلسه با موفقیت بازیابی شد.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore sessions from backup", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "خطا در بازیابی پشتیبان: ${e.localizedMessage}")
                }
            }
        }
    }

    fun loadTapSession(session: TapSession) {
        viewModelScope.launch {
            try {
                val uri = Uri.parse(session.mediaUri)
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take persistable URI permission for media on load", e)
            }

            if (session.txtUri.isNotEmpty()) {
                try {
                    val uri = Uri.parse(session.txtUri)
                    val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to take persistable URI permission for txt on load", e)
                }
            }

            val mediaUri = Uri.parse(session.mediaUri)
            _tapAudioFileUri.value = mediaUri
            _tapAudioFileName.value = session.mediaName
            _tapAudioMimeType.value = session.mediaMimeType

            if (session.txtUri.isNotEmpty()) {
                _tapSourceTxtFileUri.value = Uri.parse(session.txtUri)
                _tapSourceTxtFileName.value = session.txtName
            } else {
                _tapSourceTxtFileUri.value = null
                _tapSourceTxtFileName.value = null
            }

            val srtList = if (session.srtLinesJson.isNotEmpty()) {
                aiLinesFromJson(session.srtLinesJson)
            } else {
                emptyList()
            }
            _tapSrtLines.value = srtList

            repository.saveSetting("tap_selected_audio_uri", session.mediaUri)
            repository.saveSetting("tap_selected_audio_name", session.mediaName)
            repository.saveSetting("tap_selected_audio_mime", session.mediaMimeType)
            repository.saveSetting("tap_txt_file_uri", session.txtUri)
            repository.saveSetting("tap_txt_file_name", session.txtName)
            repository.saveSetting("tap_is_recording", "false")
            repository.saveSetting("tap_current_rec_start_ms", "0")
            _tapActiveLineIndex.value = 0
            repository.saveSetting("tap_active_line_index", "0")

            val txtLinesStr = if (session.txtUri.isNotEmpty()) {
                try {
                    val txtUri = Uri.parse(session.txtUri)
                    val inputStream = context.contentResolver.openInputStream(txtUri)
                    val txtContent = inputStream?.use { it.bufferedReader(Charsets.UTF_8).readText() } ?: ""
                    repository.saveSetting("tap_source_txt", txtContent)
                    txtContent
                } catch (e: Exception) {
                    val reconstructed = srtList.joinToString("\n") { it.text }
                    repository.saveSetting("tap_source_txt", reconstructed)
                    reconstructed
                }
            } else {
                val reconstructed = srtList.joinToString("\n") { it.text }
                repository.saveSetting("tap_source_txt", reconstructed)
                reconstructed
            }
            _tapSourceTxtLines.value = txtLinesStr.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

            initializeTapMediaPlayer(mediaUri)

            val updatedSession = session.copy(lastActiveTimeMs = System.currentTimeMillis())
            repository.saveTapSession(updatedSession)
            backupSessionsToDownloadFolder()
        }
    }

    fun deleteTapSession(session: TapSession) {
        viewModelScope.launch {
            repository.deleteTapSession(session.mediaUri)
            if (_tapAudioFileUri.value?.toString() == session.mediaUri) {
                clearTapSelectedAudio()
            } else {
                backupSessionsToDownloadFolder()
            }
        }
    }

    fun clearTapSelectedAudio() {
        stopCleanTapMediaPlayer()
        _tapAudioFileUri.value = null
        _tapAudioFileName.value = null
        _tapAudioMimeType.value = null
        _tapSrtLines.value = emptyList()
        _tapActiveLineIndex.value = 0
        _tapIsRecording.value = false
        _tapCurrentRecordingStartMs.value = 0L

        // Clear TXT file state on clear
        _tapSourceTxtFileUri.value = null
        _tapSourceTxtFileName.value = null
        _tapSourceTxtLines.value = emptyList()

        viewModelScope.launch {
            repository.saveSetting("tap_selected_audio_uri", "")
            repository.saveSetting("tap_selected_audio_name", "")
            repository.saveSetting("tap_selected_audio_mime", "")
            repository.saveSetting("tap_is_recording", "false")
            repository.saveSetting("tap_current_rec_start_ms", "0")
            repository.saveSetting("tap_active_line_index", "0")
            repository.saveSetting("tap_txt_file_uri", "")
            repository.saveSetting("tap_txt_file_name", "")
            repository.saveSetting("tap_source_txt", "")
        }
    }

    private fun initializeTapMediaPlayer(uri: Uri) {
        stopCleanTapMediaPlayer()
        try {
            tapMediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                _tapPlayerDuration.value = duration.toLong()
            }
            _tapPlayerIsPlaying.value = false
            _tapPlayerCurrentPosMs.value = 0L
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Tap Media Player for URI $uri", e)
        }
    }

    fun toggleTapPlayback() {
        val player = tapMediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _tapPlayerIsPlaying.value = false
            stopTapPlayerTracking()
        } else {
            player.start()
            _tapPlayerIsPlaying.value = true
            startTapPlayerTracking()
        }
    }

    fun stopCleanTapMediaPlayer() {
        stopTapPlayerTracking()
        try {
            tapMediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            // ignore
        }
        tapMediaPlayer = null
        _tapPlayerIsPlaying.value = false
        _tapPlayerCurrentPosMs.value = 0L
        _tapPlayerDuration.value = 0L
    }

    private fun startTapPlayerTracking(stopTimeMs: Long? = null) {
        stopTapPlayerTracking()
        tapPlayerTrackingJob = viewModelScope.launch(Dispatchers.Main) {
            val startTime = java.lang.System.currentTimeMillis()
            val initialPlayerPos = tapMediaPlayer?.currentPosition?.toLong() ?: 0L
            
            while (isActive) {
                val player = tapMediaPlayer
                if (player != null && player.isPlaying && !_tapIsSeeking.value) {
                    val elapsedRealtime = java.lang.System.currentTimeMillis() - startTime
                    val estimatedPos = initialPlayerPos + elapsedRealtime
                    
                    val actualPos = player.currentPosition.toLong()
                    val drift = Math.abs(estimatedPos - actualPos)
                    val finalPos = if (drift > 250) actualPos else estimatedPos
                    _tapPlayerCurrentPosMs.value = finalPos
                    
                    if (stopTimeMs != null && stopTimeMs > 0 && finalPos >= stopTimeMs) {
                        try {
                            player.pause()
                            if (_precisePlaybackStop.value) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    player.seekTo(stopTimeMs, MediaPlayer.SEEK_CLOSEST)
                                } else {
                                    player.seekTo(stopTimeMs.toInt())
                                }
                            }
                        } catch (e: Exception) {}
                        _tapPlayerIsPlaying.value = false
                        _tapPlayerCurrentPosMs.value = stopTimeMs
                        stopTapPlayerTracking()
                        break
                    }
                    
                    if (stopTimeMs == null || stopTimeMs <= 0) {
                        matchTapActiveLineWithTime(finalPos)
                    }
                }
                delay(16)
            }
        }
    }

    private fun stopTapPlayerTracking() {
        tapPlayerTrackingJob?.cancel()
        tapPlayerTrackingJob = null
    }

    private fun matchTapActiveLineWithTime(timeMs: Long) {
        val lines = _tapSrtLines.value
        if (_tapIsRecording.value) return
        for ((idx, line) in lines.withIndex()) {
            if (timeMs >= line.startTimeMs && timeMs <= line.endTimeMs) {
                if (_tapActiveLineIndex.value != idx) {
                    _tapActiveLineIndex.value = idx
                    saveCurrentTapActiveLineIndex(idx)
                }
                break
            }
        }
    }

    fun seekTapPlayerToMs(timeMs: Long) {
        val player = tapMediaPlayer ?: return
        _tapIsSeeking.value = true
        _tapPlayerCurrentPosMs.value = timeMs
        stopTapPlayerTracking()
        player.setOnSeekCompleteListener {
            _tapIsSeeking.value = false
            _tapPlayerCurrentPosMs.value = timeMs
            if (player.isPlaying) {
                startTapPlayerTracking()
            }
        }
        player.seekTo(timeMs.toInt())
    }

    fun seekTapForward(millis: Long = 5000L) {
        val player = tapMediaPlayer ?: return
        val current = player.currentPosition.toLong()
        val target = (current + millis).coerceAtMost(_tapPlayerDuration.value)
        seekTapPlayerToMs(target)
    }

    fun seekTapBackward(millis: Long = 5000L) {
        val player = tapMediaPlayer ?: return
        val current = player.currentPosition.toLong()
        val target = (current - millis).coerceAtLeast(0L)
        seekTapPlayerToMs(target)
    }

    fun tapTimingButton() {
        val player = tapMediaPlayer ?: return
        val currentPlayMs = player.currentPosition.toLong()

        val list = _tapSrtLines.value.toMutableList()
        val txtLines = _tapSourceTxtLines.value

        // Ensure we have at least one line
        if (list.isEmpty()) {
            val preText = txtLines.getOrNull(0) ?: "1"
            list.add(SrtParser.SrtLine(index = 1, startTimeMs = 0, endTimeMs = 0, text = preText))
        }

        if (!_tapIsRecording.value) {
            // First click: mark start of the active SRT block (default to first line or whatever is currently active)
            var activeIdx = _tapActiveLineIndex.value
            if (activeIdx < 0 || activeIdx >= list.size) {
                activeIdx = 0
            }
            
            val currentLine = list[activeIdx]
            list[activeIdx] = currentLine.copy(startTimeMs = currentPlayMs, endTimeMs = currentPlayMs + 1000L)
            _tapSrtLines.value = list
            _tapIsRecording.value = true
            _tapCurrentRecordingStartMs.value = currentPlayMs
            _tapActiveLineIndex.value = activeIdx

            viewModelScope.launch {
                repository.saveSetting("tap_is_recording", "true")
                repository.saveSetting("tap_current_rec_start_ms", currentPlayMs.toString())
                saveCurrentTapActiveLineIndex(activeIdx)
                saveCurrentTapSrtLines()
            }
        } else {
            // Next click: end current block, start next block
            val activeIdx = _tapActiveLineIndex.value
            if (activeIdx >= 0 && activeIdx < list.size) {
                val currentLine = list[activeIdx]
                list[activeIdx] = currentLine.copy(endTimeMs = currentPlayMs)
            }

            // Start next block
            val nextIdx = activeIdx + 1
            if (nextIdx < list.size) {
                // Next line already exists! Set its startTimeMs
                val nextLine = list[nextIdx]
                list[nextIdx] = nextLine.copy(startTimeMs = currentPlayMs, endTimeMs = currentPlayMs + 1000L)
                _tapActiveLineIndex.value = nextIdx
                _tapCurrentRecordingStartMs.value = currentPlayMs
            } else {
                // Next line does not exist. Create and append a new line!
                val lineNum = list.size + 1
                val preText = txtLines.getOrNull(lineNum - 1) ?: "$lineNum"
                val newBlock = SrtParser.SrtLine(
                    index = lineNum,
                    startTimeMs = currentPlayMs,
                    endTimeMs = currentPlayMs + 1000L,
                    text = preText
                )
                list.add(newBlock)
                _tapActiveLineIndex.value = list.lastIndex
                _tapCurrentRecordingStartMs.value = currentPlayMs
            }

            _tapSrtLines.value = list
            viewModelScope.launch {
                repository.saveSetting("tap_is_recording", "true")
                repository.saveSetting("tap_current_rec_start_ms", currentPlayMs.toString())
                saveCurrentTapActiveLineIndex(_tapActiveLineIndex.value)
                saveCurrentTapSrtLines()
            }
        }
    }

    fun finishRecordingTiming() {
        val player = tapMediaPlayer
        val currentPlayMs = player?.currentPosition?.toLong() ?: 0L
        
        // Pause player if playing
        if (player != null && player.isPlaying) {
            try {
                player.pause()
                _tapPlayerIsPlaying.value = false
                stopTapPlayerTracking()
            } catch (e: Exception) {
                // Ignore
            }
        }

        val list = _tapSrtLines.value.toMutableList()

        if (_tapIsRecording.value && list.isNotEmpty()) {
            val lastIdx = list.lastIndex
            list[lastIdx] = list[lastIdx].copy(endTimeMs = currentPlayMs)
            _tapSrtLines.value = list
            _tapIsRecording.value = false
            _tapCurrentRecordingStartMs.value = 0L

            viewModelScope.launch {
                repository.saveSetting("tap_is_recording", "false")
                repository.saveSetting("tap_current_rec_start_ms", "0")
                saveCurrentTapSrtLines()
            }
        } else {
            // Even if not actively recording, always make sure we persist latest edits
            viewModelScope.launch {
                saveCurrentTapSrtLines()
            }
        }
    }

    fun undoLastTap() {
        val currentList = _tapSrtLines.value.toMutableList()
        if (currentList.isEmpty()) return

        if (currentList.size == 1) {
            currentList.clear()
            _tapSrtLines.value = emptyList()
            _tapIsRecording.value = false
            _tapCurrentRecordingStartMs.value = 0L
            _tapActiveLineIndex.value = 0

            viewModelScope.launch {
                repository.saveSetting("tap_is_recording", "false")
                repository.saveSetting("tap_current_rec_start_ms", "0")
                saveCurrentTapActiveLineIndex(0)
                saveCurrentTapSrtLines()
            }
        } else {
            // Remove the last block (the one currently being recorded)
            currentList.removeAt(currentList.lastIndex)
            
            // The previous block becomes the active block again
            val lastIdx = currentList.lastIndex
            val previousBlock = currentList[lastIdx]
            _tapSrtLines.value = currentList
            _tapIsRecording.value = true
            _tapCurrentRecordingStartMs.value = previousBlock.startTimeMs
            _tapActiveLineIndex.value = lastIdx

            viewModelScope.launch {
                repository.saveSetting("tap_is_recording", "true")
                repository.saveSetting("tap_current_rec_start_ms", previousBlock.startTimeMs.toString())
                saveCurrentTapActiveLineIndex(lastIdx)
                saveCurrentTapSrtLines()
            }
        }
    }

    fun saveCurrentTapSrtLines() {
        val uriStr = _tapAudioFileUri.value?.toString() ?: return
        val current = _tapSrtLines.value
        viewModelScope.launch {
            repository.saveSetting("tap_srt_lines_$uriStr", aiLinesToJson(current))
            saveCurrentTapSession()
        }
    }

    private fun saveCurrentTapActiveLineIndex(idx: Int) {
        viewModelScope.launch {
            repository.saveSetting("tap_active_line_index", idx.toString())
        }
    }

    fun updateTapLineText(index: Int, newText: String) {
        val current = _tapSrtLines.value.toMutableList()
        if (index >= 0 && index < current.size) {
            val oldLine = current[index]
            current[index] = oldLine.copy(text = newText)
            _tapSrtLines.value = current
            saveCurrentTapSrtLines()
        }
    }

    fun updateTapLineTiming(index: Int, startMs: Long, endMs: Long) {
        val current = _tapSrtLines.value.toMutableList()
        if (index >= 0 && index < current.size) {
            val oldLine = current[index]
            current[index] = oldLine.copy(startTimeMs = startMs, endTimeMs = endMs)
            _tapSrtLines.value = current
            saveCurrentTapSrtLines()
        }
    }

    fun setTapActiveLineIndex(index: Int) {
        val currentLines = _tapSrtLines.value
        if (index >= 0 && index < currentLines.size) {
            _tapActiveLineIndex.value = index
            saveCurrentTapActiveLineIndex(index)
            seekTapPlayerToLineIndex(index)
        }
    }

    private fun seekTapPlayerToLineIndex(index: Int) {
        val player = tapMediaPlayer ?: return
        val lines = _tapSrtLines.value
        if (index < 0 || index >= lines.size) return

        val line = lines[index]
        _tapIsSeeking.value = true
        _tapPlayerCurrentPosMs.value = line.startTimeMs
        stopTapPlayerTracking()

        player.setOnSeekCompleteListener {
            _tapIsSeeking.value = false
            _tapPlayerCurrentPosMs.value = line.startTimeMs
            try {
                player.start()
            } catch (e: Exception) {}
            _tapPlayerIsPlaying.value = true
            
            val stopAt = if (line.endTimeMs > line.startTimeMs) line.endTimeMs else null
            startTapPlayerTracking(stopTimeMs = stopAt)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            player.seekTo(line.startTimeMs, MediaPlayer.SEEK_CLOSEST)
        } else {
            player.seekTo(line.startTimeMs.toInt())
        }
    }

    fun setTapSourceTxtFile(uri: Uri, name: String) {
        try {
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable URI permission for txt: $uri", e)
        }

        _tapSourceTxtFileUri.value = uri
        _tapSourceTxtFileName.value = name

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.use { it.bufferedReader(Charsets.UTF_8).readText() } ?: ""
                val lines = text.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                withContext(Dispatchers.Main) {
                    _tapSourceTxtLines.value = lines
                    
                    val srtList = _tapSrtLines.value.toMutableList()
                    val newSrtList = mutableListOf<SrtParser.SrtLine>()
                    val maxCount = maxOf(srtList.size, lines.size)
                    
                    for (i in 0 until maxCount) {
                        val existingLine = srtList.getOrNull(i)
                        if (existingLine != null) {
                            val textToUse = lines.getOrNull(i) ?: existingLine.text
                            newSrtList.add(existingLine.copy(text = textToUse))
                        } else {
                            val textToUse = lines.getOrNull(i) ?: "${i + 1}"
                            newSrtList.add(SrtParser.SrtLine(
                                index = i + 1,
                                startTimeMs = 0L,
                                endTimeMs = 0L,
                                text = textToUse
                            ))
                        }
                    }
                    _tapSrtLines.value = newSrtList
                    saveCurrentTapSrtLines()

                    viewModelScope.launch {
                        repository.saveSetting("tap_txt_file_uri", uri.toString())
                        repository.saveSetting("tap_txt_file_name", name)
                        repository.saveSetting("tap_source_txt", text)
                        saveCurrentTapSession()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read text file", e)
            }
        }
    }

    fun clearTapSourceTxtFile() {
        _tapSourceTxtFileUri.value = null
        _tapSourceTxtFileName.value = null
        _tapSourceTxtLines.value = emptyList()
        viewModelScope.launch {
            repository.saveSetting("tap_txt_file_uri", "")
            repository.saveSetting("tap_txt_file_name", "")
            repository.saveSetting("tap_source_txt", "")
            saveCurrentTapSession()
        }
    }

    fun importSrtToTapLines(uri: Uri, mode: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.use { it.bufferedReader(Charsets.UTF_8).readText() } ?: ""
                val importedLines = SrtParser.parse(text)
                
                if (importedLines.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(false, "فایل SRT نامعتبر است یا لاینی ندارد.")
                    }
                    return@launch
                }
                
                withContext(Dispatchers.Main) {
                    if (mode == 1) {
                        // Mode 1: Sync timings of current lines with imported lines
                        val currentLines = _tapSrtLines.value.toMutableList()
                        val updatedLines = mutableListOf<SrtParser.SrtLine>()
                        
                        for (i in currentLines.indices) {
                            val current = currentLines[i]
                            val imported = importedLines.getOrNull(i)
                            if (imported != null) {
                                updatedLines.add(current.copy(
                                    startTimeMs = imported.startTimeMs,
                                    endTimeMs = imported.endTimeMs
                                ))
                            } else {
                                updatedLines.add(current)
                            }
                        }
                        
                        // If current lines list is empty or shorter, we can also add any extra lines from imported with original text if needed.
                        // But user specifically said "فقط زمان بندی لاین های جدا شده فعلی با فایل srt ورودی هماهنگ میشه"
                        // So we strictly sync current lines.
                        _tapSrtLines.value = updatedLines
                    } else {
                        // Mode 2: Replace both timings and texts
                        _tapSrtLines.value = importedLines
                        
                        // Also fill tapSourceTxtLines to match the new text so the tapping flow makes sense
                        val importedTexts = importedLines.map { it.text }
                        _tapSourceTxtLines.value = importedTexts
                    }
                    
                    saveCurrentTapSrtLines()
                    onResult(true, "فایل SRT با موفقیت وارد شد.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import SRT file", e)
                withContext(Dispatchers.Main) {
                    onResult(false, "خطا در بارگذاری فایل SRT: ${e.message}")
                }
            }
        }
    }

    fun addNewTapLinePlaceholder() {
        val list = _tapSrtLines.value.toMutableList()
        val nextIdx = list.size + 1
        val txtLines = _tapSourceTxtLines.value
        val preText = txtLines.getOrNull(nextIdx - 1) ?: "$nextIdx"
        
        val lastEndTime = list.lastOrNull()?.endTimeMs ?: 0L
        
        list.add(SrtParser.SrtLine(
            index = nextIdx,
            startTimeMs = lastEndTime,
            endTimeMs = lastEndTime + 1000L,
            text = preText
        ))
        _tapSrtLines.value = list
        saveCurrentTapSrtLines()
    }

    fun writeTapSrtToUri(context: android.content.Context, uri: Uri): Boolean {
        val srtContent = SrtParser.buildSrt(_tapSrtLines.value)
        val success = SafHelper.writeTextToUri(context, uri, srtContent)
        return success
    }

    fun exportTapSrtToDownloads(): String? {
        val srtContent = SrtParser.buildSrt(_tapSrtLines.value)
        if (srtContent.isEmpty()) return null

        val rawName = _tapAudioFileName.value ?: "tap_subtitles"
        val cleanName = rawName.substringBeforeLast(".").replace(" ", "_")
        val fileName = "${cleanName}_subbed.srt"

        val success = repository.saveYoutubeSrtToDownloads(fileName, srtContent)
        return if (success) fileName else null
    }

    fun setStudioOption(option: Int) {
        _studioOptionSetting.value = option
        viewModelScope.launch {
            repository.saveSetting("studio_option_setting", option.toString())
        }
    }

    fun setShowVideoPlayer(show: Boolean) {
        _showVideoPlayer.value = show
        viewModelScope.launch {
            repository.saveSetting("show_video_player", show.toString())
        }
    }

    fun setPrecisePlaybackStop(enabled: Boolean) {
        _precisePlaybackStop.value = enabled
        viewModelScope.launch {
            repository.saveSetting("precise_playback_stop", enabled.toString())
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopMediaPlayer()
        stopCleanAiMediaPlayer()
        stopCleanTapMediaPlayer()
    }
}
