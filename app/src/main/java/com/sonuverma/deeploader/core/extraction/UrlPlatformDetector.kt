package com.sonuverma.deeploader.core.extraction

import com.sonuverma.deeploader.data.models.Platform
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects which platform a URL belongs to.
 * Supports YouTube, Instagram, Twitter/X, TikTok, Reddit, Facebook,
 * Dailymotion, Vimeo, SoundCloud, Snapchat, and torrent magnet links.
 *
 * Extracts video IDs where possible for direct API calls.
 * Developer: Sonu Verma
 */
@Singleton
class UrlPlatformDetector @Inject constructor() {

    /**
     * Detects the platform from a URL string.
     * Also handles magnet: URIs for torrent detection.
     */
    fun detect(url: String): PlatformDetectionResult {
        val trimmed = url.trim()

        // Magnet links → torrent
        if (trimmed.startsWith("magnet:")) {
            return PlatformDetectionResult(Platform.TORRENT, trimmed, extractMagnetHash(trimmed))
        }

        val host = try {
            URI(trimmed).host?.lowercase() ?: return PlatformDetectionResult(Platform.UNKNOWN, trimmed)
        } catch (e: Exception) {
            return PlatformDetectionResult(Platform.UNKNOWN, trimmed)
        }

        return when {
            // YouTube (all domains)
            host.contains("youtube.com") || host.contains("youtu.be") ||
            host.contains("youtube-nocookie.com") || host.contains("music.youtube.com") -> {
                PlatformDetectionResult(Platform.YOUTUBE, trimmed, extractYouTubeId(trimmed))
            }

            // Instagram
            host.contains("instagram.com") || host.contains("instagr.am") -> {
                PlatformDetectionResult(Platform.INSTAGRAM, trimmed)
            }

            // Twitter / X
            host.contains("twitter.com") || host.contains("x.com") ||
            host.contains("t.co") -> {
                PlatformDetectionResult(Platform.TWITTER, trimmed)
            }

            // TikTok
            host.contains("tiktok.com") || host.contains("vm.tiktok.com") -> {
                PlatformDetectionResult(Platform.TIKTOK, trimmed)
            }

            // Reddit
            host.contains("reddit.com") || host.contains("redd.it") ||
            host.contains("v.redd.it") -> {
                PlatformDetectionResult(Platform.REDDIT, trimmed)
            }

            // Facebook
            host.contains("facebook.com") || host.contains("fb.com") ||
            host.contains("fb.watch") -> {
                PlatformDetectionResult(Platform.FACEBOOK, trimmed)
            }

            // Dailymotion
            host.contains("dailymotion.com") || host.contains("dai.ly") -> {
                PlatformDetectionResult(Platform.DAILYMOTION, trimmed)
            }

            // Vimeo
            host.contains("vimeo.com") || host.contains("player.vimeo.com") -> {
                PlatformDetectionResult(Platform.VIMEO, trimmed)
            }

            // SoundCloud
            host.contains("soundcloud.com") || host.contains("snd.sc") -> {
                PlatformDetectionResult(Platform.SOUNDCLOUD, trimmed)
            }

            // Snapchat
            host.contains("snapchat.com") || host.contains("snap.com") -> {
                PlatformDetectionResult(Platform.SNAPCHAT, trimmed)
            }

            // Unknown — will use yt-dlp fallback
            else -> PlatformDetectionResult(Platform.UNKNOWN, trimmed)
        }
    }

    /**
     * Extracts YouTube video ID from various URL formats.
     * Supports: youtube.com/watch?v=, youtu.be/, youtube.com/shorts/,
     * youtube.com/embed/, youtube.com/live/, music.youtube.com/watch?v=
     */
    private fun extractYouTubeId(url: String): String? {
        // youtu.be/VIDEO_ID
        val shortRegex = Regex("youtu\\.be/([a-zA-Z0-9_-]{11})")
        shortRegex.find(url)?.groupValues?.get(1)?.let { return it }

        // youtube.com/watch?v=VIDEO_ID
        val watchRegex = Regex("[?&]v=([a-zA-Z0-9_-]{11})")
        watchRegex.find(url)?.groupValues?.get(1)?.let { return it }

        // youtube.com/shorts/VIDEO_ID
        val shortsRegex = Regex("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})")
        shortsRegex.find(url)?.groupValues?.get(1)?.let { return it }

        // youtube.com/embed/VIDEO_ID
        val embedRegex = Regex("youtube\\.com/embed/([a-zA-Z0-9_-]{11})")
        embedRegex.find(url)?.groupValues?.get(1)?.let { return it }

        // youtube.com/live/VIDEO_ID
        val liveRegex = Regex("youtube\\.com/live/([a-zA-Z0-9_-]{11})")
        liveRegex.find(url)?.groupValues?.get(1)?.let { return it }

        return null
    }

    /**
     * Extracts info hash from magnet URI.
     */
    private fun extractMagnetHash(magnetUri: String): String? {
        val regex = Regex("xt=urn:btih:([a-fA-F0-9]{40}|[a-zA-Z2-7]{32})")
        return regex.find(magnetUri)?.groupValues?.get(1)
    }

    /**
     * Checks if a string looks like a valid URL.
     */
    fun isValidUrl(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("http://") ||
               trimmed.startsWith("https://") ||
               trimmed.startsWith("magnet:")
    }
}

data class PlatformDetectionResult(
    val platform: Platform,
    val url: String,
    val contentId: String? = null // Video ID, magnet hash, etc.
)
