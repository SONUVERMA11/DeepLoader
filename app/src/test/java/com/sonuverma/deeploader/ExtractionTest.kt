package com.sonuverma.deeploader

import com.sonuverma.deeploader.core.extraction.UrlPlatformDetector
import com.sonuverma.deeploader.data.models.DownloadJob
import com.sonuverma.deeploader.data.models.DownloadStatus
import com.sonuverma.deeploader.data.models.ExtractionLayer
import com.sonuverma.deeploader.data.models.ExtractionResult
import com.sonuverma.deeploader.data.models.Platform
import com.sonuverma.deeploader.data.models.StreamFormat
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive offline tests for DeepLoader backend workflow.
 *
 * Tests the entire pipeline WITHOUT network access:
 *   1. URL Detection → Platform identification
 *   2. ExtractionResult → Stream parsing & quality selection
 *   3. DownloadJob → State machine correctness
 *   4. StreamFormat → Display formatting
 *
 * These tests verify that if the extraction layer returns valid data,
 * the entire downstream pipeline (format selection → download job creation
 * → progress tracking) works correctly.
 *
 * Developer: Sonu Verma
 */
class ExtractionTest {

    // ═══════════════════════════════════════════════
    // 1. URL Platform Detection Tests
    // ═══════════════════════════════════════════════

    private val detector = UrlPlatformDetector()

