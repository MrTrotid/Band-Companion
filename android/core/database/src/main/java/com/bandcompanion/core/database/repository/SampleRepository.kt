package com.bandcompanion.core.database.repository

import com.bandcompanion.core.database.dao.SampleDao
import com.bandcompanion.core.database.entity.SampleMetricEntity
import com.bandcompanion.core.model.SampleMetric
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SampleRepository(private val sampleDao: SampleDao) {
    suspend fun saveSamples(deviceId: String, samples: List<SampleMetric>) {
        if (samples.isEmpty()) return
        val entities = samples.map { SampleMetricEntity.fromDomainModel(deviceId, it) }
        sampleDao.upsertMergedSamples(entities)
    }

    fun getSamplesBetween(deviceId: String, startTime: Long, endTime: Long): Flow<List<SampleMetric>> {
        return sampleDao.getSamplesBetween(deviceId, startTime, endTime).map { list ->
            list.map { it.toDomainModel() }
        }
    }

    suspend fun getSamplesBetweenSync(deviceId: String, startTime: Long, endTime: Long): List<SampleMetric> {
        return sampleDao.getSamplesBetweenSync(deviceId, startTime, endTime).map { it.toDomainModel() }
    }

    suspend fun getLatestSample(deviceId: String): SampleMetric? {
        return sampleDao.getLatestSample(deviceId)?.toDomainModel()
    }

    suspend fun getAverageHeartRateBetween(deviceId: String, startTime: Long, endTime: Long): Float? {
        return sampleDao.getAverageHeartRateBetween(deviceId, startTime, endTime)
    }

    suspend fun getTotalStepsBetween(deviceId: String, startTime: Long, endTime: Long): Int {
        return sampleDao.getTotalStepsBetween(deviceId, startTime, endTime) ?: 0
    }

    suspend fun purgeInvalidHeartRates(): Int {
        return sampleDao.purgeInvalidHeartRates()
    }
}
