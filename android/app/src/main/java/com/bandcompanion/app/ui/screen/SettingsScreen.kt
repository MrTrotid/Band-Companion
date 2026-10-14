package com.bandcompanion.app.ui.screen

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.GlassDockBorderMuted
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.StatusConnectingAmber
import com.bandcompanion.app.ui.theme.StatusDisconnectedRed
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.DeviceInfo

/**
 * Apple/Dribbble-grade Device Settings & Hardware Preferences Screen.
 * Controls:
 * - Find My Band vibration alert
 * - Wearing wrist selection (Left vs Right)
 * - Lift-to-Wake (Raise wrist to wake)
 * - Sedentary Stand Reminders
 * - Watchface Studio & Autonomic Stress Calibration launches
 * - Local-first privacy guarantees & data exports
 */
@Composable
fun SettingsScreen(
    deviceInfo: DeviceInfo?,
    isLiveConnected: Boolean = false,
    isLiveConnecting: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onExportCsvClick: () -> Unit,
    onExportJsonClick: () -> Unit,
    onFindBandClick: () -> Unit = {},
    onWristChanged: (isRightWrist: Boolean) -> Unit = {},
    onLiftToWakeChanged: (enabled: Boolean) -> Unit = {},
    onActivityReminderChanged: (enabled: Boolean) -> Unit = {},
    onOpenWatchfacesClick: () -> Unit = {},
    onOpenStressTestClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    var isRightWrist by remember { mutableStateOf(false) }
    var isLiftToWakeEnabled by remember { mutableStateOf(true) }
    var isStandReminderEnabled by remember { mutableStateOf(true) }

    val isBonded = deviceInfo?.isBonded == true
    val (badgeText, badgeColor) = when {
        !isBonded -> Pair("NOT PAIRED", StatusDisconnectedRed)
        isLiveConnected -> Pair("CONNECTED", NeonTeal)
        isLiveConnecting -> Pair("CONNECTING...", StatusConnectingAmber)
        else -> Pair("DISCONNECTED", TextMuted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ObsidianCard)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = "HARDWARE & SYSTEM PREFERENCES",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )
        }

        // Paired Hardware Card
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
                    .border(
                        0.75.dp,
                        Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.BluetoothConnected, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "PAIRED BAND", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                        }

                        // Live Dynamic Status badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.15f))
                                .border(0.5.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(text = badgeText, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = badgeColor)
                        }
                    }

                    Text(text = deviceInfo?.name ?: "HUAWEI Band 10", fontSize = 20.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    Text(text = "MAC: ${deviceInfo?.macAddress ?: "C4:16:88:8D:04:EF"}", fontSize = 12.sp, color = TextSecondary)
                    Text(text = "Transport: Huawei LPv2 • HiChain3 Application Layer", fontSize = 11.sp, color = NeonTeal, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Hardware Controls Card (Find My Band, Wrist, Lift to Wake)
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
                    .border(
                        0.75.dp,
                        Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Watch, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "ON-WRIST HARDWARE CONTROLS", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    }

                    // Find My Band Action Button
                    Button(
                        onClick = onFindBandClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(23.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ObsidianHighlight,
                            contentColor = ElectricCyan
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Vibration, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "FIND MY BAND (RING & VIBRATE)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Wearing Wrist Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Wearing Wrist", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = if (isRightWrist) "Right wrist" else "Left wrist", fontSize = 11.sp, color = TextMuted)
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianHighlight)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (!isRightWrist) ElectricCyan else Color.Transparent)
                                    .clickable {
                                        isRightWrist = false
                                        onWristChanged(false)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "LEFT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isRightWrist) ObsidianBlack else TextSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isRightWrist) ElectricCyan else Color.Transparent)
                                    .clickable {
                                        isRightWrist = true
                                        onWristChanged(true)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "RIGHT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRightWrist) ObsidianBlack else TextSecondary
                                )
                            }
                        }
                    }

                    // Lift-to-Wake Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            Column {
                                Text(text = "Raise Wrist to Wake", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(text = "Automatically light screen on wrist movement", fontSize = 11.sp, color = TextMuted)
                            }
                        }

                        Switch(
                            checked = isLiftToWakeEnabled,
                            onCheckedChange = {
                                isLiftToWakeEnabled = it
                                onLiftToWakeChanged(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ElectricCyan,
                                checkedTrackColor = ElectricCyan.copy(alpha = 0.35f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = ObsidianHighlight
                            )
                        )
                    }

                    // Sedentary Stand Reminder Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AccessibilityNew, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            Column {
                                Text(text = "Activity Stand Reminders", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(text = "Alert to move after 50 minutes of inactivity", fontSize = 11.sp, color = TextMuted)
                            }
                        }

                        Switch(
                            checked = isStandReminderEnabled,
                            onCheckedChange = {
                                isStandReminderEnabled = it
                                onActivityReminderChanged(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonTeal,
                                checkedTrackColor = NeonTeal.copy(alpha = 0.35f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = ObsidianHighlight
                            )
                        )
                    }
                }
            }
        }

        // Quick Studio Launchers: Watchfaces & Stress Testing
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Watchface Studio Launcher Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(115.dp)
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(20.dp), spotColor = ElectricCyan.copy(alpha = 0.2f))
                    .clickable { onOpenWatchfacesClick() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            0.75.dp,
                            Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
                        Column {
                            Text(text = "Watchfaces", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "Browse & Switch", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }

            // Stress Calibration Launcher Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(115.dp)
                    .shadow(elevation = 10.dp, shape = RoundedCornerShape(20.dp), spotColor = NeonTeal.copy(alpha = 0.2f))
                    .clickable { onOpenStressTestClick() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCard)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            0.75.dp,
                            Brush.verticalGradient(listOf(GlassDockBorder.copy(alpha = 0.5f), Color.Transparent)),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(imageVector = Icons.Default.Psychology, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(22.dp))
                        Column {
                            Text(text = "Stress Test", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(text = "60s Calibration", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }
        }

        // Data Export Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        0.75.dp,
                        Brush.verticalGradient(listOf(GlassDockBorderMuted, Color.Transparent)),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "LOCAL DATA EXPORT", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "Export your telemetry and sleep architecture directly to CSV or JSON. No cloud servers are involved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onExportCsvClick,
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianHighlight, contentColor = ElectricCyan),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Export CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onExportJsonClick,
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianHighlight, contentColor = NeonTeal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Export JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Privacy Guarantee Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = NeonTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "PRIVACY PROMISE", style = MaterialTheme.typography.labelSmall, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = "Band Companion communicates strictly over peer-to-peer Bluetooth Low Energy. No account, no Huawei Cloud, and zero remote tracking.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}
