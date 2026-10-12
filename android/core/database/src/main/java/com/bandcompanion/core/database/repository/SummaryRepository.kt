package com.bandcompanion.core.database.repository

import com.bandcompanion.core.database.dao.SummaryDao
import com.bandcompanion.core.database.entity.DailySummaryEntity
import com.bandcompanion.core.model.DailyRollup
import com.bandcompanion.core.model.DailySummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SummaryRepository(private val summaryDao: SummaryDao) {
    suspend fun saveSummary(deviceId: String, summary: DailySummary) {
        val entity = DailySummaryEntity.fromDomainModel(deviceId, summary)
        summaryDao.insertSummary(entity)
    }

    suspend fun saveSummaries(deviceId: String, summaries: List<DailySummary>) {
        val entities = summaries.map { DailySummaryEntity.fromDomainModel(deviceId, it) }
        summaryDao.insertSummaries(entities)
    }

    fun getSummaryForDate(deviceId: String, date: String): Flow<DailySummary?> {
        return summaryDao.getSummaryForDate(deviceId, date).map { it?.toDomainModel() }
    }

    fun getDailyRollupForDate(deviceId: String, date: String): Flow<DailyRollup?> {
        return summaryDao.getSummaryForDate(deviceId, date).map { entity ->
            entity?.let {
                DailyRollup(
                    deviceId = it.deviceId,
                    date = it.date,
                    stepCount = it.totalSteps,
                    activeMinutes = it.activeMinutes,
                    caloriesBurned = it.caloriesBurned,
                    standHours = it.standHours,
                    stressScore = it.stressScore,
                    avgHrv = it.avgHrv,
                    avgSpo2 = it.avgSpo2
                )
            }
        }
    }

    suspend fun getSummaryForDateSync(deviceId: String, date: String): DailySummary? {
        return summaryDao.getSummaryForDateSync(deviceId, date)?.toDomainModel()
    }

    fun getRecentSummaries(deviceId: String, limit: Int = 30): Flow<List<DailySummary>> {
        return summaryDao.getRecentSummaries(deviceId, limit).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    suspend fun getRecentSummariesSync(deviceId: String, limit: Int = 30): List<DailySummary> {
        return summaryDao.getRecentSummariesSync(deviceId, limit).map { it.toDomainModel() }
    }

    suspend fun getRollingRestingHeartRateBaseline(deviceId: String, sinceDate: String): Float? {
        return summaryDao.getRollingRestingHeartRateBaseline(deviceId, sinceDate)
    }
}
