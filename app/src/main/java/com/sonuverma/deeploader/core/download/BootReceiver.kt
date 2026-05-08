package com.sonuverma.deeploader.core.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

/**
 * Receives BOOT_COMPLETED broadcast to resume paused downloads
 * after device restart. WorkManager handles the actual resume logic.
 * Developer: Sonu Verma
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // WorkManager automatically reschedules pending work after boot.
            // This receiver exists as an explicit hook for any additional
            // post-boot initialization we may need in the future.
            android.util.Log.i("DeepLoader", "Boot completed — WorkManager will resume pending downloads")
        }
    }
}
