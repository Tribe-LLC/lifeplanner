package az.tribe.lifeplanner

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
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
import az.tribe.lifeplanner.ui.onboarding.CoachOnboardingViewModel
import az.tribe.lifeplanner.ui.onboarding.IntroFlow
import az.tribe.lifeplanner.ui.components.BottomNavigationBar
import az.tribe.lifeplanner.ui.components.NavigationRailBar
import az.tribe.lifeplanner.ui.components.CelebrationOverlay
import az.tribe.lifeplanner.domain.model.TodayWeather
import az.tribe.lifeplanner.ui.foryou.WeatherDetailFullScreen
import az.tribe.lifeplanner.ui.components.CelebrationType
import az.tribe.lifeplanner.ui.components.NavContextAction
import az.tribe.lifeplanner.ui.gamification.GamificationEvent
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
import az.tribe.lifeplanner.ui.navigation.Screen
import az.tribe.lifeplanner.ui.theme.LifePlannerTheme
import az.tribe.lifeplanner.ui.theme.ThemeController
import az.tribe.lifeplanner.ui.theme.ThemeMode
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.adamglin.phosphoricons.regular.Sparkle
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
    // D3 audit G2: appearance follows a persisted preference (defaults to System), not a hardcoded dark.
    val themeController: ThemeController = koinInject()
    val themeMode by themeController.mode.collectAsState()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    LifePlannerTheme(darkTheme = darkTheme) {
        var myPushNotificationToken by remember { mutableStateOf("") }

        val hasCompletedOnboarding by authViewModel.hasCompletedOnboarding.collectAsState()
        val authState by authViewModel.authState.collectAsState()
        val settings: com.russhwolf.settings.Settings = koinInject()

        LaunchedEffect(true) {
            Logger.d("App") { "LaunchedEffectApp is called" }
            NotifierManager.addListener(object : NotifierManager.Listener {
                override fun onNewToken(token: String) {
                    myPushNotificationToken = token
                    Logger.d("App") { "onNewToken: $token" }
                }

                override fun onNotificationClicked(data: Map<String, Any?>) {
                    super.onNotificationClicked(data)
                    Logger.d("App") { "Notification clicked with data: $data" }
                    val goalId = data["linked_goal_id"] as? String
                    val open = data["open"] as? String
                    if (!open.isNullOrBlank()) {
                        az.tribe.lifeplanner.data.habits.NudgePlan.opened(open)
                    } else if (!goalId.isNullOrBlank()) {
                        az.tribe.lifeplanner.util.DeepLinkNavigator.navigate("goal_detail/$goalId")
                    }
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

        // One-time upgrade of pre-track-mode habits ("Drink 8 glasses" -> count habit).
        val backfillHabitTargets: az.tribe.lifeplanner.usecases.habit.BackfillHabitTargetsUseCase = koinInject()
        LaunchedEffect(Unit) {
            runCatching { backfillHabitTargets() }
        }

        val builtinCoachFetcher: az.tribe.lifeplanner.data.network.BuiltinCoachFetcher = koinInject()
        val personaApiFetcher: az.tribe.lifeplanner.data.network.PersonaApiFetcher = koinInject()
        val systemPromptFetcher: az.tribe.lifeplanner.data.network.SystemPromptFetcher = koinInject()
        val knowledgeFetcher: az.tribe.lifeplanner.data.network.KnowledgeFetcher = koinInject()
        LaunchedEffect(Unit) {
            // Learn content first: the cached library is a local read, and having it in place before
            // anything renders avoids the map flashing the bundled lessons and then swapping.
            knowledgeFetcher.loadCache()
            builtinCoachFetcher.fetch()
            personaApiFetcher.loadCache()  // instant local load before network
            personaApiFetcher.fetch()       // refresh + persist to local DB and Supabase
            systemPromptFetcher.fetch()
            knowledgeFetcher.fetch()        // refresh lessons + persist to local DB
        }

        // Sync widget data on every app resume (processes pending widget check-ins)
        var resumeCount by remember { mutableIntStateOf(0) }

        // Trigger Supabase sync on app foreground, only for real accounts
        val syncManager: SyncManager = koinInject()
        LaunchedEffect(resumeCount, authState) {
            if (resumeCount > 0 && authState is AuthState.Authenticated) {
                syncManager.performFullSync()
            }
        }

        // Pillar 1: build the "why" layer, then link goals to it, in order so it works on one launch.
        // 1) promote onboarding topValues → LifeValue rows, 2) seed category defaults if still none,
        // 3) auto-link goals to whichever values now exist. All idempotent.
        val promoteTopValues: az.tribe.lifeplanner.usecases.PromoteTopValuesToLifeValuesUseCase = koinInject()
        val seedDefaultValues: az.tribe.lifeplanner.usecases.SeedDefaultLifeValuesUseCase = koinInject()
        val autoLinkGoalValues: az.tribe.lifeplanner.usecases.AutoLinkGoalValuesUseCase = koinInject()
        LaunchedEffect(authState) {
            runCatching { promoteTopValues() }
            runCatching { seedDefaultValues() }
            runCatching { autoLinkGoalValues() }
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
                        // Already checked in or invalid, skip
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

        // Full-screen weather detail, hoisted to the root so it draws above the bottom nav bar.
        var weatherDetail by remember { mutableStateOf<TodayWeather?>(null) }

        // Collect gamification events for global celebrations
        LaunchedEffect(Unit) {
            gamificationViewModel.gamificationEvents.collect { event ->
                when (event) {
                    is GamificationEvent.BadgeEarned -> {
                        globalCelebrationType = CelebrationType.BADGE_UNLOCKED
                        globalCelebrationMessage = buildString {
                            append("Badge Unlocked: ${event.badge.type.displayName}")
                            if (event.alsoEarnedCount > 0) {
                                append(" +${event.alsoEarnedCount} more")
                            }
                        }
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

        // Behavioral tracking, record screen enter/exit on every nav change
        val behaviorTracker: az.tribe.lifeplanner.data.behavior.BehaviorTracker = koinInject()
        LaunchedEffect(currentRoute) {
            behaviorTracker.onScreenChanged(currentRoute)
        }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    behaviorTracker.onAppBackground()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Track app opened once per composition
        LaunchedEffect(Unit) { Analytics.appOpened() }

        // PostHog screen tracking, fires on every route change
        LaunchedEffect(currentRoute) {
            currentRoute?.let { Analytics.screenViewed(it) }
        }

        // v4: its own first run, tabs and navigation. Everything above this line (sync, widgets,
        // fetchers, celebrations, tracking) is shared; everything below is the v3 shell.
        if (FeatureFlags.V4_SHELL) {
            V4AppRoot(
                navController = navController,
                currentRoute = currentRoute,
                authState = authState,
                authViewModel = authViewModel,
                goalViewModel = viewModel,
                promoRoute = promoRoute,
                onSignedOut = { gamificationViewModel.resetState() },
                onOpenWeather = { weatherDetail = it },
                overlays = {
                    CelebrationOverlay(
                        type = globalCelebrationType,
                        isVisible = showGlobalCelebration,
                        message = globalCelebrationMessage,
                        onDismiss = { showGlobalCelebration = false }
                    )
                    weatherDetail?.let { w ->
                        WeatherDetailFullScreen(weather = w, onDismiss = { weatherDetail = null })
                    }
                },
            )
            return@LifePlannerTheme
        }

        // The legacy Home tab is gone; For You is the home.
        val homeRoute = Screen.ForYou.route

        // Registration is the whole gate. First run used to be a chain — the intro, then thirteen
        // screens of coach questions — with the app on the far side of it. That contradicted the
        // promise the intro itself makes ("no heavy setup"), and its final step could not be
        // completed without typing a goal, so a user who did not want to answer was simply stuck.
        //
        // The coach's questions still exist and are still worth answering; they are now offered
        // from inside the app, where the user can see what they are for, instead of standing
        // between them and it.
        val firstRunRoute =
            if (IntroFlow.isComplete(settings)) homeRoute
            else Screen.OnboardingRedesign.route

        // Determine start destination based on auth state
        val startDestination = when (authState) {
            is AuthState.Loading -> return@LifePlannerTheme // Still loading
            is AuthState.Authenticated, is AuthState.Guest -> {
                // Legacy users who finished the old onboarding before coach onboarding existed
                if (hasCompletedOnboarding == true && !CoachOnboardingViewModel.isComplete(settings)) {
                    settings.putBoolean(CoachOnboardingViewModel.COACH_ONBOARDING_KEY, true)
                }
                firstRunRoute
            }
            else -> Screen.CoachOnboarding.route
        }

        // React to auth state changes, navigate to the right screen
        LaunchedEffect(authState) {
            // On iOS the NavHost below composes *after* this effect's first run, so the graph is
            // briefly unset and any navigate() here throws "Navigation graph has not been set" —
            // which aborts the process on startup. Wait for the first back stack entry rather than
            // bailing out, so the signed-out and verification redirects still fire on a cold start.
            navController.currentBackStackEntryFlow.firstOrNull()

            when {
                // Authenticated or Guest → ensure on Home or force coach onboarding
                authState is AuthState.Authenticated || authState is AuthState.Guest -> {
                    // Sync is now triggered from AuthViewModel after login completes,
                    // so we don't trigger it here to avoid racing with DB operations.
                    val current = navController.currentDestination?.route
                    when {
                        // From sign_in, apply legacy upgrade here too, then route correctly
                        current == "sign_in" -> {
                            if (hasCompletedOnboarding == true && !CoachOnboardingViewModel.isComplete(settings)) {
                                settings.putBoolean(CoachOnboardingViewModel.COACH_ONBOARDING_KEY, true)
                            }
                            val next = if (CoachOnboardingViewModel.isComplete(settings)) homeRoute
                                       else firstRunRoute
                            navController.navigate(next) { popUpTo(0) { inclusive = true } }
                        }
                        // On a main app screen but onboarding not done, force it.
                        // NOTE: do NOT run the legacy auto-upgrade here, it fires on every
                        // auth-state refresh (e.g. sync updating lastSyncedAt) and would set
                        // COACH_ONBOARDING_KEY mid-onboarding, causing premature navigation.
                        // NOTE: the intro handoff for users arriving via the auth gate is NOT done
                        // here. The gate lives inside CoachOnboardingScreen and `signInAsGuest()`
                        // resolves in that screen's own LaunchedEffect, so an outer observer races
                        // it and loses. That screen calls `onNeedsIntro` instead.
                        //
                        // The intro route must be excluded here, or this fires while the user is
                        // partway through it (coach onboarding is legitimately incomplete then)
                        // and yanks them straight out of first run.
                        // Only the intro is required now. This used to check the coach flow, which
                        // meant anyone who had not finished thirteen screens of questions was
                        // yanked back into them on every auth change.
                        !IntroFlow.isComplete(settings) && current != null
                                && current != Screen.CoachOnboarding.route
                                && current != Screen.OnboardingRedesign.route -> {
                            navController.navigate(firstRunRoute) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }
                // Signed out → reset cached data and go to Coach Onboarding (shows auth gate)
                authState is AuthState.Unauthenticated -> {
                    gamificationViewModel.resetState()
                    val current = navController.currentDestination?.route
                    if (current != Screen.CoachOnboarding.route && current != "sign_in") {
                        navController.navigate(Screen.CoachOnboarding.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
                // Verification pending (recovered from app relaunch) → go to sign_in
                authState is AuthState.EmailVerificationPending -> {
                    val current = navController.currentDestination?.route
                    if (current != "sign_in" && current != Screen.CoachOnboarding.route) {
                        navController.navigate("sign_in") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
        }

        // Track which tab is selected inside the Hub screen (Journal screen)
        var hubSelectedTab by remember { mutableStateOf(0) }
        // The hub's day lens, mirrored up here so the Write FAB (which lives outside the hub) can
        // file the new entry under the day the user is actually looking at.
        var hubSelectedDate by remember { mutableStateOf<kotlinx.datetime.LocalDate?>(null) }

        // Handle marketing deep link (e.g. lifeplanner://promo/chat)
        LaunchedEffect(promoRoute, authState) {
            if (promoRoute != null && (authState is AuthState.Authenticated || authState is AuthState.Guest)) {
                if (promoRoute == "journal_habits") {
                    hubSelectedTab = 2
                    navController.navigate(Screen.Journal.route) { launchSingleTop = true }
                } else {
                    navController.navigate(promoRoute) { launchSingleTop = true }
                }
            }
        }

        // Handle generic deep link navigation via DeepLinkNavigator
        LaunchedEffect(Unit) {
            az.tribe.lifeplanner.util.DeepLinkNavigator.navEvents.collect { route ->
                if (authState is AuthState.Authenticated || authState is AuthState.Guest) {
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            }
        }

        // 2026-07-21: v2 nav. Three tabs: legacy Home, the Journal hub (Goals/Habits/Journal),
        // legacy Profile.
        // Routes where bottom navigation should be visible
        val mainRoutes = buildList {
            add(homeRoute)
            add(Screen.Journal.route)
            add(Screen.Profile.route)
        }

        // Tab index for directional slide transitions between bottom nav tabs
        val tabIndex = mapOf(
            homeRoute to 0,
            Screen.Journal.route to 1,
            Screen.Profile.route to 2
        )
        // Slide offset = 25% of width for a subtle directional hint
        val slideOffset: (Int) -> Int = { fullWidth -> fullWidth / 4 }

        val showBottomNav = currentRoute in mainRoutes

        // Contextual circle button action, changes per screen and hub tab (v2 behavior).
        val navContextAction: NavContextAction? = when (currentRoute) {
            // Today's FAB is Search: quick find + act from the front door.
            homeRoute -> NavContextAction(
                icon = PhosphorIcons.Regular.MagnifyingGlass,
                contentDescription = "Search",
                onClick = {
                    navController.navigate(Screen.Search.route) { launchSingleTop = true }
                },
            )
            Screen.Journal.route -> when (hubSelectedTab) {
                1 -> NavContextAction(
                    icon = PhosphorIcons.Regular.Flag,
                    contentDescription = "Add Goal"
                ) {
                    navController.navigate(Screen.GoalWizard.route) { launchSingleTop = true }
                }
                2 -> NavContextAction(
                    icon = PhosphorIcons.Regular.Sparkle,
                    contentDescription = "New Habit"
                ) {
                    navController.navigate(Screen.HabitChat.route) { launchSingleTop = true }
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
                    val route = hubSelectedDate?.let { "journal_wizard?date=$it" } ?: "journal_wizard"
                    navController.navigate(route) { launchSingleTop = true }
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

        val focusManager = LocalFocusManager.current
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            val useRail = maxWidth >= 600.dp

            Row(Modifier.fillMaxSize()) {
                if (useRail) {
                    NavigationRailBar(
                        navController = navController,
                        isVisible = showBottomNav,
                        contextAction = navContextAction
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.fillMaxSize(),
                        // Restored from v2.2 (production): a directional horizontal slide keyed on
                        // tab order, uniform tween(300). The redesign had replaced this with a
                        // vertical slide-up, which reads like a modal sheet on every navigation and
                        // loses the left/right sense of place between tabs.
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
                        appNavJournal(
                            navController = navController,
                            tabIndex = tabIndex,
                            slideOffset = slideOffset,
                            hubSelectedTab = hubSelectedTab,
                            onTabSelected = { hubSelectedTab = it },
                            onSelectedDateChanged = { hubSelectedDate = it }
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
                        appNavHabitDetailRedesign(navController = navController)
                        appNavToday(navController = navController)
                        appNavForYou(navController = navController, onOpenWeather = { weatherDetail = it })
                        if (FeatureFlags.PILLAR_POSSIBILITY) appNavPossibilityMode(navController = navController)
                        appNavGoalsRedesign(navController = navController)
                        appNavYouRedesign(navController = navController)
                        appNavOnboardingRedesign(navController = navController, homeRoute = homeRoute)
                        appNavCoach(navController = navController)
                        appNavAuth(navController = navController, homeRoute = homeRoute)
                        appNavDecisions(navController = navController)
                        if (FeatureFlags.PILLAR_CAUSAL) appNavCausal(navController = navController)
                        if (FeatureFlags.PILLAR_BECOMING) appNavBecoming(navController = navController)
                        if (FeatureFlags.PILLAR_WIRING) appNavWiring(navController = navController)
                        appNavKnowledge(navController = navController)
                        appNavWheel(navController = navController)
                    }

                    if (!useRail) {
                        Box(Modifier.align(Alignment.BottomCenter)) {
                            BottomNavigationBar(
                                navController = navController,
                                isVisible = showBottomNav,
                                contextAction = navContextAction
                            )
                        }
                    }

                    CelebrationOverlay(
                        type = globalCelebrationType,
                        isVisible = showGlobalCelebration,
                        message = globalCelebrationMessage,
                        onDismiss = { showGlobalCelebration = false }
                    )

                    // Weather detail covers the whole content column, including the bottom nav bar.
                    weatherDetail?.let { w ->
                        WeatherDetailFullScreen(weather = w, onDismiss = { weatherDetail = null })
                    }
                }
            }
        }
    }
}
