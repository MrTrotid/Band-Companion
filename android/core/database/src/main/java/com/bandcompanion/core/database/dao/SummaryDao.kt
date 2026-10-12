package com.bandcompanion.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bandcompanion.core.database.entity.DailySummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: DailySummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummaries(summaries: List<DailySummaryEntity>)

    @Query("SELECT * FROM daily_summary WHERE device_id = :deviceId AND date = :date LIMIT 1")
    fun getSummaryForDate(deviceId: String, date: String): Flow<DailySummaryEntity?>

    @Query("SELECT * FROM daily_summary WHERE device_id = :deviceId AND date = :date LIMIT 1")
    suspend fun getSummaryForDateSync(deviceId: String, date: String): DailySummaryEntity?

    @Query("SELECT * FROM daily_summary WHERE device_id = :deviceId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getSummariesBetween(deviceId: String, startDate: String, endDate: String): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summary WHERE device_id = :deviceId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getSummariesBetweenSync(deviceId: String, startDate: String, endDate: String): List<DailySummaryEntity>

    @Query("SELECT * FROM daily_summary WHERE device_id = :deviceId ORDER BY date DESC LIMIT :limit")
    fun getRecentSummaries(deviceId: String, limit: Int): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summary WHERE device_id = :deviceId ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentSummariesSync(deviceId: String, limit: Int): List<DailySummaryEntity>

    @Query("SELECT AVG(resting_heart_rate) FROM daily_summary WHERE device_id = :deviceId AND resting_heart_rate IS NOT NULL AND date >= :sinceDate")
    suspend fun getRollingRestingHeartRateBaseline(deviceId: String, sinceDate: String): Float?
}
