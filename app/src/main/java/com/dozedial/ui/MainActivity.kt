package com.dozedial.ui

import android.Manifest
import android.app.TimePickerDialog
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dozedial.alarm.AlarmConfig
import com.dozedial.ble.BleConnectionState
import com.dozedial.ble.BleDevice
import com.dozedial.ble.HeartRateParser
import com.dozedial.data.baseline.UserSleepBaselineEntity
import com.dozedial.data.db.HeartRateRecordEntity
import com.dozedial.data.db.SleepSessionEntity
import com.dozedial.engine.SleepStage
import com.dozedial.service.HeartRateCollectionService
import com.dozedial.ui.components.AutonomicMetricsGrid
import com.dozedial.ui.components.HypnogramChart
import com.dozedial.ui.components.LiveHeartRateGauge
import com.dozedial.ui.components.LiveSleepStageCard
import com.dozedial.ui.components.LiveStabilitySparkline
import com.dozedial.ui.components.SmartAlarmClockDial
import com.dozedial.ui.theme.DozeDialColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = DozeDialColors.DarkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBluetoothState()
    }
}

fun getRequiredBlePermissions(): Array<String> {
    val list = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        list.add(Manifest.permission.BLUETOOTH_SCAN)
        list.add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        list.add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        list.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return list.toTypedArray()
}

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    var hasPermissions by remember {
        mutableStateOf(
            getRequiredBlePermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshBluetoothState()
    }

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(getRequiredBlePermissions())
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(DozeDialColors.MidnightBlack)) {
                HeaderSection(connectionState = uiState.connectionState)
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = DozeDialColors.MidnightBlack,
                    contentColor = DozeDialColors.CelestialCyan,
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = DozeDialColors.NeonIndigo
                            )
                        }
                    }
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Sleep & Alarm", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = {
                            if (uiState.recordingState.isRecording) {
                                BadgedBox(badge = { Badge(containerColor = DozeDialColors.SunriseCoral) { Text("REC") } }) {
                                    Icon(Icons.Default.Alarm, contentDescription = null)
                                }
                            } else {
                                Icon(Icons.Default.Alarm, contentDescription = null)
                            }
                        },
                        selectedContentColor = DozeDialColors.NeonIndigo,
                        unselectedContentColor = DozeDialColors.TextTertiary
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Live Staging", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                        selectedContentColor = DozeDialColors.CelestialCyan,
                        unselectedContentColor = DozeDialColors.TextTertiary
                    )
                    Tab(
                        selected = selectedTabIndex == 2,
                        onClick = { selectedTabIndex = 2 },
                        text = { Text("Analytics (${uiState.sessions.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        selectedContentColor = DozeDialColors.EmeraldSleep,
                        unselectedContentColor = DozeDialColors.TextTertiary
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTabIndex) {
            0 -> {
                // Tab 0: Sleep & Alarm
                SleepAndAlarmTab(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    uiState = uiState,
                    onOneTapSleep = { viewModel.oneTapSleep() },
                    onStopRecording = { viewModel.stopOvernightRecording() },
                    onToggleAlarm = { viewModel.toggleAlarmEnabled() },
                    onUpdateConfig = { viewModel.updateAlarmConfig(it) },
                    onTestPreview = { viewModel.testAlarmPreview() }
                )
            }
            1 -> {
                // Tab 1: Live Telemetry & Staging
                LiveTelemetryTab(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    uiState = uiState,
                    hasPermissions = hasPermissions,
                    onRequestPermissions = { permissionLauncher.launch(getRequiredBlePermissions()) },
                    onEnableBt = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    onStartScan = { viewModel.startScan() },
                    onStopScan = { viewModel.stopScan() },
                    onConnect = { viewModel.connect(it) },
                    onDisconnect = { viewModel.disconnect() },
                    onToggleDebug = { viewModel.toggleDebug() }
                )
            }
            2 -> {
                // Tab 2: Analytics & Hypnogram History
                AnalyticsHistoryTab(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    sessions = uiState.sessions,
                    baseline = uiState.baseline,
                    onSessionClick = { session -> viewModel.selectSession(session) },
                    onDeleteClick = { session -> viewModel.deleteSession(session.sessionId) }
                )
            }
        }
    }

    // Session Detail & Hypnogram Dialog
    uiState.selectedSession?.let { session ->
        SessionDetailDialog(
            session = session,
            records = uiState.selectedSessionRecords,
            baseline = uiState.baseline,
            isLoadingRecords = uiState.isLoadingSessionRecords,
            onDismiss = { viewModel.clearSelectedSession() },
            onExportCsv = {
                scope.launch {
                    val shareIntent = viewModel.exportSession(session)
                    if (shareIntent != null) {
                        shareLauncher.launch(Intent.createChooser(shareIntent, "Export Sleep Heart Rate CSV"))
                    } else {
                        Toast.makeText(context, "No records to export", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDelete = {
                viewModel.deleteSession(session.sessionId)
            }
        )
    }
}

@Composable
fun HeaderSection(connectionState: BleConnectionState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "DozeDial",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = DozeDialColors.NeonIndigo,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Autonomous Sleep Staging & Smart-Alarm",
                style = MaterialTheme.typography.bodySmall,
                color = DozeDialColors.TextSecondary
            )
        }

        // Connection Status Badge with breathing dot
        DeviceConnectionStatusBadge(connectionState)
    }
}

@Composable
fun DeviceConnectionStatusBadge(connectionState: BleConnectionState) {
    val isConnected = connectionState is BleConnectionState.Connected
    val isConnecting = connectionState is BleConnectionState.Connecting ||
        connectionState is BleConnectionState.DiscoveringServices ||
        connectionState is BleConnectionState.SubscribingToNotifications

    val dotColor = when {
        isConnected -> DozeDialColors.EmeraldSleep
        isConnecting -> DozeDialColors.DawnGold
        else -> Color(0xFF6B7280)
    }

    val statusText = when (connectionState) {
        is BleConnectionState.Connected -> connectionState.deviceName ?: "Fitbit Air"
        is BleConnectionState.Connecting -> "Connecting…"
        is BleConnectionState.DiscoveringServices -> "Discovering…"
        is BleConnectionState.SubscribingToNotifications -> "Subscribing…"
        else -> "Fitbit Air Off"
    }

    // Pulse animation
    val transition = rememberInfiniteTransition(label = "badgePulse")
    val scale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DozeDialColors.CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(if (isConnected || isConnecting) scale else 1f)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = DozeDialColors.TextPrimary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SleepAndAlarmTab(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    onOneTapSleep: () -> Unit,
    onStopRecording: () -> Unit,
    onToggleAlarm: () -> Unit,
    onUpdateConfig: (AlarmConfig) -> Unit,
    onTestPreview: () -> Unit
) {
    val context = LocalContext.current
    val config = uiState.alarmConfig
    val recordingState = uiState.recordingState

    LazyColumn(
        modifier = modifier
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Overnight Recording Hero Card
        item {
            OvernightRecordingHeroCard(
                recordingState = recordingState,
                onOneTapSleep = onOneTapSleep,
                onStopRecording = onStopRecording
            )
        }

        // 2. Circular Smart Alarm Clock Dial
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = DozeDialColors.CelestialCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Smart Wake Window",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Switch(
                            checked = config.isEnabled,
                            onCheckedChange = { onToggleAlarm() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DozeDialColors.NeonIndigo,
                                uncheckedTrackColor = Color(0xFF334155)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Circular Arc Dial
                    SmartAlarmClockDial(
                        config = config,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (config.isEnabled) {
                            "Window: ${config.formattedWindowRange()}"
                        } else {
                            "Smart Alarm is currently off"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (config.isEnabled) DozeDialColors.CelestialCyan else DozeDialColors.TextTertiary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Time Adjust Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = {
                                val tMin = (config.targetMinute - 15 + 60) % 60
                                val tHour = if (config.targetMinute < 15) (config.targetHour - 1 + 24) % 24 else config.targetHour
                                onUpdateConfig(config.copy(targetHour = tHour, targetMinute = tMin))
                            }
                        ) {
                            Text("-15m")
                        }

                        Button(
                            onClick = {
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        onUpdateConfig(config.copy(targetHour = hour, targetMinute = minute))
                                    },
                                    config.targetHour,
                                    config.targetMinute,
                                    false
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DozeDialColors.NeonIndigo)
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Set Time")
                        }

                        OutlinedButton(
                            onClick = {
                                val tMin = (config.targetMinute + 15) % 60
                                val tHour = if (config.targetMinute + 15 >= 60) (config.targetHour + 1) % 24 else config.targetHour
                                onUpdateConfig(config.copy(targetHour = tHour, targetMinute = tMin))
                            }
                        ) {
                            Text("+15m")
                        }
                    }
                }
            }
        }

        // 3. Smart Window & Wake Options Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Smart Window Duration",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Wakes you gently if you shift into Light Sleep within this window",
                        style = MaterialTheme.typography.bodySmall,
                        color = DozeDialColors.TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Window duration options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 15, 20, 30).forEach { mins ->
                            val isSelected = config.smartWindowMinutes == mins
                            Button(
                                onClick = { onUpdateConfig(config.copy(smartWindowMinutes = mins)) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) DozeDialColors.NeonIndigo else Color(0xFF1E293B),
                                    contentColor = if (isSelected) Color.White else DozeDialColors.TextSecondary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text("${mins}m", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Volume Crescendo Ramp",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Smoothly ramps alarm volume from 10% to 100%",
                        style = MaterialTheme.typography.bodySmall,
                        color = DozeDialColors.TextSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15, 30, 45, 60).forEach { secs ->
                            val isSelected = config.crescendoSeconds == secs
                            Button(
                                onClick = { onUpdateConfig(config.copy(crescendoSeconds = secs)) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) DozeDialColors.CelestialCyan else Color(0xFF1E293B),
                                    contentColor = if (isSelected) Color(0xFF0F172A) else DozeDialColors.TextSecondary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                Text("${secs}s", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Vibration toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Vibration, contentDescription = null, tint = DozeDialColors.TextSecondary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Gentle Pulse Vibration", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }

                        Switch(
                            checked = config.vibrate,
                            onCheckedChange = { onUpdateConfig(config.copy(vibrate = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DozeDialColors.CelestialCyan
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dry-run preview alarm button
                    OutlinedButton(
                        onClick = onTestPreview,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Sunrise & Audio Crescendo")
                    }
                }
            }
        }
    }
}

@Composable
fun OvernightRecordingHeroCard(
    recordingState: HeartRateCollectionService.RecordingState,
    onOneTapSleep: () -> Unit,
    onStopRecording: () -> Unit
) {
    if (recordingState.isRecording) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.SunriseCoral.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
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
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(DozeDialColors.SunriseCoral)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OVERNIGHT SLEEP ACTIVE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = DozeDialColors.SunriseCoral,
                            letterSpacing = 1.sp
                        )
                    }

                    val hours = recordingState.elapsedSeconds / 3600
                    val minutes = (recordingState.elapsedSeconds % 3600) / 60
                    val seconds = recordingState.elapsedSeconds % 60
                    Text(
                        text = "%02d:%02d:%02d".format(hours, minutes, seconds),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Live Stage", style = MaterialTheme.typography.labelSmall, color = DozeDialColors.TextSecondary)
                        Text(
                            recordingState.currentStage.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(recordingState.currentStage.colorHex)
                        )
                    }
                    Column {
                        Text("Live HR", style = MaterialTheme.typography.labelSmall, color = DozeDialColors.TextSecondary)
                        Text(
                            recordingState.currentBpm?.let { "$it BPM" } ?: "--",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Column {
                        Text("Packets", style = MaterialTheme.typography.labelSmall, color = DozeDialColors.TextSecondary)
                        Text(
                            "${recordingState.sampleCount}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onStopRecording,
                    colors = ButtonDefaults.buttonColors(containerColor = DozeDialColors.SunriseCoral),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Stop & Finalize Session", fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        Card(
            colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = DozeDialColors.NeonIndigo,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "One-Tap Sleep",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Arm your smart alarm and begin uninterrupted overnight physiological recording with a single gesture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DozeDialColors.TextSecondary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onOneTapSleep,
                    colors = ButtonDefaults.buttonColors(containerColor = DozeDialColors.NeonIndigo),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Overnight Sleep", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun LiveTelemetryTab(
    modifier: Modifier = Modifier,
    uiState: MainUiState,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onEnableBt: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    onDisconnect: () -> Unit,
    onToggleDebug: () -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!hasPermissions) {
            item {
                PermissionRequiredCard(onRequestPermissions)
            }
        }

        item {
            BluetoothStatusBar(
                isEnabled = uiState.isBluetoothEnabled,
                onEnableClick = onEnableBt
            )
        }

        // Live Telemetry Cards
        item {
            LiveHeartRateGauge(bpm = uiState.latestSample?.bpm)
        }

        item {
            LiveSleepStageCard(stage = uiState.recordingState.currentStage)
        }

        item {
            AutonomicMetricsGrid(features = uiState.recordingState.currentFeatures)
        }

        item {
            LiveStabilitySparkline(samples = uiState.liveRollingSamples)
        }

        // Active Connection / Scan Section
        if (uiState.connectionState is BleConnectionState.Connected) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Connected: ${uiState.connectionState.deviceName ?: "Fitbit Air"}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = DozeDialColors.EmeraldSleep
                            )
                            Text(
                                text = "MAC: ${uiState.connectionState.deviceAddress}",
                                style = MaterialTheme.typography.bodySmall,
                                color = DozeDialColors.TextSecondary
                            )
                        }

                        OutlinedButton(onClick = onDisconnect) {
                            Text("Disconnect")
                        }
                    }
                }
            }

            // Developer Debug
            item {
                DeveloperDebugSection(
                    isExpanded = uiState.isDebugExpanded,
                    sample = uiState.latestSample,
                    onToggle = onToggleDebug
                )
            }
        } else {
            item {
                FitbitAirPreparationCard()
            }

            item {
                ScanControlSection(
                    isScanning = uiState.isScanning,
                    hasPermissions = hasPermissions,
                    isBtEnabled = uiState.isBluetoothEnabled,
                    onStartScan = onStartScan,
                    onStopScan = onStopScan
                )
            }

            item {
                Text(
                    text = "Discovered Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (uiState.discoveredDevices.isEmpty()) {
                item {
                    EmptyDevicesCard(isScanning = uiState.isScanning)
                }
            } else {
                items(uiState.discoveredDevices, key = { it.address }) { device ->
                    DiscoveredDeviceItem(
                        device = device,
                        isConnecting = uiState.connectionState is BleConnectionState.Connecting,
                        onConnectClick = { onConnect(device) }
                    )
                }
            }
        }
    }
}

