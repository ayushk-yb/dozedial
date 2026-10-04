package com.dozedial.baseline

import com.dozedial.data.baseline.AdaptiveBaselineEngine
import com.dozedial.data.baseline.UserSleepBaselineEntity
import com.dozedial.data.db.HeartRateRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveBaselineEngineTest {

    private fun createSyntheticRecords(
        count: Int,
        baseBpm: Int,
        varianceBpm: Int = 1,
        startTimeMs: Long = 1000000L
    ): List<HeartRateRecordEntity> {
        val list = mutableListOf<HeartRateRecordEntity>()
        for (i in 0 until count) {
            val bpmOffset = if (i % 2 == 0) varianceBpm else -varianceBpm
            list.add(
                HeartRateRecordEntity(
                    id = i.toLong(),
                    sessionId = 1L,
                    timestampMs = startTimeMs + (i * 1000L),
                    bpm = baseBpm + bpmOffset,
                    sensorContact = true,
                    energyExpended = null,
                    rawBytesHex = "00"
                )
            )
        }
        return list
    }

    @Test
    fun analyzeNight_insufficientSamples_returnsNull() {
        val shortRecords = createSyntheticRecords(count = 20, baseBpm = 60)
        val analysis = AdaptiveBaselineEngine.analyzeNight(shortRecords)
        assertNull(analysis)
    }

    @Test
    fun analyzeNight_validSession_computes10thPercentileRhrAndStd() {
        val records = createSyntheticRecords(count = 300, baseBpm = 58, varianceBpm = 1)
        val analysis = AdaptiveBaselineEngine.analyzeNight(records)

        assertNotNull(analysis)
        assertEquals(300, analysis!!.sampleCount)
        assertTrue("RHR should be near 57-58 BPM", analysis.nightRhr in 56.0..59.0)
        assertTrue("Deep std should be calm (< 1.5)", analysis.nightDeepStd < 1.5)
    }

    @Test
    fun updateBaselineWithNight_firstNight_initializesDirectly() {
        val analysis = AdaptiveBaselineEngine.NightAnalysis(
            nightRhr = 54.0,
            nightDeepStd = 1.20,
            nightWakeSpike = 5.0,
            diurnalDipPercent = 15.0,
            sampleCount = 500
        )

        val updated = AdaptiveBaselineEngine.updateBaselineWithNight(
            currentBaseline = null,
            analysis = analysis,
            timestampMs = 5000L
        )

        assertEquals(1, updated.nightsAnalyzed)
        assertEquals(54.0, updated.rollingRhr, 0.01)
        assertEquals(1.20, updated.deepSleepStdThreshold, 0.01)
        assertEquals(5.0, updated.wakeSpikeThreshold, 0.01)
    }

    @Test
    fun updateBaselineWithNight_multiNightEma_convergesGracefully() {
        var baseline: UserSleepBaselineEntity? = null

        // Simulate 7 nights where true RHR shifts gradually from 60 to 50 BPM
        val nightRhrs = listOf(60.0, 58.0, 56.0, 54.0, 52.0, 50.0, 50.0)

        for (nightRhr in nightRhrs) {
            val analysis = AdaptiveBaselineEngine.NightAnalysis(
                nightRhr = nightRhr,
                nightDeepStd = 1.25,
                nightWakeSpike = 4.5,
                diurnalDipPercent = 14.0,
                sampleCount = 600
            )
            baseline = AdaptiveBaselineEngine.updateBaselineWithNight(baseline, analysis)
        }

        assertNotNull(baseline)
        assertEquals(7, baseline!!.nightsAnalyzed)
        // Check that rolling RHR smoothed down towards 50
        assertTrue("Rolling RHR should converge towards 50 BPM", baseline.rollingRhr in 50.0..54.0)
    }
}
