package com.dozedial.engine

import com.dozedial.data.baseline.UserSleepBaselineEntity
import com.dozedial.data.db.HeartRateRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepStageClassifierTest {

    private val userBaseline = UserSleepBaselineEntity(
        rollingRhr = 56.0,
        deepSleepStdThreshold = 1.35,
        wakeSpikeThreshold = 4.8,
        nightsAnalyzed = 5
    )

    @Test
    fun classify_lowHeartRateAndMinimalVariance_returnsDeep() {
        val features = SleepFeatures(
            mean5m = 56.5,
            stdDev5m = 0.85,
            slope15s = 0.0,
            dipPercentage = 12.0,
            sampleCount = 120,
            latestBpm = 56
        )

        val stage = SleepStageClassifier.classify(features, userBaseline)
        assertEquals(SleepStage.DEEP, stage)
    }

    @Test
    fun classify_positiveGradientSpike_returnsWake() {
        val features = SleepFeatures(
            mean5m = 60.0,
            stdDev5m = 1.8,
            slope15s = 6.0, // Above wakeSpikeThreshold (4.8)
            dipPercentage = 5.0,
            sampleCount = 120,
            latestBpm = 68
        )

        val stage = SleepStageClassifier.classify(features, userBaseline)
        assertEquals(SleepStage.WAKE, stage)
    }

    @Test
    fun classify_elevatedMeanWithOscillatoryVariance_returnsRem() {
        val features = SleepFeatures(
            mean5m = 62.0, // RHR (56) + 6.0
            stdDev5m = 2.4, // In 1.4..3.8
            slope15s = 1.0,
            dipPercentage = 2.0,
            sampleCount = 120,
            latestBpm = 63
        )

        val stage = SleepStageClassifier.classify(features, userBaseline)
        assertEquals(SleepStage.REM, stage)
    }

    @Test
    fun classify_intermediateDynamics_returnsLight() {
        val features = SleepFeatures(
            mean5m = 58.0,
            stdDev5m = 1.8, // Above deep threshold (1.35), not quite high enough for REM
            slope15s = 0.5,
            dipPercentage = 8.0,
            sampleCount = 120,
            latestBpm = 58
        )

        val stage = SleepStageClassifier.classify(features, userBaseline)
        assertEquals(SleepStage.LIGHT, stage)
    }

    @Test
    fun classifySessionTimeline_generatesEpochSequence() {
        val records = mutableListOf<HeartRateRecordEntity>()
        val startMs = 1700000000000L

        // 100 samples of calm deep sleep (100 seconds)
        for (i in 0 until 100) {
            records.add(
                HeartRateRecordEntity(
                    id = i.toLong(),
                    sessionId = 1L,
                    timestampMs = startMs + (i * 1000L),
                    bpm = 56,
                    sensorContact = true,
                    energyExpended = null,
                    rawBytesHex = "00"
                )
            )
        }

        val epochs = SleepStageClassifier.classifySessionTimeline(
            records = records,
            baseline = userBaseline,
            epochDurationSec = 30
        )

        assertTrue("Should produce multiple 30s epochs", epochs.size >= 3)
    }
}
