package az.tribe.lifeplanner

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import az.tribe.lifeplanner.ui.AchievementsScreen
import az.tribe.lifeplanner.ui.AddGoalFromTemplateScreen
import az.tribe.lifeplanner.ui.AddGoalScreen
import az.tribe.lifeplanner.ui.AIChatScreen
import az.tribe.lifeplanner.ui.AiGoalGenerationScreen
import az.tribe.lifeplanner.ui.AnalyticsDashboard
import az.tribe.lifeplanner.ui.DependencyGraphScreen
import az.tribe.lifeplanner.ui.GoalDetailScreen
import az.tribe.lifeplanner.ui.GoalViewModel
import az.tribe.lifeplanner.ui.GoalsScreen
import az.tribe.lifeplanner.ui.HabitTrackerScreen
import az.tribe.lifeplanner.ui.JournalScreen
import az.tribe.lifeplanner.ui.LifeBalanceScreen
import az.tribe.lifeplanner.ui.BackupSettingsScreen
import az.tribe.lifeplanner.ui.ProfileScreen
import az.tribe.lifeplanner.ui.ReminderSettingsScreen
import az.tribe.lifeplanner.ui.ReviewScreen
import az.tribe.lifeplanner.ui.TemplatePickerScreen
import az.tribe.lifeplanner.ui.HomeScreen
import az.tribe.lifeplanner.ui.OnboardingScreen
import az.tribe.lifeplanner.ui.SignInScreen
import az.tribe.lifeplanner.ui.components.BottomNavigationBar
import az.tribe.lifeplanner.ui.navigation.Screen
import az.tribe.lifeplanner.ui.theme.LifePlannerTheme
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import com.mmk.kmpnotifier.notification.NotifierManager
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

