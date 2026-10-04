package com.dozedial.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dozedial.data.baseline.UserSleepBaselineEntity
import com.dozedial.data.db.HeartRateRecordEntity
import com.dozedial.engine.SleepStage
import com.dozedial.engine.SleepStageClassifier
import com.dozedial.ui.theme.DozeDialColors

/**
 * High-performance clinical hypnogram canvas chart displaying sleep stage progression
 * (Wake, REM, Light, Deep) over time for an overnight recording session.
 */
@Composable
fun HypnogramChart(
    records: List<HeartRateRecordEntity>,
    baseline: UserSleepBaselineEntity = UserSleepBaselineEntity.default(),
    modifier: Modifier = Modifier
) {
    if (records.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DozeDialColors.CardBackground.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No sleep stage epochs available",
                style = MaterialTheme.typography.bodyMedium,
                color = DozeDialColors.TextSecondary
            )
        }
        return
    }

    // Classify into 30s epochs
    val epochs = remember(records, baseline) {
        SleepStageClassifier.classifySessionTimeline(records, baseline, epochDurationSec = 30)
    }

    if (epochs.isEmpty()) {
        return
    }

    val deepCount = epochs.count { it.stage == SleepStage.DEEP }
    val lightCount = epochs.count { it.stage == SleepStage.LIGHT }
    val remCount = epochs.count { it.stage == SleepStage.REM }
    val wakeCount = epochs.count { it.stage == SleepStage.WAKE }
    val total = epochs.size.coerceAtLeast(1)

    val deepPct = ((deepCount.toFloat() / total) * 100f).toInt()
    val lightPct = ((lightCount.toFloat() / total) * 100f).toInt()
    val remPct = ((remCount.toFloat() / total) * 100f).toInt()
    val wakePct = ((wakeCount.toFloat() / total) * 100f).toInt()

    Column(modifier = modifier) {
        // Hypnogram Legend with percentages
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StageLegendItem("Deep", "$deepPct%", DozeDialColors.DeepSleep)
            StageLegendItem("Light", "$lightPct%", DozeDialColors.LightSleep)
            StageLegendItem("REM", "$remPct%", DozeDialColors.RemSleep)
            StageLegendItem("Wake", "$wakePct%", DozeDialColors.WakeArousal)
        }

        // Hypnogram Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A).copy(alpha = 0.7f))
        ) {
            val w = size.width
            val h = size.height
            val vPad = 16f
            val usableH = h - (vPad * 2f)

            // Stage Y positions: Wake (top), REM, Light, Deep (bottom)
            fun stageToY(stage: SleepStage): Float {
                return when (stage) {
                    SleepStage.WAKE -> vPad
                    SleepStage.REM -> vPad + (usableH * 0.33f)
                    SleepStage.LIGHT -> vPad + (usableH * 0.66f)
                    SleepStage.DEEP -> vPad + usableH
                }
            }

            // Draw horizontal guide lines
            val stages = listOf(SleepStage.WAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)
            for (st in stages) {
                val y = stageToY(st)
                drawLine(
                    color = Color(0xFF334155).copy(alpha = 0.4f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            // Draw stepwise hypnogram line
            val path = Path()
            val xStep = w / (epochs.size - 1).coerceAtLeast(1).toFloat()

            for (i in epochs.indices) {
                val currStage = epochs[i].stage
                val currY = stageToY(currStage)
                val currX = i * xStep

                if (i == 0) {
                    path.moveTo(currX, currY)
                } else {
                    val prevY = stageToY(epochs[i - 1].stage)
                    // Stepwise transition: horizontal line then vertical step
                    path.lineTo(currX, prevY)
                    path.lineTo(currX, currY)
                }
            }

            // Draw line
            drawPath(
                path = path,
                color = DozeDialColors.CelestialCyan,
                style = Stroke(width = 2.5f, cap = StrokeCap.Square)
            )

            // Draw colored segment dots / blocks
            for (i in epochs.indices) {
                val stage = epochs[i].stage
                val color = when (stage) {
                    SleepStage.DEEP -> DozeDialColors.DeepSleep
                    SleepStage.LIGHT -> DozeDialColors.LightSleep
                    SleepStage.REM -> DozeDialColors.RemSleep
                    SleepStage.WAKE -> DozeDialColors.WakeArousal
                }
                val y = stageToY(stage)
                val x = i * xStep
                drawCircle(color = color, radius = 2f, center = Offset(x, y))
            }
        }
    }
}

@Composable
private fun StageLegendItem(label: String, percent: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$label $percent",
            style = MaterialTheme.typography.labelSmall,
            color = DozeDialColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
