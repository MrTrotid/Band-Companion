package com.bandcompanion.app.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.GlassDockBorderMuted
import com.bandcompanion.app.ui.theme.HotMagentaRing
import com.bandcompanion.app.ui.theme.LavenderRecovery
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.StatusConnectedGreen
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.DeviceInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Interactive Hardware Watch Hero component.
 * Features:
 * - Centered vector/canvas representation of Huawei Band 10 (1.47" AMOLED display, titanium frame, strap, side button)
 * - Live on-wrist OLED watchface showing current time, heart rate, and step count
 * - Outward radiating callout connector lines with glowing anchor dots and directional arrowheads pointing to 4 feature hubs:
 *   1. Top-Left: 1.47" AMOLED Display -> Watchface Studio
 *   2. Top-Right: Optical PPG Sensor -> Continuous HR & SpO2
 *   3. Bottom-Left: 9-Axis Motion IMU -> 4-Ring Activity Engine
 *   4. Bottom-Right: Autonomic RRI -> TruSleep & HRV Stress Engine
 */
@Composable
fun WatchHeroCard(
    deviceInfo: DeviceInfo?,
    currentPulseBpm: Int?,
    todaySteps: Int?,
    batteryLevel: Int? = null,
    isLiveConnected: Boolean,
    onWatchfaceClick: () -> Unit,
    onPulseClick: () -> Unit,
    onActivityClick: () -> Unit,
    onStressClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTimeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    val currentDayString = SimpleDateFormat("EEE d", Locale.getDefault()).format(Date()).uppercase()

    val infiniteTransition = rememberInfiniteTransition(label = "heroPulseTransition")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = ElectricCyan.copy(alpha = 0.15f)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    0.75.dp,
                    Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.6f), Color.Transparent)),
                    RoundedCornerShape(22.dp)
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Bar: Device Label & Architecture Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isLiveConnected) StatusConnectedGreen else TextMuted)
                    )
                    Text(
                        text = "DEVICE ARCHITECTURE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextMuted,
                        letterSpacing = 1.0.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianHighlight)
                        .border(0.5.dp, GlassDockBorderMuted, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = deviceInfo?.name ?: "HUAWEI BAND 10",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Central Interactive Canvas Area with Watch & Radiating Callouts
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                val areaWidth = maxWidth
                val areaHeight = maxHeight
                val colWidth = (areaWidth - 110.dp) / 2

                // Canvas drawing radiating arrows behind the cards
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f

                    // Watch anchor coordinates (based on 76dp wide, 140dp tall case)
                    val watchHalfW = 38.dp.toPx()
                    val watchHalfH = 68.dp.toPx()

                    val pTopLeftWatch = Offset(cx - watchHalfW, cy - (watchHalfH * 0.45f))
                    val pTopRightWatch = Offset(cx + watchHalfW, cy - (watchHalfH * 0.45f))
                    val pBottomLeftWatch = Offset(cx - watchHalfW, cy + (watchHalfH * 0.45f))
                    val pBottomRightWatch = Offset(cx + watchHalfW, cy + (watchHalfH * 0.45f))

                    // Target positions near the callout cards
                    val pTopLeftTarget = Offset(cx - watchHalfW - 16.dp.toPx(), cy - (areaHeight.toPx() * 0.28f))
                    val pTopRightTarget = Offset(cx + watchHalfW + 16.dp.toPx(), cy - (areaHeight.toPx() * 0.28f))
                    val pBottomLeftTarget = Offset(cx - watchHalfW - 16.dp.toPx(), cy + (areaHeight.toPx() * 0.28f))
                    val pBottomRightTarget = Offset(cx + watchHalfW + 16.dp.toPx(), cy + (areaHeight.toPx() * 0.28f))

                    // Helper to draw an arrow with glowing anchor dot
                    fun drawCalloutArrow(from: Offset, to: Offset, color: Color) {
                        // Glowing anchor dot on the watch body
                        drawCircle(
                            color = color.copy(alpha = 0.4f * pulseGlow),
                            radius = 5.dp.toPx(),
                            center = from
                        )
                        drawCircle(
                            color = color,
                            radius = 2.5.dp.toPx(),
                            center = from
                        )

                        // Path from watch anchor to callout
                        val path = Path().apply {
                            moveTo(from.x, from.y)
                            val midX = (from.x + to.x) / 2f
                            cubicTo(
                                midX, from.y,
                                midX, to.y,
                                to.x, to.y
                            )
                        }

                        // Glow line
                        drawPath(
                            path = path,
                            color = color.copy(alpha = 0.35f * pulseGlow),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        // Core sharp line
                        drawPath(
                            path = path,
                            color = color.copy(alpha = 0.9f),
                            style = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Directional Arrow Head pointing to target
                        val angle = atan2(to.y - from.y, to.x - from.x)
                        val arrowLen = 6.dp.toPx()
                        val arrowAngle = PI / 6f // 30 degrees

                        val arrowP1 = Offset(
                            to.x - arrowLen * cos(angle - arrowAngle).toFloat(),
                            to.y - arrowLen * sin(angle - arrowAngle).toFloat()
                        )
                        val arrowP2 = Offset(
                            to.x - arrowLen * cos(angle + arrowAngle).toFloat(),
                            to.y - arrowLen * sin(angle + arrowAngle).toFloat()
                        )

                        val arrowPath = Path().apply {
                            moveTo(to.x, to.y)
                            lineTo(arrowP1.x, arrowP1.y)
                            moveTo(to.x, to.y)
                            lineTo(arrowP2.x, arrowP2.y)
                        }
                        drawPath(
                            path = arrowPath,
                            color = color,
                            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Draw 4 callout arrows
                    drawCalloutArrow(pTopLeftWatch, pTopLeftTarget, ElectricCyan)
                    drawCalloutArrow(pTopRightWatch, pTopRightTarget, HotMagentaRing)
                    drawCalloutArrow(pBottomLeftWatch, pBottomLeftTarget, NeonTeal)
                    drawCalloutArrow(pBottomRightWatch, pBottomRightTarget, LavenderRecovery)
                }

                // Callout Columns (Left and Right)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Display (Top) & Motion IMU (Bottom)
                    Column(
                        modifier = Modifier
                            .width(colWidth)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        CalloutCard(
                            title = "AMOLED",
                            subtitle = "Watchfaces",
                            icon = Icons.Default.Palette,
                            accentColor = ElectricCyan,
                            isLeft = true,
                            onClick = onWatchfaceClick
                        )

                        CalloutCard(
                            title = "9-Axis IMU",
                            subtitle = "Activity Rings",
                            icon = Icons.AutoMirrored.Filled.DirectionsRun,
                            accentColor = NeonTeal,
                            isLeft = true,
                            onClick = onActivityClick
                        )
                    }

                    Spacer(modifier = Modifier.width(100.dp)) // Space for central watch

                    // Right Column: Optical PPG (Top) & Autonomic RRI (Bottom)
                    Column(
                        modifier = Modifier
                            .width(colWidth)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        CalloutCard(
                            title = "PPG Sensor",
                            subtitle = "Pulse & SpO2",
                            icon = Icons.Default.Favorite,
                            accentColor = HotMagentaRing,
                            isLeft = false,
                            onClick = onPulseClick
                        )

                        CalloutCard(
                            title = "Autonomic",
                            subtitle = "Sleep & Stress",
                            icon = Icons.Default.Psychology,
                            accentColor = LavenderRecovery,
                            isLeft = false,
                            onClick = onStressClick
                        )
                    }
                }

                // Centered Realistic Huawei Band 10 Vector Graphic
                CentralBandIllustration(
                    currentTime = currentTimeString,
                    currentDate = currentDayString,
                    currentPulse = currentPulseBpm ?: 72,
                    currentSteps = todaySteps ?: 0,
                    isConnected = isLiveConnected
                )
            }
        }
    }
}

