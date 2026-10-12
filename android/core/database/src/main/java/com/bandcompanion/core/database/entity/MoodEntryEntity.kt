package com.bandcompanion.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bandcompanion.core.model.MoodEntry

@Entity(tableName = "mood_entries")
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "mood_type")
    val moodType: String,
    @ColumnInfo(name = "energy_level")
    val energyLevel: Int,
    @ColumnInfo(name = "note")
    val note: String? = null
) {
    fun toDomainModel(): MoodEntry = MoodEntry(
        id = id,
        deviceId = deviceId,
        timestamp = timestamp,
        moodType = moodType,
        energyLevel = energyLevel,
        note = note
    )

    companion object {
        fun fromDomainModel(model: MoodEntry): MoodEntryEntity = MoodEntryEntity(
            id = model.id,
            deviceId = model.deviceId,
            timestamp = model.timestamp,
            moodType = model.moodType,
            energyLevel = model.energyLevel,
            note = model.note
        )
    }
}
