package com.bandcompanion.app.ui.screen

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.AmberStrain
import com.bandcompanion.app.ui.theme.CoralRed
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.SleepAwakeColor
import com.bandcompanion.app.ui.theme.SleepDeepColor
import com.bandcompanion.app.ui.theme.SleepLightColor
import com.bandcompanion.app.ui.theme.SleepRemColor
import com.bandcompanion.app.ui.theme.SurfaceDark
import com.bandcompanion.app.ui.theme.SurfaceVariantDark
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.DailySummary
import com.bandcompanion.core.model.DeviceInfo

@Composable
fun DashboardScreen(
    deviceInfo: DeviceInfo?,
    todaySummary: DailySummary?,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    onPairClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val isBonded = deviceInfo?.isBonded == true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Setup / Pair Banner if not bonded
        if (!isBonded) {
            UnbondedSetupCard(onPairClick = onPairClick)
        } else {
            // Connected Device Status Header
            DeviceHeaderCard(
                deviceInfo = deviceInfo,
                isSyncing = isSyncing,
                onSyncClick = onSyncClick
            )
        }

        // Huawei Health Tri-Ring Activity Card
        HuaweiTriRingActivityCard(
            steps = todaySummary?.totalSteps ?: 0,
            stepGoal = 10000,
            activeMinutes = (todaySummary?.totalSteps ?: 0) / 100,
            activeMinGoal = 30,
            calories = (todaySummary?.totalSteps ?: 0) / 25,
            calGoal = 400
        )

        // Huawei Health TruSleep Card
        HuaweiSleepCard(
            totalSleepMinutes = todaySummary?.totalSleepMinutes ?: 0,
            deepMinutes = todaySummary?.deepSleepMinutes ?: 0,
            remMinutes = todaySummary?.remSleepMinutes ?: 0,
            lightMinutes = todaySummary?.lightSleepMinutes ?: 0,
            awakeMinutes = todaySummary?.awakeMinutes ?: 0,
            sleepScore = todaySummary?.recoveryScore
        )

        // Heart Rate Card (Daily Range & Resting HR)
        HuaweiHeartRateCard(
            restingHr = todaySummary?.restingHeartRate,
            minHr = todaySummary?.minHeartRate ?: 47,
            maxHr = todaySummary?.maxHeartRate ?: 166
        )

        // Dual SpO2 & Strain Tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HuaweiSpo2Card(
                avgSpo2 = todaySummary?.avgSpo2,
                modifier = Modifier.weight(1f)
            )
            HuaweiStrainCard(
                strain = todaySummary?.dayStrain,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun UnbondedSetupCard(onPairClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPairClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
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
                        .background(ElectricCyan.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.AddLink, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(text = "Connect Huawei Band 10", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "Tap here to scan and pair your watch", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Button(
                onClick = onPairClick,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Pair", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun DeviceHeaderCard(
    deviceInfo: DeviceInfo?,
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
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
                        .background(SurfaceVariantDark, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.BluetoothConnected, contentDescription = null, tint = ElectricCyan)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = deviceInfo?.name ?: "HUAWEI Band 10", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.BatteryFull, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "${deviceInfo?.batteryLevel ?: 92}% • Connected", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            Button(
                onClick = onSyncClick,
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariantDark, contentColor = ElectricCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ElectricCyan)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Sync", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Sync", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun HuaweiTriRingActivityCard(
    steps: Int,
    stepGoal: Int,
    activeMinutes: Int,
    activeMinGoal: Int,
    calories: Int,
    calGoal: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "ACTIVITY RINGS", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                Text(text = "Daily Target", fontSize = 11.sp, color = NeonTeal, fontWeight = FontWeight.Medium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Concentric 3-Ring Canvas
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                    Canvas(modifier = Modifier.size(110.dp)) {
                        val strokeW = 7.dp.toPx()
                        val pad = 12.dp.toPx()

                        // Outer: Steps (Cyan)
                        drawArc(color = SurfaceVariantDark, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(strokeW, cap = StrokeCap.Round), topLeft = Offset(0f, 0f), size = Size(size.width, size.height))
                        val sweepSteps = (steps.toFloat() / stepGoal).coerceIn(0f, 1f) * 360f
                        drawArc(color = ElectricCyan, startAngle = -90f, sweepAngle = sweepSteps, useCenter = false, style = Stroke(strokeW, cap = StrokeCap.Round), topLeft = Offset(0f, 0f), size = Size(size.width, size.height))

                        // Middle: Active Minutes (Amber)
                        val mSize = Size(size.width - pad * 2, size.height - pad * 2)
                        drawArc(color = SurfaceVariantDark, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(strokeW, cap = StrokeCap.Round), topLeft = Offset(pad, pad), size = mSize)
                        val sweepMin = (activeMinutes.toFloat() / activeMinGoal).coerceIn(0f, 1f) * 360f
                        drawArc(color = AmberStrain, startAngle = -90f, sweepAngle = sweepMin, useCenter = false, style = Stroke(strokeW, cap = StrokeCap.Round), topLeft = Offset(pad, pad), size = mSize)

                        // Inner: Active Calories (Coral)
                        val iSize = Size(size.width - pad * 4, size.height - pad * 4)
                        drawArc(color = SurfaceVariantDark, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(strokeW, cap = StrokeCap.Round), topLeft = Offset(pad * 2, pad * 2), size = iSize)
                        val sweepCal = (calories.toFloat() / calGoal).coerceIn(0f, 1f) * 360f
                        drawArc(color = CoralRed, startAngle = -90f, sweepAngle = sweepCal, useCenter = false, style = Stroke(strokeW, cap = StrokeCap.Round), topLeft = Offset(pad * 2, pad * 2), size = iSize)
                    }
                }

                // Legend and Numbers
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActivityMetricRow(icon = Icons.Default.DirectionsRun, color = ElectricCyan, label = "Steps", value = "$steps", goal = "/ $stepGoal")
                    ActivityMetricRow(icon = Icons.Default.Timer, color = AmberStrain, label = "Active Mins", value = "${activeMinutes}m", goal = "/ ${activeMinGoal}m")
                    ActivityMetricRow(icon = Icons.Default.LocalFireDepartment, color = CoralRed, label = "Calories", value = "${calories} kcal", goal = "/ ${calGoal}")
                }
            }
        }
    }
}

@Composable
fun ActivityMetricRow(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, label: String, value: String, goal: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(24.dp).background(color.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = label, fontSize = 11.sp, color = TextMuted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = goal, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
fun HuaweiSleepCard(
    totalSleepMinutes: Int,
    deepMinutes: Int,
    remMinutes: Int,
    lightMinutes: Int,
    awakeMinutes: Int,
    sleepScore: Int?
) {
    val total = totalSleepMinutes.coerceAtLeast(1)
    val score = sleepScore ?: 85

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Bedtime, contentDescription = null, tint = SleepLightColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "TRUESLEEP™", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .background(NeonTeal.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(text = "$score Normal", fontSize = 11.sp, color = NeonTeal, fontWeight = FontWeight.Bold)
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                val h = totalSleepMinutes / 60
                val m = totalSleepMinutes % 60
                Text(text = "${h}h ${m}m", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Total Sleep", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            }

            // Stacked sleep stages proportion bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(SurfaceVariantDark, RoundedCornerShape(4.dp))
            ) {
                if (deepMinutes > 0) {
                    Box(modifier = Modifier.weight((deepMinutes.toFloat() / total).coerceAtLeast(0.05f)).height(8.dp).background(SleepDeepColor, RoundedCornerShape(4.dp)))
                }
                if (remMinutes > 0) {
                    Box(modifier = Modifier.weight((remMinutes.toFloat() / total).coerceAtLeast(0.05f)).height(8.dp).background(SleepRemColor))
                }
                if (lightMinutes > 0) {
                    Box(modifier = Modifier.weight((lightMinutes.toFloat() / total).coerceAtLeast(0.05f)).height(8.dp).background(SleepLightColor))
                }
                if (awakeMinutes > 0) {
                    Box(modifier = Modifier.weight((awakeMinutes.toFloat() / total).coerceAtLeast(0.02f)).height(8.dp).background(SleepAwakeColor))
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SleepStageLabel("Deep", deepMinutes, SleepDeepColor)
                SleepStageLabel("REM", remMinutes, SleepRemColor)
                SleepStageLabel("Light", lightMinutes, SleepLightColor)
                SleepStageLabel("Awake", awakeMinutes, SleepAwakeColor)
            }
        }
    }
}

@Composable
fun SleepStageLabel(label: String, min: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "$label ${min}m", fontSize = 10.sp, color = TextSecondary)
    }
}

@Composable
fun HuaweiHeartRateCard(restingHr: Int?, minHr: Int, maxHr: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = CoralRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "HEART RATE", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                }
                Text(text = "Range: $minHr - $maxHr bpm", fontSize = 11.sp, color = TextMuted)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(text = "${restingHr ?: 56}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "Resting Heart Rate (bpm)", fontSize = 12.sp, color = TextSecondary)
                }

                // Decorative wave curve
                Box(modifier = Modifier.width(120.dp).height(36.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(0f, size.height * 0.7f)
                        path.quadraticBezierTo(size.width * 0.25f, size.height * 0.2f, size.width * 0.5f, size.height * 0.6f)
                        path.quadraticBezierTo(size.width * 0.75f, size.height * 0.9f, size.width, size.height * 0.3f)
                        drawPath(path, color = CoralRed, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}

@Composable
fun HuaweiSpo2Card(avgSpo2: Float?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "SpO2", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Icon(imageVector = Icons.Default.WaterDrop, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
            }
            Text(text = "${(avgSpo2 ?: 97f).toInt()}%", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = "Normal: 95% - 100%", fontSize = 11.sp, color = NeonTeal)
        }
    }
}

@Composable
fun HuaweiStrainCard(strain: Float?, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Day Strain", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = AmberStrain, modifier = Modifier.size(16.dp))
            }
            Text(text = String.format("%.1f", strain ?: 11.2f), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = "Banister TRIMP Load", fontSize = 11.sp, color = TextSecondary)
        }
    }
}
