package com.sonuverma.deeploader.core.download

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sonuverma.deeploader.data.db.DownloadDao
import com.sonuverma.deeploader.data.models.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ChunkedDownloader — Multi-threaded byte-range parallel download engine.
 *
 * Splits files into N chunks (default 16) and downloads each chunk
 * simultaneously using OkHttp with HTTP Range headers. Supports:
 *   - Pause/Resume with byte-exact chunk offsets
 *   - Real-time speed calculation (1-second rolling average)
 *   - Progress callbacks at configurable intervals
 *   - Automatic retry for failed chunks (up to 3 attempts)
 *   - Server capability detection (Range support, content length)
 *
 * Architecture:
 *   1. HEAD request → detect content-length + range support
 *   2. Split into N chunks with byte ranges
 *   3. Launch N coroutines, each downloading its chunk with Range header
 *   4. Write chunks to RandomAccessFile at correct offsets
 *   5. Persist chunk offsets to Room for resume after app kill
 *
 * Developer: Sonu Verma
 */
@Singleton
class ChunkedDownloader @Inject constructor(
    private val httpClient: OkHttpClient,
    private val downloadDao: DownloadDao
) {
    companion object {
        private const val TAG = "ChunkedDownloader"
        private const val BUFFER_SIZE = 32 * 1024          // 32KB read buffer
        private const val PROGRESS_INTERVAL_MS = 250L      // Update progress every 250ms
        private const val SPEED_CALC_INTERVAL_MS = 1000L   // Recalculate speed every 1s
        private const val MAX_CHUNK_RETRIES = 3
    }

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = mutableMapOf<String, DownloadTask>()

    /**
     * Start downloading a file from the given URL.
     *
     * @param downloadId Unique ID matching the DownloadEntity
     * @param url Direct stream URL to download
     * @param outputFile Target file to write to
     * @param chunkCount Number of parallel chunks (1-32)
     * @param existingOffsets Previously saved chunk offsets for resume (empty for new)
     * @param onProgress Callback with (downloadedBytes, totalBytes, speedBps)
     */
    fun startDownload(
        downloadId: String,
        url: String,
        outputFile: File,
        chunkCount: Int = 16,
        existingOffsets: List<Long> = emptyList(),
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBps: Long) -> Unit = { _, _, _ -> },
        onComplete: (File) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Cancel any existing download for this ID
        cancelDownload(downloadId)

        val job = scope.launch {
            try {
                executeDownload(
                    downloadId = downloadId,
                    url = url,
                    outputFile = outputFile,
                    chunkCount = chunkCount,
                    existingOffsets = existingOffsets,
                    onProgress = onProgress
                )
                onComplete(outputFile)
            } catch (e: CancellationException) {
                Log.i(TAG, "Download $downloadId cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Download $downloadId failed", e)
                onError(e.message ?: "Unknown download error")
            } finally {
                activeJobs.remove(downloadId)
            }
        }

        activeJobs[downloadId] = DownloadTask(
            job = job,
            isPaused = AtomicBoolean(false),
            downloadedBytes = AtomicLong(0)
        )
    }

    /**
     * Core download execution — HEAD probe, chunk split, parallel download.
     */
    private suspend fun executeDownload(
        downloadId: String,
        url: String,
        outputFile: File,
        chunkCount: Int,
        existingOffsets: List<Long>,
        onProgress: (Long, Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {

        // Step 1: Probe server for content-length and range support
        val serverInfo = probeServer(url)
        val totalBytes = serverInfo.contentLength
        val supportsRange = serverInfo.supportsRange

        // Update total size in DB
        if (totalBytes > 0) {
            downloadDao.updateProgress(downloadId, 0, DownloadStatus.DOWNLOADING.name)
        }

        // Step 2: Determine actual chunk count
        val effectiveChunks = when {
            !supportsRange -> 1                                    // No range support → single stream
            totalBytes <= 0 -> 1                                   // Unknown size → single stream
            totalBytes < 1024 * 1024 -> 1                          // < 1MB → no need for chunks
            totalBytes < 10 * 1024 * 1024 -> minOf(chunkCount, 4)  // < 10MB → max 4 chunks
            else -> chunkCount                                      // Full chunking
        }

        // Step 3: Calculate chunk byte ranges
        val chunks = if (effectiveChunks == 1 || totalBytes <= 0) {
            listOf(ChunkRange(0, 0, totalBytes - 1)) // Single chunk = entire file
        } else {
            val chunkSize = totalBytes / effectiveChunks
            (0 until effectiveChunks).map { i ->
                val start = i * chunkSize
                val end = if (i == effectiveChunks - 1) totalBytes - 1 else (i + 1) * chunkSize - 1
                val resumeOffset = existingOffsets.getOrNull(i) ?: 0L
                ChunkRange(i, start + resumeOffset, end)
            }
        }

        // Step 4: Create output file with correct size
        outputFile.parentFile?.mkdirs()
        if (totalBytes > 0 && !outputFile.exists()) {
            RandomAccessFile(outputFile, "rw").use { it.setLength(totalBytes) }
        }

        // Step 5: Speed tracking
        val downloadedBytes = AtomicLong(existingOffsets.sum())
        val lastSpeedCheck = AtomicLong(System.currentTimeMillis())
        val lastSpeedBytes = AtomicLong(downloadedBytes.get())
        val currentSpeed = AtomicLong(0)
        val lastProgressUpdate = AtomicLong(0)
        val chunkOffsets = LongArray(effectiveChunks) { existingOffsets.getOrNull(it) ?: 0L }

        activeJobs[downloadId]?.downloadedBytes = downloadedBytes

        // Step 6: Launch parallel chunk downloads
        val chunkJobs = chunks.map { chunk ->
            async {
                downloadChunk(
                    downloadId = downloadId,
                    url = url,
                    outputFile = outputFile,
                    chunk = chunk,
                    totalBytes = totalBytes,
                    downloadedBytes = downloadedBytes,
                    chunkOffsets = chunkOffsets,
                    currentSpeed = currentSpeed,
                    lastSpeedCheck = lastSpeedCheck,
                    lastSpeedBytes = lastSpeedBytes,
                    lastProgressUpdate = lastProgressUpdate,
                    onProgress = onProgress
                )
            }
        }

        // Wait for all chunks to complete
        chunkJobs.awaitAll()

        // Final progress update
        onProgress(downloadedBytes.get(), totalBytes, currentSpeed.get())
    }

    /**
     * Download a single chunk using HTTP Range header.
     * Writes directly to the correct file offset via RandomAccessFile.
     */
    private suspend fun downloadChunk(
        downloadId: String,
        url: String,
        outputFile: File,
        chunk: ChunkRange,
        totalBytes: Long,
        downloadedBytes: AtomicLong,
        chunkOffsets: LongArray,
        currentSpeed: AtomicLong,
        lastSpeedCheck: AtomicLong,
        lastSpeedBytes: AtomicLong,
        lastProgressUpdate: AtomicLong,
        onProgress: (Long, Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        var retries = 0
        var currentStart = chunk.start

        while (retries < MAX_CHUNK_RETRIES) {
            try {
                val requestBuilder = Request.Builder().url(url)

                // Add Range header if server supports it and we have a valid range
                if (chunk.end > 0) {
                    requestBuilder.header("Range", "bytes=$currentStart-${chunk.end}")
                }

                val response = httpClient.newCall(requestBuilder.build()).execute()

                if (!response.isSuccessful && response.code != 206) {
                    throw DownloadException("HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw DownloadException("Empty response body")
                val inputStream = body.byteStream()
                val buffer = ByteArray(BUFFER_SIZE)

                RandomAccessFile(outputFile, "rw").use { raf ->
                    raf.seek(currentStart)

                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        // Check for pause/cancel
                        val task = activeJobs[downloadId]
                        if (task == null || !isActive) break
                        if (task.isPaused.get()) {
                            // Save current offsets and wait
                            persistChunkOffsets(downloadId, chunkOffsets)
                            while (task.isPaused.get() && isActive) {
                                kotlinx.coroutines.delay(500)
                            }
                        }

                        raf.write(buffer, 0, bytesRead)
                        currentStart += bytesRead
                        chunkOffsets[chunk.index] += bytesRead

                        val totalDownloaded = downloadedBytes.addAndGet(bytesRead.toLong())

                        // Speed calculation (every 1 second)
                        val now = System.currentTimeMillis()
                        val timeSinceLastSpeed = now - lastSpeedCheck.get()
                        if (timeSinceLastSpeed >= SPEED_CALC_INTERVAL_MS) {
                            val bytesSinceLastSpeed = totalDownloaded - lastSpeedBytes.get()
                            val speed = (bytesSinceLastSpeed * 1000) / timeSinceLastSpeed
                            currentSpeed.set(speed)
                            lastSpeedCheck.set(now)
                            lastSpeedBytes.set(totalDownloaded)
                        }

                        // Progress callback (throttled)
                        val timeSinceLastProgress = now - lastProgressUpdate.get()
                        if (timeSinceLastProgress >= PROGRESS_INTERVAL_MS) {
                            lastProgressUpdate.set(now)
                            onProgress(totalDownloaded, totalBytes, currentSpeed.get())
                        }
                    }
                }

                inputStream.close()
                response.close()
                return@withContext // Chunk completed successfully

            } catch (e: CancellationException) {
                throw e // Don't retry cancellations
            } catch (e: Exception) {
                retries++
                if (retries >= MAX_CHUNK_RETRIES) {
                    throw DownloadException("Chunk ${chunk.index} failed after $MAX_CHUNK_RETRIES retries: ${e.message}")
                }
                Log.w(TAG, "Chunk ${chunk.index} retry $retries: ${e.message}")
                kotlinx.coroutines.delay(1000L * retries) // Exponential backoff
            }
        }
    }

    /**
     * Probe server with HEAD request to determine content-length and range support.
     */
    private suspend fun probeServer(url: String): ServerInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()

            val response = httpClient.newCall(request).execute()
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
            val acceptRanges = response.header("Accept-Ranges")
            val supportsRange = acceptRanges?.equals("bytes", ignoreCase = true) == true
            response.close()

            ServerInfo(contentLength, supportsRange)
        } catch (e: Exception) {
            Log.w(TAG, "HEAD probe failed, falling back to single stream: ${e.message}")
            ServerInfo(-1L, false)
        }
    }

    /**
     * Save chunk offsets to Room for resume after app kill.
     */
    private suspend fun persistChunkOffsets(downloadId: String, offsets: LongArray) {
        val json = gson.toJson(offsets.toList())
        downloadDao.updateChunkOffsets(downloadId, json)
    }

    // ─── Control Methods ───

    fun pauseDownload(downloadId: String) {
        activeJobs[downloadId]?.let { task ->
            task.isPaused.set(true)
            scope.launch {
                downloadDao.updateStatus(downloadId, DownloadStatus.PAUSED.name)
            }
        }
    }

    fun resumeDownload(downloadId: String) {
        activeJobs[downloadId]?.let { task ->
            task.isPaused.set(false)
            scope.launch {
                downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING.name)
            }
        }
    }

    fun cancelDownload(downloadId: String) {
        activeJobs[downloadId]?.let { task ->
            task.job.cancel()
            activeJobs.remove(downloadId)
        }
    }

    fun cancelAll() {
        activeJobs.forEach { (_, task) -> task.job.cancel() }
        activeJobs.clear()
    }

    fun isPaused(downloadId: String): Boolean {
        return activeJobs[downloadId]?.isPaused?.get() ?: false
    }

    fun isActive(downloadId: String): Boolean {
        return activeJobs.containsKey(downloadId)
    }

    fun getActiveCount(): Int = activeJobs.size

    /**
     * Parse saved chunk offsets JSON back to list.
     */
    fun parseChunkOffsets(json: String): List<Long> {
        return try {
            val type = object : TypeToken<List<Long>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// ─── Internal Data Classes ───

private data class DownloadTask(
    val job: Job,
    val isPaused: AtomicBoolean,
    var downloadedBytes: AtomicLong
)

private data class ChunkRange(
    val index: Int,
    val start: Long,
    val end: Long
)

private data class ServerInfo(
    val contentLength: Long,
    val supportsRange: Boolean
)

class DownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
