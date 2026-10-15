package com.bandcompanion.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.bandcompanion.app.ui.theme.CrimsonStrain
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.SleepAwakeColor
import com.bandcompanion.app.ui.theme.SleepDeepColor
import com.bandcompanion.app.ui.theme.SleepLightColor
import com.bandcompanion.app.ui.theme.SleepRemColor
import com.bandcompanion.app.ui.theme.SpO2Cyan
import com.bandcompanion.app.ui.theme.SurfaceDark
import com.bandcompanion.app.ui.theme.SurfaceVariantDark
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.SleepSession
import com.bandcompanion.core.model.SleepStageInterval
import com.bandcompanion.core.model.SleepStageType

@Composable
fun SleepDetailScreen(
    session: SleepSession?
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Macro Score Card
        SleepScoreHeaderCard(session = session)

        // Hypnogram Timeline Canvas
        HypnogramCard(stages = session?.stages ?: sampleHypnogramStages())

        // Sleep Architecture Distribution
        StageBreakdownCard(session = session)

        // Deep Sleep Continuity & Sleep Hygiene Benchmarks (Section 7.2)
        DeepSleepContinuityCard()

        // Sleep Breathing Awareness & Apnea Risk (Section 7.2)
        SleepBreathingAwarenessCard()

        // Daytime Naps & Autonomic Restoration
        DaytimeNapsCard(session = session)

        // Overnight Biomarkers: HRV, SpO2, Respiratory
        OvernightBiomarkersCard(session = session)

        Spacer(modifier = Modifier.height(110.dp))
    }
}

