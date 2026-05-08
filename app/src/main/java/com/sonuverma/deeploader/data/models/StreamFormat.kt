package com.sonuverma.deeploader.data.models

/**
 * Represents a single media stream (video, audio, or combined).
 * Used across all extraction layers for unified format representation.
 */
data class StreamFormat(
    val url: String,
    val format: String = "",           // mp4, webm, m4a, opus, etc.
    val formatNote: String = "",       // "720p", "1080p60", "audio only"
    val codec: String = "",            // h264, vp9, av01, aac, opus
    val height: Int = 0,               // Video height in pixels (0 for audio)
    val width: Int = 0,                // Video width in pixels (0 for audio)
    val fps: Int = 0,                  // Frames per second
    val bitrate: Long = 0L,           // Bits per second
    val fileSize: Long = 0L,          // Estimated size in bytes (-1 if unknown)
    val isVideoOnly: Boolean = false,  // True if stream has no audio track
    val isAudioOnly: Boolean = false,  // True if stream has no video track
    val sampleRate: Int = 0,           // Audio sample rate in Hz
    val channels: Int = 0,             // Audio channels (1=mono, 2=stereo)
    val qualityLabel: String = "",     // Human-readable: "1080p", "720p HD"
    val mimeType: String = ""          // Full MIME type
) {
    /**
     * Human-readable quality string for UI display.
     */
    val displayQuality: String
        get() = when {
            isAudioOnly -> {
                val kbps = bitrate / 1000
                when {
                    kbps > 0 -> "${kbps}kbps ${format.uppercase()}"
                    else -> "Audio ${format.uppercase()}"
                }
            }
            height > 0 -> {
                val fpsLabel = if (fps > 30) "${fps}fps " else ""
                "${height}p $fpsLabel${format.uppercase()}"
            }
            qualityLabel.isNotEmpty() -> qualityLabel
            else -> "Unknown"
        }

    /**
     * Human-readable file size string.
     */
    val displaySize: String
        get() = when {
            fileSize <= 0 -> "Unknown size"
            fileSize < 1024 -> "${fileSize}B"
            fileSize < 1024 * 1024 -> "${fileSize / 1024}KB"
            fileSize < 1024 * 1024 * 1024 -> "${"%.1f".format(fileSize.toDouble() / (1024 * 1024))}MB"
            else -> "${"%.2f".format(fileSize.toDouble() / (1024 * 1024 * 1024))}GB"
        }

    /**
     * Codec info for format selection detail view.
     */
    val displayCodec: String
        get() = when {
            codec.contains("avc1") || codec.contains("h264") -> "H.264"
            codec.contains("vp9") || codec.contains("vp09") -> "VP9"
            codec.contains("av01") -> "AV1"
            codec.contains("aac") || codec.contains("mp4a") -> "AAC"
            codec.contains("opus") -> "Opus"
            codec.contains("vorbis") -> "Vorbis"
            codec.isNotEmpty() -> codec.uppercase()
            else -> ""
        }
}
