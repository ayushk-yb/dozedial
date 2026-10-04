package com.dozedial.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service managing audio crescendo and progressive haptics during alarm firing.
 */
class AlarmPlaybackService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var crescendoJob: Job? = null
    private var isPlaying = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> {
                val reason = intent.getStringExtra(SmartAlarmReceiver.EXTRA_REASON) ?: "Wake Up"
                val stage = intent.getStringExtra(SmartAlarmReceiver.EXTRA_STAGE) ?: "LIGHT"
                startAlarm(reason, stage)
            }
            ACTION_DISMISS_ALARM -> {
                stopAlarm()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAlarm(reason: String, stage: String) {
        if (isPlaying) return
        isPlaying = true

        val prefs = AlarmPreferences(this).loadConfig()

        // 1. Post Foreground Notification with full-screen intent
        val notification = buildAlarmNotification(reason, stage)
        startForeground(NOTIFICATION_ID, notification)

        // 2. Setup Audio Player with Crescendo
        try {
            val alertUri: Uri = prefs.ringtoneUri?.let { Uri.parse(it) }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                isLooping = true
                setVolume(0.10f, 0.10f)
                prepare()
                start()
            }

            // Ramp up volume over crescendoSeconds
            val rampSeconds = prefs.crescendoSeconds.coerceIn(10, 120)
            crescendoJob = serviceScope.launch {
                val steps = rampSeconds * 2 // 2 volume increments per second
                val delayMs = 500L
                for (step in 1..steps) {
                    if (!isActive || mediaPlayer == null) break
                    delay(delayMs)
                    val progress = step.toFloat() / steps.toFloat()
                    val currentVolume = 0.10f + (0.90f * progress)
                    try {
                        mediaPlayer?.setVolume(currentVolume, currentVolume)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MediaPlayer for alarm", e)
        }

        // 3. Setup Progressive Haptics
        if (prefs.vibrate) {
            startVibration()
        }
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Pulse 500ms on, 500ms off
                val timings = longArrayOf(0, 500, 500)
                val amplitudes = intArrayOf(0, 180, 0)
                vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing vibration", e)
        }
    }

    private fun stopAlarm() {
        isPlaying = false
        crescendoJob?.cancel()

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
        vibrator = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Alarm Wake-Up",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Active full-screen sunrise wake-up alarm"
                setBypassDnd(true)
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildAlarmNotification(reason: String, stage: String): Notification {
        val wakeIntent = Intent(this, AlarmWakeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(SmartAlarmReceiver.EXTRA_REASON, reason)
            putExtra(SmartAlarmReceiver.EXTRA_STAGE, stage)
        }
        val wakePendingIntent = PendingIntent.getActivity(
            this,
            2001,
            wakeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, AlarmPlaybackService::class.java).apply {
            action = ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            2002,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DozeDial Smart Alarm")
            .setContentText("$reason ($stage sleep)")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(wakePendingIntent, true)
            .setContentIntent(wakePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss Alarm", dismissPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AlarmPlaybackService"
        const val CHANNEL_ID = "dozedial_smart_alarm_channel"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START_ALARM = "com.dozedial.alarm.ACTION_START_ALARM"
        const val ACTION_DISMISS_ALARM = "com.dozedial.alarm.ACTION_DISMISS_ALARM"
    }
}
