package com.sonuverma.deeploader.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sonuverma.deeploader.data.models.ExtractionResult
import com.sonuverma.deeploader.data.models.QualityTier
import com.sonuverma.deeploader.data.models.StreamFormat
import com.sonuverma.deeploader.ui.theme.DeepLoaderColors

/**
 * Format Selection Bottom Sheet — iOS-style modal sheet.
 *
 * Shows extracted video/audio streams grouped by quality tier.
 * User selects a format then taps "Download" to begin.
 * Includes thumbnail, title, uploader info, and MP3 conversion toggle.
 *
 * Developer: Sonu Verma
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatBottomSheet(
    result: ExtractionResult,
    onDismiss: () -> Unit,
    onDownload: (StreamFormat, StreamFormat?, Boolean) -> Unit, // video, audio (for merge), convertToMp3
    onPlay: ((StreamFormat) -> Unit)? = null // Optional: play stream directly
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFormat by remember { mutableStateOf<StreamFormat?>(null) }
    var convertToMp3 by remember { mutableStateOf(false) }

    val qualityTiers = remember(result) { result.getQualityTiers() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Media Info Header ───
            item {
                MediaInfoHeader(result = result)
            }

            // ─── Quality Tiers ───
            qualityTiers.forEach { tier ->
                item {
                    Text(
                        text = tier.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(tier.streams) { stream ->
                    FormatRow(
                        stream = stream,
                        isSelected = selectedFormat == stream,
                        onClick = {
                            selectedFormat = stream
                            // Auto-enable MP3 conversion for audio-only formats
                            if (stream.isAudioOnly) convertToMp3 = false
                        }
                    )
                }
            }

            // ─── MP3 Conversion Toggle ───
            item {
                if (selectedFormat != null && selectedFormat?.isAudioOnly == false) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { convertToMp3 = !convertToMp3 },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (convertToMp3)
                                DeepLoaderColors.AccentPurple.copy(alpha = 0.12f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = DeepLoaderColors.AccentPurple,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Convert to MP3",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Extract audio as 320kbps MP3",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (convertToMp3) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = DeepLoaderColors.AccentPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ─── Action Buttons ───
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Play button (stream directly)
                    if (onPlay != null) {
                        Button(
                            onClick = {
                                selectedFormat?.let { format -> onPlay(format) }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = selectedFormat != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DeepLoaderColors.AccentPurple,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            )
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Download button
                    Button(
                        onClick = {
                            selectedFormat?.let { format ->
                                val audioForMerge = if (format.isVideoOnly) {
                                    result.getBestAudioStream()
                                } else null
                                onDownload(format, audioForMerge, convertToMp3)
                            }
                        },
                        modifier = Modifier
                            .weight(if (onPlay != null) 1.5f else 1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = selectedFormat != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedFormat != null) {
                                val size = selectedFormat?.displaySize ?: ""
                                "Download${if (size.isNotEmpty() && size != "Unknown size") " • $size" else ""}"
                            } else {
                                "Select a format"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Bottom spacing
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MediaInfoHeader(result: ExtractionResult) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Thumbnail
        Card(
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.size(width = 120.dp, height = 68.dp)
        ) {
            AsyncImage(
                model = result.thumbnailUrl,
                contentDescription = result.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.uploaderName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (result.duration > 0) {
                Text(
                    text = formatDuration(result.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun FormatRow(
    stream: StreamFormat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Format icon
            Icon(
                imageVector = if (stream.isAudioOnly) Icons.Filled.AudioFile else Icons.Filled.VideoFile,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Format info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stream.displayQuality,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (stream.displayCodec.isNotEmpty()) {
                        Text(
                            text = stream.displayCodec,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (stream.fps > 30) {
                        Text(
                            text = "${stream.fps}fps",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepLoaderColors.AccentGreen
                        )
                    }
                    if (stream.isVideoOnly) {
                        Text(
                            text = "Video only",
                            style = MaterialTheme.typography.labelSmall,
                            color = DeepLoaderColors.AccentOrange
                        )
                    }
                }
            }

            // File size
            Text(
                text = stream.displaySize,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Selection indicator
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Format seconds into human-readable duration string.
 */
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
