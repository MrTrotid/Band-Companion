package com.bandcompanion.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bandcompanion.core.database.dao.DeviceDao
import com.bandcompanion.core.database.dao.MoodEntryDao
import com.bandcompanion.core.database.dao.SampleDao
import com.bandcompanion.core.database.dao.SleepDao
import com.bandcompanion.core.database.dao.SummaryDao
import com.bandcompanion.core.database.dao.WeightEntryDao
import com.bandcompanion.core.database.entity.DailySummaryEntity
import com.bandcompanion.core.database.entity.DeviceEntity
import com.bandcompanion.core.database.entity.MoodEntryEntity
import com.bandcompanion.core.database.entity.SampleMetricEntity
import com.bandcompanion.core.database.entity.SleepSessionEntity
import com.bandcompanion.core.database.entity.SleepStageIntervalEntity
import com.bandcompanion.core.database.entity.WeightEntryEntity

/**
 * Main local-first SQLite database for Band Companion.
 * Holds fine-grained minute telemetry, TruSleep 700013 sequences, daily summaries, device credentials,
 * weight logs, and mood entries.
 */
@Database(
    entities = [
        SampleMetricEntity::class,
        SleepSessionEntity::class,
        SleepStageIntervalEntity::class,
        DailySummaryEntity::class,
        DeviceEntity::class,
        WeightEntryEntity::class,
        MoodEntryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sampleDao(): SampleDao
    abstract fun sleepDao(): SleepDao
    abstract fun summaryDao(): SummaryDao
    abstract fun deviceDao(): DeviceDao
    abstract fun weightDao(): WeightEntryDao
    abstract fun moodDao(): MoodEntryDao

    companion object {
        private const val DATABASE_NAME = "band_companion.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
