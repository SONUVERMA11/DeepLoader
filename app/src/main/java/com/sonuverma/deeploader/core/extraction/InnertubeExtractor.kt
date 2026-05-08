package com.sonuverma.deeploader.core.extraction

import com.google.gson.JsonParser
import com.sonuverma.deeploader.data.models.ExtractionResult
import com.sonuverma.deeploader.data.models.StreamFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Layer 3 — Raw Innertube API extractor (nuclear fallback).
 *
 * Directly calls YouTube's internal Innertube API pretending to be
 * the Android YouTube app. This bypasses most extraction blocks because
 * Google doesn't block their own app's API calls.
 *
 * Endpoint: POST https://www.youtube.com/youtubei/v1/player
 * Client: ANDROID (clientName=3, version=19.09.37)
 *
 * This is the last resort — only used when both NewPipe and yt-dlp fail.
 * Only works for YouTube (not other platforms).
 *
 * Typical extraction time: ~800ms
 *
 * Developer: Sonu Verma
 */
@Singleton
class InnertubeExtractor @Inject constructor(
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val INNERTUBE_API_URL = "https://www.youtube.com/youtubei/v1/player"
        private const val INNERTUBE_API_KEY = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"

        // Android YouTube app client identity
        private const val CLIENT_NAME = "ANDROID"
        private const val CLIENT_VERSION = "19.09.37"
        private const val ANDROID_SDK_VERSION = 30
        private const val ANDROID_VERSION = "11"
        private const val DEVICE_MODEL = "Pixel 5"
        private const val DEVICE_MAKE = "Google"

        // User-Agent matching the Android YouTube app
        private const val USER_AGENT =
            "com.google.android.youtube/$CLIENT_VERSION (Linux; U; Android $ANDROID_VERSION; en_US; $DEVICE_MODEL Build/RQ3A.211001.001) gzip"
    }

    /**
     * Extract video info directly from YouTube's Innertube API.
     *
     * @param videoId YouTube video ID (11 characters)
     * @return ExtractionResult with available streams
     */
    suspend fun extract(videoId: String): ExtractionResult = withContext(Dispatchers.IO) {
        withTimeout(15_000L) {
            val requestBody = buildInnertubePayload(videoId)

            val request = Request.Builder()
                .url("$INNERTUBE_API_URL?key=$INNERTUBE_API_KEY&prettyPrint=false")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .header("User-Agent", USER_AGENT)
                .header("X-YouTube-Client-Name", "3") // 3 = ANDROID
                .header("X-YouTube-Client-Version", CLIENT_VERSION)
                .header("Content-Type", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: throw InnertubeException("Empty response from Innertube API")

            if (!response.isSuccessful) {
                throw InnertubeException("Innertube API returned ${response.code}: ${response.message}")
            }

            parseInnertubeResponse(videoId, responseBody)
        }
    }

    /**
     * Build the Innertube API request payload.
     * Mimics the exact structure the Android YouTube app sends.
     */
    private fun buildInnertubePayload(videoId: String): String {
        return """
        {
            "context": {
                "client": {
                    "clientName": "$CLIENT_NAME",
                    "clientVersion": "$CLIENT_VERSION",
                    "androidSdkVersion": $ANDROID_SDK_VERSION,
                    "hl": "en",
                    "gl": "US",
                    "deviceMake": "$DEVICE_MAKE",
                    "deviceModel": "$DEVICE_MODEL",
                    "osName": "Android",
                    "osVersion": "$ANDROID_VERSION",
                    "platform": "MOBILE"
                },
                "user": {
                    "lockedSafetyMode": false
                }
            },
            "videoId": "$videoId",
            "params": "CgIQBg==",
            "playbackContext": {
                "contentPlaybackContext": {
                    "html5Preference": "HTML5_PREF_WANTS"
                }
            },
            "contentCheckOk": true,
            "racyCheckOk": true
        }
        """.trimIndent()
    }

    /**
     * Parse the Innertube API response into ExtractionResult.
     * Extracts streaming data, video details, and available formats.
     */
    private fun parseInnertubeResponse(videoId: String, json: String): ExtractionResult {
        val root = JsonParser.parseString(json).asJsonObject

        // Check for playability errors
        val playabilityStatus = root.getAsJsonObject("playabilityStatus")
        val status = playabilityStatus?.get("status")?.asString ?: "UNKNOWN"
        if (status != "OK") {
            val reason = playabilityStatus?.get("reason")?.asString
                ?: playabilityStatus?.getAsJsonArray("messages")?.firstOrNull()?.asString
                ?: "Video is not available"
            throw InnertubeException(reason)
        }

        // Extract video details
        val videoDetails = root.getAsJsonObject("videoDetails")
        val title = videoDetails?.get("title")?.asString ?: "Unknown"
        val description = videoDetails?.get("shortDescription")?.asString ?: ""
        val channelName = videoDetails?.get("author")?.asString ?: ""
        val channelId = videoDetails?.get("channelId")?.asString ?: ""
        val duration = videoDetails?.get("lengthSeconds")?.asString?.toLongOrNull() ?: 0L
        val viewCount = videoDetails?.get("viewCount")?.asString?.toLongOrNull() ?: 0L
        val isLive = videoDetails?.get("isLiveContent")?.asBoolean ?: false

        // Get thumbnail (highest quality)
        val thumbnailUrl = videoDetails?.getAsJsonObject("thumbnail")
            ?.getAsJsonArray("thumbnails")
            ?.lastOrNull()?.asJsonObject
            ?.get("url")?.asString ?: ""

        // Extract streaming data
        val streamingData = root.getAsJsonObject("streamingData")
            ?: throw InnertubeException("No streaming data in response")

        val videoStreams = mutableListOf<StreamFormat>()
        val audioStreams = mutableListOf<StreamFormat>()
        val videoOnlyStreams = mutableListOf<StreamFormat>()

        // Parse regular formats (combined video+audio)
        streamingData.getAsJsonArray("formats")?.forEach { element ->
            val format = element.asJsonObject
            parseInnertubeFormat(format)?.let { stream ->
                if (stream.isAudioOnly) audioStreams.add(stream)
                else videoStreams.add(stream)
            }
        }

        // Parse adaptive formats (video-only and audio-only)
        streamingData.getAsJsonArray("adaptiveFormats")?.forEach { element ->
            val format = element.asJsonObject
            parseInnertubeFormat(format)?.let { stream ->
                when {
                    stream.isAudioOnly -> audioStreams.add(stream)
                    stream.isVideoOnly -> videoOnlyStreams.add(stream)
                    else -> videoStreams.add(stream)
                }
            }
        }

        return ExtractionResult(
            id = videoId,
            title = title,
            description = description,
            thumbnailUrl = thumbnailUrl,
            uploaderName = channelName,
            uploaderUrl = if (channelId.isNotEmpty()) "https://www.youtube.com/channel/$channelId" else "",
            duration = duration,
            viewCount = viewCount,
            videoStreams = videoStreams,
            audioStreams = audioStreams,
            videoOnlyStreams = videoOnlyStreams,
            isLive = isLive
        )
    }

    /**
     * Parse a single format from Innertube streaming data.
     * Handles both signatureCipher and direct URL formats.
     */
    private fun parseInnertubeFormat(format: com.google.gson.JsonObject): StreamFormat? {
        // Get stream URL — may be direct or require cipher decryption
        val url = format.get("url")?.asString
            ?: decipherUrl(format.get("signatureCipher")?.asString ?: return null)
            ?: return null

        val mimeType = format.get("mimeType")?.asString ?: ""
        val isVideo = mimeType.startsWith("video/")
        val isAudio = mimeType.startsWith("audio/")

        if (!isVideo && !isAudio) return null

        val height = format.get("height")?.asInt ?: 0
        val width = format.get("width")?.asInt ?: 0
        val fps = format.get("fps")?.asInt ?: 0
        val bitrate = format.get("bitrate")?.asLong ?: 0L
        val contentLength = format.get("contentLength")?.asString?.toLongOrNull() ?: 0L
        val quality = format.get("quality")?.asString ?: ""
        val qualityLabel = format.get("qualityLabel")?.asString ?: ""

        // Extract codec from mimeType (e.g., "video/mp4; codecs=\"avc1.64001F\"")
        val codec = Regex("codecs=\"([^\"]+)\"").find(mimeType)?.groupValues?.get(1) ?: ""
        val ext = when {
            mimeType.contains("mp4") -> "mp4"
            mimeType.contains("webm") -> "webm"
            mimeType.contains("3gpp") -> "3gp"
            mimeType.contains("mp4a") || mimeType.contains("audio/mp4") -> "m4a"
            mimeType.contains("opus") || mimeType.contains("audio/webm") -> "opus"
            else -> "unknown"
        }

        val sampleRate = format.get("audioSampleRate")?.asString?.toIntOrNull() ?: 0
        val channels = format.get("audioChannels")?.asInt ?: 0

        return StreamFormat(
            url = url,
            format = ext,
            formatNote = qualityLabel.ifEmpty { quality },
            codec = codec,
            height = height,
            width = width,
            fps = fps,
            bitrate = bitrate,
            fileSize = contentLength,
            isVideoOnly = isVideo && channels == 0 && sampleRate == 0,
            isAudioOnly = isAudio,
            sampleRate = sampleRate,
            channels = channels,
            qualityLabel = qualityLabel.ifEmpty { if (isAudio) "${bitrate / 1000}kbps" else "${height}p" },
            mimeType = mimeType
        )
    }

    /**
     * Attempt to extract URL from signatureCipher parameter.
     * Note: Full cipher decryption requires JavaScript execution which we don't support.
     * This handles the simpler cases where the URL is directly extractable.
     */
    private fun decipherUrl(signatureCipher: String): String? {
        val params = signatureCipher.split("&").associate { param ->
            val (key, value) = param.split("=", limit = 2)
            key to URLDecoder.decode(value, "UTF-8")
        }

        val url = params["url"] ?: return null
        val sig = params["s"]

        // If there's no signature, the URL works directly
        return if (sig == null) url else null

        // Full cipher decryption would require downloading and parsing YouTube's
        // player JavaScript — beyond the scope of this fallback layer.
        // If we reach here, NewPipe (Layer 1) should have handled it.
    }
}

class InnertubeException(message: String, cause: Throwable? = null) : Exception(message, cause)
