package az.tribe.lifeplanner

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import az.tribe.lifeplanner.ui.AchievementsScreen
import az.tribe.lifeplanner.ui.AddGoalFromTemplateScreen
import az.tribe.lifeplanner.ui.AddGoalScreen
import az.tribe.lifeplanner.ui.AddHabitScreen
import az.tribe.lifeplanner.ui.SmartGoalGeneratorScreen
import az.tribe.lifeplanner.ui.EditGoalScreen
import az.tribe.lifeplanner.ui.AIChatScreen
import az.tribe.lifeplanner.ui.AiGoalGenerationScreen
import az.tribe.lifeplanner.ui.AnalyticsDashboard
import az.tribe.lifeplanner.ui.DependencyGraphScreen
import az.tribe.lifeplanner.ui.GoalDetailScreen
import az.tribe.lifeplanner.ui.GoalViewModel
import az.tribe.lifeplanner.ui.GoalsScreen
import az.tribe.lifeplanner.ui.HabitTrackerScreen
import az.tribe.lifeplanner.ui.JournalEntryDetailScreen
import az.tribe.lifeplanner.ui.JournalCreationWizardScreen
import az.tribe.lifeplanner.ui.JournalScreen
import az.tribe.lifeplanner.ui.LifeBalanceScreen
import az.tribe.lifeplanner.ui.BackupSettingsScreen
import az.tribe.lifeplanner.ui.ProfileScreen
import az.tribe.lifeplanner.ui.ReminderSettingsScreen
import az.tribe.lifeplanner.ui.TemplatePickerScreen
import az.tribe.lifeplanner.ui.HomeScreen
import az.tribe.lifeplanner.ui.StoryReaderScreen
import az.tribe.lifeplanner.ui.focus.FocusScreen
import az.tribe.lifeplanner.ui.retrospective.RetrospectiveScreen
import az.tribe.lifeplanner.ui.OnboardingScreen
import az.tribe.lifeplanner.ui.SignInScreen
import az.tribe.lifeplanner.ui.WelcomeScreen
import az.tribe.lifeplanner.ui.onboarding.OnboardingReminderScreen
import az.tribe.lifeplanner.ui.coach.CoachProfileScreen
import az.tribe.lifeplanner.ui.coach.CoachViewModel
import az.tribe.lifeplanner.ui.coach.CreateCoachScreen
import az.tribe.lifeplanner.ui.coach.CreateGroupScreen
import az.tribe.lifeplanner.ui.components.BottomNavigationBar
import az.tribe.lifeplanner.ui.components.CelebrationOverlay
import az.tribe.lifeplanner.ui.components.CelebrationType
import az.tribe.lifeplanner.ui.gamification.GamificationEvent
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.data.analytics.FacebookAnalytics
import az.tribe.lifeplanner.domain.service.ForceUpdateChecker
import az.tribe.lifeplanner.domain.service.UpdateMode
import az.tribe.lifeplanner.domain.service.UpdateState
import az.tribe.lifeplanner.ui.ForceUpdateScreen
import az.tribe.lifeplanner.util.InAppUpdateEffect
import az.tribe.lifeplanner.ui.navigation.Screen
import co.touchlab.kermit.Logger
import org.koin.compose.viewmodel.koinViewModel
import az.tribe.lifeplanner.ui.theme.LifePlannerTheme
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.util.NetworkConnectivityObserver
import az.tribe.lifeplanner.widget.WidgetDataSyncService
import az.tribe.lifeplanner.widget.WidgetDashboardData
import az.tribe.lifeplanner.widget.WidgetHabitData
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import com.mmk.kmpnotifier.notification.NotifierManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.savedstate.read
import org.koin.compose.koinInject

