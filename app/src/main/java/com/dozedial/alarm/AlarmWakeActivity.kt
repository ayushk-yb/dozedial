package com.dozedial.alarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dozedial.service.HeartRateCollectionService
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Full-screen wake activity that displays over the lockscreen and turns on the display.
 * Renders an ambient sunrise simulation with sleep metrics and tactile wake-up control.
 */
class AlarmWakeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockscreenFlags()

        val reason = intent.getStringExtra(SmartAlarmReceiver.EXTRA_REASON) ?: "Smart Alarm"
        val stageName = intent.getStringExtra(SmartAlarmReceiver.EXTRA_STAGE) ?: "LIGHT"

        setContent {
            SunriseWakeScreen(
                reason = reason,
                stageName = stageName,
                onDismissAlarm = {
                    dismissAlarmAndFinalize()
                }
            )
        }
    }

    private fun setupLockscreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun dismissAlarmAndFinalize() {
        // 1. Stop audio playback service
        val stopServiceIntent = Intent(this, AlarmPlaybackService::class.java).apply {
            action = AlarmPlaybackService.ACTION_DISMISS_ALARM
        }
        startService(stopServiceIntent)

        // 2. Stop and finalize overnight recording session
        HeartRateCollectionService.stopRecording(this)

        finish()
    }
}

@Composable
fun SunriseWakeScreen(
    reason: String,
    stageName: String,
    onDismissAlarm: () -> Unit
) {
    val recordingState by HeartRateCollectionService.recordingState.collectAsStateWithLifecycle()

    var currentTimeStr by remember {
        mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    var currentSecondsStr by remember {
        mutableStateOf(LocalTime.now().format(DateTimeFormatter.ofPattern("ss")))
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            val now = LocalTime.now()
            currentTimeStr = now.format(DateTimeFormatter.ofPattern("HH:mm"))
            currentSecondsStr = now.format(DateTimeFormatter.ofPattern("ss"))
        }
    }

    // Breathing pulse animation for the sunrise glow
    val transition = rememberInfiniteTransition(label = "sunrisePulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Palette: Midnight Deep Navy -> Sunrise Coral -> Warm Golden Glow
    val sunriseColors = listOf(
        Color(0xFF0A0F1D), // Midnight deep
        Color(0xFF1E1B4B), // Indigo dawn
        Color(0xFF831843), // Twilight berry
        Color(0xFFE11D48), // Sunrise coral
        Color(0xFFF59E0B)  // Golden sun
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(sunriseColors))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Ambient background glowing sun circle
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(320.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFB703).copy(alpha = 0.35f),
                            Color(0xFFFB8500).copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Greeting & Smart Wake Badge
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.35f),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = Color(0xFFFFB703),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SMART SUNRISE WAKE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB703),
                            letterSpacing = 1.sp
                        )
                    }
                }

                Text(
                    text = "Good Morning",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (stageName.equals("LIGHT", ignoreCase = true)) {
                        "Woke you gently in Light Sleep"
                    } else {
                        reason
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // Center Section: Prominent Digital Clock
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = currentTimeStr,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp),
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                    Text(
                        text = ":$currentSecondsStr",
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Overnight Sleep Stats Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFF43F5E), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            val hrText = recordingState.currentBpm?.let { "$it bpm" } ?: "--"
                            Text(hrText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Heart Rate", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Bedtime, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            val hours = recordingState.elapsedSeconds / 3600
                            val minutes = (recordingState.elapsedSeconds % 3600) / 60
                            val durText = if (hours > 0) "%dh %dm".format(hours, minutes) else "%dm".format(minutes)
                            Text(durText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Duration", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Alarm, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(stageName.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Wake Stage", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            // Bottom Section: Slide to Wake / Stop Alarm Control
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onDismissAlarm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(29.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF0F172A)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "I'm Awake • Stop Alarm",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
