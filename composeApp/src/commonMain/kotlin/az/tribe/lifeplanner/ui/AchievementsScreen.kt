package az.tribe.lifeplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.model.BadgeCategory
import az.tribe.lifeplanner.domain.model.BadgeRequirements
import az.tribe.lifeplanner.ui.components.BadgeCard
import az.tribe.lifeplanner.ui.components.getBadgeIcon
import az.tribe.lifeplanner.ui.components.rememberHapticManager
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    onNavigateBack: () -> Unit,
    viewModel: GamificationViewModel = koinInject()
) {
    val userProgress by viewModel.userProgress.collectAsState()
    val badges by viewModel.badges.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Badge detail bottom sheet state
    var selectedBadgeType by remember { mutableStateOf<BadgeType?>(null) }
    val earnedBadgesMap = badges.associateBy { it.type }

    // Refresh data when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // Badge Detail Bottom Sheet
    selectedBadgeType?.let { badgeType ->
        BadgeDetailBottomSheet(
            badgeType = badgeType,
            badge = earnedBadgesMap[badgeType],
            userProgress = userProgress,
            onDismiss = { selectedBadgeType = null },
            onMarkAsSeen = { badge ->
                viewModel.markBadgeAsSeen(badge.id)
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Column {
                        Text("Badges")
                        Text(
                            text = "${badges.size} of ${BadgeType.entries.size} earned",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            BadgesContent(
                earnedBadges = earnedBadgesMap,
                onBadgeClick = { badgeType -> selectedBadgeType = badgeType },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun BadgesContent(
    earnedBadges: Map<BadgeType, az.tribe.lifeplanner.domain.model.Badge>,
    onBadgeClick: (BadgeType) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = BadgeCategory.entries

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        categories.forEach { category ->
            val badgesInCategory = BadgeType.entries.filter {
                BadgeRequirements.getCategory(it) == category
            }

            if (badgesInCategory.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = category.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.height(
                                ((badgesInCategory.size + 3) / 4 * 100).dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(badgesInCategory) { badgeType ->
                                val badge = earnedBadges[badgeType]
                                BadgeCard(
                                    badge = badge,
                                    badgeType = badgeType,
                                    isEarned = badge != null,
                                    onClick = { onBadgeClick(badgeType) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bottom sheet showing badge details
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BadgeDetailBottomSheet(
    badgeType: BadgeType,
    badge: az.tribe.lifeplanner.domain.model.Badge?,
    userProgress: az.tribe.lifeplanner.domain.model.UserProgress?,
    onDismiss: () -> Unit,
    onMarkAsSeen: (az.tribe.lifeplanner.domain.model.Badge) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEarned = badge != null
    val haptic = rememberHapticManager()

    // Mark badge as seen and play haptic when bottom sheet is shown
    LaunchedEffect(badge) {
        badge?.let {
            if (it.isNew) {
                haptic.celebration()
                onMarkAsSeen(it)
            } else if (isEarned) {
                haptic.click()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Close button row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // Badge Icon with spring bounce entrance
            val badgeScale = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                badgeScale.animateTo(
                    1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(badgeScale.value)
                    .clip(CircleShape)
                    .background(
                        if (isEarned) Color(badgeType.color)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getBadgeIcon(badgeType),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (isEarned) Color.White else MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Badge Name
            Text(
                text = badgeType.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Badge Description
            Text(
                text = badgeType.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalDivider()

            Spacer(modifier = Modifier.height(24.dp))

            if (isEarned && badge != null) {
                // Earned badge info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Earned",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF4CAF50)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = formatEarnedDate(badge.earnedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Not earned - show progress
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Not Yet Earned",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress towards badge
                val (currentValue, requiredValue) = getBadgeProgress(badgeType, userProgress)
                val progress = if (requiredValue > 0) {
                    (currentValue.toFloat() / requiredValue).coerceIn(0f, 1f)
                } else 0f

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Progress: $currentValue / $requiredValue",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(badgeType.color),
                        trackColor = Color(badgeType.color).copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${(progress * 100).toInt()}% complete",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hint on how to earn
                Text(
                    text = getBadgeHint(badgeType),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Get current progress towards a badge
 */
private fun getBadgeProgress(
    badgeType: BadgeType,
    userProgress: az.tribe.lifeplanner.domain.model.UserProgress?
): Pair<Int, Int> {
    val required = BadgeRequirements.getRequirementValue(badgeType)
    val current = when (badgeType) {
        // Streak badges
        BadgeType.STREAK_3, BadgeType.STREAK_7, BadgeType.STREAK_14,
        BadgeType.STREAK_30, BadgeType.STREAK_100 -> userProgress?.currentStreak ?: 0

        // Goal badges
        BadgeType.FIRST_STEP, BadgeType.GOAL_1, BadgeType.GOAL_5,
        BadgeType.GOAL_10, BadgeType.GOAL_25, BadgeType.GOAL_50 -> userProgress?.goalsCompleted ?: 0

        // Habit badges
        BadgeType.HABIT_STARTER, BadgeType.HABIT_5 -> userProgress?.habitsCompleted ?: 0
        BadgeType.HABIT_PERFECT_WEEK, BadgeType.HABIT_PERFECT_MONTH -> 0 // Special tracking needed

        // Journal badges
        BadgeType.JOURNAL_FIRST, BadgeType.JOURNAL_10, BadgeType.JOURNAL_30 ->
            userProgress?.journalEntriesCount ?: 0

        // Other badges - no easy progress tracking
        else -> 0
    }
    return current to required
}

/**
 * Get a hint on how to earn the badge
 */
private fun getBadgeHint(badgeType: BadgeType): String {
    return when (badgeType) {
        BadgeType.FIRST_STEP -> "Complete your first goal to earn this badge!"
        BadgeType.STREAK_3 -> "Check in for 3 days in a row"
        BadgeType.STREAK_7 -> "Keep your streak going for a full week"
        BadgeType.STREAK_14 -> "Maintain consistency for two weeks"
        BadgeType.STREAK_30 -> "A whole month of daily check-ins!"
        BadgeType.STREAK_100 -> "The ultimate dedication - 100 days!"
        BadgeType.GOAL_1 -> "Set a goal and complete it"
        BadgeType.GOAL_5 -> "Complete 5 goals to level up"
        BadgeType.GOAL_10 -> "You're on your way to becoming a goal crusher"
        BadgeType.GOAL_25 -> "25 goals - you're unstoppable!"
        BadgeType.GOAL_50 -> "The legendary 50 goals milestone"
        BadgeType.HABIT_STARTER -> "Create your first habit to start tracking"
        BadgeType.HABIT_5 -> "Build a strong habit routine with 5 habits"
        BadgeType.HABIT_PERFECT_WEEK -> "Complete all your habits every day for a week"
        BadgeType.HABIT_PERFECT_MONTH -> "Perfect habit completion for an entire month"
        BadgeType.JOURNAL_FIRST -> "Write your first journal entry"
        BadgeType.JOURNAL_10 -> "Reflect on your journey with 10 entries"
        BadgeType.JOURNAL_30 -> "A month of journaling builds self-awareness"
        BadgeType.BALANCED -> "Have active goals in all life categories"
        BadgeType.HEALTH_FOCUS -> "Complete 5 health-related goals"
        BadgeType.CAREER_FOCUS -> "Complete 5 career-related goals"
        BadgeType.EARLY_BIRD -> "Check in before 7 AM"
        BadgeType.NIGHT_OWL -> "Check in after 10 PM"
        BadgeType.COMEBACK -> "Return after being away for 7+ days"
        BadgeType.PERFECTIONIST -> "Complete a goal at exactly 100% progress"
        BadgeType.FOCUS_FIRST -> "Complete your first focus session"
        BadgeType.FOCUS_HOUR -> "Complete a 60-minute focus session"
        BadgeType.FOCUS_10 -> "Complete 10 focus sessions"
        BadgeType.FOCUS_50 -> "Complete 50 focus sessions"
        BadgeType.GETTING_STARTED -> "Complete all Getting Started objectives"
    }
}

/**
 * Format the earned date for display
 */
private fun formatEarnedDate(dateTime: kotlinx.datetime.LocalDateTime): String {
    val month = dateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }
    return "${month.take(3)} ${dateTime.day}, ${dateTime.year}"
}
