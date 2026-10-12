package com.bandcompanion.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bandcompanion.core.database.entity.SampleMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<SampleMetricEntity>)

    @Query("SELECT * FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getSamplesBetween(deviceId: String, startTime: Long, endTime: Long): Flow<List<SampleMetricEntity>>

    @Query("SELECT * FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getSamplesBetweenSync(deviceId: String, startTime: Long, endTime: Long): List<SampleMetricEntity>

    @Query("SELECT * FROM sample_metric WHERE device_id = :deviceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSample(deviceId: String): SampleMetricEntity?

    @Query("SELECT AVG(heart_rate) FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime AND heart_rate > 35 AND heart_rate < 230")
    suspend fun getAverageHeartRateBetween(deviceId: String, startTime: Long, endTime: Long): Float?

    @Query("SELECT MIN(heart_rate) FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime AND heart_rate > 35")
    suspend fun getMinHeartRateBetween(deviceId: String, startTime: Long, endTime: Long): Int?

    @Query("SELECT MAX(heart_rate) FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime AND heart_rate < 230")
    suspend fun getMaxHeartRateBetween(deviceId: String, startTime: Long, endTime: Long): Int?

    @Query("SELECT SUM(steps) FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTotalStepsBetween(deviceId: String, startTime: Long, endTime: Long): Int?

    @Query("SELECT AVG(spo2_percent) FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime AND spo2_percent > 70")
    suspend fun getAverageSpo2Between(deviceId: String, startTime: Long, endTime: Long): Float?

    @Query("DELETE FROM sample_metric WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteSamplesBefore(cutoffTimestamp: Long): Int
}
