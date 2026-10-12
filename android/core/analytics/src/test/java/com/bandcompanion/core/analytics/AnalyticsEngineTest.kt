package com.bandcompanion.core.analytics

import com.bandcompanion.core.model.SampleMetric
import com.bandcompanion.core.model.SleepSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsEngineTest {

    @Test
    fun testRecoveryEngineOptimal() {
        val result = RecoveryEngine.calculateRecovery(
            overnightHrv = 84f,
            minHrvBaseline = 51f,
            maxHrvBaseline = 78f,
            overnightRhr = 52f,
            baselineRhr = 56f,
            sleepQualityScore = 85,
            overnightSpo2 = 98f
        )

        // Overnight HRV above upper baseline (100) + RHR suppressed below baseline (100) + high sleep score
        assertTrue(result.totalScore >= 80)
        assertEquals("Optimal Recovery", result.label)
        assertEquals(100, result.hrvScore)
        assertEquals(100, result.rhrScore)
    }

    @Test
    fun testRecoveryEngineFatigued() {
        val result = RecoveryEngine.calculateRecovery(
            overnightHrv = 30f,
            minHrvBaseline = 50f,
            maxHrvBaseline = 75f,
            overnightRhr = 68f,
            baselineRhr = 55f,
            sleepQualityScore = 40,
            overnightSpo2 = 91f
        )

        // Depressed HRV + significantly elevated RHR
        assertTrue(result.totalScore < 45)
        assertEquals("Low Recovery / Fatigued", result.label)
    }

    @Test
    fun testStrainEngineCalculations() {
        val now = System.currentTimeMillis() / 1000
        val samples = mutableListOf<SampleMetric>()

        // 30 minutes at resting HR (60 bpm) -> no active strain
        for (i in 0 until 30) {
            samples.add(SampleMetric(timestamp = now + (i * 60), heartRate = 60))
        }

        val restingStrain = StrainEngine.calculateStrain(samples, restingHr = 60, maxHr = 190)
        assertEquals(0.0f, restingStrain.dayStrain, 0.01f)

        // Add 45 minutes of vigorous exercise at 165 bpm (threshold/anaerobic)
        for (i in 30 until 75) {
            samples.add(SampleMetric(timestamp = now + (i * 60), heartRate = 165))
        }

        val workoutStrain = StrainEngine.calculateStrain(samples, restingHr = 60, maxHr = 190)
        assertTrue("Workout strain should be substantially elevated", workoutStrain.dayStrain > 8.0f)
        assertTrue(workoutStrain.zones.zone4Minutes + workoutStrain.zones.zone5Minutes > 30)
    }

    @Test
    fun testAcwrCalculations() {
        // Steady workload of 10 strain per day for 20 days
        val steadyHistory = List(20) { 10.0f }
        val steadyResult = AcwrEngine.calculateAcwr(steadyHistory)

        assertNotNull(steadyResult)
        assertEquals(AcwrEngine.WorkloadStatus.OPTIMAL, steadyResult!!.status)
        assertTrue(steadyResult.ratio in 0.95f..1.05f)

        // Sudden acute spike: 10 days of 5 strain followed by 3 days of 20 strain
        val spikeHistory = List(10) { 5.0f } + listOf(20.0f, 20.0f, 20.0f)
        val spikeResult = AcwrEngine.calculateAcwr(spikeHistory)

        assertNotNull(spikeResult)
        assertTrue(spikeResult!!.ratio > 1.30f)
    }

    @Test
    fun testSleepAnalyticsReport() {
        val session = SleepSession(
            sessionId = "test-session-1",
            startTimestamp = 1000000L,
            endTimestamp = 1028800L,
            durationMinutes = 480,
            sleepScore = 88,
            deepSleepMinutes = 110,
            remSleepMinutes = 105,
            lightSleepMinutes = 240,
            awakeMinutes = 25,
            sleepEfficiency = 92f
        )

        val report = SleepAnalytics.analyzeSession(session)
        assertEquals(455, report.totalSleepMinutes)
        assertTrue(report.isDeepSleepAdequate)
        assertTrue(report.isRemSleepAdequate)
        assertEquals(88, report.sleepScore)
    }
}
