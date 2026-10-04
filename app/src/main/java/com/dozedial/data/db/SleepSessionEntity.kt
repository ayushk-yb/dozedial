package com.dozedial.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an overnight or nap sleep recording session.
 *
 * @property sessionId Unique identifier for the session (auto-generated).
 * @property startTime Epoch millisecond timestamp when recording began.
 * @property endTime Epoch millisecond timestamp when recording finished (null while recording).
 * @property deviceAddress MAC address of the BLE peripheral (e.g. Fitbit Air).
 * @property deviceName Human-readable advertised name of the peripheral.
 * @property totalSamples Count of raw heart rate packets captured during the session.
 * @property avgBpm Arithmetic mean of BPM across all non-zero packets.
 * @property minBpm Lowest valid recorded BPM during the session.
 * @property maxBpm Highest valid recorded BPM during the session.
 * @property notes Optional user notes or annotations for sleep feasibility analysis.
 */
@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val sessionId: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val deviceAddress: String,
    val deviceName: String?,
    val totalSamples: Int = 0,
    val avgBpm: Double? = null,
    val minBpm: Int? = null,
    val maxBpm: Int? = null,
    val notes: String? = null
)
