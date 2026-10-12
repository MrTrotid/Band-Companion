package com.bandcompanion.core.model

/**
 * 4-Ring Activity & Wellbeing daily rollup model.
 */
data class DailyRollup(
    val deviceId: String,
    val date: String, // Format: YYYY-MM-DD
    val stepCount: Int = 0,
    val activeMinutes: Int = 0,
    val caloriesBurned: Int = 0,
    val standHours: Int = 0,
    val stressScore: Int? = null,
    val avgHrv: Float? = null,
    val avgSpo2: Float? = null
) {
    fun stepProgress(target: Int = 10000): Float =
        if (target > 0) stepCount.toFloat() / target else 0f

    fun activeMinutesProgress(target: Int = 30): Float =
        if (target > 0) activeMinutes.toFloat() / target else 0f

    fun calorieProgress(target: Int = 500): Float =
        if (target > 0) caloriesBurned.toFloat() / target else 0f

    fun standHoursProgress(target: Int = 12): Float =
        if (target > 0) standHours.toFloat() / target else 0f
}
