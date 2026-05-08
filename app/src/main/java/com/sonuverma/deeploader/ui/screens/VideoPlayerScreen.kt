package com.sonuverma.deeploader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.sonuverma.deeploader.core.player.PlayerManager
import com.sonuverma.deeploader.core.player.PlayerState
import com.sonuverma.deeploader.core.player.PipController
import com.sonuverma.deeploader.ui.theme.DeepLoaderColors
import kotlinx.coroutines.delay

/**
 * VideoPlayerScreen — Full-screen immersive video player.
 *
 * Features:
 *   - Auto-hiding overlay controls (show on tap, hide after 3s)
 *   - Custom seek bar with buffered position indicator
 *   - Play/Pause/Seek ±10s controls
 *   - Playback speed selector (0.5x – 2.0x)
 *   - Picture-in-Picture button
 *   - Background audio toggle
 *   - Duration / remaining time display
 *   - Gradient overlays for control visibility
 *
 * Developer: Sonu Verma
 */
@Composable
fun VideoPlayerScreen(
    playerManager: PlayerManager,
    pipController: PipController,
    onBack: () -> Unit = {}
) {
    val playbackState by playerManager.playbackState.collectAsState()
    var showControls by remember { mutableStateOf(true) }
    var showSpeedSelector by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Auto-hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls && playbackState.isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Re-show controls when playback pauses
    LaunchedEffect(playbackState.isPlaying) {
        if (!playbackState.isPlaying) {
            showControls = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
                showSpeedSelector = false
            }
    ) {
        // ─── Video Surface ───
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = playerManager.player
                    useController = false // We use our own custom controls
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.player = playerManager.player
            }
        )

        // ─── Buffering Indicator ───
        if (playbackState.state == PlayerState.BUFFERING) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }

        // ─── Overlay Controls ───
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top gradient + header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Back button
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }

                        // Title
                        Text(
                            text = playbackState.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )

                        // PiP button
                        if (pipController.isPipSupported()) {
                            IconButton(onClick = {
                                val activity = context as? android.app.Activity
                                activity?.let { pipController.enterPip(it) }
                            }) {
                                Icon(
                                    Icons.Filled.PictureInPicture,
                                    contentDescription = "Picture in Picture",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Center play/pause controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = { playerManager.seekBack() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            Icons.Filled.FastRewind,
                            contentDescription = "Rewind 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Play/Pause
                    IconButton(
                        onClick = {
                            if (playbackState.state == PlayerState.ENDED) {
                                playerManager.seekTo(0)
                                playerManager.play()
                            } else {
                                playerManager.togglePlayPause()
                            }
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            when (playbackState.state) {
                                PlayerState.ENDED -> Icons.Filled.Replay
                                else -> if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow
                            },
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Forward 10s
                    IconButton(
                        onClick = { playerManager.seekForward() },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            Icons.Filled.FastForward,
                            contentDescription = "Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom gradient + seek bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Seek bar
                    Slider(
                        value = playbackState.progress.coerceIn(0f, 1f),
                        onValueChange = { fraction ->
                            playerManager.seekTo((fraction * playbackState.durationMs).toLong())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = DeepLoaderColors.PrimaryBlue,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )

                    // Time + controls row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Time display
                        Text(
                            text = "${playbackState.displayPosition} / ${playbackState.displayDuration}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )

                        // Bottom controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Speed selector
                            IconButton(
                                onClick = { showSpeedSelector = !showSpeedSelector },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Speed,
                                    contentDescription = "Speed",
                                    tint = if (playbackState.playbackSpeed != 1.0f)
                                        DeepLoaderColors.AccentCyan
                                    else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Background audio toggle
                            IconButton(
                                onClick = {
                                    val current = playerManager.isBackgroundMode.value
                                    playerManager.setBackgroundMode(!current)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Filled.GraphicEq,
                                    contentDescription = "Background Audio",
                                    tint = if (playerManager.isBackgroundMode.value)
                                        DeepLoaderColors.AccentGreen
                                    else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Speed selector popup
                AnimatedVisibility(
                    visible = showSpeedSelector,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 100.dp)
                ) {
                    SpeedSelector(
                        currentSpeed = playbackState.playbackSpeed,
                        onSpeedSelected = { speed ->
                            playerManager.setPlaybackSpeed(speed)
                            showSpeedSelector = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedSelector(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        speeds.forEach { speed ->
            val isSelected = speed == currentSpeed
            Text(
                text = "${speed}x",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) DeepLoaderColors.AccentCyan else Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSpeedSelected(speed) }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            )
        }
    }
}