@Composable
fun SleepScoreHeaderCard(session: SleepSession?) {
    val hasRealTimes = session != null && session.startTimestamp > 0
    val startLocalStr = if (hasRealTimes) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(session!!.startTimestamp))
    } else "10:13 PM"
    val endLocalStr = if (hasRealTimes && session!!.endTimestamp > 0) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(session!!.endTimestamp))
    } else "06:03 AM"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = ElectricCyan.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    0.75.dp,
                    Brush.verticalGradient(
                        listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)
                    ),
                    RoundedCornerShape(22.dp)
                )
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "LAST NIGHT'S SLEEP",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${session?.sleepScore ?: 88}",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "$startLocalStr – $endLocalStr",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Efficiency: ${(session?.sleepEfficiency ?: 98f).toInt()}% • Latency: ${session?.latencyMinutes ?: 10}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Sleep",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = formatMinutes(session?.totalSleepMinutes ?: 470),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                    Text(
                        text = "In Bed: ${formatMinutes(session?.durationMinutes ?: 470)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun HypnogramCard(stages: List<SleepStageInterval>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = NeonTeal.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    0.75.dp,
                    Brush.verticalGradient(
                        listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)
                    ),
                    RoundedCornerShape(22.dp)
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HYPNOGRAM TIMELINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LegendItem("Awake", SleepAwakeColor)
                        LegendItem("REM", SleepRemColor)
                        LegendItem("Light", SleepLightColor)
                        LegendItem("Deep", SleepDeepColor)
                    }
                }

                // Clinical TruSleep Stepped Hypnogram Canvas with Y-Axis Levels & HR Alignment
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .background(SurfaceVariantDark, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    // Y-Axis Stage Labels
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 6.dp, top = 4.dp, bottom = 22.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Awake", fontSize = 8.sp, color = SleepAwakeColor, fontWeight = FontWeight.Bold)
                        Text(text = "REM", fontSize = 8.sp, color = SleepRemColor, fontWeight = FontWeight.Bold)
                        Text(text = "Light", fontSize = 8.sp, color = SleepLightColor, fontWeight = FontWeight.Bold)
                        Text(text = "Deep", fontSize = 8.sp, color = SleepDeepColor, fontWeight = FontWeight.Bold)
                    }

                    // Main Multi-Track Canvas
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                if (stages.isEmpty()) return@Canvas

                                val w = size.width
                                val h = size.height
                                val hypnoH = h * 0.70f
                                val hrH = h * 0.30f

                                // 4 Horizontal Stage Guide Lines
                                val rowYAwake = hypnoH * 0.10f
                                val rowYRem = hypnoH * 0.36f
                                val rowYLight = hypnoH * 0.62f
                                val rowYDeep = hypnoH * 0.88f
                                val rowYs = listOf(rowYAwake, rowYRem, rowYLight, rowYDeep)
                                for (ry in rowYs) {
                                    drawLine(
                                        color = TextMuted.copy(alpha = 0.15f),
                                        start = Offset(0f, ry),
                                        end = Offset(w, ry),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }

                                val totalDuration = stages.sumOf { it.durationSeconds }.toFloat().coerceAtLeast(1f)
                                val widthScale = w / totalDuration

                                // 1. Stepped Hypnogram Blocks & Vertical Transition Lines
                                var currentX = 0f
                                var prevY = rowYLight
                                for (interval in stages) {
                                    val segWidth = interval.durationSeconds * widthScale
                                    val (segColor, targetY) = when (interval.stage) {
                                        SleepStageType.AWAKE -> SleepAwakeColor to rowYAwake
                                        SleepStageType.REM -> SleepRemColor to rowYRem
                                        SleepStageType.LIGHT -> SleepLightColor to rowYLight
                                        SleepStageType.DEEP -> SleepDeepColor to rowYDeep
                                        SleepStageType.NAP -> SleepLightColor to rowYLight
                                    }

                                    // Vertical transition connector line
                                    if (currentX > 0f && prevY != targetY) {
                                        drawLine(
                                            color = segColor.copy(alpha = 0.7f),
                                            start = Offset(currentX, prevY),
                                            end = Offset(currentX, targetY),
                                            strokeWidth = 2.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }

                                    // Horizontal stage block
                                    val barHeight = 8.dp.toPx()
                                    drawRoundRect(
                                        color = segColor,
                                        topLeft = Offset(currentX, targetY - (barHeight / 2f)),
                                        size = Size(segWidth.coerceAtLeast(2.dp.toPx()), barHeight),
                                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                                    )

                                    currentX += segWidth
                                    prevY = targetY
                                }

                                // 2. Synchronized Nocturnal Heart Rate Waveform (Deep Nadir 52 bpm, REM 68 bpm)
                                val hrPath = Path()
                                val hrFill = Path()
                                val hrSamplesCount = 36
                                for (i in 0 until hrSamplesCount) {
                                    val frac = i.toFloat() / (hrSamplesCount - 1)
                                    val px = frac * w
                                    val hrBpm = when {
                                        frac in 0.12f..0.28f -> 52.5f // Deep Sleep 1 (nadir)
                                        frac in 0.32f..0.44f -> 67.5f // REM bout 1
                                        frac in 0.48f..0.62f -> 53.0f // Deep Sleep 2 (nadir)
                                        frac in 0.68f..0.82f -> 69.0f // REM bout 2
                                        else -> 58.0f                 // Light baseline
                                    }
                                    val normY = 1f - ((hrBpm - 48f) / 26f).coerceIn(0f, 1f)
                                    val py = hypnoH + (normY * (hrH - 4f)) + 2f

                                    if (i == 0) {
                                        hrPath.moveTo(px, py)
                                        hrFill.moveTo(px, h)
                                        hrFill.lineTo(px, py)
                                    } else {
                                        hrPath.lineTo(px, py)
                                        hrFill.lineTo(px, py)
                                    }
                                }
                                hrFill.lineTo(w, h)
                                hrFill.close()

                                drawPath(
                                    path = hrFill,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(CrimsonStrain.copy(alpha = 0.20f), Color.Transparent),
                                        startY = hypnoH,
                                        endY = h
                                    )
                                )
                                drawPath(
                                    path = hrPath,
                                    color = CrimsonStrain.copy(alpha = 0.85f),
                                    style = Stroke(width = 1.75.dp.toPx(), cap = StrokeCap.Round)
                                )

                                // 3. SpO2 Markers along timeline (97–99%)
                                val spo2Fracs = listOf(0.15f, 0.32f, 0.48f, 0.65f, 0.80f, 0.94f)
                                for (f in spo2Fracs) {
                                    drawCircle(color = SpO2Cyan, radius = 2.5.dp.toPx(), center = Offset(f * w, h - 3.dp.toPx()))
                                }
                            }
                        }

                        // Time Axis Labels
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "22:13", fontSize = 9.sp, color = TextMuted)
                            Text(text = "00:00", fontSize = 9.sp, color = TextMuted)
                            Text(text = "02:00", fontSize = 9.sp, color = TextMuted)
                            Text(text = "04:00", fontSize = 9.sp, color = TextMuted)
                            Text(text = "06:03", fontSize = 9.sp, color = TextMuted)
                        }
                    }
                }

                // Legend & Correlation Annotations
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "• Crimson: Nocturnal HR (Nadir 52 bpm)",
                        fontSize = 9.sp,
                        color = CrimsonStrain,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "• Cyan: 97-99% SpO2",
                        fontSize = 9.sp,
                        color = SpO2Cyan,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Proportional Stage Summary Pills (Deep 2h 15m, Light 4h 8m, REM 1h 27m, Awake 0m)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StageDurationPill(label = "Deep", duration = "2h 15m", pct = "28%", color = SleepDeepColor, modifier = Modifier.weight(1f))
                    StageDurationPill(label = "Light", duration = "4h 8m", pct = "52%", color = SleepLightColor, modifier = Modifier.weight(1f))
                    StageDurationPill(label = "REM", duration = "1h 27m", pct = "18%", color = SleepRemColor, modifier = Modifier.weight(1f))
                    StageDurationPill(label = "Awake", duration = "0m", pct = "0%", color = SleepAwakeColor, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Deep Sleep Continuity & Slow-Wave Consolidation Card (Section 7.2)
 */
@Composable
fun DeepSleepContinuityCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(22.dp), spotColor = SleepDeepColor.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.75.dp, Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)), RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Bedtime, contentDescription = null, tint = SleepDeepColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "DEEP SLEEP CONTINUITY", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .background(SleepDeepColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(text = "84% Continuity", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SleepDeepColor)
                    }
                }

                Text(
                    text = "Consolidated slow-wave sleep bouts with minimal micro-arousals promote deep physical cellular repair, growth hormone release, and immune restoration.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * Sleep Breathing Awareness & Apnea Risk Card (Section 7.2)
 */
@Composable
fun SleepBreathingAwarenessCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(22.dp), spotColor = ElectricCyan.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.75.dp, Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)), RoundedCornerShape(22.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Air, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "SLEEP BREATHING AWARENESS", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .background(RelaxedGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(text = "Normal • Low Risk", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RelaxedGreen)
                    }
                }

                Text(
                    text = "Cardiopulmonary coupling and arterial saturation remained stable throughout all 7h 50m of sleep with zero nocturnal hypoxia events (<90%).",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
fun StageBreakdownCard(session: SleepSession?) {
    val total = (session?.totalSleepMinutes ?: 470).coerceAtLeast(1)
    val deepMin = session?.deepSleepMinutes ?: 135
    val remMin = session?.remSleepMinutes ?: 87
    val lightMin = session?.lightSleepMinutes ?: 248
    val awakeMin = session?.awakeMinutes ?: 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = SleepDeepColor.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    0.75.dp,
                    Brush.verticalGradient(
                        listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)
                    ),
                    RoundedCornerShape(22.dp)
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "STAGE COMPOSITION",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )

                StageRow(name = "Deep Sleep (Physical Recovery)", minutes = deepMin, total = total, color = SleepDeepColor, target = "Target: 20-25%")
                StageRow(name = "REM Sleep (Cognitive / Memory)", minutes = remMin, total = total, color = SleepRemColor, target = "Target: 20-25%")
                StageRow(name = "Light Sleep", minutes = lightMin, total = total, color = SleepLightColor, target = "Target: 50-60%")
                StageRow(name = "Awake / Restless", minutes = awakeMin, total = total, color = SleepAwakeColor, target = "Normal: < 10%")
            }
        }
    }
}

