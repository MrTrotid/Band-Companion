package com.bandcompanion.app.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.AmberGoldRing
import com.bandcompanion.app.ui.theme.AmberStrain
import com.bandcompanion.app.ui.theme.CoralRed
import com.bandcompanion.app.ui.theme.CrimsonStrain
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.ElectricTealRing
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.HighRed
import com.bandcompanion.app.ui.theme.HotMagentaRing
import com.bandcompanion.app.ui.theme.MediumOrange
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.NormalBlue
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.SleepDeepColor
import com.bandcompanion.app.ui.theme.SleepLightColor
import com.bandcompanion.app.ui.theme.SleepRemColor
import com.bandcompanion.app.ui.theme.SurfaceDark
import com.bandcompanion.app.ui.theme.SurfaceVariantDark
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.DailySummary

@Composable
fun TrendsScreen(
    summaries: List<DailySummary>
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
        Text(
            text = "PHYSIOLOGICAL TRENDS & DISCOVERY",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        // 1. VO2 Max & Cardiorespiratory Aerobic Fitness Engine
        Vo2MaxCard()

        // 2. Circadian Rhythm & Sleep Debt Analyzer
        SleepDebtCard()

        // 3. Autonomic HRV Resilience Radar (rMSSD)
        HrvRadarCard()

        // 4. Cardio Training Zones Breakdown
        CardioZonesCard()

        // 5. Daytime Stress vs Rest Matrix
        DaytimeStressMatrixCard()

        // 6. ACWR Rolling Timeline Chart
        AcwrTrendChartCard(summaries = summaries)

        // 7. Strain vs. Sleep Recovery Correlation
        StrainRecoveryCorrelationCard()

        // 8. Resting Heart Rate Baseline Stability
        RhrBaselineCard()

        // 9. Autonomic Recovery Quadrant (Sleep HRV vs RHR Baseline Z-Score, Section 20)
        AutonomicRecoveryQuadrantCard()

        // 10. Sleep Regularity Index (SRI) & Mid-Sleep Stability (Section 20)
        SleepRegularityIndexCard()

        Spacer(modifier = Modifier.height(110.dp))
    }
}

/**
 * 1. VO2 Max & Cardiorespiratory Aerobic Fitness Card.
 */