    @Test
    fun `YouTube standard URL detected correctly`() {
        val result = detector.detect("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(Platform.YOUTUBE, result.platform)
        assertEquals("dQw4w9WgXcQ", result.contentId)
    }

    @Test
    fun `YouTube short URL detected correctly`() {
        val result = detector.detect("https://youtu.be/dQw4w9WgXcQ")
        assertEquals(Platform.YOUTUBE, result.platform)
        assertEquals("dQw4w9WgXcQ", result.contentId)
    }

    @Test
    fun `YouTube Shorts URL detected correctly`() {
        val result = detector.detect("https://www.youtube.com/shorts/dQw4w9WgXcQ")
        assertEquals(Platform.YOUTUBE, result.platform)
        assertEquals("dQw4w9WgXcQ", result.contentId)
    }

    @Test
    fun `YouTube embed URL detected correctly`() {
        val result = detector.detect("https://www.youtube.com/embed/dQw4w9WgXcQ")
        assertEquals(Platform.YOUTUBE, result.platform)
        assertEquals("dQw4w9WgXcQ", result.contentId)
    }

    @Test
    fun `YouTube live URL detected correctly`() {
        val result = detector.detect("https://www.youtube.com/live/dQw4w9WgXcQ")
        assertEquals(Platform.YOUTUBE, result.platform)
        assertEquals("dQw4w9WgXcQ", result.contentId)
    }

    @Test
    fun `YouTube Music URL detected correctly`() {
        val result = detector.detect("https://music.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(Platform.YOUTUBE, result.platform)
    }

    @Test
    fun `Instagram URL detected correctly`() {
        val result = detector.detect("https://www.instagram.com/p/ABC123/")
        assertEquals(Platform.INSTAGRAM, result.platform)
    }

    @Test
    fun `Twitter URL detected correctly`() {
        val result = detector.detect("https://twitter.com/user/status/123")
        assertEquals(Platform.TWITTER, result.platform)
    }

    @Test
    fun `X dot com URL detected correctly`() {
        val result = detector.detect("https://x.com/user/status/123")
        assertEquals(Platform.TWITTER, result.platform)
    }

    @Test
    fun `TikTok URL detected correctly`() {
        val result = detector.detect("https://www.tiktok.com/@user/video/123")
        assertEquals(Platform.TIKTOK, result.platform)
    }

    @Test
    fun `Reddit URL detected correctly`() {
        val result = detector.detect("https://reddit.com/r/videos/comments/abc/test/")
        assertEquals(Platform.REDDIT, result.platform)
    }

    @Test
    fun `Facebook URL detected correctly`() {
        val result = detector.detect("https://www.facebook.com/watch?v=123")
        assertEquals(Platform.FACEBOOK, result.platform)
    }

    @Test
    fun `SoundCloud URL detected correctly`() {
        val result = detector.detect("https://soundcloud.com/artist/track")
        assertEquals(Platform.SOUNDCLOUD, result.platform)
    }

    @Test
    fun `Vimeo URL detected correctly`() {
        val result = detector.detect("https://vimeo.com/123456")
        assertEquals(Platform.VIMEO, result.platform)
    }

    @Test
    fun `Dailymotion URL detected correctly`() {
        val result = detector.detect("https://www.dailymotion.com/video/x7t83")
        assertEquals(Platform.DAILYMOTION, result.platform)
    }

    @Test
    fun `Magnet link detected as TORRENT`() {
        val magnetUri = "magnet:?xt=urn:btih:c12fe1c06bba254a9dc9f519b335aa7c1367a88a&dn=test"
        val result = detector.detect(magnetUri)
        assertEquals(Platform.TORRENT, result.platform)
        assertEquals("c12fe1c06bba254a9dc9f519b335aa7c1367a88a", result.contentId)
    }

    @Test
    fun `Unknown URL returns UNKNOWN platform`() {
        val result = detector.detect("https://www.example.com/video")
        assertEquals(Platform.UNKNOWN, result.platform)
    }

    @Test
    fun `Invalid string returns UNKNOWN platform`() {
        val result = detector.detect("not a url at all")
        assertEquals(Platform.UNKNOWN, result.platform)
    }

    @Test
    fun `URL validation works for http and https`() {
        assertTrue(detector.isValidUrl("https://youtube.com/watch?v=abc"))
        assertTrue(detector.isValidUrl("http://example.com"))
        assertTrue(detector.isValidUrl("magnet:?xt=urn:btih:abc"))
        assertFalse(detector.isValidUrl("not a url"))
        assertFalse(detector.isValidUrl("ftp://example.com"))
    }

    // ═══════════════════════════════════════════════
    // 2. ExtractionResult & Stream Selection Tests
    // ═══════════════════════════════════════════════

    private fun createMockExtractionResult(): ExtractionResult {
        return ExtractionResult(
            id = "dQw4w9WgXcQ",
            title = "Rick Astley - Never Gonna Give You Up",
            description = "The official video for Rick Astley",
            thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
            uploaderName = "Rick Astley",
            uploaderUrl = "https://www.youtube.com/channel/UCuAXFkgsw1L7xaCfnd5JJOw",
            duration = 213L,
            viewCount = 1_500_000_000L,
            uploadDate = "2009-10-25",
            platform = Platform.YOUTUBE,
            videoStreams = listOf(
                StreamFormat(url = "https://stream.example.com/720p", format = "mp4", height = 720, width = 1280, fps = 30, bitrate = 2_500_000, fileSize = 66_000_000, qualityLabel = "720p"),
                StreamFormat(url = "https://stream.example.com/360p", format = "mp4", height = 360, width = 640, fps = 30, bitrate = 800_000, fileSize = 21_000_000, qualityLabel = "360p")
            ),
            audioStreams = listOf(
                StreamFormat(url = "https://stream.example.com/audio_128k", format = "m4a", bitrate = 128_000, isAudioOnly = true, qualityLabel = "128kbps"),
                StreamFormat(url = "https://stream.example.com/audio_256k", format = "m4a", bitrate = 256_000, isAudioOnly = true, qualityLabel = "256kbps")
            ),
            videoOnlyStreams = listOf(
                StreamFormat(url = "https://stream.example.com/1080p_vonly", format = "webm", height = 1080, width = 1920, fps = 30, bitrate = 4_000_000, fileSize = 107_000_000, isVideoOnly = true, qualityLabel = "1080p"),
                StreamFormat(url = "https://stream.example.com/4k_vonly", format = "webm", height = 2160, width = 3840, fps = 30, bitrate = 15_000_000, fileSize = 400_000_000, isVideoOnly = true, qualityLabel = "4K")
            ),
            sourceLayer = ExtractionLayer.NEWPIPE,
            originalUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        )
    }

    @Test
    fun `ExtractionResult getBestStream returns highest progressive stream`() {
        val result = createMockExtractionResult()
        val best = result.getBestStream()
        assertNotNull(best)
        assertEquals(720, best!!.height)
        assertEquals("mp4", best.format)
    }

    @Test
    fun `ExtractionResult getBestAudioStream returns highest bitrate audio`() {
        val result = createMockExtractionResult()
        val bestAudio = result.getBestAudioStream()
        assertNotNull(bestAudio)
        assertEquals(256_000L, bestAudio!!.bitrate)
    }

    @Test
    fun `ExtractionResult quality tiers are properly grouped`() {
        val result = createMockExtractionResult()
        val tiers = result.getQualityTiers()
        assertTrue("Should have multiple quality tiers", tiers.size >= 2)
        // Audio tier should exist
        assertTrue("Should have audio-only tier", tiers.any { it.label == "Audio Only" })
    }

    @Test
    fun `ExtractionResult with empty streams returns null for getBestStream`() {
        val emptyResult = ExtractionResult(id = "test", title = "Test")
        assertNull(emptyResult.getBestStream())
        assertNull(emptyResult.getBestAudioStream())
    }

    @Test
    fun `ExtractionResult getBestStream falls back to videoOnlyStreams`() {
        val videoOnlyResult = ExtractionResult(
            id = "test", title = "Test",
            videoOnlyStreams = listOf(
                StreamFormat(url = "https://test.com/1080p", height = 1080, isVideoOnly = true),
                StreamFormat(url = "https://test.com/720p", height = 720, isVideoOnly = true)
            )
        )
        val best = videoOnlyResult.getBestStream()
        assertNotNull(best)
        assertEquals(1080, best!!.height)
    }

    // ═══════════════════════════════════════════════
    // 3. StreamFormat Display Tests
    // ═══════════════════════════════════════════════

    @Test
    fun `StreamFormat displayQuality for video shows resolution and format`() {
        val format = StreamFormat(url = "test", height = 1080, format = "mp4", fps = 30)
        assertEquals("1080p MP4", format.displayQuality)
    }

    @Test
    fun `StreamFormat displayQuality for 60fps video shows fps`() {
        val format = StreamFormat(url = "test", height = 1080, format = "mp4", fps = 60)
        assertEquals("1080p 60fps MP4", format.displayQuality)
    }

    @Test
    fun `StreamFormat displayQuality for audio shows bitrate`() {
        val format = StreamFormat(url = "test", bitrate = 128_000, format = "m4a", isAudioOnly = true)
        assertEquals("128kbps M4A", format.displayQuality)
    }

    @Test
    fun `StreamFormat displaySize formats correctly`() {
        assertEquals("Unknown size", StreamFormat(url = "test", fileSize = 0).displaySize)
        assertEquals("500B", StreamFormat(url = "test", fileSize = 500).displaySize)
        assertEquals("50KB", StreamFormat(url = "test", fileSize = 50 * 1024).displaySize)
        // 66_000_000 bytes / (1024*1024) = 62.9 MB
        assertEquals("62.9MB", StreamFormat(url = "test", fileSize = 66_000_000).displaySize)
    }

    @Test
    fun `StreamFormat displayCodec recognizes standard codecs`() {
        assertEquals("H.264", StreamFormat(url = "test", codec = "avc1.64001F").displayCodec)
        assertEquals("VP9", StreamFormat(url = "test", codec = "vp09.00.31.08").displayCodec)
        assertEquals("AV1", StreamFormat(url = "test", codec = "av01.0.08M.08").displayCodec)
        assertEquals("AAC", StreamFormat(url = "test", codec = "mp4a.40.2").displayCodec)
        assertEquals("Opus", StreamFormat(url = "test", codec = "opus").displayCodec)
    }

    // ═══════════════════════════════════════════════
    // 4. DownloadJob State Machine Tests
    // ═══════════════════════════════════════════════

    @Test
    fun `DownloadJob progress calculation is correct`() {
        val job = DownloadJob(url = "test", title = "Test", totalBytes = 100, downloadedBytes = 50)
        assertEquals(0.5f, job.progress, 0.001f)
        assertEquals(50, job.progressPercent)
    }

    @Test
    fun `DownloadJob progress with unknown total returns 0`() {
        val job = DownloadJob(url = "test", title = "Test", totalBytes = -1, downloadedBytes = 50)
        assertEquals(0f, job.progress, 0.001f)
        assertEquals(0, job.progressPercent)
    }

    @Test
    fun `DownloadJob isActive for DOWNLOADING status`() {
        val job = DownloadJob(url = "test", title = "Test", status = DownloadStatus.DOWNLOADING)
        assertTrue(job.isActive)
        assertFalse(job.isComplete)
        assertFalse(job.isFailed)
    }

    @Test
    fun `DownloadJob isActive for MERGING status`() {
        val job = DownloadJob(url = "test", title = "Test", status = DownloadStatus.MERGING)
        assertTrue(job.isActive)
    }

    @Test
    fun `DownloadJob isComplete for COMPLETED status`() {
        val job = DownloadJob(url = "test", title = "Test", status = DownloadStatus.COMPLETED)
        assertTrue(job.isComplete)
        assertFalse(job.isActive)
    }

    @Test
    fun `DownloadJob canRetry when failed with retries remaining`() {
        val job = DownloadJob(url = "test", title = "Test", status = DownloadStatus.FAILED, retryCount = 1, maxRetries = 3)
        assertTrue(job.canRetry)
    }

    @Test
    fun `DownloadJob canRetry is false when max retries reached`() {
        val job = DownloadJob(url = "test", title = "Test", status = DownloadStatus.FAILED, retryCount = 3, maxRetries = 3)
        assertFalse(job.canRetry)
    }

    @Test
    fun `DownloadJob displaySpeed formats correctly`() {
        assertEquals("", DownloadJob(url = "t", title = "t", speedBytesPerSecond = 0).displaySpeed)
        assertEquals("500 B/s", DownloadJob(url = "t", title = "t", speedBytesPerSecond = 500).displaySpeed)
        assertEquals("1.5 KB/s", DownloadJob(url = "t", title = "t", speedBytesPerSecond = 1536).displaySpeed)
        assertEquals("3.5 MB/s", DownloadJob(url = "t", title = "t", speedBytesPerSecond = 3670016).displaySpeed)
    }

    @Test
    fun `DownloadJob displayEta formats correctly`() {
        assertEquals("", DownloadJob(url = "t", title = "t", etaSeconds = 0).displayEta)
        assertEquals("45s", DownloadJob(url = "t", title = "t", etaSeconds = 45).displayEta)
        assertEquals("2m 30s", DownloadJob(url = "t", title = "t", etaSeconds = 150).displayEta)
        assertEquals("1h 5m", DownloadJob(url = "t", title = "t", etaSeconds = 3900).displayEta)
    }

    @Test
    fun `DownloadJob displaySize formats correctly`() {
        assertEquals("Unknown", DownloadJob(url = "t", title = "t", totalBytes = -1).displaySize)
        assertEquals("1.5 MB", DownloadJob(url = "t", title = "t", totalBytes = 1_572_864).displaySize)
    }

    // ═══════════════════════════════════════════════
    // 5. End-to-End Workflow Simulation
    // ═══════════════════════════════════════════════

    @Test
    fun `Full workflow - URL detection to download job creation`() {
        // Step 1: User pastes a YouTube URL
        val inputUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

        // Step 2: Detect platform
        val detection = detector.detect(inputUrl)
        assertEquals(Platform.YOUTUBE, detection.platform)
        assertEquals("dQw4w9WgXcQ", detection.contentId)
        assertTrue(detector.isValidUrl(inputUrl))

        // Step 3: Simulate extraction result (NewPipe Layer 1 success)
        val extractionResult = createMockExtractionResult()
        assertEquals("Rick Astley - Never Gonna Give You Up", extractionResult.title)
        assertEquals(Platform.YOUTUBE, extractionResult.platform)
        assertEquals(ExtractionLayer.NEWPIPE, extractionResult.sourceLayer)

        // Step 4: User selects best quality
        val bestStream = extractionResult.getBestStream()
        assertNotNull("Best stream should not be null", bestStream)
        assertEquals(720, bestStream!!.height)
        assertEquals("mp4", bestStream.format)
        assertTrue("Stream URL should not be empty", bestStream.url.isNotEmpty())

        // Step 5: Create download job
        val downloadJob = DownloadJob(
            url = extractionResult.originalUrl,
            title = extractionResult.title,
            thumbnailUrl = extractionResult.thumbnailUrl,
            platform = extractionResult.platform,
            selectedFormat = bestStream,
            audioFormat = extractionResult.getBestAudioStream(),
            totalBytes = bestStream.fileSize,
            chunkCount = 16
        )

        assertEquals(DownloadStatus.QUEUED, downloadJob.status)
        assertEquals(66_000_000L, downloadJob.totalBytes)
        assertEquals(0f, downloadJob.progress, 0.001f)
        assertFalse(downloadJob.isActive)
        assertFalse(downloadJob.isComplete)

        // Step 6: Simulate download progress
        val inProgressJob = downloadJob.copy(
            status = DownloadStatus.DOWNLOADING,
            downloadedBytes = 33_000_000,
            speedBytesPerSecond = 2_500_000
        )
        assertTrue(inProgressJob.isActive)
        assertEquals(50, inProgressJob.progressPercent)
        assertEquals("2.4 MB/s", inProgressJob.displaySpeed)

        // Step 7: Simulate completion
        val completedJob = inProgressJob.copy(
            status = DownloadStatus.COMPLETED,
            downloadedBytes = 66_000_000,
            completedAt = System.currentTimeMillis(),
            filePath = "/storage/emulated/0/Movies/DeepLoader/rick_astley.mp4"
        )
        assertTrue(completedJob.isComplete)
        assertEquals(100, completedJob.progressPercent)
        assertFalse(completedJob.isActive)

        println("✅ Full workflow test PASSED")
        println("   URL: $inputUrl")
        println("   Platform: ${detection.platform.displayName}")
        println("   Title: ${extractionResult.title}")
        println("   Best Stream: ${bestStream.displayQuality} (${bestStream.displaySize})")
        println("   Download: QUEUED → DOWNLOADING (50%) → COMPLETED")
    }

    @Test
    fun `Full workflow - Audio-only download`() {
        val result = createMockExtractionResult()
        val bestAudio = result.getBestAudioStream()
        assertNotNull(bestAudio)

        val job = DownloadJob(
            url = result.originalUrl,
            title = result.title,
            platform = result.platform,
            selectedFormat = bestAudio!!,
            convertToMp3 = true,
            mp3Bitrate = 320
        )

        assertTrue(job.convertToMp3)
        assertEquals(320, job.mp3Bitrate)
        assertEquals(DownloadStatus.QUEUED, job.status)

        println("✅ Audio-only workflow test PASSED")
        println("   Format: ${bestAudio.displayQuality}")
        println("   Convert to MP3: ${job.convertToMp3} @ ${job.mp3Bitrate}kbps")
    }

    @Test
    fun `Full workflow - Torrent download`() {
        val magnetUri = "magnet:?xt=urn:btih:c12fe1c06bba254a9dc9f519b335aa7c1367a88a&dn=ubuntu-24.04.iso"
        val detection = detector.detect(magnetUri)

        assertEquals(Platform.TORRENT, detection.platform)
        assertEquals("c12fe1c06bba254a9dc9f519b335aa7c1367a88a", detection.contentId)

        println("✅ Torrent workflow test PASSED")
        println("   InfoHash: ${detection.contentId}")
        println("   Platform: ${detection.platform.displayName}")
    }

    // ═══════════════════════════════════════════════
    // 6. Platform Enum Tests
    // ═══════════════════════════════════════════════

    @Test
    fun `All platforms have display names and icons`() {
        Platform.entries.forEach { platform ->
            assertTrue("${platform.name} should have a display name", platform.displayName.isNotEmpty())
            assertTrue("${platform.name} should have an icon", platform.icon.isNotEmpty())
        }
    }

    @Test
    fun `All download statuses have display names`() {
        DownloadStatus.entries.forEach { status ->
            assertTrue("${status.name} should have a display name", status.displayName.isNotEmpty())
        }
    }
}
