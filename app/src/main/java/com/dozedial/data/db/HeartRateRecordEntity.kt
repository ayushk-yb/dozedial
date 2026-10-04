package com.dozedial.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing a single parsed BLE heart rate packet within a sleep session.
 *
 * @property id Primary key for the record.
 * @property sessionId Identifier of the parent [SleepSessionEntity].
 * @property timestampMs Epoch millisecond timestamp when this packet arrived.
 * @property bpm Heart rate in beats per minute.
 * @property sensorContact Whether the optical PPG sensor had skin contact (null if unsupported).
 * @property energyExpended Cumulative energy expended in kJ, if reported.
 * @property rawBytesHex Hex-formatted raw payload bytes for byte-level auditing and verification.
 */
@Entity(
    tableName = "heart_rate_records",
    indices = [
        Index(value = ["sessionId", "timestampMs"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class HeartRateRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestampMs: Long,
    val bpm: Int,
    val sensorContact: Boolean?,
    val energyExpended: Int?,
    val rawBytesHex: String
)
