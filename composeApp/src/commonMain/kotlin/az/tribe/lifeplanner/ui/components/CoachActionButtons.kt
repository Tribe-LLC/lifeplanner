package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.CoachSuggestion
import az.tribe.lifeplanner.ui.theme.CategoryColors
import kotlin.math.absoluteValue

/**
 * Displays coach suggestions as preview cards for goals/habits
 * and simple buttons for other actions.
 * Uses horizontal pager for multiple goal/habit suggestions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachSuggestionButtons(
    suggestions: List<CoachSuggestion>,
    onExecute: (CoachSuggestion) -> Unit,
    isExecuting: Boolean = false,
    executedSuggestionIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    // Separate card-based suggestions (goals/habits) from others
    val cardSuggestions = suggestions.filter {
        it is CoachSuggestion.CreateGoal || it is CoachSuggestion.CreateHabit
    }
    val otherSuggestions = suggestions.filter {
        it !is CoachSuggestion.CreateGoal && it !is CoachSuggestion.CreateHabit
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Show goal/habit cards in horizontal pager if multiple
        if (cardSuggestions.isNotEmpty()) {
            if (cardSuggestions.size > 1) {
                // Multiple cards - use horizontal pager
                SuggestionsPager(
                    suggestions = cardSuggestions,
                    executedSuggestionIds = executedSuggestionIds,
                    isExecuting = isExecuting,
                    onExecute = onExecute
                )
            } else {
                // Single card - show directly
                val suggestion = cardSuggestions.first()
                val isAdded = executedSuggestionIds.contains(suggestion.id)
                when (suggestion) {
                    is CoachSuggestion.CreateGoal -> {
                        GoalPreviewCard(
                            suggestion = suggestion,
                            onAdd = { onExecute(suggestion) },
                            isExecuting = isExecuting,
                            isAdded = isAdded
                        )
                    }
                    is CoachSuggestion.CreateHabit -> {
                        HabitPreviewCard(
                            suggestion = suggestion,
                            onAdd = { onExecute(suggestion) },
                            isExecuting = isExecuting,
                            isAdded = isAdded
                        )
                    }
                    else -> {}
                }
            }
        }

        // Show other suggestions normally
        otherSuggestions.forEach { suggestion ->
            val isAdded = executedSuggestionIds.contains(suggestion.id)
            when (suggestion) {
                is CoachSuggestion.CreateJournalEntry,
                is CoachSuggestion.CheckInHabit -> {
                    SuggestionActionButton(
                        suggestion = suggestion,
                        onClick = { onExecute(suggestion) },
                        enabled = !isExecuting && !isAdded,
                        isCompleted = isAdded
                    )
                }
                is CoachSuggestion.AskQuestion -> {
                    QuestionCard(
                        suggestion = suggestion,
                        onOptionSelected = { onExecute(suggestion) },
                        isExecuting = isExecuting
                    )
                }
                else -> {}
            }
        }
    }
}

/**
 * Horizontal pager for multiple goal/habit suggestions
 */
@Composable
private fun SuggestionsPager(
    suggestions: List<CoachSuggestion>,
    executedSuggestionIds: Set<String>,
    isExecuting: Boolean,
    onExecute: (CoachSuggestion) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { suggestions.size })

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val suggestion = suggestions[page]
            val isAdded = executedSuggestionIds.contains(suggestion.id)

            // Add scale effect for pages
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val scale = lerp(0.9f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(0.5f, 1f, 1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
                    }
                    .fillMaxWidth()
            ) {
                when (suggestion) {
                    is CoachSuggestion.CreateGoal -> {
                        GoalPreviewCard(
                            suggestion = suggestion,
                            onAdd = { onExecute(suggestion) },
                            isExecuting = isExecuting,
                            isAdded = isAdded
                        )
                    }
                    is CoachSuggestion.CreateHabit -> {
                        HabitPreviewCard(
                            suggestion = suggestion,
                            onAdd = { onExecute(suggestion) },
                            isExecuting = isExecuting,
                            isAdded = isAdded
                        )
                    }
                    else -> {}
                }
            }
        }

        // Page indicator dots
        if (suggestions.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(suggestions.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                    )
                }
            }
        }
    }
}

