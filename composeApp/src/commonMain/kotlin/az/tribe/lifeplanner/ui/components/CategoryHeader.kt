package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalCategory

@Composable
fun CategoryHeader(
    category: GoalCategory,
    goalCount: Int,
    expanded: Boolean = true,
    onExpandChange: ((Boolean) -> Unit)? = null
) {
    // State for collapsible section
    var isExpanded by remember { mutableStateOf(expanded) }

    // Apply any external expanded state changes
    LaunchedEffect(expanded) {
        isExpanded = expanded
    }

    // Animation for dropdown icon
    val rotationState by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "rotation"
    )

    // Get category color and create gradient
    val categoryColor = category.backgroundColor()
    val gradientColors = listOf(
        categoryColor,
        categoryColor.copy(alpha = 0.7f)
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Create a card with a gradient background
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isExpanded = !isExpanded
                    onExpandChange?.invoke(isExpanded)
                }
        ) {
            Box(
                modifier = Modifier
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = gradientColors
                        )
                    )
            ) {
                // Semi-transparent overlay for text contrast
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.1f))
                )

                // Category content
                Column {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Category name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Category indicator circle
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.9f))
                            )

                            // Category name with proper capitalization
                            Text(
                                text = category.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Goal count chip
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$goalCount ${if (goalCount == 1) "goal" else "goals"}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = Color.White
                                    )
                                }
                            }

                            // Collapsible section icon
                            Box(
                                modifier = Modifier
                                    .alpha(0.8f)
                                    .size(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = Color.White,
                                    modifier = Modifier.rotate(rotationState)
                                )
                            }
                        }
                    }

                    // Optional category detail section (future expansion)
                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically(
                            animationSpec = tween(300, easing = EaseOutQuad)
                        ) + fadeIn(
                            animationSpec = tween(200)
                        ),
                        exit = shrinkVertically(
                            animationSpec = tween(300, easing = EaseInQuad)
                        ) + fadeOut(
                            animationSpec = tween(200)
                        )
                    ) {
                        // You can add additional category details here in the future
                        // For now, this section is empty but has the animation setup
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp) // Just a tiny extra space for now
                        )
                    }
                }
            }
        }
    }
}