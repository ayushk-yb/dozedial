package com.dozedial.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Manages system exact alarm scheduling and real-time smart-window trigger evaluations.
 */
object SmartAlarmManager {

    private const val TAG = "SmartAlarmManager"
    const val REQUEST_CODE_EXACT_ALARM = 4001

    enum class TriggerDecision {
        NO_TRIGGER,
        SMART_LIGHT_SLEEP,
        HARD_CUTOFF
    }

    /**
     * Pure logic function to evaluate whether the alarm should fire given current time,
     * target alarm cutoff, window size, and user's current sleep stage.
     */
    fun evaluateTrigger(
        nowMs: Long,
        targetMs: Long,
        windowMinutes: Int,
        currentStage: com.dozedial.engine.SleepStage
    ): TriggerDecision {
        // 1. Hard cutoff reached or exceeded
        if (nowMs >= targetMs) {
            return TriggerDecision.HARD_CUTOFF
        }

        // 2. Inside the smart evaluation window
        val windowStartMs = targetMs - (windowMinutes * 60 * 1000L)
        if (nowMs >= windowStartMs) {
            if (currentStage.isOptimalForWaking) {
                return TriggerDecision.SMART_LIGHT_SLEEP
            }
        }

        return TriggerDecision.NO_TRIGGER
    }

    /**
     * Schedules an exact system alarm via [AlarmManager.setExactAndAllowWhileIdle].
     * Guarantees waking the device at the target cutoff even in deep Doze mode.
     */
    fun scheduleExactSafetyAlarm(context: Context, config: AlarmConfig) {
        if (!config.isEnabled) {
            cancelSafetyAlarm(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "SCHEDULE_EXACT_ALARM permission not granted by user")
            }
        }

        val targetMs = config.getNextTargetEpochMs()
        val intent = Intent(context, SmartAlarmReceiver::class.java).apply {
            action = SmartAlarmReceiver.ACTION_SAFETY_CUTOFF
            putExtra(SmartAlarmReceiver.EXTRA_REASON, "Hard Cutoff Alarm Reached")
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_EXACT_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetMs,
                pendingIntent
            )
            Log.i(TAG, "Scheduled exact safety alarm for timestamp $targetMs (${config.formattedTargetTime()})")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule exact alarm due to security policy", e)
        }
    }

    /**
     * Cancels any pending exact safety alarm.
     */
    fun cancelSafetyAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, SmartAlarmReceiver::class.java).apply {
            action = SmartAlarmReceiver.ACTION_SAFETY_CUTOFF
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_EXACT_ALARM,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.i(TAG, "Cancelled exact safety alarm")
        }
    }

    /**
     * Fires the alarm immediately, launching the playback service and full-screen sunrise UI.
     */
    fun triggerAlarmNow(context: Context, reason: String, stage: com.dozedial.engine.SleepStage = com.dozedial.engine.SleepStage.LIGHT) {
        val intent = Intent(context, SmartAlarmReceiver::class.java).apply {
            action = SmartAlarmReceiver.ACTION_SMART_TRIGGER
            putExtra(SmartAlarmReceiver.EXTRA_REASON, reason)
            putExtra(SmartAlarmReceiver.EXTRA_STAGE, stage.name)
        }
        context.sendBroadcast(intent)
    }
}
