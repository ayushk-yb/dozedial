package com.dozedial.engine

/**
 * Autonomic features computed from rolling 1 Hz heart rate dynamics over a sliding window
 * (typically 300 samples / 5 minutes).
 *
 * @property mean5m Arithmetic mean BPM across the rolling buffer.
 * @property stdDev5m Standard deviation of BPM across the rolling buffer.
 * @property slope15s 15-second BPM gradient (BPM[t] - BPM[t-15]).
 * @property dipPercentage Relative percentage dip compared to baseline resting heart rate.
 * @property sampleCount Number of valid data points currently in the rolling window.
 * @property latestBpm Most recently observed heart rate in BPM.
 */
data class SleepFeatures(
    val mean5m: Double = 0.0,
    val stdDev5m: Double = 0.0,
    val slope15s: Double = 0.0,
    val dipPercentage: Double = 0.0,
    val sampleCount: Int = 0,
    val latestBpm: Int = 0
)
