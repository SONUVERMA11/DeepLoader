package com.sonuverma.deeploader.core.extraction

import android.content.Context
import com.sonuverma.deeploader.data.models.ExtractionLayer
import com.sonuverma.deeploader.data.models.ExtractionResult
import com.sonuverma.deeploader.data.models.Platform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core extraction orchestrator — the heart of DeepLoader.
 *
 * Implements a 3-layer fallback chain for maximum reliability:
 *   Layer 1: NewPipe Extractor (~400ms, native Java, handles PO tokens internally)
 *   Layer 2: yt-dlp binary (~2s, tv_embedded+ios player clients, 1000+ sites)
 *   Layer 3: Raw Innertube API (~800ms, direct YouTube Android API call)
 *
 * The chain short-circuits on the first successful extraction.
 * Each layer has its own timeout and error handling.
 *
 * Developer: Sonu Verma
 */
@Singleton
class ExtractionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val newPipeExtractor: NewPipeExtractorWrapper,
    private val ytDlpExtractor: YtDlpExtractor,
    private val innertubeExtractor: InnertubeExtractor,
    private val platformDetector: UrlPlatformDetector
) {

    /**
     * Extract media information from a URL using the 3-layer fallback chain.
     *
     * @param url The media URL to extract from
     * @return ExtractionResult with all available streams, or throws ExtractionException
     */
    suspend fun extract(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        val detection = platformDetector.detect(url)

        // Magnet links bypass extraction — go straight to torrent engine
        if (detection.platform == Platform.TORRENT) {
            throw ExtractionException(
                "Magnet links should be handled by TorrentEngine, not ExtractionEngine",
                layer = null
            )
        }

        val errors = mutableListOf<LayerError>()

        // ─── Layer 1: NewPipe Extractor (primary, fastest) ───
        try {
            val result = newPipeExtractor.extract(url)
            if (result.videoStreams.isNotEmpty() || result.audioStreams.isNotEmpty() || result.videoOnlyStreams.isNotEmpty()) {
                return@withContext result.copy(
                    platform = detection.platform,
                    sourceLayer = ExtractionLayer.NEWPIPE,
                    originalUrl = url
                )
            }
            errors.add(LayerError(ExtractionLayer.NEWPIPE, "No streams found"))
        } catch (e: Exception) {
            errors.add(LayerError(ExtractionLayer.NEWPIPE, e.message ?: "Unknown error"))
        }

        // ─── Layer 2: yt-dlp binary (wide coverage, handles most sites) ───
        try {
            val result = ytDlpExtractor.extract(url)
            if (result.videoStreams.isNotEmpty() || result.audioStreams.isNotEmpty() || result.videoOnlyStreams.isNotEmpty()) {
                return@withContext result.copy(
                    platform = detection.platform,
                    sourceLayer = ExtractionLayer.YTDLP,
                    originalUrl = url
                )
            }
            errors.add(LayerError(ExtractionLayer.YTDLP, "No streams found"))
        } catch (e: Exception) {
            errors.add(LayerError(ExtractionLayer.YTDLP, e.message ?: "Unknown error"))
        }

        // ─── Layer 3: Raw Innertube API (YouTube-only nuclear fallback) ───
        if (detection.platform == Platform.YOUTUBE && detection.contentId != null) {
            try {
                val result = innertubeExtractor.extract(detection.contentId)
                if (result.videoStreams.isNotEmpty() || result.audioStreams.isNotEmpty() || result.videoOnlyStreams.isNotEmpty()) {
                    return@withContext result.copy(
                        platform = Platform.YOUTUBE,
                        sourceLayer = ExtractionLayer.INNERTUBE,
                        originalUrl = url
                    )
                }
                errors.add(LayerError(ExtractionLayer.INNERTUBE, "No streams found"))
            } catch (e: Exception) {
                errors.add(LayerError(ExtractionLayer.INNERTUBE, e.message ?: "Unknown error"))
            }
        }

        // All layers failed — build comprehensive error message
        val errorSummary = errors.joinToString("\n") { "  ${it.layer.name}: ${it.message}" }
        throw ExtractionException(
            "All extraction layers failed for URL: $url\n$errorSummary",
            layer = null,
            layerErrors = errors
        )
    }

    /**
     * Quick check if a URL is extractable (doesn't actually extract).
     * Used for clipboard watcher to avoid unnecessary extraction attempts.
     */
    fun isSupported(url: String): Boolean {
        return platformDetector.isValidUrl(url)
    }

    /**
     * Returns the detected platform for a URL without extracting.
     */
    fun detectPlatform(url: String): PlatformDetectionResult {
        return platformDetector.detect(url)
    }
}

/**
 * Exception thrown when extraction fails.
 * Contains details about which layers were tried and why they failed.
 */
class ExtractionException(
    message: String,
    val layer: ExtractionLayer?,
    val layerErrors: List<LayerError> = emptyList(),
    cause: Throwable? = null
) : Exception(message, cause) {

    /**
     * Human-readable error message for UI display.
     * Never exposes raw stack traces.
     */
    val userMessage: String
        get() = when {
            layerErrors.any { it.message.contains("geo", ignoreCase = true) } ->
                "This content is not available in your region"
            layerErrors.any { it.message.contains("private", ignoreCase = true) } ->
                "This content is private or requires authentication"
            layerErrors.any { it.message.contains("removed", ignoreCase = true) || it.message.contains("deleted", ignoreCase = true) } ->
                "This content has been removed or deleted"
            layerErrors.any { it.message.contains("age", ignoreCase = true) } ->
                "This content is age-restricted and cannot be extracted"
            layerErrors.any { it.message.contains("network", ignoreCase = true) || it.message.contains("connect", ignoreCase = true) } ->
                "Network error — check your internet connection and try again"
            layerErrors.any { it.message.contains("timeout", ignoreCase = true) } ->
                "Request timed out — the server may be slow, try again"
            else ->
                "Could not extract media from this URL. The content may be unavailable or the format unsupported."
        }
}

data class LayerError(
    val layer: ExtractionLayer,
    val message: String
)
