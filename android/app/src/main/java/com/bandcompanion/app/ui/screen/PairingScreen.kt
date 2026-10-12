package com.bandcompanion.app.ui.screen

import androidx.compose.animation.AnimatedVisibility
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
    onStartScan: () -> Unit,
    onSelectDevice: (DiscoveredBand) -> Unit,
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
                        .scale(if (isScanning || pairingStatusText != null) pulseScale else 1.0f)
                        .background(ElectricCyan.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWaitingForWatchCheckmark) Icons.Default.CheckCircle else Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        tint = if (isWaitingForWatchCheckmark) NeonTeal else ElectricCyan,
                        modifier = Modifier.size(44.dp)
                    )
                }

                if (isWaitingForWatchCheckmark) {
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
            text = "DISCOVERED DEVICES",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )

        // Device List
        if (discoveredBands.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(SurfaceDark, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isScanning) "Searching for Bluetooth signals..." else "No bands found yet. Tap 'Scan Again'.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(discoveredBands) { band ->
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
