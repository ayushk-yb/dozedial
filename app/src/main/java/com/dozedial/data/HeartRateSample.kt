package com.dozedial.data

import java.time.Instant

/**
 * Immutable model representing a single Heart Rate Measurement sample parsed from
 * the standard Bluetooth SIG Heart Rate Service (UUID 0x180D, Characteristic 0x2A37).
 *
 * This data model preserves all raw information, timestamps, and RR intervals
 * for future consumption by sleep-state and smart-alarm engines.
 *
 * @property timestamp The moment this sample was captured/parsed.
 * @property bpm Heart rate in beats per minute.
 * @property rrIntervalsMs List of parsed RR intervals in milliseconds (resolution converted from 1/1024s).
 * @property sensorContactDetected True if sensor contact is detected, false if sensor contact is not detected,
 *                                 or null if the sensor contact feature is not supported by the device.
 * @property energyExpended Total energy expended in kiloJoules (kJ), if reported in this packet.
 * @property rawBytes The original raw payload bytes received in the GATT notification.
 */
data class HeartRateSample(
    val timestamp: Instant,
    val bpm: Int,
    val rrIntervalsMs: List<Int>,
    val sensorContactDetected: Boolean?,
    val energyExpended: Int?,
    val rawBytes: ByteArray
) {
    /**
     * Formats the raw bytes as space-separated uppercase hexadecimal values (e.g. "06 48 03 2F 03 35 03").
     */
    val rawBytesHex: String
        get() = rawBytes.joinToString(" ") { byte -> "%02X".format(byte) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HeartRateSample

        if (timestamp != other.timestamp) return false
        if (bpm != other.bpm) return false
        if (rrIntervalsMs != other.rrIntervalsMs) return false
        if (sensorContactDetected != other.sensorContactDetected) return false
        if (energyExpended != other.energyExpended) return false
        if (!rawBytes.contentEquals(other.rawBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + bpm
        result = 31 * result + rrIntervalsMs.hashCode()
        result = 31 * result + (sensorContactDetected?.hashCode() ?: 0)
        result = 31 * result + (energyExpended ?: 0)
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}
