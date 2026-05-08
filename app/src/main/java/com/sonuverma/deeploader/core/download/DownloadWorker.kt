package com.sonuverma.deeploader.core.download

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.core.app.NotificationCompat
import com.sonuverma.deeploader.DeepLoaderApp
import com.sonuverma.deeploader.data.db.DownloadDao
import com.sonuverma.deeploader.data.models.DownloadStatus
import com.sonuverma.deeploader.data.prefs.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * DownloadWorker — WorkManager-backed persistent download task.
 *
 * Runs as a foreground worker with a persistent notification showing
 * real-time download progress. Survives app kills, swipe-away, and
 * device reboots (WorkManager re-enqueues automatically).
 *
 * Each worker handles ONE download job. The DownloadManager queues
 * multiple workers respecting the max concurrent download limit.
 *
 * Input data:
 *   - DOWNLOAD_ID: Room entity ID
 *   - STREAM_URL: Direct download URL
 *   - AUDIO_URL: Optional separate audio URL (for DASH merge)
 *   - OUTPUT_DIR: Target directory path
 *   - FILE_NAME: Output filename
 *   - CHUNK_COUNT: Number of parallel chunks
 *   - CONVERT_MP3: Whether to convert to MP3 after download
 *   - MP3_BITRATE: Target MP3 bitrate (128/192/320)
 *
 * Developer: Sonu Verma
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val chunkedDownloader: ChunkedDownloader,
    private val audioVideoMerger: AudioVideoMerger,
    private val mediaStoreSaver: MediaStoreSaver,
    private val downloadDao: DownloadDao,
    private val prefs: AppPreferences
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "DownloadWorker"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_STREAM_URL = "stream_url"
        const val KEY_AUDIO_URL = "audio_url"
        const val KEY_OUTPUT_DIR = "output_dir"
        const val KEY_FILE_NAME = "file_name"
        const val KEY_CHUNK_COUNT = "chunk_count"
        const val KEY_CONVERT_MP3 = "convert_mp3"
        const val KEY_MP3_BITRATE = "mp3_bitrate"
        const val KEY_TOTAL_BYTES = "total_bytes"
        private const val NOTIFICATION_ID_BASE = 2000
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val streamUrl = inputData.getString(KEY_STREAM_URL) ?: return@withContext Result.failure()
        val audioUrl = inputData.getString(KEY_AUDIO_URL)
        val outputDir = inputData.getString(KEY_OUTPUT_DIR) ?: getDefaultOutputDir()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "download_${System.currentTimeMillis()}"
        val chunkCount = inputData.getInt(KEY_CHUNK_COUNT, 16)
        val convertMp3 = inputData.getBoolean(KEY_CONVERT_MP3, false)
        val mp3Bitrate = inputData.getInt(KEY_MP3_BITRATE, 320)

        val notificationId = NOTIFICATION_ID_BASE + downloadId.hashCode().and(0xFFF)

        try {
            // Show foreground notification
            setForeground(createForegroundInfo(downloadId, fileName, 0, notificationId))

            // Update status to DOWNLOADING
            downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING.name)

            // Get existing chunk offsets for resume
            val entity = downloadDao.getById(downloadId)
            val existingOffsets = if (entity != null) {
                chunkedDownloader.parseChunkOffsets(entity.chunkOffsetsJson)
            } else {
                emptyList()
            }

            // Step 1: Download main stream (video or combined)
            val videoFile = File(outputDir, "${fileName}_video.tmp")
            var lastProgress = 0
            var totalBytes = 0L

            chunkedDownloader.startDownload(
                downloadId = downloadId,
                url = streamUrl,
                outputFile = videoFile,
                chunkCount = chunkCount,
                existingOffsets = existingOffsets,
                onProgress = { downloaded, total, speed ->
                    totalBytes = total
                    val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    if (progress != lastProgress) {
                        lastProgress = progress
                        // Update notification
                        setForegroundAsync(createForegroundInfo(downloadId, fileName, progress, notificationId))
                        // Update Room
                        kotlinx.coroutines.runBlocking {
                            downloadDao.updateProgress(downloadId, downloaded, DownloadStatus.DOWNLOADING.name)
                        }
                    }
                },
                onComplete = { /* Video download complete */ },
                onError = { error -> throw DownloadException(error) }
            )

            // Wait for download to complete — the callback approach runs in background
            // So we wait by checking if the file is complete
            waitForCompletion(downloadId, videoFile, totalBytes)

            // Step 2: Download audio stream if DASH (video-only + separate audio)
            var finalFile = videoFile
            if (audioUrl != null && audioUrl.isNotEmpty()) {
                downloadDao.updateStatus(downloadId, DownloadStatus.MERGING.name)
                setForeground(createForegroundInfo(downloadId, "Downloading audio...", -1, notificationId))

                val audioFile = File(outputDir, "${fileName}_audio.tmp")
                chunkedDownloader.startDownload(
                    downloadId = "${downloadId}_audio",
                    url = audioUrl,
                    outputFile = audioFile,
                    chunkCount = 4, // Audio files are small — 4 chunks is plenty
                    onComplete = { /* Audio done */ },
                    onError = { error -> throw DownloadException("Audio download failed: $error") }
                )

                waitForCompletion("${downloadId}_audio", audioFile, 0)

                // Step 3: Merge video + audio
                setForeground(createForegroundInfo(downloadId, "Merging audio & video...", -1, notificationId))
                val mergedFile = File(outputDir, "$fileName.mp4")
                audioVideoMerger.merge(videoFile, audioFile, mergedFile)

                // Cleanup temp files
                videoFile.delete()
                audioFile.delete()
                finalFile = mergedFile
            } else {
                // Rename temp file to final name
                val ext = guessExtension(streamUrl, convertMp3)
                val renamedFile = File(outputDir, "$fileName.$ext")
                videoFile.renameTo(renamedFile)
                finalFile = renamedFile
            }

            // Step 4: Convert to MP3 if requested
            if (convertMp3) {
                downloadDao.updateStatus(downloadId, DownloadStatus.CONVERTING.name)
                setForeground(createForegroundInfo(downloadId, "Converting to MP3...", -1, notificationId))

                val mp3File = File(outputDir, "$fileName.mp3")
                audioVideoMerger.convertToMp3(finalFile, mp3File, mp3Bitrate)

                if (finalFile != mp3File) finalFile.delete()
                finalFile = mp3File
            }

            // Step 5: Save to gallery/MediaStore
            val saveToGallery = prefs.saveToGallery.first()
            if (saveToGallery) {
                downloadDao.updateStatus(downloadId, DownloadStatus.SAVING.name)
                setForeground(createForegroundInfo(downloadId, "Saving to gallery...", -1, notificationId))
                mediaStoreSaver.saveToMediaStore(finalFile, fileName, getMimeType(finalFile))
            }

            // Step 6: Mark complete
            downloadDao.markCompleted(downloadId, finalFile.absolutePath)

            Log.i(TAG, "Download $downloadId completed: ${finalFile.absolutePath}")
            Result.success(
                Data.Builder()
                    .putString("file_path", finalFile.absolutePath)
                    .putString("download_id", downloadId)
                    .build()
            )

        } catch (e: Exception) {
            Log.e(TAG, "Download $downloadId failed", e)
            downloadDao.updateStatusWithError(
                downloadId,
                DownloadStatus.FAILED.name,
                e.message ?: "Unknown error"
            )
            Result.failure(
                Data.Builder()
                    .putString("error", e.message)
                    .putString("download_id", downloadId)
                    .build()
            )
        }
    }

    /**
     * Wait for a download to finish by polling the active state.
     */
    private suspend fun waitForCompletion(downloadId: String, file: File, expectedSize: Long) {
        var waitMs = 0L
        val maxWaitMs = 3600_000L // 1 hour max
        while (chunkedDownloader.isActive(downloadId) && waitMs < maxWaitMs) {
            kotlinx.coroutines.delay(500)
            waitMs += 500
        }
        // Verify file exists
        if (!file.exists() || (expectedSize > 0 && file.length() < expectedSize * 0.95)) {
            // Allow 5% tolerance for content-length vs actual
            if (expectedSize > 0 && file.length() < expectedSize * 0.5) {
                throw DownloadException("Download incomplete: ${file.length()} / $expectedSize bytes")
            }
        }
    }

    private fun createForegroundInfo(downloadId: String, title: String, progress: Int, notificationId: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, DeepLoaderApp.CHANNEL_DOWNLOADS)
            .setContentTitle("DeepLoader")
            .setContentText(if (progress >= 0) "$title — $progress%" else title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                if (progress >= 0) {
                    setProgress(100, progress, false)
                } else {
                    setProgress(0, 0, true) // Indeterminate
                }
            }
            .build()

        return ForegroundInfo(notificationId, notification)
    }

    private fun getDefaultOutputDir(): String {
        val dir = File(applicationContext.getExternalFilesDir(null), "DeepLoader")
        dir.mkdirs()
        return dir.absolutePath
    }

    private fun guessExtension(url: String, isMp3: Boolean): String {
        if (isMp3) return "mp3"
        return when {
            url.contains(".mp4", ignoreCase = true) -> "mp4"
            url.contains(".webm", ignoreCase = true) -> "webm"
            url.contains(".m4a", ignoreCase = true) -> "m4a"
            url.contains(".opus", ignoreCase = true) -> "opus"
            url.contains(".mp3", ignoreCase = true) -> "mp3"
            url.contains("audio", ignoreCase = true) -> "m4a"
            else -> "mp4"
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "opus" -> "audio/opus"
            "mkv" -> "video/x-matroska"
            else -> "application/octet-stream"
        }
    }
}
