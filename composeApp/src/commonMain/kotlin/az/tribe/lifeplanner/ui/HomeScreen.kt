package az.tribe.lifeplanner.ui

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import az.tribe.lifeplanner.ui.components.getIcon
import az.tribe.lifeplanner.ui.habit.HabitWithStatus
import az.tribe.lifeplanner.domain.repository.ChatRepository
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.domain.model.ChatSession
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.ui.theme.LifePlannerGradients
import az.tribe.lifeplanner.ui.theme.backgroundColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.ui.components.CompactGoalTile
import az.tribe.lifeplanner.ui.components.NextAction
import az.tribe.lifeplanner.ui.components.NextActionCard
import az.tribe.lifeplanner.ui.components.GlassCard
import az.tribe.lifeplanner.ui.components.NewBadgesCard
import az.tribe.lifeplanner.ui.components.QuickActionsPillRow
import az.tribe.lifeplanner.ui.components.UpdateReminderBanner
import az.tribe.lifeplanner.ui.components.VerifyEmailBanner
import az.tribe.lifeplanner.ui.components.SyncStatusIndicator
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.data.sync.SyncStatus
import kotlinx.coroutines.flow.StateFlow
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
import az.tribe.lifeplanner.ui.habit.HabitViewModel
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.objectives.BeginnerObjectiveViewModel
import az.tribe.lifeplanner.ui.objectives.BeginnerObjectivesCard
import az.tribe.lifeplanner.domain.model.ObjectiveType
import az.tribe.lifeplanner.ui.components.AddGoalBottomSheet
import az.tribe.lifeplanner.ui.components.StoriesCarousel
import az.tribe.lifeplanner.ui.home.generateDailyRecapStory
import az.tribe.lifeplanner.ui.home.getCuratedTipStories
import az.tribe.lifeplanner.domain.model.Story
import az.tribe.lifeplanner.domain.repository.StoryRepository
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    viewModel: GoalViewModel,
    onGoalClick: (Goal) -> Unit,
    goToAnalytics: () -> Unit,
    onAddGoalClick: () -> Unit,
    goToAiGeneration: () -> Unit,
    onNavigateToHabits: () -> Unit = {},
    onNavigateToAddHabit: () -> Unit = {},
    onNavigateToJournal: () -> Unit = {},
    onNavigateToGoals: () -> Unit = {},
    onNavigateToAchievements: () -> Unit = {},
    onNavigateToFocus: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToRetrospective: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onNavigateToReminders: () -> Unit = {},
    onNavigateToLifeBalance: () -> Unit = {},
    onNavigateToHealth: () -> Unit = {},
    onNavigateToStoryReader: () -> Unit = {},
    onNavigateToTemplates: () -> Unit = {},
    onContinueChat: (sessionId: String) -> Unit = {},
    onStartFocusForMilestone: (goalId: String, milestoneId: String) -> Unit = { _, _ -> },
    onNavigateToJournalEntry: (entryId: String) -> Unit = {},
    showUpdateReminder: Boolean = false,
    onUpdateClick: () -> Unit = {},
) {
    val snackBarHostState = remember { SnackbarHostState() }
    var showAccountSheet by remember { mutableStateOf(false) }
    var showAddGoalSheet by remember { mutableStateOf(false) }

    // Inject ViewModels
    val authViewModel: AuthViewModel = koinInject()
    val gamificationViewModel: GamificationViewModel = koinViewModel()
    val habitViewModel: HabitViewModel = koinViewModel()
    val objectiveViewModel: BeginnerObjectiveViewModel = koinViewModel()

    val storyRepository: StoryRepository = koinInject()

    val authState by authViewModel.authState.collectAsState()
    val userProgress by gamificationViewModel.userProgress.collectAsState()
    val newBadges by gamificationViewModel.newBadges.collectAsState()
    val goals by viewModel.goals.collectAsState()
    val habits by habitViewModel.habits.collectAsState()

    // Extract current user
    val currentUser = when (authState) {
        is AuthState.Authenticated -> (authState as AuthState.Authenticated).user
        is AuthState.Guest -> (authState as AuthState.Guest).user
        else -> null
    }

    // Calculate dashboard stats
    if (goals.isNotEmpty()) {
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

    // Stories: remote + daily recap + curated tips
    var remoteStories by remember { mutableStateOf<List<Story>>(emptyList()) }
    LaunchedEffect(Unit) {
        remoteStories = storyRepository.getActiveStories()
    }
    val allStories = remember(habits, userProgress, today, remoteStories) {
        val recap = generateDailyRecapStory(userProgress, habits, today)
        val tips = getCuratedTipStories(today)
        listOf(recap) + tips + remoteStories
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

    // Load recent chat session for Coach AI card
    val chatRepository: ChatRepository = koinInject()
    var recentSession by remember { mutableStateOf<ChatSession?>(null) }
    var recentCoach by remember { mutableStateOf<CoachPersona?>(null) }
    LaunchedEffect(Unit) {
        val sessions = chatRepository.getAllSessions()
        val latest = sessions.maxByOrNull { it.lastMessageAt }
        recentSession = latest
        recentCoach = latest?.let { CoachPersona.getById(it.coachId) }
    }

    // Calculate habits stats
    val beginnerObjectives by objectiveViewModel.objectives.collectAsState()
    val objectivesExpanded by objectiveViewModel.isExpanded.collectAsState()
    val objectivesDismissed by objectiveViewModel.isDismissed.collectAsState()
    val pendingVerifyEmail by authViewModel.pendingVerificationEmail.collectAsState()

    // Reflection prompt after habit check-in from HomeScreen
    val recentCheckIn by habitViewModel.recentCheckIn.collectAsState()
    LaunchedEffect(recentCheckIn) {
        recentCheckIn?.let { checkIn ->
            val result = snackBarHostState.showSnackbar(
                message = "Nice work on \"${checkIn.habit.title}\"! Add a reflection?",
                actionLabel = "Reflect",
                duration = androidx.compose.material3.SnackbarDuration.Long
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                onNavigateToJournal()
            }
            habitViewModel.clearRecentCheckIn()
        }
    }

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

    LaunchedEffect(authState) {
        viewModel.loadAllGoals()
        viewModel.loadAnalytics()
        habitViewModel.loadHabits()
        gamificationViewModel.refresh()
    }

    rememberCoroutineScope()

    fun handleStoryAction(action: String?) {
        when (action) {
            "habits" -> onNavigateToHabits()
            "add_habit" -> onNavigateToAddHabit()
            "goals" -> onNavigateToGoals()
            "focus" -> onNavigateToFocus()
            "journal" -> onNavigateToJournal()
            "achievements" -> onNavigateToAchievements()
            "ai_chat" -> onNavigateToChat()
            "life_balance" -> onNavigateToLifeBalance()
            "health" -> onNavigateToHealth()
            else -> { /* no-op */ }
        }
    }

    val greetingLine = remember(currentUser?.displayName) {
        val hour = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).hour
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        } + (currentUser?.displayName?.let { ", $it" } ?: "") + "!"
    }

    val motivationLine = remember {
        val hour = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).hour
        when (hour) {
            in 5..11 -> "Let's make today count"
            in 12..16 -> "Keep the momentum going"
            in 17..20 -> "Great work today"
            else -> "Rest well, recharge"
        }
    }

    val level = userProgress?.currentLevel ?: 1
    val streak = userProgress?.currentStreak ?: 0

    Scaffold(
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
                bottom = innerPadding.calculateBottomPadding() + 112.dp
            ),
            verticalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.sm)
        ) {
            // 1. Hero Banner
            item(key = "hero_banner") {
                HeroBanner(
                    greeting = greetingLine,
                    subtitle = motivationLine,
                    level = level,
                    streak = streak,
                    levelTitle = userProgress?.title ?: "Novice",
                    isSignedIn = authState is AuthState.Authenticated && currentUser?.email != null,
                    onProfileClick = onNavigateToProfile
                )
            }

            // Update reminder banner (shown after user skips soft update)
            if (showUpdateReminder) {
                item(key = "update_reminder") {
                    UpdateReminderBanner(onUpdateClick = onUpdateClick)
                }
            }

            // Stories carousel
            if (allStories.isNotEmpty()) {
                item(key = "stories_carousel") {
                    StoriesCarousel(
                        stories = allStories,
                        onStoryAction = { action -> handleStoryAction(action) },
                        onOpenReader = { onNavigateToStoryReader() },
                        modifier = Modifier.layout { measurable, constraints ->
                            val extraWidth = LifePlannerDesign.Padding.screenHorizontal.roundToPx() * 2
                            val placeable = measurable.measure(
                                constraints.copy(maxWidth = constraints.maxWidth + extraWidth)
                            )
                            layout(placeable.width, placeable.height) {
                                placeable.place(-LifePlannerDesign.Padding.screenHorizontal.roundToPx(), 0)
                            }
                        }
                    )
                }
            }

            // 2. Smart Actions — contextual based on user state
            item {
                QuickActionsPillRow(
                    onAddGoal = { showAddGoalSheet = true },
                    onAiSuggest = goToAiGeneration,
                    onNewHabit = onNavigateToAddHabit,
                    onHabitCheckIn = onNavigateToHabits,
                    onJournal = onNavigateToJournal,
                    onFocus = onNavigateToFocus,
                    onCoach = onNavigateToChat,
                    isCoachLocked = level < 3,
                    hasGoals = goals.isNotEmpty(),
                    hasHabits = habits.isNotEmpty(),
                    pendingHabits = habits.count { !it.isCompletedToday },
                    streak = streak,
                    goalsDueToday = goalsDueToday.size
                )
            }


            // 3. Beginner Objectives — getting started checklist (hidden permanently once all done)
            if (!objectivesDismissed && beginnerObjectives.isNotEmpty()) {
                item(key = "beginner_objectives") {
                    BeginnerObjectivesCard(
                        objectives = beginnerObjectives,
                        isExpanded = objectivesExpanded,
                        allComplete = beginnerObjectives.isNotEmpty() && beginnerObjectives.all { it.isCompleted },
                        onToggleExpanded = { objectiveViewModel.toggleExpanded() },
                        onDismiss = { objectiveViewModel.dismiss() },
                        onObjectiveClick = { type ->
                            when (type) {
                                ObjectiveType.CREATE_GOAL -> onAddGoalClick()
                                ObjectiveType.CREATE_HABIT -> onNavigateToAddHabit()
                                ObjectiveType.WRITE_JOURNAL -> onNavigateToJournal()
                                ObjectiveType.COMPLETE_HABIT_CHECKIN -> onNavigateToHabits()
                                ObjectiveType.START_FOCUS_SESSION -> onNavigateToFocus()
                                ObjectiveType.CHAT_WITH_COACH -> onNavigateToChat()
                                ObjectiveType.SET_REMINDER -> onNavigateToReminders()
                                ObjectiveType.CHECK_LIFE_BALANCE -> onNavigateToLifeBalance()
                                ObjectiveType.COMPLETE_GOAL -> onNavigateToGoals()
                                ObjectiveType.SECURE_ACCOUNT -> { showAccountSheet = true }
                            }
                        }
                    )
                }
            }

            // New Badges quick action — tap to view on Achievements screen
            if (newBadges.isNotEmpty()) {
                item(key = "new_badges") {
                    NewBadgesCard(
                        badges = newBadges,
                        onClick = onNavigateToAchievements
                    )
                }
            }

            // Secure Account CTA — smart status banner
            if (pendingVerifyEmail != null) {
                // Email linked but not yet verified — show verification status
                item(key = "verify_email_banner") {
                    VerifyEmailBanner(
                        email = pendingVerifyEmail!!,
                        onResend = { authViewModel.resendVerificationEmail(pendingVerifyEmail!!) }
                    )
                }
            } else if (currentUser?.email == null) {
                // Pure guest — show secure account CTA
                item {
                    SecureAccountCTABanner(onClick = { showAccountSheet = true })
                }
            }

            // 4. Priority Goals — horizontal LazyRow
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Priority Goals",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (upcomingGoals.isNotEmpty()) {
                        Surface(
                            onClick = onNavigateToGoals,
                            shape = RoundedCornerShape(50),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "See all",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { showAddGoalSheet = true }),
                        cornerRadius = 16.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (goals.isEmpty()) "Set your first goal" else "All goals completed!",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (goals.isEmpty()) "Tap to create a goal and start tracking progress"
                                    else "Tap to set a new goal",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
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
            run {
                val pendingHabits = habits.filter { !it.isCompletedToday }
                val allDone = habits.isNotEmpty() && pendingHabits.isEmpty()

                item(key = "habits_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (habits.isNotEmpty()) "Today's Habits ($habitsCompleted/$totalHabits)"
                            else "Today's Habits",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (habits.isNotEmpty()) {
                            Surface(
                                onClick = onNavigateToHabits,
                                shape = RoundedCornerShape(50),
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "See all",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
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

                if (habits.isEmpty()) {
                    item(key = "habits_empty_cta") {
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onNavigateToHabits),
                            cornerRadius = 16.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF4CAF50).copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Add,
                                        null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Build your first habit",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Tap to create a daily habit and build consistency",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else if (allDone) {
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

            // 7. Explore section — Retrospective, Flow Focus, Coach AI
            item(key = "explore_header") {
                Text(
                    "Explore",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Day Retrospective card
            item(key = "retrospective_card") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToRetrospective),
                    cornerRadius = 16.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF7C4DFF).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                contentDescription = null,
                                tint = Color(0xFF7C4DFF),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Yesterday's Recap",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Review what you accomplished",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Flow Focus card
            item(key = "flow_focus_card") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToFocus),
                    cornerRadius = 16.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFF6B35).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color(0xFFFF6B35),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Flow Focus",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Start a free-flow focus session",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Coach AI card — locked until level 2
            item(key = "coach_ai_card") {
                val session = recentSession
                val coach = recentCoach
                val coachUnlocked = level >= 3

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = {
                            if (coachUnlocked) {
                                if (session != null) {
                                    onContinueChat(session.coachId)
                                } else {
                                    onNavigateToChat()
                                }
                            } else {
                                onNavigateToChat() // Will show locked screen
                            }
                        }),
                    cornerRadius = 16.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (coachUnlocked) Color(0xFF00BFA5).copy(alpha = 0.12f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Psychology,
                                contentDescription = null,
                                tint = if (coachUnlocked) Color(0xFF00BFA5)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            if (!coachUnlocked) {
                                Text(
                                    "Coach AI",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Unlocks at Level 3",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFF6B35)
                                )
                            } else if (session != null && coach != null) {
                                Text(
                                    "Continue with ${coach.name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val lastMsg = session.messages.lastOrNull { it.role == MessageRole.ASSISTANT }
                                Text(
                                    lastMsg?.content?.take(60) ?: session.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    "Coach AI",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Start a conversation with your coach",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 8. Next Action Card — only show when there's something actionable
            if (nextAction !is NextAction.AllCaughtUp || goals.isNotEmpty() || habits.isNotEmpty()) {
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
    }

    if (showAddGoalSheet) {
        AddGoalBottomSheet(
            onDismiss = { showAddGoalSheet = false },
            onQuickAddClick = {
                showAddGoalSheet = false
                onAddGoalClick()
            },
            onAiGenerateClick = {
                showAddGoalSheet = false
                goToAiGeneration()
            }
        )
    }

    if (showAccountSheet) {
        // Guest users should see sign-up (link account) flow, not sign-in
        val isGuest = authState is AuthState.Guest
        AuthBottomSheet(
            isSignUp = isGuest,
            authViewModel = authViewModel,
            authState = authState,
            onDismiss = { showAccountSheet = false },
            onSuccess = { showAccountSheet = false }
        )
    }
}

@Composable
private fun HeroBanner(
    greeting: String,
    subtitle: String,
    level: Int,
    streak: Int,
    levelTitle: String,
    isSignedIn: Boolean,
    onProfileClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp
    ) {
        Box {
            // Gradient accent at the top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 20.dp, end = 12.dp, bottom = 16.dp)
            ) {
                // Top row: greeting + profile icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greeting,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSignedIn) subtitle else "Sign in to sync & back up your data",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSignedIn) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onProfileClick) {
                        Icon(
                            Icons.Rounded.AccountCircle,
                            contentDescription = "Profile",
                            tint = if (isSignedIn) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Pills row: Level + Streak + Sync
                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Lv.$level · $levelTitle",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    if (streak > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFFF6B35).copy(alpha = 0.12f)
                        ) {
                            Text(
                                "\uD83D\uDD25 $streak day streak",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF6B35),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    if (!isSignedIn) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                "Guest",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(categoryColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
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
            contentAlignment = Alignment.Center
        ) {
            Icon(
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
        verticalAlignment = Alignment.CenterVertically
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
            contentAlignment = Alignment.Center
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
                horizontalAlignment = Alignment.CenterHorizontally
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
                    Surface(
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
