package com.sonuverma.deeploader.ui.components

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sonuverma.deeploader.data.models.TorrentInfo
import com.sonuverma.deeploader.data.models.TorrentStatus
import com.sonuverma.deeploader.ui.theme.DeepLoaderColors

/**
 * TorrentCard — Displays an active torrent with progress, speed, and peer info.
 *
 * Features:
 *   - Real-time progress bar with percentage
 *   - Download/upload speed indicators
 *   - Connected peers and seeder count
 *   - Streaming badge when sequential mode is active
 *   - Pause/resume/stream/delete action buttons
 *   - Status-based color coding
 *
 * Developer: Sonu Verma
 */
@Composable
fun TorrentCard(
    torrent: TorrentInfo,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onStream: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val statusColor = when (torrent.status) {
        TorrentStatus.DOWNLOADING -> DeepLoaderColors.AccentGreen
        TorrentStatus.SEEDING -> DeepLoaderColors.PrimaryBlue
        TorrentStatus.PAUSED -> DeepLoaderColors.AccentOrange
        TorrentStatus.COMPLETED -> DeepLoaderColors.AccentCyan
        TorrentStatus.ERROR -> DeepLoaderColors.AccentRed
        TorrentStatus.CHECKING -> DeepLoaderColors.AccentPurple
        TorrentStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // ─── Top Row: Name + Status Badge ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = torrent.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Status badge
                        StatusBadge(
                            text = torrent.status.displayName,
                            color = statusColor
                        )

                        // Streaming badge
                        if (torrent.isStreaming) {
                            StatusBadge(
                                text = "Streaming",
                                color = DeepLoaderColors.AccentPink
                            )
                        }

                        // Category badge
                        Text(
                            text = torrent.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Size display
                Text(
                    text = torrent.displaySize,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ─── Progress Bar ───
            if (torrent.status == TorrentStatus.DOWNLOADING ||
                torrent.status == TorrentStatus.CHECKING ||
                torrent.status == TorrentStatus.PAUSED) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { torrent.downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = statusColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "${(torrent.downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // ─── Speed + Peers Row ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Speed indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Download speed
                    if (torrent.downloadSpeed > 0) {
                        SpeedIndicator(
                            icon = Icons.Filled.ArrowDownward,
                            speed = formatSpeed(torrent.downloadSpeed),
                            color = DeepLoaderColors.AccentGreen
                        )
                    }

                    // Upload speed
                    if (torrent.uploadSpeed > 0) {
                        SpeedIndicator(
                            icon = Icons.Filled.ArrowUpward,
                            speed = formatSpeed(torrent.uploadSpeed),
                            color = DeepLoaderColors.PrimaryBlue
                        )
                    }

                    // Peers
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Group,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${torrent.seeders}S / ${torrent.connectedPeers}P",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action buttons
                Row {
                    if (torrent.status == TorrentStatus.DOWNLOADING) {
                        IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.Pause,
                                contentDescription = "Pause",
                                tint = DeepLoaderColors.AccentOrange,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (torrent.status == TorrentStatus.PAUSED) {
                        IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Resume",
                                tint = DeepLoaderColors.AccentGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Stream button (for video torrents)
                    if (torrent.isStreaming || torrent.category == com.sonuverma.deeploader.data.models.TorrentCategory.VIDEO) {
                        IconButton(onClick = onStream, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.PlayCircle,
                                contentDescription = "Stream",
                                tint = DeepLoaderColors.AccentPink,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Remove",
                            tint = DeepLoaderColors.AccentRed.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Search result card for torrent search — shows name, size, seeders, and source.
 */
@Composable
fun TorrentSearchResultCard(
    torrent: TorrentInfo,
    onDownload: () -> Unit = {},
    onStream: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Name
            Text(
                text = torrent.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Size
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Cloud,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = torrent.displaySize,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Seeders (green)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = DeepLoaderColors.AccentGreen
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${torrent.seeders}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = DeepLoaderColors.AccentGreen
                        )
                    }

                    // Leechers (orange)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = DeepLoaderColors.AccentOrange
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${torrent.leechers}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepLoaderColors.AccentOrange
                        )
                    }

                    // Source badge
                    StatusBadge(
                        text = torrent.source.displayName,
                        color = DeepLoaderColors.AccentPurple
                    )
                }

                // Action buttons
                Row {
                    IconButton(onClick = onStream, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.PlayCircle,
                            contentDescription = "Stream",
                            tint = DeepLoaderColors.AccentPink,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = "Download",
                            tint = DeepLoaderColors.AccentGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Private Composables ───

@Composable
private fun StatusBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
private fun SpeedIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    speed: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = speed,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec < 1024 -> "$bytesPerSec B/s"
        bytesPerSec < 1024 * 1024 -> "${"%.1f".format(bytesPerSec / 1024.0)} KB/s"
        else -> "${"%.1f".format(bytesPerSec / (1024.0 * 1024))} MB/s"
    }
}
