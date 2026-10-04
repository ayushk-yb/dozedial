package com.dozedial.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dozedial.alarm.AlarmConfig
import com.dozedial.ui.theme.DozeDialColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Beautiful glowing circular clock dial showing the scheduled wake-up time
 * and highlighting the active Smart Wake-Up Window arc.
 */
@Composable
fun SmartAlarmClockDial(
    config: AlarmConfig,
    modifier: Modifier = Modifier
) {
    val targetHour12 = config.targetHour % 12
    val targetFraction = (targetHour12 + config.targetMinute / 60f) / 12f
    // 0 on clock is top (-90 degrees)
    val targetAngle = (targetFraction * 360f) - 90f

    // Window angle on a 12-hour clock face
    val windowHours = config.smartWindowMinutes / 60f
    val windowSweepAngle = (windowHours / 12f) * 360f
    val startAngle = targetAngle - windowSweepAngle

    val arcColor = if (config.isEnabled) DozeDialColors.CelestialCyan else Color.Gray.copy(alpha = 0.5f)
    val targetMarkerColor = if (config.isEnabled) DozeDialColors.NeonIndigo else Color.Gray
    val trackColor = Color(0xFF1E293B).copy(alpha = 0.8f)

    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(190.dp)) {
            val strokeWidth = 12f
            val arcSize = size.width - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val canvasSize = Size(arcSize, arcSize)

            // Background circular track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = canvasSize,
                style = Stroke(width = strokeWidth)
            )

            // 12-hour ticks
            for (i in 0 until 12) {
                val tickAngleDeg = (i * 30f) - 90f
                val rad = Math.toRadians(tickAngleDeg.toDouble())
                val radiusOuter = (size.width / 2f) - 15f
                val radiusInner = radiusOuter - (if (i % 3 == 0) 7f else 3.5f)
                val cx = size.width / 2f
                val cy = size.height / 2f

                val start = Offset(
                    (cx + radiusInner * cos(rad)).toFloat(),
                    (cy + radiusInner * sin(rad)).toFloat()
                )
                val end = Offset(
                    (cx + radiusOuter * cos(rad)).toFloat(),
                    (cy + radiusOuter * sin(rad)).toFloat()
                )

                drawLine(
                    color = Color(0xFF475569).copy(alpha = 0.5f),
                    start = start,
                    end = end,
                    strokeWidth = if (i % 3 == 0) 2f else 1f
                )
            }

            // Smart Wake Window Arc
            if (config.isEnabled && windowSweepAngle > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            DozeDialColors.NeonIndigo,
                            DozeDialColors.CelestialCyan
                        )
                    ),
                    startAngle = startAngle,
                    sweepAngle = windowSweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = canvasSize,
                    style = Stroke(width = strokeWidth + 3f, cap = StrokeCap.Round)
                )
            }

            // Target Alarm Cutoff Marker Pin
            val targetRad = Math.toRadians(targetAngle.toDouble())
            val markerRadius = (size.width / 2f) - (strokeWidth / 2f)
            val markerX = (size.width / 2f + markerRadius * cos(targetRad)).toFloat()
            val markerY = (size.height / 2f + markerRadius * sin(targetRad)).toFloat()

            // Outer glow circle
            drawCircle(
                color = targetMarkerColor.copy(alpha = 0.4f),
                radius = 10f,
                center = Offset(markerX, markerY)
            )
            // Solid center pin
            drawCircle(
                color = Color.White,
                radius = 5f,
                center = Offset(markerX, markerY)
            )
        }

        // Center Content
        Box(
            modifier = Modifier
                .size(145.dp)
                .clip(CircleShape)
                .background(DozeDialColors.CardBackground.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (config.isEnabled) "TARGET CUTOFF" else "ALARM OFF",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (config.isEnabled) DozeDialColors.CelestialCyan else Color.Gray,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = config.formattedTargetTime(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (config.isEnabled) Color.White else Color.Gray
                )

                Spacer(modifier = Modifier.height(2.dp))

                if (config.isEnabled) {
                    Text(
                        text = "Window: ${config.smartWindowMinutes}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = DozeDialColors.EmeraldSleep,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
