package com.sonuverma.deeploader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * DeepLoader Application class.
 * Initializes Hilt DI, notification channels, and WorkManager with custom configuration.
 * Developer: Sonu Verma
 */
@HiltAndroidApp
class DeepLoaderApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        setupCrashLogger()
        createNotificationChannels()
    }

    private fun setupCrashLogger() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val crashFile = java.io.File(getExternalFilesDir(null), "crash.log")
                val writer = java.io.FileWriter(crashFile, true)
                writer.append("\n\n--- CRASH LOG [${java.util.Date()}] ---\n")
                val pw = java.io.PrintWriter(writer)
                throwable.printStackTrace(pw)
                pw.flush()
                writer.close()
            } catch (e: Exception) {
                // Ignore
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Creates notification channels required for Android 8.0+.
     * Three channels: active downloads, completed downloads, and app updates.
     */
    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Active downloads channel — high priority for ongoing download progress
        val downloadChannel = NotificationChannel(
            CHANNEL_DOWNLOADS,
            "Active Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress for active downloads"
            setShowBadge(false)
        }

        // Completed downloads channel — default priority for completion alerts
        val completedChannel = NotificationChannel(
            CHANNEL_COMPLETED,
            "Completed Downloads",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when downloads complete"
            setShowBadge(true)
        }

        // App update channel — low priority for background update checks
        val updateChannel = NotificationChannel(
            CHANNEL_UPDATES,
            "App Updates",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notifications for app and component updates"
            setShowBadge(false)
        }

        manager.createNotificationChannels(
            listOf(downloadChannel, completedChannel, updateChannel)
        )
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "deeploader_downloads"
        const val CHANNEL_COMPLETED = "deeploader_completed"
        const val CHANNEL_UPDATES = "deeploader_updates"
    }
}
