package com.bandcompanion.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.bandcompanion.core.model.SleepStageInterval
import com.bandcompanion.core.model.SleepStageType

/**
 * Hypnogram stage interval linked to a parent [SleepSessionEntity].
 */
@Entity(
    tableName = "sleep_stage_interval",
    foreignKeys = [
        ForeignKey(
            entity = SleepSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["start_timestamp"])
    ]
)
data class SleepStageIntervalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "stage_code")
    val stageCode: Int, // 1=Light, 2=REM, 3=Deep, 4=Awake, 5=Nap
    @ColumnInfo(name = "start_timestamp")
    val startTimestamp: Long,
    @ColumnInfo(name = "end_timestamp")
    val endTimestamp: Long,
    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int
) {
    fun toDomainModel(): SleepStageInterval = SleepStageInterval(
        stage = SleepStageType.fromRawCode(stageCode),
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        durationSeconds = durationSeconds
    )

    companion object {
        fun fromDomainModel(sessionId: String, model: SleepStageInterval): SleepStageIntervalEntity =
            SleepStageIntervalEntity(
                sessionId = sessionId,
                stageCode = model.stage.rawCode,
                startTimestamp = model.startTimestamp,
                endTimestamp = model.endTimestamp,
                durationSeconds = model.durationSeconds
            )
    }
}
