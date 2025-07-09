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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.Goal

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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Main card with goal information
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationY = yOffset
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = goal.category.backgroundColor().copy(alpha = 0.5f)
                )
                .clickable {
                    if (showDeleteConfirm) {
                        showDeleteConfirm = false
                    } else {
                        onClick()
                    }
                },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Box {
                // Category indicator - thin colorful line at the top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            color = goal.category.backgroundColor(),
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                )

                // Goal content
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 16.dp)
                ) {
                    // Main content
                    GoalCard(goal = goal)

                }

            }
        }
    }
}