package az.tribe.lifeplanner

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import az.tribe.lifeplanner.core.FeatureFlags
import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.service.ForceUpdateChecker
import az.tribe.lifeplanner.domain.service.UpdateMode
import az.tribe.lifeplanner.domain.service.UpdateState
import az.tribe.lifeplanner.ui.ForceUpdateScreen
import az.tribe.lifeplanner.ui.goal.GoalViewModel
import az.tribe.lifeplanner.ui.components.BottomNavigationBar
import az.tribe.lifeplanner.ui.components.CelebrationOverlay
import az.tribe.lifeplanner.ui.components.CelebrationType
import az.tribe.lifeplanner.ui.components.NavContextAction
import az.tribe.lifeplanner.ui.gamification.GamificationEvent
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
import az.tribe.lifeplanner.ui.navigation.Screen
import az.tribe.lifeplanner.ui.theme.LifePlannerTheme
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import az.tribe.lifeplanner.util.InAppUpdateEffect
import az.tribe.lifeplanner.util.NetworkConnectivityObserver
import az.tribe.lifeplanner.widget.WidgetDashboardData
import az.tribe.lifeplanner.widget.WidgetDataSyncService
import az.tribe.lifeplanner.widget.WidgetHabitData
import co.touchlab.kermit.Logger
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Brain
import com.adamglin.phosphoricons.regular.Flag
import com.adamglin.phosphoricons.regular.MagnifyingGlass
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.Star
import com.mmk.kmpnotifier.notification.NotifierManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock

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

        val builtinCoachFetcher: az.tribe.lifeplanner.data.network.BuiltinCoachFetcher = koinInject()
        val systemPromptFetcher: az.tribe.lifeplanner.data.network.SystemPromptFetcher = koinInject()
        LaunchedEffect(Unit) {
            builtinCoachFetcher.fetch()
            systemPromptFetcher.fetch()
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
                val gamificationRepo: GamificationRepository =
                    org.koin.mp.KoinPlatform.getKoin().get()

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

                    else -> { /* no-op for other events */
                    }
                }
            }
        }

        // Celebrate when all Getting Started objectives are completed
        val objectiveViewModel: az.tribe.lifeplanner.ui.objectives.BeginnerObjectiveViewModel =
            koinViewModel()
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
        val mainRoutes = buildList {
            add(Screen.Home.route)
            add(Screen.Journal.route)
            if (FeatureFlags.ABILITIES_ENABLED) add(Screen.Abilities.route)
            add(Screen.Profile.route)
        }

        // Tab index for directional slide transitions between bottom nav tabs
        val tabIndex = if (FeatureFlags.ABILITIES_ENABLED) {
            mapOf(
                Screen.Home.route to 0,
                Screen.Journal.route to 1,
                Screen.Abilities.route to 2,
                Screen.Profile.route to 3
            )
        } else {
            mapOf(
                Screen.Home.route to 0,
                Screen.Journal.route to 1,
                Screen.Profile.route to 2
            )
        }
        // Slide offset = 25% of width for a subtle directional hint
        val slideOffset: (Int) -> Int = { fullWidth -> fullWidth / 4 }

        val showBottomNav = currentRoute in mainRoutes

        // Track which tab is selected inside the Hub screen (Journal screen)
        var hubSelectedTab by remember { mutableStateOf(0) }

        // Contextual circle button action — changes per screen and hub tab
        val navContextAction: NavContextAction? = when (currentRoute) {
            Screen.Home.route -> NavContextAction(
                icon = PhosphorIcons.Regular.MagnifyingGlass,
                contentDescription = "Search"
            ) {
                navController.navigate(Screen.Search.route) { launchSingleTop = true }
            }
            Screen.Journal.route -> when (hubSelectedTab) {
                1 -> NavContextAction(
                    icon = PhosphorIcons.Regular.Flag,
                    contentDescription = "Add Goal"
                ) {
                    navController.navigate(Screen.GoalWizard.route) { launchSingleTop = true }
                }
                2 -> NavContextAction(
                    icon = PhosphorIcons.Regular.Plus,
                    contentDescription = "New Habit"
                ) {
                    navController.navigate(Screen.AddHabit.route) { launchSingleTop = true }
                }
                3 -> if (FeatureFlags.ABILITIES_ENABLED) NavContextAction(
                    icon = PhosphorIcons.Regular.Star,
                    contentDescription = "Add Ability"
                ) {
                    navController.navigate(Screen.CreateAbility.route) { launchSingleTop = true }
                } else null
                else -> NavContextAction(
                    icon = PhosphorIcons.Regular.PencilSimple,
                    contentDescription = "Write"
                ) {
                    navController.navigate("journal_wizard") { launchSingleTop = true }
                }
            }
            Screen.Profile.route -> NavContextAction(
                icon = PhosphorIcons.Regular.Brain,
                contentDescription = "Coach"
            ) {
                navController.navigate(Screen.AIChat.route) { launchSingleTop = true }
            }
            else -> null
        }

        // Use Box layout to prevent jumping when bottom nav hides
        // NavHost fills entire space, bottom bar overlays at bottom
        val focusManager = LocalFocusManager.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
            ) {
                appNavHome(
                    navController = navController,
                    viewModel = viewModel,
                    tabIndex = tabIndex,
                    slideOffset = slideOffset,
                    softUpdateDismissed = softUpdateDismissed,
                    updateState = updateState,
                    onHubTabSelected = { hubSelectedTab = it },
                    onSoftUpdateDismissed = { softUpdateDismissed = false }
                )
                appNavJournal(
                    navController = navController,
                    tabIndex = tabIndex,
                    slideOffset = slideOffset,
                    hubSelectedTab = hubSelectedTab,
                    onTabSelected = { hubSelectedTab = it }
                )
                appNavProfile(
                    navController = navController,
                    tabIndex = tabIndex,
                    slideOffset = slideOffset
                )
                appNavAbilities(
                    navController = navController,
                    tabIndex = tabIndex,
                    slideOffset = slideOffset
                )
                appNavGoals(
                    navController = navController,
                    viewModel = viewModel,
                    onHubTabSelected = { hubSelectedTab = it }
                )
                appNavHabits(navController = navController)
                appNavCoach(navController = navController)
                appNavAuth(navController = navController)
            }

            // Bottom nav at the bottom, overlays content
            Box(
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomNavigationBar(
                    navController = navController,
                    isVisible = showBottomNav,
                    contextAction = navContextAction
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