@Composable
fun Vo2MaxCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(22.dp), spotColor = NeonTeal.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.75.dp, Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "CARDIORESPIRATORY FITNESS", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .background(NeonTeal.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(text = "Superior Level", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = NeonTeal)
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "49.4", fontSize = 36.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "mL / kg / min", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonTeal, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "AEROBIC CAPACITY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text(text = "Top 5% of Age Group", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }

                // Tier Gauge Bar (Poor, Fair, Good, Excellent, Superior)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .background(ObsidianHighlight, RoundedCornerShape(4.dp))
                    ) {
                        Box(modifier = Modifier.weight(1f).height(8.dp).background(HighRed.copy(alpha = 0.4f), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)))
                        Box(modifier = Modifier.weight(1f).height(8.dp).background(MediumOrange.copy(alpha = 0.4f)))
                        Box(modifier = Modifier.weight(1.2f).height(8.dp).background(NormalBlue.copy(alpha = 0.4f)))
                        Box(modifier = Modifier.weight(1.2f).height(8.dp).background(ElectricCyan.copy(alpha = 0.6f)))
                        Box(modifier = Modifier.weight(1.5f).height(8.dp).background(NeonTeal, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Poor (<35)", fontSize = 9.sp, color = TextMuted)
                        Text(text = "Good (42)", fontSize = 9.sp, color = TextMuted)
                        Text(text = "Superior (49+)", fontSize = 9.sp, color = NeonTeal, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "Cooper & Storer-Davis physiological index derived from resting HR (52 bpm), max cardiovascular capacity, and active cadence reserve.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * 2. Circadian Rhythm & Sleep Debt Analyzer Card.
 */
@Composable
fun SleepDebtCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(22.dp), spotColor = ElectricCyan.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.75.dp, Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Bedtime, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "CIRCADIAN RHYTHM & SLEEP DEBT", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .background(RelaxedGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(text = "0m Sleep Debt", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RelaxedGreen)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "OPTIMAL SLEEP WINDOW", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text(text = "10:15 PM – 06:15 AM", fontSize = 16.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                        Text(text = "Natural melatonin onset aligned", fontSize = 10.sp, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "CONSISTENCY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text(text = "94%", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ElectricCyan)
                        Text(text = "High circadian stability", fontSize = 10.sp, color = RelaxedGreen)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianHighlight, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Last night's 7h 50m sleep satisfied 100% of physiological baseline requirements with zero cumulative fatigue.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * 3. Autonomic HRV Resilience Radar (rMSSD) Card.
 */
@Composable
fun HrvRadarCard(
    onOpenDetail: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenDetail() }
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(22.dp), spotColor = NeonTeal.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.75.dp, Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Timeline, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "AUTONOMIC HRV RESILIENCE", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "Baseline 51–78 ms", fontSize = 11.sp, color = TextMuted)
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(14.dp))
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "67", fontSize = 36.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "ms rMSSD", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NeonTeal, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "VAGAL TONE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text(text = "+16% Above Median", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RelaxedGreen)
                    }
                }

                // 7-day mini trend canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .background(ObsidianHighlight, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Baseline corridor shading (51 to 78 ms on 40 to 110 scale)
                        val yTop = h - ((78f - 40f) / 70f * h)
                        val yBottom = h - ((51f - 40f) / 70f * h)
                        drawRect(
                            color = NeonTeal.copy(alpha = 0.12f),
                            topLeft = Offset(0f, yTop),
                            size = androidx.compose.ui.geometry.Size(w, yBottom - yTop)
                        )

                        val hrvPoints = listOf(84f, 104f, 77f, 68f, 79f, 65f, 67f)
                        val stepX = w / (hrvPoints.size - 1)
                        val path = Path()
                        for (i in hrvPoints.indices) {
                            val px = i * stepX
                            val py = h - ((hrvPoints[i] - 40f) / 70f * h)
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        drawPath(path = path, color = NeonTeal, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

                        val lastX = (hrvPoints.size - 1) * stepX
                        val lastY = h - ((hrvPoints.last() - 40f) / 70f * h)
                        drawCircle(color = NeonTeal, radius = 4.5.dp.toPx(), center = Offset(lastX, lastY))
                    }
                }

                Text(
                    text = "High parasympathetic activation indicates rapid central nervous system recovery and optimal readiness for athletic exertion.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * 4. Cardio Training Zones Breakdown Card.
 */
@Composable
fun CardioZonesCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(22.dp), spotColor = HotMagentaRing.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.75.dp, Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = HotMagentaRing, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "CARDIAC TRAINING ZONES", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                    Text(text = "Daily Distribution", fontSize = 11.sp, color = TextMuted)
                }

                ZoneBarRow(name = "Zone 1 • Active Recovery (<100 bpm)", pct = 45, color = NeonCyanRing, description = "Parasympathetic tone & recovery")
                ZoneBarRow(name = "Zone 2 • Aerobic Base (100–120 bpm)", pct = 40, color = ElectricTealRing, description = "Mitochondrial development & fat oxidation")
                ZoneBarRow(name = "Zone 3 • Aerobic Tempo (120–140 bpm)", pct = 12, color = HotMagentaRing, description = "Cardiovascular efficiency")
                ZoneBarRow(name = "Zone 4 • Threshold (140–160 bpm)", pct = 3, color = AmberStrain, description = "Lactate threshold buffering")
            }
        }
    }
}

@Composable
private fun ZoneBarRow(name: String, pct: Int, color: Color, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, fontSize = 11.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(text = "$pct%", fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(ObsidianHighlight, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct / 100f)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
        Text(text = description, fontSize = 9.sp, color = TextMuted)
    }
}

