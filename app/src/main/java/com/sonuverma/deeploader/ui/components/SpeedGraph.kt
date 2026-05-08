package com.sonuverma.deeploader.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sonuverma.deeploader.ui.theme.DeepLoaderColors

/**
 * Real-time speed graph — shows download speed over time.
 *
 * Displays a smooth bezier curve of speed history with:
 * - Gradient fill under the curve
 * - Current speed badge
 * - Peak speed indicator
 * - Color-coded speed levels (slow/medium/fast/ludicrous)
 *
 * Developer: Sonu Verma
 */
@Composable
fun SpeedGraph(
    speedHistory: List<Long>, // bytes per second, most recent last
    currentSpeed: Long,       // current speed in bytes/sec
    modifier: Modifier = Modifier
) {
    val maxSpeed = remember(speedHistory) {
        (speedHistory.maxOrNull() ?: 0L).coerceAtLeast(1024L) // Min 1 KB/s scale
    }

    val speedColor = remember(currentSpeed) {
        when {
            currentSpeed > 50 * 1024 * 1024 -> DeepLoaderColors.SpeedLudicrous  // > 50 MB/s
            currentSpeed > 10 * 1024 * 1024 -> DeepLoaderColors.SpeedFast       // > 10 MB/s
            currentSpeed > 1024 * 1024 -> DeepLoaderColors.SpeedMedium          // > 1 MB/s
            else -> DeepLoaderColors.SpeedSlow                                   // < 1 MB/s
        }
    }

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
                .padding(16.dp)
        ) {
            // Header row — title + current speed badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Download Speed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Current speed badge
                Text(
                    text = formatSpeed(currentSpeed),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = speedColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Peak speed
            Text(
                text = "Peak: ${formatSpeed(maxSpeed)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Speed graph canvas
            if (speedHistory.isNotEmpty()) {
                SpeedGraphCanvas(
                    speeds = speedHistory,
                    maxSpeed = maxSpeed,
                    lineColor = speedColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                )
            }
        }
    }
}

@Composable
private fun SpeedGraphCanvas(
    speeds: List<Long>,
    maxSpeed: Long,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier) {
        if (speeds.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val padding = 4f

        val effectiveHeight = height - padding * 2
        val stepX = if (speeds.size > 1) width / (speeds.size - 1).toFloat() else width

        // Build path for the speed line
        val linePath = Path()
        val fillPath = Path()

        speeds.forEachIndexed { index, speed ->
            val x = index * stepX
            val normalizedSpeed = (speed.toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)
            val y = height - padding - (normalizedSpeed * effectiveHeight)

            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, height) // Start fill from bottom
                fillPath.lineTo(x, y)
            } else {
                // Smooth bezier curve between points
                val prevX = (index - 1) * stepX
                val prevSpeed = (speeds[index - 1].toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)
                val prevY = height - padding - (prevSpeed * effectiveHeight)
                val controlX = (prevX + x) / 2f

                linePath.cubicTo(controlX, prevY, controlX, y, x, y)
                fillPath.cubicTo(controlX, prevY, controlX, y, x, y)
            }
        }

        // Close fill path
        fillPath.lineTo(width, height)
        fillPath.close()

        // Draw gradient fill under the curve
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.2f),
                    lineColor.copy(alpha = 0.02f)
                )
            )
        )

        // Draw grid lines (subtle)
        val gridLines = 3
        for (i in 1..gridLines) {
            val y = padding + (effectiveHeight * i / (gridLines + 1))
            drawLine(
                color = surfaceVariant,
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 0.5f
            )
        }

        // Draw the speed line
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(
                width = 2.5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Draw current speed dot at the end
        if (speeds.isNotEmpty()) {
            val lastX = (speeds.size - 1) * stepX
            val lastNormalized = (speeds.last().toFloat() / maxSpeed.toFloat()).coerceIn(0f, 1f)
            val lastY = height - padding - (lastNormalized * effectiveHeight)

            // Outer glow
            drawCircle(
                color = lineColor.copy(alpha = 0.3f),
                radius = 6f,
                center = Offset(lastX, lastY)
            )
            // Inner dot
            drawCircle(
                color = lineColor,
                radius = 3.5f,
                center = Offset(lastX, lastY)
            )
        }
    }
}

/**
 * Format bytes/sec into human-readable speed string.
 */
private fun formatSpeed(bytesPerSecond: Long): String {
    return when {
        bytesPerSecond <= 0 -> "0 B/s"
        bytesPerSecond < 1024 -> "${bytesPerSecond} B/s"
        bytesPerSecond < 1024 * 1024 -> "${"%.1f".format(bytesPerSecond.toDouble() / 1024)} KB/s"
        bytesPerSecond < 1024L * 1024 * 1024 -> "${"%.1f".format(bytesPerSecond.toDouble() / (1024 * 1024))} MB/s"
        else -> "${"%.2f".format(bytesPerSecond.toDouble() / (1024L * 1024 * 1024))} GB/s"
    }
}
