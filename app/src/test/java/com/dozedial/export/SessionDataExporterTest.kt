package com.dozedial.export

import com.dozedial.data.db.HeartRateRecordEntity
import com.dozedial.data.db.SleepSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDataExporterTest {

    @Test
    fun testExportToCsvString_formatAndHeaders() {
        val session = SleepSessionEntity(
            sessionId = 42L,
            startTime = 1760000000000L,
            endTime = 1760000060000L,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Fitbit Air",
            totalSamples = 2,
            avgBpm = 65.0,
            minBpm = 64,
            maxBpm = 66
        )

        val records = listOf(
            HeartRateRecordEntity(
                id = 1L,
                sessionId = 42L,
                timestampMs = 1760000000000L,
                bpm = 64,
                sensorContact = true,
                energyExpended = 120,
                rawBytesHex = "01 40 00"
            ),
            HeartRateRecordEntity(
                id = 2L,
                sessionId = 42L,
                timestampMs = 1760000005000L,
                bpm = 66,
                sensorContact = null,
                energyExpended = null,
                rawBytesHex = "01 42 00"
            )
        )

        val csv = SessionDataExporter.exportToCsvString(session, records)
        val lines = csv.trim().lines()

        assertEquals(3, lines.size)
        assertEquals("session_id,iso_timestamp,timestamp_epoch_ms,elapsed_seconds,bpm,sensor_contact,raw_hex", lines[0])

        // First record
        val line1 = lines[1]
        assertTrue("Line 1 starts with session id", line1.startsWith("42,"))
        assertTrue("Line 1 contains epoch ms", line1.contains(",1760000000000,"))
        assertTrue("Line 1 elapsed seconds is 0", line1.contains(",0,64,true,01 40 00"))

        // Second record
        val line2 = lines[2]
        assertTrue("Line 2 starts with session id", line2.startsWith("42,"))
        assertTrue("Line 2 contains epoch ms", line2.contains(",1760000005000,"))
        assertTrue("Line 2 elapsed seconds is 5", line2.contains(",5,66,,01 42 00"))
    }

    @Test
    fun testExportToCsvString_emptyRecords() {
        val session = SleepSessionEntity(
            sessionId = 1L,
            startTime = 1760000000000L,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Fitbit Air"
        )

        val csv = SessionDataExporter.exportToCsvString(session, emptyList())
        val lines = csv.trim().lines()

        assertEquals(1, lines.size)
        assertEquals(SessionDataExporter.CSV_HEADER, lines[0])
    }
}
