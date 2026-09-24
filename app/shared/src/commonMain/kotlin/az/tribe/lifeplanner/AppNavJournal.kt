package az.tribe.lifeplanner

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import az.tribe.lifeplanner.ui.journal.JournalCreationWizardScreen
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.ui.journal.JournalEntryDetailScreen
import az.tribe.lifeplanner.ui.journal.JournalScreen
import az.tribe.lifeplanner.ui.navigation.Screen

internal fun NavGraphBuilder.appNavJournal(
    navController: NavController,
    tabIndex: Map<String, Int>,
    slideOffset: (Int) -> Int,
    /** Read lazily: a `composable {}` body captures its arguments once, so an Int would go stale. */
    hubSelectedTab: () -> Int,
    onTabSelected: (Int) -> Unit,
    onSelectedDateChanged: (kotlinx.datetime.LocalDate) -> Unit = {}
) {
    composable(
        Screen.Journal.route,
        enterTransition = {
            val fromIndex = tabIndex[initialState.destination.route]
            val toIndex = tabIndex[targetState.destination.route]
            if (fromIndex != null && toIndex != null) {
                slideInHorizontally(tween(300)) { w ->
                    if (fromIndex > toIndex) -slideOffset(w) else slideOffset(w)
                } + fadeIn(tween(300))
            } else fadeIn(tween(300))
        },
        exitTransition = {
            val fromIndex = tabIndex[initialState.destination.route]
            val toIndex = tabIndex[targetState.destination.route]
            if (fromIndex != null && toIndex != null) {
                slideOutHorizontally(tween(300)) { w ->
                    if (fromIndex < toIndex) -slideOffset(w) else slideOffset(w)
                } + fadeOut(tween(300))
            } else fadeOut(tween(300))
        },
        popEnterTransition = {
            val fromIndex = tabIndex[initialState.destination.route]
            val toIndex = tabIndex[targetState.destination.route]
            if (fromIndex != null && toIndex != null) {
                slideInHorizontally(tween(300)) { w ->
                    if (fromIndex > toIndex) -slideOffset(w) else slideOffset(w)
                } + fadeIn(tween(300))
            } else fadeIn(tween(300))
        },
        popExitTransition = {
            val fromIndex = tabIndex[initialState.destination.route]
            val toIndex = tabIndex[targetState.destination.route]
            if (fromIndex != null && toIndex != null) {
                slideOutHorizontally(tween(300)) { w ->
                    if (fromIndex < toIndex) -slideOffset(w) else slideOffset(w)
                } + fadeOut(tween(300))
            } else fadeOut(tween(300))
        }
    ) {
        // Show back button when navigated from within the app (not bottom nav tab switch)
        val previousRoute = navController.previousBackStackEntry?.destination?.route
        val isBottomNavEntry = previousRoute == null || previousRoute == Screen.ForYou.route
        JournalScreen(
            onNavigateBack = { navController.popBackStack() },
            onEntryClick = { entryId ->
                navController.navigate("journal_entry_detail/$entryId") {
                    launchSingleTop = true
                }
            },
            onNavigateToWizard = { mood ->
                val route = if (mood != null) "journal_wizard?mood=${mood.name}" else "journal_wizard"
                navController.navigate(route) {
                    launchSingleTop = true
                }
            },
            isFromBottomNav = isBottomNavEntry,
            selectedTab = hubSelectedTab(),
            onTabSelected = onTabSelected,
            onSelectedDateChanged = onSelectedDateChanged,
            onPracticeHabit = { id ->
                navController.navigate("habit_practice/$id") { launchSingleTop = true }
            },
            onGoalClick = { goal ->
                navController.navigate("goal_detail/${goal.id}") {
                    launchSingleTop = true
                }
            },
            onAddGoalClick = {
                navController.navigate(Screen.GoalWizard.route) {
                    launchSingleTop = true
                }
            },
            onAddHabitClick = {
                navController.navigate(Screen.AddHabit.route) {
                    launchSingleTop = true
                }
            },
            onHabitClick = { habitId ->
                navController.navigate("habit_detail_redesign/$habitId") {
                    launchSingleTop = true
                }
            },
            onNavigateToFocus = {
                navController.navigate("focus_setup") {
                    launchSingleTop = true
                }
            },
            onStartFocusForMilestone = { goalId, milestoneId ->
                navController.navigate("focus_setup?goalId=$goalId&milestoneId=$milestoneId") {
                    launchSingleTop = true
                }
            },
            onAbilityClick = { abilityId ->
                navController.navigate("ability_detail/$abilityId") {
                    launchSingleTop = true
                }
            },
            onCreateAbility = {
                navController.navigate(Screen.CreateAbility.route) {
                    launchSingleTop = true
                }
            }
        )
    }

    // Journal Creation Wizard
    composable(
        route = Screen.JournalWizard.route,
        arguments = listOf(
            navArgument("goalId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("mood") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
            navArgument("date") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        )
    ) { backStackEntry ->
        val goalId = backStackEntry.arguments?.read { getStringOrNull("goalId") }
        val mood = backStackEntry.arguments?.read { getStringOrNull("mood") }
            ?.let { runCatching { Mood.valueOf(it) }.getOrNull() }
        // The hub's day lens rides along, so writing while looking at Tuesday files under Tuesday.
        val date = backStackEntry.arguments?.read { getStringOrNull("date") }
            ?.let { runCatching { kotlinx.datetime.LocalDate.parse(it) }.getOrNull() }
        JournalCreationWizardScreen(
            onNavigateBack = { navController.popBackStack() },
            preSelectedGoalId = goalId,
            initialMood = mood,
            initialDate = date,
        )
    }

    // Journal Entry Detail Screen
    composable(
        route = Screen.JournalEntryDetail.route,
        arguments = listOf(navArgument("entryId") { type = NavType.StringType })
    ) { backStackEntry ->
        val entryId = backStackEntry.arguments?.read { getStringOrNull("entryId") }
            ?: return@composable
        JournalEntryDetailScreen(
            entryId = entryId,
            onBackClick = { navController.popBackStack() },
            onNavigateToGoal = { goalId ->
                navController.navigate("goal_detail/$goalId") {
                    launchSingleTop = true
                }
            }
        )
    }
}
