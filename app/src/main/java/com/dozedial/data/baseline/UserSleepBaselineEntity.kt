package com.dozedial.data.baseline

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the user's learned autonomic baseline parameters across multi-night sleep recordings.
 *
 * Rather than relying on rigid population averages, DozeDial adapts to each user's personal
 * resting heart rate, deep sleep variance nadir, and waking arousal spike gradients.
 *
 * @property id Singleton primary key (fixed to 1 for single-user profile).
 * @property rollingRhr 7-night Exponential Moving Average (EMA) of sustained resting heart rate (BPM).
 * @property rollingDiurnalDip Typical percentage drop from daytime baseline to nocturnal nadir.
 * @property deepSleepStdThreshold Learned rolling standard deviation cutoff for parasympathetic deep sleep (BPM).
 * @property wakeSpikeThreshold Learned rolling positive gradient cutoff indicating an arousal/wake spike (BPM / 15s).
 * @property nightsAnalyzed Count of completed nights incorporated into this baseline model.
 * @property lastUpdatedMs Timestamp of the most recent baseline update.
 */
@Entity(tableName = "user_sleep_baseline")
data class UserSleepBaselineEntity(
    @PrimaryKey val id: Int = 1,
    val rollingRhr: Double = 60.0,
    val rollingDiurnalDip: Double = 12.0,
    val deepSleepStdThreshold: Double = 1.35,
    val wakeSpikeThreshold: Double = 4.8,
    val nightsAnalyzed: Int = 0,
    val lastUpdatedMs: Long = 0L
) {
    companion object {
        fun default(): UserSleepBaselineEntity = UserSleepBaselineEntity()
    }
}
