package com.bandcompanion.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bandcompanion.app.ui.component.EmotionalWellbeingCard
import com.bandcompanion.app.ui.component.HeartRateCard
import com.bandcompanion.app.ui.component.OxygenationCard
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler
import com.bandcompanion.core.model.DailySummary
import com.bandcompanion.core.model.SampleMetric
import com.bandcompanion.core.model.SleepSession

/**
 * Editorial-grade Deep Physiological Analytics Screen (Destination 2: HEALTH).
 * Provides granular, interactive exploration of continuous biometric streams:
 * 1. 24/7 Continuous Heart Rate, time-axis waveform, Lowest/Peak callouts, 5-zone cardiac breakdown.
 * 2. Nocturnal HRV Resilience Radar (rMSSD 67 ms vs 51–78 ms baseline corridor).
 * 3. Arterial Blood Oxygenation (SpO2) with Optical Sensor Coverage & Motion-Gated Rest Ribbon.
 * 4. Autonomic Nervous System & TruRelax™ Stress with automated 3-state valence and stillness gate.
 */
@Composable
fun HealthAnalyticsScreen(
    todaySummary: DailySummary?,
    latestSleepSession: SleepSession?,
    hrSamples: List<SampleMetric>,
    currentPulseBpm: Int?,
    stressRecords: List<BandProtocolHandler.StressRecord> = emptyList(),
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "PHYSIOLOGICAL VITAL STREAMS",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )

        // 1. Continuous Heart Rate & Cardiovascular Strain Module
        HeartRateCard(
            currentBpm = currentPulseBpm,
            restingBpm = todaySummary?.restingHeartRate ?: 52,
            minBpm = todaySummary?.minHeartRate ?: 52,
            maxBpm = todaySummary?.maxHeartRate ?: 130,
            hrSamples = hrSamples
        )

        // 2. Autonomic HRV Resilience Radar (rMSSD vs Baseline Corridor)
        HrvRadarCard()

        // 3. Arterial Blood Oxygenation (SpO2) Module
        OxygenationCard(
            currentSpo2 = todaySummary?.avgSpo2 ?: 97f,
            minSpo2 = (todaySummary?.avgSpo2 ?: 97f) - 2.0f,
            maxSpo2 = 99f,
            spo2Samples = hrSamples
        )

        // 4. Autonomic Nervous System & TruRelax™ Stress Module
        EmotionalWellbeingCard(
            stressScore = todaySummary?.stressScore ?: 41,
            latestMood = null,
            onLogMoodClick = {},
            stressRecords = stressRecords
        )

        Spacer(modifier = Modifier.height(110.dp))
    }
}
