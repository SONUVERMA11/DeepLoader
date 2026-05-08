package com.sonuverma.deeploader.core.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.util.Rational
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PipController — Manages Picture-in-Picture mode for video playback.
 *
 * Provides:
 *   - PiP entry when user navigates away during video playback
 *   - Auto-PiP on home button press (Android 12+)
 *   - Aspect ratio calculation from video dimensions
 *   - PiP support detection (not available on all devices)
 *
 * Usage:
 *   - Call enterPip() when user presses home during video playback
 *   - Call updatePipParams() when video aspect ratio changes
 *
 * Developer: Sonu Verma
 */
@Singleton
class PipController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "PipController"
        private val DEFAULT_ASPECT_RATIO = Rational(16, 9)
    }

    /**
     * Check if the device supports PiP mode.
     */
    fun isPipSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
               context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    /**
     * Enter PiP mode for the given activity.
     *
     * @param activity The current activity
     * @param videoWidth Video width in pixels (for aspect ratio)
     * @param videoHeight Video height in pixels (for aspect ratio)
     */
    fun enterPip(activity: Activity, videoWidth: Int = 16, videoHeight: Int = 9) {
        if (!isPipSupported()) {
            Log.w(TAG, "PiP not supported on this device")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
                Rational(videoWidth, videoHeight)
            } else {
                DEFAULT_ASPECT_RATIO
            }

            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setAutoEnterEnabled(true)
                        setSeamlessResizeEnabled(true)
                    }
                }
                .build()

            try {
                activity.enterPictureInPictureMode(params)
                Log.i(TAG, "Entered PiP mode ($videoWidth x $videoHeight)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enter PiP: ${e.message}")
            }
        }
    }

    /**
     * Update PiP parameters (e.g., when video aspect ratio changes).
     */
    fun updatePipParams(activity: Activity, videoWidth: Int, videoHeight: Int) {
        if (!isPipSupported() || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
            Rational(videoWidth, videoHeight)
        } else {
            DEFAULT_ASPECT_RATIO
        }

        val params = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(true)
                }
            }
            .build()

        try {
            activity.setPictureInPictureParams(params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update PiP params: ${e.message}")
        }
    }

    /**
     * Set auto-PiP behavior for Android 12+.
     * When enabled, app automatically enters PiP when user presses home.
     */
    fun setAutoPipEnabled(activity: Activity, enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(DEFAULT_ASPECT_RATIO)
                .setAutoEnterEnabled(enabled)
                .build()

            try {
                activity.setPictureInPictureParams(params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set auto-PiP: ${e.message}")
            }
        }
    }
}
