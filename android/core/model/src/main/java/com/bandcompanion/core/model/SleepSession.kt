package com.bandcompanion.core.model

/**
 * Macro sleep session representing an overnight sleep period or nap.
 *
 * Sourced directly from Huawei TruSleep dictionary 700013 (`SLEEP_DETAILS`).
 */
data class SleepSession(
    val sessionId: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationMinutes: Int,
    val sleepScore: Int? = null,
    val avgHrv: Float? = null,
    val minHrvBaseline: Float? = null,
    val maxHrvBaseline: Float? = null,
    val avgHeartRate: Int? = null,
    val avgSpo2: Float? = null,
    val avgBreathRate: Float? = null,
    val sleepEfficiency: Float? = null,
    val latencyMinutes: Int? = null,
    val deepSleepMinutes: Int = 0,
    val remSleepMinutes: Int = 0,
    val lightSleepMinutes: Int = 0,
    val awakeMinutes: Int = 0,
    val stages: List<SleepStageInterval> = emptyList(),
    val source: String = "HUAWEI_SLEEP_DETAILS"
) {
    val totalSleepMinutes: Int
        get() = deepSleepMinutes + remSleepMinutes + lightSleepMinutes

    val deepSleepPercentage: Float
        get() = if (totalSleepMinutes > 0) (deepSleepMinutes.toFloat() / totalSleepMinutes) * 100f else 0f

    val remSleepPercentage: Float
        get() = if (totalSleepMinutes > 0) (remSleepMinutes.toFloat() / totalSleepMinutes) * 100f else 0f
}
