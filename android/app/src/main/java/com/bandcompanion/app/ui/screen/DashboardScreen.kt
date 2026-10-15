package com.bandcompanion.app.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.component.ConcentricActivityRings
import com.bandcompanion.app.ui.component.EmotionalWellbeingCard
import com.bandcompanion.app.ui.component.HeartRateCard
import com.bandcompanion.app.ui.component.LogWeightDialog
import com.bandcompanion.app.ui.component.MoodCheckInDialog
import com.bandcompanion.app.ui.component.OxygenationCard
import com.bandcompanion.app.ui.component.SleepPerformanceCard
import com.bandcompanion.app.ui.component.WatchHeroCard
import com.bandcompanion.app.ui.component.WeatherCard
import com.bandcompanion.app.ui.component.WeightBmiCard
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler
import com.bandcompanion.app.ui.theme.CrimsonStrain
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.HotMagentaRing
import com.bandcompanion.app.ui.theme.LavenderRecovery
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler.WeatherCondition
import com.bandcompanion.core.model.DailySummary
import com.bandcompanion.core.model.DeviceInfo
import com.bandcompanion.core.model.MoodEntry
import com.bandcompanion.core.model.SampleMetric
import com.bandcompanion.core.model.SleepSession
import com.bandcompanion.core.model.WeightEntry

/**
 * Athletic Health Hub Dashboard styled after Whoop & Oura.
 * Unifies 4 Concentric Activity Rings, Cardiovascular Strain, TruSleep™ Staging,
 * SpO2 Oxygenation, Autonomic Emotional Wellbeing, and Body Composition.
 */
