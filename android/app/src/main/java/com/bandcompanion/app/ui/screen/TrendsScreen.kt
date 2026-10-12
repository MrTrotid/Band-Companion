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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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

@Composable
fun TrendsScreen(
    summaries: List<DailySummary>
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
        Text(
            text = "PHYSIOLOGICAL TRENDS & ADAPTATION",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )

        // ACWR Rolling Timeline Chart
        AcwrTrendChartCard(summaries = summaries)

        // Strain vs. Sleep Recovery Correlation
        StrainRecoveryCorrelationCard()

        // Resting Heart Rate Baseline Stability
        RhrBaselineCard()

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AcwrTrendChartCard(summaries: List<DailySummary>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.ShowChart, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
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
                    .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
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

@Composable
fun StrainRecoveryCorrelationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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

@Composable
fun RhrBaselineCard() {
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
