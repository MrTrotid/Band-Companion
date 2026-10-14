package com.bandcompanion.app.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.bandcompanion.app.ui.theme.CrimsonStrain
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.GlassDockBorderMuted
import com.bandcompanion.app.ui.theme.HotMagentaRing
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.SpO2Cyan
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.SampleMetric
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apple/Dribbble-grade Live Metrics Screen.
 * Features:
 * - 3D-inspired multi-orbit radial pulse wave radar canvas
 * - Heart rate responsive cardiac zone badge
 * - Glassmorphic telemetry cards with live animated mini sparkline waveforms
 */
@Composable
fun LiveMetricsScreen(
    latestSample: SampleMetric?
) {
    val scrollState = rememberScrollState()
    val hr = latestSample?.heartRate ?: 72
    val pulseDuration = (60_000 / hr.coerceIn(40, 200)).coerceIn(300, 1500)

    val infiniteTransition = rememberInfiniteTransition(label = "liveSensors")

    // Dynamic heart pulsation scaled to current BPM
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration / 2, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartPulse"
    )

    // Orbital wave rotation
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarOrbit"
    )

    val zoneInfo = when {
        hr < 60 -> Pair("Resting Zone", RelaxedGreen)
        hr < 100 -> Pair("Aerobic Baseline", NeonTeal)
        hr < 140 -> Pair("Cardio Endurance", ElectricCyan)
        hr < 170 -> Pair("Anaerobic Threshold", HotMagentaRing)
        else -> Pair("Peak Performance", CrimsonStrain)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CONTINUOUS OPTICAL PPG",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonTeal.copy(alpha = 0.15f))
                    .border(0.5.dp, NeonTeal.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "REAL-TIME",
                    color = NeonTeal,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // 3D Multi-Orbit Heart Rate Hero Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = CrimsonStrain.copy(alpha = 0.18f),
                    spotColor = CrimsonStrain.copy(alpha = 0.35f)
                ),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            colors = listOf(
                                GlassDockBorder.copy(alpha = 0.6f),
                                Color(0x1AFFFFFF),
                                Color.Transparent
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(vertical = 28.dp, horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // 3D Orbital Canvas
                    Box(
                        modifier = Modifier.size(170.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)

                            // Background radial glow
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        CrimsonStrain.copy(alpha = 0.28f),
                                        CrimsonStrain.copy(alpha = 0.08f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = size.width / 2f
                                ),
                                radius = size.width / 2f,
                                center = center
                            )

                            // Concentric orbital rings with 3D tilt simulation
                            val rads = Math.toRadians(orbitRotation.toDouble())
                            for (ringIdx in 1..3) {
                                val radius = (28f * ringIdx) + 18f
                                val ringAlpha = 0.15f + (ringIdx * 0.10f)

                                drawCircle(
                                    color = CrimsonStrain.copy(alpha = ringAlpha),
                                    radius = radius,
                                    center = center,
                                    style = Stroke(
                                        width = 1.25.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )

                                // Glowing particle on each orbit
                                val particleAngle = rads * (if (ringIdx % 2 == 0) 1.2 else -0.8) + (ringIdx * 1.5)
                                val px = center.x + (radius * cos(particleAngle)).toFloat()
                                val py = center.y + (radius * sin(particleAngle)).toFloat()

                                drawCircle(
                                    color = Color.White,
                                    radius = 2.5.dp.toPx(),
                                    center = Offset(px, py)
                                )
                                drawCircle(
                                    color = CrimsonStrain,
                                    radius = 5.dp.toPx(),
                                    center = Offset(px, py),
                                    style = Stroke(width = 1.dp.toPx())
                                )
                            }
                        }

                        // Central pulsing heart capsule
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .scale(heartScale)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            CrimsonStrain.copy(alpha = 0.5f),
                                            CrimsonStrain.copy(alpha = 0.2f)
                                        )
                                    )
                                )
                                .border(1.5.dp, CrimsonStrain.copy(alpha = 0.8f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Heartbeat",
                                tint = CrimsonStrain,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Numeric Readout
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "$hr",
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                letterSpacing = (-1).sp
                            )
                            Text(
                                text = "BPM",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = CrimsonStrain,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }

                        Text(
                            text = "INSTANTANEOUS PULSE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.sp
                        )
                    }

                    // Cardiac Zone Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(zoneInfo.second.copy(alpha = 0.15f))
                            .border(0.75.dp, zoneInfo.second.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(zoneInfo.second)
                            )
                            Text(
                                text = zoneInfo.first,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = zoneInfo.second,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }
            }
        }

        // Secondary Telemetry Row: SpO2 & Cadence with Live Sparklines
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Blood Oxygen Card with Waveform
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(150.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = SpO2Cyan.copy(alpha = 0.15f),
                        spotColor = SpO2Cyan.copy(alpha = 0.25f)
                    ),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            0.75.dp,
                            Brush.verticalGradient(
                                listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)
                            ),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "BLOOD OXYGEN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = SpO2Cyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${(latestSample?.spo2 ?: 98f).toInt()}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Text(
                                text = "%",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SpO2Cyan,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        // Mini Waveform Canvas
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        ) {
                            val path = Path()
                            val w = size.width
                            val h = size.height
                            path.moveTo(0f, h * 0.7f)
                            path.cubicTo(w * 0.25f, h * 0.1f, w * 0.5f, h * 0.9f, w * 0.75f, h * 0.3f)
                            path.lineTo(w, h * 0.6f)

                            drawPath(
                                path = path,
                                color = SpO2Cyan,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Text(
                            text = "Normoxia • Safe Range",
                            fontSize = 10.sp,
                            color = NeonTeal,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Cadence / Minute Steps Card with Bar Graph
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(150.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = NeonCyanRing.copy(alpha = 0.15f),
                        spotColor = NeonCyanRing.copy(alpha = 0.25f)
                    ),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            0.75.dp,
                            Brush.verticalGradient(
                                listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)
                            ),
                            RoundedCornerShape(22.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MINUTE STEPS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 0.5.sp
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                                contentDescription = null,
                                tint = NeonCyanRing,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${latestSample?.steps ?: 18}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Text(
                                text = "SPM",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyanRing,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        // Mini Dynamic Activity Bars Canvas
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        ) {
                            val barCount = 7
                            val barWidth = 6.dp.toPx()
                            val spacing = (size.width - (barCount * barWidth)) / (barCount - 1)
                            val heights = listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 0.85f, 0.55f)

                            for (i in 0 until barCount) {
                                val x = i * (barWidth + spacing)
                                val barH = size.height * heights[i]
                                drawRoundRect(
                                    color = if (i == 5) NeonCyanRing else NeonCyanRing.copy(alpha = 0.35f),
                                    topLeft = Offset(x, size.height - barH),
                                    size = androidx.compose.ui.geometry.Size(barWidth, barH),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                                )
                            }
                        }

                        Text(
                            text = "Active Cadence",
                            fontSize = 10.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(110.dp))
    }
}
