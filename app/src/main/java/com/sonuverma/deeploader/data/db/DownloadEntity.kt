package com.sonuverma.deeploader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sonuverma.deeploader.data.models.DownloadStatus
import com.sonuverma.deeploader.data.models.Platform

/**
 * Room entity representing a download record.
 * Persists across app kills for resume support and download history.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String = "",
    val platform: String = Platform.UNKNOWN.name,
    val status: String = DownloadStatus.QUEUED.name,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    val filePath: String = "",
    val outputFileName: String = "",
    val mimeType: String = "",
    val streamUrl: String = "",        // Direct stream URL for download
    val audioStreamUrl: String = "",   // Separate audio URL for DASH merging
    val format: String = "",           // mp4, webm, m4a, etc.
    val quality: String = "",          // "1080p", "720p", etc.
    val retryCount: Int = 0,
    val chunkCount: Int = 16,
    val errorMessage: String = "",
    val convertToMp3: Boolean = false,
    val mp3Bitrate: Int = 320,
    val isWifiOnly: Boolean = false,
    val scheduledTime: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,

    // Chunk resume data — JSON array of chunk offsets for byte-range resume
    val chunkOffsetsJson: String = "[]"
)
