package com.bandcompanion.core.analytics

import com.bandcompanion.core.model.SleepSession
import com.bandcompanion.core.model.SleepStageInterval
import com.bandcompanion.core.model.SleepStageType

/**
 * Detailed TruSleep 700013 stage breakdown and sleep hygiene analysis.
 */
object SleepAnalytics {

    data class SleepHygieneReport(
        val totalSleepMinutes: Int,
        val deepSleepMinutes: Int,
        val remSleepMinutes: Int,
        val lightSleepMinutes: Int,
        val awakeMinutes: Int,
        val deepSleepPercent: Float,
        val remSleepPercent: Float,
        val lightSleepPercent: Float,
        val sleepEfficiency: Float,
        val sleepScore: Int,
        val isDeepSleepAdequate: Boolean,
        val isRemSleepAdequate: Boolean,
        val summaryText: String
    )

    /**
     * Produces a comprehensive breakdown of TruSleep architecture.
     *
     * Benchmark targets:
     * - Deep Sleep: 20–25% of total sleep time
     * - REM Sleep: 20–25% of total sleep time
     * - Sleep Efficiency: >= 85%
     */
    fun analyzeSession(session: SleepSession): SleepHygieneReport {
        val total = session.totalSleepMinutes.coerceAtLeast(1)
        val deepPct = (session.deepSleepMinutes.toFloat() / total) * 100f
        val remPct = (session.remSleepMinutes.toFloat() / total) * 100f
        val lightPct = (session.lightSleepMinutes.toFloat() / total) * 100f
        val efficiency = session.sleepEfficiency ?: if (session.durationMinutes > 0) {
            (total.toFloat() / session.durationMinutes) * 100f
        } else 85f

        val deepOk = deepPct >= 18f
        val remOk = remPct >= 18f

        val summary = buildString {
            append("Efficiency: ${efficiency.toInt()}%. ")
            if (deepOk) append("Deep sleep is restorative (${deepPct.toInt()}%). ")
            else append("Deep sleep below target (${deepPct.toInt()}%). ")
            if (remOk) append("REM cognitive sleep is optimal (${remPct.toInt()}%).")
            else append("REM cognitive sleep below target (${remPct.toInt()}%).")
        }

        return SleepHygieneReport(
            totalSleepMinutes = total,
            deepSleepMinutes = session.deepSleepMinutes,
            remSleepMinutes = session.remSleepMinutes,
            lightSleepMinutes = session.lightSleepMinutes,
            awakeMinutes = session.awakeMinutes,
            deepSleepPercent = Math.round(deepPct * 10f) / 10f,
            remSleepPercent = Math.round(remPct * 10f) / 10f,
            lightSleepPercent = Math.round(lightPct * 10f) / 10f,
            sleepEfficiency = Math.round(efficiency * 10f) / 10f,
            sleepScore = session.sleepScore ?: 70,
            isDeepSleepAdequate = deepOk,
            isRemSleepAdequate = remOk,
            summaryText = summary
        )
    }
}
