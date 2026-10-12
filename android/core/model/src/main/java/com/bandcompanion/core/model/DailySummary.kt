package com.bandcompanion.core.model

/**
 * Pre-aggregated daily rollup for instant dashboard rendering.
 *
 * Combines physical measurements with calculated physiological models:
 * Recovery (Readiness 0-100), Cardiovascular Strain (TRIMP 0-21), and ACWR.
 */
data class DailySummary(
    val date: String, // Format: YYYY-MM-DD
    val totalSteps: Int = 0,
    val restingHeartRate: Int? = null,
    val minHeartRate: Int? = null,
    val maxHeartRate: Int? = null,
    val avgSpo2: Float? = null,
    val totalSleepMinutes: Int = 0,
    val deepSleepMinutes: Int = 0,
    val remSleepMinutes: Int = 0,
    val lightSleepMinutes: Int = 0,
    val awakeMinutes: Int = 0,
    val recoveryScore: Int? = null, // 0 - 100
    val dayStrain: Float? = null,    // 0.0 - 21.0
    val acuteLoad: Float? = null,   // 7-day EWMA
    val chronicLoad: Float? = null, // 42-day EWMA
    val acwrRatio: Float? = null    // Acute:Chronic Workload Ratio
) {
    val isOptimalWorkload: Boolean
        get() = acwrRatio != null && acwrRatio in 0.8f..1.3f

    val recoveryLabel: String
        get() = when {
            recoveryScore == null -> "Calculating"
            recoveryScore >= 67 -> "Optimal"
            recoveryScore >= 34 -> "Moderate"
            else -> "Low Recovery"
        }
}
