package com.bandcompanion.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.bandcompanion.core.database.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WeightEntryEntity): Long

    @Query("SELECT * FROM weight_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries ORDER BY timestamp DESC LIMIT 1")
    fun getLatestEntry(): Flow<WeightEntryEntity?>

    @Query("SELECT * FROM weight_entries WHERE timestamp >= :sinceTimestamp ORDER BY timestamp ASC")
    fun getEntriesSince(sinceTimestamp: Long): Flow<List<WeightEntryEntity>>

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
