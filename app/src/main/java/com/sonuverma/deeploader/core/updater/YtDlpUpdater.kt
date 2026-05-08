package com.sonuverma.deeploader.core.updater

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.gson.JsonParser
import com.sonuverma.deeploader.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * yt-dlp Auto-Updater.
 *
 * Checks the yt-dlp GitHub releases API for new versions and downloads
 * the correct ARM binary for the device's CPU architecture.
 *
 * Check frequency: Once per app launch (if more than 24 hours since last check)
 * Binary location: context.filesDir/yt-dlp
 *
 * Supported architectures:
 *   - arm64-v8a → yt-dlp_linux_aarch64
 *   - armeabi-v7a → yt-dlp_linux_armv7l
 *   - x86_64 → yt-dlp_linux (for emulators)
 *
 * Developer: Sonu Verma
 */
@Singleton
class YtDlpUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
    private val prefs: AppPreferences
) {
    companion object {
        private const val TAG = "YtDlpUpdater"
        private const val GITHUB_API_URL = "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val BINARY_NAME = "yt-dlp"
    }

    private val binaryFile: File
        get() = File(context.filesDir, BINARY_NAME)

    /**
     * Check for updates and download if a new version is available.
     * Skips check if less than 24 hours since last check.
     *
     * @param force Force check regardless of interval
     * @return UpdateResult indicating what happened
     */
    suspend fun checkAndUpdate(force: Boolean = false): UpdateResult = withContext(Dispatchers.IO) {
        try {
            // Skip if auto-update is disabled
            val autoUpdate = prefs.autoUpdateYtDlp.first()
            if (!autoUpdate && !force) {
                return@withContext UpdateResult.Skipped("Auto-update disabled")
            }

            // Skip if checked recently (unless forced)
            if (!force) {
                val lastCheck = prefs.ytDlpLastCheck.first()
                val timeSinceLastCheck = System.currentTimeMillis() - lastCheck
                if (timeSinceLastCheck < CHECK_INTERVAL_MS) {
                    return@withContext UpdateResult.Skipped("Checked ${timeSinceLastCheck / 3600000}h ago")
                }
            }

            // Fetch latest release info from GitHub API
            val latestRelease = fetchLatestRelease()
                ?: return@withContext UpdateResult.Failed("Could not fetch release info")

            val latestVersion = latestRelease.version
            val currentVersion = prefs.ytDlpVersion.first()

            // Update last check timestamp
            prefs.setYtDlpLastCheck(System.currentTimeMillis())

            // Check if we already have the latest version
            if (currentVersion == latestVersion && binaryFile.exists() && binaryFile.canExecute()) {
                return@withContext UpdateResult.AlreadyUpToDate(latestVersion)
            }

            // Download the binary for this device's architecture
            val downloadUrl = latestRelease.getDownloadUrlForArch()
                ?: return@withContext UpdateResult.Failed("No compatible binary for device architecture")

            Log.i(TAG, "Downloading yt-dlp $latestVersion from $downloadUrl")
            downloadBinary(downloadUrl)

            // Make binary executable
            binaryFile.setExecutable(true, false)

            // Verify the binary works
            if (!verifyBinary()) {
                binaryFile.delete()
                return@withContext UpdateResult.Failed("Downloaded binary failed verification")
            }

            // Save version
            prefs.setYtDlpVersion(latestVersion)

            Log.i(TAG, "yt-dlp updated to $latestVersion")
            UpdateResult.Updated(latestVersion)

        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            UpdateResult.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Fetch the latest release info from GitHub API.
     */
    private fun fetchLatestRelease(): ReleaseInfo? {
        val request = Request.Builder()
            .url(GITHUB_API_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val body = response.body?.string() ?: return null
        val json = JsonParser.parseString(body).asJsonObject

        val version = json.get("tag_name")?.asString ?: return null
        val assets = json.getAsJsonArray("assets") ?: return null

        val assetMap = mutableMapOf<String, String>()
        for (asset in assets) {
            val assetObj = asset.asJsonObject
            val name = assetObj.get("name")?.asString ?: continue
            val downloadUrl = assetObj.get("browser_download_url")?.asString ?: continue
            assetMap[name] = downloadUrl
        }

        return ReleaseInfo(version, assetMap)
    }

    /**
     * Download the binary from the given URL to the binary file path.
     * Uses streaming to avoid loading the entire file into memory.
     */
    private fun downloadBinary(url: String) {
        val request = Request.Builder()
            .url(url)
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Download failed: ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response body")

        // Write to a temp file first, then rename (atomic operation)
        val tempFile = File(context.filesDir, "${BINARY_NAME}.tmp")
        try {
            FileOutputStream(tempFile).use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output, bufferSize = 8192)
                }
            }

            // Rename temp to final (atomic on most filesystems)
            if (binaryFile.exists()) binaryFile.delete()
            tempFile.renameTo(binaryFile)

        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    /**
     * Verify the downloaded binary is actually executable.
     * Runs yt-dlp --version and checks for valid output.
     */
    private fun verifyBinary(): Boolean {
        return try {
            val process = ProcessBuilder(binaryFile.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()

            exitCode == 0 && output.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Binary verification failed", e)
            false
        }
    }

    /**
     * Get the current installed version of yt-dlp, or null if not installed.
     */
    suspend fun getCurrentVersion(): String? {
        val version = prefs.ytDlpVersion.first()
        return if (version == "none" || !binaryFile.exists()) null else version
    }

    /**
     * Check if the binary is installed and ready to use.
     */
    fun isInstalled(): Boolean {
        return binaryFile.exists() && binaryFile.canExecute()
    }
}

/**
 * Release info parsed from GitHub API.
 */
data class ReleaseInfo(
    val version: String,
    val assets: Map<String, String> // filename → download URL
) {
    /**
     * Get the download URL for the current device's CPU architecture.
     * Falls back to generic linux binary for x86 (emulators).
     */
    fun getDownloadUrlForArch(): String? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null

        val assetName = when {
            abi.contains("arm64") || abi.contains("aarch64") -> "yt-dlp_linux_aarch64"
            abi.contains("armeabi") || abi.contains("armv7") -> "yt-dlp_linux_armv7l"
            abi.contains("x86_64") -> "yt-dlp_linux"
            abi.contains("x86") -> "yt-dlp_linux"
            else -> return null
        }

        return assets[assetName]
    }
}

/**
 * Result of an update check.
 */
sealed class UpdateResult {
    data class Updated(val version: String) : UpdateResult()
    data class AlreadyUpToDate(val version: String) : UpdateResult()
    data class Skipped(val reason: String) : UpdateResult()
    data class Failed(val error: String) : UpdateResult()
}
