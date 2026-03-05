package az.tribe.lifeplanner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import az.tribe.lifeplanner.ui.components.getIcon
import az.tribe.lifeplanner.ui.habit.HabitWithStatus
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.ui.theme.LifePlannerGradients
import az.tribe.lifeplanner.ui.theme.backgroundColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.ui.components.CompactGoalTile
import az.tribe.lifeplanner.ui.components.InlineGreetingRow
import az.tribe.lifeplanner.ui.components.InlineStatsRow
import az.tribe.lifeplanner.ui.components.NextAction
import az.tribe.lifeplanner.ui.components.NextActionCard
import az.tribe.lifeplanner.ui.components.GlassCard
import az.tribe.lifeplanner.ui.components.QuickActionsPillRow
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.data.sync.SyncState
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
import az.tribe.lifeplanner.ui.habit.HabitViewModel
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GoalViewModel,
    onGoalClick: (Goal) -> Unit,
    goToAnalytics: () -> Unit,
    onAddGoalClick: () -> Unit,
    goToAiGeneration: () -> Unit,
    onNavigateToHabits: () -> Unit = {},
    onNavigateToJournal: () -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onStartFocusForMilestone: (goalId: String, milestoneId: String) -> Unit = { _, _ -> },
) {
    val snackBarHostState = remember { SnackbarHostState() }
    var showAccountSheet by remember { mutableStateOf(false) }

    // Inject ViewModels
    val authViewModel: AuthViewModel = koinInject()
    val gamificationViewModel: GamificationViewModel = koinViewModel()
    val habitViewModel: HabitViewModel = koinViewModel()
    val syncManager: SyncManager = koinInject()

    val authState by authViewModel.authState.collectAsState()
    val userProgress by gamificationViewModel.userProgress.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val habits by habitViewModel.habits.collectAsState()

    // Extract current user
    val currentUser = when (authState) {
        is AuthState.Authenticated -> (authState as AuthState.Authenticated).user
        is AuthState.Guest -> (authState as AuthState.Guest).user
        else -> null
    }

    // Calculate dashboard stats
    val totalProgress = if (goals.isNotEmpty()) {
        (goals.sumOf { it.progress ?: 0L } / goals.size).toInt()
    } else 0

    // Get upcoming goals (due soon, not completed)
    val upcomingGoals = goals
        .filter { it.status != GoalStatus.COMPLETED }
        .sortedBy { it.dueDate }
        .take(5)

    // Calculate goals due today
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val goalsDueToday = goals.filter { goal ->
        goal.status != GoalStatus.COMPLETED && goal.dueDate == today
    }

    // Calculate next milestones (first incomplete per active goal)
    val nextMilestones = goals
        .filter { it.status != GoalStatus.COMPLETED && it.milestones.isNotEmpty() }
        .mapNotNull { goal ->
            goal.milestones.firstOrNull { !it.isCompleted }?.let { goal to it }
        }
        .take(5)

    // Load accumulated focus time per milestone
    val focusRepository: FocusRepository = koinInject()
    var milestoneFocusMinutes by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    LaunchedEffect(nextMilestones) {
        milestoneFocusMinutes = nextMilestones.associate { (_, milestone) ->
            val sessions = focusRepository.getSessionsByMilestoneId(milestone.id)
            milestone.id to (sessions.sumOf { it.actualDurationSeconds } / 60)
        }
    }

    // Calculate habits stats
    val habitsCompleted = habits.count { it.isCompletedToday }
    val totalHabits = habits.size

    // Derive NextAction
    val nextAction = remember(goalsDueToday, habits, upcomingGoals) {
        val firstGoalDueToday = goalsDueToday.firstOrNull()
        val nextUncheckedHabit = habits.firstOrNull { !it.isCompletedToday }
        val highestProgressGoal = upcomingGoals
            .filter { (it.progress ?: 0L) > 0L }
            .maxByOrNull { it.progress ?: 0L }

        when {
            firstGoalDueToday != null -> NextAction.GoalDueToday(firstGoalDueToday)
            nextUncheckedHabit != null -> NextAction.NextHabit(nextUncheckedHabit)
            highestProgressGoal != null -> NextAction.ContinueGoal(highestProgressGoal)
            else -> NextAction.AllCaughtUp
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadAllGoals()
        viewModel.loadAnalytics()
        habitViewModel.loadHabits()
        gamificationViewModel.refresh()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val syncStatus by syncManager.syncStatus.collectAsState()

    val targetTitleColor = when (syncStatus.state) {
        SyncState.SYNCED -> Color(0xFF4CAF50)
        SyncState.SYNCING -> MaterialTheme.colorScheme.tertiary
        SyncState.ERROR -> MaterialTheme.colorScheme.error
        SyncState.OFFLINE -> MaterialTheme.colorScheme.outline
        SyncState.IDLE -> MaterialTheme.colorScheme.primary
    }
    val titleColor by animateColorAsState(
        targetValue = targetTitleColor,
        animationSpec = tween(durationMillis = 600)
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        "Life Planner",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            Icons.Rounded.AccountCircle,
                            contentDescription = "Profile",
                            tint = if (authState is AuthState.Authenticated)
                                Color(0xFF4CAF50)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackBarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                    actionContentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = PaddingValues(top = innerPadding.calculateTopPadding())),
            contentPadding = PaddingValues(
                top = LifePlannerDesign.Padding.screenHorizontal,
                start = LifePlannerDesign.Padding.screenHorizontal,
                end = LifePlannerDesign.Padding.screenHorizontal,
                bottom = innerPadding.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm)
        ) {
            // 1. Inline Greeting Row
            item {
                InlineGreetingRow(
                    userName = currentUser?.displayName,
                    streak = userProgress?.currentStreak ?: 0,
                    level = userProgress?.currentLevel ?: 1,
                    levelTitle = userProgress?.title ?: "Novice"
                )
            }

            // 2. Quick Actions Pill Row
            item {
                QuickActionsPillRow(
                    onAddGoal = onAddGoalClick,
                    onAiSuggest = goToAiGeneration,
                    onNewHabit = onNavigateToHabits,
                    onJournal = onNavigateToJournal,
                    onFocus = onNavigateToFocus
                )
            }

            // 3. Inline Stats Row
            item {
                InlineStatsRow(
                    streak = userProgress?.currentStreak ?: 0,
                    habitsCompleted = habitsCompleted,
                    totalHabits = totalHabits,
                    goalsDueToday = goalsDueToday.size,
                    totalProgress = totalProgress
                )
            }

            // Secure Account CTA for guests
            if (currentUser?.isGuest != false) {
                item {
                    SecureAccountCTABanner(onClick = { showAccountSheet = true })
                }
            }

            // 4. Priority Goals — horizontal LazyRow
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        "Priority Goals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (upcomingGoals.isNotEmpty()) {
                        androidx.compose.material3.Surface(
                            onClick = onNavigateToGoals,
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    "See all",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                androidx.compose.material3.Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            item {
                if (upcomingGoals.isEmpty()) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Icon(
                                Icons.Rounded.CheckCircle,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    "All caught up!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "No urgent deadlines. Keep up the great work!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 4.dp)
                    ) {
                        items(upcomingGoals.size) { index ->
                            CompactGoalTile(
                                goal = upcomingGoals[index],
                                onClick = { onGoalClick(upcomingGoals[index]) }
                            )
                        }
                    }
                }
            }

            // 5. Next Steps (first incomplete milestone per active goal)
            if (nextMilestones.isNotEmpty()) {
                item(key = "milestones_header") {
                    Text(
                        "Next Steps (${nextMilestones.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                item(key = "milestones_list") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 16.dp
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            nextMilestones.forEachIndexed { index, (goal, milestone) ->
                                CompactHomeMilestoneRow(
                                    goal = goal,
                                    milestone = milestone,
                                    focusMinutes = milestoneFocusMinutes[milestone.id] ?: 0,
                                    onRowClick = { onGoalClick(goal) },
                                    onStartFocus = { onStartFocusForMilestone(goal.id, milestone.id) }
                                )
                                if (index < nextMilestones.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Today's Habits
            if (habits.isNotEmpty()) {
                val pendingHabits = habits.filter { !it.isCompletedToday }
                val allDone = pendingHabits.isEmpty()

                item(key = "habits_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            "Today's Habits ($habitsCompleted/$totalHabits)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        androidx.compose.material3.Surface(
                            onClick = onNavigateToHabits,
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    "See all",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                androidx.compose.material3.Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (allDone) {
                    item(key = "habits_all_done") {
                        AllHabitsDoneCard(
                            totalCompleted = habits.size,
                            currentStreak = userProgress?.currentStreak ?: 0,
                            currentLevel = userProgress?.currentLevel ?: 1
                        )
                    }
                } else {
                    item(key = "habits_list") {
                        GlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 16.dp
                        ) {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                pendingHabits.forEachIndexed { index, habitWithStatus ->
                                    CompactHomeHabitRow(
                                        habitWithStatus = habitWithStatus,
                                        onCheckIn = { habitViewModel.toggleCheckIn(habitWithStatus.habit.id) }
                                    )
                                    if (index < pendingHabits.size - 1) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .height(1.dp)
                                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 7. Next Action Card (moved to bottom as shortcut)
            item {
                NextActionCard(
                    nextAction = nextAction,
                    onGoalClick = onGoalClick,
                    onHabitCheckIn = { habitId ->
                        habitViewModel.toggleCheckIn(habitId)
                    }
                )
            }
        }
    }

    if (showAccountSheet) {
        AccountCreationBottomSheet(
            authViewModel = authViewModel,
            onDismiss = { showAccountSheet = false },
            onNavigateToSignIn = {
                showAccountSheet = false
                onNavigateToProfile()
            }
        )
    }
}

@Composable
private fun CompactHomeHabitRow(
    habitWithStatus: HabitWithStatus,
    onCheckIn: () -> Unit
) {
    val habit = habitWithStatus.habit
    val categoryColor = habit.category.backgroundColor()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCheckIn)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // Category icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(categoryColor.copy(alpha = 0.15f)),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = habit.category.getIcon(),
                contentDescription = null,
                tint = categoryColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title + streak
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = habit.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (habit.currentStreak > 0) {
                Text(
                    text = "${habit.currentStreak} day streak",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF6B35)
                )
            }
        }

        // Tap to complete hint
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Icon(
                Icons.Rounded.Check,
                contentDescription = "Complete",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun CompactHomeMilestoneRow(
    goal: Goal,
    milestone: Milestone,
    focusMinutes: Int = 0,
    onRowClick: () -> Unit,
    onStartFocus: () -> Unit
) {
    val categoryColor = goal.category.backgroundColor()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRowClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        // Category-colored dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(categoryColor)
        )

        Spacer(Modifier.width(12.dp))

        // Milestone title + parent goal name + focus time
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = milestone.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = goal.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (focusMinutes > 0) {
                Text(
                    text = "${focusMinutes}m focused",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF6B35)
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Play icon to launch Focus
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF6B35).copy(alpha = 0.15f))
                .clickable(onClick = onStartFocus),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Start focus",
                tint = Color(0xFFFF6B35),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AllHabitsDoneCard(
    totalCompleted: Int,
    currentStreak: Int,
    currentLevel: Int
) {
    val advice = remember(currentStreak, currentLevel) {
        when {
            currentStreak >= 30 -> "A month of consistency! You're building lasting habits. Consider raising the bar with a new challenge."
            currentStreak >= 14 -> "Two weeks strong! Your discipline is becoming second nature. Keep this energy going."
            currentStreak >= 7 -> "A full week! Research shows it takes 21 days to form a habit — you're well on your way."
            currentStreak >= 3 -> "Great momentum! Focus on not breaking the chain. Every day counts."
            currentLevel >= 10 -> "Level $currentLevel and crushing it! Your consistency is inspiring."
            totalCompleted >= 5 -> "All $totalCompleted habits done — what a productive day! Take a moment to appreciate your effort."
            else -> "All done! Small wins lead to big transformations. You're on the right track."
        }
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Gradient accent
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFF81C784))))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = "\uD83C\uDF89",
                    style = MaterialTheme.typography.headlineLarge
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "All $totalCompleted habits done!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = advice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight
                )

                if (currentStreak > 0) {
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFFF6B35).copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = "\uD83D\uDD25 $currentStreak day streak",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF6B35),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
