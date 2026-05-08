package com.sonuverma.deeploader.core.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PlayerManager — Centralized ExoPlayer/Media3 manager.
 *
 *
 * Handles:
 *   - Stream URL playback (direct HTTP URLs from extraction)
 *   - Local file playback (downloaded files)
 *   - Torrent streaming (local file from sequential download)
 *   - Background audio mode (continues when app is backgrounded)
 *   - Playback state tracking via StateFlow
 *   - Speed/quality controls
 *   - Subtitle loading
 *
 * The ExoPlayer instance is singleton — survives navigation between screens.
 * The PlaybackService handles media session for lock screen controls.
 *
 * Developer: Sonu Verma
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Singleton
class PlayerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PlayerManager"
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36"
    }

    private var _player: ExoPlayer? = null
    val player: ExoPlayer
        get() = _player ?: createPlayer().also { _player = it }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ─── Playback State ───

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _isBackgroundMode = MutableStateFlow(false)
    val isBackgroundMode: StateFlow<Boolean> = _isBackgroundMode.asStateFlow()

    /**
     * Create and configure the ExoPlayer instance.
     */
    private fun createPlayer(): ExoPlayer {
        // Custom HTTP data source with proper User-Agent
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // Pause when headphones disconnected
            .build()

        player.addListener(createPlayerListener())

        // Start position tracking loop
        startPositionTracking()

        Log.i(TAG, "ExoPlayer created")
        return player
    }

    // ─── Playback Controls ───

    /**
     * Play a stream URL (from extraction result).
     */
    fun playStreamUrl(
        url: String,
        title: String = "",
        thumbnailUrl: String = "",
        startPositionMs: Long = 0
    ) {
        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtworkUri(if (thumbnailUrl.isNotEmpty()) Uri.parse(thumbnailUrl) else null)
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()

        _playbackState.value = _playbackState.value.copy(
            title = title,
            thumbnailUrl = thumbnailUrl,
            sourceType = SourceType.STREAM
        )

        Log.i(TAG, "Playing stream: $title ($url)")
    }

    /**
     * Play a local file (downloaded or from torrent).
     */
    fun playLocalFile(
        filePath: String,
        title: String = "",
        thumbnailUrl: String = "",
        startPositionMs: Long = 0
    ) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.e(TAG, "File not found: $filePath")
            return
        }

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title.ifEmpty { file.nameWithoutExtension })
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.play()

        _playbackState.value = _playbackState.value.copy(
            title = title.ifEmpty { file.nameWithoutExtension },
            sourceType = SourceType.LOCAL
        )

        Log.i(TAG, "Playing local file: $filePath")
    }

    /**
     * Play a torrent file that's being sequentially downloaded.
     * ExoPlayer handles partial files gracefully if pieces are sequential.
     */
    fun playTorrentStream(
        filePath: String,
        title: String = "",
        startPositionMs: Long = 0
    ) {
        playLocalFile(filePath, title, startPositionMs = startPositionMs)
        _playbackState.value = _playbackState.value.copy(sourceType = SourceType.TORRENT)
    }

    fun play() = player.play()
    fun pause() = player.pause()

    fun togglePlayPause() {
        if (player.isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun seekForward(ms: Long = 10_000) {
        player.seekTo(player.currentPosition + ms)
    }

    fun seekBack(ms: Long = 10_000) {
        player.seekTo(maxOf(0, player.currentPosition - ms))
    }

    fun setPlaybackSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    fun setRepeatMode(mode: Int) {
        player.repeatMode = mode
    }

    /**
     * Enable background audio mode — video stops but audio continues.
     */
    fun setBackgroundMode(enabled: Boolean) {
        _isBackgroundMode.value = enabled
        if (enabled) {
            // Disable video rendering to save battery
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setMaxVideoSizeSd()
                .build()
        }
    }

    /**
     * Stop playback and release player resources.
     */
    fun stop() {
        player.stop()
        _playbackState.value = PlaybackState()
    }

    /**
     * Release the player completely (call in onDestroy).
     */
    fun release() {
        _player?.release()
        _player = null
        _playbackState.value = PlaybackState()
        Log.i(TAG, "ExoPlayer released")
    }

    // ─── Listener ───

    private fun createPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val stateStr = when (state) {
                    Player.STATE_IDLE -> PlayerState.IDLE
                    Player.STATE_BUFFERING -> PlayerState.BUFFERING
                    Player.STATE_READY -> if (player.playWhenReady) PlayerState.PLAYING else PlayerState.PAUSED
                    Player.STATE_ENDED -> PlayerState.ENDED
                    else -> PlayerState.IDLE
                }
                _playbackState.value = _playbackState.value.copy(state = stateStr)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.value = _playbackState.value.copy(
                    state = if (isPlaying) PlayerState.PLAYING else PlayerState.PAUSED,
                    isPlaying = isPlaying
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                _playbackState.value = _playbackState.value.copy(
                    state = PlayerState.ERROR,
                    errorMessage = error.message ?: "Playback error"
                )
                Log.e(TAG, "Player error: ${error.message}")
            }
        }
    }

    // ─── Position Tracking ───

    private fun startPositionTracking() {
        scope.launch {
            while (isActive) {
                val p = _player
                if (p != null && p.isPlaying) {
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = p.currentPosition,
                        durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
                        bufferedPositionMs = p.bufferedPosition
                    )
                }
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }
}

// ─── State Models ───

data class PlaybackState(
    val state: PlayerState = PlayerState.IDLE,
    val isPlaying: Boolean = false,
    val title: String = "",
    val thumbnailUrl: String = "",
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val sourceType: SourceType = SourceType.STREAM,
    val errorMessage: String = ""
) {
    val progress: Float
        get() = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f

    val displayPosition: String
        get() = formatDuration(currentPositionMs)

    val displayDuration: String
        get() = formatDuration(durationMs)

    val displayRemaining: String
        get() = formatDuration(maxOf(0, durationMs - currentPositionMs))
}

enum class PlayerState {
    IDLE, BUFFERING, PLAYING, PAUSED, ENDED, ERROR
}

enum class SourceType {
    STREAM, LOCAL, TORRENT
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
