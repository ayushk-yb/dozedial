package com.dozedial.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Main Room database instance for DozeDial sleep sessions and heart rate telemetry.
 */
@Database(
    entities = [
        SleepSessionEntity::class,
        HeartRateRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DozeDialDatabase : RoomDatabase() {

    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun heartRateRecordDao(): HeartRateRecordDao

    companion object {
        @Volatile
        private var INSTANCE: DozeDialDatabase? = null

        fun getInstance(context: Context): DozeDialDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DozeDialDatabase::class.java,
                    "dozedial_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
