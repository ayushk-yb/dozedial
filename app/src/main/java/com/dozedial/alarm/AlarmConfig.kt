package com.dozedial.alarm

/**
 * User configuration parameters for the DozeDial Smart-Alarm subsystem.
 *
 * @property isEnabled Whether the alarm is armed.
 * @property targetHour Hour of the day (0-23) for the hard wake-up cutoff.
 * @property targetMinute Minute of the hour (0-59) for the hard wake-up cutoff.
 * @property smartWindowMinutes Duration of the pre-cutoff window (e.g. 20 min) during which
 * the engine searches for light sleep / arousal to trigger an optimal, inertia-free wake-up.
 * @property crescendoSeconds Ramp-up time in seconds for the alarm audio volume from 10% to 100%.
 * @property ringtoneUri Custom ringtone URI string, or null for default system alarm chime.
 * @property vibrate Whether to accompany the crescendo audio with rhythmic haptic vibration.
 */
data class AlarmConfig(
    val isEnabled: Boolean = false,
    val targetHour: Int = 7,
    val targetMinute: Int = 30,
    val smartWindowMinutes: Int = 20,
    val crescendoSeconds: Int = 45,
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true
) {
    /**
     * Calculates the next epoch timestamp (in milliseconds) for the target wake-up time.
     * If the time has already passed today, targets tomorrow.
     */
    fun getNextTargetEpochMs(nowMs: Long = System.currentTimeMillis()): Long {
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(java.util.Calendar.HOUR_OF_DAY, targetHour)
            set(java.util.Calendar.MINUTE, targetMinute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMs) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    /**
     * Calculates the starting epoch timestamp of the smart evaluation window.
     */
    fun getWindowStartEpochMs(nowMs: Long = System.currentTimeMillis()): Long {
        val target = getNextTargetEpochMs(nowMs)
        return target - (smartWindowMinutes * 60 * 1000L)
    }

    /**
     * Formats target time as HH:mm with AM/PM for UI display.
     */
    fun formattedTargetTime(): String {
        val hour12 = when {
            targetHour == 0 -> 12
            targetHour > 12 -> targetHour - 12
            else -> targetHour
        }
        val amPm = if (targetHour < 12) "AM" else "PM"
        return "%d:%02d %s".format(hour12, targetMinute, amPm)
    }

    /**
     * Formats the smart window range as e.g. "7:10 AM - 7:30 AM".
     */
    fun formattedWindowRange(): String {
        var winMinute = targetMinute - smartWindowMinutes
        var winHour = targetHour
        while (winMinute < 0) {
            winMinute += 60
            winHour = (winHour - 1 + 24) % 24
        }
        val startHour12 = when {
            winHour == 0 -> 12
            winHour > 12 -> winHour - 12
            else -> winHour
        }
        val startAmPm = if (winHour < 12) "AM" else "PM"
        val startStr = "%d:%02d %s".format(startHour12, winMinute, startAmPm)
        return "$startStr – ${formattedTargetTime()}"
    }
}
