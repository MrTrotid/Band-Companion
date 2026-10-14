package com.bandcompanion.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Timeline
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.SleepAwakeColor
import com.bandcompanion.app.ui.theme.SleepDeepColor
import com.bandcompanion.app.ui.theme.SleepLightColor
import com.bandcompanion.app.ui.theme.SleepRemColor
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

        // Overnight Biomarkers: HRV, SpO2, Respiratory
        OvernightBiomarkersCard(session = session)

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
fun SleepScoreHeaderCard(session: SleepSession?) {
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
                        text = "${session?.sleepScore ?: 69}",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Efficiency: ${(session?.sleepEfficiency ?: 86f).toInt()}% • Latency: ${session?.latencyMinutes ?: 13}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Sleep",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                    Text(
                        text = formatMinutes(session?.totalSleepMinutes ?: 277),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                    Text(
                        text = "In Bed: ${formatMinutes(session?.durationMinutes ?: 351)}",
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

                // Timeline Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (stages.isEmpty()) return@Canvas

                        val totalDuration = stages.sumOf { it.durationSeconds }.toFloat().coerceAtLeast(1f)
                        val widthScale = size.width / totalDuration
                        var currentX = 0f

                        for (interval in stages) {
                            val segWidth = interval.durationSeconds * widthScale
                            val (segColor, heightFrac, topFrac) = when (interval.stage) {
                                SleepStageType.AWAKE -> Triple(SleepAwakeColor, 0.25f, 0.0f)
                                SleepStageType.REM -> Triple(SleepRemColor, 0.35f, 0.25f)
                                SleepStageType.LIGHT -> Triple(SleepLightColor, 0.50f, 0.50f)
                                SleepStageType.DEEP -> Triple(SleepDeepColor, 0.70f, 0.30f)
                                SleepStageType.NAP -> Triple(SleepLightColor, 0.40f, 0.50f)
                            }

                            drawRoundRect(
                                color = segColor,
                                topLeft = Offset(currentX, size.height * topFrac),
                                size = Size(segWidth.coerceAtLeast(1.5f), size.height * heightFrac),
                                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                            )
                            currentX += segWidth
                        }
                    }
                }
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
    val total = (session?.totalSleepMinutes ?: 277).coerceAtLeast(1)
    val deepMin = session?.deepSleepMinutes ?: 64
    val remMin = session?.remSleepMinutes ?: 52
    val lightMin = session?.lightSleepMinutes ?: 161
    val awakeMin = session?.awakeMinutes ?: 38

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
                        value = "${(session?.avgHrv ?: 84f).toInt()} ms",
                        sub = "Baseline: ${(session?.minHrvBaseline ?: 51f).toInt()}-${(session?.maxHrvBaseline ?: 78f).toInt()} ms",
                        icon = Icons.Default.Timeline,
                        color = NeonTeal
                    )
                    BiomarkerTile(
                        title = "Breathing Rate",
                        value = "${(session?.avgBreathRate ?: 13f).toInt()} bpm",
                        sub = "Baseline: 11-14 bpm",
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
                    value = "${(session?.avgSpo2 ?: 96f).toInt()}%",
                    sub = "Optimal: 95-99%",
                    icon = Icons.Default.Favorite,
                    color = ElectricCyan
                )
                BiomarkerTile(
                    title = "Sleep Heart Rate",
                    value = "${session?.avgHeartRate ?: 54} bpm",
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
    val base = 1791217740L
    return listOf(
        SleepStageInterval(SleepStageType.AWAKE, base, base + 780),
        SleepStageInterval(SleepStageType.LIGHT, base + 780, base + 3600),
        SleepStageInterval(SleepStageType.DEEP, base + 3600, base + 7200),
        SleepStageInterval(SleepStageType.REM, base + 7200, base + 9600),
        SleepStageInterval(SleepStageType.LIGHT, base + 9600, base + 14400),
        SleepStageInterval(SleepStageType.DEEP, base + 14400, base + 16800),
        SleepStageInterval(SleepStageType.REM, base + 16800, base + 19200),
        SleepStageInterval(SleepStageType.AWAKE, base + 19200, base + 21060)
    )
}
