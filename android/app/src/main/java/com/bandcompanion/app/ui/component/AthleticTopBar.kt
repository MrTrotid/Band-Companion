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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBorderMuted
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.StatusConnectedGreen
import com.bandcompanion.app.ui.theme.StatusConnectingAmber
import com.bandcompanion.app.ui.theme.StatusDisconnectedRed
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.DeviceInfo

/**
 * Editorial Athletic Top Header.
 * Features:
 * - Minimal, uncluttered branding with device model tag
 * - Animated radar wave beacon showing live BLE connection status
 * - Integrated connection status capsule with battery percentage
 * - High-precision circular sync button with spring feedback
 * - Balanced layout without redundant settings bloat
 */
@Composable
fun AthleticTopBar(
    deviceInfo: DeviceInfo?,
    batteryLevel: Int? = null,
    isLiveConnected: Boolean = false,
    isLiveConnecting: Boolean = false,
    isSyncing: Boolean = false,
    onSyncClick: () -> Unit,
    onPairClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isBonded = deviceInfo?.isBonded == true
    val effectiveBattery = batteryLevel ?: deviceInfo?.batteryLevel

    val infiniteTransition = rememberInfiniteTransition(label = "topBarRadar")

    // Radar pulse wave animation
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarWave"
    )

    // Sync button rotation animation
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "syncSpin"
    )

    val beaconColor = when {
        !isBonded -> StatusDisconnectedRed
        isLiveConnected -> StatusConnectedGreen
        isLiveConnecting -> StatusConnectingAmber
        else -> TextMuted
    }

    val statusText = when {
        !isBonded -> "PAIR BAND"
        isSyncing -> "SYNCING..."
        isLiveConnected -> "CONNECTED"
        isLiveConnecting -> "CONNECTING..."
        else -> "OFFLINE"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianBlack,
                        ObsidianBlack.copy(alpha = 0.95f),
                        ObsidianBlack.copy(alpha = 0.85f)
                    )
                )
            )
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Clean, Confident Brand & Hardware Identity
            Column(
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "BAND COMPANION",
                    style = androidx.compose.ui.text.TextStyle(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "HUAWEI BAND 10",
                        color = ElectricCyan.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = " • LOCAL CORE",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Right: Unified Status Capsule + Single Sync Action Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Connection Status & Battery Capsule (34dp height)
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Color(0xFF282C38))
                        .border(
                            width = 0.75.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(beaconColor.copy(alpha = 0.5f), GlassDockBorderMuted)
                            ),
                            shape = RoundedCornerShape(17.dp)
                        )
                        .clickable(enabled = !isBonded) { onPairClick() }
                        .padding(horizontal = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Radar Beacon Canvas
                        Canvas(modifier = Modifier.size(10.dp)) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val baseRadius = 3.dp.toPx()

                            if (isLiveConnected || isLiveConnecting) {
                                val waveRadius = baseRadius + (pulseProgress * 3.dp.toPx())
                                val waveAlpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.6f
                                drawCircle(
                                    color = beaconColor.copy(alpha = waveAlpha),
                                    radius = waveRadius,
                                    center = center,
                                    style = Stroke(width = 1.25.dp.toPx())
                                )
                            }

                            drawCircle(
                                color = beaconColor,
                                radius = baseRadius,
                                center = center
                            )
                        }

                        Text(
                            text = statusText,
                            color = if (isLiveConnected) TextPrimary else beaconColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        )

                        // Battery indicator if connected
                        val battery = effectiveBattery
                        if (isBonded && battery != null && isLiveConnected) {
                            Text(
                                text = "•",
                                color = TextMuted,
                                fontSize = 9.sp
                            )
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = "Battery",
                                tint = if (battery > 20) NeonTeal else StatusConnectingAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "$battery%",
                                color = TextPrimary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Quick Sync Action Button (34dp circle)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF282C38))
                        .border(
                            0.75.dp,
                            if (isSyncing) ElectricCyan.copy(alpha = 0.8f) else GlassDockBorderMuted,
                            CircleShape
                        )
                        .clickable(enabled = !isSyncing) { onSyncClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync",
                        tint = if (isSyncing) ElectricCyan else TextSecondary,
                        modifier = Modifier
                            .size(17.dp)
                            .rotate(if (isSyncing) syncRotation else 0f)
                    )
                }
            }
        }

        // Bottom specular hairline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.75.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            GlassDockBorderMuted.copy(alpha = 0.4f),
                            ElectricCyan.copy(alpha = 0.25f),
                            GlassDockBorderMuted.copy(alpha = 0.4f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
