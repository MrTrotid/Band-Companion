package com.bandcompanion.app.ui.component

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
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBackground
import com.bandcompanion.app.ui.theme.GlassDockBorder
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Apple/Dribbble-styled Athletic Top Navigation & Device Control Panel.
 * Features:
 * - Frosted glass translucent header with specular gradient accent line
 * - Animated radar wave beacon indicating live BLE link activity
 * - Device status capsule with live battery percentage
 * - Interactive 360° spinning sync button with spring feedback
 * - Date and high-contrast athletic typography
 */
@Composable
fun AthleticTopBar(
    deviceInfo: DeviceInfo?,
    isSyncing: Boolean,
    onSyncClick: () -> Unit,
    onPairClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateString = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date()).uppercase()
    val isBonded = deviceInfo?.isBonded == true
    val isConnected = deviceInfo?.isConnected == true

    // Infinite transition for continuous subtle animations
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

    // Sync button rotation
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
        isConnected -> StatusConnectedGreen
        else -> StatusConnectingAmber
    }

    val statusText = when {
        !isBonded -> "PAIR BAND"
        isSyncing -> "SYNCING..."
        isConnected -> "CONNECTED"
        else -> "SEARCHING"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianBlack,
                        GlassDockBackground.copy(alpha = 0.95f),
                        ObsidianBlack.copy(alpha = 0.85f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Athletic Brand Title & Today's Date
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ATHLETIC HUB",
                        style = androidx.compose.ui.text.TextStyle(
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    )

                    // Small neon badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ElectricCyan.copy(alpha = 0.15f))
                            .border(0.5.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "BAND 10",
                            color = ElectricCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = dateString,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp
                )
            }

            // Right: Device Capsule & Action Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Connection Status Capsule
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF161922))
                        .border(
                            width = 0.75.dp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(beaconColor.copy(alpha = 0.5f), GlassDockBorderMuted)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable(enabled = !isBonded) { onPairClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Radar Beacon Canvas
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val baseRadius = 4.dp.toPx()

                            // Expanding pulse wave when active
                            if (isConnected || !isBonded) {
                                val waveRadius = baseRadius + (pulseProgress * 4.dp.toPx())
                                val waveAlpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.6f
                                drawCircle(
                                    color = beaconColor.copy(alpha = waveAlpha),
                                    radius = waveRadius,
                                    center = center,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }

                            // Solid center beacon dot
                            drawCircle(
                                color = beaconColor,
                                radius = baseRadius,
                                center = center
                            )
                        }

                        Text(
                            text = statusText,
                            color = if (isConnected) TextPrimary else beaconColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        )

                        // Battery indicator if connected
                        val battery = deviceInfo?.batteryLevel
                        if (isBonded && battery != null) {
                            Text(
                                text = "•",
                                color = TextMuted,
                                fontSize = 10.sp
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
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Quick Sync Action Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161922))
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
                            .size(18.dp)
                            .rotate(if (isSyncing) syncRotation else 0f)
                    )
                }
            }
        }

        // Bottom specular highlight gradient hairline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.75.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            GlassDockBorder.copy(alpha = 0.6f),
                            GlassDockBorderMuted.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
