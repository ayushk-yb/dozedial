package com.dozedial.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dozedial.engine.SleepFeatures
import com.dozedial.engine.SleepStage
import com.dozedial.ui.theme.DozeDialColors

@Composable
fun LiveHeartRateGauge(
    bpm: Int?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(DozeDialColors.SunriseCoral.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = DozeDialColors.SunriseCoral,
                        modifier = Modifier
                            .size(32.dp)
                            .scale(if (bpm != null && bpm > 0) pulseScale else 1f)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "LIVE HEART RATE",
                        style = MaterialTheme.typography.labelSmall,
                        color = DozeDialColors.TextSecondary,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (bpm != null && bpm > 0) "$bpm" else "--",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }
            }

            Text(
                text = "BPM",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DozeDialColors.TextTertiary
            )
        }
    }
}

@Composable
fun LiveSleepStageCard(
    stage: SleepStage,
    modifier: Modifier = Modifier
) {
    val stageColor = Color(stage.colorHex)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(stageColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stage.displayName.uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = stageColor,
                        letterSpacing = 0.5.sp
                    )
                }

                if (stage.isOptimalForWaking) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(DozeDialColors.EmeraldSleep.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "OPTIMAL WAKE STAGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = DozeDialColors.EmeraldSleep,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stage.description,
                style = MaterialTheme.typography.bodySmall,
                color = DozeDialColors.TextSecondary
            )
        }
    }
}

@Composable
fun AutonomicMetricsGrid(
    features: SleepFeatures,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AutonomicMetricItem(
            modifier = Modifier.weight(1f),
            title = "5m Mean",
            value = if (features.sampleCount > 0) "%.1f".format(features.mean5m) else "--",
            unit = "BPM"
        )
        AutonomicMetricItem(
            modifier = Modifier.weight(1f),
            title = "Variance (σ)",
            value = if (features.sampleCount > 0) "%.2f".format(features.stdDev5m) else "--",
            unit = "BPM"
        )
        AutonomicMetricItem(
            modifier = Modifier.weight(1f),
            title = "15s Gradient",
            value = if (features.sampleCount > 0) "%+.1f".format(features.slope15s) else "--",
            unit = "Δ"
        )
    }
}

@Composable
private fun AutonomicMetricItem(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = DozeDialColors.TextTertiary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = DozeDialColors.TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun LiveStabilitySparkline(
    samples: List<Int>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "5-MINUTE ROLLING STABILITY SPARKLINE",
                style = MaterialTheme.typography.labelSmall,
                color = DozeDialColors.TextSecondary,
                letterSpacing = 0.5.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (samples.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Collecting rolling telemetry…",
                        style = MaterialTheme.typography.bodySmall,
                        color = DozeDialColors.TextTertiary
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val min = (samples.minOrNull() ?: 50).coerceAtLeast(30)
                    val max = (samples.maxOrNull() ?: 90).coerceAtMost(200)
                    val span = (max - min).coerceAtLeast(5)

                    val path = Path()
                    val xStep = w / (samples.size - 1).coerceAtLeast(1).toFloat()

                    for (i in samples.indices) {
                        val bpm = samples[i].coerceIn(min, max)
                        val ratio = (bpm - min).toFloat() / span.toFloat()
                        val x = i * xStep
                        val y = h - (ratio * h)

                        if (i == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = DozeDialColors.NeonIndigo,
                        style = Stroke(width = 2f)
                    )
                }
            }
        }
    }
}
