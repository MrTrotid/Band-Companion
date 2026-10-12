package com.bandcompanion.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.bandcompanion.core.model.WeightEntry

@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "weight_kg")
    val weightKg: Float,
    @ColumnInfo(name = "user_height_cm")
    val userHeightCm: Float = 175.0f,
    @ColumnInfo(name = "note")
    val note: String? = null
) {
    fun toDomainModel(): WeightEntry = WeightEntry(
        id = id,
        timestamp = timestamp,
        weightKg = weightKg,
        userHeightCm = userHeightCm,
        note = note
    )

    companion object {
        fun fromDomainModel(model: WeightEntry): WeightEntryEntity = WeightEntryEntity(
            id = model.id,
            timestamp = model.timestamp,
            weightKg = model.weightKg,
            userHeightCm = model.userHeightCm,
            note = model.note
        )
    }
}
