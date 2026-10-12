package com.bandcompanion.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
import android.bluetooth.BluetoothDevice
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.SurfaceDark
import com.bandcompanion.app.ui.theme.SurfaceVariantDark
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.bluetooth.DiscoveredBand

@Composable
fun PairingScreen(
    discoveredBands: List<DiscoveredBand>,
    isScanning: Boolean,
    pairingStatusText: String?,
    isWaitingForWatchCheckmark: Boolean,
    isAwaitingWatchFaceConfirmation: Boolean = false,
    bondState: Int = BluetoothDevice.BOND_NONE,
    onStartScan: () -> Unit,
    onSelectDevice: (DiscoveredBand) -> Unit,
    onConfirmCheckmark: () -> Unit = {},
    onFinishPairing: () -> Unit = {},
    onResendActivation: () -> Unit = {},
    onBackClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radarPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Pair Huawei Band",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        // Animated Radar or Pairing Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .scale(if (isScanning || pairingStatusText != null || isAwaitingWatchFaceConfirmation) pulseScale else 1.0f)
                        .background((if (isAwaitingWatchFaceConfirmation) NeonTeal else ElectricCyan).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWaitingForWatchCheckmark || isAwaitingWatchFaceConfirmation) Icons.Default.CheckCircle else Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        tint = if (isWaitingForWatchCheckmark || isAwaitingWatchFaceConfirmation) NeonTeal else ElectricCyan,
                        modifier = Modifier.size(44.dp)
                    )
                }

                if (isAwaitingWatchFaceConfirmation) {
                    if (bondState == BluetoothDevice.BOND_BONDING) {
                        Text(
                            text = "📱 TAP 'PAIR' ON YOUR PHONE POPUP!",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonTeal
                        )
                        Text(
                            text = "A system Bluetooth pairing dialog or notification has appeared on your phone. Please tap 'Pair' to complete bonding.",
                            fontSize = 13.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    } else {
                        Text(
                            text = "⌚ LOOK AT YOUR WATCH SCREEN!",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonTeal
                        )
                        Text(
                            text = "Does your Huawei Band show 'Paired successfully' or your active watch face?",
                            fontSize = 13.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onFinishPairing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonTeal,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "✓ YES! WATCH FACE IS ACTIVE",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = onResendActivation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SurfaceVariantDark,
                            contentColor = ElectricCyan
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "↻ RESEND ACTIVATION SIGNAL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else if (isWaitingForWatchCheckmark) {
                    Text(
                        text = "👉 TAP CHECKMARK ON YOUR BAND!",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonTeal
                    )
                    Text(
                        text = "Look at your Huawei Band screen right now and tap the checkmark / confirm button.",
                        fontSize = 13.sp,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onConfirmCheckmark,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonTeal,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "✓ I TAPPED CHECKMARK ON WATCH",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (pairingStatusText != null) {
                    Text(
                        text = pairingStatusText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan
                    )
                } else {
                    Text(
                        text = "Searching for nearby Huawei Band 10...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Ensure your band is nearby. (If previously paired, go to Settings -> System -> Disconnect on the band)",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                Button(
                    onClick = onStartScan,
                    enabled = !isScanning && pairingStatusText == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceVariantDark,
                        contentColor = ElectricCyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ElectricCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scanning...")
                    } else {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Again")
                    }
                }
            }
        }

        Text(
            text = "SAVED WEARABLE",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        DiscoveredBandCard(
            band = DiscoveredBand(name = "HUAWEI Band 10-4EF", macAddress = "C4:16:88:8D:04:EF", rssi = -55),
            onClick = {
                onSelectDevice(DiscoveredBand(name = "HUAWEI Band 10-4EF", macAddress = "C4:16:88:8D:04:EF", rssi = -55))
            }
        )

        Text(
            text = "SCAN RESULTS",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        // Device List
        val filteredBands = discoveredBands.filterNot { it.macAddress.equals("C4:16:88:8D:04:EF", ignoreCase = true) }
        if (filteredBands.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(SurfaceDark, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isScanning) "Scanning for other devices..." else "Tap 'Scan Again' to search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredBands) { band ->
                    DiscoveredBandCard(
                        band = band,
                        onClick = { onSelectDevice(band) }
                    )
                }
            }
        }
    }
}

@Composable
fun DiscoveredBandCard(
    band: DiscoveredBand,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(SurfaceVariantDark, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Watch, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = band.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(text = "MAC: ${band.macAddress} • Signal: ${band.rssi} dBm", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Pair", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