@Composable
fun StageRow(name: String, minutes: Int, total: Int, color: Color, target: String) {
    val pct = (minutes.toFloat() / total * 100f).toInt()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(text = "${formatMinutes(minutes)} ($pct%)", fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(SurfaceVariantDark, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (pct / 100f).coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
        Text(text = target, fontSize = 10.sp, color = TextMuted)
    }
}

@Composable
fun OvernightBiomarkersCard(session: SleepSession?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = ElectricCyan.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    0.75.dp,
                    Brush.verticalGradient(
                        listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)
                    ),
                    RoundedCornerShape(22.dp)
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "OVERNIGHT BIOMARKERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BiomarkerTile(
                        title = "Overnight HRV",
                        value = "${(session?.avgHrv ?: 67f).toInt()} ms",
                        sub = "Baseline: ${(session?.minHrvBaseline ?: 51f).toInt()}-${(session?.maxHrvBaseline ?: 78f).toInt()} ms",
                        icon = Icons.Default.Timeline,
                        color = NeonTeal
                    )
                    BiomarkerTile(
                        title = "Breathing Rate",
                        value = "${(session?.avgBreathRate ?: 15f).toInt()} br/min",
                        sub = "Baseline: 12-20 br/min",
                        icon = Icons.Default.Air,
                        color = ElectricCyan
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BiomarkerTile(
                        title = "Avg Sleep SpO2",
                        value = "${(session?.avgSpo2 ?: 98f).toInt()}%",
                        sub = "Optimal: 95-100%",
                        icon = Icons.Default.Favorite,
                        color = ElectricCyan
                    )
                    BiomarkerTile(
                        title = "Sleep Heart Rate",
                        value = "${session?.avgHeartRate ?: 60} bpm",
                        sub = "Restful drop: -12%",
                        icon = Icons.Default.Bedtime,
                        color = SleepLightColor
                    )
                }
        }
    }
}
}

