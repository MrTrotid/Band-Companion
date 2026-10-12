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
import androidx.compose.material.icons.filled.Scale
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
import com.bandcompanion.app.ui.theme.AmberGoldRing
import com.bandcompanion.app.ui.theme.ElectricTealRing
import com.bandcompanion.app.ui.theme.HighRed
import com.bandcompanion.app.ui.theme.MediumOrange
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.NormalBlue
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.WeightEntry

/**
 * Whoop / Oura Athletic Weight & Body Mass Index (BMI) Module.
 * Displays latest weight, BMI category, 7-day trend, and logging dialog trigger.
 */
@Composable
fun WeightBmiCard(
    latestWeight: WeightEntry?,
    onLogWeightClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weightKg = latestWeight?.weightKg ?: 70.0f
    val bmi = latestWeight?.bmi ?: 22.86f
    val bmiCategory = latestWeight?.bmiCategory ?: "NORMAL"

    val (bmiColor, bmiLabel) = when (bmiCategory) {
        "UNDERWEIGHT" -> AmberGoldRing to "Underweight (<18.5)"
        "NORMAL" -> RelaxedGreen to "Normal Weight (18.5 - 24.9)"
        "OVERWEIGHT" -> MediumOrange to "Overweight (25 - 29.9)"
        else -> HighRed to "Obese (≥30)"
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
                            .background(ElectricTealRing, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BODY COMPOSITION & BMI",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = TextSecondary
                    )
                }

                Text(
                    text = "BMI %.1f • %s".format(bmi, bmiCategory),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = bmiColor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Weight Large Readout
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "%.1f".format(weightKg),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = " kg",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricTealRing,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "POUNDS (LBS)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "%.1f lbs".format(weightKg * 2.20462f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BMI Spectrum Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(ObsidianHighlight, RoundedCornerShape(4.dp))
                ) {
                    // Underweight (up to 18.5)
                    Box(
                        modifier = Modifier
                            .weight(18.5f)
                            .height(8.dp)
                            .background(AmberGoldRing.copy(alpha = 0.5f), RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                    )
                    // Normal (18.5 to 24.9 = 6.4)
                    Box(
                        modifier = Modifier
                            .weight(6.4f)
                            .height(8.dp)
                            .background(RelaxedGreen)
                    )
                    // Overweight (25 to 29.9 = 5)
                    Box(
                        modifier = Modifier
                            .weight(5f)
                            .height(8.dp)
                            .background(MediumOrange.copy(alpha = 0.6f))
                    )
                    // Obese (30+)
                    Box(
                        modifier = Modifier
                            .weight(10f)
                            .height(8.dp)
                            .background(HighRed.copy(alpha = 0.6f), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = bmiLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Log Weight Action Button
            Button(
                onClick = onLogWeightClick,
                colors = ButtonDefaults.buttonColors(containerColor = ObsidianHighlight),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Scale,
                    contentDescription = null,
                    tint = ElectricTealRing,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (latestWeight != null) "Update Weight (Current: %.1f kg)".format(weightKg) else "Log Body Weight",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
        }
    }
}
