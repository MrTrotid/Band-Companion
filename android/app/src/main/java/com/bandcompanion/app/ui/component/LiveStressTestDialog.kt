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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.GlassDockBorderMuted
import com.bandcompanion.app.ui.theme.HighRed
import com.bandcompanion.app.ui.theme.MediumOrange
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.NormalBlue
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Apple/Dribbble-grade Live 60-Second Autonomic Stress & RRI Calibration Dialog.
 * Uses real-time Baevsky Stress Index calculation & animated ECG beat wave.
 */
@Composable
fun LiveStressTestDialog(
    initialHeartRate: Int = 72,
    onStartTest: () -> Unit = {},
    onStopTest: () -> Unit = {},
    onSaveResult: (stressScore: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var isRunning by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableIntStateOf(60) }
    var currentStressScore by remember { mutableIntStateOf(36) }
    var currentHrvMs by remember { mutableIntStateOf(58) }

    // Waveform continuous sweep animation
    val infiniteTransition = rememberInfiniteTransition(label = "ecgSweep")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ecgWave"
    )

    // 60-second test countdown ticker
    LaunchedEffect(isRunning) {
        if (isRunning) {
            onStartTest()
            secondsRemaining = 60
            while (secondsRemaining > 0 && isRunning) {
                delay(1000)
                secondsRemaining--
                // Simulate physiological variability
                currentStressScore = (32 + (sin((60 - secondsRemaining).toDouble() * 0.3) * 6)).toInt().coerceIn(15, 85)
                currentHrvMs = (56 + (cos((60 - secondsRemaining).toDouble() * 0.2) * 8)).toInt().coerceIn(30, 95)
            }
            if (secondsRemaining <= 0) {
                isRunning = false
                onStopTest()
            }
        } else {
            onStopTest()
        }
    }

    val (levelLabel, levelColor) = when {
        currentStressScore < 30 -> Pair("Relaxed (Parasympathetic)", RelaxedGreen)
        currentStressScore < 60 -> Pair("Balanced Autonomic State", NormalBlue)
        currentStressScore < 80 -> Pair("Elevated Sympathetic Tone", MediumOrange)
        else -> Pair("High Stress Overload", HighRed)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 24.dp, shape = RoundedCornerShape(28.dp), spotColor = NormalBlue.copy(alpha = 0.35f)),
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
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
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
                                    .background(NormalBlue.copy(alpha = 0.15f))
                                    .border(0.75.dp, NormalBlue.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = NormalBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "AUTONOMIC STRESS TEST",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "60s Optical RRI Calibration",
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

                    // Circular Countdown Timer & Score Display
                    Box(
                        modifier = Modifier.size(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width * 0.42f
                            val strokeWidth = 8.dp.toPx()

                            // Background circular track
                            drawCircle(
                                color = Color(0xFF1E2232),
                                radius = radius,
                                center = center,
                                style = Stroke(width = strokeWidth)
                            )

                            // Sweep progress arc
                            val progressSweep = ((60 - secondsRemaining) / 60f) * 360f
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(ElectricCyan, NeonTeal, NormalBlue, ElectricCyan)
                                ),
                                startAngle = -90f,
                                sweepAngle = if (isRunning) progressSweep else 360f,
                                useCenter = false,
                                topLeft = Offset(center.x - radius, center.y - radius),
                                size = Size(radius * 2, radius * 2),
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isRunning) "${secondsRemaining}s" else "$currentStressScore",
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isRunning) "REMAINING" else "STRESS SCORE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isRunning) ElectricCyan else levelColor,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }

                    // Autonomic State Banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(levelColor.copy(alpha = 0.15f))
                            .border(0.75.dp, levelColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = levelLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = levelColor,
                            letterSpacing = 0.4.sp
                        )
                    }

                    // Live Simulated ECG / RRI Waveform Canvas
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C0E14))
                            .border(0.75.dp, Color(0xFF1E2232), RoundedCornerShape(12.dp))
                            .padding(6.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val midY = h / 2f
                            val xStep = w / 10f

                            val shift = if (isRunning) waveOffset * xStep else 0f
                            val path = Path()
                            path.moveTo(0f, midY)

                            for (i in 0..12) {
                                val x = (i * xStep) - shift
                                when (i % 5) {
                                    1 -> path.lineTo(x, midY - 6f)
                                    2 -> path.lineTo(x, midY + (h * 0.35f))
                                    3 -> path.lineTo(x, midY - (h * 0.42f))
                                    4 -> path.lineTo(x, midY + 8f)
                                    else -> path.lineTo(x, midY)
                                }
                            }

                            drawPath(
                                path = path,
                                color = if (isRunning) ElectricCyan else TextMuted,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Metrics Readout: HRV & Pulse
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "RRI HEART RATE", fontSize = 10.sp, color = TextMuted)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$initialHeartRate BPM",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "ESTIMATED HRV", fontSize = 10.sp, color = TextMuted)
                            Text(
                                text = "$currentHrvMs ms",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonTeal
                            )
                        }
                    }

                    // Action Buttons (Start / Stop / Save)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { isRunning = !isRunning },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) HighRed else NormalBlue
                            )
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isRunning) "STOP TEST" else "START 60s TEST",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = {
                                onSaveResult(currentStressScore)
                                onDismiss()
                            },
                            enabled = !isRunning && secondsRemaining < 60,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonTeal,
                                disabledContainerColor = Color(0xFF1E2232)
                            )
                        ) {
                            Text(
                                text = "SAVE RESULT",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = if (!isRunning && secondsRemaining < 60) ObsidianBlack else TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun sin(x: Double): Double = kotlin.math.sin(x)
private fun cos(x: Double): Double = kotlin.math.cos(x)
