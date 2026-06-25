package com.example.ui

import android.app.Application
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.utils.SafHelper
import com.example.utils.SrtParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class ReviewerViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ReviewerViewModel"
    private val context = application.applicationContext
    private val database = AppDatabase.getDatabase(context)
    val repository = SubtitleRepository(context, database)

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

    // Media Player state
    var mediaPlayer: MediaPlayer? = null
    private var playerTrackingJob: Job? = null

    private val _playerIsPlaying = MutableStateFlow(false)
    val playerIsPlaying: StateFlow<Boolean> = _playerIsPlaying.asStateFlow()

    private val _playerCurrentPosMs = MutableStateFlow(0L)
    val playerCurrentPosMs: StateFlow<Long> = _playerCurrentPosMs.asStateFlow()

    private val _playerDuration = MutableStateFlow(0L)
    val playerDuration: StateFlow<Long> = _playerDuration.asStateFlow()

    private val _autoPlayOnNextLine = MutableStateFlow(true)
    val autoPlayOnNextLine: StateFlow<Boolean> = _autoPlayOnNextLine.asStateFlow()

    private val _isSeeking = MutableStateFlow(false)
    val isSeeking: StateFlow<Boolean> = _isSeeking.asStateFlow()

    private val _timelinesWeightFraction = MutableStateFlow(0.8f)
    val timelinesWeightFraction: StateFlow<Float> = _timelinesWeightFraction.asStateFlow()

    private val _precisePlaybackStop = MutableStateFlow(true)
    val precisePlaybackStop: StateFlow<Boolean> = _precisePlaybackStop.asStateFlow()

    init {
        viewModelScope.launch {
            // Load precise playback stop setting
            val preciseStop = repository.getSettingValue("precise_playback_stop", "true") == "true"
            _precisePlaybackStop.value = preciseStop

            // Load last active folder tree
            val lastFolder = repository.getSettingValue("last_folder_tree_uri", "")
            if (lastFolder.isNotEmpty()) {
                _activeProjectFolderUri.value = lastFolder
                val lastSubId = repository.getSettingValue("last_sub_project_folder_id", "")
                val lastSubName = repository.getSettingValue("last_sub_project_folder_name", "")
                scanTreeForSubdirs(
                    lastFolder,
                    initialSubId = lastSubId.takeIf { it.isNotEmpty() },
                    initialSubName = lastSubName.takeIf { it.isNotEmpty() }
                )
            }
        }
    }

    fun setTimelinesWeightFraction(fraction: Float) {
        _timelinesWeightFraction.value = fraction.coerceIn(0.2f, 1.8f)
    }

    fun setAutoPlay(enabled: Boolean) {
        _autoPlayOnNextLine.value = enabled
    }

    /**
     * Folder & SAF selection
     */
    fun selectActiveMainFolder(treeUri: String) {
        _activeProjectFolderUri.value = treeUri
        try {
            val uri = Uri.parse(treeUri)
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to take persistable URI permission for $treeUri", e)
        }
        viewModelScope.launch {
            repository.saveSetting("last_folder_tree_uri", treeUri)
            scanTreeForSubdirs(treeUri)
        }
    }

    fun clearActiveMainFolder() {
        _activeProjectFolderUri.value = null
        _availableSubProjects.value = emptyList()
        _selectedProjectSubFolderId.value = null
        _selectedProjectSubFolderName.value = null
        _srtLines.value = emptyList()
        _projectMetadata.value = null
        stopMediaPlayer()
        viewModelScope.launch {
            repository.saveSetting("last_folder_tree_uri", "")
            repository.saveSetting("last_sub_project_folder_id", "")
            repository.saveSetting("last_sub_project_folder_name", "")
        }
    }

    fun refreshActiveMainFolder() {
        val folderUri = _activeProjectFolderUri.value ?: return
        val lastSubId = _selectedProjectSubFolderId.value
        val lastSubName = _selectedProjectSubFolderName.value
        viewModelScope.launch {
            scanTreeForSubdirs(
                folderUri,
                initialSubId = lastSubId,
                initialSubName = lastSubName
            )
        }
    }

    fun closeSelectedProject() {
        _selectedProjectSubFolderId.value = null
        _selectedProjectSubFolderName.value = null
        _srtLines.value = emptyList()
        _projectMetadata.value = null
        _activeLineIndex.value = 0
        stopMediaPlayer()
        viewModelScope.launch {
            repository.saveSetting("last_sub_project_folder_id", "")
            repository.saveSetting("last_sub_project_folder_name", "")
        }
    }

    fun clearProjectCache() {
        val folderUri = _activeProjectFolderUri.value ?: return
        val subId = _selectedProjectSubFolderId.value ?: return
        val uniqueKey = "${folderUri}_$subId"
        viewModelScope.launch {
            repository.clearSrtLineStates(uniqueKey)
            // Re-load current project lines purely from files
            val meta = _projectMetadata.value
            if (meta != null) {
                val lines = repository.loadAndMergeProjectLines(folderUri, subId, meta)
                _srtLines.value = lines
            }
        }
    }

    private fun scanTreeForSubdirs(treeUriStr: String, initialSubId: String? = null, initialSubName: String? = null) {
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

            if (initialSubId != null && initialSubName != null && subdirs.any { it.documentId == initialSubId }) {
                loadSubProject(SubDirectoryProject(name = initialSubName, documentId = initialSubId))
            } else {
                // Clear current sub project selection
                _selectedProjectSubFolderId.value = null
                _selectedProjectSubFolderName.value = null
                _srtLines.value = emptyList()
                _projectMetadata.value = null
                stopMediaPlayer()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning subdirectories", e)
        }
    }

    fun loadSubProject(subp: SubDirectoryProject) {
        val folderUri = _activeProjectFolderUri.value ?: return
        _selectedProjectSubFolderId.value = subp.documentId
        _selectedProjectSubFolderName.value = subp.name

        viewModelScope.launch {
            repository.saveSetting("last_sub_project_folder_id", subp.documentId)
            repository.saveSetting("last_sub_project_folder_name", subp.name)

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
                repository.insertProject(
                    state.copy(
                        lastActiveLineIndex = index,
                        lastModifiedTimeMs = java.lang.System.currentTimeMillis()
                    )
                )
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
                                player.setOnSeekCompleteListener(null)
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

    override fun onCleared() {
        super.onCleared()
        stopMediaPlayer()
    }
}
