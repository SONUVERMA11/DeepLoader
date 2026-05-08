package com.sonuverma.deeploader.core.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaStoreSaver — Saves downloaded files to the device's MediaStore.
 *
 * On Android 10+ (API 29+), uses MediaStore API for scoped storage.
 * On Android 9 and below, copies directly to public directories.
 *
 * Files are saved to:
 *   - Videos → Movies/DeepLoader/
 *   - Audio  → Music/DeepLoader/
 *   - Other  → Download/DeepLoader/
 *
 * After saving, the file appears in the device gallery, file manager,
 * and music apps automatically.
 *
 * Developer: Sonu Verma
 */
@Singleton
class MediaStoreSaver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MediaStoreSaver"
        private const val SUBFOLDER = "DeepLoader"
        private const val BUFFER_SIZE = 8192
    }

    /**
     * Save a downloaded file to the device's MediaStore.
     *
     * @param file The downloaded file to save
     * @param displayName User-visible filename (without extension)
     * @param mimeType MIME type of the file
     * @return MediaStore URI of the saved file
     */
    fun saveToMediaStore(file: File, displayName: String, mimeType: String): Uri? {
        if (!file.exists()) {
            Log.e(TAG, "File does not exist: ${file.absolutePath}")
            return null
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStoreApi(file, displayName, mimeType)
        } else {
            saveToPublicDirectory(file, displayName, mimeType)
        }
    }

    /**
     * Android 10+ (API 29+): Use MediaStore insert + content resolver.
     * No WRITE_EXTERNAL_STORAGE permission needed.
     */
    private fun saveWithMediaStoreApi(file: File, displayName: String, mimeType: String): Uri? {
        val resolver = context.contentResolver

        // Determine target collection based on MIME type
        val (collection, relativePath) = when {
            mimeType.startsWith("video/") -> {
                val col = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                col to "${Environment.DIRECTORY_MOVIES}/$SUBFOLDER"
            }
            mimeType.startsWith("audio/") -> {
                val col = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                col to "${Environment.DIRECTORY_MUSIC}/$SUBFOLDER"
            }
            else -> {
                val col = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Files.getContentUri("external")
                }
                col to "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER"
            }
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${displayName}.${file.extension}")
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.SIZE, file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(collection, contentValues) ?: run {
            Log.e(TAG, "Failed to insert into MediaStore")
            return null
        }

        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                FileInputStream(file).use { inputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }

            // Mark as no longer pending
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Log.i(TAG, "Saved to MediaStore: $uri")
            return uri

        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to MediaStore", e)
            // Clean up failed entry
            resolver.delete(uri, null, null)
            return null
        }
    }

    /**
     * Android 9 and below: Copy file to public directory.
     * Requires WRITE_EXTERNAL_STORAGE permission.
     */
    @Suppress("DEPRECATION")
    private fun saveToPublicDirectory(file: File, displayName: String, mimeType: String): Uri? {
        val publicDir = when {
            mimeType.startsWith("video/") ->
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), SUBFOLDER)
            mimeType.startsWith("audio/") ->
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), SUBFOLDER)
            else ->
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), SUBFOLDER)
        }

        publicDir.mkdirs()
        val destFile = File(publicDir, "${displayName}.${file.extension}")

        return try {
            file.copyTo(destFile, overwrite = true)
            Log.i(TAG, "Saved to public directory: ${destFile.absolutePath}")
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to public directory", e)
            null
        }
    }

    /**
     * Get the DeepLoader download directory for internal storage.
     * Used for temp files and as default download location.
     */
    fun getDownloadDirectory(): File {
        val externalDir = context.getExternalFilesDir(null)
        val dir = if (externalDir != null) {
            File(externalDir, SUBFOLDER)
        } else {
            File(context.filesDir, SUBFOLDER)
        }
        dir.mkdirs()
        return dir
    }

    /**
     * Calculate total size of all files in the download directory.
     */
    fun getUsedStorage(): Long {
        return getDownloadDirectory().walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    /**
     * Get count of files in the download directory.
     */
    fun getFileCount(): Int {
        return getDownloadDirectory().walkTopDown()
            .filter { it.isFile }
            .count()
    }
}
