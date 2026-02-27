package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.model.Badge
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign

/**
 * Badge card component showing earned or locked badges
 */
@Composable
fun BadgeCard(
    badge: Badge?,
    badgeType: BadgeType,
    isEarned: Boolean,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isNew = badge?.isNew == true

    // Enhanced scale for new badges: 1.12f with spring
    val scale by animateFloatAsState(
        targetValue = if (isNew) 1.12f else 1f,
        animationSpec = spring()
    )

    // Wobble rotation for new badges: -3° to +3°
    val infiniteTransition = rememberInfiniteTransition(label = "badgeWobble")
    val wobbleRotation by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wobble"
    )

    // Pulse glow for new badges
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .graphicsLayer {
                rotationZ = if (isNew) wobbleRotation else 0f
            }
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Badge Icon with optional pulse glow
        Box(
            modifier = Modifier
                .size(48.dp)
                .then(
                    if (isNew) {
                        Modifier.border(
                            width = 3.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                                    Color(badgeType.color).copy(alpha = glowAlpha)
                                )
                            ),
                            shape = CircleShape
                        )
                    } else Modifier
                )
                .clip(CircleShape)
                .background(
                    if (isEarned) {
                        Color(badgeType.color)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = getBadgeIcon(badgeType),
                contentDescription = badgeType.displayName,
                modifier = Modifier.size(24.dp),
                tint = if (isEarned) Color.White else MaterialTheme.colorScheme.outline
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Badge Name
        Text(
            text = badgeType.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isEarned) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isEarned) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            },
            textAlign = TextAlign.Center,
            maxLines = 2
        )

        // New indicator
        if (isNew) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "NEW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Level progress bar component
 */
@Composable
fun LevelProgressBar(
    currentLevel: Int,
    currentXp: Int,
    xpForNextLevel: Int,
    progress: Float,
    title: String,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(1000)
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.medium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = LifePlannerDesign.Alpha.containerMedium)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = LifePlannerDesign.Elevation.none)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LifePlannerDesign.Padding.standard)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Level badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$currentLevel",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Level $currentLevel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // XP display
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$currentXp XP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "/ $xpForNextLevel XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${(animatedProgress * 100).toInt()}% to Level ${currentLevel + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Compact stat card for quick stats display
 */
@Composable
fun GamificationStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.small),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = LifePlannerDesign.Alpha.containerLight)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = LifePlannerDesign.Elevation.none)
    ) {
        Column(
            modifier = Modifier.padding(LifePlannerDesign.Padding.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Get appropriate icon for badge type
 */
fun getBadgeIcon(type: BadgeType): ImageVector {
    return when {
        type.name.startsWith("STREAK") -> Icons.Default.LocalFireDepartment
        type.name.startsWith("GOAL") || type.name == "FIRST_STEP" -> Icons.AutoMirrored.Filled.TrendingUp
        type.name.startsWith("HABIT") -> Icons.Default.Loop
        type.name.startsWith("JOURNAL") -> Icons.AutoMirrored.Filled.MenuBook
        type == BadgeType.PERFECTIONIST -> Icons.Default.Check
        else -> Icons.Default.EmojiEvents
    }
}