/**
 * 5. Daytime Stress vs Rest Matrix Card.
 */
@Composable
fun DaytimeStressMatrixCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(22.dp), spotColor = NormalBlue.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.75.dp, Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.SelfImprovement, contentDescription = null, tint = NormalBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "STRESS VS. REST LOAD MATRIX", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                    Text(text = "TruRelax™ Timeline", fontSize = 11.sp, color = NormalBlue, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StressMetricBadge(label = "Relaxed", pct = "42%", color = RelaxedGreen, modifier = Modifier.weight(1f))
                    StressMetricBadge(label = "Balanced", pct = "58%", color = NormalBlue, modifier = Modifier.weight(1f))
                    StressMetricBadge(label = "Elevated", pct = "0%", color = MediumOrange, modifier = Modifier.weight(1f))
                    StressMetricBadge(label = "High", pct = "0%", color = HighRed, modifier = Modifier.weight(1f))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianHighlight, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "100% of daytime intervals spent in restorative autonomic balance with zero acute stress spikes.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StressMetricBadge(label: String, pct: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(ObsidianHighlight, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = pct, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
fun AcwrTrendChartCard(summaries: List<DailySummary>) {
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
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ACUTE : CHRONIC WORKLOAD RATIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(text = "Optimal 0.8 - 1.3", fontSize = 11.sp, color = NeonTeal, fontWeight = FontWeight.SemiBold)
                }

                // Canvas drawing ACWR line with shaded optimal zone
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(SurfaceVariantDark, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Draw optimal band (0.80 to 1.30 on scale 0.0 to 2.0)
                        val yUpper = h - (1.30f / 2.0f * h)
                        val yLower = h - (0.80f / 2.0f * h)
                        drawRect(
                            color = NeonTeal.copy(alpha = 0.15f),
                            topLeft = Offset(0f, yUpper),
                            size = androidx.compose.ui.geometry.Size(w, yLower - yUpper)
                        )

                        // Draw reference line at 1.0
                        val yOne = h - (1.0f / 2.0f * h)
                        drawLine(
                            color = TextMuted.copy(alpha = 0.4f),
                            start = Offset(0f, yOne),
                            end = Offset(w, yOne),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Trend line points
                        val points = listOf(0.95f, 1.02f, 1.15f, 1.08f, 1.25f, 1.10f, 1.03f)
                        val stepX = w / (points.size - 1)

                        val path = Path()
                        for (i in points.indices) {
                            val px = i * stepX
                            val py = h - (points[i] / 2.0f * h)
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }

                        drawPath(
                            path = path,
                            color = ElectricCyan,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Draw circle on latest point
                        val lastX = (points.size - 1) * stepX
                        val lastY = h - (points.last() / 2.0f * h)
                        drawCircle(color = ElectricCyan, radius = 5.dp.toPx(), center = Offset(lastX, lastY))
                    }
                }

                Text(
                    text = "Workload safely primed in the adaptation sweet spot with no acute spikes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun StrainRecoveryCorrelationCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = AmberStrain.copy(alpha = 0.2f)
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.AutoGraph, contentDescription = null, tint = AmberStrain, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STRAIN VS. RECOVERY COUPLING",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Higher cardiovascular strain (14.0+) followed by 85%+ sleep efficiency demonstrates optimal autonomic nervous system recovery (parasympathetic rebound).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "Average Strain", fontSize = 11.sp, color = TextMuted)
                        Text(text = "11.2", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AmberStrain)
                    }
                    Column {
                        Text(text = "Recovery Coupling", fontSize = 11.sp, color = TextMuted)
                        Text(text = "+0.78 (Strong)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonTeal)
                    }
                    Column {
                        Text(text = "Deep Sleep Rebound", fontSize = 11.sp, color = TextMuted)
                        Text(text = "24.2%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ElectricCyan)
                    }
                }
            }
        }
    }
}

