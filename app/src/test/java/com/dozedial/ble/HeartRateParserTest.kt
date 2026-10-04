package com.dozedial.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit tests verifying Bluetooth SIG Heart Rate Measurement (0x2A37) parsing.
 *
 * Verifies all 8 required cases from project specification:
 * 1. UINT8 heart rate
 * 2. UINT16 heart rate
 * 3. Single RR interval
 * 4. Multiple RR intervals
 * 5. Sensor contact present (supported+detected, supported+not detected, not supported)
 * 6. Energy expended present
 * 7. Combinations of flags
 * 8. Invalid/truncated/malformed packets
 */
class HeartRateParserTest {

    private val fixedTimestamp = Instant.parse("2026-10-04T12:00:00Z")

    @Test
    fun parse_uint8HeartRate_returnsCorrectBpm() {
        // Flags: 0x00 (UINT8, contact not supported, no energy, no RR)
        // HR: 72 BPM (0x48)
        val packet = byteArrayOf(0x00, 0x48)

        val sample = HeartRateParser.parse(packet, fixedTimestamp)

        assertNotNull(sample)
        assertEquals(fixedTimestamp, sample!!.timestamp)
        assertEquals(72, sample.bpm)
        assertNull(sample.sensorContactDetected)
        assertNull(sample.energyExpended)
        assertTrue(sample.rrIntervalsMs.isEmpty())
        assertTrue(sample.rawBytes.contentEquals(packet))
    }

    @Test
    fun parse_uint16HeartRate_returnsCorrectBpm() {
        // Flags: 0x01 (UINT16 format)
        // HR: 300 BPM (0x012C -> low byte 0x2C, high byte 0x01)
        val packet = byteArrayOf(0x01, 0x2C, 0x01)

        val sample = HeartRateParser.parse(packet, fixedTimestamp)

        assertNotNull(sample)
        assertEquals(300, sample!!.bpm)
        assertNull(sample.sensorContactDetected)
        assertNull(sample.energyExpended)
        assertTrue(sample.rrIntervalsMs.isEmpty())
    }

    @Test
    fun parse_singleRrInterval_parsedAndConvertedToMs() {
        // Flags: 0x10 (UINT8 + RR intervals present)
        // HR: 70 BPM (0x46)
        // RR: 800 (1/1024s) -> low: 0x20, high: 0x03 (0x0320 = 800)
        // 800 * 1000 / 1024 = 781.25 -> 781 ms
        val packet = byteArrayOf(0x10, 0x46, 0x20, 0x03)

        val sample = HeartRateParser.parse(packet, fixedTimestamp)

        assertNotNull(sample)
        assertEquals(70, sample!!.bpm)
        assertEquals(1, sample.rrIntervalsMs.size)
        assertEquals(781, sample.rrIntervalsMs[0])
    }

    @Test
    fun parse_multipleRrIntervals_preservesAllIntervals() {
        // Flags: 0x10 (UINT8 + RR intervals)
        // HR: 72 BPM (0x48)
        // RR 1: 0x033F = 831 -> round(831 * 1000 / 1024) = 812 ms
        // RR 2: 0x034D = 845 -> round(845 * 1000 / 1024) = 825 ms
        val packet = byteArrayOf(
            0x10,
            0x48,
            0x3F.toByte(), 0x03, // 831 raw -> 812 ms
            0x4D.toByte(), 0x03  // 845 raw -> 825 ms
        )

        val sample = HeartRateParser.parse(packet, fixedTimestamp)

        assertNotNull(sample)
        assertEquals(72, sample!!.bpm)
        assertEquals(listOf(812, 825), sample.rrIntervalsMs)
    }

    @Test
    fun parse_sensorContactDetected_returnsTrue() {
        // Flags: 0x06 (Bit 1 = supported, Bit 2 = contact detected)
        val packet = byteArrayOf(0x06, 0x50) // 80 BPM

        val sample = HeartRateParser.parse(packet, fixedTimestamp)

        assertNotNull(sample)
        assertEquals(80, sample!!.bpm)
        assertEquals(true, sample.sensorContactDetected)
    }

    @Test
    fun parse_sensorContactSupportedButNotDetected_returnsFalse() {
        // Flags: 0x02 (Bit 1 = supported, Bit 2 = NOT detected)
        val packet = byteArrayOf(0x02, 0x50)

        val sample = HeartRateParser.parse(packet, fixedTimestamp)

        assertNotNull(sample)
        assertEquals(80, sample!!.bpm)
        assertEquals(false, sample.sensorContactDetected)
    }

    @Test
    fun parse_sensorContactNotSupported_returnsNull() {
        // Flags: 0x00 (Bit 1 = not supported)
        val packet = byteArrayOf(0x00, 0x50)

        val sample = HeartRateParser.parse(packet, fixedTimestamp)

        assertNotNull(sample)
        assertNull(sample!!.sensorContactDetected)
    }

