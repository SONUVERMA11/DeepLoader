package com.sonuverma.deeploader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonuverma.deeploader.core.clipboard.ClipboardDetection
import com.sonuverma.deeploader.core.clipboard.ClipboardWatcher
import com.sonuverma.deeploader.core.download.DownloadManager
import com.sonuverma.deeploader.core.download.NetworkMonitor
import com.sonuverma.deeploader.core.download.NetworkState
import com.sonuverma.deeploader.core.extraction.ExtractionEngine
import com.sonuverma.deeploader.core.extraction.ExtractionException
import com.sonuverma.deeploader.core.player.PlayerManager
import com.sonuverma.deeploader.core.player.PipController
import com.sonuverma.deeploader.core.updater.UpdateResult
import com.sonuverma.deeploader.core.updater.YtDlpUpdater
import com.sonuverma.deeploader.data.db.DownloadDao
import com.sonuverma.deeploader.data.db.DownloadEntity
import com.sonuverma.deeploader.data.models.DownloadStatus
import com.sonuverma.deeploader.data.models.ExtractionResult
import com.sonuverma.deeploader.data.models.StreamFormat
import com.sonuverma.deeploader.data.prefs.AppPreferences
import com.sonuverma.deeploader.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main ViewModel for DeepLoader.
 *
 * Coordinates between:
 *   - ExtractionEngine (URL → streams)
 *   - DownloadManager (download lifecycle)
 *   - NetworkMonitor (connectivity state)
 *   - PlayerManager (media playback)
 *   - PipController (picture-in-picture)
 *   - ClipboardWatcher (auto-detect URLs)
 *   - YtDlpUpdater (keep extraction engine current)
 *   - DownloadDao (download history)
 *   - AppPreferences (user settings)
 *
 * All UI state flows from here — screens are pure composable functions
 * that observe ViewModel state.
 *
 * Developer: Sonu Verma
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val extractionEngine: ExtractionEngine,
    private val downloadManager: DownloadManager,
    private val networkMonitor: NetworkMonitor,
    val playerManager: PlayerManager,
    val pipController: PipController,
    private val clipboardWatcher: ClipboardWatcher,
    private val ytDlpUpdater: YtDlpUpdater,
    private val downloadDao: DownloadDao,
    private val prefs: AppPreferences
) : ViewModel() {

    // ─── Extraction State ───

    private val _extractionState = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val extractionState: StateFlow<ExtractionState> = _extractionState.asStateFlow()

    private val _currentResult = MutableStateFlow<ExtractionResult?>(null)
    val currentResult: StateFlow<ExtractionResult?> = _currentResult.asStateFlow()

    // ─── Clipboard State ───

    val clipboardDetection: StateFlow<ClipboardDetection?> = clipboardWatcher.detectedUrl

    // ─── Download State ───

    val activeDownloads = downloadDao.getActiveDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedDownloads = downloadDao.getCompletedDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDownloads = downloadDao.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Settings State ───

    val themeMode = prefs.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val wifiOnly = prefs.wifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val clipboardWatcherEnabled = prefs.clipboardWatcher
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ─── Network State ───

    val networkState: StateFlow<NetworkState> = networkMonitor.networkState

    // ─── yt-dlp Update State ───

    private val _ytDlpUpdateState = MutableStateFlow<YtDlpUpdateState>(YtDlpUpdateState.Idle)
    val ytDlpUpdateState: StateFlow<YtDlpUpdateState> = _ytDlpUpdateState.asStateFlow()

    init {
        // Auto-update yt-dlp on app launch
        viewModelScope.launch {
            updateYtDlp()
        }
        // Start network monitoring
        networkMonitor.startMonitoring()
    }

    // ─── Extraction Actions ───

    /**
     * Extract media info from a URL.
     * Updates extractionState through Idle → Loading → Success/Error.
     */
    fun extractUrl(url: String) {
        if (url.isBlank()) return

        viewModelScope.launch {
            _extractionState.value = ExtractionState.Loading(url)

            try {
                val result = extractionEngine.extract(url)
                _currentResult.value = result
                _extractionState.value = ExtractionState.Success(result)
            } catch (e: ExtractionException) {
                _extractionState.value = ExtractionState.Error(e.userMessage)
            } catch (e: Exception) {
                _extractionState.value = ExtractionState.Error(
                    "An unexpected error occurred. Please try again."
                )
            }
        }
    }

    /**
     * Reset extraction state to idle.
     */
    fun resetExtraction() {
        _extractionState.value = ExtractionState.Idle
        _currentResult.value = null
    }

    // ─── Clipboard Actions ───

    fun dismissClipboard() {
        clipboardWatcher.dismissDetection()
    }

    fun startClipboardWatcher() {
        clipboardWatcher.startWatching()
    }

    fun stopClipboardWatcher() {
        clipboardWatcher.stopWatching()
    }

    fun checkClipboardNow() {
        clipboardWatcher.checkNow()
    }

    // ─── Theme Actions ───

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
        }
    }

    // ─── Settings Actions ───

    fun setWifiOnly(enabled: Boolean) {
        viewModelScope.launch { prefs.setWifiOnly(enabled) }
    }

    fun setClipboardWatcher(enabled: Boolean) {
        viewModelScope.launch { prefs.setClipboardWatcher(enabled) }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { prefs.setBiometricLock(enabled) }
    }

    fun setNotificationSound(enabled: Boolean) {
        viewModelScope.launch { prefs.setNotificationSound(enabled) }
    }

    fun setAutoRetry(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoRetry(enabled) }
    }

    fun setSaveToGallery(enabled: Boolean) {
        viewModelScope.launch { prefs.setSaveToGallery(enabled) }
    }

    fun setShowSpeedGraph(enabled: Boolean) {
        viewModelScope.launch { prefs.setShowSpeedGraph(enabled) }
    }

    fun setAutoUpdateYtDlp(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoUpdateYtDlp(enabled) }
    }

    // ─── yt-dlp Update Actions ───

    fun updateYtDlp(force: Boolean = false) {
        viewModelScope.launch {
            _ytDlpUpdateState.value = YtDlpUpdateState.Checking

            when (val result = ytDlpUpdater.checkAndUpdate(force)) {
                is UpdateResult.Updated -> {
                    _ytDlpUpdateState.value = YtDlpUpdateState.Updated(result.version)
                }
                is UpdateResult.AlreadyUpToDate -> {
                    _ytDlpUpdateState.value = YtDlpUpdateState.UpToDate(result.version)
                }
                is UpdateResult.Skipped -> {
                    _ytDlpUpdateState.value = YtDlpUpdateState.Idle
                }
                is UpdateResult.Failed -> {
                    _ytDlpUpdateState.value = YtDlpUpdateState.Failed(result.error)
                }
            }
        }
    }

    // ─── Download Actions ───

    /**
     * Start downloading a selected format from extraction result.
     */
    fun startDownload(
        result: ExtractionResult,
        videoFormat: StreamFormat,
        audioFormat: StreamFormat? = null,
        convertToMp3: Boolean = false
    ): String {
        return downloadManager.startDownload(result, videoFormat, audioFormat, convertToMp3)
    }

    fun pauseDownload(id: String) {
        downloadManager.pauseDownload(id)
    }

    fun resumeDownload(id: String) {
        downloadManager.resumeDownload(id)
    }

    fun cancelDownload(id: String) {
        downloadManager.cancelDownload(id)
    }

    fun retryDownload(id: String) {
        downloadManager.retryDownload(id)
    }

    fun pauseAllDownloads() {
        downloadManager.pauseAll()
    }

    // ─── Download History Actions ───

    fun deleteDownload(id: String) {
        viewModelScope.launch {
            downloadDao.deleteById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            downloadDao.clearHistory()
        }
    }

    // ─── Playback Actions ───

    /**
     * Play a stream URL from extraction result (direct playback without download).
     */
    fun playStream(result: ExtractionResult, format: StreamFormat) {
        playerManager.playStreamUrl(
            url = format.url,
            title = result.title,
            thumbnailUrl = result.thumbnailUrl
        )
    }

    override fun onCleared() {
        super.onCleared()
        clipboardWatcher.stopWatching()
        networkMonitor.stopMonitoring()
        downloadManager.stopForegroundServiceIfIdle()
        playerManager.release()
    }
}

// ─── UI State Classes ───

sealed class ExtractionState {
    data object Idle : ExtractionState()
    data class Loading(val url: String) : ExtractionState()
    data class Success(val result: ExtractionResult) : ExtractionState()
    data class Error(val message: String) : ExtractionState()
}

sealed class YtDlpUpdateState {
    data object Idle : YtDlpUpdateState()
    data object Checking : YtDlpUpdateState()
    data class Updated(val version: String) : YtDlpUpdateState()
    data class UpToDate(val version: String) : YtDlpUpdateState()
    data class Failed(val error: String) : YtDlpUpdateState()
}
