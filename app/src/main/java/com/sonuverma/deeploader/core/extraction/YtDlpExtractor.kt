package com.sonuverma.deeploader.core.extraction

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sonuverma.deeploader.data.models.ExtractionResult
import com.sonuverma.deeploader.data.models.StreamFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layer 2 — yt-dlp binary extractor.
 *
 * Uses the yt-dlp ARM binary for extraction from 1000+ sites.
 * CRITICAL: Must use --extractor-args "youtube:player_client=tv_embedded,ios"
 * to bypass YouTube PO Token enforcement.
 *
 * The binary is auto-updated at runtime from GitHub releases.
 * Stored at: context.filesDir/yt-dlp
 *
 * Typical extraction time: ~2s
 *
 * Developer: Sonu Verma
 */
@Singleton
class YtDlpExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    /**
     * Path to the yt-dlp binary on device storage.
     */
    private val binaryPath: String
        get() = File(context.filesDir, "yt-dlp").absolutePath

    /**
     * Check if the yt-dlp binary is available and executable.
     */
    fun isBinaryAvailable(): Boolean {
        val file = File(binaryPath)
        return file.exists() && file.canExecute()
    }

    /**
     * Extract media info from a URL using yt-dlp --dump-json.
     *
     * @param url The media URL to extract
     * @return ExtractionResult with available streams
     * @throws Exception if extraction fails or binary not available
     */
    suspend fun extract(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        if (!isBinaryAvailable()) {
            throw YtDlpException("yt-dlp binary not found. Run auto-updater first.")
        }

        // 30-second timeout for slow networks
        withTimeout(30_000L) {
            val process = ProcessBuilder(
                binaryPath,
                "--dump-json",
                "--no-playlist",
                // CRITICAL: These flags bypass YouTube PO token blocks
                "--extractor-args", "youtube:player_client=tv_embedded,ios",
                "--no-check-certificates",
                "--socket-timeout", "20",
                "--no-warnings",
                // Prefer formats with both video and audio
                "--format", "best[ext=mp4]/best",
                // Also dump all available formats
                "--print", "%(formats)j",
                url
            )
                .redirectErrorStream(false)
                .start()

            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))

            val output = StringBuilder()
            val errorOutput = StringBuilder()

            // Read stdout line by line to avoid memory issues with large outputs
            var line: String?
            while (stdout.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            while (stderr.readLine().also { line = it } != null) {
                errorOutput.appendLine(line)
            }

            val exitCode = process.waitFor()

            if (exitCode != 0) {
                val errorMsg = errorOutput.toString().trim()
                throw YtDlpException(
                    sanitizeErrorMessage(errorMsg.ifEmpty { "yt-dlp exited with code $exitCode" })
                )
            }

            val jsonOutput = output.toString().trim()
            if (jsonOutput.isEmpty()) {
                throw YtDlpException("yt-dlp returned empty output")
            }

            parseYtDlpJson(jsonOutput)
        }
    }

    /**
     * Parse yt-dlp --dump-json output into ExtractionResult.
     * Handles the complex nested JSON format with format arrays.
     */
    private fun parseYtDlpJson(json: String): ExtractionResult {
        // yt-dlp can output multiple JSON objects (one per line for playlists)
        // We take the first line which is the main video info
        val firstJson = json.lines().firstOrNull { it.startsWith("{") }
            ?: throw YtDlpException("Invalid JSON output from yt-dlp")

        val root = JsonParser.parseString(firstJson).asJsonObject

        val id = root.getStringOrEmpty("id")
        val title = root.getStringOrEmpty("title").ifEmpty { root.getStringOrEmpty("fulltitle") }
        val description = root.getStringOrEmpty("description")
        val thumbnail = root.getStringOrEmpty("thumbnail")
        val uploader = root.getStringOrEmpty("uploader").ifEmpty { root.getStringOrEmpty("channel") }
        val uploaderUrl = root.getStringOrEmpty("uploader_url").ifEmpty { root.getStringOrEmpty("channel_url") }
        val duration = root.getLongOrZero("duration")
        val viewCount = root.getLongOrZero("view_count")
        val uploadDate = root.getStringOrEmpty("upload_date")
        val isLive = root.getBoolOrFalse("is_live")

        // Parse all available formats
        val formats = root.getAsJsonArray("formats") ?: return ExtractionResult(
            id = id,
            title = title,
            description = description,
            thumbnailUrl = thumbnail,
            uploaderName = uploader,
            uploaderUrl = uploaderUrl,
            duration = duration,
            viewCount = viewCount,
            uploadDate = uploadDate,
            isLive = isLive
        )

        val videoStreams = mutableListOf<StreamFormat>()
        val audioStreams = mutableListOf<StreamFormat>()
        val videoOnlyStreams = mutableListOf<StreamFormat>()

        for (formatElement in formats) {
            val format = formatElement.asJsonObject
            val streamFormat = parseFormat(format) ?: continue

            when {
                streamFormat.isAudioOnly -> audioStreams.add(streamFormat)
                streamFormat.isVideoOnly -> videoOnlyStreams.add(streamFormat)
                else -> videoStreams.add(streamFormat) // Combined video+audio
            }
        }

        return ExtractionResult(
            id = id,
            title = title,
            description = description,
            thumbnailUrl = thumbnail,
            uploaderName = uploader,
            uploaderUrl = uploaderUrl,
            duration = duration,
            viewCount = viewCount,
            uploadDate = uploadDate,
            videoStreams = videoStreams,
            audioStreams = audioStreams,
            videoOnlyStreams = videoOnlyStreams,
            isLive = isLive
        )
    }

    /**
     * Parse a single format entry from yt-dlp JSON.
     * Filters out storyboard/thumbnail-only formats.
     */
    private fun parseFormat(format: JsonObject): StreamFormat? {
        val url = format.getStringOrEmpty("url")
        if (url.isEmpty()) return null

        val vcodec = format.getStringOrEmpty("vcodec")
        val acodec = format.getStringOrEmpty("acodec")

        // Skip formats with no audio AND no video (storyboard/poster)
        val hasVideo = vcodec.isNotEmpty() && vcodec != "none"
        val hasAudio = acodec.isNotEmpty() && acodec != "none"
        if (!hasVideo && !hasAudio) return null

        val height = format.getIntOrZero("height")
        val width = format.getIntOrZero("width")
        val fps = format.getIntOrZero("fps")
        val fileSize = format.getLongOrZero("filesize").let {
            if (it == 0L) format.getLongOrZero("filesize_approx") else it
        }
        val abr = format.getLongOrZero("abr") * 1000  // kbps → bps
        val vbr = format.getLongOrZero("vbr") * 1000
        val tbr = format.getLongOrZero("tbr") * 1000

        val ext = format.getStringOrEmpty("ext")
        val formatNote = format.getStringOrEmpty("format_note")
        val codec = if (hasVideo) vcodec else acodec
        val bitrate = when {
            hasVideo && hasAudio -> tbr
            hasVideo -> vbr.let { if (it == 0L) tbr else it }
            else -> abr.let { if (it == 0L) tbr else it }
        }

        return StreamFormat(
            url = url,
            format = ext,
            formatNote = formatNote,
            codec = codec,
            height = height,
            width = width,
            fps = fps,
            bitrate = bitrate,
            fileSize = fileSize,
            isVideoOnly = hasVideo && !hasAudio,
            isAudioOnly = !hasVideo && hasAudio,
            sampleRate = format.getIntOrZero("asr"),
            channels = format.getIntOrZero("audio_channels"),
            qualityLabel = when {
                hasVideo -> "${height}p"
                else -> "${abr / 1000}kbps"
            },
            mimeType = format.getStringOrEmpty("mime_type")
        )
    }

    /**
     * Remove sensitive information and yt-dlp internal messages from error output.
     * Users should never see raw stack traces or internal paths.
     */
    private fun sanitizeErrorMessage(error: String): String {
        return error
            .lines()
            .filter { line ->
                !line.startsWith("WARNING:") &&
                !line.contains("Traceback") &&
                !line.contains("File \"") &&
                line.isNotBlank()
            }
            .joinToString(" ")
            .replace(Regex("ERROR:\\s*"), "")
            .trim()
            .take(200) // Cap length for UI display
    }

    // ─── JSON Helper Extensions ───

    private fun JsonObject.getStringOrEmpty(key: String): String {
        return try { get(key)?.asString ?: "" } catch (e: Exception) { "" }
    }

    private fun JsonObject.getIntOrZero(key: String): Int {
        return try { get(key)?.asInt ?: 0 } catch (e: Exception) { 0 }
    }

    private fun JsonObject.getLongOrZero(key: String): Long {
        return try { get(key)?.asLong ?: 0L } catch (e: Exception) { 0L }
    }

    private fun JsonObject.getBoolOrFalse(key: String): Boolean {
        return try { get(key)?.asBoolean ?: false } catch (e: Exception) { false }
    }
}

class YtDlpException(message: String, cause: Throwable? = null) : Exception(message, cause)
