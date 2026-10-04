package com.dozedial.engine

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Maintains a rolling ring buffer of 1 Hz heart rate samples (typically 300 samples / 5 minutes)
 * and extracts continuous autonomic features for sleep staging.
 */
class SleepFeatureExtractor(val windowSize: Int = 300) {

    private val ringBuffer = ArrayDeque<Int>(windowSize)

    @Synchronized
    fun addSample(bpm: Int) {
        if (bpm <= 0) return
        if (ringBuffer.size >= windowSize) {
            ringBuffer.removeFirst()
        }
        ringBuffer.addLast(bpm)
    }

    @Synchronized
    fun computeFeatures(baselineRhr: Double = 60.0): SleepFeatures {
        if (ringBuffer.isEmpty()) {
            return SleepFeatures()
        }

        val count = ringBuffer.size
        val latest = ringBuffer.last()
        val mean = ringBuffer.average()

        val variance = ringBuffer.sumOf { (it - mean).pow(2) } / count
        val stdDev = sqrt(variance)

        // Calculate 15-second slope: BPM difference between newest sample and sample from 15 steps ago
        val slopeLookback = 15.coerceAtMost(count - 1)
        val slope = if (slopeLookback > 0) {
            val pastIndex = count - 1 - slopeLookback
            (latest - ringBuffer[pastIndex]).toDouble()
        } else {
            0.0
        }

        val dipPercentage = if (baselineRhr > 0) {
            ((baselineRhr - mean) / baselineRhr) * 100.0
        } else {
            0.0
        }

        return SleepFeatures(
            mean5m = mean,
            stdDev5m = stdDev,
            slope15s = slope,
            dipPercentage = dipPercentage,
            sampleCount = count,
            latestBpm = latest
        )
    }

    @Synchronized
    fun getBufferSnapshot(): List<Int> {
        return ringBuffer.toList()
    }

    @Synchronized
    fun reset() {
        ringBuffer.clear()
    }
}
