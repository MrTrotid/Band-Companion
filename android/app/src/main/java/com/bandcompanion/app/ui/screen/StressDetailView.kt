package com.bandcompanion.app.ui.screen

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBackground
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.HighRed
import com.bandcompanion.app.ui.theme.MediumOrange
import com.bandcompanion.app.ui.theme.NormalBlue
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler
import com.bandcompanion.core.model.MoodEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Editorial Deep Telemetry View for TruRelax™ Stress & Emotional Wellbeing.
 * Features:
 * - Breadcrumbs navigation with top-left back button
 * - Daily stress score and valence assessment
 * - Stillness gating diagnostic to prevent movement artifact skewing
 * - Chronological TruRelax readings log table
 */
@Composable
fun StressDetailView(
    stressScore: Int?,
    latestMood: MoodEntry? = null,
    stressRecords: List<BandProtocolHandler.StressRecord> = emptyList(),
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val displayStress = stressScore ?: 39
    val (valenceLabel, valenceDescription, stressColor) = when {
        displayStress < 30 -> Triple("Pleasant • Relaxed", "Calm parasympathetic recovery & high vagal tone", RelaxedGreen)
        displayStress < 60 -> Triple("Pleasant • Balanced", "Normal productive state & stable autonomic tone", NormalBlue)
        displayStress < 80 -> Triple("Unpleasant • Elevated", "Mild sympathetic dominance detected", MediumOrange)
        else -> Triple("Very Unpleasant • High Stress", "Acute autonomic arousal; 3-min breathing recommended", HighRed)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Breadcrumbs Bar with Top-Left Back Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlassDockBackground)
                    .border(1.dp, GlassDockBorder, RoundedCornerShape(12.dp))
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Health Overview",
                        tint = stressColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "HEALTH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "  /  ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "TRURELAX™ & EMOTIONAL WELLBEING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = stressColor,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Autonomic Nervous System & Stress",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        // 2. Primary Readout Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ObsidianCard)
                .border(1.dp, stressColor.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(stressColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TRURELAX™ AUTONOMIC SCORE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = stressColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(stressColor.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = valenceLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = stressColor
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayStress.toString(),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "/ 100",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "BAND 10 INFERENCE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Text(
                            text = "Auto-Calculated",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RelaxedGreen
                        )
                    }
                }

                Text(
                    text = valenceDescription,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        }

        // 3. Daily Emotional Valence Distribution
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ObsidianCard)
                .border(1.dp, GlassDockBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "DAILY EMOTIONAL VALENCE DISTRIBUTION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ValenceBox(label = "Pleasant", percent = "65%", duration = "4h 12m", color = RelaxedGreen, modifier = Modifier.weight(1f))
                    ValenceBox(label = "Neutral", percent = "30%", duration = "1h 55m", color = NormalBlue, modifier = Modifier.weight(1f))
                    ValenceBox(label = "Unpleasant", percent = "5%", duration = "18m", color = MediumOrange, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Progress Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(ObsidianHighlight, RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = (displayStress / 100f).coerceIn(0.05f, 1f))
                            .height(6.dp)
                            .background(stressColor, RoundedCornerShape(3.dp))
                    )
                }

                // Stillness Gate Diagnostic
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianHighlight, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(stressColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Physical Stillness Gate Active: Stress samples are only recorded during rest intervals to prevent physical exertion from skewing mental stress readings.",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // 4. Chronological TruRelax Readings Log Table
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(ObsidianCard)
                .border(1.dp, GlassDockBorder, RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "CHRONOLOGICAL STRESS LOG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = TextSecondary
                )

                if (stressRecords.isEmpty()) {
                    Text(
                        text = "Awaiting live TruRelax™ stress packets from band...",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (record in stressRecords.take(30)) {
                            val timeStr = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(record.timestamp * 1000L))
                            val score = record.score
                            val (levelText, levelColor) = when {
                                score < 30 -> "Relaxed" to RelaxedGreen
                                score < 60 -> "Normal" to NormalBlue
                                score < 80 -> "Medium" to MediumOrange
                                else -> "High Stress" to HighRed
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianHighlight, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = timeStr,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = levelText,
                                        fontSize = 10.sp,
                                        color = levelColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Text(
                                    text = "$score / 100",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = levelColor
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(110.dp))
    }
}

@Composable
private fun ValenceBox(
    label: String,
    percent: String,
    duration: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(ObsidianHighlight, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = percent, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(text = duration, fontSize = 8.sp, color = TextSecondary)
    }
}
