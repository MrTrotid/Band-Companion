package com.bandcompanion.app.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.theme.AmberGoldRing
import com.bandcompanion.app.ui.theme.ElectricTealRing
import com.bandcompanion.app.ui.theme.HotMagentaRing
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.ObsidianHighlight
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.min

/**
 * Concentric 4-Ring Activity Canvas Engine styled after Whoop and Apple Fitness.
 * Renders 4 high-contrast concentric arcs with rounded stroke caps and animated sweeps:
 * 1. Outer Ring (Neon Cyan): Steps
 * 2. Ring 2 (Electric Teal): Active / Exercise Minutes
 * 3. Ring 3 (Hot Magenta): Active Calories (kcal)
 * 4. Inner Ring (Amber Gold): Stand Hours
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConcentricActivityRings(
    stepCount: Int,
    stepTarget: Int = 10000,
    activeMinutes: Int,
    activeMinutesTarget: Int = 30,
    calories: Int,
    calorieTarget: Int = 500,
    standHours: Int,
    standHoursTarget: Int = 12,
    modifier: Modifier = Modifier
) {
    val stepProgress = if (stepTarget > 0) stepCount.toFloat() / stepTarget else 0f
    val activeProgress = if (activeMinutesTarget > 0) activeMinutes.toFloat() / activeMinutesTarget else 0f
    val calorieProgress = if (calorieTarget > 0) calories.toFloat() / calorieTarget else 0f
    val standProgress = if (standHoursTarget > 0) standHours.toFloat() / standHoursTarget else 0f

    val animStep by animateFloatAsState(
        targetValue = stepProgress,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "animStep"
    )
    val animActive by animateFloatAsState(
        targetValue = activeProgress,
        animationSpec = tween(durationMillis = 1200, delayMillis = 100, easing = FastOutSlowInEasing),
        label = "animActive"
    )
    val animCalorie by animateFloatAsState(
        targetValue = calorieProgress,
        animationSpec = tween(durationMillis = 1200, delayMillis = 200, easing = FastOutSlowInEasing),
        label = "animCalorie"
    )
    val animStand by animateFloatAsState(
        targetValue = standProgress,
        animationSpec = tween(durationMillis = 1200, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "animStand"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DAILY ACTIVITY RINGS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = TextSecondary
                )
                Text(
                    text = "${(stepProgress * 100).toInt()}% OF GOAL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NeonCyanRing
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Concentric Canvas & Center Readout
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(230.dp)
            ) {
                Canvas(modifier = Modifier.size(230.dp)) {
                    val strokeWidth = 14.dp.toPx()
                    val spacing = 3.dp.toPx()
                    val halfStroke = strokeWidth / 2f
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)

                    // Radii from outer to inner
                    val radiusStep = (size.width / 2f) - halfStroke
                    val radiusActive = radiusStep - strokeWidth - spacing
                    val radiusCalorie = radiusActive - strokeWidth - spacing
                    val radiusStand = radiusCalorie - strokeWidth - spacing

                    fun drawRing(radius: Float, progress: Float, color: Color) {
                        val topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius)
                        val arcSize = Size(radius * 2f, radius * 2f)

                        // Background track
                        drawArc(
                            color = color.copy(alpha = 0.16f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )

                        // Active sweep
                        val sweep = min(1f, progress) * 360f
                        if (sweep > 0f) {
                            drawArc(
                                color = color,
                                startAngle = -90f,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Overlap sweep if progress > 100%
                        if (progress > 1f) {
                            val overlapSweep = min(1f, progress - 1f) * 360f
                            drawArc(
                                color = Color.White.copy(alpha = 0.35f),
                                startAngle = -90f,
                                sweepAngle = overlapSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }

                    drawRing(radiusStep, animStep, NeonCyanRing)
                    drawRing(radiusActive, animActive, ElectricTealRing)
                    drawRing(radiusCalorie, animCalorie, HotMagentaRing)
                    drawRing(radiusStand, animStand, AmberGoldRing)
                }

                // Center readout
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = NumberFormat.getNumberInstance(Locale.US).format(stepCount),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                    Text(
                        text = "STEPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$calories kcal",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = HotMagentaRing
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4-Ring Metric Badges
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(
                    color = NeonCyanRing,
                    label = "Steps",
                    value = "${NumberFormat.getNumberInstance(Locale.US).format(stepCount)} / ${stepTarget / 1000}k",
                    progress = (stepProgress * 100).toInt(),
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    color = ElectricTealRing,
                    label = "Exercise",
                    value = "${activeMinutes}m / ${activeMinutesTarget}m",
                    progress = (activeProgress * 100).toInt(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(
                    color = HotMagentaRing,
                    label = "Calories",
                    value = "$calories / $calorieTarget kcal",
                    progress = (calorieProgress * 100).toInt(),
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    color = AmberGoldRing,
                    label = "Stand",
                    value = "${standHours}h / ${standHoursTarget}h",
                    progress = (standProgress * 100).toInt(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricChip(
    color: Color,
    label: String,
    value: String,
    progress: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(ObsidianHighlight, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Text(
                        text = "$progress%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }
    }
}
