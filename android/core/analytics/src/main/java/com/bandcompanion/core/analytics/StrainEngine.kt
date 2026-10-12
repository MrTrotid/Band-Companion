package com.bandcompanion.core.analytics

import com.bandcompanion.core.model.SampleMetric
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Cardiovascular Day Strain model implementing Banister's TRIMP scaled logarithmically to 0.0 – 21.0.
 *
 * DeltaHR(t) = (HR(t) - HR_rest) / (HR_max - HR_rest)
 * TRIMP = Sum[ DeltaHR(t) * 0.64 * e^(1.92 * DeltaHR(t)) * dt ]
 * Strain = min(21.0, 7.0 * ln(1.0 + TRIMP / k))
 */
object StrainEngine {

    data class HeartRateZones(
        val zone1Minutes: Int = 0, // 50-60% HRR (Active Recovery)
        val zone2Minutes: Int = 0, // 60-70% HRR (Aerobic / Fat Burn)
        val zone3Minutes: Int = 0, // 70-80% HRR (Tempo)
        val zone4Minutes: Int = 0, // 80-90% HRR (Threshold)
        val zone5Minutes: Int = 0  // 90-100% HRR (Anaerobic / Peak)
    )

    data class StrainResult(
        val dayStrain: Float,        // 0.0 to 21.0
        val rawTrimp: Float,
        val zones: HeartRateZones,
        val peakHeartRate: Int,
        val averageActiveHeartRate: Int
    )

    /**
     * Calculates daily cardiovascular strain from minute-by-minute samples.
     *
     * @param samples Chronologically ordered minute samples.
     * @param restingHr Personal baseline resting heart rate (default 60 bpm).
     * @param maxHr Personal maximum heart rate (default 190 bpm).
     * @param scalingFactor K divisor for logarithmic compression (default 12.0f).
     */
    fun calculateStrain(
        samples: List<SampleMetric>,
        restingHr: Int = 60,
        maxHr: Int = 190,
        scalingFactor: Float = 12.0f
    ): StrainResult {
        if (samples.isEmpty()) {
            return StrainResult(0.0f, 0.0f, HeartRateZones(), 0, 0)
        }

        val hrRange = (maxHr - restingHr).coerceAtLeast(30).toFloat()
        var totalTrimp = 0.0f
        var peakHr = 0
        var hrSum = 0
        var hrCount = 0

        var z1 = 0
        var z2 = 0
        var z3 = 0
        var z4 = 0
        var z5 = 0

        for (sample in samples) {
            val hr = sample.heartRate ?: continue
            if (sample.isOffWrist || hr < restingHr) continue

            if (hr > peakHr) peakHr = hr
            hrSum += hr
            hrCount++

            // Heart Rate Reserve fraction: 0.0 to 1.0
            val deltaHr = ((hr - restingHr) / hrRange).coerceIn(0.0f, 1.0f)

            // Banister TRIMP minute contribution
            // dt = 1 minute
            val trimpMinute = deltaHr * 0.64f * exp(1.92f * deltaHr)
            totalTrimp += trimpMinute

            // Categorize zone
            when {
                deltaHr >= 0.90f -> z5++
                deltaHr >= 0.80f -> z4++
                deltaHr >= 0.70f -> z3++
                deltaHr >= 0.60f -> z2++
                deltaHr >= 0.50f -> z1++
            }
        }

        // Logarithmic compression to 0.0 - 21.0 scale
        val strain = if (totalTrimp > 0f) {
            min(21.0f, (7.0f * ln(1.0f + (totalTrimp / scalingFactor))).toFloat())
        } else {
            0.0f
        }

        val avgHr = if (hrCount > 0) hrSum / hrCount else 0

        return StrainResult(
            dayStrain = (Math.round(strain * 10.0f) / 10.0f),
            rawTrimp = totalTrimp,
            zones = HeartRateZones(z1, z2, z3, z4, z5),
            peakHeartRate = peakHr,
            averageActiveHeartRate = avgHr
        )
    }
}
