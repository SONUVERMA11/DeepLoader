package com.sonuverma.deeploader.core.clipboard

import android.content.ClipboardManager
import android.content.Context
import com.sonuverma.deeploader.core.extraction.UrlPlatformDetector
import com.sonuverma.deeploader.data.prefs.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clipboard Watcher — monitors the system clipboard for supported URLs.
 *
 * When a supported URL is detected in the clipboard, emits it through
 * [detectedUrl] StateFlow for the UI to show a banner prompt.
 *
 * Respects user preference — can be disabled in Settings.
 * Only triggers once per unique URL to avoid repeated prompts.
 *
 * Developer: Sonu Verma
 */
@Singleton
class ClipboardWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platformDetector: UrlPlatformDetector,
    private val prefs: AppPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // The most recently detected URL from clipboard
    private val _detectedUrl = MutableStateFlow<ClipboardDetection?>(null)
    val detectedUrl: StateFlow<ClipboardDetection?> = _detectedUrl.asStateFlow()

    // Track last detected URL to avoid duplicate prompts
    private var lastDetectedUrl: String? = null

    // Clipboard listener reference for unregistration
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    /**
     * Start watching the clipboard for URL changes.
     * Called when the app comes to the foreground.
     */
    fun startWatching() {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return

        // Remove any existing listener first
        stopWatching()

        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            scope.launch {
                checkClipboard(clipboardManager)
            }
        }

        clipboardManager.addPrimaryClipChangedListener(clipboardListener)

        // Also check current clipboard content immediately
        scope.launch {
            checkClipboard(clipboardManager)
        }
    }

    /**
     * Stop watching the clipboard.
     * Called when the app goes to the background.
     */
    fun stopWatching() {
        clipboardListener?.let { listener ->
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboardManager?.removePrimaryClipChangedListener(listener)
        }
        clipboardListener = null
    }

    /**
     * Check the current clipboard content for a supported URL.
     */
    private suspend fun checkClipboard(clipboardManager: ClipboardManager) {
        // Respect user preference
        val isEnabled = prefs.clipboardWatcher.first()
        if (!isEnabled) return

        try {
            val clip = clipboardManager.primaryClip ?: return
            if (clip.itemCount == 0) return

            val text = clip.getItemAt(0)?.text?.toString()?.trim() ?: return

            // Must be a valid URL
            if (!platformDetector.isValidUrl(text)) return

            // Skip if we already detected this exact URL
            if (text == lastDetectedUrl) return

            // Detect the platform
            val detection = platformDetector.detect(text)
            lastDetectedUrl = text

            _detectedUrl.value = ClipboardDetection(
                url = text,
                platform = detection.platform,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: SecurityException) {
            // Android 10+ restricts clipboard access in the background
            // This is expected — we can only read clipboard when the app is focused
        } catch (e: Exception) {
            // Silently ignore clipboard read errors
        }
    }

    /**
     * Dismiss the current clipboard detection.
     * Called when the user dismisses the banner or starts a download.
     */
    fun dismissDetection() {
        _detectedUrl.value = null
    }

    /**
     * Force check the clipboard now.
     * Used when the app comes to the foreground.
     */
    fun checkNow() {
        scope.launch {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return@launch
            checkClipboard(clipboardManager)
        }
    }
}

/**
 * Represents a detected URL from the clipboard.
 */
data class ClipboardDetection(
    val url: String,
    val platform: com.sonuverma.deeploader.data.models.Platform,
    val timestamp: Long
)
