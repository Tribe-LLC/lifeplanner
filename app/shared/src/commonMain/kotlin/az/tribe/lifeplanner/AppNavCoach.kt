package az.tribe.lifeplanner

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import az.tribe.lifeplanner.data.repository.BuiltinCoachStore
import az.tribe.lifeplanner.ui.chat.AIChatScreen
import az.tribe.lifeplanner.ui.coach.CoachIntroConfig
import az.tribe.lifeplanner.ui.coach.CoachIntroScreen
import az.tribe.lifeplanner.ui.coach.CoachProfileScreen
import az.tribe.lifeplanner.ui.coach.CoachViewModel
import az.tribe.lifeplanner.ui.coach.CreateCoachScreen
import az.tribe.lifeplanner.ui.coach.CreateGroupScreen
import az.tribe.lifeplanner.ui.navigation.Screen
import org.koin.compose.koinInject

internal fun NavGraphBuilder.appNavCoach(navController: NavController) {
    // AI Chat Screen - Coach List
    composable(Screen.AIChat.route) {
        AIChatScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToCoach = { coachId ->
                navController.navigate("ai_chat/$coachId") {
                    launchSingleTop = true
                }
            },
            onNavigateToCreateCoach = {
                navController.navigate(Screen.CreateCoach.route) {
                    launchSingleTop = true
                }
            },
            onNavigateToCreateGroup = {
                navController.navigate(Screen.CreateGroup.route) {
                    launchSingleTop = true
                }
            },
            onNavigateToCoachProfile = { coachId ->
                navController.navigate("coach_profile/$coachId") {
                    launchSingleTop = true
                }
            }
        )
    }

    // AI Chat Screen - Chat with specific Coach
    composable(Screen.AIChatSession.route) { backStackEntry ->
        val coachId = backStackEntry.arguments?.read { getStringOrNull("sessionId") }
        AIChatScreen(
            coachId = coachId,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToCoach = { newCoachId ->
                navController.navigate("ai_chat/$newCoachId") {
                    launchSingleTop = true
                }
            },
            onNavigateToCreateCoach = {
                navController.navigate(Screen.CreateCoach.route) {
                    launchSingleTop = true
                }
            },
            onNavigateToCreateGroup = {
                navController.navigate(Screen.CreateGroup.route) {
                    launchSingleTop = true
                }
            },
            onNavigateToCoachProfile = { profileCoachId ->
                navController.navigate("coach_profile/$profileCoachId") {
                    launchSingleTop = true
                }
            }
        )
    }

    // Coach Profile Screen
    composable(
        Screen.CoachProfile.route,
        arguments = listOf(navArgument("coachId") { type = NavType.StringType })
    ) { backStackEntry ->
        val profileCoachId = backStackEntry.arguments?.read { getStringOrNull("coachId") }
            ?: return@composable
        CoachProfileScreen(
            coachId = profileCoachId,
            onNavigateBack = { navController.popBackStack() },
            onStartChat = { chatCoachId ->
                navController.navigate("ai_chat/$chatCoachId") {
                    popUpTo(Screen.CoachProfile.route) { inclusive = true }
                }
            },
            onPlayIntro = { introCoachId ->
                navController.navigate("coach_intro/$introCoachId") {
                    launchSingleTop = true
                }
            }
        )
    }

    // Coach Intro Screen, full-screen video + mascot + typewriter text
    composable(
        Screen.CoachIntro.route,
        arguments = listOf(navArgument("coachId") { type = NavType.StringType })
    ) { backStackEntry ->
        val introCoachId = backStackEntry.arguments?.read { getStringOrNull("coachId") }
            ?: return@composable
        val coach = BuiltinCoachStore.getAll().firstOrNull { it.id == introCoachId }
            ?: return@composable
        // Derive mascot URL from the coach's avatar URL (coaches/{slug}-mascot.png pattern)
        val mascotUrl = coach.imageUrl?.replace(Regex("\\.png$"), "-mascot.png")
        val config = CoachIntroConfig(
            videoUrl = coach.clipUrl ?: "",
            mascotImageUrl = mascotUrl,
            coachLabel = "${coach.emoji}  ${coach.name}",
            introText = coach.greeting,
        )
        CoachIntroScreen(
            config = config,
            onAction = { navController.popBackStack() }
        )
    }

    // Create Custom Coach Screen
    composable(Screen.CreateCoach.route) {
        val coachViewModel: CoachViewModel = koinInject()
        CreateCoachScreen(
            onNavigateBack = { navController.popBackStack() },
            onCoachSaved = { coach ->
                coachViewModel.createCoach(coach)
                navController.popBackStack()
            }
        )
    }

    // Edit Custom Coach Screen
    composable(
        route = Screen.EditCoach.route,
        arguments = listOf(navArgument("coachId") { type = NavType.StringType })
    ) { backStackEntry ->
        val coachId = backStackEntry.arguments?.read { getStringOrNull("coachId") }
            ?: return@composable
        val coachViewModel: CoachViewModel = koinInject()
        val coachToEdit = coachViewModel.getCoachById(coachId)
        CreateCoachScreen(
            coachToEdit = coachToEdit,
            onNavigateBack = { navController.popBackStack() },
            onCoachSaved = { coach ->
                coachViewModel.updateCoach(coach)
                navController.popBackStack()
            },
            onDeleteCoach = {
                coachViewModel.deleteCoach(coachId)
                navController.popBackStack()
            }
        )
    }

    // Create Coach Group Screen
    composable(Screen.CreateGroup.route) {
        val coachViewModel: CoachViewModel = koinInject()
        val uiState by coachViewModel.uiState.collectAsState()
        CreateGroupScreen(
            customCoaches = uiState.customCoaches,
            onNavigateBack = { navController.popBackStack() },
            onGroupSaved = { group ->
                coachViewModel.createGroup(group)
                navController.popBackStack()
            }
        )
    }

    // Edit Coach Group Screen
    composable(
        route = Screen.EditGroup.route,
        arguments = listOf(navArgument("groupId") { type = NavType.StringType })
    ) { backStackEntry ->
        val groupId = backStackEntry.arguments?.read { getStringOrNull("groupId") }
            ?: return@composable
        val coachViewModel: CoachViewModel = koinInject()
        val uiState by coachViewModel.uiState.collectAsState()
        val groupToEdit = coachViewModel.getGroupById(groupId)
        CreateGroupScreen(
            groupToEdit = groupToEdit,
            customCoaches = uiState.customCoaches,
            onNavigateBack = { navController.popBackStack() },
            onGroupSaved = { group ->
                coachViewModel.updateGroup(group)
                navController.popBackStack()
            },
            onDeleteGroup = {
                coachViewModel.deleteGroup(groupId)
                navController.popBackStack()
            }
        )
    }
}
