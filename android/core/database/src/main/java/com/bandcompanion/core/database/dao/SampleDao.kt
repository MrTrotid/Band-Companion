package com.bandcompanion.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bandcompanion.core.database.entity.SampleMetricEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRawSamples(samples: List<SampleMetricEntity>): List<Long>

    @Update
    suspend fun updateSample(sample: SampleMetricEntity)

    @Query("SELECT * FROM sample_metric WHERE device_id = :deviceId AND timestamp = :timestamp LIMIT 1")
    suspend fun getSample(deviceId: String, timestamp: Long): SampleMetricEntity?

    @Transaction
    suspend fun upsertMergedSamples(samples: List<SampleMetricEntity>) {
        for (sample in samples) {
            val existing = getSample(sample.deviceId, sample.timestamp)
            if (existing == null) {
                insertRawSamples(listOf(sample))
            } else {
                val merged = existing.copy(
                    steps = if (sample.steps > 0) sample.steps else existing.steps,
                    heartRate = sample.heartRate ?: existing.heartRate,
                    spo2Percent = sample.spo2Percent ?: existing.spo2Percent,
                    calories = if (sample.calories > 0) sample.calories else existing.calories,
                    intensity = if (sample.intensity > 0) sample.intensity else existing.intensity,
                    source = if (sample.source.isNotEmpty()) sample.source else existing.source
                )
                updateSample(merged)
            }
        }
    }

    @Query("SELECT * FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getSamplesBetween(deviceId: String, startTime: Long, endTime: Long): Flow<List<SampleMetricEntity>>

    @Query("SELECT * FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getSamplesBetweenSync(deviceId: String, startTime: Long, endTime: Long): List<SampleMetricEntity>

    @Query("SELECT * FROM sample_metric WHERE device_id = :deviceId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSample(deviceId: String): SampleMetricEntity?

    @Query("SELECT AVG(heart_rate) FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime AND heart_rate >= 48 AND heart_rate <= 220")
    suspend fun getAverageHeartRateBetween(deviceId: String, startTime: Long, endTime: Long): Float?

    @Query("SELECT MIN(heart_rate) FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime AND heart_rate >= 48 AND heart_rate <= 220")
    suspend fun getMinHeartRateBetween(deviceId: String, startTime: Long, endTime: Long): Int?

    @Query("SELECT MAX(heart_rate) FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime AND heart_rate >= 48 AND heart_rate <= 220")
    suspend fun getMaxHeartRateBetween(deviceId: String, startTime: Long, endTime: Long): Int?

    @Query("UPDATE sample_metric SET heart_rate = NULL WHERE heart_rate < 48 OR heart_rate > 220")
    suspend fun purgeInvalidHeartRates(): Int

    @Query("SELECT SUM(steps) FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTotalStepsBetween(deviceId: String, startTime: Long, endTime: Long): Int?

    @Query("SELECT AVG(spo2_percent) FROM sample_metric WHERE device_id = :deviceId AND timestamp BETWEEN :startTime AND :endTime AND spo2_percent > 70")
    suspend fun getAverageSpo2Between(deviceId: String, startTime: Long, endTime: Long): Float?

    @Query("DELETE FROM sample_metric WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteSamplesBefore(cutoffTimestamp: Long): Int
}
