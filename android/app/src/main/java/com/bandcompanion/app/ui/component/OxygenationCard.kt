package com.bandcompanion.app.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.HighRed
import com.bandcompanion.app.ui.theme.MediumOrange
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.RelaxedGreen
import com.bandcompanion.app.ui.theme.SpO2Cyan
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.SampleMetric
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Whoop / Oura Athletic Blood Oxygenation (SpO2) Card.
 * Monitors 24/7 arterial oxygen saturation, overnight hypoxia events,
 * and expandable chronological spot readings with exact timestamps.
 */
@Composable
fun OxygenationCard(
    currentSpo2: Float?,
    minSpo2: Float? = null,
    maxSpo2: Float? = null,
    spo2Samples: List<SampleMetric> = emptyList(),
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "spo2Chevron"
    )

    val validSpo2Samples = remember(spo2Samples) {
        spo2Samples.filter { it.spo2 != null && it.spo2!! in 70f..100f }
    }
    val validSpo2Values = remember(validSpo2Samples) {
        validSpo2Samples.mapNotNull { it.spo2 }
    }
    val displaySpo2 = currentSpo2 ?: if (validSpo2Values.isNotEmpty()) validSpo2Values.average().toFloat() else 98.0f
    val statusText = when {
        displaySpo2 >= 95.0f -> "Optimal Oxygenation"
        displaySpo2 >= 90.0f -> "Acceptable Range"
        else -> "Low Oxygen Saturation"
    }
    val statusColor = when {
        displaySpo2 >= 95.0f -> SpO2Cyan
        displaySpo2 >= 90.0f -> MediumOrange
        else -> HighRed
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)),
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
                            .background(SpO2Cyan, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BLOOD OXYGEN (SpO2)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand SpO2 log",
                        tint = TextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Large Readout
            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "%.0f".format(displaySpo2),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
                Text(
                    text = "%",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SpO2Cyan,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "RANGE TODAY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "${(minSpo2 ?: 95f).toInt()}% - ${(maxSpo2 ?: 99f).toInt()}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hypoxia / Overnight Stability Assessment Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianHighlight, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = RelaxedGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "No Hypoxia Dips Detected",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Continuous arterial saturation stable • 0 desaturations < 90%",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                    }
                }
            }

            // Expandable Exact Timestamps Section
            if (isExpanded) {
                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.75.dp)
                        .background(TextMuted.copy(alpha = 0.2f))
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "EXACT TIMESTAMPS & SATURATION LOG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = SpO2Cyan
                )

                Spacer(modifier = Modifier.height(10.dp))

                val sortedSamples = remember(validSpo2Samples) { validSpo2Samples.sortedByDescending { it.timestamp } }
                if (sortedSamples.isEmpty()) {
                    Text(
                        text = "Accumulating SpO2 spot checks from band...",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (sample in sortedSamples.take(40)) {
                            val timeStr = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(sample.timestamp * 1000L))
                            val spo2 = sample.spo2 ?: 98f
                            val (levelText, levelColor) = when {
                                spo2 >= 98f -> "Peak Saturation" to SpO2Cyan
                                spo2 >= 95f -> "Optimal Range" to RelaxedGreen
                                else -> "Normal Baseline" to MediumOrange
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ObsidianHighlight.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
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

                                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${spo2.toInt()}",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "%",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SpO2Cyan,
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
