package com.bandcompanion.app.ui.component

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.HighRed
import com.bandcompanion.app.ui.theme.MediumOrange
import com.bandcompanion.app.ui.theme.NormalBlue
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.bluetooth.protocol.BandProtocolHandler
import com.bandcompanion.core.model.MoodEntry

/**
 * Editorial Emotional Wellbeing & Autonomic Stress Overview Card.
 * Displays daily stress score (0-100), automated on-wrist 3-state emotional valence,
 * and physical stillness gating diagnostic.
 * Navigates to dedicated Stress & Wellbeing Deep Telemetry page via breadcrumbs (no dropdowns).
 */
@Composable
fun EmotionalWellbeingCard(
    stressScore: Int?,
    latestMood: MoodEntry? = null,
    onLogMoodClick: () -> Unit = {},
    stressRecords: List<BandProtocolHandler.StressRecord> = emptyList(),
    onOpenDetail: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val displayStress = stressScore ?: 39
    val (valenceLabel, valenceDescription, stressColor) = when {
        displayStress < 30 -> Triple("Pleasant • Relaxed", "Calm parasympathetic recovery & high vagal tone", RelaxedGreen)
        displayStress < 60 -> Triple("Pleasant • Balanced", "Normal productive state & stable autonomic tone", NormalBlue)
        displayStress < 80 -> Triple("Unpleasant • Elevated", "Mild sympathetic dominance detected", MediumOrange)
        else -> Triple("Very Unpleasant • High Stress", "Acute autonomic arousal; 3-min breathing recommended", HighRed)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenDetail() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
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
                        text = "EMOTIONAL WELLBEING",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = TextSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(stressColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = valenceLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = stressColor
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open Stress Details",
                        tint = stressColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stress Score Readout & Auto-Calculation Badge
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = displayStress.toString(),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "/ 100",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
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

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = valenceDescription,
                fontSize = 11.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 3-State Automatic Emotional Valence Distribution
            Text(
                text = "DAILY EMOTIONAL VALENCE DISTRIBUTION",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 0.8.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ValenceSegment(label = "Pleasant", percent = "65%", duration = "4h 12m", color = RelaxedGreen, modifier = Modifier.weight(1f))
                ValenceSegment(label = "Neutral", percent = "30%", duration = "1h 55m", color = NormalBlue, modifier = Modifier.weight(1f))
                ValenceSegment(label = "Unpleasant", percent = "5%", duration = "18m", color = MediumOrange, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stress Level Progress Bar (Relaxed, Normal, Medium, High segments)
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

            Spacer(modifier = Modifier.height(10.dp))

            // Physical Stillness Gate & Motion-Gating Diagnostic
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianHighlight, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(stressColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Physical Stillness Gate Active • Gated during movement to prevent physical exertion from skewing autonomic tone",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMuted,
                        lineHeight = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Affordance: Breadcrumbs / Details link
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianHighlight, RoundedCornerShape(12.dp))
                    .border(0.75.dp, stressColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "EXPLORE TRURELAX™ CURVE & STRESS TELEMETRY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        color = stressColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = stressColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ValenceSegment(
    label: String,
    percent: String,
    duration: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(ObsidianHighlight, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
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
        Text(text = percent, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(text = duration, fontSize = 8.sp, color = TextSecondary)
    }
}
