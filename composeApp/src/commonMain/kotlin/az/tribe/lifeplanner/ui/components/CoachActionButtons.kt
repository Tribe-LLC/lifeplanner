package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
    onAnswerQuestion: (String) -> Unit = {},
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
                SuggestionsPager(
                    suggestions = cardSuggestions,
                    executedSuggestionIds = executedSuggestionIds,
                    isExecuting = isExecuting,
                    onExecute = onExecute
                )
            } else {
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

        // Show other suggestions
        otherSuggestions.forEach { suggestion ->
            val isAdded = executedSuggestionIds.contains(suggestion.id)
            when (suggestion) {
                is CoachSuggestion.CreateJournalEntry -> {
                    JournalPreviewCard(
                        suggestion = suggestion,
                        onAdd = { onExecute(suggestion) },
                        isExecuting = isExecuting,
                        isAdded = isAdded
                    )
                }
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
                        onOptionSelected = { optionLabel ->
                            onAnswerQuestion(optionLabel)
                            onExecute(suggestion)
                        },
                        isExecuting = isExecuting,
                        isAnswered = isAdded
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
 * Preview card for a goal suggestion — gradient accent bar + clean layout
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
    val gradient = category.getGradient()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient accent bar at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(gradient)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Category icon with gradient background
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(gradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "New Goal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = categoryColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

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

                // Milestones
                if (suggestion.milestones.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                categoryColor.copy(alpha = 0.06f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Milestones",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = categoryColor
                        )
                        suggestion.milestones.forEachIndexed { index, milestone ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(
                                            categoryColor.copy(alpha = 0.15f),
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

                // Add button with gradient
                GradientActionButton(
                    text = if (isAdded) "Added" else "Add Goal",
                    gradient = gradient,
                    onClick = onAdd,
                    isExecuting = isExecuting,
                    isAdded = isAdded
                )
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
        GoalCategory.PHYSICAL
    }

    val categoryColor = category.getPreviewColor()
    val gradient = category.getGradient()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient accent bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(gradient)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(gradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Loop,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "New Habit",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = categoryColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

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

                // Add button with gradient
                GradientActionButton(
                    text = if (isAdded) "Added" else "Add Habit",
                    gradient = gradient,
                    onClick = onAdd,
                    isExecuting = isExecuting,
                    isAdded = isAdded
                )
            }
        }
    }
}

/**
 * Preview card for a journal entry suggestion
 */
@Composable
private fun JournalPreviewCard(
    suggestion: CoachSuggestion.CreateJournalEntry,
    onAdd: () -> Unit,
    isExecuting: Boolean,
    isAdded: Boolean = false
) {
    val journalColor = Color(0xFF9C6ADE)
    val journalGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF9C6ADE), Color(0xFF6A82FB))
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient accent bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(journalGradient)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(journalGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "New Journal Entry",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (suggestion.mood != null) {
                            Text(
                                text = suggestion.mood,
                                style = MaterialTheme.typography.labelMedium,
                                color = journalColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
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

                // Content preview
                if (suggestion.content.isNotBlank()) {
                    Text(
                        text = suggestion.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Add button
                GradientActionButton(
                    text = if (isAdded) "Added" else "Add Entry",
                    gradient = journalGradient,
                    onClick = onAdd,
                    isExecuting = isExecuting,
                    isAdded = isAdded
                )
            }
        }
    }
}

/**
 * Shared gradient action button used by all suggestion cards
 */
@Composable
private fun GradientActionButton(
    text: String,
    gradient: Brush,
    onClick: () -> Unit,
    isExecuting: Boolean,
    isAdded: Boolean
) {
    val scale by animateFloatAsState(
        targetValue = if (isAdded) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium)
    )

    Surface(
        onClick = onClick,
        enabled = !isExecuting && !isAdded,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isAdded) Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ) else gradient,
                    RoundedCornerShape(14.dp)
                )
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else if (isAdded) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Text(
                        text = text,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Text(
                        text = text,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineChip(timeline: String) {
    val (text, color) = when (timeline) {
        "SHORT_TERM" -> "30 days" to Color(0xFF4CAF50)
        "MID_TERM" -> "90 days" to Color(0xFFFF9800)
        "LONG_TERM" -> "1 year" to Color(0xFF2196F3)
        else -> "90 days" to Color(0xFFFF9800)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.height(26.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
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
        color = color.copy(alpha = 0.1f),
        modifier = Modifier.height(26.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Loop,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Interactive question card
 */
@Composable
private fun QuestionCard(
    suggestion: CoachSuggestion.AskQuestion,
    onOptionSelected: (String) -> Unit,
    isExecuting: Boolean,
    isAnswered: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Purple accent bar for questions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF7A5AF8), Color(0xFF9C6ADE))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = suggestion.question,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestion.options.forEach { option ->
                        Surface(
                            onClick = { onOptionSelected(option.label) },
                            enabled = !isExecuting && !isAnswered,
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
}

@Composable
private fun SuggestionActionButton(
    suggestion: CoachSuggestion,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isCompleted: Boolean = false
) {
    val (icon, color) = when (suggestion) {
        is CoachSuggestion.CheckInHabit -> Icons.Rounded.Check to Color(0xFF4CAF50)
        else -> Icons.Rounded.Help to MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (isCompleted) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                else color.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isCompleted) Icons.Rounded.CheckCircle else icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isCompleted) MaterialTheme.colorScheme.tertiary else color
            )
            Text(
                text = if (isCompleted) "Done" else suggestion.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isCompleted) MaterialTheme.colorScheme.tertiary else color
            )
        }
    }
}

// Extension functions for category colors
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

private fun GoalCategory.getGradient(): Brush {
    val colors = when (this) {
        GoalCategory.CAREER -> CategoryColors.CAREER_GRADIENT
        GoalCategory.FINANCIAL -> CategoryColors.FINANCIAL_GRADIENT
        GoalCategory.PHYSICAL -> CategoryColors.PHYSICAL_GRADIENT
        GoalCategory.SOCIAL -> CategoryColors.SOCIAL_GRADIENT
        GoalCategory.EMOTIONAL -> CategoryColors.EMOTIONAL_GRADIENT
        GoalCategory.SPIRITUAL -> CategoryColors.SPIRITUAL_GRADIENT
        GoalCategory.FAMILY -> CategoryColors.FAMILY_GRADIENT
    }
    return Brush.horizontalGradient(colors)
}

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
