package com.dozedial.alarm

import com.dozedial.engine.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartAlarmLogicTest {

    private val targetMs = 1700003600000L // e.g. 7:30 AM
    private val windowMinutes = 20
    private val windowStartMs = targetMs - (windowMinutes * 60 * 1000L) // 7:10 AM

    @Test
    fun evaluateTrigger_beforeWindow_returnsNoTrigger() {
        val now = windowStartMs - 60000L // 7:09 AM
        val decision = SmartAlarmManager.evaluateTrigger(
            nowMs = now,
            targetMs = targetMs,
            windowMinutes = windowMinutes,
            currentStage = SleepStage.LIGHT
        )

        assertEquals(SmartAlarmManager.TriggerDecision.NO_TRIGGER, decision)
    }

    @Test
    fun evaluateTrigger_insideWindow_deepSleep_returnsNoTrigger() {
        val now = windowStartMs + 300000L // 7:15 AM
        // User is still in deep sleep; do NOT wake to prevent sleep inertia
        val decision = SmartAlarmManager.evaluateTrigger(
            nowMs = now,
            targetMs = targetMs,
            windowMinutes = windowMinutes,
            currentStage = SleepStage.DEEP
        )

        assertEquals(SmartAlarmManager.TriggerDecision.NO_TRIGGER, decision)
    }

    @Test
    fun evaluateTrigger_insideWindow_lightSleep_triggersSmartWake() {
        val now = windowStartMs + 300000L // 7:15 AM
        // User shifted into light sleep; trigger optimal smart alarm!
        val decision = SmartAlarmManager.evaluateTrigger(
            nowMs = now,
            targetMs = targetMs,
            windowMinutes = windowMinutes,
            currentStage = SleepStage.LIGHT
        )

        assertEquals(SmartAlarmManager.TriggerDecision.SMART_LIGHT_SLEEP, decision)
    }

    @Test
    fun evaluateTrigger_insideWindow_wakeArousal_triggersSmartWake() {
        val now = windowStartMs + 100000L // 7:11 AM
        val decision = SmartAlarmManager.evaluateTrigger(
            nowMs = now,
            targetMs = targetMs,
            windowMinutes = windowMinutes,
            currentStage = SleepStage.WAKE
        )

        assertEquals(SmartAlarmManager.TriggerDecision.SMART_LIGHT_SLEEP, decision)
    }

    @Test
    fun evaluateTrigger_exactCutoff_triggersHardCutoff() {
        val now = targetMs // 7:30:00 AM exactly
        val decision = SmartAlarmManager.evaluateTrigger(
            nowMs = now,
            targetMs = targetMs,
            windowMinutes = windowMinutes,
            currentStage = SleepStage.DEEP
        )

        assertEquals(SmartAlarmManager.TriggerDecision.HARD_CUTOFF, decision)
    }

    @Test
    fun evaluateTrigger_hardCutoffReached_triggersHardCutoffEvenInDeep() {
        val now = targetMs + 1000L // 7:30:01 AM
        val decision = SmartAlarmManager.evaluateTrigger(
            nowMs = now,
            targetMs = targetMs,
            windowMinutes = windowMinutes,
            currentStage = SleepStage.DEEP
        )

        assertEquals(SmartAlarmManager.TriggerDecision.HARD_CUTOFF, decision)
    }

    @Test
    fun evaluateTrigger_afterCutoff_triggersHardCutoffEvenInRem() {
        val now = targetMs + 5000L // 7:30:05 AM
        val decision = SmartAlarmManager.evaluateTrigger(
            nowMs = now,
            targetMs = targetMs,
            windowMinutes = windowMinutes,
            currentStage = SleepStage.REM
        )

        assertEquals(SmartAlarmManager.TriggerDecision.HARD_CUTOFF, decision)
    }

    @Test
    fun sessionTargetPinning_avoidsDynamicRolloverBug() {
        val config = AlarmConfig(targetHour = 7, targetMinute = 30)
        // Simulate session start at 11:30 PM (23:30)
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 30)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val sessionStartMs = calendar.timeInMillis
        val pinnedTargetMs = config.getNextTargetEpochMs(sessionStartMs)

        // At 7:30:01 AM next morning, evaluate with pinned target
        val wakeTimeMs = pinnedTargetMs + 1000L
        val decision = SmartAlarmManager.evaluateTrigger(
            nowMs = wakeTimeMs,
            targetMs = pinnedTargetMs,
            windowMinutes = config.smartWindowMinutes,
            currentStage = SleepStage.DEEP
        )

        assertEquals(
            "Hard cutoff must fire when now exceeds pinned session target",
            SmartAlarmManager.TriggerDecision.HARD_CUTOFF,
            decision
        )
    }

    @Test
    fun alarmConfig_nextTargetEpoch_calculatesCorrectOffset() {
        val config = AlarmConfig(targetHour = 8, targetMinute = 0)
        val now = 1000000L
        val target = config.getNextTargetEpochMs(now)
        assertTrue("Target epoch should be strictly in the future", target > now)
    }
}