@Composable
fun AnalyticsHistoryTab(
    modifier: Modifier = Modifier,
    sessions: List<SleepSessionEntity>,
    baseline: UserSleepBaselineEntity?,
    onSessionClick: (SleepSessionEntity) -> Unit,
    onDeleteClick: (SleepSessionEntity) -> Unit
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Adaptive Baseline Profile Card
        item {
            AdaptiveBaselineCard(baseline = baseline)
        }

        // 2. History Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Sleep Session History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${sessions.size} recorded",
                    style = MaterialTheme.typography.bodySmall,
                    color = DozeDialColors.TextSecondary
                )
            }
        }

        if (sessions.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = DozeDialColors.NeonIndigo
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No sleep recordings yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Start overnight recording from the 'Sleep & Alarm' tab to begin building your adaptive profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = DozeDialColors.TextSecondary
                        )
                    }
                }
            }
        } else {
            items(sessions, key = { it.sessionId }) { session ->
                SessionHistoryItem(
                    session = session,
                    onClick = { onSessionClick(session) },
                    onDelete = { onDeleteClick(session) }
                )
            }
        }
    }
}

@Composable
fun AdaptiveBaselineCard(baseline: UserSleepBaselineEntity?) {
    val base = baseline ?: UserSleepBaselineEntity.default()

    Card(
        colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = DozeDialColors.NeonIndigo,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Adaptive Autonomic Baseline",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DozeDialColors.NeonIndigo.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${base.nightsAnalyzed} NIGHTS",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = DozeDialColors.NeonIndigo
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Learned physiological thresholds personalized across multi-night sleep recordings.",
                style = MaterialTheme.typography.bodySmall,
                color = DozeDialColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Rolling RHR",
                    value = "%.1f".format(base.rollingRhr)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Deep Std (σ)",
                    value = "%.2f".format(base.deepSleepStdThreshold)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Wake Spike",
                    value = "+%.1f".format(base.wakeSpikeThreshold)
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Diurnal Dip",
                    value = "%.0f%%".format(base.rollingDiurnalDip)
                )
            }
        }
    }
}

