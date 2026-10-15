package com.bandcompanion.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bandcompanion.app.NavigationTab
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.ElectricTealRing
import com.bandcompanion.app.ui.theme.GlassDockBackground
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.GlassDockBorderMuted
import com.bandcompanion.app.ui.theme.GlassPillIndicator
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.TextMuted
import com.bandcompanion.app.ui.theme.TextPrimary
import kotlin.math.roundToInt

/**
 * Dribbble / Apple-inspired Floating Capsule Navigation Dock.
 * Features:
 * - Floating rounded capsule geometry elevated from screen edge
 * - Glassmorphic obsidian translucent backdrop with specular gradient rim stroke
 * - Direct 1:1 finger-matching scrub: dragging across dock selects sections immediately
 * - Optical and geometric icon centering with responsive neon pill tracker
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AthleticNavigationBar(
    pagerState: PagerState,
    onTabSelected: (Int) -> Unit,
    onTabScrubbed: (Int) -> Unit = onTabSelected,
    modifier: Modifier = Modifier
) {
    val tabs = NavigationTab.values()
    val coroutineScope = rememberCoroutineScope()
    var dragContinuousOffset by remember { mutableStateOf<Float?>(null) }

    val continuousOffset = dragContinuousOffset
        ?: (pagerState.currentPage + pagerState.currentPageOffsetFraction)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // Floating glass capsule container with direct 1:1 finger scrubbing
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(29.dp),
                    ambientColor = NeonCyanRing.copy(alpha = 0.25f),
                    spotColor = ElectricCyan.copy(alpha = 0.35f)
                )
                .clip(RoundedCornerShape(29.dp))
                .background(GlassDockBackground)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GlassDockBorder.copy(alpha = 0.7f),
                            GlassDockBorderMuted.copy(alpha = 0.25f),
                            Color(0x0DFFFFFF),
                            GlassDockBorder.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(29.dp)
                )
                .pointerInput(tabs.size) {
                    detectTapGestures { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 0.999f)
                        val targetIndex = (fraction * tabs.size).toInt().coerceIn(0, tabs.size - 1)
                        onTabSelected(targetIndex)
                    }
                }
                .pointerInput(tabs.size) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 0.999f)
                            val exactSection = fraction * tabs.size
                            val targetIndex = exactSection.toInt().coerceIn(0, tabs.size - 1)
                            dragContinuousOffset = targetIndex.toFloat()
                            onTabScrubbed(targetIndex)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val fraction = (change.position.x / size.width).coerceIn(0f, 0.999f)
                            val exactSection = fraction * tabs.size
                            val targetIndex = exactSection.toInt().coerceIn(0, tabs.size - 1)
                            dragContinuousOffset = (exactSection - 0.5f).coerceIn(0f, (tabs.size - 1).toFloat())
                            if (targetIndex != pagerState.currentPage) {
                                onTabScrubbed(targetIndex)
                            }
                        },
                        onDragEnd = {
                            dragContinuousOffset = null
                        },
                        onDragCancel = {
                            dragContinuousOffset = null
                        }
                    )
                }
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalWidth = maxWidth
                val tabWidth = totalWidth / tabs.size

                // Glowing neon pill that tracks touch drag in real-time with 0 lag
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (continuousOffset * tabWidth.toPx()).roundToInt(),
                                y = 0
                            )
                        }
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = 0.22f),
                                    NeonTeal.copy(alpha = 0.10f)
                                )
                            )
                        )
                        .border(
                            width = 0.75.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(25.dp)
                        )
                )

                // Navigation Tab Items Row - Perfectly Centered Icons
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val distanceToSelected = kotlin.math.abs(continuousOffset - index)
                        val proximity = (1f - distanceToSelected).coerceIn(0f, 1f)
                        val iconScale = 1.0f + (0.15f * proximity)

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            // Optically and geometrically centered icon container
                            Box(
                                modifier = Modifier.size(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (proximity > 0.5f) ElectricCyan else TextMuted,
                                    modifier = Modifier
                                        .size(23.dp)
                                        .scale(iconScale)
                                )
                            }

                            // Glowing cyan micro-dot for active tab indicator
                            if (proximity > 0.6f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 5.dp)
                                        .size(4.dp)
                                        .background(
                                            color = ElectricCyan.copy(alpha = proximity),
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AthleticNavigationBar(
    selectedTab: NavigationTab,
    onTabSelected: (NavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = NavigationTab.values()
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .shadow(
                    elevation = 20.dp,
                    shape = RoundedCornerShape(34.dp),
                    ambientColor = NeonCyanRing.copy(alpha = 0.25f),
                    spotColor = ElectricCyan.copy(alpha = 0.35f)
                )
                .clip(RoundedCornerShape(34.dp))
                .background(GlassDockBackground)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            GlassDockBorder.copy(alpha = 0.7f),
                            GlassDockBorderMuted.copy(alpha = 0.25f),
                            Color(0x0DFFFFFF),
                            GlassDockBorder.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(34.dp)
                )
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalWidth = maxWidth
                val tabWidth = totalWidth / tabs.size

                val animatedPillOffset by animateFloatAsState(
                    targetValue = selectedIndex.toFloat(),
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "pillOffset"
                )

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (animatedPillOffset * tabWidth.toPx()).roundToInt(),
                                y = 0
                            )
                        }
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = 0.22f),
                                    NeonTeal.copy(alpha = 0.10f)
                                )
                            )
                        )
                        .border(
                            width = 0.75.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    ElectricCyan.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            ),
                            shape = RoundedCornerShape(28.dp)
                        )
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = tab == selectedTab

                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.15f else 1.0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "iconScale"
                        )

                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) ElectricCyan else TextMuted,
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            label = "tabColor"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onTabSelected(tab)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(26.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = contentColor,
                                        modifier = Modifier
                                            .size(22.dp)
                                            .scale(iconScale)
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = tab.label.uppercase(),
                                    color = if (isSelected) TextPrimary else TextMuted,
                                    fontSize = 8.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
