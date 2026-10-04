package com.dozedial.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dozedial.DozeDialApplication
import com.dozedial.data.db.HeartRateRecordEntity
import com.dozedial.data.db.SleepSessionEntity
import com.dozedial.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.dozedial.alarm.AlarmPreferences
import com.dozedial.alarm.SmartAlarmManager
import com.dozedial.data.baseline.AdaptiveBaselineEngine
import com.dozedial.data.baseline.UserSleepBaselineEntity
import com.dozedial.engine.SleepFeatureExtractor
import com.dozedial.engine.SleepFeatures
import com.dozedial.engine.SleepStage
import com.dozedial.engine.SleepStageClassifier

/**
 * Foreground service managing continuous, overnight BLE heart rate data collection,
 * real-time autonomic sleep stage estimation, and smart alarm trigger evaluation.
 */
class HeartRateCollectionService : Service() {

    data class RecordingState(
        val isRecording: Boolean = false,
        val sessionId: Long? = null,
        val deviceAddress: String? = null,
        val deviceName: String? = null,
        val currentBpm: Int? = null,
        val sampleCount: Int = 0,
        val startTimeMs: Long = 0L,
        val elapsedSeconds: Long = 0L,
        val currentStage: SleepStage = SleepStage.WAKE,
        val currentFeatures: SleepFeatures = SleepFeatures()
    )

    companion object {
        private const val TAG = "HeartRateService"
        const val CHANNEL_ID = "dozedial_sleep_recording"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.dozedial.service.ACTION_START"
        const val ACTION_STOP = "com.dozedial.service.ACTION_STOP"

        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"
        const val EXTRA_DEVICE_NAME = "extra_device_name"

        private const val WAKE_LOCK_TIMEOUT_MS = 10L * 60L * 60L * 1000L // 10 hours safety cap

        private val _recordingState = MutableStateFlow(RecordingState())
        val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

        fun startRecording(context: Context, deviceAddress: String, deviceName: String?) {
            val intent = Intent(context, HeartRateCollectionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress)
                putExtra(EXTRA_DEVICE_NAME, deviceName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, HeartRateCollectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentSessionId: Long? = null
    private var sessionStartTimeMs: Long = 0L

    private val featureExtractor = SleepFeatureExtractor(windowSize = 300)
    private var cachedBaseline: UserSleepBaselineEntity = UserSleepBaselineEntity.default()
    private var alarmTriggeredThisSession = false

    private val buffer = mutableListOf<HeartRateRecordEntity>()
    private val bufferMutex = Mutex()
    private var totalSampleCounter = 0
    private var latestBpm: Int? = null

    private var sampleCollectorJob: Job? = null
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: "Unknown"
                val name = intent.getStringExtra(EXTRA_DEVICE_NAME)
                startSession(address, name)
            }
            ACTION_STOP -> {
                stopSession()
            }
        }
        return START_STICKY
    }

