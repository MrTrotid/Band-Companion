package com.bandcompanion.core.model

/**
 * Standard sleep stages mapped from Huawei TruSleep sequence dictionary 700013.
 */
enum class SleepStageType(val rawCode: Int, val label: String) {
    LIGHT(1, "Light Sleep"),
    REM(2, "REM Sleep"),
    DEEP(3, "Deep Sleep"),
    AWAKE(4, "Awake"),
    NAP(5, "Nap");

    companion object {
        fun fromRawCode(code: Int): SleepStageType = when (code) {
            1 -> LIGHT
            2 -> REM
            3 -> DEEP
            4 -> AWAKE
            5 -> NAP
            else -> LIGHT
        }
    }
}

/**
 * Fine-grained hypnogram interval.
 *
 * @property stage The sleep stage category.
 * @property startTimestamp Unix epoch seconds.
 * @property endTimestamp Unix epoch seconds.
 * @property durationSeconds Duration of this interval in seconds.
 */
data class SleepStageInterval(
    val stage: SleepStageType,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationSeconds: Int = (endTimestamp - startTimestamp).toInt()
)
