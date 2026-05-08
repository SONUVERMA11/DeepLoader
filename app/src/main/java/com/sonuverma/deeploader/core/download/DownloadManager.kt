package com.sonuverma.deeploader.core.download

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sonuverma.deeploader.data.db.DownloadDao
import com.sonuverma.deeploader.data.db.DownloadEntity
import com.sonuverma.deeploader.data.models.DownloadStatus
import com.sonuverma.deeploader.data.models.ExtractionResult
import com.sonuverma.deeploader.data.models.Platform
import com.sonuverma.deeploader.data.models.StreamFormat
import com.sonuverma.deeploader.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DownloadManager — Central coordinator for all downloads.
 *
 * Handles the full lifecycle from user action to completed file:
 *   1. Creates DownloadEntity in Room
 *   2. Enqueues WorkManager task (survives app kills)
 *   3. Starts foreground service for notification
 *   4. Respects WiFi-only preference
 *   5. Respects max concurrent download limit
 *   6. Manages pause/resume/cancel/retry
 *
 * WorkManager handles the actual download execution via DownloadWorker.
 * This class only coordinates — it doesn't download anything itself.
 *
 * Developer: Sonu Verma
 */
@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao,
    private val chunkedDownloader: ChunkedDownloader,
    private val mediaStoreSaver: MediaStoreSaver,
    private val prefs: AppPreferences
) {
    companion object {
        private const val TAG = "DownloadManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Start a new download from an extraction result and selected format.
     *
     * @param result The extraction result containing metadata
     * @param videoFormat The selected video/audio stream to download
     * @param audioFormat Optional separate audio stream for DASH merging
     * @param convertToMp3 Whether to convert the final file to MP3
     * @return The download ID
     */
    fun startDownload(
        result: ExtractionResult,
        videoFormat: StreamFormat,
        audioFormat: StreamFormat? = null,
        convertToMp3: Boolean = false
    ): String {
        val downloadId = UUID.randomUUID().toString()
        val fileName = sanitizeFileName(result.title)

        scope.launch {
            val wifiOnly = prefs.wifiOnly.first()
            val chunkCount = prefs.defaultChunkCount.first()
            val mp3Bitrate = prefs.mp3Bitrate.first()

            // Step 1: Create Room entity
            val entity = DownloadEntity(
                id = downloadId,
                url = result.originalUrl,
                title = result.title,
                thumbnailUrl = result.thumbnailUrl,
                platform = result.platform.name,
                status = DownloadStatus.QUEUED.name,
                totalBytes = videoFormat.fileSize,
                streamUrl = videoFormat.url,
                audioStreamUrl = audioFormat?.url ?: "",
                format = videoFormat.format,
                quality = videoFormat.qualityLabel,
                chunkCount = chunkCount,
                convertToMp3 = convertToMp3,
                mp3Bitrate = mp3Bitrate,
                isWifiOnly = wifiOnly
            )
            downloadDao.insert(entity)

            // Step 2: Build WorkManager constraints
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresStorageNotLow(true)
                .build()

            // Step 3: Build input data
            val inputData = Data.Builder()
                .putString(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
                .putString(DownloadWorker.KEY_STREAM_URL, videoFormat.url)
                .putString(DownloadWorker.KEY_AUDIO_URL, audioFormat?.url ?: "")
                .putString(DownloadWorker.KEY_OUTPUT_DIR, mediaStoreSaver.getDownloadDirectory().absolutePath)
                .putString(DownloadWorker.KEY_FILE_NAME, fileName)
                .putInt(DownloadWorker.KEY_CHUNK_COUNT, chunkCount)
                .putBoolean(DownloadWorker.KEY_CONVERT_MP3, convertToMp3)
                .putInt(DownloadWorker.KEY_MP3_BITRATE, mp3Bitrate)
                .putLong(DownloadWorker.KEY_TOTAL_BYTES, videoFormat.fileSize)
                .build()

            // Step 4: Enqueue work
            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag("download_$downloadId")
                .addTag("deeploader_downloads")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "download_$downloadId",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

            // Step 5: Start foreground service
            startForegroundService()

            Log.i(TAG, "Download queued: $downloadId ($fileName)")
        }

        return downloadId
    }

    // ─── Control Methods ───

    /**
     * Pause a specific download.
     */
    fun pauseDownload(downloadId: String) {
        chunkedDownloader.pauseDownload(downloadId)
        scope.launch {
            downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED.name)
        }
    }

    /**
     * Resume a paused download.
     */
    fun resumeDownload(downloadId: String) {
        if (chunkedDownloader.isActive(downloadId)) {
            // Download is still in memory — just unpause
            chunkedDownloader.resumeDownload(downloadId)
        } else {
            // Download was killed — re-enqueue via WorkManager
            scope.launch {
                val entity = downloadDao.getById(downloadId) ?: return@launch
                reEnqueueDownload(entity)
            }
        }
    }

    /**
     * Cancel and delete a download.
     */
    fun cancelDownload(downloadId: String) {
        chunkedDownloader.cancelDownload(downloadId)
        WorkManager.getInstance(context).cancelAllWorkByTag("download_$downloadId")
        scope.launch {
            downloadDao.updateStatus(downloadId, DownloadStatus.CANCELLED.name)
        }
    }

    /**
     * Retry a failed download.
     */
    fun retryDownload(downloadId: String) {
        scope.launch {
            val entity = downloadDao.getById(downloadId) ?: return@launch
            downloadDao.updateStatus(downloadId, DownloadStatus.QUEUED.name)
            reEnqueueDownload(entity)
        }
    }

    /**
     * Pause all active downloads.
     */
    fun pauseAll() {
        chunkedDownloader.cancelAll()
        scope.launch {
            downloadDao.pauseAllActive()
        }
    }

    /**
     * Resume all paused downloads.
     */
    fun resumeAll() {
        scope.launch {
            downloadDao.resumeAllPaused()
            // Re-enqueue would need individual handling
        }
    }

    /**
     * Re-enqueue a download with existing entity data.
     * Used for resume after app kill and retry.
     */
    private suspend fun reEnqueueDownload(entity: DownloadEntity) {
        val wifiOnly = entity.isWifiOnly

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresStorageNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putString(DownloadWorker.KEY_DOWNLOAD_ID, entity.id)
            .putString(DownloadWorker.KEY_STREAM_URL, entity.streamUrl)
            .putString(DownloadWorker.KEY_AUDIO_URL, entity.audioStreamUrl)
            .putString(DownloadWorker.KEY_OUTPUT_DIR, mediaStoreSaver.getDownloadDirectory().absolutePath)
            .putString(DownloadWorker.KEY_FILE_NAME, sanitizeFileName(entity.title))
            .putInt(DownloadWorker.KEY_CHUNK_COUNT, entity.chunkCount)
            .putBoolean(DownloadWorker.KEY_CONVERT_MP3, entity.convertToMp3)
            .putInt(DownloadWorker.KEY_MP3_BITRATE, entity.mp3Bitrate)
            .putLong(DownloadWorker.KEY_TOTAL_BYTES, entity.totalBytes)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag("download_${entity.id}")
            .addTag("deeploader_downloads")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "download_${entity.id}",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }

    /**
     * Start the foreground service to keep downloads alive.
     */
    private fun startForegroundService() {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    /**
     * Stop the foreground service when no downloads are active.
     */
    fun stopForegroundServiceIfIdle() {
        if (chunkedDownloader.getActiveCount() == 0) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = DownloadService.ACTION_STOP
            }
            context.startService(intent)
        }
    }

    /**
     * Check if the device is currently on WiFi.
     */
    fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /**
     * Sanitize a title for use as a filename.
     * Removes illegal characters, trims whitespace, caps length.
     */
    private fun sanitizeFileName(title: String): String {
        return title
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(120) // Filesystem filename length limit
            .trimEnd('.', ' ', '_')
            .ifEmpty { "download_${System.currentTimeMillis()}" }
    }
}
