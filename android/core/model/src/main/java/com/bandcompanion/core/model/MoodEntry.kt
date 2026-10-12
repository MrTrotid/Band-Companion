package com.bandcompanion.core.model

/**
 * Domain model representing an emotional wellbeing / mood self check-in.
 */
data class MoodEntry(
    val id: Long = 0,
    val deviceId: String,
    val timestamp: Long,
    val moodType: String, // "CALM", "ENERGIZED", "FOCUSED", "STRESSED", "FATIGUED", "ANXIOUS"
    val energyLevel: Int, // 1 to 5
    val note: String? = null
)
