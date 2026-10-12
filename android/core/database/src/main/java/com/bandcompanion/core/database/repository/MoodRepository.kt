package com.bandcompanion.core.database.repository

import com.bandcompanion.core.database.dao.MoodEntryDao
import com.bandcompanion.core.database.entity.MoodEntryEntity
import com.bandcompanion.core.model.MoodEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MoodRepository(private val moodDao: MoodEntryDao) {

    suspend fun logMood(
        deviceId: String,
        moodType: String,
        energyLevel: Int,
        note: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        val entry = MoodEntry(
            deviceId = deviceId,
            timestamp = timestamp,
            moodType = moodType,
            energyLevel = energyLevel,
            note = note
        )
        return moodDao.insert(MoodEntryEntity.fromDomainModel(entry))
    }

    fun getMoodEntries(deviceId: String): Flow<List<MoodEntry>> {
        return moodDao.getMoodEntries(deviceId).map { list -> list.map { it.toDomainModel() } }
    }

    fun getLatestMood(deviceId: String): Flow<MoodEntry?> {
        return moodDao.getLatestMood(deviceId).map { it?.toDomainModel() }
    }

    fun getMoodEntriesSince(deviceId: String, sinceTimestamp: Long): Flow<List<MoodEntry>> {
        return moodDao.getMoodEntriesSince(deviceId, sinceTimestamp).map { list -> list.map { it.toDomainModel() } }
    }

    suspend fun deleteMood(id: Long) {
        moodDao.deleteById(id)
    }
}