    @SuppressLint("WakelockTimeout")
    private fun startSession(deviceAddress: String, deviceName: String?) {
        if (_recordingState.value.isRecording) {
            Log.w(TAG, "Recording session already in progress")
            return
        }

        acquireWakeLock()

        sessionStartTimeMs = System.currentTimeMillis()
        totalSampleCounter = 0
        latestBpm = null
        featureExtractor.reset()
        alarmTriggeredThisSession = false

        val initialNotification = buildNotification(
            bpm = null,
            elapsedSeconds = 0L,
            sampleCount = 0,
            stage = SleepStage.WAKE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        serviceScope.launch {
            val database = DozeDialApplication.instance.database
            val baseline = try {
                database.userSleepBaselineDao().getBaselineSync()
            } catch (e: Exception) {
                null
            }
            cachedBaseline = baseline ?: UserSleepBaselineEntity.default()

            val session = SleepSessionEntity(
                startTime = sessionStartTimeMs,
                deviceAddress = deviceAddress,
                deviceName = deviceName
            )
            val newSessionId = database.sleepSessionDao().insert(session)
            currentSessionId = newSessionId

            _recordingState.value = RecordingState(
                isRecording = true,
                sessionId = newSessionId,
                deviceAddress = deviceAddress,
                deviceName = deviceName,
                startTimeMs = sessionStartTimeMs,
                sampleCount = 0,
                elapsedSeconds = 0L,
                currentStage = SleepStage.WAKE,
                currentFeatures = SleepFeatures()
            )

            startSampleCollection(newSessionId)
            startTicker()
        }
    }

    private fun startSampleCollection(sessionId: Long) {
        val bleManager = DozeDialApplication.instance.bleManager
        sampleCollectorJob = serviceScope.launch {
            bleManager.samples.collect { sample ->
                val record = HeartRateRecordEntity(
                    sessionId = sessionId,
                    timestampMs = sample.timestamp.toEpochMilli(),
                    bpm = sample.bpm,
                    sensorContact = sample.sensorContactDetected,
                    energyExpended = sample.energyExpended,
                    rawBytesHex = sample.rawBytesHex
                )

                // 1. Update rolling feature buffer & classify current sleep stage
                featureExtractor.addSample(sample.bpm)
                val features = featureExtractor.computeFeatures(cachedBaseline.rollingRhr)
                val stage = SleepStageClassifier.classify(features, cachedBaseline)

                // 2. Evaluate Smart-Alarm trigger if active
                if (!alarmTriggeredThisSession) {
                    val alarmConfig = AlarmPreferences(this@HeartRateCollectionService).loadConfig()
                    if (alarmConfig.isEnabled) {
                        val now = System.currentTimeMillis()
                        val targetMs = alarmConfig.getNextTargetEpochMs(now)
                        val decision = SmartAlarmManager.evaluateTrigger(
                            nowMs = now,
                            targetMs = targetMs,
                            windowMinutes = alarmConfig.smartWindowMinutes,
                            currentStage = stage
                        )
                        if (decision == SmartAlarmManager.TriggerDecision.SMART_LIGHT_SLEEP) {
                            alarmTriggeredThisSession = true
                            Log.i(TAG, "Smart alarm triggered in optimal waking stage: $stage")
                            SmartAlarmManager.triggerAlarmNow(
                                context = this@HeartRateCollectionService,
                                reason = "Optimal Light Sleep Window Detected",
                                stage = stage
                            )
                        } else if (decision == SmartAlarmManager.TriggerDecision.HARD_CUTOFF) {
                            alarmTriggeredThisSession = true
                            Log.i(TAG, "Smart alarm triggered at target hard cutoff")
                            SmartAlarmManager.triggerAlarmNow(
                                context = this@HeartRateCollectionService,
                                reason = "Target Alarm Cutoff Reached",
                                stage = stage
                            )
                        }
                    }
                }

                var itemsToFlush: List<HeartRateRecordEntity>? = null
                bufferMutex.withLock {
                    buffer.add(record)
                    totalSampleCounter++
                    latestBpm = sample.bpm
                    if (buffer.size >= 50) {
                        itemsToFlush = buffer.toList()
                        buffer.clear()
                    }
                }

                _recordingState.update { current ->
                    current.copy(
                        currentBpm = sample.bpm,
                        sampleCount = totalSampleCounter,
                        currentStage = stage,
                        currentFeatures = features
                    )
                }

                itemsToFlush?.let { records ->
                    flushRecords(records)
                }
            }
        }
    }

    private fun startTicker() {
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                val elapsed = (System.currentTimeMillis() - sessionStartTimeMs) / 1000L
                _recordingState.update { it.copy(elapsedSeconds = elapsed) }

                // Periodic flush every 5 seconds
                if (elapsed % 5L == 0L) {
                    flushPendingBuffer()
                }

                // Update notification display
                updateNotification(latestBpm, elapsed, totalSampleCounter)
            }
        }
    }

    private suspend fun flushPendingBuffer() {
        var itemsToFlush: List<HeartRateRecordEntity>? = null
        bufferMutex.withLock {
            if (buffer.isNotEmpty()) {
                itemsToFlush = buffer.toList()
                buffer.clear()
            }
        }
        itemsToFlush?.let { records ->
            flushRecords(records)
        }
    }

    private suspend fun flushRecords(records: List<HeartRateRecordEntity>) {
        if (records.isEmpty()) return
        try {
            DozeDialApplication.instance.database.heartRateRecordDao().insertBatch(records)
            Log.d(TAG, "Flushed ${records.size} samples to database (Total: $totalSampleCounter)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush records to database", e)
        }
    }

    private fun stopSession() {
        if (!_recordingState.value.isRecording) {
            stopSelf()
            return
        }

        sampleCollectorJob?.cancel()
        tickerJob?.cancel()

        serviceScope.launch {
            // Flush any remaining records in memory
            flushPendingBuffer()

            val sessionId = currentSessionId
            if (sessionId != null) {
                try {
                    val db = DozeDialApplication.instance.database
                    val existingSession = db.sleepSessionDao().getSessionById(sessionId)
                    if (existingSession != null) {
                        val recordCount = db.heartRateRecordDao().getRecordCount(sessionId)
                        val minBpm = db.heartRateRecordDao().getMinBpm(sessionId)
                        val maxBpm = db.heartRateRecordDao().getMaxBpm(sessionId)
                        val avgBpm = db.heartRateRecordDao().getAvgBpm(sessionId)

                        val finalizedSession = existingSession.copy(
                            endTime = System.currentTimeMillis(),
                            totalSamples = recordCount,
                            avgBpm = avgBpm,
                            minBpm = minBpm,
                            maxBpm = maxBpm
                        )
                        db.sleepSessionDao().update(finalizedSession)
                        Log.i(TAG, "Finalized sleep session $sessionId: count=$recordCount, avg=$avgBpm, min=$minBpm, max=$maxBpm")

                        // Update adaptive baseline from this finalized night
                        val records = db.heartRateRecordDao().getRecordsForSessionSync(sessionId)
                        val analysis = AdaptiveBaselineEngine.analyzeNight(records)
                        if (analysis != null) {
                            val currentBase = db.userSleepBaselineDao().getBaselineSync()
                            val updatedBase = AdaptiveBaselineEngine.updateBaselineWithNight(currentBase, analysis)
                            db.userSleepBaselineDao().insertOrUpdate(updatedBase)
                            Log.i(TAG, "Updated adaptive baseline: nights=${updatedBase.nightsAnalyzed}, rhr=${updatedBase.rollingRhr}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error finalizing session in DB", e)
                }
            }

            _recordingState.value = RecordingState(isRecording = false)
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DozeDial:SleepRecordingWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            Log.i(TAG, "Acquired partial wake lock for overnight recording")
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.i(TAG, "Released partial wake lock")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        } finally {
            wakeLock = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sleep Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active overnight heart rate recording status and controls"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        bpm: Int?,
        elapsedSeconds: Long,
        sampleCount: Int,
        stage: SleepStage = _recordingState.value.currentStage
    ): Notification {
        val hours = elapsedSeconds / 3600
        val minutes = (elapsedSeconds % 3600) / 60
        val seconds = elapsedSeconds % 60
        val durationStr = if (hours > 0) {
            "%dh %02dm %02ds".format(hours, minutes, seconds)
        } else {
            "%02dm %02ds".format(minutes, seconds)
        }

        val bpmText = if (bpm != null && bpm > 0) "$bpm BPM" else "-- BPM"
        val contentText = "Live HR: $bpmText • ${stage.shortName} • $durationStr • $sampleCount samples"

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, HeartRateCollectionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DozeDial Sleep Recording Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop & Save", stopPendingIntent)
            .build()
    }

    private fun updateNotification(bpm: Int?, elapsedSeconds: Long, sampleCount: Int) {
        val notification = buildNotification(bpm, elapsedSeconds, sampleCount)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }
}
