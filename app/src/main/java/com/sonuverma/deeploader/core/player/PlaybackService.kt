package com.sonuverma.deeploader.core.player

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * PlaybackService — Media3 MediaSessionService for background audio playback.
 *
 * Provides:
 *   - Lock screen playback controls (play/pause/seek)
 *   - Bluetooth/headphone media button support
 *   - Notification with album art and controls
 *   - Background audio when app is minimized
 *   - Integration with Android Auto (future)
 *
 * The service binds to the singleton PlayerManager's ExoPlayer instance,
 * so there's only one player across the entire app.
 *
 * Developer: Sonu Verma
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerManager: PlayerManager

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSession.Builder(this, playerManager.player)
            .setCallback(object : MediaSession.Callback {
                // Accept all connections (single-app use case)
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: run {
            stopSelf()
            return
        }

        // If not playing, stop the service
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
