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

    @Test
    fun testRecoveryRenormalizationWhenHrvMissing() {
        // MATH-01: When overnight HRV is missing, weights must re-normalize rather than penalizing score to near-zero
        val result = RecoveryEngine.calculateRecovery(
            overnightHrv = null,
            minHrvBaseline = null,
            maxHrvBaseline = null,
            overnightRhr = 52f,
            baselineRhr = 56f,
            sleepQualityScore = 90,
            overnightSpo2 = 98f
        )

        // RHR (suppressed -> 100) + Sleep (90) + SpO2 (98% -> 100) re-normalized over 0.25 + 0.20 + 0.15 = 0.60
        assertTrue("Expected high recovery when present metrics are optimal", result.totalScore >= 85)
        assertTrue("Expected partial flag when HRV is missing", result.isPartial)
        assertEquals("Optimal Recovery", result.label)
    }

    @Test
    fun testAcwrCalibrationGuard() {
        // MATH-01: On Days 1-13, ACWR must flag CALIBRATING to prevent false Day-1 spikes
        val fiveDaysOfStrain = listOf(12.0f, 10.0f, 15.0f, 8.0f, 11.0f)
        val result = AcwrEngine.calculateAcwr(fiveDaysOfStrain)

        assertNotNull(result)
        assertTrue("Expected isCalibrating=true for < 14 days", result!!.isCalibrating)
        assertEquals(AcwrEngine.WorkloadStatus.CALIBRATING, result.status)
        assertEquals(5, result.calibrationDays)
    }

    @Test
    fun testStrainEngineSentinelAndClampingGuards() {
        // MATH-01: Reject off-wrist 0/255 bpm and guard against division by zero
        val now = System.currentTimeMillis() / 1000L
        val noisySamples = listOf(
            SampleMetric(timestamp = now, heartRate = 0),     // off-wrist 0
            SampleMetric(timestamp = now + 60, heartRate = 255), // off-wrist 255
            SampleMetric(timestamp = now + 120, heartRate = 135) // valid aerobic
        )

        val result = StrainEngine.calculateStrain(
            samples = noisySamples,
            restingHr = 60,
            maxHr = 190
        )

        assertTrue("Expected positive finite strain for valid sample", result.dayStrain > 0.0f)
        assertTrue("Expected non-zero raw TRIMP", result.rawTrimp > 0.0f)
        assertEquals(135, result.peakHeartRate)
    }

    @Test
    fun testSleepSessionValidatorIntervals() {
        // SLEEP-01: Normalizes ms to sec, filters negative durations, trims overlaps
        val intervals = listOf(
            com.bandcompanion.core.model.SleepStageInterval(
                stage = com.bandcompanion.core.model.SleepStageType.LIGHT,
                startTimestamp = 1791997200000L, // in ms
                endTimestamp = 1791999000000L,   // in ms
                durationSeconds = 1800
            ),
            com.bandcompanion.core.model.SleepStageInterval(
                stage = com.bandcompanion.core.model.SleepStageType.DEEP,
                startTimestamp = 1791998400L, // overlaps previous by 600s
                endTimestamp = 1792000800L,
                durationSeconds = 2400
            ),
            com.bandcompanion.core.model.SleepStageInterval(
                stage = com.bandcompanion.core.model.SleepStageType.REM,
                startTimestamp = 1792000800L,
                endTimestamp = 1792000000L, // inverted / negative duration
                durationSeconds = -800
            )
        )

        val validated = SleepSessionValidator.normalizeAndValidate(intervals)
        assertEquals(2, validated.size)
        assertEquals(com.bandcompanion.core.model.SleepStageType.LIGHT, validated[0].stage)
        assertEquals(1791997200L, validated[0].startTimestamp)
        assertEquals(1791999000L, validated[0].endTimestamp)

        // Second interval start must be trimmed to previous interval's end
        assertEquals(com.bandcompanion.core.model.SleepStageType.DEEP, validated[1].stage)
        assertEquals(1791999000L, validated[1].startTimestamp)
        assertEquals(1792000800L, validated[1].endTimestamp)
        assertEquals(1800, validated[1].durationSeconds)
    }
}
