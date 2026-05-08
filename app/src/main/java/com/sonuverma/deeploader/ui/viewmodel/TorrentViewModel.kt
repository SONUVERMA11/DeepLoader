package com.sonuverma.deeploader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sonuverma.deeploader.core.torrent.SessionStats
import com.sonuverma.deeploader.core.torrent.TorrentEngine
import com.sonuverma.deeploader.core.torrent.TorrentSearchEngine
import com.sonuverma.deeploader.data.models.TorrentCategory
import com.sonuverma.deeploader.data.models.TorrentInfo
import com.sonuverma.deeploader.data.models.TorrentSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Torrent Search & Download screens.
 *
 * Coordinates:
 *   - TorrentSearchEngine (multi-source search)
 *   - TorrentEngine (libtorrent4j download/seeding)
 *
 * Separate from MainViewModel to keep concerns isolated.
 *
 * Developer: Sonu Verma
 */
@HiltViewModel
class TorrentViewModel @Inject constructor(
    private val searchEngine: TorrentSearchEngine,
    private val torrentEngine: TorrentEngine
) : ViewModel() {

    // ─── Search State ───

    private val _searchState = MutableStateFlow<TorrentSearchState>(TorrentSearchState.Idle)
    val searchState: StateFlow<TorrentSearchState> = _searchState.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TorrentInfo>>(emptyList())
    val searchResults: StateFlow<List<TorrentInfo>> = _searchResults.asStateFlow()

    // ─── Active Torrents ───

    val activeTorrents: StateFlow<Map<String, TorrentInfo>> = torrentEngine.torrentStates
    val sessionStats: StateFlow<SessionStats> = torrentEngine.sessionStats
    val isEngineRunning: StateFlow<Boolean> = torrentEngine.isRunning

    // ─── Filters ───

    private val _selectedCategory = MutableStateFlow<TorrentCategory?>(null)
    val selectedCategory: StateFlow<TorrentCategory?> = _selectedCategory.asStateFlow()

    private val _selectedSource = MutableStateFlow<TorrentSource?>(null)
    val selectedSource: StateFlow<TorrentSource?> = _selectedSource.asStateFlow()

    init {
        // Start torrent engine on ViewModel creation
        torrentEngine.start()
    }

    // ─── Search Actions ───

    fun search(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _searchState.value = TorrentSearchState.Loading(query)

            try {
                val results = searchEngine.search(
                    query = query,
                    category = _selectedCategory.value,
                    source = _selectedSource.value
                )
                _searchResults.value = results
                _searchState.value = if (results.isEmpty()) {
                    TorrentSearchState.Empty(query)
                } else {
                    TorrentSearchState.Results(results.size)
                }
            } catch (e: Exception) {
                _searchState.value = TorrentSearchState.Error(
                    e.message ?: "Search failed"
                )
            }
        }
    }

    fun setCategory(category: TorrentCategory?) {
        _selectedCategory.value = category
    }

    fun setSource(source: TorrentSource?) {
        _selectedSource.value = source
    }

    fun clearSearch() {
        _searchState.value = TorrentSearchState.Idle
        _searchResults.value = emptyList()
    }

    // ─── Download Actions ───

    /**
     * Start downloading a torrent from search results.
     * @param sequential If true, enables streaming mode (in-order download)
     */
    fun startTorrent(torrentInfo: TorrentInfo, sequential: Boolean = false) {
        val magnetUri = torrentInfo.magnetUri
        if (magnetUri.isBlank()) return

        torrentEngine.addTorrent(magnetUri, sequential)
    }

    fun pauseTorrent(infoHash: String) {
        torrentEngine.pauseTorrent(infoHash)
    }

    fun resumeTorrent(infoHash: String) {
        torrentEngine.resumeTorrent(infoHash)
    }

    fun removeTorrent(infoHash: String, deleteFiles: Boolean = false) {
        torrentEngine.removeTorrent(infoHash, deleteFiles)
    }

    fun toggleSequential(infoHash: String, enabled: Boolean) {
        torrentEngine.setSequentialMode(infoHash, enabled)
    }

    /**
     * Get the file path for streaming a specific file.
     */
    fun getStreamingPath(infoHash: String, fileIndex: Int = 0): String? {
        return torrentEngine.getFilePath(infoHash, fileIndex)
    }

    /**
     * Fetch magnet link for 1337x results (which don't include magnets in search).
     */
    fun fetchMagnet(detailUrl: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val magnet = searchEngine.fetchMagnetLink(detailUrl)
            onResult(magnet)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Don't stop the engine — it should survive ViewModel lifecycle
        // Engine is singleton and managed by Hilt
    }
}

// ─── Search State ───

sealed class TorrentSearchState {
    data object Idle : TorrentSearchState()
    data class Loading(val query: String) : TorrentSearchState()
    data class Results(val count: Int) : TorrentSearchState()
    data class Empty(val query: String) : TorrentSearchState()
    data class Error(val message: String) : TorrentSearchState()
}
