package com.bandcompanion.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bandcompanion.core.model.SleepSession

/**
 * Overnight sleep or nap session stored in SQLite.
 */
@Entity(
    tableName = "sleep_session",
    indices = [
        Index(value = ["device_id", "start_timestamp"], unique = true),
        Index(value = ["start_timestamp"])
    ]
)
data class SleepSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "start_timestamp")
    val startTimestamp: Long, // Epoch seconds
    @ColumnInfo(name = "end_timestamp")
    val endTimestamp: Long,   // Epoch seconds
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,
    @ColumnInfo(name = "sleep_score")
    val sleepScore: Int? = null,
    @ColumnInfo(name = "avg_hrv")
    val avgHrv: Float? = null,
    @ColumnInfo(name = "min_hrv_baseline")
    val minHrvBaseline: Float? = null,
    @ColumnInfo(name = "max_hrv_baseline")
    val maxHrvBaseline: Float? = null,
    @ColumnInfo(name = "avg_heart_rate")
    val avgHeartRate: Int? = null,
    @ColumnInfo(name = "avg_spo2")
    val avgSpo2: Float? = null,
    @ColumnInfo(name = "avg_breath_rate")
    val avgBreathRate: Float? = null,
    @ColumnInfo(name = "sleep_efficiency")
    val sleepEfficiency: Float? = null,
    @ColumnInfo(name = "latency_minutes")
    val latencyMinutes: Int? = null,
    @ColumnInfo(name = "deep_sleep_minutes")
    val deepSleepMinutes: Int = 0,
    @ColumnInfo(name = "rem_sleep_minutes")
    val remSleepMinutes: Int = 0,
    @ColumnInfo(name = "light_sleep_minutes")
    val lightSleepMinutes: Int = 0,
    @ColumnInfo(name = "awake_minutes")
    val awakeMinutes: Int = 0,
    @ColumnInfo(name = "source")
    val source: String = "HUAWEI_SLEEP_DETAILS"
) {
    fun toDomainModel(): SleepSession = SleepSession(
        sessionId = sessionId,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        durationMinutes = durationMinutes,
        sleepScore = sleepScore,
        avgHrv = avgHrv,
        minHrvBaseline = minHrvBaseline,
        maxHrvBaseline = maxHrvBaseline,
        avgHeartRate = avgHeartRate,
        avgSpo2 = avgSpo2,
        avgBreathRate = avgBreathRate,
        sleepEfficiency = sleepEfficiency,
        latencyMinutes = latencyMinutes,
        deepSleepMinutes = deepSleepMinutes,
        remSleepMinutes = remSleepMinutes,
        lightSleepMinutes = lightSleepMinutes,
        awakeMinutes = awakeMinutes,
        source = source
    )

    companion object {
        fun fromDomainModel(deviceId: String, model: SleepSession): SleepSessionEntity =
            SleepSessionEntity(
                sessionId = model.sessionId,
                deviceId = deviceId,
                startTimestamp = model.startTimestamp,
                endTimestamp = model.endTimestamp,
                durationMinutes = model.durationMinutes,
                sleepScore = model.sleepScore,
                avgHrv = model.avgHrv,
                minHrvBaseline = model.minHrvBaseline,
                maxHrvBaseline = model.maxHrvBaseline,
                avgHeartRate = model.avgHeartRate,
                avgSpo2 = model.avgSpo2,
                avgBreathRate = model.avgBreathRate,
                sleepEfficiency = model.sleepEfficiency,
                latencyMinutes = model.latencyMinutes,
                deepSleepMinutes = model.deepSleepMinutes,
                remSleepMinutes = model.remSleepMinutes,
                lightSleepMinutes = model.lightSleepMinutes,
                awakeMinutes = model.awakeMinutes,
                source = model.source
            )
    }
}
