package com.dozedial.engine

import com.dozedial.data.baseline.UserSleepBaselineEntity
import com.dozedial.data.db.HeartRateRecordEntity

/**
 * Real-time and batch sleep stage classifier using autonomic heart rate dynamics.
 *
 * Grounded in autonomic neuroscience:
 * - DEEP sleep exhibits dominant parasympathetic tone with near-zero respiratory sinus arrhythmia
 *   variance (sigma <= deepSleepStdThreshold) and a sustained heart rate nadir.
 * - WAKE / AROUSAL presents sudden sympathetic activation (gradient spike >= wakeSpikeThreshold
 *   or high turbulent standard deviation).
 * - REM exhibits elevated mean heart rate with rapid oscillatory moderate variance.
 * - LIGHT sleep represents intermediate transitional sleep, the optimal waking window
 *   to avoid sleep inertia.
 */
object SleepStageClassifier {

    data class EpochStage(
        val timestampMs: Long,
        val stage: SleepStage,
        val bpm: Int
    )

    /**
     * Classifies real-time state given current rolling features and the user's personalized baseline.
     */
    fun classify(
        features: SleepFeatures,
        baseline: UserSleepBaselineEntity = UserSleepBaselineEntity.default()
    ): SleepStage {
        if (features.sampleCount < 10) {
            return if (features.latestBpm > baseline.rollingRhr * 1.25) {
                SleepStage.WAKE
            } else {
                SleepStage.LIGHT
            }
        }

        val rhr = baseline.rollingRhr
        val deepStdThreshold = baseline.deepSleepStdThreshold
        val wakeSpikeThreshold = baseline.wakeSpikeThreshold

        // 1. Wake / Arousal Detection (Sympathetic surge or turbulent volatility)
        val isArousalSpike = features.slope15s >= wakeSpikeThreshold
        val isTurbulentVariance = features.stdDev5m >= 3.8
        val isElevatedWakeRate = features.mean5m >= (rhr * 1.30) || features.latestBpm >= (rhr * 1.35)

        if (isArousalSpike || isTurbulentVariance || isElevatedWakeRate) {
            return SleepStage.WAKE
        }

        // 2. Deep Sleep Detection (Parasympathetic stability nadir)
        val isParasympatheticNadir = features.mean5m <= (rhr + 3.5)
        val isMinimalVariance = features.stdDev5m <= deepStdThreshold
        if (isParasympatheticNadir && isMinimalVariance && features.sampleCount >= 20) {
            return SleepStage.DEEP
        }

        // 3. REM Sleep Detection (Elevated tone with characteristic oscillatory variance)
        val isElevatedTone = features.mean5m >= (rhr + 3.0)
        val isRemVariance = features.stdDev5m in 1.4..3.8
        if (isElevatedTone && isRemVariance) {
            return SleepStage.REM
        }

        // 4. Default: Light transitional sleep (Optimal wake-up stage)
        return SleepStage.LIGHT
    }

    /**
     * Reconstructs a full-night hypnogram timeline from recorded heart rate packets.
     * Divides session into fixed-duration epochs (e.g. 30 seconds) and classifies each epoch.
     */
    fun classifySessionTimeline(
        records: List<HeartRateRecordEntity>,
        baseline: UserSleepBaselineEntity = UserSleepBaselineEntity.default(),
        epochDurationSec: Int = 30
    ): List<EpochStage> {
        if (records.isEmpty()) return emptyList()

        val extractor = SleepFeatureExtractor(windowSize = 300)
        val result = mutableListOf<EpochStage>()

        val epochMs = epochDurationSec * 1000L
        var nextEpochBoundary = records.first().timestampMs + epochMs

        for (record in records) {
            extractor.addSample(record.bpm)

            if (record.timestampMs >= nextEpochBoundary) {
                val features = extractor.computeFeatures(baseline.rollingRhr)
                val stage = classify(features, baseline)
                result.add(
                    EpochStage(
                        timestampMs = record.timestampMs,
                        stage = stage,
                        bpm = record.bpm
                    )
                )
                nextEpochBoundary = record.timestampMs + epochMs
            }
        }

        // Ensure the last segment is captured if needed
        if (result.isEmpty() && records.isNotEmpty()) {
            val features = extractor.computeFeatures(baseline.rollingRhr)
            val stage = classify(features, baseline)
            result.add(
                EpochStage(
                    timestampMs = records.last().timestampMs,
                    stage = stage,
                    bpm = records.last().bpm
                )
            )
        }

        return result
    }
}
