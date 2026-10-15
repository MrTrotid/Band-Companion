package com.bandcompanion.core.database.repository

import com.bandcompanion.core.database.dao.SleepDao
import com.bandcompanion.core.database.entity.SleepSessionEntity
import com.bandcompanion.core.database.entity.SleepStageIntervalEntity
import com.bandcompanion.core.model.SleepSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class SleepRepository(private val sleepDao: SleepDao) {
    suspend fun saveSession(deviceId: String, session: SleepSession) {
        val entity = SleepSessionEntity.fromDomainModel(deviceId, session)
        val stageEntities = session.stages.map {
            SleepStageIntervalEntity.fromDomainModel(session.sessionId, it)
        }
        sleepDao.insertSessionWithStages(entity, stageEntities)
    }

    suspend fun saveSessions(deviceId: String, sessions: List<SleepSession>) {
        for (session in sessions) {
            saveSession(deviceId, session)
        }
    }

    fun getLatestSleepSession(deviceId: String): Flow<SleepSession?> {
        return sleepDao.getLatestSleepSession(deviceId).flatMapLatest { entity ->
            if (entity == null) {
                flowOf(null)
            } else {
                sleepDao.getStagesForSession(entity.sessionId).map { stageEntities ->
                    entity.toDomainModel().copy(stages = stageEntities.map { it.toDomainModel() })
                }
            }
        }
    }

    fun getLatestMajorSleepSession(deviceId: String): Flow<SleepSession?> {
        return sleepDao.getLatestMajorSleepSession(deviceId).flatMapLatest { entity ->
            if (entity == null) {
                flowOf(null)
            } else {
                sleepDao.getStagesForSession(entity.sessionId).map { stageEntities ->
                    entity.toDomainModel().copy(stages = stageEntities.map { it.toDomainModel() })
                }
            }
        }
    }

    suspend fun getLatestSleepSessionWithStages(deviceId: String): SleepSession? {
        val entity = sleepDao.getLatestSleepSessionSync(deviceId) ?: return null
        val stages = sleepDao.getStagesForSessionSync(entity.sessionId).map { it.toDomainModel() }
        return entity.toDomainModel().copy(stages = stages)
    }

    fun getSleepSessionsBetween(deviceId: String, startTime: Long, endTime: Long): Flow<List<SleepSession>> {
        return sleepDao.getSleepSessionsBetween(deviceId, startTime, endTime).map { list ->
            list.map { it.toDomainModel() }
        }
    }
}