@Composable
fun RhrBaselineCard() {
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "RESTING HEART RATE BASELINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "56.6 bpm", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(text = "30-day rolling baseline", fontSize = 11.sp, color = TextSecondary)
                    }
                    Box(
                        modifier = Modifier
                            .background(NeonTeal.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = "Stable (-1.2 bpm)", fontSize = 12.sp, color = NeonTeal, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 9. Autonomic Recovery Quadrant (Sleep HRV vs. RHR Rolling Baseline Z-Score, Section 20)
 */
@Composable
fun AutonomicRecoveryQuadrantCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(22.dp), spotColor = NeonTeal.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.75.dp, Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AutoGraph, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "AUTONOMIC RECOVERY QUADRANT", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .background(RelaxedGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(text = "Q1: Parasympathetic Primed", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RelaxedGreen)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "SLEEP HRV Z-SCORE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text(text = "+1.1 SD", fontSize = 20.sp, fontWeight = FontWeight.Black, color = NeonTeal)
                        Text(text = "Above 7-day median (67 ms)", fontSize = 10.sp, color = TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "RHR DEVIATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text(text = "-2.4 bpm", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ElectricCyan)
                        Text(text = "Quiescent baseline drop", fontSize = 10.sp, color = RelaxedGreen)
                    }
                }

                // 2D Quadrant Matrix Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(95.dp)
                        .background(SurfaceVariantDark, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val midX = w / 2f
                        val midY = h / 2f

                        // Shaded Q1 (Top-Left quadrant: High HRV, Low RHR)
                        drawRect(
                            color = RelaxedGreen.copy(alpha = 0.12f),
                            topLeft = Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(midX, midY)
                        )

                        // Axis lines
                        drawLine(color = TextMuted.copy(alpha = 0.35f), start = Offset(midX, 0f), end = Offset(midX, h), strokeWidth = 1.dp.toPx())
                        drawLine(color = TextMuted.copy(alpha = 0.35f), start = Offset(0f, midY), end = Offset(w, midY), strokeWidth = 1.dp.toPx())

                        // Current coordinate point (Z-score +1.1, RHR -2.4)
                        val pointX = midX - (2.4f / 6f * midX) // Lower RHR moves left into Q1
                        val pointY = midY - (1.1f / 2.5f * midY) // Higher HRV moves up into Q1
                        drawCircle(color = NeonTeal, radius = 5.dp.toPx(), center = Offset(pointX, pointY))
                    }
                }

                Text(
                    text = "Elevated vagal tone paired with a suppressed resting heart rate confirms optimal central nervous system recovery and cellular readiness for high athletic strain.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/**
 * 10. Sleep Regularity Index (SRI) & Mid-Sleep Stability Card (Section 20)
 */
@Composable
fun SleepRegularityIndexCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = RoundedCornerShape(22.dp), spotColor = ElectricCyan.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.75.dp, Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Bedtime, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "SLEEP REGULARITY INDEX (SRI)", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .background(ElectricCyan.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(text = "94% SRI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectricCyan)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(text = "MID-SLEEP POINT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text(text = "02:08 AM", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                        Text(text = "±18m weekly variance", fontSize = 10.sp, color = RelaxedGreen)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "CIRCADIAN STABILITY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text(text = "High Regularity", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RelaxedGreen)
                        Text(text = "Zero social jetlag", fontSize = 10.sp, color = TextSecondary)
                    }
                }

                // 7-Day Consistency Raster Barcode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val days = listOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed")
                    for (day in days) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(ObsidianHighlight, RoundedCornerShape(6.dp))
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = day, fontSize = 9.sp, color = TextMuted)
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier.size(width = 16.dp, height = 24.dp).background(ElectricCyan.copy(alpha = 0.75f), RoundedCornerShape(3.dp)))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "94%", fontSize = 9.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = "Stable sleep-wake timing within ±20 minutes across consecutive days maintains strong circadian rhythm amplitude and optimizes next-day executive performance.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
            }
        }
    }
}
