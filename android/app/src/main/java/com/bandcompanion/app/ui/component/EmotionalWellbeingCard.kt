package com.bandcompanion.app.ui.component

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.SelfImprovement
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
import com.bandcompanion.core.model.MoodEntry

/**
 * Whoop / Oura Athletic Emotional Wellbeing & Autonomic Stress Module.
 * Displays daily stress score (0-100), latest emotional check-in, and log button.
 */
@Composable
fun EmotionalWellbeingCard(
    stressScore: Int?,
    latestMood: MoodEntry?,
    onLogMoodClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayStress = stressScore ?: 35
    val (valenceLabel, valenceDescription, stressColor) = when {
        displayStress < 30 -> Triple("Pleasant • Relaxed", "Calm parasympathetic recovery & low mental fatigue", RelaxedGreen)
        displayStress < 60 -> Triple("Pleasant • Balanced", "Normal productive state & stable autonomic tone", NormalBlue)
        displayStress < 80 -> Triple("Unpleasant • Elevated", "Mild tension detected; sympathetic dominance", MediumOrange)
        else -> Triple("Very Unpleasant • High Stress", "Acute autonomic arousal; 3-min breathing recommended", HighRed)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
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
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stress Score Readout & Mood Badge
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

                if (latestMood != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "LATEST CHECK-IN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Text(
                            text = "${latestMood.moodType} (Energy: ${latestMood.energyLevel}/5)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NormalBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = valenceDescription,
                fontSize = 11.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stress Level Progress Bar (Relaxed, Normal, Medium, High segments)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(ObsidianHighlight, RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = (displayStress / 100f).coerceIn(0.05f, 1f))
                        .height(8.dp)
                        .background(stressColor, RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Check-in Action Button
            Button(
                onClick = onLogMoodClick,
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianHighlight),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.SelfImprovement,
                    contentDescription = null,
                    tint = NormalBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (latestMood != null) "Update Mood Check-in" else "Check in Mood & Energy",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
        }
    }
}
