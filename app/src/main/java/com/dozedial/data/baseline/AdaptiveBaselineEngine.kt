package com.dozedial.data.baseline

import com.dozedial.data.db.HeartRateRecordEntity
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * On-device continuous learning engine that refines the user's autonomic sleep profile
 * after each completed sleep session.
 *
 * Uses Exponential Moving Average (EMA) with alpha=0.3 (~7-night rolling memory) to update:
 * 1. Sustained nocturnal Resting Heart Rate (10th percentile nadir).
 * 2. Deep sleep standard deviation threshold (parasympathetic stability).
 * 3. Wake / arousal gradient spike threshold.
 */
object AdaptiveBaselineEngine {

    const val MINIMUM_SAMPLES_FOR_ANALYSIS = 60
    private const val EMA_ALPHA = 0.30

    data class NightAnalysis(
        val nightRhr: Double,
        val nightDeepStd: Double,
        val nightWakeSpike: Double,
        val diurnalDipPercent: Double,
        val sampleCount: Int
    )

    /**
     * Extracts autonomic features from a single night's heart rate telemetry records.
     * Returns null if the session has insufficient data.
     */
    fun analyzeNight(records: List<HeartRateRecordEntity>): NightAnalysis? {
        val validBpms = records.map { it.bpm }.filter { it in 30..220 }
        if (validBpms.size < MINIMUM_SAMPLES_FOR_ANALYSIS) {
            return null
        }

        val sortedBpms = validBpms.sorted()
        // 10th percentile BPM represents sustained resting nadir free from transient drops
        val p10Index = (sortedBpms.size * 0.10).toInt().coerceIn(0, sortedBpms.lastIndex)
        val nightRhr = sortedBpms[p10Index].toDouble()

        // 90th percentile to estimate pre-sleep/arousal baseline for diurnal dip calculation
        val p90Index = (sortedBpms.size * 0.90).toInt().coerceIn(0, sortedBpms.lastIndex)
        val nightHigh = sortedBpms[p90Index].toDouble()
        val diurnalDipPercent = if (nightHigh > nightRhr) {
            ((nightHigh - nightRhr) / nightHigh) * 100.0
        } else {
            10.0
        }

        // Compute deep sleep std dev by examining calm segments near the RHR nadir (RHR to RHR + 4 BPM)
        val calmBpms = validBpms.filter { it.toDouble() <= nightRhr + 4.0 }
        val nightDeepStd = if (calmBpms.size >= 15) {
            val mean = calmBpms.average()
            val variance = calmBpms.sumOf { (it - mean).pow(2) } / calmBpms.size
            sqrt(variance).coerceIn(0.8, 2.5)
        } else {
            1.35
        }

        // Compute 15-sample (approx 15-second) positive gradients to measure typical arousal spikes
        val gradients = mutableListOf<Double>()
        val step = 15
        if (records.size > step) {
            for (i in step until records.size) {
                val delta = records[i].bpm - records[i - step].bpm
                if (delta > 0) {
                    gradients.add(delta.toDouble())
                }
            }
        }

        val nightWakeSpike = if (gradients.isNotEmpty()) {
            val sortedGradients = gradients.sorted()
            val p80 = (sortedGradients.size * 0.80).toInt().coerceIn(0, sortedGradients.lastIndex)
            sortedGradients[p80].coerceIn(3.0, 9.0)
        } else {
            4.8
        }

        return NightAnalysis(
            nightRhr = nightRhr,
            nightDeepStd = nightDeepStd,
            nightWakeSpike = nightWakeSpike,
            diurnalDipPercent = diurnalDipPercent,
            sampleCount = validBpms.size
        )
    }

    /**
     * Updates an existing baseline (or initializes a new one) with metrics from a completed night.
     */
    fun updateBaselineWithNight(
        currentBaseline: UserSleepBaselineEntity?,
        analysis: NightAnalysis,
        timestampMs: Long = System.currentTimeMillis()
    ): UserSleepBaselineEntity {
        val prev = currentBaseline ?: UserSleepBaselineEntity.default()

        return if (prev.nightsAnalyzed == 0) {
            UserSleepBaselineEntity(
                id = 1,
                rollingRhr = analysis.nightRhr,
                rollingDiurnalDip = analysis.diurnalDipPercent,
                deepSleepStdThreshold = analysis.nightDeepStd,
                wakeSpikeThreshold = analysis.nightWakeSpike,
                nightsAnalyzed = 1,
                lastUpdatedMs = timestampMs
            )
        } else {
            val updatedRhr = (1.0 - EMA_ALPHA) * prev.rollingRhr + EMA_ALPHA * analysis.nightRhr
            val updatedDip = (1.0 - EMA_ALPHA) * prev.rollingDiurnalDip + EMA_ALPHA * analysis.diurnalDipPercent
            val updatedDeepStd = (1.0 - EMA_ALPHA) * prev.deepSleepStdThreshold + EMA_ALPHA * analysis.nightDeepStd
            val updatedWakeSpike = (1.0 - EMA_ALPHA) * prev.wakeSpikeThreshold + EMA_ALPHA * analysis.nightWakeSpike

            UserSleepBaselineEntity(
                id = 1,
                rollingRhr = updatedRhr,
                rollingDiurnalDip = updatedDip,
                deepSleepStdThreshold = updatedDeepStd,
                wakeSpikeThreshold = updatedWakeSpike,
                nightsAnalyzed = prev.nightsAnalyzed + 1,
                lastUpdatedMs = timestampMs
            )
        }
    }
}
