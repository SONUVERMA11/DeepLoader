package com.sonuverma.deeploader.data.models

import java.util.UUID

/**
 * Represents a download task with full lifecycle state.
 * Tracks progress, speed, chunks, and retry state.
 */
data class DownloadJob(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val thumbnailUrl: String = "",
    val platform: Platform = Platform.UNKNOWN,
    val selectedFormat: StreamFormat? = null,
    val audioFormat: StreamFormat? = null, // For DASH — separate audio stream to merge
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val etaSeconds: Long = -1L,
    val filePath: String = "",
    val outputFileName: String = "",
    val mimeType: String = "",
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val chunkCount: Int = 16,
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val convertToMp3: Boolean = false,
    val mp3Bitrate: Int = 320, // kbps: 128, 192, 320
    val isWifiOnly: Boolean = false,
    val scheduledTime: Long = 0L // 0 = immediate, else epoch ms
) {
    val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt().coerceIn(0, 100)

    val isActive: Boolean
        get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.MERGING

    val isComplete: Boolean
        get() = status == DownloadStatus.COMPLETED

    val isFailed: Boolean
        get() = status == DownloadStatus.FAILED

    val canRetry: Boolean
        get() = isFailed && retryCount < maxRetries

    val displaySpeed: String
        get() = when {
            speedBytesPerSecond <= 0 -> ""
            speedBytesPerSecond < 1024 -> "${speedBytesPerSecond} B/s"
            speedBytesPerSecond < 1024 * 1024 -> "${"%.1f".format(speedBytesPerSecond.toDouble() / 1024)} KB/s"
            else -> "${"%.1f".format(speedBytesPerSecond.toDouble() / (1024 * 1024))} MB/s"
        }

    val displayEta: String
        get() = when {
            etaSeconds <= 0 -> ""
            etaSeconds < 60 -> "${etaSeconds}s"
            etaSeconds < 3600 -> "${etaSeconds / 60}m ${etaSeconds % 60}s"
            else -> "${etaSeconds / 3600}h ${(etaSeconds % 3600) / 60}m"
        }

    val displaySize: String
        get() = when {
            totalBytes <= 0 -> "Unknown"
            totalBytes < 1024 * 1024 -> "${"%.0f".format(totalBytes.toDouble() / 1024)} KB"
            totalBytes < 1024L * 1024 * 1024 -> "${"%.1f".format(totalBytes.toDouble() / (1024 * 1024))} MB"
            else -> "${"%.2f".format(totalBytes.toDouble() / (1024L * 1024 * 1024))} GB"
        }
}

enum class DownloadStatus(val displayName: String) {
    QUEUED("Queued"),
    DOWNLOADING("Downloading"),
    PAUSED("Paused"),
    MERGING("Merging Audio/Video"),
    CONVERTING("Converting to MP3"),
    SAVING("Saving to Gallery"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}
