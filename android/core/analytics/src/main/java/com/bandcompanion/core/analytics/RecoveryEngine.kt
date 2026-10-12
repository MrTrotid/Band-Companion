package com.bandcompanion.core.analytics

import kotlin.math.max
import kotlin.math.min

/**
 * Multi-parameter physiological recovery & readiness scoring model (0–100%).
 *
 * Implements the weighted formula:
 * Recovery = 0.40 * S_HRV + 0.25 * S_RHR + 0.20 * S_Sleep + 0.15 * S_SpO2
 */
object RecoveryEngine {

    data class RecoveryResult(
        val totalScore: Int,
        val hrvScore: Int,
        val rhrScore: Int,
        val sleepScore: Int,
        val spo2Score: Int,
        val label: String
    )

    /**
     * Calculates the readiness score from overnight biomarkers and historical baselines.
     *
     * @param overnightHrv Average nocturnal HRV (ms) from TruSleep 700013.
     * @param minHrvBaseline Lower bound of user's typical HRV baseline (ms).
     * @param maxHrvBaseline Upper bound of user's typical HRV baseline (ms).
     * @param overnightRhr Nocturnal resting heart rate (bpm).
     * @param baselineRhr 30-day rolling average resting heart rate (bpm).
     * @param sleepQualityScore Sleep score reported by TruSleep (0-100).
     * @param overnightSpo2 Average nocturnal SpO2 percentage.
     */
    fun calculateRecovery(
        overnightHrv: Float?,
        minHrvBaseline: Float?,
        maxHrvBaseline: Float?,
        overnightRhr: Float?,
        baselineRhr: Float?,
        sleepQualityScore: Int?,
        overnightSpo2: Float?
    ): RecoveryResult {
        val sHrv = computeHrvScore(overnightHrv, minHrvBaseline, maxHrvBaseline)
        val sRhr = computeRhrScore(overnightRhr, baselineRhr)
        val sSleep = (sleepQualityScore ?: 70).coerceIn(0, 100)
        val sSpo2 = computeSpo2Score(overnightSpo2)

        val total = (0.40f * sHrv + 0.25f * sRhr + 0.20f * sSleep + 0.15f * sSpo2)
            .toInt()
            .coerceIn(0, 100)

        val label = when {
            total >= 67 -> "Optimal Recovery"
            total >= 34 -> "Moderate Recovery"
            else -> "Low Recovery / Fatigued"
        }

        return RecoveryResult(
            totalScore = total,
            hrvScore = sHrv,
            rhrScore = sRhr,
            sleepScore = sSleep,
            spo2Score = sSpo2,
            label = label
        )
    }

    private fun computeHrvScore(
        hrv: Float?,
        minBaseline: Float?,
        maxBaseline: Float?
    ): Int {
        if (hrv == null || minBaseline == null || maxBaseline == null || maxBaseline <= minBaseline) {
            return 70 // Sensible default when baseline is building
        }

        return when {
            hrv >= maxBaseline -> 100
            hrv >= minBaseline -> {
                val ratio = (hrv - minBaseline) / (maxBaseline - minBaseline)
                (60 + ratio * 40).toInt()
            }
            else -> {
                // Below baseline - logarithmic penalty
                val deficit = (minBaseline - hrv) / minBaseline
                max(0, (60 - deficit * 100).toInt())
            }
        }.coerceIn(0, 100)
    }

    private fun computeRhrScore(rhr: Float?, baselineRhr: Float?): Int {
        if (rhr == null || baselineRhr == null || baselineRhr <= 30f) {
            return 70
        }

        val delta = rhr - baselineRhr
        return when {
            delta <= -3f -> 100 // RHR suppressed below baseline (well recovered)
            delta <= 0f -> (80 + (-delta / 3f) * 20).toInt()
            delta <= 5f -> (80 - (delta / 5f) * 30).toInt() // Slightly elevated
            else -> max(0, (50 - (delta - 5f) * 8).toInt()) // Significantly elevated (sympathetic stress)
        }.coerceIn(0, 100)
    }

    private fun computeSpo2Score(spo2: Float?): Int {
        if (spo2 == null) return 85
        return when {
            spo2 >= 97f -> 100
            spo2 >= 95f -> 90
            spo2 >= 92f -> 75
            spo2 >= 90f -> 50
            else -> max(0, ((spo2 - 80f) / 10f * 50).toInt())
        }.coerceIn(0, 100)
    }
}
