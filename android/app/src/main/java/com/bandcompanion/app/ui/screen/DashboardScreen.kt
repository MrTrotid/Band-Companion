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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.scale
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
import com.bandcompanion.app.ui.component.WeightBmiCard
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
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
    onSyncClick: () -> Unit,
    onPairClick: () -> Unit,
    onLogMood: (moodType: String, energyLevel: Int, note: String?) -> Unit = { _, _, _ -> },
    onLogWeight: (weightKg: Float, heightCm: Float, note: String?) -> Unit = { _, _, _ -> }
) {
    val scrollState = rememberScrollState()
    val isBonded = deviceInfo?.isBonded == true

    var showMoodDialog by remember { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(false) }

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
        } else {
            // Connected Device Status Header
            AthleticDeviceHeaderCard(
                deviceInfo = deviceInfo,
                isSyncing = isSyncing,
                onSyncClick = onSyncClick
            )
        }

        // 1. Concentric 4-Ring Activity Canvas Engine
        ConcentricActivityRings(
            stepCount = todaySummary?.totalSteps ?: 0,
            stepTarget = 10000,
            activeMinutes = todaySummary?.activeMinutes ?: ((todaySummary?.totalSteps ?: 0) / 100),
            activeMinutesTarget = 30,
            calories = todaySummary?.caloriesBurned ?: ((todaySummary?.totalSteps ?: 0) * 0.045f).toInt(),
            calorieTarget = 500,
            standHours = todaySummary?.standHours ?: if ((todaySummary?.totalSteps ?: 0) > 2000) 8 else 3,
            standHoursTarget = 12
        )

        // 2. Cardiovascular Strain & 24/7 Continuous PPG Curve
        HeartRateCard(
            currentBpm = currentPulseBpm ?: todaySummary?.restingHeartRate,
            restingBpm = todaySummary?.restingHeartRate,
            minBpm = todaySummary?.minHeartRate,
            maxBpm = todaySummary?.maxHeartRate,
            hrSamples = hrSamples
        )

        // 3. TruSleep™ Staging & Sleep Performance
        SleepPerformanceCard(
            sleepSession = latestSleepSession,
            overnightHrvMs = todaySummary?.avgHrv,
            respiratoryRate = 14.6f
        )

        // 4. Blood Oxygenation (SpO2)
        OxygenationCard(
            currentSpo2 = todaySummary?.avgSpo2 ?: 98f,
            minSpo2 = 96f,
            maxSpo2 = 99f
        )

        // 5. Emotional Wellbeing & Autonomic Stress
        EmotionalWellbeingCard(
            stressScore = todaySummary?.stressScore ?: 34,
            latestMood = latestMood,
            onLogMoodClick = { showMoodDialog = true }
        )

        // 6. Body Composition & BMI Tracker
        WeightBmiCard(
            latestWeight = latestWeight,
            onLogWeightClick = { showWeightDialog = true }
        )

        Spacer(modifier = Modifier.height(28.dp))
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
