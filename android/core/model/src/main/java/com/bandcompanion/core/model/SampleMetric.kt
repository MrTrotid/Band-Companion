package com.bandcompanion.core.model

/**
 * Granular minute-level health and activity measurement.
 *
 * @property timestamp Unix epoch seconds at minute boundary.
 * @property steps Delta steps accumulated in this minute.
 * @property heartRate Beats per minute (null if off-wrist).
 * @property spo2 Blood oxygen saturation percentage (null if not sampled).
 * @property intensity Movement intensity rating (0-255).
 * @property calories Estimated energy expenditure in kcal.
 */
data class SampleMetric(
    val timestamp: Long,
    val steps: Int = 0,
    val heartRate: Int? = null,
    val spo2: Float? = null,
    val intensity: Int = 0,
    val calories: Int = 0,
    val source: String = "LPV2_FITNESS"
) {
    val isOffWrist: Boolean
        get() = heartRate == null || heartRate <= 0 || heartRate >= 254
}
