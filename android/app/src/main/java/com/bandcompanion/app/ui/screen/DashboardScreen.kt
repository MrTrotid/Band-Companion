package com.bandcompanion.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.NightsStay
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onSyncClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Device Status Header
        DeviceHeaderCard(
            deviceInfo = deviceInfo,
            isSyncing = isSyncing,
            onSyncClick = onSyncClick
        )

        // Recovery & Strain Dual Panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RecoveryCard(
                recoveryScore = todaySummary?.recoveryScore ?: 82,
                modifier = Modifier.weight(1f)
            )
            StrainCard(
                dayStrain = todaySummary?.dayStrain ?: 11.4f,
                modifier = Modifier.weight(1f)
            )
        }

        // ACWR Workload Status
        AcwrCard(
            acuteLoad = todaySummary?.acuteLoad ?: 12.1f,
            chronicLoad = todaySummary?.chronicLoad ?: 11.8f,
            ratio = todaySummary?.acwrRatio ?: 1.03f
        )

        // Today's Telemetry Grid
        Text(
            text = "TODAY'S BIOMETRICS",
            style = MaterialTheme.typography.labelMedium,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        TelemetryGrid(summary = todaySummary)

        Spacer(modifier = Modifier.height(24.dp))
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
                    Icon(
                        imageVector = Icons.Default.BluetoothConnected,
                        contentDescription = null,
                        tint = ElectricCyan
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = deviceInfo?.name ?: "HUAWEI Band 10",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BatteryFull,
                            contentDescription = null,
                            tint = NeonTeal,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${deviceInfo?.batteryLevel ?: 92}% • Connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Button(
                onClick = onSyncClick,
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceVariantDark,
                    contentColor = ElectricCyan
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = ElectricCyan
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Sync", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun RecoveryCard(recoveryScore: Int, modifier: Modifier = Modifier) {
    val ringColor = when {
        recoveryScore >= 67 -> NeonTeal
        recoveryScore >= 34 -> AmberStrain
        else -> CoralRed
    }

    Card(
        modifier = modifier.height(180.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "RECOVERY",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                Canvas(modifier = Modifier.size(80.dp)) {
                    // Background track
                    drawCircle(
                        color = SurfaceVariantDark,
                        radius = size.minDimension / 2,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Progress arc
                    val sweep = (recoveryScore / 100f) * 360f
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$recoveryScore%",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Text(
                text = if (recoveryScore >= 67) "Optimal" else "Moderate",
                fontSize = 12.sp,
                color = ringColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StrainCard(dayStrain: Float, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(180.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "DAY STRAIN",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(90.dp)
            ) {
                Canvas(modifier = Modifier.size(80.dp)) {
                    drawCircle(
                        color = SurfaceVariantDark,
                        radius = size.minDimension / 2,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    val sweep = (dayStrain / 21f) * 360f
                    drawArc(
                        color = AmberStrain,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = String.format("%.1f", dayStrain),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Text(
                text = "Target: 10 - 14",
                fontSize = 12.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AcwrCard(acuteLoad: Float, chronicLoad: Float, ratio: Float) {
    val statusColor = if (ratio in 0.8f..1.3f) NeonTeal else AmberStrain
    val statusLabel = if (ratio in 0.8f..1.3f) "Optimal Workload" else "Elevated Load"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ACWR (ACUTE : CHRONIC)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = statusLabel,
                    fontSize = 12.sp,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = String.format("%.2f", ratio),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Sweet spot: 0.80 - 1.30",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "7d Acute", fontSize = 11.sp, color = TextMuted)
                        Text(text = "$acuteLoad", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "42d Chronic", fontSize = 11.sp, color = TextMuted)
                        Text(text = "$chronicLoad", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryGrid(summary: DailySummary?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricTile(
            title = "Steps",
            value = "${summary?.totalSteps ?: 2075}",
            sub = "Goal 10,000",
            icon = Icons.Default.Speed,
            color = ElectricCyan,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            title = "Resting HR",
            value = "${summary?.restingHeartRate ?: 56} bpm",
            sub = "Baseline: 55-60",
            icon = Icons.Default.Favorite,
            color = CoralRed,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricTile(
            title = "Sleep Duration",
            value = formatMinutes(summary?.totalSleepMinutes ?: 455),
            sub = "Efficiency 86%",
            icon = Icons.Default.NightsStay,
            color = NeonTeal,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            title = "Avg SpO2",
            value = "${(summary?.avgSpo2 ?: 96.7f).toInt()}%",
            sub = "Normal: 95-100%",
            icon = Icons.Default.Favorite,
            color = ElectricCyan,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Medium)
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = sub, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "${h}h ${m}m"
}
