package com.sonuverma.deeploader.core.torrent

import android.content.Context
import android.util.Log
import com.sonuverma.deeploader.data.models.TorrentFile
import com.sonuverma.deeploader.data.models.TorrentInfo
import com.sonuverma.deeploader.data.models.TorrentStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.PieceFinishedAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TorrentEngine — libtorrent4j-based BitTorrent client.
 *
 * Capabilities:
 *   - Magnet link resolution (metadata download from DHT/PEX)
 *   - Multi-file torrent downloading with per-file priority
 *   - Sequential mode for video streaming while downloading
 *   - DHT bootstrapping for tracker-less torrents
 *   - Upload ratio control (seeding management)
 *   - Bandwidth limiting (respect user preferences)
 *   - Real-time progress/speed/peer reporting via StateFlow
 *
 * Session lifecycle:
 *   start() → addTorrent() → [...] → removeTorrent() → stop()
 *
 * Developer: Sonu Verma
 */
@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "TorrentEngine"
        private const val DHT_BOOTSTRAP_NODE_1 = "router.bittorrent.com"
        private const val DHT_BOOTSTRAP_NODE_2 = "router.utorrent.com"
        private const val DHT_BOOTSTRAP_NODE_3 = "dht.transmissionbt.com"
        private const val DHT_BOOTSTRAP_PORT = 6881
        private const val STATS_UPDATE_INTERVAL_MS = 1000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sessionManager: SessionManager? = null

    // Track active torrents by info hash
    private val activeTorrents = ConcurrentHashMap<String, TorrentHandle>()

    // Observable state for each torrent
    private val _torrentStates = MutableStateFlow<Map<String, TorrentInfo>>(emptyMap())
    val torrentStates: StateFlow<Map<String, TorrentInfo>> = _torrentStates.asStateFlow()

    // Session-wide stats
    private val _sessionStats = MutableStateFlow(SessionStats())
    val sessionStats: StateFlow<SessionStats> = _sessionStats.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // Save directory for downloaded torrent files
    private val saveDirectory: File
        get() {
            val externalDir = context.getExternalFilesDir(null)
            val dir = if (externalDir != null) {
                File(externalDir, "DeepLoader/Torrents")
            } else {
                File(context.filesDir, "DeepLoader/Torrents")
            }
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "Failed to create save directory: ${dir.absolutePath}")
            }
            return dir
        }

    /**
     * Initialize and start the libtorrent session.
     * Must be called before adding any torrents.
     */
    fun start() {
        if (sessionManager != null) return

        scope.launch {
            try {
                val settings = SettingsPack()

                // Network settings
                settings.setString(settings_pack.string_types.listen_interfaces.swigValue(), "0.0.0.0:6881,[::]:6881")
                settings.setInteger(settings_pack.int_types.active_downloads.swigValue(), 5)
                settings.setInteger(settings_pack.int_types.active_seeds.swigValue(), 3)
                settings.setInteger(settings_pack.int_types.connections_limit.swigValue(), 200)
                settings.setInteger(settings_pack.int_types.download_rate_limit.swigValue(), 0) // Unlimited
                settings.setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), 0)   // Unlimited

                // Enable DHT, PEX, LSD for peer discovery
                settings.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
                settings.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)

                // User agent — blend in with popular clients
                settings.setString(settings_pack.string_types.user_agent.swigValue(), "DeepLoader/1.0 libtorrent/2.1")

                val params = SessionParams(settings)
                val session = SessionManager(false) // useInternalAlertLoop = false

                session.addListener(createAlertListener())
                session.start(params)

                // Bootstrap DHT from well-known nodes
                settings.setString(settings_pack.string_types.dht_bootstrap_nodes.swigValue(), "$DHT_BOOTSTRAP_NODE_1:$DHT_BOOTSTRAP_PORT,$DHT_BOOTSTRAP_NODE_2:$DHT_BOOTSTRAP_PORT,$DHT_BOOTSTRAP_NODE_3:$DHT_BOOTSTRAP_PORT")

                sessionManager = session
                _isRunning.value = true
                Log.i(TAG, "TorrentEngine started")

                // Start periodic stats update loop
                startStatsLoop()

            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start TorrentEngine", e)
                _isRunning.value = false
            }
        }
    }

    /**
     * Stop the libtorrent session and clean up all resources.
     */
    fun stop() {
        scope.launch {
            try {
                activeTorrents.clear()
                sessionManager?.stop()
                sessionManager = null
                _isRunning.value = false
                _torrentStates.value = emptyMap()
                Log.i(TAG, "TorrentEngine stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping TorrentEngine", e)
            }
        }
    }

    /**
     * Add a torrent by magnet URI.
     *
     * @param magnetUri Full magnet link (magnet:?xt=urn:btih:...)
     * @param sequential If true, downloads pieces in order for video streaming
     * @return The info hash string for tracking this torrent
     */
    fun addTorrent(magnetUri: String, sequential: Boolean = false): String? {
        val session = sessionManager ?: run {
            Log.w(TAG, "Session not started, starting now...")
            start()
            return null
        }

        return try {
            // Extract info hash from magnet URI
            val infoHash = extractInfoHash(magnetUri) ?: return null

            // Check if already added
            if (activeTorrents.containsKey(infoHash)) {
                Log.i(TAG, "Torrent already active: $infoHash")
                return infoHash
            }

            session.download(magnetUri, saveDirectory, org.libtorrent4j.swig.torrent_flags_t())

            // Initial state entry
            val currentStates = _torrentStates.value.toMutableMap()
            currentStates[infoHash] = TorrentInfo(
                name = "Resolving magnet link...",
                magnetUri = magnetUri,
                infoHash = infoHash,
                status = TorrentStatus.CHECKING,
                isStreaming = sequential
            )
            _torrentStates.value = currentStates

            Log.i(TAG, "Torrent added: $infoHash (sequential=$sequential)")
            infoHash

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to add torrent: ${e.message}")
            null
        }
    }

    /**
     * Remove a torrent and optionally delete its downloaded files.
     */
    fun removeTorrent(infoHash: String, deleteFiles: Boolean = false) {
        val handle = activeTorrents.remove(infoHash) ?: return

        try {
            val session = sessionManager ?: return
            if (deleteFiles) {
                session.remove(handle, org.libtorrent4j.SessionHandle.DELETE_FILES)
            } else {
                session.remove(handle)
            }

            val currentStates = _torrentStates.value.toMutableMap()
            currentStates.remove(infoHash)
            _torrentStates.value = currentStates

            Log.i(TAG, "Torrent removed: $infoHash (deleteFiles=$deleteFiles)")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing torrent $infoHash", e)
        }
    }

    /**
     * Pause a torrent.
     */
    fun pauseTorrent(infoHash: String) {
        activeTorrents[infoHash]?.pause()
        updateTorrentState(infoHash) { it.copy(status = TorrentStatus.PAUSED) }
    }

    /**
     * Resume a paused torrent.
     */
    fun resumeTorrent(infoHash: String) {
        activeTorrents[infoHash]?.resume()
        updateTorrentState(infoHash) { it.copy(status = TorrentStatus.DOWNLOADING) }
    }

    /**
     * Enable/disable sequential downloading for a torrent.
     * Sequential mode downloads pieces in order — required for streaming.
     */
    fun setSequentialMode(infoHash: String, enabled: Boolean) {
        activeTorrents[infoHash]?.setFlags(
            if (enabled) org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD
            else org.libtorrent4j.swig.torrent_flags_t(),
            org.libtorrent4j.TorrentFlags.SEQUENTIAL_DOWNLOAD
        )
        updateTorrentState(infoHash) { it.copy(isStreaming = enabled) }
    }

    /**
     * Set file priorities within a multi-file torrent.
     * Priority 0 = skip, 1 = low, 4 = normal, 7 = highest
     */
    fun setFilePriority(infoHash: String, fileIndex: Int, priority: Int) {
        activeTorrents[infoHash]?.filePriority(fileIndex, org.libtorrent4j.Priority.fromSwig(priority))
    }

    /**
     * Get the file path of a specific file in the torrent.
     * Used for streaming — point ExoPlayer at this path.
     */
    fun getFilePath(infoHash: String, fileIndex: Int): String? {
        val handle = activeTorrents[infoHash] ?: return null
        val info = handle.torrentFile() ?: return null
        val files = info.files()
        if (fileIndex >= files.numFiles()) return null
        return File(saveDirectory, files.filePath(fileIndex)).absolutePath
    }

    /**
     * Set download/upload bandwidth limits.
     * 0 = unlimited, values in bytes/sec.
     */
    fun setBandwidthLimits(downloadLimit: Int, uploadLimit: Int) {
        val settings = sessionManager?.settings() ?: return
        settings.setInteger(settings_pack.int_types.download_rate_limit.swigValue(), downloadLimit)
        settings.setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), uploadLimit)
        sessionManager?.applySettings(settings)
    }

    // ─── Private: Alert Handling ───

    private fun createAlertListener(): AlertListener {
        return object : AlertListener {
            override fun types(): IntArray = intArrayOf(
                AlertType.ADD_TORRENT.swig(),
                AlertType.METADATA_RECEIVED.swig(),
                AlertType.PIECE_FINISHED.swig(),
                AlertType.TORRENT_FINISHED.swig()
            )

            override fun alert(alert: Alert<*>) {
                try {
                    when (alert) {
                        is AddTorrentAlert -> onTorrentAdded(alert)
                        is MetadataReceivedAlert -> onMetadataReceived(alert)
                        is PieceFinishedAlert -> onPieceFinished(alert)
                        is TorrentFinishedAlert -> onTorrentFinished(alert)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Alert listener error", e)
                }
            }
        }
    }

    private fun onTorrentAdded(alert: AddTorrentAlert) {
        val handle = alert.handle()
        val infoHash = handle.infoHash().toHex()
        activeTorrents[infoHash] = handle
        Log.i(TAG, "Torrent handle acquired: $infoHash")
    }

    private fun onMetadataReceived(alert: MetadataReceivedAlert) {
        val handle = alert.handle()
        val infoHash = handle.infoHash().toHex()
        val torrentInfo = handle.torrentFile() ?: return
        val files = torrentInfo.files()

        val fileList = (0 until files.numFiles()).map { i ->
            TorrentFile(
                path = files.filePath(i),
                size = files.fileSize(i),
                priority = 4 // Normal by default
            )
        }

        updateTorrentState(infoHash) {
            it.copy(
                name = torrentInfo.name(),
                size = torrentInfo.totalSize(),
                files = fileList,
                status = TorrentStatus.DOWNLOADING
            )
        }

        // Enable sequential if streaming was requested
        val state = _torrentStates.value[infoHash]
        if (state?.isStreaming == true) {
            setSequentialMode(infoHash, true)
        }

        Log.i(TAG, "Metadata received: ${torrentInfo.name()} (${fileList.size} files, ${torrentInfo.totalSize()} bytes)")
    }

    private fun onPieceFinished(alert: PieceFinishedAlert) {
        // Progress updates handled by the stats loop
    }

    private fun onTorrentFinished(alert: TorrentFinishedAlert) {
        val infoHash = alert.handle().infoHash().toHex()
        updateTorrentState(infoHash) {
            it.copy(
                status = TorrentStatus.COMPLETED,
                downloadProgress = 1.0f
            )
        }
        Log.i(TAG, "Torrent completed: $infoHash")
    }

    // ─── Private: Stats Loop ───

    /**
     * Periodically polls all active torrent handles for progress/speed stats.
     * Updates every 1 second.
     */
    private fun startStatsLoop() {
        scope.launch {
            while (isActive && _isRunning.value) {
                try {
                    var totalDownSpeed = 0L
                    var totalUpSpeed = 0L

                    activeTorrents.forEach { (infoHash, handle) ->
                        if (!handle.isValid) return@forEach

                        val status = handle.status()
                        val progress = status.progress()
                        val downSpeed = status.downloadRate().toLong()
                        val upSpeed = status.uploadRate().toLong()
                        val peers = status.numPeers()
                        val seeders = status.numSeeds()

                        totalDownSpeed += downSpeed
                        totalUpSpeed += upSpeed

                        val torrentStatus = when {
                            status.flags().and_(org.libtorrent4j.TorrentFlags.PAUSED).non_zero() -> TorrentStatus.PAUSED
                            status.isFinished() -> TorrentStatus.COMPLETED
                            status.isSeeding -> TorrentStatus.SEEDING
                            progress > 0f -> TorrentStatus.DOWNLOADING
                            else -> TorrentStatus.CHECKING
                        }

                        updateTorrentState(infoHash) {
                            it.copy(
                                downloadProgress = progress,
                                downloadSpeed = downSpeed,
                                uploadSpeed = upSpeed,
                                connectedPeers = peers,
                                seeders = seeders,
                                status = torrentStatus
                            )
                        }
                    }

                    _sessionStats.value = SessionStats(
                        activeTorrents = activeTorrents.size,
                        totalDownloadSpeed = totalDownSpeed,
                        totalUploadSpeed = totalUpSpeed,
                        dhtNodes = sessionManager?.stats()?.dhtNodes()?.toLong() ?: 0
                    )

                } catch (e: Throwable) {
                    // Stats update failure is non-fatal
                }

                delay(STATS_UPDATE_INTERVAL_MS)
            }
        }
    }

    // ─── Helpers ───

    private fun updateTorrentState(infoHash: String, transform: (TorrentInfo) -> TorrentInfo) {
        val currentStates = _torrentStates.value.toMutableMap()
        val existing = currentStates[infoHash] ?: return
        currentStates[infoHash] = transform(existing)
        _torrentStates.value = currentStates
    }

    private fun extractInfoHash(magnetUri: String): String? {
        val regex = Regex("btih:([a-fA-F0-9]{40})")
        return regex.find(magnetUri)?.groupValues?.get(1)?.lowercase()
    }
}

/**
 * Session-wide torrent stats.
 */
data class SessionStats(
    val activeTorrents: Int = 0,
    val totalDownloadSpeed: Long = 0,
    val totalUploadSpeed: Long = 0,
    val dhtNodes: Long = 0
)
