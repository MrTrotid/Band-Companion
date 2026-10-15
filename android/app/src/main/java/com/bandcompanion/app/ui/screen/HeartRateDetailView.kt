package com.bandcompanion.app.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.AmberStrain
import com.bandcompanion.app.ui.theme.CrimsonStrain
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.ElectricTealRing
import com.bandcompanion.app.ui.theme.GlassDockBackground
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.HotMagentaRing
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.SampleMetric
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Editorial Deep Telemetry View for Cardiovascular Strain & Heart Rate.
 * Features:
 * - Breadcrumb navigation with top-left back button
 * - Interactive 24-hour Continuous Heart Rate Waveform:
 *   - Accurate time-based X-axis (00:00 to 24:00)
 *   - Broken discontinuous line segments with empty space for unworn intervals (no fake data)
 *   - Labeled Y-axis guide lines (60, 100, 140, 180 BPM)
 *   - Touch-scrubbing & hover: shows exact time, exact BPM, and cardiac zone at spikes
 *   - Off-wrist hover: shows "Watch Off-Wrist (No Data)"
 * - 5-zone cardiac intensity breakdown calculated from real samples
 * - Chronological telemetry log table
 */
@Composable
fun HeartRateDetailView(
    currentBpm: Int?,
    restingBpm: Int?,
    minBpm: Int?,
    maxBpm: Int?,
    hrSamples: List<SampleMetric>,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAnim"
    )

    // Filter valid heart rate samples (48..220 bpm) to eliminate optical artifacts
    val validHrList = remember(hrSamples) {
        hrSamples.filter { it.heartRate != null && it.heartRate!! in 48..220 }
            .sortedBy { it.timestamp }
    }

    // Midnight reference for current day
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val midnightEpochSec = cal.timeInMillis / 1000L
    val totalDaySeconds = 86400f

    // Calculate effective min, max, avg
    val hrValues = remember(validHrList) { validHrList.mapNotNull { it.heartRate } }
    val effectiveMin = minBpm ?: hrValues.minOrNull() ?: 54
    val effectiveMax = maxBpm ?: hrValues.maxOrNull() ?: 138
    val effectiveAvg = if (hrValues.isNotEmpty()) hrValues.average().toInt() else 72

    // Interactive scrub state: fraction from 0f to 1f across the 24h timeline
    var scrubFraction by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Breadcrumbs Bar with Top-Left Back Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassDockBackground)
                    .border(1.dp, GlassDockBorder, RoundedCornerShape(12.dp))
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Health Overview",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "HEALTH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "  /  ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "CARDIOVASCULAR STRAIN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = CrimsonStrain,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "24-Hour Continuous Waveform & Spikes",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // 2. Realtime Vital Badge & Primary Readout Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ObsidianCard)
                .border(1.dp, CrimsonStrain.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                            text = if (currentBpm != null) "LATEST SYNCHRONIZED PULSE" else "AWAITING SYNC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = CrimsonStrain
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(CrimsonStrain.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (restingBpm != null) "Resting $restingBpm bpm" else "PPG Continuous",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CrimsonStrain
                        )
                    }
                }

                // Large BPM value
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val displayBpm = currentBpm ?: restingBpm ?: hrValues.lastOrNull()
                    Text(
                        text = displayBpm?.toString() ?: "--",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BPM",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = CrimsonStrain,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "STATUS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Text(
                            text = when {
                                displayBpm == null -> "Awaiting Wear"
                                displayBpm < 60 -> "Resting Baseline"
                                displayBpm < 100 -> "Normal Aerobic"
                                displayBpm < 140 -> "Cardio Exertion"
                                else -> "Peak Intensity"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (displayBpm != null && displayBpm >= 140) CrimsonStrain else ElectricTealRing
                        )
                    }
                }

                // 4-Column Stat Capsules: RESTING, AVG, MIN NADIR, PEAK
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatPill(label = "RESTING", value = "${restingBpm ?: effectiveMin} bpm", color = NeonCyanRing, modifier = Modifier.weight(1f))
                    StatPill(label = "AVERAGE", value = "$effectiveAvg bpm", color = ElectricTealRing, modifier = Modifier.weight(1f))
                    StatPill(label = "MIN NADIR", value = "$effectiveMin bpm", color = ElectricCyan, modifier = Modifier.weight(1f))
                    StatPill(label = "PEAK SPIKE", value = "$effectiveMax bpm", color = CrimsonStrain, modifier = Modifier.weight(1f))
                }
            }
        }

        // 3. Interactive 24-Hour Heart Rate Waveform with Touch-Scrubbing Tooltips
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ObsidianCard)
                .border(1.dp, GlassDockBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Header & Scrub Instruction
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "24-HOUR CONTINUOUS WAVEFORM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Drag or tap to inspect spikes",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = ElectricCyan
                    )
                }

                // Interactive HUD Card (Displays hovered spike or off-wrist notice)
                val currentScrub = scrubFraction
                if (currentScrub != null) {
                    val scrubSeconds = (currentScrub * totalDaySeconds).toLong()
                    val scrubEpoch = midnightEpochSec + scrubSeconds
                    val scrubTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(scrubEpoch * 1000L))

                    // Find closest sample
                    val closest = validHrList.minByOrNull { abs(it.timestamp - scrubEpoch) }
                    val isNearSample = closest != null && abs(closest.timestamp - scrubEpoch) <= 900L // within 15 mins

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianHighlight)
                            .border(
                                width = 1.dp,
                                color = if (isNearSample) CrimsonStrain.copy(alpha = 0.5f) else TextMuted.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (isNearSample && closest?.heartRate != null) {
                            val bpm = closest.heartRate!!
                            val (zoneName, zoneColor) = when {
                                bpm < 60 -> "Zone 1 • Resting Baseline" to NeonCyanRing
                                bpm < 100 -> "Zone 2 • Aerobic Base" to ElectricTealRing
                                bpm < 140 -> "Zone 3 • Cardio Endurance" to HotMagentaRing
                                else -> "Zone 4/5 • Peak Intensity" to CrimsonStrain
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = scrubTimeStr,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "•  $zoneName",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = zoneColor
                                    )
                                }

                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "$bpm",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "bpm",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CrimsonStrain,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WatchLater,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$scrubTimeStr • Watch Off-Wrist (No Recorded Telemetry)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                // The 24h Canvas with discontinuous segments and touch gestures
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianHighlight)
                        .pointerInput(validHrList) {
                            detectTapGestures { offset ->
                                scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(validHrList) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    scrubFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                }
                            )
                        }
                ) {
                    if (validHrList.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)) {
                            val minVal = 40f
                            val maxVal = 180f
                            val range = maxVal - minVal

                            // Draw horizontal Y-axis guide lines: 60, 100, 140, 180 bpm
                            val guidelines = listOf(60f, 100f, 140f, 180f)
                            for (guide in guidelines) {
                                val normY = 1f - ((guide - minVal) / range)
                                val y = normY * size.height
                                drawLine(
                                    color = TextMuted.copy(alpha = 0.15f),
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            }

                            // Group into continuous segments (gap > 600s = off-wrist gap)
                            val segments = mutableListOf<MutableList<SampleMetric>>()
                            var currentSegment = mutableListOf<SampleMetric>()

                            for (sample in validHrList) {
                                if (currentSegment.isEmpty()) {
                                    currentSegment.add(sample)
                                } else {
                                    val prev = currentSegment.last()
                                    if (sample.timestamp - prev.timestamp > 600L) {
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

                            // Draw each segment independently
                            for (segment in segments) {
                                if (segment.size == 1) {
                                    val s = segment.first()
                                    val secFromMidnight = (s.timestamp - midnightEpochSec).coerceIn(0L, 86400L)
                                    val x = (secFromMidnight / totalDaySeconds) * size.width
                                    val normY = 1f - ((s.heartRate!! - minVal) / range)
                                    val y = normY * size.height
                                    drawCircle(
                                        color = CrimsonStrain,
                                        radius = 4.dp.toPx(),
                                        center = Offset(x, y)
                                    )
                                } else {
                                    val strokePath = Path()
                                    val fillPath = Path()

                                    segment.forEachIndexed { idx, s ->
                                        val secFromMidnight = (s.timestamp - midnightEpochSec).coerceIn(0L, 86400L)
                                        val x = (secFromMidnight / totalDaySeconds) * size.width
                                        val normY = 1f - ((s.heartRate!! - minVal) / range)
                                        val y = normY * size.height

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

                                    // Gradient fill under continuous curve
                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                CrimsonStrain.copy(alpha = 0.35f),
                                                CrimsonStrain.copy(alpha = 0.02f)
                                            )
                                        )
                                    )

                                    // Stroke path
                                    drawPath(
                                        path = strokePath,
                                        color = CrimsonStrain,
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )
                                }
                            }

                            // If scrubbed: draw vertical indicator cursor line and highlight dot
                            scrubFraction?.let { frac ->
                                val cursorX = frac * size.width
                                drawLine(
                                    color = ElectricCyan,
                                    start = Offset(cursorX, 0f),
                                    end = Offset(cursorX, size.height),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                )

                                val scrubSec = (frac * totalDaySeconds).toLong()
                                val scrubEpoch = midnightEpochSec + scrubSec
                                val closestSample = validHrList.minByOrNull { abs(it.timestamp - scrubEpoch) }
                                if (closestSample != null && abs(closestSample.timestamp - scrubEpoch) <= 900L) {
                                    val sampleSec = (closestSample.timestamp - midnightEpochSec).coerceIn(0L, 86400L)
                                    val ptX = (sampleSec / totalDaySeconds) * size.width
                                    val normY = 1f - ((closestSample.heartRate!! - minVal) / range)
                                    val ptY = normY * size.height

                                    // Outer glowing ring
                                    drawCircle(
                                        color = NeonCyanRing.copy(alpha = 0.5f),
                                        radius = 8.dp.toPx(),
                                        center = Offset(ptX, ptY)
                                    )
                                    // Solid inner dot
                                    drawCircle(
                                        color = CrimsonStrain,
                                        radius = 4.dp.toPx(),
                                        center = Offset(ptX, ptY)
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
                                tint = CrimsonStrain.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Awaiting Wrist Placement • No Recorded Heart Rate",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextMuted
                            )
                        }
                    }
                }

                // 24-Hour Time Axis Labels
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "00:00", fontSize = 10.sp, color = TextMuted)
                    Text(text = "04:00", fontSize = 10.sp, color = TextMuted)
                    Text(text = "08:00", fontSize = 10.sp, color = TextMuted)
                    Text(text = "12:00", fontSize = 10.sp, color = TextMuted)
                    Text(text = "16:00", fontSize = 10.sp, color = TextMuted)
                    Text(text = "20:00", fontSize = 10.sp, color = TextMuted)
                    Text(text = "24:00", fontSize = 10.sp, color = TextMuted)
                }
            }
        }

        // 4. Five Cardiac Zones Breakdown (Huawei Health / Whoop Standards)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ObsidianCard)
                .border(1.dp, GlassDockBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "CARDIAC TRAINING ZONES BREAKDOWN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = CrimsonStrain
                )

                val z1Count = hrValues.count { it in 48..99 }
                val z2Count = hrValues.count { it in 100..119 }
                val z3Count = hrValues.count { it in 120..139 }
                val z4Count = hrValues.count { it in 140..159 }
                val z5Count = hrValues.count { it >= 160 }
                val totalCount = hrValues.size.coerceAtLeast(1)

                DetailedZoneRow(
                    name = "Zone 5 • Extreme / VO2 Max (>160 bpm)",
                    duration = "${z5Count}m (${(z5Count * 100) / totalCount}%)",
                    color = CrimsonStrain,
                    pct = z5Count.toFloat() / totalCount
                )
                DetailedZoneRow(
                    name = "Zone 4 • Anaerobic / Threshold (140–160 bpm)",
                    duration = "${z4Count}m (${(z4Count * 100) / totalCount}%)",
                    color = AmberStrain,
                    pct = z4Count.toFloat() / totalCount
                )
                DetailedZoneRow(
                    name = "Zone 3 • Aerobic Endurance (120–140 bpm)",
                    duration = "${z3Count}m (${(z3Count * 100) / totalCount}%)",
                    color = HotMagentaRing,
                    pct = z3Count.toFloat() / totalCount
                )
                DetailedZoneRow(
                    name = "Zone 2 • Fat Burn / Aerobic Base (100–120 bpm)",
                    duration = "${z2Count}m (${(z2Count * 100) / totalCount}%)",
                    color = ElectricTealRing,
                    pct = z2Count.toFloat() / totalCount
                )
                DetailedZoneRow(
                    name = "Zone 1 • Warm-up / Rest (<100 bpm)",
                    duration = "${z1Count}m (${(z1Count * 100) / totalCount}%)",
                    color = NeonCyanRing,
                    pct = z1Count.toFloat() / totalCount
                )
            }
        }

        // 5. Chronological Telemetry Log Table
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ObsidianCard)
                .border(1.dp, GlassDockBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "CHRONOLOGICAL TELEMETRY LOG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )

                val sortedSamples = remember(validHrList) { validHrList.sortedByDescending { it.timestamp } }
                if (sortedSamples.isEmpty()) {
                    Text(
                        text = "Awaiting live telemetry packets from band...",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (sample in sortedSamples.take(30)) {
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
                                    .background(ObsidianHighlight, RoundedCornerShape(10.dp))
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

        Spacer(modifier = Modifier.height(110.dp))
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianHighlight)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun DetailedZoneRow(
    name: String,
    duration: String,
    color: Color,
    pct: Float
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            Text(text = duration, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(ObsidianHighlight, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = pct.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}
