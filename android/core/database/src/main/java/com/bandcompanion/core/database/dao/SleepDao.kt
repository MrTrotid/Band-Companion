package com.bandcompanion.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.bandcompanion.core.database.entity.SleepSessionEntity
import com.bandcompanion.core.database.entity.SleepStageIntervalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepSession(session: SleepSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepSessions(sessions: List<SleepSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleepStages(stages: List<SleepStageIntervalEntity>)

    @Query("SELECT * FROM sleep_session WHERE device_id = :deviceId AND start_timestamp BETWEEN :startTime AND :endTime ORDER BY start_timestamp DESC")
    fun getSleepSessionsBetween(deviceId: String, startTime: Long, endTime: Long): Flow<List<SleepSessionEntity>>

    @Query("SELECT * FROM sleep_session WHERE device_id = :deviceId AND start_timestamp BETWEEN :startTime AND :endTime ORDER BY start_timestamp DESC")
    suspend fun getSleepSessionsBetweenSync(deviceId: String, startTime: Long, endTime: Long): List<SleepSessionEntity>

    @Query("SELECT * FROM sleep_session WHERE device_id = :deviceId ORDER BY start_timestamp DESC LIMIT 1")
    fun getLatestSleepSession(deviceId: String): Flow<SleepSessionEntity?>

    @Query("SELECT * FROM sleep_session WHERE device_id = :deviceId ORDER BY start_timestamp DESC LIMIT 1")
    suspend fun getLatestSleepSessionSync(deviceId: String): SleepSessionEntity?

    @Query("SELECT * FROM sleep_stage_interval WHERE session_id = :sessionId ORDER BY start_timestamp ASC")
    fun getStagesForSession(sessionId: String): Flow<List<SleepStageIntervalEntity>>

    @Query("SELECT * FROM sleep_stage_interval WHERE session_id = :sessionId ORDER BY start_timestamp ASC")
    suspend fun getStagesForSessionSync(sessionId: String): List<SleepStageIntervalEntity>

    @Transaction
    suspend fun insertSessionWithStages(session: SleepSessionEntity, stages: List<SleepStageIntervalEntity>) {
        insertSleepSession(session)
        if (stages.isNotEmpty()) {
            insertSleepStages(stages)
        }
    }
}
