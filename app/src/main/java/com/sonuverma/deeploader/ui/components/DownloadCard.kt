package com.sonuverma.deeploader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonuverma.deeploader.data.db.DownloadEntity
import com.sonuverma.deeploader.data.models.DownloadStatus
import com.sonuverma.deeploader.ui.theme.DeepLoaderColors

/**
 * Download Card — shows individual download progress.
 *
 * Features:
 * - Thumbnail with platform color indicator
 * - Title with marquee-like overflow
 * - Animated progress bar (continuous, not segmented)
 * - Real-time speed badge (e.g. "12.4 MB/s")
 * - ETA display
 * - Pause/Resume/Retry/Cancel action buttons
 * - Status-aware styling (active=blue, complete=green, error=red)
 *
 * Developer: Sonu Verma
 */
@Composable
fun DownloadCard(
    download: DownloadEntity,
    modifier: Modifier = Modifier,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onRetry: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val status = try {
        DownloadStatus.valueOf(download.status)
    } catch (e: Exception) {
        DownloadStatus.QUEUED
    }

    val progress = if (download.totalBytes > 0) {
        download.downloadedBytes.toFloat() / download.totalBytes.toFloat()
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300),
        label = "progress"
    )

    val statusColor by animateColorAsState(
        targetValue = when (status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.MERGING -> DeepLoaderColors.PrimaryBlue
            DownloadStatus.COMPLETED -> DeepLoaderColors.AccentGreen
            DownloadStatus.FAILED -> DeepLoaderColors.AccentRed
            DownloadStatus.PAUSED -> DeepLoaderColors.AccentOrange
            DownloadStatus.CONVERTING -> DeepLoaderColors.AccentPurple
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "statusColor"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Thumbnail
                Card(
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(width = 72.dp, height = 48.dp)
                ) {
                    Box {
                        AsyncImage(
                            model = download.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 72.dp, height = 48.dp)
                        )
                        // Platform color dot
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title and status
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.title.ifEmpty { "Untitled" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    // Status row — speed + ETA + status text
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = status.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )

                        if (download.quality.isNotEmpty()) {
                            Text(
                                text = "• ${download.quality}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Action button
                when (status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.MERGING, DownloadStatus.CONVERTING -> {
                        IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Pause,
                                contentDescription = "Pause",
                                tint = statusColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Resume",
                                tint = DeepLoaderColors.AccentGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    DownloadStatus.FAILED -> {
                        IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Retry",
                                tint = DeepLoaderColors.AccentOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Completed",
                            tint = DeepLoaderColors.AccentGreen,
                            modifier = Modifier
                                .size(28.dp)
                                .padding(4.dp)
                        )
                    }
                    else -> {} // No action button for queued/cancelled
                }
            }

            // Progress bar — only show for active/paused downloads
            if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PAUSED ||
                status == DownloadStatus.MERGING || status == DownloadStatus.CONVERTING ||
                status == DownloadStatus.SAVING
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = statusColor,
                    trackColor = statusColor.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round
                )

                // Progress details row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )

                    val sizeText = buildString {
                        if (download.downloadedBytes > 0) {
                            append(formatBytes(download.downloadedBytes))
                        }
                        if (download.totalBytes > 0) {
                            append(" / ${formatBytes(download.totalBytes)}")
                        }
                    }
                    if (sizeText.isNotEmpty()) {
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Error message
            if (status == DownloadStatus.FAILED && download.errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Error,
                        contentDescription = null,
                        tint = DeepLoaderColors.AccentRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = download.errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = DeepLoaderColors.AccentRed,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Format bytes into human-readable string.
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / 1024)}KB"
        bytes < 1024L * 1024 * 1024 -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))}MB"
        else -> "${"%.2f".format(bytes.toDouble() / (1024L * 1024 * 1024))}GB"
    }
}
