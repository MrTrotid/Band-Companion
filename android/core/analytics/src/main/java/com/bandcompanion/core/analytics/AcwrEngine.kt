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
        UNDER_TRAINING("Under-training", "Workload is significantly below chronic capacity (< 0.80)."),
        OPTIMAL("Optimal Zone", "Workload is in the sweet spot for fitness gains and low injury risk (0.80–1.30)."),
        ELEVATED("Elevated Load", "Workload is increasing faster than baseline adaptation (1.30–1.50)."),
        HIGH_INJURY_RISK("Overreaching / High Risk", "Rapid spike in workload relative to chronic tolerance (> 1.50).")
    }

    data class AcwrResult(
        val acuteLoad: Float,
        val chronicLoad: Float,
        val ratio: Float,
        val status: WorkloadStatus
    )

    /**
     * Calculates ACWR given chronological daily strain / load values (newest last).
     *
     * @param historicalDailyStrains Ordered list of daily strains ending with today's strain.
     */
    fun calculateAcwr(historicalDailyStrains: List<Float>): AcwrResult? {
        if (historicalDailyStrains.isEmpty()) return null

        val alphaAcute = 2.0f / (7.0f + 1.0f)   // 0.25
        val alphaChronic = 2.0f / (42.0f + 1.0f) // ~0.0465

        var acuteEwma = historicalDailyStrains.first()
        var chronicEwma = historicalDailyStrains.first()

        for (strain in historicalDailyStrains) {
            acuteEwma = (strain * alphaAcute) + (acuteEwma * (1.0f - alphaAcute))
            chronicEwma = (strain * alphaChronic) + (chronicEwma * (1.0f - alphaChronic))
        }

        val ratio = if (chronicEwma > 0.1f) acuteEwma / chronicEwma else 1.0f

        val status = when {
            ratio < 0.80f -> WorkloadStatus.UNDER_TRAINING
            ratio <= 1.30f -> WorkloadStatus.OPTIMAL
            ratio <= 1.50f -> WorkloadStatus.ELEVATED
            else -> WorkloadStatus.HIGH_INJURY_RISK
        }

        return AcwrResult(
            acuteLoad = (acuteEwma * 10f).roundToInt() / 10f,
            chronicLoad = (chronicEwma * 10f).roundToInt() / 10f,
            ratio = (ratio * 100f).roundToInt() / 100f,
            status = status
        )
    }
}
