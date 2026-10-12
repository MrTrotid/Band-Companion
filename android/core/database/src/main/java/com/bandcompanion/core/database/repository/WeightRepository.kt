package com.bandcompanion.core.database.repository

import com.bandcompanion.core.database.dao.WeightEntryDao
import com.bandcompanion.core.database.entity.WeightEntryEntity
import com.bandcompanion.core.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WeightRepository(private val weightDao: WeightEntryDao) {

    suspend fun logWeight(weightKg: Float, userHeightCm: Float = 175.0f, note: String? = null, timestamp: Long = System.currentTimeMillis()): Long {
        val entry = WeightEntry(
            timestamp = timestamp,
            weightKg = weightKg,
            userHeightCm = userHeightCm,
            note = note
        )
        return weightDao.insert(WeightEntryEntity.fromDomainModel(entry))
    }

    fun getAllEntries(): Flow<List<WeightEntry>> {
        return weightDao.getAllEntries().map { list -> list.map { it.toDomainModel() } }
    }

    fun getLatestEntry(): Flow<WeightEntry?> {
        return weightDao.getLatestEntry().map { it?.toDomainModel() }
    }

    fun getEntriesSince(sinceTimestamp: Long): Flow<List<WeightEntry>> {
        return weightDao.getEntriesSince(sinceTimestamp).map { list -> list.map { it.toDomainModel() } }
    }

    suspend fun deleteEntry(id: Long) {
        weightDao.deleteById(id)
    }
}
