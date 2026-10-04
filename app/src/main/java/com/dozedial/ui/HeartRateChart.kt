package com.dozedial.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dozedial.data.db.HeartRateRecordEntity
import kotlin.math.roundToInt

/**
 * High-performance canvas chart for displaying overnight heart rate time-series data.
 * Supports intelligent downsampling to render tens of thousands of points smoothly at 60 FPS.
 */
@Composable
fun HeartRateChart(
    records: List<HeartRateRecordEntity>,
    modifier: Modifier = Modifier
) {
    if (records.isEmpty()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No heart rate records to display",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // Downsample points to ~600 data points maximum for smooth, crisp Canvas rendering
    val downsampled = remember(records) {
        val maxPoints = 600
        if (records.size <= maxPoints) {
            records
        } else {
            val step = records.size.toDouble() / maxPoints.toDouble()
            val list = mutableListOf<HeartRateRecordEntity>()
            var curr = 0.0
            while (curr < records.size) {
                list.add(records[curr.toInt()])
                curr += step
            }
            list
        }
    }

    val validBpmValues = remember(records) { records.map { it.bpm }.filter { it > 0 } }
    val minBpm = remember(validBpmValues) { (validBpmValues.minOrNull() ?: 45).coerceAtLeast(30) }
    val maxBpm = remember(validBpmValues) { (validBpmValues.maxOrNull() ?: 100).coerceAtMost(220) }
    val avgBpm = remember(validBpmValues) {
        if (validBpmValues.isNotEmpty()) validBpmValues.average().roundToInt() else 0
    }

    val chartLineColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val avgLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)

    Column(modifier = modifier) {
        // Legend & Bounds summary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Min: $minBpm bpm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Avg: $avgBpm bpm",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Max: $maxBpm bpm",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Canvas curve
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            val w = size.width
            val h = size.height
            val verticalPadding = 16f
            val usableHeight = (h - verticalPadding * 2f).coerceAtLeast(10f)

            // Draw horizontal guide lines (Min, Mid, Max)
            val bpmSpan = (maxBpm - minBpm).coerceAtLeast(10)

            // Min guide line
            drawLine(
                color = gridColor,
                start = Offset(0f, h - verticalPadding),
                end = Offset(w, h - verticalPadding),
                strokeWidth = 1f
            )

            // Max guide line
            drawLine(
                color = gridColor,
                start = Offset(0f, verticalPadding),
                end = Offset(w, verticalPadding),
                strokeWidth = 1f
            )

            // Average guide line (dashed look via small segments or solid)
            val avgRatio = (avgBpm - minBpm).toFloat() / bpmSpan.toFloat()
            val avgY = (h - verticalPadding) - (avgRatio * usableHeight)
            drawLine(
                color = avgLineColor,
                start = Offset(0f, avgY),
                end = Offset(w, avgY),
                strokeWidth = 1.5f
            )

            if (downsampled.size < 2) {
                // Just 1 point
                val y = (h - verticalPadding) - (((downsampled[0].bpm - minBpm).toFloat() / bpmSpan) * usableHeight)
                drawCircle(color = chartLineColor, radius = 4f, center = Offset(w / 2f, y))
                return@Canvas
            }

            val strokePath = Path()
            val fillPath = Path()

            val xStep = w / (downsampled.size - 1).toFloat()

            for (i in downsampled.indices) {
                val bpm = downsampled[i].bpm.coerceIn(minBpm, maxBpm)
                val ratio = (bpm - minBpm).toFloat() / bpmSpan.toFloat()
                val x = i * xStep
                val y = (h - verticalPadding) - (ratio * usableHeight)

                if (i == 0) {
                    strokePath.moveTo(x, y)
                    fillPath.moveTo(x, h)
                    fillPath.lineTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }

                if (i == downsampled.size - 1) {
                    fillPath.lineTo(x, h)
                    fillPath.close()
                }
            }

            // Fill gradient below curve
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        chartLineColor.copy(alpha = 0.25f),
                        chartLineColor.copy(alpha = 0.02f)
                    ),
                    startY = verticalPadding,
                    endY = h
                )
            )

            // Draw line curve
            drawPath(
                path = strokePath,
                color = chartLineColor,
                style = Stroke(
                    width = 2.5f,
                    cap = StrokeCap.Round
                )
            )
        }
    }
}
