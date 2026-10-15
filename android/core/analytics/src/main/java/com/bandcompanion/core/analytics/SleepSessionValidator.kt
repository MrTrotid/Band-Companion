package com.bandcompanion.core.analytics

import com.bandcompanion.core.model.SleepStageInterval
import com.bandcompanion.core.model.SleepStageType

/**
 * Validates, normalizes, and sanitizes TruSleep Dictionary 700013 stage intervals (SLEEP-01):
 * 1. Automatically detects and normalizes millisecond vs. second epoch timestamps.
 * 2. Rejects intervals with end <= start, negative duration, or duration > 24 hours.
 * 3. Chronologically sorts and trims overlapping boundaries to maintain continuous sleep hypnograms.
 */
object SleepSessionValidator {
    private const val MIN_VALID_EPOCH_SEC = 1704067200L // 2024-01-01T00:00:00Z
    private const val MAX_VALID_EPOCH_SEC = 2147483647L // 2038-01-19T03:14:07Z

    fun normalizeAndValidate(
        intervals: List<SleepStageInterval>
    ): List<SleepStageInterval> {
        val cleaned = intervals.mapNotNull { interval ->
            val startSec = normalizeToSeconds(interval.startTimestamp)
            val endSec = normalizeToSeconds(interval.endTimestamp)

            if (startSec !in MIN_VALID_EPOCH_SEC..MAX_VALID_EPOCH_SEC) return@mapNotNull null
            if (endSec !in MIN_VALID_EPOCH_SEC..MAX_VALID_EPOCH_SEC) return@mapNotNull null
            if (endSec <= startSec) return@mapNotNull null
            if (endSec - startSec > 86_400L) return@mapNotNull null // Exclude intervals > 24h

            interval.copy(
                startTimestamp = startSec,
                endTimestamp = endSec,
                durationSeconds = (endSec - startSec).toInt()
            )
        }.sortedBy { it.startTimestamp }

        if (cleaned.isEmpty()) return emptyList()

        // Ensure strictly non-overlapping intervals
        val nonOverlapping = mutableListOf<SleepStageInterval>()
        for (interval in cleaned) {
            val prev = nonOverlapping.lastOrNull()
            if (prev == null || interval.startTimestamp >= prev.endTimestamp) {
                nonOverlapping.add(interval)
            } else if (interval.endTimestamp > prev.endTimestamp) {
                // Trim overlap start to previous interval's end
                nonOverlapping.add(
                    interval.copy(
                        startTimestamp = prev.endTimestamp,
                        durationSeconds = (interval.endTimestamp - prev.endTimestamp).toInt()
                    )
                )
            }
        }
        return nonOverlapping
    }

    private fun normalizeToSeconds(timestamp: Long): Long {
        // If timestamp is in milliseconds (> 10^11), convert to seconds
        return if (timestamp > 100_000_000_000L) timestamp / 1000L else timestamp
    }
}