/**
 * High-fidelity Vector Illustration of the Huawei Band 10.
 * Accurately depicts:
 * - 1.47" AMOLED display with 194x368 proportions
 * - Curved titanium chassis
 * - Right physical button
 * - Matte silicone ribbed strap
 * - Live OLED watchface rendering
 */
@Composable
private fun CentralBandIllustration(
    currentTime: String,
    currentDate: String,
    currentPulse: Int,
    currentSteps: Int,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(86.dp)
            .height(230.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background Straps & Chassis Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f

            // Silicone Strap Dimensions
            val strapWidth = 44.dp.toPx()
            val strapTopH = 50.dp.toPx()
            val strapBottomH = 50.dp.toPx()

            // Top Strap
            drawRoundRect(
                color = Color(0xFF1E212A),
                topLeft = Offset(cx - (strapWidth / 2f), 0f),
                size = Size(strapWidth, strapTopH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            // Top Strap grooves
            for (i in 1..3) {
                val y = i * 11.dp.toPx()
                drawLine(
                    color = Color(0xFF161820),
                    start = Offset(cx - (strapWidth / 2f) + 6.dp.toPx(), y),
                    end = Offset(cx + (strapWidth / 2f) - 6.dp.toPx(), y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Bottom Strap
            drawRoundRect(
                color = Color(0xFF1E212A),
                topLeft = Offset(cx - (strapWidth / 2f), h - strapBottomH),
                size = Size(strapWidth, strapBottomH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            // Bottom Strap grooves
            for (i in 1..3) {
                val y = h - strapBottomH + (i * 11.dp.toPx())
                drawLine(
                    color = Color(0xFF161820),
                    start = Offset(cx - (strapWidth / 2f) + 6.dp.toPx(), y),
                    end = Offset(cx + (strapWidth / 2f) - 6.dp.toPx(), y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Titanium Band 10 Body Case
            val caseW = 68.dp.toPx()
            val caseH = 138.dp.toPx()
            val caseLeft = cx - (caseW / 2f)
            val caseTop = cy - (caseH / 2f)

            // Outer drop shadow / ambient glow
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(ElectricCyan.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = caseH * 0.7f
                ),
                topLeft = Offset(caseLeft - 6.dp.toPx(), caseTop - 6.dp.toPx()),
                size = Size(caseW + 12.dp.toPx(), caseH + 12.dp.toPx()),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
            )

            // Titanium Bezel
            drawRoundRect(
                color = Color(0xFF323746),
                topLeft = Offset(caseLeft, caseTop),
                size = Size(caseW, caseH),
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx())
            )
            // Specular metallic edge stroke
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF5A627A), Color(0xFF232733), Color(0xFF5A627A)),
                    startY = caseTop,
                    endY = caseTop + caseH
                ),
                topLeft = Offset(caseLeft, caseTop),
                size = Size(caseW, caseH),
                cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                style = Stroke(width = 1.25.dp.toPx())
            )

            // Physical Right Button
            val btnW = 3.5.dp.toPx()
            val btnH = 22.dp.toPx()
            val btnX = caseLeft + caseW
            val btnY = cy - (btnH / 2f)
            drawRoundRect(
                color = Color(0xFF4A5266),
                topLeft = Offset(btnX, btnY),
                size = Size(btnW, btnH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )

            // AMOLED Screen Bezel (Pure OLED Black)
            val screenW = 54.dp.toPx()
            val screenH = 120.dp.toPx()
            val screenLeft = cx - (screenW / 2f)
            val screenTop = cy - (screenH / 2f)

            drawRoundRect(
                color = Color(0xFF07080B),
                topLeft = Offset(screenLeft, screenTop),
                size = Size(screenW, screenH),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
            )
            // Subtle glass edge reflection
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0x33FFFFFF), Color.Transparent),
                    start = Offset(screenLeft, screenTop),
                    end = Offset(screenLeft + (screenW * 0.4f), screenTop + (screenH * 0.4f))
                ),
                topLeft = Offset(screenLeft, screenTop),
                size = Size(screenW, screenH),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = Stroke(width = 0.75.dp.toPx())
            )
        }

        // Live OLED Display Content
        Column(
            modifier = Modifier
                .width(52.dp)
                .height(116.dp)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Status bar dot & date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) NeonTeal else Color(0xFFFFB703))
                )
                Text(
                    text = currentDate,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
            }

            // Center: Big Neon Clock
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTime,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = ElectricCyan,
                    letterSpacing = (-0.5).sp
                )
            }

            // Bottom: Pulse & Step mini metrics
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Heart Rate
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = HotMagentaRing,
                        modifier = Modifier.size(7.dp)
                    )
                    Text(
                        text = "$currentPulse",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }

                // Mini Steps Indicator Bar
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color(0xFF1E212A))
                ) {
                    val progress = (currentSteps / 10000f).coerceIn(0.1f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .clip(RoundedCornerShape(1.5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(NeonTeal, ElectricCyan)
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * Callout Card positioned in each quadrant.
 */
@Composable
private fun CalloutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isLeft: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianHighlight)
            .border(
                0.75.dp,
                Brush.horizontalGradient(
                    if (isLeft) {
                        listOf(accentColor.copy(alpha = 0.5f), GlassDockBorderMuted)
                    } else {
                        listOf(GlassDockBorderMuted, accentColor.copy(alpha = 0.5f))
                    }
                ),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End
        ) {
            if (isLeft) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
