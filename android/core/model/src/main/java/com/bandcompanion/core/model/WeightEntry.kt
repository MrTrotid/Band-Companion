package com.bandcompanion.core.model

/**
 * Domain model representing a body weight measurement and calculated BMI metrics.
 */
data class WeightEntry(
    val id: Long = 0,
    val timestamp: Long,
    val weightKg: Float,
    val userHeightCm: Float = 175.0f,
    val note: String? = null
) {
    val bmi: Float
        get() {
            val heightM = userHeightCm / 100.0f
            return if (heightM > 0f) weightKg / (heightM * heightM) else 0f
        }

    val bmiCategory: String
        get() = when {
            bmi < 18.5f -> "UNDERWEIGHT"
            bmi < 25.0f -> "NORMAL"
            bmi < 30.0f -> "OVERWEIGHT"
            else -> "OBESE"
        }
}
