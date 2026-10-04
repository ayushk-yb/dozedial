package com.dozedial.alarm

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences storage for user alarm preferences.
 */
class AlarmPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<AlarmConfig> = _config.asStateFlow()

    fun loadConfig(): AlarmConfig {
        return AlarmConfig(
            isEnabled = prefs.getBoolean(KEY_IS_ENABLED, false),
            targetHour = prefs.getInt(KEY_TARGET_HOUR, 7),
            targetMinute = prefs.getInt(KEY_TARGET_MINUTE, 30),
            smartWindowMinutes = prefs.getInt(KEY_SMART_WINDOW, 20),
            crescendoSeconds = prefs.getInt(KEY_CRESCENDO, 45),
            ringtoneUri = prefs.getString(KEY_RINGTONE_URI, null),
            vibrate = prefs.getBoolean(KEY_VIBRATE, true)
        )
    }

    fun saveConfig(newConfig: AlarmConfig) {
        prefs.edit().apply {
            putBoolean(KEY_IS_ENABLED, newConfig.isEnabled)
            putInt(KEY_TARGET_HOUR, newConfig.targetHour)
            putInt(KEY_TARGET_MINUTE, newConfig.targetMinute)
            putInt(KEY_SMART_WINDOW, newConfig.smartWindowMinutes)
            putInt(KEY_CRESCENDO, newConfig.crescendoSeconds)
            putString(KEY_RINGTONE_URI, newConfig.ringtoneUri)
            putBoolean(KEY_VIBRATE, newConfig.vibrate)
            apply()
        }
        _config.value = newConfig
    }

    companion object {
        private const val PREFS_NAME = "dozedial_alarm_prefs"
        private const val KEY_IS_ENABLED = "alarm_is_enabled"
        private const val KEY_TARGET_HOUR = "alarm_target_hour"
        private const val KEY_TARGET_MINUTE = "alarm_target_minute"
        private const val KEY_SMART_WINDOW = "alarm_smart_window"
        private const val KEY_CRESCENDO = "alarm_crescendo"
        private const val KEY_RINGTONE_URI = "alarm_ringtone_uri"
        private const val KEY_VIBRATE = "alarm_vibrate"
    }
}
