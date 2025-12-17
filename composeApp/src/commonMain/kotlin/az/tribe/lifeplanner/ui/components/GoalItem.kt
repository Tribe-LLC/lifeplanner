package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.theme.gradientColors

@Composable
fun GoalItem(
    goal: Goal,
    onClick: () -> Unit,
    scrollState: LazyListState
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Calculate animation values for cards based on scroll
    val visibleItems = scrollState.layoutInfo.visibleItemsInfo
    val indexInVisible = visibleItems.indexOfFirst { it.key == goal.id }

    // Create a subtle parallax effect based on scroll position
    val yOffset by animateFloatAsState(
        targetValue = if (indexInVisible in 0..3) (3 - indexInVisible) * -2f else 0f,
        label = "yOffset"
    )

    val scale by animateFloatAsState(
        targetValue = if (showDeleteConfirm) 0.98f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )

    // Get category gradient colors
    val categoryGradientColors = goal.category.gradientColors()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Modern glass-style card with gradient accent
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = yOffset
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 8.dp,
                    shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.large),
                    spotColor = categoryGradientColors.first().copy(alpha = 0.3f),
                    ambientColor = categoryGradientColors.first().copy(alpha = 0.1f)
                )
                .clickable {
                    if (showDeleteConfirm) {
                        showDeleteConfirm = false
                    } else {
                        onClick()
                    }
                },
            cornerRadius = LifePlannerDesign.CornerRadius.large
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Gradient accent bar on the left
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(
                            brush = Brush.verticalGradient(categoryGradientColors)
                        )
                )

                Column(modifier = Modifier.weight(1f)) {
                    // Category indicator - gradient line at the top
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        categoryGradientColors.first(),
                                        categoryGradientColors.last().copy(alpha = 0.5f)
                                    )
                                )
                            )
                    )

                    // Goal content
                    Column(
                        modifier = Modifier.padding(LifePlannerDesign.Padding.standard)
                    ) {
                        GoalCard(goal = goal)
                    }
                }
            }
        }
    }
}