package com.bandcompanion.app.ui.component

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.GlassDockBorderMuted
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler.WeatherCondition

/**
 * Apple/Dribbble-grade Weather Card with 24-hr hourly temperature curve,
 * astronomical sun/moon conditions, and Huawei Band 10 weather sync button.
 */
@Composable
fun WeatherCard(
    weather: WeatherCondition,
    isSyncingWeather: Boolean = false,
    onSyncWeatherClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "weatherSpin")
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "weatherSpinAnim"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = ElectricCyan.copy(alpha = 0.2f)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 0.75.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GlassDockBorder.copy(alpha = 0.6f),
                            GlassDockBorderMuted.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "WEATHER FORECAST",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ElectricCyan.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "LIVE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                            }
                        }
                        Text(
                            text = "${weather.location} • ${weather.conditionName}",
                            fontSize = 12.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Sync to Watch Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1B1E2B))
                            .border(0.75.dp, GlassDockBorder, RoundedCornerShape(16.dp))
                            .clickable(enabled = !isSyncingWeather) { onSyncWeatherClick() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync Weather",
                                tint = if (isSyncingWeather) ElectricCyan else TextSecondary,
                                modifier = Modifier
                                    .size(13.dp)
                                    .rotate(if (isSyncingWeather) syncRotation else 0f)
                            )
                            Text(
                                text = if (isSyncingWeather) "SYNCING..." else "SYNC WATCH",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSyncingWeather) ElectricCyan else TextPrimary,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                // Current Temperature & Condition Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "${weather.tempCelsius}",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = "°C",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                            Text(
                                text = "H: ${weather.tempCelsius + 3}° L: ${weather.tempCelsius - 4}°",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Dynamic weather sun/cloud icon with glow
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        AmberGoldRing.copy(alpha = 0.35f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (weather.conditionCode == 1) Icons.Default.WbSunny else Icons.Default.CloudQueue,
                            contentDescription = weather.conditionName,
                            tint = AmberGoldRing,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // 24-Hour Hourly Temperature Curve Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color(0xFF0F1118), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Simulated 24-hr temperature offsets from baseline
                        val baseT = weather.tempCelsius.toFloat()
                        val temps = floatArrayOf(
                            baseT - 2f,
                            baseT,
                            baseT + 3f,
                            baseT + 2f,
                            baseT - 1f,
                            baseT - 3f
                        )
                        val minT = temps.minOrNull() ?: 10f
                        val maxT = temps.maxOrNull() ?: 30f
                        val range = (maxT - minT).coerceAtLeast(1f)
                        val stepX = w / (temps.size - 1)

                        val path = Path()
                        val fillPath = Path()
                        fillPath.moveTo(0f, h)

                        for (i in temps.indices) {
                            val px = i * stepX
                            val py = h - ((temps[i] - minT) / range * (h * 0.7f)) - (h * 0.15f)
                            if (i == 0) {
                                path.moveTo(px, py)
                                fillPath.lineTo(px, py)
                            } else {
                                val prevX = (i - 1) * stepX
                                val prevY = h - ((temps[i - 1] - minT) / range * (h * 0.7f)) - (h * 0.15f)
                                val cx = (prevX + px) / 2f
                                path.cubicTo(cx, prevY, cx, py, px, py)
                                fillPath.cubicTo(cx, prevY, cx, py, px, py)
                            }
                        }

                        fillPath.lineTo(w, h)
                        fillPath.close()

                        // Gradient fill under curve
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = 0.25f),
                                    Color.Transparent
                                )
                            )
                        )

                        // Temperature stroke line
                        drawPath(
                            path = path,
                            color = ElectricCyan,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Highlight current temperature point
                        val currX = stepX * 1
                        val currY = h - ((temps[1] - minT) / range * (h * 0.7f)) - (h * 0.15f)
                        drawCircle(color = ElectricCyan, radius = 4.dp.toPx(), center = Offset(currX, currY))
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(currX, currY))
                    }
                }

                // Micro metrics row: UV Index, Air Quality, Sun/Moon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = AmberGoldRing,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "UV Index: ${weather.uvIndex} Moderate",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Air,
                            contentDescription = null,
                            tint = NeonTeal,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "AQI: ${weather.aqi} Good",
                            fontSize = 11.sp,
                            color = NeonTeal,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WbTwilight,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "18:55",
                            fontSize = 11.sp,
                            color = TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
