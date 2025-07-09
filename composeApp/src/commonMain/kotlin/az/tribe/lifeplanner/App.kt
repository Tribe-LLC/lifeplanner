package az.tribe.lifeplanner

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import az.tribe.lifeplanner.ui.AddGoalScreen
import az.tribe.lifeplanner.ui.AiGoalGenerationScreen
import az.tribe.lifeplanner.ui.AnalyticsDashboard
import az.tribe.lifeplanner.ui.GoalDetailScreen
import az.tribe.lifeplanner.ui.GoalViewModel
import az.tribe.lifeplanner.ui.HomeScreen
import az.tribe.lifeplanner.ui.OnboardingScreen
import az.tribe.lifeplanner.ui.navigation.Screen
import az.tribe.lifeplanner.ui.theme.LifePlannerTheme
import com.mmk.kmpnotifier.notification.NotifierManager
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

@Composable
@Preview
fun App(viewModel: GoalViewModel = koinInject()) {
    LifePlannerTheme {

        var myPushNotificationToken by remember { mutableStateOf("") }
        LaunchedEffect(true) {

            println("LaunchedEffectApp is called")
            NotifierManager.addListener(object : NotifierManager.Listener {
                override fun onNewToken(token: String) {
                    myPushNotificationToken = token
                    println("onNewToken: $token")
                }
            })
            myPushNotificationToken = NotifierManager.getPushNotifier().getToken() ?: ""
            println("Firebase Token: $myPushNotificationToken")
        }

        val isForceUpdateEnabled = viewModel.isForceUpdateEnabled.collectAsState().value ?: false

        val navController = rememberNavController()


        NavHost(navController = navController, startDestination = Screen.Home.route) {

            composable(Screen.Home.route) {

                if (isForceUpdateEnabled)
                    Text(text = "Firebase Remote Config\n\nisForceUpdateEnabled: $isForceUpdateEnabled")
                else
                    HomeScreen(
                        viewModel = viewModel,
                        onGoalClick = { goal ->
                            navController.navigate("goal_detail/${goal.id}")
                        },
                        onAddGoalClick = {
                            navController.navigate(Screen.AddGoal.route)
                        },
                        goToAnalytics = {
                            navController.navigate(Screen.Analytics.route)
                        },
                        // NEW: Add AI generation navigation
                        goToAiGeneration = {
                            navController.navigate(Screen.AiGoalGeneration.route){
                                launchSingleTop = true

                                popUpTo(Screen.Home.route)
                            }
                        }
                    )
            }

            composable(
                route = "goal_detail/{goalId}",
                arguments = listOf(navArgument("goalId") { type = NavType.StringType })
            ) { backStackEntry ->
                val goalId = backStackEntry.arguments?.getString("goalId") ?: return@composable
                GoalDetailScreen(
                    goalId = goalId,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onEditClick = { navController.navigate("edit_goal/$goalId") },
                )
            }

            composable(Screen.AddGoal.route) {
                AddGoalScreen(
                    viewModel = viewModel,
                    onGoalSaved = { navController.popBackStack() },
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(Screen.Onboarding.route) {
                OnboardingScreen()
            }


            composable(Screen.Analytics.route) {
                AnalyticsDashboard(viewModel, onBackClick = {
                    navController.popBackStack()
                })
            }

            composable(Screen.AiGoalGeneration.route) {
                AiGoalGenerationScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onHomeClick = {
                        navController.popBackStack()
                    }

                )
            }

            composable(
                route = Screen.EditGoal.route,
                arguments = listOf(navArgument("goalId") { type = NavType.StringType })
            ) { backStackEntry ->
                val goalId = backStackEntry.arguments?.getString("goalId") ?: return@composable

            }
        }
    }
}