@Composable
@Preview
fun App(
    viewModel: GoalViewModel = koinInject(),
    authViewModel: AuthViewModel = koinInject(),
    promoRoute: String? = null
) {
    LifePlannerTheme {
        var myPushNotificationToken by remember { mutableStateOf("") }

        // Collect onboarding state from ViewModel
        val hasCompletedOnboarding by authViewModel.hasCompletedOnboarding.collectAsState()
        val authState by authViewModel.authState.collectAsState()

        LaunchedEffect(true) {
            Logger.d("App") { "LaunchedEffectApp is called" }
            NotifierManager.addListener(object : NotifierManager.Listener {
                override fun onNewToken(token: String) {
                    myPushNotificationToken = token
                    Logger.d("App") { "onNewToken: $token" }
                }
            })
            myPushNotificationToken = NotifierManager.getPushNotifier().getToken() ?: ""
            Logger.d("App") { "Push notification token retrieved" }
        }

        // Start network connectivity observation
        val connectivityObserver: NetworkConnectivityObserver = koinInject()
        LaunchedEffect(Unit) {
            connectivityObserver.observe().collect { /* keeps StateFlow primed */ }
        }

        // Sync widget data on every app resume (processes pending widget check-ins)
        var resumeCount by remember { mutableIntStateOf(0) }

        // Trigger Supabase sync on app foreground — only for real accounts
        val syncManager: SyncManager = koinInject()
        LaunchedEffect(resumeCount, authState) {
            if (resumeCount > 0 && authState is AuthState.Authenticated) {
                syncManager.performFullSync()
            }
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    resumeCount++
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(resumeCount) {
            try {
                val widgetSync: WidgetDataSyncService = org.koin.mp.KoinPlatform.getKoin().get()
                val habitRepo: HabitRepository = org.koin.mp.KoinPlatform.getKoin().get()
                val goalRepo: GoalRepository = org.koin.mp.KoinPlatform.getKoin().get()
                val gamificationRepo: GamificationRepository = org.koin.mp.KoinPlatform.getKoin().get()

                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

                // Force SQLDelight to re-read DB (picks up widget check-ins made via direct SQL)
                habitRepo.invalidateCache()

                // Process any pending check-ins from widget before syncing
                val pendingCheckIns = widgetSync.getPendingCheckIns()
                for (habitId in pendingCheckIns) {
                    try {
                        habitRepo.checkIn(habitId, today)
                    } catch (_: Exception) {
                        // Already checked in or invalid — skip
                    }
                    widgetSync.removePendingCheckIn(habitId)
                }

                val habitsWithStatus = habitRepo.getHabitsWithTodayStatus(today)
                val activeGoals = goalRepo.getActiveGoals()
                val progress = gamificationRepo.getUserProgress().firstOrNull()

                val dashboardData = WidgetDashboardData(
                    currentStreak = progress?.currentStreak ?: 0,
                    totalXp = progress?.totalXp ?: 0,
                    currentLevel = progress?.currentLevel ?: 1,
                    activeGoals = activeGoals.size,
                    habitsTotal = habitsWithStatus.size,
                    habitsDoneToday = habitsWithStatus.count { it.second },
                    lastUpdated = today.toString()
                )

                val widgetHabits = habitsWithStatus.map { (habit, isDone) ->
                    WidgetHabitData(
                        id = habit.id,
                        title = habit.title,
                        isCompletedToday = isDone,
                        currentStreak = habit.currentStreak,
                        category = habit.category.name
                    )
                }

                widgetSync.syncWidgetData(dashboardData, widgetHabits)
            } catch (e: Exception) {
                Logger.e("App", e) { "Widget sync failed: ${e.message}" }
            }
        }

        // Force update check via PostHog feature flag
        var updateState by remember { mutableStateOf<UpdateState>(UpdateState.UpToDate) }
        var softUpdateDismissed by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            updateState = ForceUpdateChecker.check()
        }

        // Block app if force update required
        when (val state = updateState) {
            is UpdateState.UpdateRequired -> {
                if (state.mode == UpdateMode.FORCE || !softUpdateDismissed) {
                    Analytics.forceUpdateShown(
                        state.mode.name.lowercase(),
                        BuildKonfig.APP_VERSION,
                        state.minVersion
                    )
                    ForceUpdateScreen(
                        mode = state.mode,
                        storeUrl = state.storeUrl,
                        onDismiss = {
                            Analytics.softUpdateDismissed(BuildKonfig.APP_VERSION)
                            softUpdateDismissed = true
                        }
                    )
                    return@LifePlannerTheme
                }
            }
            else -> {}
        }

        // Trigger Play Store in-app update when update is available
        InAppUpdateEffect(enabled = updateState is UpdateState.UpdateRequired)

        // Global celebration overlay state
        val gamificationViewModel: GamificationViewModel = koinViewModel()
        var showGlobalCelebration by remember { mutableStateOf(false) }
        var globalCelebrationType by remember { mutableStateOf(CelebrationType.BADGE_UNLOCKED) }
        var globalCelebrationMessage by remember { mutableStateOf("") }

        // Collect gamification events for global celebrations
        LaunchedEffect(Unit) {
            gamificationViewModel.gamificationEvents.collect { event ->
                when (event) {
                    is GamificationEvent.BadgeEarned -> {
                        globalCelebrationType = CelebrationType.BADGE_UNLOCKED
                        globalCelebrationMessage = "Badge Unlocked: ${event.badge.type.displayName}"
                        showGlobalCelebration = true
                    }
                    is GamificationEvent.LevelUp -> {
                        globalCelebrationType = CelebrationType.LEVEL_UP
                        globalCelebrationMessage = "Level ${event.newLevel}: ${event.title}"
                        showGlobalCelebration = true
                    }
                    is GamificationEvent.StreakUpdated -> {
                        val milestoneStreaks = listOf(7, 14, 30, 50, 100)
                        if (event.newStreak in milestoneStreaks) {
                            globalCelebrationType = CelebrationType.STREAK_MILESTONE
                            globalCelebrationMessage = "${event.newStreak}-Day Streak!"
                            showGlobalCelebration = true
                        }
                    }
                    else -> { /* no-op for other events */ }
                }
            }
        }

        // Celebrate when all Getting Started objectives are completed
        val objectiveViewModel: az.tribe.lifeplanner.ui.objectives.BeginnerObjectiveViewModel = koinViewModel()
        LaunchedEffect(Unit) {
            objectiveViewModel.celebrationEvent.collect {
                globalCelebrationType = CelebrationType.BADGE_UNLOCKED
                globalCelebrationMessage = "Explorer Badge Earned!\nAll objectives complete!"
                showGlobalCelebration = true
            }
        }

        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // Track app opened once per composition
        LaunchedEffect(Unit) { Analytics.appOpened() }

        // PostHog screen tracking — fires on every route change
        LaunchedEffect(currentRoute) {
            currentRoute?.let { Analytics.screenViewed(it) }
        }

        // Determine start destination based on auth state
        val startDestination = when (authState) {
            is AuthState.Loading -> return@LifePlannerTheme // Still loading
            is AuthState.Authenticated, is AuthState.Guest -> Screen.Home.route
            else -> Screen.Welcome.route
        }

        // React to auth state changes — navigate to the right screen
        LaunchedEffect(authState) {
            when {
                // Authenticated or Guest → ensure on Home
                authState is AuthState.Authenticated || authState is AuthState.Guest -> {
                    // Sync is now triggered from AuthViewModel after login completes,
                    // so we don't trigger it here to avoid racing with DB operations.
                    val current = navController.currentDestination?.route
                    // Only auto-navigate from sign_in; WelcomeScreen handles its own
                    // splash timing and calls onComplete() when ready
                    if (current == "sign_in") {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
                // Signed out → reset cached data and go to Welcome
                authState is AuthState.Unauthenticated -> {
                    gamificationViewModel.resetState()
                    val current = navController.currentDestination?.route
                    if (current != Screen.Welcome.route && current != "sign_in") {
                        navController.navigate(Screen.Welcome.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
                // Verification pending (recovered from app relaunch) → go to sign_in
                authState is AuthState.EmailVerificationPending -> {
                    val current = navController.currentDestination?.route
                    if (current != "sign_in" && current != Screen.Welcome.route) {
                        navController.navigate("sign_in") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
        }

        // Handle marketing deep link (e.g. lifeplanner://promo/chat)
        LaunchedEffect(promoRoute, authState) {
            if (promoRoute != null && (authState is AuthState.Authenticated || authState is AuthState.Guest)) {
                navController.navigate(promoRoute) {
                    launchSingleTop = true
                }
            }
        }

        // Routes where bottom navigation should be visible
        val mainRoutes = listOf(
            Screen.Home.route,
            Screen.Journal.route,
            Screen.Profile.route
        )

        // Tab index for directional slide transitions between bottom nav tabs
        val tabIndex = mapOf(
            Screen.Home.route to 0,
            Screen.Journal.route to 1,
            Screen.Profile.route to 2
        )
        // Slide offset = 25% of width for a subtle directional hint
        val slideOffset: (Int) -> Int = { fullWidth -> fullWidth / 4 }

        val showBottomNav = currentRoute in mainRoutes

        // Use Box layout to prevent jumping when bottom nav hides
        // NavHost fills entire space, bottom bar overlays at bottom
        val focusManager = LocalFocusManager.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            // Home Screen (Dashboard)
            composable(
                Screen.Home.route,
                enterTransition = {
                    val fromIndex = tabIndex[initialState.destination.route]
                    val toIndex = tabIndex[targetState.destination.route]
                    if (fromIndex != null && toIndex != null) {
                        slideInHorizontally(tween(300)) { w -> if (fromIndex > toIndex) -slideOffset(w) else slideOffset(w) } +
                            fadeIn(tween(300))
                    } else fadeIn(tween(300))
                },
                exitTransition = {
                    val fromIndex = tabIndex[initialState.destination.route]
                    val toIndex = tabIndex[targetState.destination.route]
                    if (fromIndex != null && toIndex != null) {
                        slideOutHorizontally(tween(300)) { w -> if (fromIndex < toIndex) -slideOffset(w) else slideOffset(w) } +
                            fadeOut(tween(300))
                    } else fadeOut(tween(300))
                },
                popEnterTransition = {
                    val fromIndex = tabIndex[initialState.destination.route]
                    val toIndex = tabIndex[targetState.destination.route]
                    if (fromIndex != null && toIndex != null) {
                        slideInHorizontally(tween(300)) { w -> if (fromIndex > toIndex) -slideOffset(w) else slideOffset(w) } +
                            fadeIn(tween(300))
                    } else fadeIn(tween(300))
                },
                popExitTransition = {
                    val fromIndex = tabIndex[initialState.destination.route]
                    val toIndex = tabIndex[targetState.destination.route]
                    if (fromIndex != null && toIndex != null) {
                        slideOutHorizontally(tween(300)) { w -> if (fromIndex < toIndex) -slideOffset(w) else slideOffset(w) } +
                            fadeOut(tween(300))
                    } else fadeOut(tween(300))
                }
            ) {
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
                        },
                        onNavigateToHabits = {
                            navController.navigate(Screen.HabitTracker.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToAddHabit = {
                            navController.navigate(Screen.AddHabit.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToJournal = {
                            navController.navigate(Screen.Journal.route) {
                                popUpTo(navController.graph.startDestinationRoute!!) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToGoals = {
                            navController.navigate(Screen.Goals.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToAchievements = {
                            navController.navigate(Screen.Achievements.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToFocus = {
                            navController.navigate("focus_setup") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToProfile = {
                            navController.navigate(Screen.Profile.route) {
                                popUpTo(navController.graph.startDestinationRoute!!) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToRetrospective = {
                            navController.navigate(Screen.Retrospective.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToChat = {
                            navController.navigate(Screen.AIChat.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToReminders = {
                            navController.navigate(Screen.Reminders.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToLifeBalance = {
                            navController.navigate(Screen.LifeBalance.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToHealth = {
                            navController.navigate(Screen.Health.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToStoryReader = {
                            navController.navigate(Screen.StoryReader.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToTemplates = {
                            navController.navigate(Screen.Templates.route) {
                                launchSingleTop = true
                            }
                        },
                        onContinueChat = { coachId ->
                            navController.navigate("ai_chat/$coachId") {
                                launchSingleTop = true
                            }
                        },
                        onStartFocusForMilestone = { goalId, milestoneId ->
                            navController.navigate("focus_setup?goalId=$goalId&milestoneId=$milestoneId") {
                                launchSingleTop = true
                            }
                        },
                        onNavigateToJournalEntry = { entryId ->
                            navController.navigate("journal_entry_detail/$entryId") {
                                launchSingleTop = true
                            }
                        },
                        showUpdateReminder = softUpdateDismissed && updateState is UpdateState.UpdateRequired,
                        onUpdateClick = { softUpdateDismissed = false } // Re-show update screen
                    )
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
                    onBack = { navController.popBackStack() }
                )
            }

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

            composable(Screen.AddHabit.route) {
                AddHabitScreen(
                    onHabitSaved = { navController.popBackStack() },
                    onBackClick = { navController.popBackStack() }
                )
            }

            // Journal Screen (via bottom nav or direct navigation)
            composable(
                Screen.Journal.route,
                enterTransition = {
                    val fromIndex = tabIndex[initialState.destination.route]
                    val toIndex = tabIndex[targetState.destination.route]
                    if (fromIndex != null && toIndex != null) {
                        slideInHorizontally(tween(300)) { w -> if (fromIndex > toIndex) -slideOffset(w) else slideOffset(w) } +
                            fadeIn(tween(300))
                    } else fadeIn(tween(300))
                },
                exitTransition = {
                    val fromIndex = tabIndex[initialState.destination.route]
                    val toIndex = tabIndex[targetState.destination.route]
                    if (fromIndex != null && toIndex != null) {
                        slideOutHorizontally(tween(300)) { w -> if (fromIndex < toIndex) -slideOffset(w) else slideOffset(w) } +
                            fadeOut(tween(300))
                    } else fadeOut(tween(300))
                },
                popEnterTransition = {
                    val fromIndex = tabIndex[initialState.destination.route]
                    val toIndex = tabIndex[targetState.destination.route]
                    if (fromIndex != null && toIndex != null) {
                        slideInHorizontally(tween(300)) { w -> if (fromIndex > toIndex) -slideOffset(w) else slideOffset(w) } +
                            fadeIn(tween(300))
                    } else fadeIn(tween(300))
                },
                popExitTransition = {
                    val fromIndex = tabIndex[initialState.destination.route]
                    val toIndex = tabIndex[targetState.destination.route]
                    if (fromIndex != null && toIndex != null) {
                        slideOutHorizontally(tween(300)) { w -> if (fromIndex < toIndex) -slideOffset(w) else slideOffset(w) } +
                            fadeOut(tween(300))
                    } else fadeOut(tween(300))
                }
            ) {
                // Show back button when navigated from within the app (not bottom nav tab switch)
                val previousRoute = navController.previousBackStackEntry?.destination?.route
                val isBottomNavEntry = previousRoute == null || previousRoute == Screen.Home.route
                JournalScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onEntryClick = { entryId ->
                        navController.navigate("journal_entry_detail/$entryId") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToWizard = {
                        navController.navigate("journal_wizard") {
                            launchSingleTop = true
                        }
                    },
                    isFromBottomNav = isBottomNavEntry
                )
            }

            // Journal Creation Wizard
            composable(
                route = Screen.JournalWizard.route,
                arguments = listOf(navArgument("goalId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val goalId = backStackEntry.arguments?.read { getStringOrNull("goalId") }
                JournalCreationWizardScreen(
                    onNavigateBack = { navController.popBackStack() },
                    preSelectedGoalId = goalId
                )
            }

            // Journal Entry Detail Screen
            composable(
                route = Screen.JournalEntryDetail.route,
                arguments = listOf(navArgument("entryId") { type = NavType.StringType })
            ) { backStackEntry ->
                val entryId = backStackEntry.arguments?.read { getStringOrNull("entryId") } ?: return@composable
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

            // Profile Screen
            composable(
                Screen.Profile.route,
                enterTransition = {
                    val fromIndex = tabIndex[initialState.destination.route]
                    val toIndex = tabIndex[targetState.destination.route]
                    if (fromIndex != null && toIndex != null) {
                        slideInHorizontally(tween(300)) { w -> if (fromIndex > toIndex) -slideOffset(w) else slideOffset(w) } +
                            fadeIn(tween(300))
                    } else fadeIn(tween(300))
                },
                exitTransition = {
                    val fromIndex = tabIndex[initialState.destination.route]
                    val toIndex = tabIndex[targetState.destination.route]
                    if (fromIndex != null && toIndex != null) {
                        slideOutHorizontally(tween(300)) { w -> if (fromIndex < toIndex) -slideOffset(w) else slideOffset(w) } +
                            fadeOut(tween(300))
                    } else fadeOut(tween(300))
                },
                popEnterTransition = {
                    val fromIndex = tabIndex[initialState.destination.route]
                    val toIndex = tabIndex[targetState.destination.route]
                    if (fromIndex != null && toIndex != null) {
                        slideInHorizontally(tween(300)) { w -> if (fromIndex > toIndex) -slideOffset(w) else slideOffset(w) } +
                            fadeIn(tween(300))
                    } else fadeIn(tween(300))
                },
                popExitTransition = {
                    val fromIndex = tabIndex[initialState.destination.route]
                    val toIndex = tabIndex[targetState.destination.route]
                    if (fromIndex != null && toIndex != null) {
                        slideOutHorizontally(tween(300)) { w -> if (fromIndex < toIndex) -slideOffset(w) else slideOffset(w) } +
                            fadeOut(tween(300))
                    } else fadeOut(tween(300))
                }
            ) {
                ProfileScreen(
                    onNavigateToAchievements = {
                        navController.navigate(Screen.Achievements.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToLifeBalance = {
                        navController.navigate(Screen.LifeBalance.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToHealth = {
                        navController.navigate(Screen.Health.route) {
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
                    onNavigateToRetrospective = {
                        navController.navigate(Screen.Retrospective.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToAICoach = {
                        navController.navigate(Screen.AIChat.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSignIn = {
                        navController.navigate("sign_in") {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToFeedback = {
                        navController.navigate(Screen.Feedback.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Feedback Screen
            composable(Screen.Feedback.route) {
                az.tribe.lifeplanner.ui.feedback.FeedbackScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Goal Detail Screen
            composable(
                route = "goal_detail/{goalId}",
                arguments = listOf(navArgument("goalId") { type = NavType.StringType })
            ) { backStackEntry ->
                val goalId = backStackEntry.arguments?.read { getStringOrNull("goalId") } ?: return@composable
                LaunchedEffect(goalId) { FacebookAnalytics.logViewContent(goalId, "goal") }
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
                    },
                    onNavigateToJournal = { entryId ->
                        navController.navigate("journal_entry_detail/$entryId") {
                            launchSingleTop = true
                        }
                    },
                    onReflectOnGoal = { id ->
                        navController.navigate("journal_wizard?goalId=$id") {
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

            // Welcome Screen (video background + auth buttons)
            composable(Screen.Welcome.route) {
                WelcomeScreen(
                    onComplete = {
                        navController.navigate(Screen.OnboardingReminders.route) {
                            popUpTo(Screen.Welcome.route) { inclusive = true }
                        }
                    }
                )
            }

            // Onboarding Reminder Setup (shown once after welcome)
            composable(Screen.OnboardingReminders.route) {
                OnboardingReminderScreen(
                    onComplete = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.OnboardingReminders.route) { inclusive = true }
                        }
                    },
                    onSkip = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.OnboardingReminders.route) { inclusive = true }
                        }
                    }
                )
            }

            // Onboarding Screen (kept for existing users)
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onOnboardingComplete = {
                        FacebookAnalytics.logCompleteTutorial()
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
                SmartGoalGeneratorScreen(
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onComplete = {
                        navController.navigate(Screen.Goals.route) {
                            popUpTo(Screen.AiGoalGeneration.route) { inclusive = true }
                        }
                    }
                )
            }

            // Achievements Screen
            composable(Screen.Achievements.route) {
                AchievementsScreen(
                    onNavigateBack = { navController.popBackStack() }
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
                val goalId = backStackEntry.arguments?.read { getStringOrNull("goalId") }?.takeIf { it.isNotEmpty() }
                val milestoneId = backStackEntry.arguments?.read { getStringOrNull("milestoneId") }?.takeIf { it.isNotEmpty() }
                FocusScreen(
                    onNavigateBack = { navController.popBackStack() },
                    goalId = goalId,
                    milestoneId = milestoneId
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
                val goalId = backStackEntry.arguments?.read { getStringOrNull("goalId") } ?: return@composable
                DependencyGraphScreen(
                    focusGoalId = goalId,
                    onNavigateBack = { navController.popBackStack() },
                    onGoalClick = { id ->
                        navController.navigate("goal_detail/$id")
                    }
                )
            }

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
                val profileCoachId = backStackEntry.arguments?.read { getStringOrNull("coachId") } ?: return@composable
                CoachProfileScreen(
                    coachId = profileCoachId,
                    onNavigateBack = { navController.popBackStack() }
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
                val coachId = backStackEntry.arguments?.read { getStringOrNull("coachId") } ?: return@composable
                val coachViewModel: CoachViewModel = koinInject()
                val coachToEdit = coachViewModel.getCoachById(coachId)
                CreateCoachScreen(
                    coachToEdit = coachToEdit,
                    onNavigateBack = { navController.popBackStack() },
                    onCoachSaved = { coach ->
                        coachViewModel.updateCoach(coach)
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
                val groupId = backStackEntry.arguments?.read { getStringOrNull("groupId") } ?: return@composable
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
                    }
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
                    backStackEntry.arguments?.read { getStringOrNull("templateId") } ?: return@composable
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
                    onCreateHabit = { area ->
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

            // Edit Goal Screen
            composable(
                route = Screen.EditGoal.route,
                arguments = listOf(navArgument("goalId") { type = NavType.StringType })
            ) { backStackEntry ->
                val goalId = backStackEntry.arguments?.read { getStringOrNull("goalId") } ?: return@composable
                EditGoalScreen(
                    goalId = goalId,
                    viewModel = viewModel,
                    onGoalSaved = { navController.popBackStack() },
                    onBackClick = { navController.popBackStack() }
                )
            }
        }

        // Bottom nav at the bottom, overlays content
        Box(
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomNavigationBar(
                navController = navController,
                isVisible = showBottomNav
            )
        }

        // Global celebration overlay (on top of everything)
        CelebrationOverlay(
            type = globalCelebrationType,
            isVisible = showGlobalCelebration,
            message = globalCelebrationMessage,
            onDismiss = { showGlobalCelebration = false }
        )
    }
    }
}
