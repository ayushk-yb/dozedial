package com.dozedial.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [SleepSessionEntity].
 */
@Dao
interface SleepSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SleepSessionEntity): Long

    @Update
    suspend fun update(session: SleepSessionEntity)

    @Query("SELECT * FROM sleep_sessions WHERE sessionId = :sessionId")
    suspend fun getSessionById(sessionId: Long): SleepSessionEntity?

    @Query("SELECT * FROM sleep_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_sessions WHERE endTime IS NULL ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveSession(): SleepSessionEntity?

    @Query("DELETE FROM sleep_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)
}
