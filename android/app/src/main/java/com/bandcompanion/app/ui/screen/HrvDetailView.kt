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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBackground
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.SleepSession

/**
 * Editorial Deep Telemetry View for Autonomic HRV Resilience Radar.
 * Features:
 * - Breadcrumbs navigation with top-left back button
 * - Nocturnal rMSSD curve against individual baseline corridor (51–78 ms)
 * - Vagal tone and parasympathetic recovery analysis
 * - Circadian HRV telemetry breakdown
 */
@Composable
fun HrvDetailView(
    latestSleepSession: SleepSession?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val avgHrv = latestSleepSession?.avgHrv?.toInt() ?: 67
    val minBaseline = latestSleepSession?.minHrvBaseline?.toInt() ?: 51
    val maxBaseline = latestSleepSession?.maxHrvBaseline?.toInt() ?: 78

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
                        tint = NeonTeal,
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
                        text = "AUTONOMIC HRV RADAR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonTeal,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Vagal Tone & Parasympathetic Recovery",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // 2. Primary HRV Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ObsidianCard)
                .border(1.dp, NeonTeal.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
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
                                .background(NeonTeal, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NOCTURNAL RMSSD RADAR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = NeonTeal
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(NeonTeal.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Corridor $minBaseline–$maxBaseline ms",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonTeal
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "$avgHrv",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ms rMSSD",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonTeal,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "AUTONOMIC STATE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Text(
                            text = if (avgHrv >= minBaseline) "High Resilience" else "Fatigued / Low",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (avgHrv >= minBaseline) RelaxedGreen else Color(0xFFFF9800)
                        )
                    }
                }

                Text(
                    text = "Your overnight heart rate variability indicates strong parasympathetic nervous system recovery, supporting optimal physical training readiness.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        // 3. 7-Day Baseline Corridor Shading Chart
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
                    text = "7-DAY HRV BASELINE CORRIDOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianHighlight)
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val minScale = 40f
                        val maxScale = 100f
                        val range = maxScale - minScale

                        // Baseline corridor shading (51 to 78 ms)
                        val corridorTop = h * (1f - (maxBaseline - minScale) / range)
                        val corridorBottom = h * (1f - (minBaseline - minScale) / range)

                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(NeonTeal.copy(alpha = 0.18f), NeonTeal.copy(alpha = 0.06f)),
                                startY = corridorTop,
                                endY = corridorBottom
                            ),
                            topLeft = Offset(0f, corridorTop),
                            size = androidx.compose.ui.geometry.Size(w, corridorBottom - corridorTop)
                        )

                        // Corridor boundary lines
                        drawLine(
                            color = NeonTeal.copy(alpha = 0.4f),
                            start = Offset(0f, corridorTop),
                            end = Offset(w, corridorTop),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                        drawLine(
                            color = NeonTeal.copy(alpha = 0.4f),
                            start = Offset(0f, corridorBottom),
                            end = Offset(w, corridorBottom),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        // 7 historical days mock/trend line
                        val sampleDays = listOf(62f, 58f, 65f, 70f, 64f, 68f, avgHrv.toFloat())
                        val stepX = w / (sampleDays.size - 1)
                        val path = Path()

                        sampleDays.forEachIndexed { idx, value ->
                            val x = idx * stepX
                            val y = h * (1f - (value - minScale) / range)
                            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            drawCircle(color = NeonTeal, radius = 3.dp.toPx(), center = Offset(x, y))
                        }

                        drawPath(path = path, color = NeonTeal, style = Stroke(width = 2.dp.toPx()))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "6 Days Ago", fontSize = 10.sp, color = TextMuted)
                    Text(text = "Today (Night Nadir)", fontSize = 10.sp, color = NeonTeal, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4. Clinical Significance & Autonomic Balance Guide
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ObsidianCard)
                .border(1.dp, GlassDockBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "CLINICAL PHYSIOLOGY NOTES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                }
                Text(
                    text = "• rMSSD (Root Mean Square of Successive Differences) reflects beat-to-beat parasympathetic autonomic regulation.\n" +
                            "• A reading within or above your baseline corridor ($minBaseline–$maxBaseline ms) signals effective central nervous system recovery.\n" +
                            "• Suppression below $minBaseline ms suggests accumulated muscular fatigue, systemic stress, or disrupted sleep architecture.",
                    fontSize = 11.sp,
                    color = TextMuted,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(110.dp))
    }
}
