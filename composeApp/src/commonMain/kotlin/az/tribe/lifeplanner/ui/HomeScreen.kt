package az.tribe.lifeplanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.ui.components.DailyMotivationCard
import az.tribe.lifeplanner.ui.components.DashboardStatsRow
import az.tribe.lifeplanner.ui.components.PriorityGoalsSection
import az.tribe.lifeplanner.ui.components.QuickActionsGrid
import az.tribe.lifeplanner.ui.components.TodayHabitsSection
import az.tribe.lifeplanner.ui.components.TodayProgressCard
import az.tribe.lifeplanner.ui.components.WelcomeHeader
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
    onNavigateToJournal: () -> Unit = {}
) {
    val snackBarHostState = remember { SnackbarHostState() }

    // Inject ViewModels
    val authViewModel: AuthViewModel = koinInject()
    val gamificationViewModel: GamificationViewModel = koinViewModel()
    val habitViewModel: HabitViewModel = koinViewModel()

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
    val activeGoals = goals.filter { it.status != GoalStatus.COMPLETED }.size
    val completedGoals = goals.filter { it.status == GoalStatus.COMPLETED }.size
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
    val goalsDueToday = goals.count { goal ->
        goal.status != GoalStatus.COMPLETED && goal.dueDate == today
    }

    // Calculate habits stats
    val habitsCompleted = habits.count { it.isCompletedToday }
    val totalHabits = habits.size

    LaunchedEffect(Unit) {
        viewModel.loadAllGoals()
        viewModel.loadAnalytics()
        habitViewModel.loadHabits()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackBarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = PaddingValues(top = innerPadding.calculateTopPadding())),
            contentPadding = PaddingValues(horizontal = LifePlannerDesign.Padding.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.lg)
        ) {
            // 1. Welcome Header with XP progress
            item {
                WelcomeHeader(
                    userName = currentUser?.displayName,
                    streak = userProgress?.currentStreak ?: 0,
                    level = userProgress?.currentLevel ?: 1,
                    levelTitle = userProgress?.title ?: "Novice",
                    xpProgress = userProgress?.levelProgress ?: 0f,
                    totalXp = userProgress?.totalXp ?: 0
                )
            }

            // 2. Quick Actions Grid (2x2)
            item {
                QuickActionsGrid(
                    onAddGoalClick = onAddGoalClick,
                    onAiSuggestClick = goToAiGeneration,
                    onNewHabitClick = onNavigateToHabits,
                    onJournalClick = onNavigateToJournal
                )
            }

            // 3. Dashboard Stats Row
            item {
                DashboardStatsRow(
                    activeGoals = activeGoals,
                    completedGoals = completedGoals,
                    totalProgress = totalProgress
                )
            }

            // 4. Today's Progress Card
            item {
                TodayProgressCard(
                    streak = userProgress?.currentStreak ?: 0,
                    habitsCompleted = habitsCompleted,
                    totalHabits = totalHabits,
                    goalsDueToday = goalsDueToday
                )
            }

            // 5. Today's Habits Section
            item {
                TodayHabitsSection(
                    habits = habits,
                    onCheckIn = { habitId ->
                        habitViewModel.toggleCheckIn(habitId)
                    },
                    onSeeAllClick = onNavigateToHabits
                )
            }

            // 6. Priority Goals Section
            item {
                PriorityGoalsSection(
                    upcomingGoals = upcomingGoals,
                    onGoalClick = onGoalClick
                )
            }

            // 7. Daily Inspiration (at bottom)
            item {
                DailyMotivationCard()
            }
        }
    }
}
