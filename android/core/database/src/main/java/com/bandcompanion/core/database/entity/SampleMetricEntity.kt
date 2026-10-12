package com.bandcompanion.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bandcompanion.core.model.SampleMetric

/**
 * Minute-by-minute measurement entity stored in SQLite.
 */
@Entity(
    tableName = "sample_metric",
    indices = [
        Index(value = ["device_id", "timestamp"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class SampleMetricEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long, // Epoch seconds at minute boundary
    @ColumnInfo(name = "steps")
    val steps: Int = 0,
    @ColumnInfo(name = "heart_rate")
    val heartRate: Int? = null,
    @ColumnInfo(name = "spo2_percent")
    val spo2Percent: Float? = null,
    @ColumnInfo(name = "intensity")
    val intensity: Int = 0,
    @ColumnInfo(name = "calories")
    val calories: Int = 0,
    @ColumnInfo(name = "source")
    val source: String = "LPV2_FITNESS"
) {
    fun toDomainModel(): SampleMetric = SampleMetric(
        timestamp = timestamp,
        steps = steps,
        heartRate = heartRate,
        spo2 = spo2Percent,
        intensity = intensity,
        calories = calories,
        source = source
    )

    companion object {
        fun fromDomainModel(deviceId: String, model: SampleMetric): SampleMetricEntity =
            SampleMetricEntity(
                deviceId = deviceId,
                timestamp = model.timestamp,
                steps = model.steps,
                heartRate = model.heartRate,
                spo2Percent = model.spo2,
                intensity = model.intensity,
                calories = model.calories,
                source = model.source
            )
    }
}