    @Test
    fun parse_energyExpendedPresent_returnsCorrectJoules() {
        // Flags: 0x08 (Energy Expended bit set)
        // HR: 65 BPM (0x41)
        // Energy: 1000 kJ (0x03E8 -> low: 0xE8, high: 0x03)
        val packet = byteArrayOf(0x08, 0x41, 0xE8.toByte(), 0x03)

        val sample = HeartRateParser.parse(packet, fixedTimestamp)

        assertNotNull(sample)
        assertEquals(65, sample!!.bpm)
        assertEquals(1000, sample.energyExpended)
    }

    @Test
    fun parse_fullCombination_uint8SensorContactEnergyAndMultipleRr() {
        // Flags: 0x1E
        //   - UINT8 (bit 0 = 0)
        //   - Sensor contact supported & detected (bit 1 = 1, bit 2 = 1 -> 0x06)
        //   - Energy expended (bit 3 = 1 -> 0x08)
        //   - RR intervals (bit 4 = 1 -> 0x10)
        //   Total flags = 0x06 | 0x08 | 0x10 = 0x1E
        // HR: 75 BPM (0x4B)
        // Energy: 250 kJ (0x00FA -> low: 0xFA, high: 0x00)
        // RR 1: 840 raw -> round(840 * 1000 / 1024) = 820 ms (0x0348 -> 0x48, 0x03)
        // RR 2: 850 raw -> round(850 * 1000 / 1024) = 830 ms (0x0352 -> 0x52, 0x03)
        val packet = byteArrayOf(
            0x1E,
            0x4B,
            0xFA.toByte(), 0x00,
            0x48, 0x03,
            0x52, 0x03
        )

        val sample = HeartRateParser.parse(packet, fixedTimestamp)

        assertNotNull(sample)
        assertEquals(75, sample!!.bpm)
        assertEquals(true, sample.sensorContactDetected)
        assertEquals(250, sample.energyExpended)
        assertEquals(listOf(820, 830), sample.rrIntervalsMs)
    }

    @Test
    fun parse_uint16WithRrIntervals() {
        // Flags: 0x11 (UINT16 + RR)
        // HR: 260 BPM (0x0104 -> low: 0x04, high: 0x01)
        // RR: 768 raw (0x0300 -> low: 0x00, high: 0x03) -> round(768 * 1000 / 1024) = 750 ms
        val packet = byteArrayOf(
            0x11,
            0x04, 0x01,
            0x00, 0x03
        )

        val sample = HeartRateParser.parse(packet, fixedTimestamp)

        assertNotNull(sample)
        assertEquals(260, sample!!.bpm)
        assertEquals(listOf(750), sample.rrIntervalsMs)
    }

    @Test
    fun parse_nullOrEmptyPacket_returnsNull() {
        assertNull(HeartRateParser.parse(null))
        assertNull(HeartRateParser.parse(byteArrayOf()))
    }

    @Test
    fun parse_truncatedUint8_returnsNull() {
        // Flags only, missing BPM byte
        val packet = byteArrayOf(0x00)
        assertNull(HeartRateParser.parse(packet))
    }

    @Test
    fun parse_truncatedUint16_returnsNull() {
        // Flags 0x01 indicates 2-byte HR, but only 1 byte provided
        val packet = byteArrayOf(0x01, 0x48)
        assertNull(HeartRateParser.parse(packet))
    }

    @Test
    fun parse_truncatedEnergyExpended_returnsNull() {
        // Flags 0x08 indicates energy present, but only 1 byte after HR provided
        val packet = byteArrayOf(0x08, 0x48, 0x0A)
        assertNull(HeartRateParser.parse(packet))
    }

    @Test
    fun parse_trailingOddByteInRrInterval_gracefullyIgnoresTrailingByte() {
        // Valid RR interval followed by incomplete half-word (1 byte)
        val packet = byteArrayOf(
            0x10,
            0x48,
            0x20, 0x03, // 800 raw -> 781 ms
            0xFF.toByte() // Incomplete byte
        )

        val sample = HeartRateParser.parse(packet, fixedTimestamp)

        assertNotNull(sample)
        assertEquals(781, sample!!.rrIntervalsMs.single())
    }

    @Test
    fun isUint16Format_detectsFormatCorrectly() {
        assertFalse(HeartRateParser.isUint16Format(byteArrayOf(0x00, 0x48)))
        assertTrue(HeartRateParser.isUint16Format(byteArrayOf(0x01, 0x48, 0x00)))
        assertFalse(HeartRateParser.isUint16Format(null))
        assertFalse(HeartRateParser.isUint16Format(byteArrayOf()))
    }
}
