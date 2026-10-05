package com.dozedial.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dozedial.alarm.AlarmConfig
import com.dozedial.ui.theme.DozeDialColors

/**
 * Metadata for a selectable alarm sound item.
 */
data class AlarmToneItem(
    val id: String,
    val title: String,
    val uriString: String?, // null indicates default system alarm
    val subtitle: String = "Device Alarm"
)

/**
 * Helper to query available system alarm tones.
 */
fun querySystemAlarmTones(context: Context): List<AlarmToneItem> {
    val items = mutableListOf<AlarmToneItem>()

    // Default system alarm always first
    items.add(
        AlarmToneItem(
            id = "default_alarm",
            title = "Default System Alarm",
            uriString = null,
            subtitle = "Standard Device Wake Chime"
        )
    )

    try {
        val manager = RingtoneManager(context).apply {
            setType(RingtoneManager.TYPE_ALARM)
        }
        val cursor = manager.cursor
        while (cursor.moveToNext()) {
            val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
            val uri = manager.getRingtoneUri(cursor.position)
            if (uri != null) {
                items.add(
                    AlarmToneItem(
                        id = uri.toString(),
                        title = title ?: "Alarm Tone",
                        uriString = uri.toString(),
                        subtitle = "System Tone"
                    )
                )
            }
        }
    } catch (e: Exception) {
        Log.w("AlarmTonePicker", "Could not query system alarm tones", e)
    }

    // If device alarm list is sparse, add system notification chime as secondary ambient preset
    if (items.size <= 2) {
        try {
            val notifUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (notifUri != null) {
                val ringtone = RingtoneManager.getRingtone(context, notifUri)
                val title = ringtone?.getTitle(context) ?: "Gentle Notification Chime"
                items.add(
                    AlarmToneItem(
                        id = notifUri.toString(),
                        title = title,
                        uriString = notifUri.toString(),
                        subtitle = "Ambient Chime"
                    )
                )
            }
        } catch (_: Exception) {}
    }

    return items
}

/**
 * Resolves a human-readable title for any alarm URI.
 */
fun resolveAlarmToneTitle(context: Context, uriString: String?): String {
    if (uriString.isNullOrBlank()) {
        return "Default System Alarm"
    }
    return try {
        val uri = Uri.parse(uriString)
        val ringtone = RingtoneManager.getRingtone(context, uri)
        val title = ringtone?.getTitle(context)
        if (!title.isNullOrBlank()) title else "Custom Alarm Tune"
    } catch (e: Exception) {
        "Custom Alarm Tune"
    }
}

/**
 * Safe audio preview controller for sample playback in the UI.
 */
class TonePreviewController(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var ringtone: Ringtone? = null

    fun play(uriString: String?, onComplete: () -> Unit = {}) {
        stop()
        try {
            val uri = if (uriString != null) {
                Uri.parse(uriString)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setOnCompletionListener {
                    stop()
                    onComplete()
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Fallback to RingtoneManager
            try {
                val uri = if (uriString != null) Uri.parse(uriString) else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                    play()
                }
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            ringtone?.stop()
        } catch (_: Exception) {}
        ringtone = null
    }
}

/**
 * Beautiful Midnight Alarm Sound & Tune Card displayed on the Sleep & Alarm tab.
 */
@Composable
fun AlarmTonePickerCard(
    config: AlarmConfig,
    onUpdateConfig: (AlarmConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSelectionDialog by remember { mutableStateOf(false) }

    val currentTitle = remember(config.ringtoneUri) {
        resolveAlarmToneTitle(context, config.ringtoneUri)
    }

    val previewController = remember { TonePreviewController(context) }
    var isPreviewingThisTone by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            previewController.stop()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DozeDialColors.CardBackground),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(DozeDialColors.NeonIndigo, DozeDialColors.RadiantViolet)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Alarm Sound & Melody",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Acoustic wake tones for smooth arousal",
                            style = MaterialTheme.typography.bodySmall,
                            color = DozeDialColors.TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Currently active tone container
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF131D2E),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26334B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSelectionDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Sound wave / status indicator
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isPreviewingThisTone) DozeDialColors.CelestialCyan.copy(alpha = 0.2f)
                                    else Color(0xFF1E293B)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPreviewingThisTone) {
                                SoundwaveAnimation(color = DozeDialColors.CelestialCyan)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = DozeDialColors.CelestialCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (config.ringtoneUri == null) "System Default Chime" else "Custom Selected Melody",
                                style = MaterialTheme.typography.labelSmall,
                                color = DozeDialColors.CelestialCyan
                            )
                        }
                    }

                    // Audition Play/Stop Icon Button
                    IconButton(
                        onClick = {
                            if (isPreviewingThisTone) {
                                previewController.stop()
                                isPreviewingThisTone = false
                            } else {
                                isPreviewingThisTone = true
                                previewController.play(config.ringtoneUri) {
                                    isPreviewingThisTone = false
                                }
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPreviewingThisTone) DozeDialColors.SunriseCoral
                                else DozeDialColors.NeonIndigo
                            )
                    ) {
                        Icon(
                            imageVector = if (isPreviewingThisTone) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPreviewingThisTone) "Stop" else "Preview",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row: "Change Tone" and "Browse Audio"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        previewController.stop()
                        isPreviewingThisTone = false
                        showSelectionDialog = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change Tone")
                }
            }
        }
    }

    // Modal Tone Selection Dialog
    if (showSelectionDialog) {
        AlarmToneSelectionDialog(
            currentUri = config.ringtoneUri,
            onSelectTone = { newUri ->
                onUpdateConfig(config.copy(ringtoneUri = newUri))
            },
            onDismiss = {
                showSelectionDialog = false
            }
        )
    }
}

