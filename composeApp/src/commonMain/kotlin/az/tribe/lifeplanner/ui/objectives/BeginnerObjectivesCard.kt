package az.tribe.lifeplanner.ui.objectives

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.BeginnerObjective
import az.tribe.lifeplanner.domain.model.ObjectiveType
import az.tribe.lifeplanner.ui.components.GlassCard

@Composable
fun BeginnerObjectivesCard(
    objectives: List<BeginnerObjective>,
    isExpanded: Boolean,
    allComplete: Boolean = false,
    onToggleExpanded: () -> Unit,
    onDismiss: () -> Unit = {},
    onObjectiveClick: (ObjectiveType) -> Unit
) {
    if (objectives.isEmpty()) return

    val completedCount = objectives.count { it.isCompleted }
    val totalCount = objectives.size
    val allDone = completedCount == totalCount
    val progress by animateFloatAsState(
        targetValue = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f,
        animationSpec = tween(600)
    )
    val totalXp = objectives.filter { it.isCompleted }.sumOf { it.xpAwarded }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp
    ) {
        Column {
            // Gradient accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            if (allDone) listOf(Color(0xFF4CAF50), Color(0xFF81C784))
                            else listOf(Color(0xFFFF6B35), Color(0xFFFFAB40))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(16.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (allDone) Color(0xFF4CAF50).copy(alpha = 0.12f)
                                    else Color(0xFFFF6B35).copy(alpha = 0.12f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint = if (allDone) Color(0xFF4CAF50) else Color(0xFFFF6B35),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (allDone) "All objectives complete!" else "Getting Started",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "$completedCount/$totalCount completed · ${totalXp} XP earned",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Icon(
                        if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (allDone) Color(0xFF4CAF50) else Color(0xFFFF6B35),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Expandable list
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    objectives.forEachIndexed { index, objective ->
                        ObjectiveRow(
                            objective = objective,
                            onClick = { onObjectiveClick(objective.type) }
                        )
                        if (index < objectives.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            }

            // Dismiss button — visible only when all objectives are complete
            if (allComplete) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismiss)
                        .background(Color(0xFF4CAF50).copy(alpha = 0.08f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        }
    }
}

@Composable
private fun ObjectiveRow(
    objective: BeginnerObjective,
    onClick: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (objective.isCompleted) Color(0xFF4CAF50)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        animationSpec = tween(300)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !objective.isCompleted, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (objective.isCompleted) Icons.Rounded.CheckCircle
            else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = objective.type.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (objective.isCompleted) FontWeight.Normal else FontWeight.Medium,
                color = if (objective.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = objective.type.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = if (objective.isCompleted) Color(0xFF4CAF50).copy(alpha = 0.1f)
            else Color(0xFFFF6B35).copy(alpha = 0.1f)
        ) {
            Text(
                text = if (objective.isCompleted) "+${objective.xpAwarded}" else "+${objective.type.xpReward}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (objective.isCompleted) Color(0xFF4CAF50) else Color(0xFFFF6B35),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}
