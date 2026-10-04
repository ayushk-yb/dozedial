package com.dozedial.ble

import com.dozedial.data.HeartRateSample
import java.time.Instant

/**
 * Dedicated parser for the Bluetooth SIG Heart Rate Measurement characteristic (0x2A37).
 *
 * Implements the GATT Heart Rate Service specification:
 * - Byte 0: Flags
 *   - Bit 0: Heart Rate Value Format (0 = UINT8, 1 = UINT16)
 *   - Bit 1: Sensor Contact Feature Supported (0 = not supported, 1 = supported)
 *   - Bit 2: Sensor Contact Detected (0 = not detected, 1 = detected)
 *   - Bit 3: Energy Expended Present (0 = false, 1 = true)
 *   - Bit 4: RR-Interval Present (0 = false, 1 = one or more UINT16 values present)
 *   - Bits 5-7: Reserved for future use
 *
 * RR intervals are provided in units of 1/1024 seconds and converted to milliseconds:
 *   ms = round(rawRR * 1000.0 / 1024.0)
 */
object HeartRateParser {

    private const val FLAG_HR_FORMAT_UINT16 = 0x01
    private const val FLAG_SENSOR_CONTACT_SUPPORTED = 0x02
    private const val FLAG_SENSOR_CONTACT_DETECTED = 0x04
    private const val FLAG_ENERGY_EXPENDED_PRESENT = 0x08
    private const val FLAG_RR_INTERVALS_PRESENT = 0x10

    /**
     * Parses a raw Bluetooth GATT notification payload into a [HeartRateSample].
     *
     * @param rawBytes The raw byte array received from characteristic 0x2A37.
     * @param timestamp Capture timestamp, defaulting to [Instant.now].
     * @return [HeartRateSample] if the packet is valid, or null if null/empty/truncated.
     */
    fun parse(rawBytes: ByteArray?, timestamp: Instant = Instant.now()): HeartRateSample? {
        if (rawBytes == null || rawBytes.isEmpty()) {
            return null
        }

        try {
            val flags = rawBytes[0].toInt() and 0xFF
            val isUint16 = (flags and FLAG_HR_FORMAT_UINT16) != 0
            val contactSupported = (flags and FLAG_SENSOR_CONTACT_SUPPORTED) != 0
            val sensorContactDetected = if (contactSupported) {
                (flags and FLAG_SENSOR_CONTACT_DETECTED) != 0
            } else {
                null
            }
            val hasEnergyExpended = (flags and FLAG_ENERGY_EXPENDED_PRESENT) != 0
            val hasRrIntervals = (flags and FLAG_RR_INTERVALS_PRESENT) != 0

            var offset = 1

            // Parse Heart Rate value
            val bpm: Int
            if (isUint16) {
                if (offset + 2 > rawBytes.size) return null
                val low = rawBytes[offset].toInt() and 0xFF
                val high = rawBytes[offset + 1].toInt() and 0xFF
                bpm = (high shl 8) or low
                offset += 2
            } else {
                if (offset + 1 > rawBytes.size) return null
                bpm = rawBytes[offset].toInt() and 0xFF
                offset += 1
            }

            // Parse Energy Expended if present (UINT16 in kJ)
            val energyExpended: Int? = if (hasEnergyExpended) {
                if (offset + 2 > rawBytes.size) return null
                val low = rawBytes[offset].toInt() and 0xFF
                val high = rawBytes[offset + 1].toInt() and 0xFF
                offset += 2
                (high shl 8) or low
            } else {
                null
            }

            // Parse RR Intervals if present (UINT16 values in 1/1024s)
            val rrIntervalsMs = mutableListOf<Int>()
            if (hasRrIntervals) {
                while (offset + 1 < rawBytes.size) {
                    val low = rawBytes[offset].toInt() and 0xFF
                    val high = rawBytes[offset + 1].toInt() and 0xFF
                    val rawRr = (high shl 8) or low
                    offset += 2
                    // Convert from 1/1024 seconds to milliseconds
                    val ms = Math.round(rawRr * 1000.0 / 1024.0).toInt()
                    rrIntervalsMs.add(ms)
                }
            }

            return HeartRateSample(
                timestamp = timestamp,
                bpm = bpm,
                rrIntervalsMs = rrIntervalsMs,
                sensorContactDetected = sensorContactDetected,
                energyExpended = energyExpended,
                rawBytes = rawBytes.copyOf()
            )
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Inspects whether the raw packet indicates UINT16 heart rate format.
     */
    fun isUint16Format(rawBytes: ByteArray?): Boolean {
        if (rawBytes == null || rawBytes.isEmpty()) return false
        return (rawBytes[0].toInt() and FLAG_HR_FORMAT_UINT16) != 0
    }
}