@Composable
fun DashboardScreen(
    deviceInfo: DeviceInfo?,
    todaySummary: DailySummary?,
    latestSleepSession: SleepSession? = null,
    latestMood: MoodEntry? = null,
    latestWeight: WeightEntry? = null,
    hrSamples: List<SampleMetric> = emptyList(),
    currentPulseBpm: Int? = null,
    isSyncing: Boolean = false,
    isLiveConnected: Boolean = false,
    weather: WeatherCondition = WeatherCondition(21, 2, "Partly Cloudy", "San Francisco", 3, 34),
    isSyncingWeather: Boolean = false,
    onSyncWeatherClick: () -> Unit = {},
    onOpenWatchfacesClick: () -> Unit = {},
    onOpenStressTestClick: () -> Unit = {},
    onSyncClick: () -> Unit,
    onPairClick: () -> Unit,
    onLogMood: (moodType: String, energyLevel: Int, note: String?) -> Unit = { _, _, _ -> },
    onLogWeight: (weightKg: Float, heightCm: Float, note: String?) -> Unit = { _, _, _ -> },
    stressRecords: List<BandProtocolHandler.StressRecord> = emptyList()
) {
    val scrollState = rememberScrollState()
    val isBonded = deviceInfo?.isBonded == true

    var showMoodDialog by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }

    // Whoop-style Banister TRIMP logarithmic daily strain (0 - 21.0 scale)
    val dailyStrain = remember(hrSamples) {
        val validHrs = hrSamples.mapNotNull { it.heartRate }.filter { it in 40..220 }
        if (validHrs.isEmpty()) {
            8.4f
        } else {
            val rest = 55f
            val max = 190f
            var trimpSum = 0.0
            for (hr in validHrs) {
                val deltaHr = ((hr - rest) / (max - rest)).coerceIn(0f, 1f)
                trimpSum += (deltaHr * 0.64 * kotlin.math.exp(1.92 * deltaHr))
            }
            val raw = (7.0 * kotlin.math.ln(1.0 + (trimpSum / 30.0))).toFloat()
            raw.coerceIn(0f, 21.0f)
        }
    }

    // Oura-style Recovery / Readiness score (0 - 100%)
    val recoveryScore = remember(latestSleepSession, todaySummary) {
        val sleepScore = latestSleepSession?.sleepScore ?: 74
        val sleepContribution = sleepScore * 0.40f
        val hrv = todaySummary?.avgHrv ?: 65f
        val hrvContribution = (hrv / 90f).coerceIn(0.4f, 1.2f) * 35f
        val rhr = todaySummary?.restingHeartRate ?: 58
        val rhrContribution = ((75 - rhr).coerceIn(0, 30) / 30f) * 25f
        (sleepContribution + hrvContribution + rhrContribution).toInt().coerceIn(35, 99)
    }

    if (showMoodDialog) {
        MoodCheckInDialog(
            onDismiss = { showMoodDialog = false },
            onSave = { mood, energy, note ->
                onLogMood(mood, energy, note)
            }
        )
    }

    if (showWeightDialog) {
        LogWeightDialog(
            initialWeightKg = latestWeight?.weightKg ?: 70.0f,
            initialHeightCm = latestWeight?.userHeightCm ?: 175.0f,
            onDismiss = { showWeightDialog = false },
            onSave = { weight, height, note ->
                onLogWeight(weight, height, note)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Setup / Pair Banner if not bonded
        if (!isBonded) {
            UnbondedSetupCard(onPairClick = onPairClick)
        }

        // Hardware Overview: Centered Huawei Band 10 with Radiating Callout Arrows
        WatchHeroCard(
            deviceInfo = deviceInfo,
            currentPulseBpm = currentPulseBpm,
            todaySteps = todaySummary?.totalSteps,
            isLiveConnected = isLiveConnected,
            onWatchfaceClick = onOpenWatchfacesClick,
            onPulseClick = { },
            onActivityClick = { },
            onStressClick = onOpenStressTestClick
        )

        // 1. Recovery & Cardiovascular Strain
        DashboardSectionHeader(title = "DAILY RECOVERY & STRAIN")
        AthleticRecoveryStrainRow(
            recoveryScore = recoveryScore,
            dailyStrain = dailyStrain
        )

        // 2. Concentric 4-Ring Activity Canvas Engine
        DashboardSectionHeader(title = "4-RING ACTIVITY ENGINE")
        ConcentricActivityRings(
            stepCount = todaySummary?.totalSteps ?: 0,
            stepTarget = 10000,
            activeMinutes = todaySummary?.activeMinutes ?: 0,
            activeMinutesTarget = 30,
            calories = todaySummary?.caloriesBurned ?: ((todaySummary?.totalSteps ?: 0) * 0.045f).toInt(),
            calorieTarget = 500,
            standHours = todaySummary?.standHours ?: 0,
            standHoursTarget = 12
        )

        // Quick Studio Launchers Row (Watchface Studio & 60s Stress Test)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(18.dp), spotColor = ElectricCyan.copy(alpha = 0.2f))
                    .clickable { onOpenWatchfacesClick() },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            0.75.dp,
                            Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(text = "Watchfaces", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "AMOLED Studio", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(84.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(18.dp), spotColor = NeonTeal.copy(alpha = 0.2f))
                    .clickable { onOpenStressTestClick() },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            0.75.dp,
                            Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(NeonTeal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Psychology, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(20.dp))
                        }
                        Column {
                            Text(text = "Stress Test", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "60s Calibration", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            }
        }

        // Atmospheric Conditions & Weather Push
        DashboardSectionHeader(title = "ATMOSPHERIC CONDITIONS")
        WeatherCard(
            weather = weather,
            isSyncingWeather = isSyncingWeather,
            onSyncWeatherClick = onSyncWeatherClick
        )

        // 3. Cardiovascular Strain & 24/7 Continuous PPG Curve
        DashboardSectionHeader(title = "CARDIOVASCULAR HEALTH")
        HeartRateCard(
            currentBpm = currentPulseBpm ?: todaySummary?.restingHeartRate,
            restingBpm = todaySummary?.restingHeartRate,
            minBpm = todaySummary?.minHeartRate,
            maxBpm = todaySummary?.maxHeartRate,
            hrSamples = hrSamples
        )

        // 4. TruSleep™ Staging & Sleep Performance
        DashboardSectionHeader(title = "TRUSLEEP™ STAGING & REST")
        SleepPerformanceCard(
            sleepSession = latestSleepSession,
            overnightHrvMs = todaySummary?.avgHrv ?: latestSleepSession?.avgHrv ?: 67f,
            respiratoryRate = latestSleepSession?.avgBreathRate ?: 15.0f
        )

        // 5. Blood Oxygenation (SpO2)
        val realSpo2s = remember(hrSamples) { hrSamples.mapNotNull { it.spo2 } }
        val minSpo2 = remember(realSpo2s) { realSpo2s.minOrNull() }
        val maxSpo2 = remember(realSpo2s) { realSpo2s.maxOrNull() }

        DashboardSectionHeader(title = "BLOOD OXYGENATION")
        OxygenationCard(
            currentSpo2 = todaySummary?.avgSpo2 ?: 98f,
            minSpo2 = minSpo2 ?: todaySummary?.avgSpo2?.minus(2.0f) ?: 95f,
            maxSpo2 = maxSpo2 ?: 99f,
            spo2Samples = hrSamples
        )

        // 6. Emotional Wellbeing & Autonomic Stress
        DashboardSectionHeader(title = "AUTONOMIC NERVOUS SYSTEM")
        EmotionalWellbeingCard(
            stressScore = todaySummary?.stressScore ?: 39,
            latestMood = null,
            onLogMoodClick = {},
            stressRecords = stressRecords
        )

        // 7. Body Composition & BMI Tracker
        DashboardSectionHeader(title = "BODY COMPOSITION")
        WeightBmiCard(
            latestWeight = latestWeight,
            onLogWeightClick = { showWeightDialog = true }
        )

        Spacer(modifier = Modifier.height(110.dp))
    }
}

@Composable
fun DashboardSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.0.sp,
        modifier = modifier.padding(start = 4.dp, top = 2.dp)
    )
}

