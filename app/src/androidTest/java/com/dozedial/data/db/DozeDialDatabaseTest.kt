package com.dozedial.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class DozeDialDatabaseTest {

    private lateinit var database: DozeDialDatabase
    private lateinit var sessionDao: SleepSessionDao
    private lateinit var recordDao: HeartRateRecordDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            DozeDialDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        sessionDao = database.sleepSessionDao()
        recordDao = database.heartRateRecordDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testSessionInsertAndActiveSessionQuery() = runBlocking {
        val session = SleepSessionEntity(
            startTime = 1760000000000L,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Fitbit Air"
        )
        val id = sessionDao.insert(session)
        assertTrue(id > 0)

        val active = sessionDao.getActiveSession()
        assertNotNull(active)
        assertEquals(id, active?.sessionId)
        assertNull(active?.endTime)

        // Finalize session
        val finalized = active!!.copy(
            endTime = 1760030000000L,
            totalSamples = 28000,
            avgBpm = 62.4,
            minBpm = 49,
            maxBpm = 86
        )
        sessionDao.update(finalized)

        // Active should now be null
        val activeAfter = sessionDao.getActiveSession()
        assertNull(activeAfter)

        val retrieved = sessionDao.getSessionById(id)
        assertEquals(28000, retrieved?.totalSamples)
        assertEquals(49, retrieved?.minBpm)
        assertEquals(86, retrieved?.maxBpm)
    }

    @Test
    fun testBatchInsertPerformanceAndAggregates() = runBlocking {
        val sessionId = sessionDao.insert(
            SleepSessionEntity(
                startTime = 1760000000000L,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = "Fitbit Air"
            )
        )

        // Generate 5,000 synthetic records simulating overnight collection
        val records = (1..5000).map { i ->
            val bpm = 50 + (i % 40) // BPM ranges 50 to 89
            HeartRateRecordEntity(
                sessionId = sessionId,
                timestampMs = 1760000000000L + (i * 1000L),
                bpm = bpm,
                sensorContact = true,
                energyExpended = i / 10,
                rawBytesHex = "01 32 00"
            )
        }

        val elapsedMs = measureTimeMillis {
            recordDao.insertBatch(records)
        }

        println("Batch insert 5000 records completed in ${elapsedMs}ms")
        assertTrue("Batch insert should be fast (under 2000ms in in-memory SQLite)", elapsedMs < 2000)

        val count = recordDao.getRecordCount(sessionId)
        assertEquals(5000, count)

        val minBpm = recordDao.getMinBpm(sessionId)
        val maxBpm = recordDao.getMaxBpm(sessionId)
        val avgBpm = recordDao.getAvgBpm(sessionId)

        assertEquals(50, minBpm)
        assertEquals(89, maxBpm)
        assertNotNull(avgBpm)
        assertTrue(avgBpm!! in 69.0..70.0)
    }

    @Test
    fun testCascadeDelete() = runBlocking {
        val sessionId = sessionDao.insert(
            SleepSessionEntity(
                startTime = 1760000000000L,
                deviceAddress = "AA:BB:CC:DD:EE:FF",
                deviceName = "Fitbit Air"
            )
        )

        val records = listOf(
            HeartRateRecordEntity(
                sessionId = sessionId,
                timestampMs = 1760000000000L,
                bpm = 60,
                sensorContact = true,
                energyExpended = null,
                rawBytesHex = "01 3C 00"
            ),
            HeartRateRecordEntity(
                sessionId = sessionId,
                timestampMs = 1760000001000L,
                bpm = 61,
                sensorContact = true,
                energyExpended = null,
                rawBytesHex = "01 3D 00"
            )
        )
        recordDao.insertBatch(records)

        assertEquals(2, recordDao.getRecordCount(sessionId))

        // Deleting the session must cascade-delete all its heart rate records
        sessionDao.deleteSessionById(sessionId)

        assertNull(sessionDao.getSessionById(sessionId))
        assertEquals(0, recordDao.getRecordCount(sessionId))
    }
}