@Composable
fun SessionHistoryItem(
    session: SleepSessionEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM dd, yyyy • HH:mm").withZone(ZoneId.systemDefault())
    }
    val startDateStr = remember(session.startTime) {
        dateFormatter.format(Instant.ofEpochMilli(session.startTime))
    }

    val durationStr = remember(session.startTime, session.endTime) {
        val end = session.endTime ?: session.startTime
        val elapsedSec = (end - session.startTime) / 1000L
        val hours = elapsedSec / 3600
        val mins = (elapsedSec % 3600) / 60
        val secs = elapsedSec % 60
        if (hours > 0) "%dh %02dm %02ds".format(hours, mins, secs) else "%02dm %02ds".format(mins, secs)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Session #${session.sessionId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = startDateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = DozeDialColors.TextSecondary
                    )
                }

                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Session",
                        tint = DozeDialColors.SunriseCoral.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Duration", style = MaterialTheme.typography.labelSmall, color = DozeDialColors.TextTertiary)
                    Text(durationStr, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Column {
                    Text("Samples", style = MaterialTheme.typography.labelSmall, color = DozeDialColors.TextTertiary)
                    Text("${session.totalSamples}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
                Column {
                    Text("Avg BPM", style = MaterialTheme.typography.labelSmall, color = DozeDialColors.TextTertiary)
                    Text(
                        text = session.avgBpm?.let { "${it.roundToInt()} bpm" } ?: "--",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = DozeDialColors.CelestialCyan
                    )
                }
                Column {
                    Text("Range", style = MaterialTheme.typography.labelSmall, color = DozeDialColors.TextTertiary)
                    Text(
                        text = if (session.minBpm != null && session.maxBpm != null) "${session.minBpm}–${session.maxBpm}" else "--",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Session #${session.sessionId}?") },
            text = { Text("This will permanently remove this session and all its recorded heart rate packets.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = DozeDialColors.SunriseCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SessionDetailDialog(
    session: SleepSessionEntity,
    records: List<HeartRateRecordEntity>,
    baseline: UserSleepBaselineEntity?,
    isLoadingRecords: Boolean,
    onDismiss: () -> Unit,
    onExportCsv: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM dd, yyyy • HH:mm:ss").withZone(ZoneId.systemDefault())
    }
    val startStr = remember(session.startTime) {
        dateFormatter.format(Instant.ofEpochMilli(session.startTime))
    }
    val endStr = remember(session.endTime) {
        session.endTime?.let { dateFormatter.format(Instant.ofEpochMilli(it)) } ?: "In Progress"
    }

    val elapsedSec = remember(session.startTime, session.endTime) {
        val end = session.endTime ?: session.startTime
        (end - session.startTime) / 1000L
    }
    val durationStr = remember(elapsedSec) {
        val hours = elapsedSec / 3600
        val mins = (elapsedSec % 3600) / 60
        val secs = elapsedSec % 60
        if (hours > 0) "%dh %02dm %02ds".format(hours, mins, secs) else "%02dm %02ds".format(mins, secs)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = DozeDialColors.SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Session #${session.sessionId}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Device: ${session.deviceName ?: session.deviceAddress}",
                            style = MaterialTheme.typography.bodySmall,
                            color = DozeDialColors.TextSecondary
                        )
                    }
                    OutlinedButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Start: $startStr\nEnd:   $endStr\nDuration: $durationStr",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = DozeDialColors.TextSecondary
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = DozeDialColors.CardBorder
                )

                // Stats Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Avg BPM",
                        value = session.avgBpm?.let { "${it.roundToInt()}" } ?: "--"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Min BPM",
                        value = session.minBpm?.toString() ?: "--"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Max BPM",
                        value = session.maxBpm?.toString() ?: "--"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Packets",
                        value = session.totalSamples.toString()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 1. Hypnogram Sleep Stages Section
                Text(
                    text = "Hypnogram Sleep Stages",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoadingRecords) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = DozeDialColors.NeonIndigo)
                    }
                } else {
                    HypnogramChart(
                        records = records,
                        baseline = baseline ?: UserSleepBaselineEntity.default(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Heart Rate Over Time Section
                Text(
                    text = "Heart Rate Over Time (BPM)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoadingRecords) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = DozeDialColors.SunriseCoral)
                    }
                } else {
                    HeartRateChart(
                        records = records,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Button(
                    onClick = onExportCsv,
                    colors = ButtonDefaults.buttonColors(containerColor = DozeDialColors.NeonIndigo),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export CSV for Analysis", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        onDelete()
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = DozeDialColors.SunriseCoral
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Session")
                }
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
        border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = DozeDialColors.TextTertiary, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun PermissionRequiredCard(onRequestClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
        border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.SunriseCoral.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = DozeDialColors.SunriseCoral
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "DozeDial requires Bluetooth scanning/connection and notification permissions to stream and record heart rate data continuously overnight.",
                style = MaterialTheme.typography.bodySmall,
                color = DozeDialColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onRequestClick,
                colors = ButtonDefaults.buttonColors(containerColor = DozeDialColors.SunriseCoral)
            ) {
                Text("Grant Permissions")
            }
        }
    }
}

@Composable
fun BluetoothStatusBar(
    isEnabled: Boolean,
    onEnableClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) DozeDialColors.CardBackground else Color(0xFF450A0A)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isEnabled) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    tint = if (isEnabled) DozeDialColors.CelestialCyan else DozeDialColors.SunriseCoral
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEnabled) "Bluetooth: ON" else "Bluetooth: OFF",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            if (!isEnabled) {
                Button(
                    onClick = onEnableClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DozeDialColors.SunriseCoral)
                ) {
                    Text("Turn On")
                }
            }
        }
    }
}