@Composable
@Preview
fun App(
    viewModel: GoalViewModel = koinInject(),
    authViewModel: AuthViewModel = koinInject()
) {
    LifePlannerTheme {
        var myPushNotificationToken by remember { mutableStateOf("") }

        // Collect onboarding state from ViewModel
        val hasCompletedOnboarding by authViewModel.hasCompletedOnboarding.collectAsState()

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
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // Wait for onboarding check to complete
        val startDestination = when (hasCompletedOnboarding) {
            true -> Screen.Home.route
            false -> Screen.Onboarding.route
            null -> return@LifePlannerTheme // Still loading
        }

        // Routes where bottom navigation should be visible
        val mainRoutes = listOf(
            Screen.Home.route,
            Screen.Goals.route,
            Screen.HabitTracker.route,
            Screen.Journal.route,
            Screen.Profile.route
        )

        val showBottomNav = currentRoute in mainRoutes

//        Scaffold(
//            bottomBar = {
//
//            },
//            containerColor =  MaterialTheme.colorScheme.surface,
//        ) { innerPadding ->


        Column(modifier = Modifier.fillMaxSize()) {


        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.weight(1f)
        ) {
            // Home Screen (Dashboard)
            composable(Screen.Home.route) {
                if (isForceUpdateEnabled) {
                    Text(text = "Firebase Remote Config\n\nisForceUpdateEnabled: $isForceUpdateEnabled")
                } else {
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
                        goToAiGeneration = {
                            navController.navigate(Screen.AiGoalGeneration.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }

            // Goals Screen (Goal List)
            composable(Screen.Goals.route) {
                GoalsScreen(
                    viewModel = viewModel,
                    onGoalClick = { goal ->
                        navController.navigate("goal_detail/${goal.id}")
                    },
                    onAddGoalClick = {
                        navController.navigate(Screen.AddGoal.route)
                    },
                    onAiGenerateClick = {
                        navController.navigate(Screen.AiGoalGeneration.route) {
                            launchSingleTop = true
                        }
                    },
                    onTemplatesClick = {
                        navController.navigate(Screen.Templates.route) {
                            launchSingleTop = true
                        }
                    },
                    goToAnalytics = {
                        navController.navigate(Screen.Analytics.route)
                    }
                )
            }

            // Habits Screen (via bottom nav)
            composable(Screen.HabitTracker.route) {
                HabitTrackerScreen(
                    onNavigateBack = { navController.popBackStack() },
                    isFromBottomNav = true
                )
            }

            // Journal Screen (via bottom nav)
            composable(Screen.Journal.route) {
                JournalScreen(
                    onNavigateBack = { navController.popBackStack() },
                    isFromBottomNav = true
                )
            }

            // Profile Screen (NEW)
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToAchievements = {
                        navController.navigate(Screen.Achievements.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToReviews = {
                        navController.navigate(Screen.Reviews.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToLifeBalance = {
                        navController.navigate(Screen.LifeBalance.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToReminders = {
                        navController.navigate(Screen.Reminders.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToBackup = {
                        navController.navigate(Screen.BackupSettings.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToAICoach = {
                        navController.navigate(Screen.AIChat.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToOnboarding = {
                        navController.navigate(Screen.Onboarding.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSignIn = {
                        navController.navigate("sign_in") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Goal Detail Screen
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
                    onViewDependencyGraph = { id ->
                        navController.navigate("dependency_graph/$id") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToGoal = { id ->
                        navController.navigate("goal_detail/$id") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Add Goal Screen
            composable(Screen.AddGoal.route) {
                AddGoalScreen(
                    viewModel = viewModel,
                    onGoalSaved = { navController.popBackStack() },
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Onboarding Screen
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onOnboardingComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }

            // Sign In Screen
            composable("sign_in") {
                SignInScreen(
                    onSignInSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo("sign_in") { inclusive = true }
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Analytics Dashboard
            composable(Screen.Analytics.route) {
                AnalyticsDashboard(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() }
                )
            }

            // AI Goal Generation Screen
            composable(Screen.AiGoalGeneration.route) {
                AiGoalGenerationScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onHomeClick = { navController.popBackStack() }
                )
            }

            // Achievements Screen
            composable(Screen.Achievements.route) {
                AchievementsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Dependency Graph Screen
            composable(Screen.DependencyGraph.route) {
                DependencyGraphScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onGoalClick = { goalId ->
                        navController.navigate("goal_detail/$goalId")
                    }
                )
            }

            // Dependency Graph for specific Goal
            composable(
                route = Screen.DependencyGraphForGoal.route,
                arguments = listOf(navArgument("goalId") { type = NavType.StringType })
            ) { backStackEntry ->
                val goalId = backStackEntry.arguments?.getString("goalId") ?: return@composable
                DependencyGraphScreen(
                    focusGoalId = goalId,
                    onNavigateBack = { navController.popBackStack() },
                    onGoalClick = { id ->
                        navController.navigate("goal_detail/$id")
                    }
                )
            }

            // AI Chat Screen
            composable(Screen.AIChat.route) {
                AIChatScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Reviews Screen
            composable(Screen.Reviews.route) {
                ReviewScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Templates Screen
            composable(Screen.Templates.route) {
                TemplatePickerScreen(
                    onBackClick = { navController.popBackStack() },
                    onTemplateSelected = { template ->
                        navController.navigate("add_goal_from_template/${template.id}") {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Add Goal from Template
            composable(
                route = Screen.AddGoalFromTemplate.route,
                arguments = listOf(navArgument("templateId") { type = NavType.StringType })
            ) { backStackEntry ->
                val templateId =
                    backStackEntry.arguments?.getString("templateId") ?: return@composable
                AddGoalFromTemplateScreen(
                    templateId = templateId,
                    viewModel = viewModel,
                    onGoalSaved = {
                        navController.navigate(Screen.Goals.route) {
                            popUpTo(Screen.Templates.route) { inclusive = true }
                        }
                    },
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Reminder Settings Screen
            composable(Screen.Reminders.route) {
                ReminderSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Life Balance Screen
            composable(Screen.LifeBalance.route) {
                LifeBalanceScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onCreateGoal = { area ->
                        navController.navigate(Screen.AddGoal.route)
                    },
                    onCreateHabit = { area ->
                        navController.navigate(Screen.HabitTracker.route)
                    }
                )
            }

            // Backup Settings Screen
            composable(Screen.BackupSettings.route) {
                BackupSettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Edit Goal Screen
            composable(
                route = Screen.EditGoal.route,
                arguments = listOf(navArgument("goalId") { type = NavType.StringType })
            ) { backStackEntry ->
                val goalId = backStackEntry.arguments?.getString("goalId") ?: return@composable
                // TODO: Implement EditGoalScreen
            }
        }

        BottomNavigationBar(
            navController = navController,
            isVisible = showBottomNav
        )
    }
    }
}
