package com.sonuverma.deeploader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.sonuverma.deeploader.ui.DeepLoaderMainScreen
import com.sonuverma.deeploader.ui.theme.DeepLoaderTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point for DeepLoader.
 * Handles edge-to-edge rendering, splash screen, and share intent receiving.
 * Developer: Sonu Verma
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Shared URL received via ACTION_SEND intent
    var sharedUrl by mutableStateOf<String?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Enable edge-to-edge rendering (transparent status/nav bars)
        enableEdgeToEdge()

        // Handle incoming share intent
        handleShareIntent(intent)

        setContent {
            DeepLoaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    DeepLoaderMainScreen(
                        sharedUrl = sharedUrl,
                        onSharedUrlConsumed = { sharedUrl = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * Extracts URL from ACTION_SEND intents (share-to-download feature).
     * Validates that the shared text contains a URL before accepting.
     */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (text != null && (text.startsWith("http://") || text.startsWith("https://"))) {
                sharedUrl = text.trim()
            }
        }
    }
}
