package com.sonuverma.deeploader.core.download

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.sonuverma.deeploader.DeepLoaderApp
import com.sonuverma.deeploader.MainActivity
import com.sonuverma.deeploader.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NotificationHelper — Shows download completion and error notifications.
 *
 * Notification types:
 *   - Download complete (green checkmark, tap to open file)
 *   - Download failed (red warning, tap to retry)
 *   - Batch complete (summary when multiple downloads finish)
 *
 * Respects the "Notification Sound" preference setting.
 *
 * Developer: Sonu Verma
 */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences
) {
    companion object {
        private const val NOTIFICATION_COMPLETE_BASE = 5000
        private const val NOTIFICATION_ERROR_BASE = 6000
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Show download complete notification.
     * Tapping opens the downloaded file in the appropriate app.
     */
    fun showCompleteNotification(downloadId: String, title: String, filePath: String, mimeType: String) {
        val isSilent = runBlocking { !prefs.notificationSound.first() }
        val notificationId = NOTIFICATION_COMPLETE_BASE + downloadId.hashCode().and(0xFFF)

        // Intent to open the downloaded file
        val file = File(filePath)
        val openIntent = if (file.exists()) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(file), mimeType)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
        } else {
            Intent(context, MainActivity::class.java)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DeepLoaderApp.CHANNEL_COMPLETED)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setSilent(isSilent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Show download error notification.
     * Tapping opens the app for retry.
     */
    fun showErrorNotification(downloadId: String, title: String, errorMessage: String) {
        val notificationId = NOTIFICATION_ERROR_BASE + downloadId.hashCode().and(0xFFF)

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DeepLoaderApp.CHANNEL_COMPLETED)
            .setContentTitle("Download Failed")
            .setContentText("$title — $errorMessage")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Cancel all notifications for a download.
     */
    fun cancelNotification(downloadId: String) {
        val completeId = NOTIFICATION_COMPLETE_BASE + downloadId.hashCode().and(0xFFF)
        val errorId = NOTIFICATION_ERROR_BASE + downloadId.hashCode().and(0xFFF)
        notificationManager.cancel(completeId)
        notificationManager.cancel(errorId)
    }
}
