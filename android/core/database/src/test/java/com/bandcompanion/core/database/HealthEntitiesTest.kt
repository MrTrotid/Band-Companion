package com.bandcompanion.core.database

import com.bandcompanion.core.model.DailyRollup
import com.bandcompanion.core.model.MoodEntry
import com.bandcompanion.core.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class HealthEntitiesTest {

    @Test
    fun testBmiCalculationAndWeightEntry() {
        val weight = WeightEntry(
            timestamp = 1791800000L,
            weightKg = 70.0f,
            userHeightCm = 175.0f,
            note = "Morning weigh-in"
        )
        // BMI = 70 / (1.75 * 1.75) = 22.857
        assertEquals(22.86f, weight.bmi, 0.05f)
        assertEquals("NORMAL", weight.bmiCategory)
    }

    @Test
    fun testMoodEntryCreation() {
        val mood = MoodEntry(
            deviceId = "C4:16:88:8D:04:EF",
            timestamp = 1791800000L,
            moodType = "ENERGIZED",
            energyLevel = 4,
            note = "Feeling great after workout"
        )
        assertEquals("ENERGIZED", mood.moodType)
        assertEquals(4, mood.energyLevel)
    }

    @Test
    fun testDailyRollupFourRingProgress() {
        val rollup = DailyRollup(
            deviceId = "C4:16:88:8D:04:EF",
            date = "2026-10-12",
            stepCount = 8500,
            activeMinutes = 45,
            caloriesBurned = 620,
            standHours = 10
        )
        assertEquals(0.85f, rollup.stepProgress(10000), 0.01f)
        assertEquals(1.50f, rollup.activeMinutesProgress(30), 0.01f)
        assertEquals(1.24f, rollup.calorieProgress(500), 0.01f)
        assertEquals(0.83f, rollup.standHoursProgress(12), 0.01f)
    }
}