/**
 * Preview card for a goal suggestion
 */
@Composable
private fun GoalPreviewCard(
    suggestion: CoachSuggestion.CreateGoal,
    onAdd: () -> Unit,
    isExecuting: Boolean,
    isAdded: Boolean = false
) {
    val category = try {
        GoalCategory.valueOf(suggestion.category)
    } catch (e: Exception) {
        GoalCategory.CAREER
    }

    val categoryColor = category.getPreviewColor()
    val categoryContainerColor = category.getPreviewContainerColor()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = categoryContainerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with category icon and timeline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(categoryColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Flag,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = categoryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Timeline chip
                TimelineChip(timeline = suggestion.timeline)
            }

            // Title
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Description
            if (suggestion.description.isNotBlank()) {
                Text(
                    text = suggestion.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Milestones section
            if (suggestion.milestones.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Milestones",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    suggestion.milestones.forEachIndexed { index, milestone ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        categoryColor.copy(alpha = 0.2f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = categoryColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = milestone.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Add button - using Material theme colors for dark mode support
            val successColor = MaterialTheme.colorScheme.tertiary
            val onSuccessColor = MaterialTheme.colorScheme.onTertiary

            Button(
                onClick = onAdd,
                enabled = !isExecuting && !isAdded,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdded) successColor else categoryColor,
                    disabledContainerColor = if (isAdded) successColor else categoryColor.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else if (isAdded) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = onSuccessColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Added",
                        fontWeight = FontWeight.SemiBold,
                        color = onSuccessColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Goal",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * Preview card for a habit suggestion
 */
@Composable
private fun HabitPreviewCard(
    suggestion: CoachSuggestion.CreateHabit,
    onAdd: () -> Unit,
    isExecuting: Boolean,
    isAdded: Boolean = false
) {
    val category = try {
        GoalCategory.valueOf(suggestion.category)
    } catch (e: Exception) {
        GoalCategory.PHYSICAL // Default for habits
    }

    val categoryColor = category.getPreviewColor()
    val categoryContainerColor = category.getPreviewContainerColor()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = categoryContainerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with category icon and frequency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(categoryColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Loop,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = categoryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Frequency chip
                FrequencyBadge(frequency = suggestion.frequency)
            }

            // Title
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Description
            if (suggestion.description.isNotBlank()) {
                Text(
                    text = suggestion.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Add button - using Material theme colors for dark mode support
            val successColor = MaterialTheme.colorScheme.tertiary
            val onSuccessColor = MaterialTheme.colorScheme.onTertiary

            Button(
                onClick = onAdd,
                enabled = !isExecuting && !isAdded,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdded) successColor else categoryColor,
                    disabledContainerColor = if (isAdded) successColor else categoryColor.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else if (isAdded) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = onSuccessColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Added",
                        fontWeight = FontWeight.SemiBold,
                        color = onSuccessColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Habit",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineChip(timeline: String) {
    val (text, color) = when (timeline) {
        "SHORT_TERM" -> "Short Term" to Color(0xFF4CAF50)
        "MID_TERM" -> "Mid Term" to Color(0xFFFF9800)
        "LONG_TERM" -> "Long Term" to Color(0xFF2196F3)
        else -> "Mid Term" to Color(0xFFFF9800)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FrequencyBadge(frequency: String) {
    val (text, color) = when (frequency.uppercase()) {
        "DAILY" -> "Daily" to Color(0xFF4CAF50)
        "WEEKLY" -> "Weekly" to Color(0xFF2196F3)
        else -> "Daily" to Color(0xFF4CAF50)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Loop,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Interactive question card for gathering user input
 */
@Composable
private fun QuestionCard(
    suggestion: CoachSuggestion.AskQuestion,
    onOptionSelected: () -> Unit,
    isExecuting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Question text
            Text(
                text = suggestion.question,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            // Options as selectable chips
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestion.options.forEach { option ->
                    Surface(
                        onClick = onOptionSelected,
                        enabled = !isExecuting,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                option.description?.let { desc ->
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionActionButton(
    suggestion: CoachSuggestion,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isCompleted: Boolean = false
) {
    val successColor = MaterialTheme.colorScheme.tertiary
    val successContainerColor = MaterialTheme.colorScheme.tertiaryContainer

    val (icon, containerColor) = if (isCompleted) {
        Icons.Rounded.CheckCircle to successContainerColor
    } else {
        when (suggestion) {
            is CoachSuggestion.CreateGoal -> Icons.Rounded.Flag to MaterialTheme.colorScheme.primaryContainer
            is CoachSuggestion.CreateHabit -> Icons.Rounded.Loop to MaterialTheme.colorScheme.secondaryContainer
            is CoachSuggestion.CreateJournalEntry -> Icons.Rounded.Edit to MaterialTheme.colorScheme.tertiaryContainer
            is CoachSuggestion.CheckInHabit -> Icons.Rounded.Check to MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            is CoachSuggestion.AskQuestion -> Icons.Rounded.Help to MaterialTheme.colorScheme.tertiaryContainer
        }
    }

    val contentColor = if (isCompleted) {
        successColor
    } else {
        when (suggestion) {
            is CoachSuggestion.CreateGoal -> MaterialTheme.colorScheme.onPrimaryContainer
            is CoachSuggestion.CreateHabit -> MaterialTheme.colorScheme.onSecondaryContainer
            is CoachSuggestion.CreateJournalEntry -> MaterialTheme.colorScheme.onTertiaryContainer
            is CoachSuggestion.CheckInHabit -> MaterialTheme.colorScheme.primary
            is CoachSuggestion.AskQuestion -> MaterialTheme.colorScheme.onTertiaryContainer
        }
    }

    val displayLabel = if (isCompleted) "Done" else suggestion.label

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (enabled || isCompleted) containerColor else containerColor.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = contentColor
            )
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

// Extension functions for category colors in preview cards
private fun GoalCategory.getPreviewColor(): Color {
    return when (this) {
        GoalCategory.CAREER -> CategoryColors.CAREER
        GoalCategory.FINANCIAL -> CategoryColors.FINANCIAL
        GoalCategory.PHYSICAL -> CategoryColors.PHYSICAL
        GoalCategory.SOCIAL -> CategoryColors.SOCIAL
        GoalCategory.EMOTIONAL -> CategoryColors.EMOTIONAL
        GoalCategory.SPIRITUAL -> CategoryColors.SPIRITUAL
        GoalCategory.FAMILY -> CategoryColors.FAMILY
    }
}

private fun GoalCategory.getPreviewContainerColor(): Color {
    return when (this) {
        GoalCategory.CAREER -> CategoryColors.CAREER_CONTAINER
        GoalCategory.FINANCIAL -> CategoryColors.FINANCIAL_CONTAINER
        GoalCategory.PHYSICAL -> CategoryColors.PHYSICAL_CONTAINER
        GoalCategory.SOCIAL -> CategoryColors.SOCIAL_CONTAINER
        GoalCategory.EMOTIONAL -> CategoryColors.EMOTIONAL_CONTAINER
        GoalCategory.SPIRITUAL -> CategoryColors.SPIRITUAL_CONTAINER
        GoalCategory.FAMILY -> CategoryColors.FAMILY_CONTAINER
    }
}

// Display name for categories
private val GoalCategory.displayName: String
    get() = when (this) {
        GoalCategory.CAREER -> "Career"
        GoalCategory.FINANCIAL -> "Financial"
        GoalCategory.PHYSICAL -> "Physical"
        GoalCategory.SOCIAL -> "Social"
        GoalCategory.EMOTIONAL -> "Emotional"
        GoalCategory.SPIRITUAL -> "Spiritual"
        GoalCategory.FAMILY -> "Family"
    }
