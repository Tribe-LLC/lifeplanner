package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuickStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    animated: Boolean = true
) {
    // Animate the value for better visual feedback
    var animatedValue by remember { mutableStateOf("0") }
    var previousValue by remember { mutableStateOf("0") }

    // Set up the animation for numeric values
    LaunchedEffect(value) {
        if (animated && value.all { it.isDigit() || it == '.' || it == ',' || it == '%' }) {
            previousValue = animatedValue
            val cleanValue = value.filter { it.isDigit() || it == '.' }
            val cleanPrev = previousValue.filter { it.isDigit() || it == '.' }

            try {
                val targetNum = cleanValue.toFloat()
                val startNum = cleanPrev.toFloatOrNull() ?: 0f

                val animator = TargetBasedAnimation(
                    animationSpec = tween(800, easing = FastOutSlowInEasing),
                    typeConverter = Float.VectorConverter,
                    initialValue = startNum,
                    targetValue = targetNum
                )

                val startTime = withFrameNanos { it }

                do {
                    val playTime = withFrameNanos { it } - startTime
                    val animationValue = animator.getValueFromNanos(playTime)

                    // Format the number based on the original value
                    animatedValue = if (value.contains("%")) {
                        "${animationValue.toInt()}%"
                    } else if (value.contains(".")) {
                        val rounded = (animationValue * 10).toInt() / 10.0
                        "$rounded"
                    } else {
                        animationValue.toInt().toString()
                    }
                } while (!animator.isFinishedFromNanos(playTime))

                // Ensure final value matches exactly
                animatedValue = value
            } catch (e: Exception) {
                // If parsing fails, just use the original value
                animatedValue = value
            }
        } else {
            // If not a number or animation not requested, just use the value directly
            animatedValue = value
        }
    }

    Card(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        // Top gradient indicator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(color, color.copy(alpha = 0.7f))
                    )
                )
        )

        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show icon if provided
            icon?.let {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = color.copy(alpha = 0.1f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Value with emphasized text
            Text(
                text = animatedValue,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Title with subdued color
            Text(
                text = title,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A row of quick stat cards with responsive layout
 */
@Composable
fun QuickStatsRow(
    stats: List<StatData>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        stats.forEach { stat ->
            QuickStatCard(
                title = stat.title,
                value = stat.value,
                icon = stat.icon,
                color = stat.color,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Data class for stat information
 */
data class StatData(
    val title: String,
    val value: String,
    val icon: ImageVector? = null,
    val color: Color = Color.Unspecified
)

/**
 * Predefined stat icons based on common metrics
 */
object StatIcons {
    val Active = Icons.Rounded.PlayCircle
    val Completed = Icons.Rounded.CheckCircle
    val Progress = Icons.Rounded.DonutLarge
    val Upcoming = Icons.Rounded.Event
    val Overdue = Icons.Rounded.Schedule
    val Tasks = Icons.Rounded.Assignment
    val Streak = Icons.Rounded.LocalFireDepartment
    val Total = Icons.Rounded.Numbers
}