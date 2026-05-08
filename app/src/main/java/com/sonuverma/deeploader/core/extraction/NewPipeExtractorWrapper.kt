package com.sonuverma.deeploader.core.extraction

import android.content.Context
import com.sonuverma.deeploader.data.models.ExtractionResult
import com.sonuverma.deeploader.data.models.Platform
import com.sonuverma.deeploader.data.models.StreamFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layer 1 — NewPipe Extractor wrapper.
 *
 * Primary extraction engine. Uses the NewPipe Extractor library which is
 * a pure Java library — no native binaries, no ARM architecture issues.
 *
 * NewPipe handles YouTube PO tokens internally through its own
 * client impersonation, making it the most reliable first option.
 *
 * Typical extraction time: ~400ms
 *
 * Supported services: YouTube, SoundCloud, MediaCCC, PeerTube, Bandcamp
 *
 * Developer: Sonu Verma
 */
@Singleton
class NewPipeExtractorWrapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Track initialization state to avoid re-initializing
    @Volatile
    private var isInitialized = false

    /**
     * Initialize NewPipe Extractor with our custom HTTP downloader.
     * Thread-safe — only initializes once even with concurrent calls.
     */
    private fun ensureInitialized() {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    NewPipe.init(NewPipeDownloaderImpl.getInstance())
                    isInitialized = true
                }
            }
        }
    }

    /**
     * Extract media info from a URL using NewPipe Extractor.
     *
     * @param url The media URL to extract
     * @return ExtractionResult with available streams
     * @throws Exception if extraction fails
     */
    suspend fun extract(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        // 15-second timeout prevents hanging on slow/broken URLs
        withTimeout(15_000L) {
            ensureInitialized()

            val streamInfo = StreamInfo.getInfo(url)

            ExtractionResult(
                id = streamInfo.id,
                title = streamInfo.name ?: "Unknown Title",
                description = streamInfo.description?.content ?: "",
                thumbnailUrl = streamInfo.thumbnails?.firstOrNull()?.url ?: "",
                uploaderName = streamInfo.uploaderName ?: "",
                uploaderUrl = streamInfo.uploaderUrl ?: "",
                duration = streamInfo.duration,
                viewCount = streamInfo.viewCount,
                uploadDate = streamInfo.textualUploadDate ?: "",
                videoStreams = streamInfo.videoStreams?.map { it.toStreamFormat() } ?: emptyList(),
                audioStreams = streamInfo.audioStreams?.map { it.toStreamFormat() } ?: emptyList(),
                videoOnlyStreams = streamInfo.videoOnlyStreams?.map { it.toStreamFormat() } ?: emptyList(),
                isLive = streamInfo.streamType?.name?.contains("LIVE", ignoreCase = true) == true,
                originalUrl = url
            )
        }
    }

    /**
     * Convert NewPipe VideoStream to our unified StreamFormat.
     */
    private fun VideoStream.toStreamFormat(): StreamFormat {
        return StreamFormat(
            url = content ?: "",
            format = getFormat()?.suffix ?: "",
            formatNote = getResolution() ?: "",
            codec = codec ?: "",
            height = extractHeight(),
            width = extractWidth(),
            fps = fps,
            bitrate = bitrate.toLong(),
            isVideoOnly = isVideoOnly,
            isAudioOnly = false,
            qualityLabel = getResolution() ?: "Unknown",
            mimeType = getFormat()?.mimeType ?: ""
        )
    }

    /**
     * Convert NewPipe AudioStream to our unified StreamFormat.
     */
    private fun AudioStream.toStreamFormat(): StreamFormat {
        return StreamFormat(
            url = content ?: "",
            format = getFormat()?.suffix ?: "",
            formatNote = "audio only",
            codec = codec ?: "",
            height = 0,
            width = 0,
            fps = 0,
            bitrate = averageBitrate.toLong(),
            isVideoOnly = false,
            isAudioOnly = true,
            qualityLabel = "${averageBitrate / 1000}kbps",
            mimeType = getFormat()?.mimeType ?: ""
        )
    }

    /**
     * Extract video height from resolution string (e.g. "1080p" → 1080).
     */
    private fun VideoStream.extractHeight(): Int {
        val resolution = getResolution() ?: return 0
        return Regex("(\\d+)").find(resolution)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /**
     * Extract video width from resolution — estimates based on common aspect ratios.
     */
    private fun VideoStream.extractWidth(): Int {
        val h = extractHeight()
        return when {
            h >= 2160 -> 3840
            h >= 1440 -> 2560
            h >= 1080 -> 1920
            h >= 720 -> 1280
            h >= 480 -> 854
            h >= 360 -> 640
            h >= 240 -> 426
            h >= 144 -> 256
            else -> 0
        }
    }
}
