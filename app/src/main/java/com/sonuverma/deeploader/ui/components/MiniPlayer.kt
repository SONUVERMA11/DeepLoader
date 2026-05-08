package com.sonuverma.deeploader.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonuverma.deeploader.core.player.PlaybackState
import com.sonuverma.deeploader.core.player.PlayerState
import com.sonuverma.deeploader.core.player.SourceType
import com.sonuverma.deeploader.ui.theme.DeepLoaderColors

/**
 * MiniPlayer — Compact playback bar shown above the bottom navigation.
 *
 * Displays when audio/video is playing in the background:
 *   - Title with source type indicator
 *   - Play/Pause button
 *   - Close button
 *   - Thin progress bar
 *   - Tap to expand to full player
 *
 * Slides in/out with animation.
 *
 * Developer: Sonu Verma
 */
@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    isVisible: Boolean,
    onTogglePlayPause: () -> Unit = {},
    onClose: () -> Unit = {},
    onExpand: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = isVisible && playbackState.state != PlayerState.IDLE,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onExpand() }
        ) {
            // Thin progress bar at the top
            LinearProgressIndicator(
                progress = { playbackState.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = DeepLoaderColors.PrimaryBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Source icon + title
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Source type indicator
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                when (playbackState.sourceType) {
                                    SourceType.STREAM -> DeepLoaderColors.PrimaryBlue.copy(alpha = 0.15f)
                                    SourceType.LOCAL -> DeepLoaderColors.AccentGreen.copy(alpha = 0.15f)
                                    SourceType.TORRENT -> DeepLoaderColors.AccentPurple.copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = when (playbackState.sourceType) {
                                SourceType.STREAM -> DeepLoaderColors.PrimaryBlue
                                SourceType.LOCAL -> DeepLoaderColors.AccentGreen
                                SourceType.TORRENT -> DeepLoaderColors.AccentPurple
                            }
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playbackState.title.ifEmpty { "Playing..." },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${playbackState.displayPosition} / ${playbackState.displayDuration}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
