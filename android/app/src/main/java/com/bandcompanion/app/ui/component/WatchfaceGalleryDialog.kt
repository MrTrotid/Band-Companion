package com.bandcompanion.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bandcompanion.app.ui.theme.AmberGoldRing
import com.bandcompanion.app.ui.theme.CrimsonStrain
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.ElectricTealRing
import com.bandcompanion.app.ui.theme.GlassDockBackground
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.GlassDockBorderMuted
import com.bandcompanion.app.ui.theme.HotMagentaRing
import com.bandcompanion.app.ui.theme.LavenderRecovery
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler.WatchfaceItem

data class GalleryWatchface(
    val id: String,
    val name: String,
    val style: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val isInstalled: Boolean = true
)

/**
 * Apple/Dribbble-grade Watchface Gallery & Live Switcher Dialog.
 * Mimics the Huawei Band 10's 1.47" AMOLED 194x368 display with high-fidelity canvas previews.
 */
@Composable
fun WatchfaceGalleryDialog(
    installedFaces: List<WatchfaceItem> = emptyList(),
    currentFaceId: String? = null,
    onDismiss: () -> Unit,
    onApplyWatchface: (String) -> Unit
) {
    val displayFaces = remember(installedFaces) {
        if (installedFaces.isNotEmpty()) {
            installedFaces.mapIndexed { idx, item ->
                val (color1, color2, style) = when (idx % 6) {
                    0 -> Triple(ElectricCyan, LavenderRecovery, "Athletic Minimal")
                    1 -> Triple(HotMagentaRing, ElectricTealRing, "Activity Hub")
                    2 -> Triple(AmberGoldRing, ElectricCyan, "Precision Analog")
                    3 -> Triple(NeonTeal, ElectricCyan, "OLED Typographic")
                    4 -> Triple(CrimsonStrain, Color.White, "High Contrast")
                    else -> Triple(HotMagentaRing, NeonTeal, "Digital Telemetry")
                }
                GalleryWatchface(
                    id = item.fileName,
                    name = item.displayName.ifEmpty { "Watchface ${idx + 1}" },
                    style = style,
                    primaryColor = color1,
                    secondaryColor = color2,
                    isInstalled = true
                )
            }
        } else {
            listOf(
                GalleryWatchface("default_watch_face", "Aurora Pulse", "Factory Default (LiteOS)", ElectricCyan, LavenderRecovery),
                GalleryWatchface("com.huawei.watchface.panda", "Interactive Panda", "Emotional Wellbeing", NeonTeal, ElectricCyan),
                GalleryWatchface("com.huawei.watchface.sports", "Athletic 4-Ring", "Activity Hub", HotMagentaRing, ElectricTealRing),
                GalleryWatchface("com.huawei.watchface.analog", "Chrono Matrix", "Precision Analog", AmberGoldRing, ElectricCyan),
                GalleryWatchface("2147483647", "Titanium Sport", "Pre-installed ROM", CrimsonStrain, Color.White),
                GalleryWatchface("100001", "Neon Horizon", "AMOLED High Contrast", HotMagentaRing, NeonTeal)
            )
        }
    }

    var selectedFaceId by remember(displayFaces) {
        mutableStateOf(currentFaceId ?: displayFaces.firstOrNull()?.id ?: "default_watch_face")
    }
    var isApplying by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(28.dp), spotColor = ElectricCyan.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(28.dp),
            color = ObsidianBlack
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(GlassDockBorder.copy(alpha = 0.8f), GlassDockBorderMuted, Color.Transparent)
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan.copy(alpha = 0.15f))
                                    .border(0.75.dp, ElectricCyan.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "WATCHFACE STUDIO",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "Huawei Band 10 AMOLED",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ObsidianCard)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Watchface Cards Grid (2 columns)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.height(340.dp)
                    ) {
                        items(displayFaces) { face ->
                            val isSelected = face.id == selectedFaceId
                            WatchfaceCardItem(
                                face = face,
                                isSelected = isSelected,
                                onClick = { selectedFaceId = face.id }
                            )
                        }
                    }

                    // Apply Button
                    Button(
                        onClick = {
                            isApplying = true
                            onApplyWatchface(selectedFaceId)
                        },
                        enabled = !isApplying,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(26.dp),
                                spotColor = ElectricCyan.copy(alpha = 0.5f)
                            ),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            disabledContainerColor = ElectricCyan.copy(alpha = 0.5f)
                        )
                    ) {
                        if (isApplying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = ObsidianBlack,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "APPLYING TO BAND 10...",
                                color = ObsidianBlack,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Watch,
                                contentDescription = null,
                                tint = ObsidianBlack,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "APPLY TO WATCH",
                                color = ObsidianBlack,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatchfaceCardItem(
    face: GalleryWatchface,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (isSelected) ObsidianHighlight else ObsidianCard)
            .border(
                width = if (isSelected) 1.5.dp else 0.75.dp,
                brush = if (isSelected) {
                    Brush.verticalGradient(listOf(ElectricCyan, NeonTeal))
                } else {
                    Brush.verticalGradient(listOf(GlassDockBorderMuted, Color.Transparent))
                },
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // High-fidelity Band 10 Screen Bezel Mockup (194 x 368 aspect ratio ~ 1:1.9)
        Box(
            modifier = Modifier
                .width(88.dp)
                .height(130.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF07080A))
                .border(1.dp, Color(0xFF222634), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                when {
                    face.id.contains("default", ignoreCase = true) || face.id.contains("aurora", ignoreCase = true) -> {
                        // Cyan/violet neon curve
                        val path = androidx.compose.ui.graphics.Path()
                        path.moveTo(0f, h * 0.4f)
                        path.cubicTo(w * 0.3f, h * 0.2f, w * 0.7f, h * 0.6f, w, h * 0.35f)
                        drawPath(path, face.primaryColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    }
                    face.id.contains("sport", ignoreCase = true) || face.id.contains("grid", ignoreCase = true) -> {
                        // 3 concentric activity arcs
                        drawArc(face.primaryColor, 0f, 260f, false, topLeft = Offset(10f, 20f), size = Size(w - 20f, w - 20f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(face.secondaryColor, 0f, 210f, false, topLeft = Offset(20f, 30f), size = Size(w - 40f, w - 40f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(NeonCyanRing, 0f, 310f, false, topLeft = Offset(30f, 40f), size = Size(w - 60f, w - 60f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                    }
                    face.id.contains("analog", ignoreCase = true) || face.id.contains("chrono", ignoreCase = true) -> {
                        // Analog dial with tick markers
                        val center = Offset(w / 2f, h / 2f)
                        drawCircle(Color(0xFF1E212B), radius = w * 0.42f, center = center, style = Stroke(1.5.dp.toPx()))
                        drawLine(face.primaryColor, center, Offset(center.x + 20f, center.y - 30f), strokeWidth = 2.dp.toPx())
                        drawLine(face.secondaryColor, center, Offset(center.x - 22f, center.y + 10f), strokeWidth = 2.dp.toPx())
                        drawCircle(Color.White, radius = 3.dp.toPx(), center = center)
                    }
                    face.id.contains("panda", ignoreCase = true) -> {
                        // Emotional wellbeing face
                        val center = Offset(w / 2f, h / 2f)
                        drawCircle(Color.White.copy(alpha = 0.9f), radius = w * 0.32f, center = center)
                        drawCircle(Color(0xFF1F222B), radius = w * 0.08f, center = Offset(center.x - 14f, center.y - 8f))
                        drawCircle(Color(0xFF1F222B), radius = w * 0.08f, center = Offset(center.x + 14f, center.y - 8f))
                        drawCircle(NeonTeal, radius = w * 0.05f, center = Offset(center.x, center.y + 10f))
                    }
                    else -> {
                        // Digital futuristic HUD
                        drawLine(face.primaryColor, Offset(10f, h * 0.3f), Offset(w - 10f, h * 0.3f), strokeWidth = 2.dp.toPx())
                        drawLine(face.secondaryColor, Offset(10f, h * 0.7f), Offset(w - 10f, h * 0.7f), strokeWidth = 2.dp.toPx())
                    }
                }
            }

            // Simulated Digital Time
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "10:28",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "OCT 14",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = face.primaryColor
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = face.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) TextPrimary else TextSecondary
            )
            Text(
                text = face.style,
                fontSize = 10.sp,
                color = TextMuted
            )
        }

        // Selection radio badge
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (isSelected) ElectricCyan else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (isSelected) ElectricCyan else TextMuted,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = ObsidianBlack,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
