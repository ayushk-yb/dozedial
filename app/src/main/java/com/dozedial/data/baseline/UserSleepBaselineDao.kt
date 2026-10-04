package com.dozedial.data.baseline

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSleepBaselineDao {

    @Query("SELECT * FROM user_sleep_baseline WHERE id = 1 LIMIT 1")
    fun getBaseline(): Flow<UserSleepBaselineEntity?>

    @Query("SELECT * FROM user_sleep_baseline WHERE id = 1 LIMIT 1")
    suspend fun getBaselineSync(): UserSleepBaselineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(baseline: UserSleepBaselineEntity)

    @Query("DELETE FROM user_sleep_baseline")
    suspend fun clearBaseline()
}
