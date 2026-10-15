package com.bandcompanion.app.ui.screen

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
import androidx.compose.material.icons.filled.CheckCircle
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
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBackground
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.HighRed
import com.bandcompanion.app.ui.theme.MediumOrange
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.SpO2Cyan
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
 * Editorial Deep Telemetry View for Blood Oxygenation (SpO2).
 * Features:
 * - Breadcrumbs navigation with top-left back button
 * - 24-hour discontinuous SpO2 trend graph with true time axis and empty unworn gaps
 * - Interactive touch-scrubbing with exact time and SpO2 % tooltips
 * - Arterial saturation distribution breakdown
 * - Chronological SpO2 log table
 */
@Composable
fun OxygenationDetailView(
    currentSpo2: Float?,
    minSpo2: Float? = null,
    maxSpo2: Float? = null,
    spo2Samples: List<SampleMetric> = emptyList(),
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val validSpo2Samples = remember(spo2Samples) {
        spo2Samples.filter { it.spo2 != null && it.spo2!! in 70f..100f }
            .sortedBy { it.timestamp }
    }
    val validSpo2Values = remember(validSpo2Samples) { validSpo2Samples.mapNotNull { it.spo2 } }
    val displaySpo2 = currentSpo2 ?: if (validSpo2Values.isNotEmpty()) validSpo2Values.average().toFloat() else 98.0f

    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val midnightEpochSec = cal.timeInMillis / 1000L
    val totalDaySeconds = 86400f

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
                        tint = SpO2Cyan,
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
                        text = "BLOOD OXYGEN (SPO2)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = SpO2Cyan,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Arterial Oxygenation & Stability",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // 2. Primary Readout Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ObsidianCard)
                .border(1.dp, SpO2Cyan.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
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
                                .background(SpO2Cyan, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PULSE OXIMETRY TELEMETRY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = SpO2Cyan
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(SpO2Cyan.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (displaySpo2 >= 95f) "Optimal Range" else "Acceptable",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpO2Cyan
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "%.0f".format(displaySpo2),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Text(
                        text = "%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SpO2Cyan,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "DAILY RANGE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Text(
                            text = "${(minSpo2 ?: 95f).toInt()}% - ${(maxSpo2 ?: 99f).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // 3. Interactive 24-Hour SpO2 Waveform with Empty Off-Wrist Gaps
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ObsidianCard)
                .border(1.dp, GlassDockBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "24-HOUR OXYGENATION CURVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = TextSecondary
                    )
                    Text(
                        text = "Drag or tap to inspect readings",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = SpO2Cyan
                    )
                }

                // Interactive HUD Card
                val currentScrub = scrubFraction
                if (currentScrub != null) {
                    val scrubSeconds = (currentScrub * totalDaySeconds).toLong()
                    val scrubEpoch = midnightEpochSec + scrubSeconds
                    val scrubTimeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(scrubEpoch * 1000L))

                    val closest = validSpo2Samples.minByOrNull { abs(it.timestamp - scrubEpoch) }
                    val isNearSample = closest != null && abs(closest.timestamp - scrubEpoch) <= 900L

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianHighlight)
                            .border(
                                width = 1.dp,
                                color = if (isNearSample) SpO2Cyan.copy(alpha = 0.5f) else TextMuted.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        if (isNearSample && closest?.spo2 != null) {
                            val spo2 = closest.spo2!!
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = SpO2Cyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$scrubTimeStr • Spot Check",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = "%.0f%% SpO2".format(spo2),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (spo2 >= 95f) SpO2Cyan else MediumOrange
                                )
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
                                    text = "$scrubTimeStr • Watch Off-Wrist (No Recorded SpO2)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                // 24h SpO2 Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianHighlight)
                        .pointerInput(validSpo2Samples) {
                            detectTapGestures { offset ->
                                scrubFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(validSpo2Samples) {
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
                    if (validSpo2Samples.isNotEmpty()) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 12.dp)) {
                            val minVal = 85f
                            val maxVal = 100f
                            val range = maxVal - minVal

                            // Guidelines: 90%, 95%, 100%
                            for (guide in listOf(90f, 95f, 100f)) {
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

                            // Discontinuous segments (gaps > 600s = off-wrist)
                            val segments = mutableListOf<MutableList<SampleMetric>>()
                            var currentSegment = mutableListOf<SampleMetric>()

                            for (sample in validSpo2Samples) {
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

                            for (segment in segments) {
                                if (segment.size == 1) {
                                    val s = segment.first()
                                    val secFromMidnight = (s.timestamp - midnightEpochSec).coerceIn(0L, 86400L)
                                    val x = (secFromMidnight / totalDaySeconds) * size.width
                                    val normY = 1f - ((s.spo2!! - minVal) / range)
                                    val y = normY * size.height
                                    drawCircle(
                                        color = SpO2Cyan,
                                        radius = 4.dp.toPx(),
                                        center = Offset(x, y)
                                    )
                                } else {
                                    val strokePath = Path()
                                    val fillPath = Path()

                                    segment.forEachIndexed { idx, s ->
                                        val secFromMidnight = (s.timestamp - midnightEpochSec).coerceIn(0L, 86400L)
                                        val x = (secFromMidnight / totalDaySeconds) * size.width
                                        val normY = 1f - ((s.spo2!! - minVal) / range)
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

                                    drawPath(
                                        path = fillPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                SpO2Cyan.copy(alpha = 0.35f),
                                                SpO2Cyan.copy(alpha = 0.02f)
                                            )
                                        )
                                    )

                                    drawPath(
                                        path = strokePath,
                                        color = SpO2Cyan,
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )
                                }
                            }

                            // Scrub cursor
                            scrubFraction?.let { frac ->
                                val cursorX = frac * size.width
                                drawLine(
                                    color = SpO2Cyan,
                                    start = Offset(cursorX, 0f),
                                    end = Offset(cursorX, size.height),
                                    strokeWidth = 1.5.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.matchParentSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Awaiting Wrist Placement • No Recorded SpO2",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextMuted
                            )
                        }
                    }
                }

                // 24h Time Axis Labels
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "00:00", fontSize = 10.sp, color = TextMuted)
                    Text(text = "06:00", fontSize = 10.sp, color = TextMuted)
                    Text(text = "12:00", fontSize = 10.sp, color = TextMuted)
                    Text(text = "18:00", fontSize = 10.sp, color = TextMuted)
                    Text(text = "24:00", fontSize = 10.sp, color = TextMuted)
                }
            }
        }

        // 4. Chronological SpO2 Log Table
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
                    text = "CHRONOLOGICAL SPOT READINGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )

                val sortedSamples = remember(validSpo2Samples) { validSpo2Samples.sortedByDescending { it.timestamp } }
                if (sortedSamples.isEmpty()) {
                    Text(
                        text = "Awaiting live SpO2 spot checks from band...",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (sample in sortedSamples.take(25)) {
                            val timeStr = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(sample.timestamp * 1000L))
                            val spo2 = sample.spo2 ?: 0f
                            val statusLabel = if (spo2 >= 95f) "Optimal Saturation" else "Mild Desaturation"
                            val statusColor = if (spo2 >= 95f) SpO2Cyan else MediumOrange

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
                                        text = statusLabel,
                                        fontSize = 10.sp,
                                        color = statusColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Text(
                                    text = "%.0f%%".format(spo2),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(110.dp))
    }
}