@Composable
fun BiomarkerTile(
    title: String,
    value: String,
    sub: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(modifier = Modifier.width(150.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = title, fontSize = 11.sp, color = TextMuted)
        }
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(text = sub, fontSize = 10.sp, color = TextSecondary)
    }
}

private fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "${h}h ${m}m"
}

private fun sampleHypnogramStages(): List<SleepStageInterval> {
    val base = 1791995280L // 22:13:00 Oct 14
    val intervals = mutableListOf<SleepStageInterval>()
    var cur = base
    val plan = listOf(
        Pair(SleepStageType.LIGHT, 25 * 60L),
        Pair(SleepStageType.DEEP, 50 * 60L),
        Pair(SleepStageType.LIGHT, 35 * 60L),
        Pair(SleepStageType.REM, 20 * 60L),
        Pair(SleepStageType.DEEP, 45 * 60L),
        Pair(SleepStageType.LIGHT, 40 * 60L),
        Pair(SleepStageType.REM, 30 * 60L),
        Pair(SleepStageType.DEEP, 40 * 60L),
        Pair(SleepStageType.LIGHT, 60 * 60L),
        Pair(SleepStageType.REM, 37 * 60L),
        Pair(SleepStageType.LIGHT, 88 * 60L)
    )
    for ((stage, dur) in plan) {
        intervals.add(SleepStageInterval(stage, cur, cur + dur, dur.toInt()))
        cur += dur
    }
    return intervals
}

@Composable
fun DaytimeNapsCard(session: SleepSession?) {
    // Check if session qualifies as daytime nap (duration < 3 hours)
    val isRecordedNap = session != null && session.durationMinutes in 15..179
    val napDurationMinutes = if (isRecordedNap) session!!.durationMinutes else 0
    val napDeepMinutes = if (isRecordedNap) session!!.deepSleepMinutes else 0
    val napLightMinutes = if (isRecordedNap) session!!.lightSleepMinutes else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = ElectricCyan.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    0.75.dp,
                    Brush.verticalGradient(
                        listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)
                    ),
                    RoundedCornerShape(22.dp)
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "DAYTIME NAPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.0.sp
                            )
                            Text(
                                text = if (isRecordedNap) "1 Nap Detected Today" else "0 Naps Detected Today",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                if (isRecordedNap) NeonTeal.copy(alpha = 0.15f) else ElectricCyan.copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                0.5.dp,
                                if (isRecordedNap) NeonTeal.copy(alpha = 0.5f) else ElectricCyan.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isRecordedNap) "AUTONOMIC REBOUND" else "CONSOLIDATED REST",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isRecordedNap) NeonTeal else ElectricCyan,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Nap Duration & Quality metrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isRecordedNap) formatMinutes(napDurationMinutes) else "0m",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = if (isRecordedNap) "Recorded via Huawei TruSleep Staging" else "All physical & cognitive recovery consolidated overnight",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "CNS Recovery",
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isRecordedNap) "+18% Readiness" else "100% Rest",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeonTeal
                        )
                    }
                }

                if (isRecordedNap) {
                    // Staging Distribution Bar
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Deep Sleep (Physical): ${napDeepMinutes}m",
                                fontSize = 10.sp,
                                color = SleepDeepColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Light Sleep: ${napLightMinutes}m",
                                fontSize = 10.sp,
                                color = SleepLightColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(SleepLightColor.copy(alpha = 0.35f), RoundedCornerShape(3.dp))
                        ) {
                            val deepRatio = if (napDurationMinutes > 0) (napDeepMinutes.toFloat() / napDurationMinutes).coerceIn(0f, 1f) else 0.5f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(deepRatio)
                                    .height(6.dp)
                                    .background(SleepDeepColor, RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StageDurationPill(
    label: String,
    duration: String,
    pct: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = duration, fontSize = 11.sp, fontWeight = FontWeight.Black, color = TextPrimary)
        Text(text = pct, fontSize = 9.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}
