package com.dozedial.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for high-resolution [HeartRateRecordEntity] time-series samples.
 */
@Dao
interface HeartRateRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(records: List<HeartRateRecordEntity>)

    @Query("SELECT * FROM heart_rate_records WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    fun getRecordsForSession(sessionId: Long): Flow<List<HeartRateRecordEntity>>

    @Query("SELECT * FROM heart_rate_records WHERE sessionId = :sessionId ORDER BY timestampMs ASC")
    suspend fun getRecordsForSessionSync(sessionId: Long): List<HeartRateRecordEntity>

    @Query("SELECT COUNT(*) FROM heart_rate_records WHERE sessionId = :sessionId")
    suspend fun getRecordCount(sessionId: Long): Int

    @Query("SELECT MIN(bpm) FROM heart_rate_records WHERE sessionId = :sessionId AND bpm > 0")
    suspend fun getMinBpm(sessionId: Long): Int?

    @Query("SELECT MAX(bpm) FROM heart_rate_records WHERE sessionId = :sessionId")
    suspend fun getMaxBpm(sessionId: Long): Int?

    @Query("SELECT AVG(bpm) FROM heart_rate_records WHERE sessionId = :sessionId AND bpm > 0")
    suspend fun getAvgBpm(sessionId: Long): Double?
}
