package com.dozedial.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * BroadcastReceiver triggered either by Android [android.app.AlarmManager] at the safety cutoff
 * or directly by the smart staging engine when light sleep is detected inside the window.
 */
class SmartAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Smart Alarm Triggered"
        val stageName = intent.getStringExtra(EXTRA_STAGE) ?: "LIGHT"

        Log.i(TAG, "Received alarm intent action: $action, reason: $reason, stage: $stageName")

        // 1. Start alarm playback service (audio crescendo and haptics)
        val serviceIntent = Intent(context, AlarmPlaybackService::class.java).apply {
            this.action = AlarmPlaybackService.ACTION_START_ALARM
            putExtra(EXTRA_REASON, reason)
            putExtra(EXTRA_STAGE, stageName)
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start AlarmPlaybackService", e)
        }

        // 2. Launch full-screen lockscreen wake activity
        val wakeIntent = Intent(context, AlarmWakeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_REASON, reason)
            putExtra(EXTRA_STAGE, stageName)
        }
        try {
            context.startActivity(wakeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch AlarmWakeActivity", e)
        }
    }

    companion object {
        private const val TAG = "SmartAlarmReceiver"
        const val ACTION_SAFETY_CUTOFF = "com.dozedial.alarm.ACTION_SAFETY_CUTOFF"
        const val ACTION_SMART_TRIGGER = "com.dozedial.alarm.ACTION_SMART_TRIGGER"
        const val EXTRA_REASON = "extra_alarm_reason"
        const val EXTRA_STAGE = "extra_wake_stage"
    }
}
