package com.bandcompanion.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bandcompanion.core.database.entity.MoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MoodEntryEntity): Long

    @Query("SELECT * FROM mood_entries WHERE device_id = :deviceId ORDER BY timestamp DESC")
    fun getMoodEntries(deviceId: String): Flow<List<MoodEntryEntity>>

    @Query("SELECT * FROM mood_entries WHERE device_id = :deviceId ORDER BY timestamp DESC LIMIT 1")
    fun getLatestMood(deviceId: String): Flow<MoodEntryEntity?>

    @Query("SELECT * FROM mood_entries WHERE device_id = :deviceId AND timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getMoodEntriesSince(deviceId: String, sinceTimestamp: Long): Flow<List<MoodEntryEntity>>

    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