@Composable
fun AthleticRecoveryStrainRow(
    recoveryScore: Int,
    dailyStrain: Float
) {
    val (recoveryLabel, recoveryColor) = when {
        recoveryScore >= 80 -> Pair("Optimal Recovery", NeonTeal)
        recoveryScore >= 60 -> Pair("Good Readiness", ElectricCyan)
        recoveryScore >= 40 -> Pair("Fair Rest", LavenderRecovery)
        else -> Pair("Low Recovery", CrimsonStrain)
    }

    val (strainLabel, strainColor) = when {
        dailyStrain < 10f -> Pair("Light Load", ElectricCyan)
        dailyStrain < 14f -> Pair("Moderate Load", NeonTeal)
        dailyStrain < 18f -> Pair("Strenuous Load", HotMagentaRing)
        else -> Pair("All-Out Effort", CrimsonStrain)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Recovery Card
        Card(
            modifier = Modifier
                .weight(1f)
                .height(115.dp)
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(22.dp), spotColor = recoveryColor.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        0.75.dp,
                        Brush.verticalGradient(listOf(recoveryColor.copy(alpha = 0.5f), Color.Transparent)),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "RECOVERY", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextMuted, letterSpacing = 0.5.sp)
                        Icon(imageVector = Icons.Default.AutoGraph, contentDescription = null, tint = recoveryColor, modifier = Modifier.size(15.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(text = "$recoveryScore", fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                        Text(text = "%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = recoveryColor, modifier = Modifier.padding(bottom = 4.dp))
                    }

                    Text(text = recoveryLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = recoveryColor)
                }
            }
        }

        // Daily Strain Card
        Card(
            modifier = Modifier
                .weight(1f)
                .height(115.dp)
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(22.dp), spotColor = strainColor.copy(alpha = 0.25f)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        0.75.dp,
                        Brush.verticalGradient(listOf(strainColor.copy(alpha = 0.5f), Color.Transparent)),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(14.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "DAY STRAIN", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextMuted, letterSpacing = 0.5.sp)
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = strainColor, modifier = Modifier.size(15.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(text = "%.1f".format(dailyStrain), fontSize = 32.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                        Text(text = "/ 21", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
                    }

                    Text(text = strainLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = strainColor)
                }
            }
        }
    }
}

@Composable
fun AthleticDeviceHeaderCard(
    deviceInfo: DeviceInfo?,
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseDot")
    val dotScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(ObsidianHighlight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothConnected,
                        contentDescription = null,
                        tint = NeonCyanRing,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = deviceInfo?.name ?: "HUAWEI Band 10",
                        fontSize = 15.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .scale(dotScale)
                                .background(RelaxedGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${deviceInfo?.batteryLevel ?: 98}% • Autonomous 3-min Sync",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Button(
                onClick = onSyncClick,
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ObsidianHighlight,
                    contentColor = NeonCyanRing
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = NeonCyanRing
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Sync", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun UnbondedSetupCard(onPairClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPairClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(NeonCyanRing.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLink,
                        contentDescription = null,
                        tint = NeonCyanRing,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Connect Huawei Band 10",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Tap to pair via encrypted HiChain3",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            Button(
                onClick = onPairClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyanRing,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "Pair", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