/**
 * Animated soundwave visualization for active previewing.
 */
@Composable
fun SoundwaveAnimation(color: Color) {
    val transition = rememberInfiniteTransition(label = "waveAnim")
    val h1 by transition.animateFloat(
        initialValue = 4f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by transition.animateFloat(
        initialValue = 14f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by transition.animateFloat(
        initialValue = 8f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(tween(450, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(modifier = Modifier.width(3.dp).height(h1.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Box(modifier = Modifier.width(3.dp).height(h2.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Box(modifier = Modifier.width(3.dp).height(h3.dp).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

/**
 * Full-featured Alarm Tone Selection Dialog.
 * Shows system tones with inline play previews, plus an option to browse device audio.
 */
@Composable
fun AlarmToneSelectionDialog(
    currentUri: String?,
    onSelectTone: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val previewController = remember { TonePreviewController(context) }
    var playingUri by remember { mutableStateOf<String?>(null) }
    var isPlayingDefault by remember { mutableStateOf(false) }

    var selectedUri by remember { mutableStateOf(currentUri) }
    val systemTones = remember { querySystemAlarmTones(context) }

    // Native Ringtone Picker Activity Result
    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            val uriStr = pickedUri?.toString()
            selectedUri = uriStr
            onSelectTone(uriStr)
            previewController.stop()
            onDismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            previewController.stop()
        }
    }

    Dialog(
        onDismissRequest = {
            previewController.stop()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            color = DozeDialColors.SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.CardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Select Alarm Sound",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Sample acoustic wake melodies",
                            style = MaterialTheme.typography.bodySmall,
                            color = DozeDialColors.TextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(DozeDialColors.NeonIndigo.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = DozeDialColors.NeonIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Browse Device Audio Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.NeonIndigo.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            previewController.stop()
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Sound")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                val initial = selectedUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, initial)
                            }
                            ringtonePickerLauncher.launch(intent)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = DozeDialColors.DawnGold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Browse Device Audio…",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Choose from ringtones, music, or downloaded audio",
                                style = MaterialTheme.typography.labelSmall,
                                color = DozeDialColors.TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF26334B))
                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable List of System Alarm Sounds
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(systemTones) { tone ->
                        val isSelected = (tone.uriString == null && selectedUri == null) ||
                            (tone.uriString != null && tone.uriString == selectedUri)

                        val isPlaying = if (tone.uriString == null) isPlayingDefault else playingUri == tone.uriString

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF1E283E) else Color.Transparent,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, DozeDialColors.NeonIndigo.copy(alpha = 0.6f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedUri = tone.uriString
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedUri = tone.uriString },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = DozeDialColors.NeonIndigo,
                                            unselectedColor = Color(0xFF475569)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = tone.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else DozeDialColors.TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = tone.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) DozeDialColors.CelestialCyan else DozeDialColors.TextTertiary
                                        )
                                    }
                                }

                                // Audition Button
                                IconButton(
                                    onClick = {
                                        if (isPlaying) {
                                            previewController.stop()
                                            playingUri = null
                                            isPlayingDefault = false
                                        } else {
                                            if (tone.uriString == null) {
                                                isPlayingDefault = true
                                                playingUri = null
                                            } else {
                                                playingUri = tone.uriString
                                                isPlayingDefault = false
                                            }
                                            previewController.play(tone.uriString) {
                                                playingUri = null
                                                isPlayingDefault = false
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isPlaying) DozeDialColors.SunriseCoral
                                            else Color(0xFF1E293B)
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Stop" else "Preview",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF26334B))
                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons: Cancel and Set Tune
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            previewController.stop()
                            onDismiss()
                        }
                    ) {
                        Text("Cancel", color = DozeDialColors.TextSecondary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            previewController.stop()
                            onSelectTone(selectedUri)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DozeDialColors.NeonIndigo),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Sound", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
