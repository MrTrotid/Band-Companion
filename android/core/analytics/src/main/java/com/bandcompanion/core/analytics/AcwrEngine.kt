package com.bandcompanion.core.analytics

import kotlin.math.roundToInt

/**
 * Acute-to-Chronic Workload Ratio (ACWR) calculator using Exponentially Weighted Moving Averages (EWMA).
 *
 * Acute Load: 7-day EWMA (alpha = 2 / (7 + 1) = 0.25)
 * Chronic Load: 42-day EWMA (alpha = 2 / (42 + 1) = 0.0465)
 * ACWR = Acute Load / Chronic Load
 * Optimal training zone: 0.80 to 1.30.
 */
object AcwrEngine {

    enum class WorkloadStatus(val label: String, val description: String) {
        CALIBRATING("Calibrating Baseline", "Building initial 14-day chronic baseline to prevent false workload spikes."),
        UNDER_TRAINING("Under-training", "Workload is significantly below chronic capacity (< 0.80)."),
        OPTIMAL("Optimal Zone", "Workload is in the sweet spot for fitness gains and low injury risk (0.80–1.30)."),
        ELEVATED("Elevated Load", "Workload is increasing faster than baseline adaptation (1.30–1.50)."),
        HIGH_INJURY_RISK("Overreaching / High Risk", "Rapid spike in workload relative to chronic tolerance (> 1.50).")
    }

    data class AcwrResult(
        val acuteLoad: Float,
        val chronicLoad: Float,
        val ratio: Float,
        val status: WorkloadStatus,
        val isCalibrating: Boolean = false,
        val calibrationDays: Int = 14
    )

    /**
     * Calculates ACWR given chronological daily strain / load values (newest last).
     * Enforces a 14-day minimum calibration guard to eliminate Day-1 cold start spikes (MATH-01).
     *
     * @param historicalDailyStrains Ordered list of daily strains ending with today's strain.
     */
    fun calculateAcwr(historicalDailyStrains: List<Float>): AcwrResult? {
        if (historicalDailyStrains.isEmpty()) return null

        val alphaAcute = 2.0f / (7.0f + 1.0f)   // 0.25
        val alphaChronic = 2.0f / (42.0f + 1.0f) // ~0.0465

        // Seed with mean of first valid days rather than uncalibrated 0.0
        val seed = historicalDailyStrains.take(7).average().toFloat().coerceAtLeast(1.0f)
        var acuteEwma = seed
        var chronicEwma = seed

        for (strain in historicalDailyStrains) {
            val safeStrain = strain.coerceAtLeast(0.0f)
            acuteEwma = (safeStrain * alphaAcute) + (acuteEwma * (1.0f - alphaAcute))
            chronicEwma = (safeStrain * alphaChronic) + (chronicEwma * (1.0f - alphaChronic))
        }

        val ratio = if (chronicEwma > 0.1f) (acuteEwma / chronicEwma).coerceIn(0.0f, 3.0f) else 1.0f

        val isCalibrating = historicalDailyStrains.size < 14 || chronicEwma < 1.0f
        val status = when {
            isCalibrating -> WorkloadStatus.CALIBRATING
            ratio < 0.80f -> WorkloadStatus.UNDER_TRAINING
            ratio <= 1.30f -> WorkloadStatus.OPTIMAL
            ratio <= 1.50f -> WorkloadStatus.ELEVATED
            else -> WorkloadStatus.HIGH_INJURY_RISK
        }

        return AcwrResult(
            acuteLoad = (acuteEwma * 10f).roundToInt() / 10f,
            chronicLoad = (chronicEwma * 10f).roundToInt() / 10f,
            ratio = (ratio * 100f).roundToInt() / 100f,
            status = status,
            isCalibrating = isCalibrating,
            calibrationDays = historicalDailyStrains.size
        )
    }
}
