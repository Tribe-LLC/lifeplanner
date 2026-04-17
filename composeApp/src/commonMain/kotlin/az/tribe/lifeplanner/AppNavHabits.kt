package az.tribe.lifeplanner

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import az.tribe.lifeplanner.ui.backup.BackupSettingsScreen
import az.tribe.lifeplanner.ui.balance.LifeBalanceScreen
import az.tribe.lifeplanner.ui.habit.AddHabitScreen
import az.tribe.lifeplanner.ui.habit.HabitTrackerScreen
import az.tribe.lifeplanner.ui.habit.SmartHabitGeneratorScreen
import az.tribe.lifeplanner.ui.home.StoryReaderScreen
import az.tribe.lifeplanner.ui.reminder.ReminderSettingsScreen
import az.tribe.lifeplanner.ui.focus.FocusScreen
import az.tribe.lifeplanner.ui.navigation.Screen
import az.tribe.lifeplanner.ui.retrospective.RetrospectiveScreen

internal fun NavGraphBuilder.appNavHabits(navController: NavController) {
    // Habits Screen
    composable(Screen.HabitTracker.route) {
        HabitTrackerScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToAddHabit = {
                navController.navigate(Screen.AddHabit.route) {
                    launchSingleTop = true
                }
            },
            onNavigateToFocus = {
                navController.navigate(Screen.FocusSetup.route) {
                    launchSingleTop = true
                }
            }
        )
    }

    composable(Screen.SmartHabitGenerator.route) {
        SmartHabitGeneratorScreen(
            onBackClick = { navController.popBackStack() },
            onComplete = { navController.popBackStack() },
            onNavigateToManual = {
                navController.navigate(Screen.AddHabit.route) { launchSingleTop = true }
            }
        )
    }

    composable(Screen.AddHabit.route) {
        AddHabitScreen(
            onHabitSaved = { navController.popBackStack() },
            onBackClick = { navController.popBackStack() }
        )
    }

    // Focus Timer Screen
    composable(
        route = "focus_setup?goalId={goalId}&milestoneId={milestoneId}",
        arguments = listOf(
            navArgument("goalId") { type = NavType.StringType; defaultValue = "" },
            navArgument("milestoneId") { type = NavType.StringType; defaultValue = "" }
        )
    ) { backStackEntry ->
        val goalId = backStackEntry.arguments?.read { getStringOrNull("goalId") }
            ?.takeIf { it.isNotEmpty() }
        val milestoneId = backStackEntry.arguments?.read { getStringOrNull("milestoneId") }
            ?.takeIf { it.isNotEmpty() }
        FocusScreen(
            onNavigateBack = { navController.popBackStack() },
            goalId = goalId,
            milestoneId = milestoneId
        )
    }

    // Reminder Settings Screen
    composable(Screen.Reminders.route) {
        ReminderSettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    // Life Balance Screen — primary bottom nav tab; no back button in TopAppBar
    composable(Screen.LifeBalance.route) {
        LifeBalanceScreen(
            onNavigateBack = { navController.popBackStack() },
            showBackButton = false,
            onCreateHabit = { _ ->
                navController.navigate(Screen.HabitTracker.route)
            },
            onNavigateToCoach = { coachId, message ->
                az.tribe.lifeplanner.ui.balance.InsightMessageHolder.pendingMessage = message
                navController.navigate("ai_chat/$coachId") {
                    launchSingleTop = true
                }
            }
        )
    }

    // Retrospective Screen
    composable(Screen.Retrospective.route) {
        RetrospectiveScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    // Health Dashboard Screen
    composable(Screen.Health.route) {
        az.tribe.lifeplanner.ui.health.HealthDashboardScreen(
            navController = navController
        )
    }

    // Story Reader Screen
    composable(Screen.StoryReader.route) {
        StoryReaderScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    // Backup Settings Screen
    composable(Screen.BackupSettings.route) {
        BackupSettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    // Global Search Screen
    composable(
        route = Screen.Search.route,
        arguments = listOf(navArgument("filter") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        })
    ) { backStackEntry ->
        val filterArg = backStackEntry.arguments?.read { getStringOrNull("filter") }
        val initialFilter = filterArg?.let {
            runCatching { az.tribe.lifeplanner.ui.search.SearchFilter.valueOf(it) }.getOrNull()
        }
        az.tribe.lifeplanner.ui.search.SearchScreen(
            initialFilter = initialFilter,
            onBack = { navController.popBackStack() },
            onGoalClick = { goalId ->
                navController.navigate("goal_detail/$goalId") { launchSingleTop = true }
            },
            onHabitClick = {
                navController.navigate(Screen.HabitTracker.route) { launchSingleTop = true }
            },
            onJournalEntryClick = { entryId ->
                navController.navigate("journal_entry_detail/$entryId") { launchSingleTop = true }
            },
            onCoachClick = { coachId ->
                navController.navigate("coach_profile/$coachId") { launchSingleTop = true }
            },
            onSettingClick = { destination ->
                val route = when (destination) {
                    az.tribe.lifeplanner.ui.search.SearchDestination.GOALS -> "search?filter=GOALS"
                    az.tribe.lifeplanner.ui.search.SearchDestination.HABITS -> "search?filter=HABITS"
                    az.tribe.lifeplanner.ui.search.SearchDestination.JOURNAL -> Screen.Journal.route
                    az.tribe.lifeplanner.ui.search.SearchDestination.AI_COACH -> Screen.AIChat.route
                    az.tribe.lifeplanner.ui.search.SearchDestination.REMINDERS -> Screen.Reminders.route
                    az.tribe.lifeplanner.ui.search.SearchDestination.LIFE_BALANCE -> Screen.LifeBalance.route
                    az.tribe.lifeplanner.ui.search.SearchDestination.ACHIEVEMENTS -> Screen.Achievements.route
                    az.tribe.lifeplanner.ui.search.SearchDestination.HEALTH -> Screen.Health.route
                    az.tribe.lifeplanner.ui.search.SearchDestination.FOCUS -> "focus_setup"
                    az.tribe.lifeplanner.ui.search.SearchDestination.RETROSPECTIVE -> Screen.Retrospective.route
                    az.tribe.lifeplanner.ui.search.SearchDestination.BACKUP -> Screen.BackupSettings.route
                }
                navController.navigate(route) { launchSingleTop = true }
            }
        )
    }
}
