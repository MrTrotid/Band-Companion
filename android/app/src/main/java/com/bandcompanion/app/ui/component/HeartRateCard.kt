package com.bandcompanion.app.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.bandcompanion.app.ui.theme.ElectricTealRing
import com.bandcompanion.app.ui.theme.HotMagentaRing
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.SampleMetric

/**
 * Whoop-styled Cardiovascular Strain & Continuous Heart Rate Card.
 * Displays live pulse, resting baseline, daily range (min/max), and continuous curve.
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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
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

                Text(
                    text = if (restingBpm != null) "Resting $restingBpm bpm" else "Continuous 24/7",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CrimsonStrain
                )
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
            val validHrList = hrSamples.mapNotNull { it.heartRate }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .background(ObsidianHighlight, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (validHrList.size >= 2) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val minVal = (validHrList.minOrNull() ?: 50).toFloat().coerceAtLeast(40f)
                        val maxVal = (validHrList.maxOrNull() ?: 160).toFloat().coerceAtLeast(minVal + 20f)
                        val range = maxVal - minVal

                        val strokePath = Path()
                        val fillPath = Path()

                        validHrList.forEachIndexed { index, hr ->
                            val x = index.toFloat() / (validHrList.size - 1) * size.width
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

            Spacer(modifier = Modifier.height(14.dp))

            // HR Zone Distribution Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ZoneIndicator(label = "Rest", color = NeonCyanRing, percent = "55%", modifier = Modifier.weight(1f))
                ZoneIndicator(label = "Fat Burn", color = ElectricTealRing, percent = "28%", modifier = Modifier.weight(1f))
                ZoneIndicator(label = "Cardio", color = HotMagentaRing, percent = "12%", modifier = Modifier.weight(1f))
                ZoneIndicator(label = "Peak", color = CrimsonStrain, percent = "5%", modifier = Modifier.weight(1f))
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
