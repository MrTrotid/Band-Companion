package com.bandcompanion.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.bandcompanion.core.model.DailySummary

/**
 * Pre-aggregated daily rollup for instant dashboard rendering.
 */
@Entity(
    tableName = "daily_summary",
    primaryKeys = ["date", "device_id"]
)
data class DailySummaryEntity(
    @ColumnInfo(name = "date")
    val date: String, // YYYY-MM-DD
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "total_steps")
    val totalSteps: Int = 0,
    @ColumnInfo(name = "resting_heart_rate")
    val restingHeartRate: Int? = null,
    @ColumnInfo(name = "min_heart_rate")
    val minHeartRate: Int? = null,
    @ColumnInfo(name = "max_heart_rate")
    val maxHeartRate: Int? = null,
    @ColumnInfo(name = "avg_spo2")
    val avgSpo2: Float? = null,
    @ColumnInfo(name = "total_sleep_minutes")
    val totalSleepMinutes: Int = 0,
    @ColumnInfo(name = "deep_sleep_minutes")
    val deepSleepMinutes: Int = 0,
    @ColumnInfo(name = "rem_sleep_minutes")
    val remSleepMinutes: Int = 0,
    @ColumnInfo(name = "light_sleep_minutes")
    val lightSleepMinutes: Int = 0,
    @ColumnInfo(name = "awake_minutes")
    val awakeMinutes: Int = 0,
    @ColumnInfo(name = "recovery_score")
    val recoveryScore: Int? = null,
    @ColumnInfo(name = "day_strain")
    val dayStrain: Float? = null,
    @ColumnInfo(name = "acute_load")
    val acuteLoad: Float? = null,
    @ColumnInfo(name = "chronic_load")
    val chronicLoad: Float? = null,
    @ColumnInfo(name = "acwr_ratio")
    val acwrRatio: Float? = null
) {
    fun toDomainModel(): DailySummary = DailySummary(
        date = date,
        totalSteps = totalSteps,
        restingHeartRate = restingHeartRate,
        minHeartRate = minHeartRate,
        maxHeartRate = maxHeartRate,
        avgSpo2 = avgSpo2,
        totalSleepMinutes = totalSleepMinutes,
        deepSleepMinutes = deepSleepMinutes,
        remSleepMinutes = remSleepMinutes,
        lightSleepMinutes = lightSleepMinutes,
        awakeMinutes = awakeMinutes,
        recoveryScore = recoveryScore,
        dayStrain = dayStrain,
        acuteLoad = acuteLoad,
        chronicLoad = chronicLoad,
        acwrRatio = acwrRatio
    )

    companion object {
        fun fromDomainModel(deviceId: String, model: DailySummary): DailySummaryEntity =
            DailySummaryEntity(
                date = model.date,
                deviceId = deviceId,
                totalSteps = model.totalSteps,
                restingHeartRate = model.restingHeartRate,
                minHeartRate = model.minHeartRate,
                maxHeartRate = model.maxHeartRate,
                avgSpo2 = model.avgSpo2,
                totalSleepMinutes = model.totalSleepMinutes,
                deepSleepMinutes = model.deepSleepMinutes,
                remSleepMinutes = model.remSleepMinutes,
                lightSleepMinutes = model.lightSleepMinutes,
                awakeMinutes = model.awakeMinutes,
                recoveryScore = model.recoveryScore,
                dayStrain = model.dayStrain,
                acuteLoad = model.acuteLoad,
                chronicLoad = model.chronicLoad,
                acwrRatio = model.acwrRatio
            )
    }
}
