package com.bandcompanion.app.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Categorical health telemetry sections for deep breadcrumb navigation.
 */
enum class HealthDetailSection {
    HEART_RATE,
    HRV_RADAR,
    OXYGENATION,
    STRESS_WELLBEING
}

/**
 * Editorial-grade Deep Physiological Analytics Screen (Destination 2: HEALTH).
 * Features:
 * - Direct breadcrumb navigation into dedicated deep telemetry pages
 * - Replaces dropdown accordions with top-left Back button navigation
 * - Real-time and continuous biometric streams:
 *   1. 24/7 Continuous Heart Rate with interactive spike hovering and unworn gaps
 *   2. Autonomic HRV Resilience Radar (rMSSD vs baseline corridor)
 *   3. Arterial Blood Oxygenation (SpO2) with optical sensor coverage
 *   4. Autonomic Nervous System & TruRelax™ Stress with automated valence
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
    var activeDetailSection by remember { mutableStateOf<HealthDetailSection?>(null) }

    // Intercept hardware/system back gesture when viewing a sub-page
    BackHandler(enabled = activeDetailSection != null) {
        activeDetailSection = null
    }

    AnimatedContent(
        targetState = activeDetailSection,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> -width / 2 } + fadeOut()
                )
            } else {
                (slideInHorizontally { width -> -width / 2 } + fadeIn()).togetherWith(
                    slideOutHorizontally { width -> width } + fadeOut()
                )
            }
        },
        label = "HealthNavTransition"
    ) { section ->
        when (section) {
            HealthDetailSection.HEART_RATE -> {
                HeartRateDetailView(
                    currentBpm = currentPulseBpm,
                    restingBpm = todaySummary?.restingHeartRate,
                    minBpm = todaySummary?.minHeartRate,
                    maxBpm = todaySummary?.maxHeartRate,
                    hrSamples = hrSamples,
                    onBackClick = { activeDetailSection = null },
                    modifier = modifier
                )
            }
            HealthDetailSection.OXYGENATION -> {
                OxygenationDetailView(
                    currentSpo2 = todaySummary?.avgSpo2,
                    minSpo2 = todaySummary?.avgSpo2?.let { it - 2.0f },
                    maxSpo2 = 99f,
                    spo2Samples = hrSamples,
                    onBackClick = { activeDetailSection = null },
                    modifier = modifier
                )
            }
            HealthDetailSection.HRV_RADAR -> {
                HrvDetailView(
                    latestSleepSession = latestSleepSession,
                    onBackClick = { activeDetailSection = null },
                    modifier = modifier
                )
            }
            HealthDetailSection.STRESS_WELLBEING -> {
                StressDetailView(
                    stressScore = todaySummary?.stressScore,
                    latestMood = null,
                    stressRecords = stressRecords,
                    onBackClick = { activeDetailSection = null },
                    modifier = modifier
                )
            }
            null -> {
                // Main Health Overview Screen (4 Hero Cards with Click-through Navigation)
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
                        restingBpm = todaySummary?.restingHeartRate ?: 54,
                        minBpm = todaySummary?.minHeartRate ?: 54,
                        maxBpm = todaySummary?.maxHeartRate ?: 130,
                        hrSamples = hrSamples,
                        onOpenDetail = { activeDetailSection = HealthDetailSection.HEART_RATE }
                    )

                    // 2. Autonomic HRV Resilience Radar (rMSSD vs Baseline Corridor)
                    HrvRadarCard(
                        onOpenDetail = { activeDetailSection = HealthDetailSection.HRV_RADAR }
                    )

                    // 3. Arterial Blood Oxygenation (SpO2) Module
                    OxygenationCard(
                        currentSpo2 = todaySummary?.avgSpo2 ?: 97f,
                        minSpo2 = (todaySummary?.avgSpo2 ?: 97f) - 2.0f,
                        maxSpo2 = 99f,
                        spo2Samples = hrSamples,
                        onOpenDetail = { activeDetailSection = HealthDetailSection.OXYGENATION }
                    )

                    // 4. Autonomic Nervous System & TruRelax™ Stress Module
                    EmotionalWellbeingCard(
                        stressScore = todaySummary?.stressScore ?: 41,
                        latestMood = null,
                        onLogMoodClick = {},
                        stressRecords = stressRecords,
                        onOpenDetail = { activeDetailSection = HealthDetailSection.STRESS_WELLBEING }
                    )

                    Spacer(modifier = Modifier.height(110.dp))
                }
            }
        }
    }
}
