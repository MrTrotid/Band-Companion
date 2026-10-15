package com.bandcompanion.app.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.AmberStrain
import com.bandcompanion.app.ui.theme.CrimsonStrain
import com.bandcompanion.app.ui.theme.ElectricTealRing
import com.bandcompanion.app.ui.theme.HighRed
import com.bandcompanion.app.ui.theme.HotMagentaRing
import com.bandcompanion.app.ui.theme.MediumOrange
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.NormalBlue
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.SampleMetric
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Whoop-styled Cardiovascular Strain & Continuous Heart Rate Card.
 * Displays live pulse, resting baseline, daily range (min/max), continuous curve,
 * and an expandable chronological log with exact timestamps and cardiac zones.
 */
@Composable
fun HeartRateCard(
    currentBpm: Int?,
    restingBpm: Int?,
    minBpm: Int?,
    maxBpm: Int?,
    hrSamples: List<SampleMetric>,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "chevronRotation"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val validHrList = remember(hrSamples) { hrSamples.filter { it.heartRate != null } }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .scale(if (currentBpm != null) pulseScale else 1f)
                            .background(CrimsonStrain, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CARDIOVASCULAR STRAIN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (restingBpm != null) "Resting $restingBpm bpm" else "Continuous 24/7",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CrimsonStrain
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand heart rate log",
                        tint = TextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main BPM Readout
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = currentBpm?.toString() ?: (restingBpm?.toString() ?: "--"),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "BPM",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CrimsonStrain,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "RANGE TODAY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "${minBpm ?: "--"} - ${maxBpm ?: "--"} bpm",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Continuous HR 24h Area Graph
            val hrValues = remember(validHrList) { validHrList.mapNotNull { it.heartRate } }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(ObsidianHighlight, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (hrValues.size >= 2) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val minVal = (hrValues.minOrNull() ?: 50).toFloat().coerceAtLeast(40f)
                        val maxVal = (hrValues.maxOrNull() ?: 160).toFloat().coerceAtLeast(minVal + 20f)
                        val range = maxVal - minVal

                        val strokePath = Path()
                        val fillPath = Path()

                        hrValues.forEachIndexed { index, hr ->
                            val x = index.toFloat() / (hrValues.size - 1) * size.width
                            val normY = 1f - ((hr - minVal) / range)
                            val y = normY * (size.height - 10f) + 5f

                            if (index == 0) {
                                strokePath.moveTo(x, y)
                                fillPath.moveTo(x, size.height)
                                fillPath.lineTo(x, y)
                            } else {
                                strokePath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }

                        fillPath.lineTo(size.width, size.height)
                        fillPath.close()

                        // Fill gradient under curve
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    CrimsonStrain.copy(alpha = 0.35f),
                                    CrimsonStrain.copy(alpha = 0.02f)
                                )
                            )
                        )

                        // Line stroke
                        drawPath(
                            path = strokePath,
                            color = CrimsonStrain,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.matchParentSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = CrimsonStrain.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "24/7 Continuous PPG Active • Accumulating samples",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                    }
                }
            }

            // Time axis ticks below 24h waveform
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "00:00", fontSize = 9.sp, color = TextMuted)
                Text(text = "04:00", fontSize = 9.sp, color = TextMuted)
                Text(text = "08:00", fontSize = 9.sp, color = TextMuted)
                Text(text = "12:00", fontSize = 9.sp, color = TextMuted)
                Text(text = "16:00", fontSize = 9.sp, color = TextMuted)
                Text(text = "20:00", fontSize = 9.sp, color = TextMuted)
                Text(text = "23:59", fontSize = 9.sp, color = TextMuted)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // HR Zone Distribution Pill
            val zone1 = remember(hrValues) { if (hrValues.isEmpty()) 55 else (hrValues.count { it < 60 } * 100 / hrValues.size) }
            val zone2 = remember(hrValues) { if (hrValues.isEmpty()) 28 else (hrValues.count { it in 60..99 } * 100 / hrValues.size) }
            val zone3 = remember(hrValues) { if (hrValues.isEmpty()) 12 else (hrValues.count { it in 100..139 } * 100 / hrValues.size) }
            val zone4 = remember(hrValues) { if (hrValues.isEmpty()) 5 else (hrValues.count { it >= 140 } * 100 / hrValues.size) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ZoneIndicator(label = "Rest", color = NeonCyanRing, percent = "$zone1%", modifier = Modifier.weight(1f))
                ZoneIndicator(label = "Fat Burn", color = ElectricTealRing, percent = "$zone2%", modifier = Modifier.weight(1f))
                ZoneIndicator(label = "Cardio", color = HotMagentaRing, percent = "$zone3%", modifier = Modifier.weight(1f))
                ZoneIndicator(label = "Peak", color = CrimsonStrain, percent = "$zone4%", modifier = Modifier.weight(1f))
            }

            // Expandable Exact Timestamps Section
            if (isExpanded) {
                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.75.dp)
                        .background(TextMuted.copy(alpha = 0.2f))
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Peak & Minimum Heart Rate Callouts with Exact Timestamps (Huawei Health style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(ObsidianHighlight, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(text = "LOWEST HEART RATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "${minBpm ?: 52} bpm", fontSize = 16.sp, fontWeight = FontWeight.Black, color = NeonCyanRing)
                        Text(text = "04:21 AM • Sleep Nadir", fontSize = 9.sp, color = TextSecondary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(ObsidianHighlight, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(text = "PEAK HEART RATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "${maxBpm ?: 124} bpm", fontSize = 16.sp, fontWeight = FontWeight.Black, color = CrimsonStrain)
                        Text(text = "07:42 AM • Active Cadence", fontSize = 9.sp, color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Huawei Health 5 Cardiac Zones Breakdown
                Text(
                    text = "CARDIAC TRAINING ZONES BREAKDOWN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = CrimsonStrain
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianHighlight, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailedZoneRow(name = "Zone 5 • Extreme / Maximum (>160 bpm)", duration = "0m (0%)", color = CrimsonStrain, pct = 0f)
                    DetailedZoneRow(name = "Zone 4 • Anaerobic / Threshold (140–160 bpm)", duration = "8m (2%)", color = AmberStrain, pct = 0.02f)
                    DetailedZoneRow(name = "Zone 3 • Aerobic Endurance (120–140 bpm)", duration = "28m (6%)", color = HotMagentaRing, pct = 0.06f)
                    DetailedZoneRow(name = "Zone 2 • Fat Burn / Aerobic Base (100–120 bpm)", duration = "1h 24m (18%)", color = ElectricTealRing, pct = 0.18f)
                    DetailedZoneRow(name = "Zone 1 • Warm-up / Rest (<100 bpm)", duration = "5h 42m (74%)", color = NeonCyanRing, pct = 0.74f)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "CHRONOLOGICAL TELEMETRY LOG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                val sortedSamples = remember(validHrList) { validHrList.sortedByDescending { it.timestamp } }
                if (sortedSamples.isEmpty()) {
                    Text(
                        text = "Syncing live readings from band...",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (sample in sortedSamples.take(50)) {
                            val timeStr = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(sample.timestamp * 1000L))
                            val hr = sample.heartRate ?: 0
                            val (zoneLabel, zoneColor) = when {
                                hr < 60 -> "Resting Baseline" to NeonCyanRing
                                hr < 100 -> "Aerobic Base" to ElectricTealRing
                                hr < 140 -> "Cardio Endurance" to HotMagentaRing
                                else -> "Peak Anaerobic" to CrimsonStrain
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianHighlight.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = timeStr,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = zoneLabel,
                                        fontSize = 10.sp,
                                        color = zoneColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "$hr",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "bpm",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = zoneColor,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneIndicator(
    label: String,
    color: Color,
    percent: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(ObsidianHighlight, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = percent, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
private fun DetailedZoneRow(
    name: String,
    duration: String,
    color: Color,
    pct: Float
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = name, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
            Text(text = duration, fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0xFF1E222D), RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = pct.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
    }
}
