package com.bandcompanion.app.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.CrimsonStrain
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.ElectricTealRing
import com.bandcompanion.app.ui.theme.HotMagentaRing
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.SampleMetric
import java.util.Calendar

/**
 * Editorial Cardiovascular Strain & Continuous Heart Rate Overview Card.
 * Displays live pulse, resting baseline, daily range (min/max), and a discontinuous
 * 24-hour preview waveform with clear empty gaps for unworn periods (no fake data).
 * Navigates to dedicated Heart Rate Deep Telemetry page via breadcrumbs.
 */
@Composable
fun HeartRateCard(
    currentBpm: Int?,
    restingBpm: Int?,
    minBpm: Int?,
    maxBpm: Int?,
    hrSamples: List<SampleMetric>,
    onOpenDetail: () -> Unit = {},
    modifier: Modifier = Modifier
) {
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

    // Filter valid heart rate samples (48..220 bpm) to eliminate optical artifacts
    val validHrList = remember(hrSamples) {
        hrSamples.filter { it.heartRate != null && it.heartRate!! in 48..220 }
            .sortedBy { it.timestamp }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenDetail() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Category Badge + Navigation Affordance
            Row(
                modifier = Modifier.fillMaxWidth(),
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (restingBpm != null) "Resting $restingBpm bpm" else "24/7 Continuous",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CrimsonStrain
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Heart Rate Details",
                        tint = CrimsonStrain,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main BPM Readout
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                val displayBpm = currentBpm ?: restingBpm
                Text(
                    text = displayBpm?.toString() ?: "--",
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
                        text = if (minBpm != null && maxBpm != null) "$minBpm - $maxBpm bpm" else "-- bpm",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Continuous 24h Area Graph with True Time-Axis & Empty Gaps for Unworn Intervals
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp)
                    .background(ObsidianHighlight, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (validHrList.isNotEmpty()) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val hrValues = validHrList.mapNotNull { it.heartRate }
                        val minVal = (hrValues.minOrNull() ?: 50).toFloat().coerceAtLeast(40f)
                        val maxVal = (hrValues.maxOrNull() ?: 160).toFloat().coerceAtLeast(minVal + 20f)
                        val range = maxVal - minVal

                        // Midnight reference for current day
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.HOUR_OF_DAY, 0)
                        cal.set(Calendar.MINUTE, 0)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        val midnightEpochSec = cal.timeInMillis / 1000L
                        val totalDaySeconds = 86400f

                        // Split into discontinuous segments (gaps > 600s / 10m indicate watch off-wrist)
                        val segments = mutableListOf<MutableList<SampleMetric>>()
                        var currentSegment = mutableListOf<SampleMetric>()

                        for (sample in validHrList) {
                            if (currentSegment.isEmpty()) {
                                currentSegment.add(sample)
                            } else {
                                val prevSample = currentSegment.last()
                                if (sample.timestamp - prevSample.timestamp > 600L) {
                                    segments.add(currentSegment)
                                    currentSegment = mutableListOf(sample)
                                } else {
                                    currentSegment.add(sample)
                                }
                            }
                        }
                        if (currentSegment.isNotEmpty()) {
                            segments.add(currentSegment)
                        }

                        // Draw each continuous segment independently (preserving empty space where unworn)
                        for (segment in segments) {
                            if (segment.size == 1) {
                                val s = segment.first()
                                val secFromMidnight = (s.timestamp - midnightEpochSec).coerceIn(0L, 86400L)
                                val x = (secFromMidnight / totalDaySeconds) * size.width
                                val normY = 1f - ((s.heartRate!! - minVal) / range)
                                val y = normY * (size.height - 12f) + 6f
                                drawCircle(
                                    color = CrimsonStrain,
                                    radius = 3.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            } else {
                                val strokePath = Path()
                                val fillPath = Path()

                                segment.forEachIndexed { idx, s ->
                                    val secFromMidnight = (s.timestamp - midnightEpochSec).coerceIn(0L, 86400L)
                                    val x = (secFromMidnight / totalDaySeconds) * size.width
                                    val normY = 1f - ((s.heartRate!! - minVal) / range)
                                    val y = normY * (size.height - 12f) + 6f

                                    if (idx == 0) {
                                        strokePath.moveTo(x, y)
                                        fillPath.moveTo(x, size.height)
                                        fillPath.lineTo(x, y)
                                    } else {
                                        strokePath.lineTo(x, y)
                                        fillPath.lineTo(x, y)
                                    }
                                }

                                val lastSample = segment.last()
                                val lastSec = (lastSample.timestamp - midnightEpochSec).coerceIn(0L, 86400L)
                                val lastX = (lastSec / totalDaySeconds) * size.width
                                fillPath.lineTo(lastX, size.height)
                                fillPath.close()

                                // Fill gradient under segment
                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            CrimsonStrain.copy(alpha = 0.35f),
                                            CrimsonStrain.copy(alpha = 0.02f)
                                        )
                                    )
                                )

                                // Stroke line for segment
                                drawPath(
                                    path = strokePath,
                                    color = CrimsonStrain,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
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
                            text = "Awaiting Wrist Placement • No Telemetry",
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
                Text(text = "06:00", fontSize = 9.sp, color = TextMuted)
                Text(text = "12:00", fontSize = 9.sp, color = TextMuted)
                Text(text = "18:00", fontSize = 9.sp, color = TextMuted)
                Text(text = "23:59", fontSize = 9.sp, color = TextMuted)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // HR Zone Distribution Mini-Pills
            val hrValues = remember(validHrList) { validHrList.mapNotNull { it.heartRate } }
            val zone1 = remember(hrValues) { if (hrValues.isEmpty()) 0 else (hrValues.count { it in 48..59 } * 100 / hrValues.size) }
            val zone2 = remember(hrValues) { if (hrValues.isEmpty()) 0 else (hrValues.count { it in 60..99 } * 100 / hrValues.size) }
            val zone3 = remember(hrValues) { if (hrValues.isEmpty()) 0 else (hrValues.count { it in 100..139 } * 100 / hrValues.size) }
            val zone4 = remember(hrValues) { if (hrValues.isEmpty()) 0 else (hrValues.count { it >= 140 } * 100 / hrValues.size) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ZoneIndicator(label = "Rest", color = NeonCyanRing, percent = "$zone1%", modifier = Modifier.weight(1f))
                ZoneIndicator(label = "Fat Burn", color = ElectricTealRing, percent = "$zone2%", modifier = Modifier.weight(1f))
                ZoneIndicator(label = "Cardio", color = HotMagentaRing, percent = "$zone3%", modifier = Modifier.weight(1f))
                ZoneIndicator(label = "Peak", color = CrimsonStrain, percent = "$zone4%", modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Affordance: Breadcrumbs / Details link
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianHighlight, RoundedCornerShape(12.dp))
                    .border(0.75.dp, CrimsonStrain.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "EXPLORE 24H INTERACTIVE TELEMETRY & SPIKES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        color = CrimsonStrain
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = CrimsonStrain,
                        modifier = Modifier.size(12.dp)
                    )
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
