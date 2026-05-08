package com.sonuverma.deeploader.core.download

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sonuverma.deeploader.DeepLoaderApp
import com.sonuverma.deeploader.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service for active downloads.
 * Keeps downloads running even when the app is in the background.
 * Shows persistent notification with progress and pause/cancel actions.
 *
 * Now integrated with DownloadManager for real pause/cancel control.
 *
 * Developer: Sonu Verma
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadManager: DownloadManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForegroundService()
            ACTION_PAUSE_ALL -> pauseAllDownloads()
            ACTION_CANCEL_ALL -> cancelAllDownloads()
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, DownloadService::class.java).apply { action = ACTION_PAUSE_ALL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getService(
            this, 2,
            Intent(this, DownloadService::class.java).apply { action = ACTION_CANCEL_ALL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, DeepLoaderApp.CHANNEL_DOWNLOADS)
            .setContentTitle("DeepLoader")
            .setContentText("Downloads in progress...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause All", pauseIntent)
            .addAction(android.R.drawable.ic_delete, "Cancel All", cancelIntent)
            .build()
    }

    private fun pauseAllDownloads() {
        downloadManager.pauseAll()
    }

    private fun cancelAllDownloads() {
        downloadManager.pauseAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ACTION_START = "com.sonuverma.deeploader.START_DOWNLOAD"
        const val ACTION_PAUSE_ALL = "com.sonuverma.deeploader.PAUSE_ALL"
        const val ACTION_CANCEL_ALL = "com.sonuverma.deeploader.CANCEL_ALL"
        const val ACTION_STOP = "com.sonuverma.deeploader.STOP"
        const val NOTIFICATION_ID = 1001
    }
}
