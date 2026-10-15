package com.bandcompanion.app.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bandcompanion.app.NavigationTab
import com.bandcompanion.app.ui.theme.ElectricCyan
import com.bandcompanion.app.ui.theme.GlassDockBackground
import com.bandcompanion.app.ui.theme.GlassDockBorder
import com.bandcompanion.app.ui.theme.GlassDockBorderMuted
import com.bandcompanion.app.ui.theme.NeonCyanRing
import com.bandcompanion.app.ui.theme.NeonTeal
import com.bandcompanion.app.ui.theme.TextMuted
import kotlin.math.roundToInt

/**
 * Editorial Floating Capsule Navigation Dock.
 * Features:
 * - Floating rounded capsule geometry elevated from screen edge
 * - Glassmorphic obsidian translucent backdrop with specular gradient rim stroke
 * - Continuous physical gesture tracking: dragging on the dock scrolls the main HorizontalPager smoothly in real-time
 * - Tap gesture: glides smoothly to the selected page
 * - Geometric and optical icon centering inside glowing neon indicator (no distracting dots)
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AthleticNavigationBar(
    pagerState: PagerState,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = NavigationTab.values()
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    var accumulatedDeltaX by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
        ) {
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val tabWidth = maxWidth / tabs.size
            val tabWidthPx = totalWidthPx / tabs.size
            val dragScale = if (tabWidthPx > 0f) screenWidthPx / tabWidthPx else 5.4f

            val continuousOffset = pagerState.currentPage + pagerState.currentPageOffsetFraction

            // Floating glass capsule container with continuous physical drag tracking
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            accumulatedDeltaX += delta
                            // Natural physical scroll: dragging right advances forward, dragging left retreats
                            coroutineScope.launch {
                                pagerState.dispatchRawDelta(delta * dragScale)
                            }
                        },
                        onDragStarted = {
                            accumulatedDeltaX = 0f
                        },
                        onDragStopped = { velocity ->
                            coroutineScope.launch {
                                val currentFloatingPage = pagerState.currentPage + pagerState.currentPageOffsetFraction
                                val targetPage = when {
                                    velocity > 350f -> pagerState.currentPage + 1
                                    velocity < -350f -> pagerState.currentPage - 1
                                    accumulatedDeltaX > 40f -> (currentFloatingPage + 0.3f).toInt()
                                    accumulatedDeltaX < -40f -> (currentFloatingPage - 0.3f).toInt()
                                    else -> currentFloatingPage.roundToInt()
                                }.coerceIn(0, tabs.size - 1)
                                pagerState.animateScrollToPage(targetPage)
                                accumulatedDeltaX = 0f
                            }
                        }
                    )
            ) {
                // Glowing neon pill that tracks touch drag in real-time in lockstep with the main pager
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

                // Navigation Tab Items Row - Perfectly Centered Icons (click glides to tab)
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
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    onTabSelected(index)
                                },
                            contentAlignment = Alignment.Center
                        ) {
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
                        }
                    }
                }
            }
        }
    }
}