@Composable
fun FitbitAirPreparationCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Before connecting:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "1. Put on your Fitbit Air.\n" +
                    "2. Open Google Health.\n" +
                    "3. Enable Share heart rate for Fitbit Air.\n" +
                    "4. Return here and tap Scan.",
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
                color = DozeDialColors.TextSecondary
            )
        }
    }
}

@Composable
fun ScanControlSection(
    isScanning: Boolean,
    hasPermissions: Boolean,
    isBtEnabled: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    Button(
        onClick = {
            if (isScanning) onStopScan() else onStartScan()
        },
        enabled = hasPermissions && isBtEnabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DozeDialColors.NeonIndigo)
    ) {
        if (isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop Scan")
        } else {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan for Fitbit Air")
        }
    }
}

@Composable
fun DiscoveredDeviceItem(
    device: BleDevice,
    isConnecting: Boolean,
    onConnectClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (device.isLikelyFitbitAir) {
                DozeDialColors.NeonIndigo.copy(alpha = 0.2f)
            } else {
                DozeDialColors.CardBackground
            }
        ),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (device.isLikelyFitbitAir) DozeDialColors.NeonIndigo.copy(alpha = 0.6f) else DozeDialColors.CardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (device.isLikelyFitbitAir) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DozeDialColors.NeonIndigo)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Fitbit Air",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "MAC: ${device.address}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DozeDialColors.TextSecondary
                )
                Text(
                    text = "RSSI: ${device.rssi} dBm",
                    style = MaterialTheme.typography.bodySmall,
                    color = DozeDialColors.TextTertiary
                )
            }

            Button(
                onClick = onConnectClick,
                enabled = !isConnecting,
                colors = ButtonDefaults.buttonColors(containerColor = DozeDialColors.NeonIndigo)
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
fun EmptyDevicesCard(isScanning: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = DozeDialColors.NeonIndigo)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Scanning for nearby Bluetooth devices…", color = Color.White)
            } else {
                Text(
                    text = "No devices discovered yet.\nTap 'Scan for Fitbit Air' to begin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = DozeDialColors.TextSecondary
                )
            }
        }
    }
}

@Composable
fun DeveloperDebugSection(
    isExpanded: Boolean,
    sample: com.dozedial.data.HeartRateSample?,
    onToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp), tint = DozeDialColors.CelestialCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Developer / Debug Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = DozeDialColors.TextSecondary
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "Raw packet:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = DozeDialColors.TextSecondary
                    )
                    Text(
                        text = sample?.rawBytesHex ?: "No packet received yet",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = DozeDialColors.CelestialCyan
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val isUint16 = HeartRateParser.isUint16Format(sample?.rawBytes)
                    Text(
                        text = "HR format: ${if (isUint16) "UINT16" else "UINT8"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )

                    Text(
                        text = "RR intervals: ${sample?.rrIntervalsMs?.size ?: 0}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )

                    if (sample?.energyExpended != null) {
                        Text(
                            text = "Energy expended: ${sample.energyExpended} kJ",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
