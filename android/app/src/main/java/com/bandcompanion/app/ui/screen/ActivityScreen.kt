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
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.ui.component.ConcentricActivityRings
import com.bandcompanion.app.ui.theme.AmberGoldRing
import com.bandcompanion.app.ui.theme.ElectricTealRing
import com.bandcompanion.app.ui.theme.GlassDockBorderMuted
import com.bandcompanion.app.ui.theme.HotMagentaRing
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.ObsidianBlack
import com.bandcompanion.app.ui.theme.ObsidianCard
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import com.bandcompanion.app.ui.theme.TextSecondary
import com.bandcompanion.core.model.DailySummary

/**
 * Editorial-grade Movement & Athletic Workload Screen (Destination 4: ACTIVITY).
 * Features:
 * 1. 4-Ring Concentric Activity Gauge (Steps, MVPA Exercise, Calories, Stand Hours).
 * 2. Activity Metrics Grid with progress towards goals.
 * 3. Full Huawei Health 5-Zone Cardiac Training Breakdown.
 * 4. Cardiorespiratory Aerobic Capacity (VO2 Max 49.4 mL/kg/min).
 * 5. Acute-to-Chronic Workload Ratio (ACWR) with 14-day calibration guard.
 */
@Composable
fun ActivityScreen(
    todaySummary: DailySummary?,
    recentSummaries: List<DailySummary> = emptyList(),
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val totalSteps = todaySummary?.totalSteps ?: 2005
    val exerciseMinutes = todaySummary?.activeMinutes ?: 9
    val calories = todaySummary?.caloriesBurned ?: 49
    val standHours = todaySummary?.standHours ?: 6

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "ATHLETIC MOVEMENT & EXERCISE",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )

        // 1. Concentric Activity Rings Card
        ConcentricActivityRings(
            stepCount = totalSteps,
            stepTarget = 10000,
            activeMinutes = exerciseMinutes,
            activeMinutesTarget = 30,
            calories = calories,
            calorieTarget = 500,
            standHours = standHours,
            standHoursTarget = 12
        )

        // 2. Cardio Training Zones Breakdown
        CardioZonesCard()

        // 3. Cardiorespiratory Aerobic Fitness (VO2 Max)
        Vo2MaxCard()

        // 4. Acute : Chronic Workload Ratio (ACWR) Timeline
        AcwrTrendChartCard(summaries = recentSummaries)

        Spacer(modifier = Modifier.height(110.dp))
    }
}
