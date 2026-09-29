package az.tribe.lifeplanner

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.savedstate.read
import az.tribe.lifeplanner.core.FeatureFlags
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.model.TodayWeather
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.ui.goal.GoalViewModel
import az.tribe.lifeplanner.ui.navigation.Screen
import az.tribe.lifeplanner.ui.v4.areas.AreaActions
import az.tribe.lifeplanner.ui.v4.areas.V4AreaScreen
import az.tribe.lifeplanner.ui.v4.coach.V4CoachScreen
import az.tribe.lifeplanner.ui.v4.connect.V4ConnectedAppsScreen
import az.tribe.lifeplanner.ui.v4.connect.V4YouScreen
import az.tribe.lifeplanner.ui.v4.firstrun.AreasScreen
import az.tribe.lifeplanner.ui.v4.firstrun.ConnectScreen
import az.tribe.lifeplanner.ui.v4.firstrun.UpdateScreen
import az.tribe.lifeplanner.ui.v4.firstrun.V4FirstRunViewModel
import az.tribe.lifeplanner.ui.v4.firstrun.WelcomeScreen
import az.tribe.lifeplanner.ui.v4.life.V4LifeScreen
import az.tribe.lifeplanner.ui.v4.quickadd.QuickAddSheet
import az.tribe.lifeplanner.ui.v4.shell.V4AddAnythingBar
import az.tribe.lifeplanner.ui.v4.shell.V4BottomBar
import az.tribe.lifeplanner.ui.v4.shell.V4Routes
import az.tribe.lifeplanner.ui.v4.theme.V4
import az.tribe.lifeplanner.ui.v4.theme.V4Theme
import az.tribe.lifeplanner.ui.v4.today.DayItemType
import az.tribe.lifeplanner.ui.v4.today.V4TodayScreen
import az.tribe.lifeplanner.ui.v4.travel.V4TripScreen
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import az.tribe.lifeplanner.ui.viewmodel.signInAsGuest
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Coarse auth phase for routing. Loading is not a phase: the last known one is kept. */
private enum class V4AuthPhase { OUT, IN, VERIFY }

/** Space the tab screens leave at the bottom for the add bar and the tab bar. */
/**
 * The measured height of what sits at the bottom of a tab (the tab bar, plus the add bar where
 * there is one). Measured rather than fixed, because the home indicator area differs by phone and
 * a fixed number left the coach's composer half under the bar on iPhone.
 */
private val LocalTabBarHeight = androidx.compose.runtime.compositionLocalOf { 86.dp }
private val TabInset @Composable get() = PaddingValues(bottom = LocalTabBarHeight.current + 12.dp)
private val CoachInset @Composable get() = PaddingValues(bottom = LocalTabBarHeight.current)

/**
 * The v4 shell: first run, the three tabs, area pages, and every v3 graph behind them. App.kt
 * keeps everything that is not navigation (sync, widgets, fetchers, celebrations) and hands over
 * here when [FeatureFlags.V4_SHELL] is on.
 */
@Composable
internal fun V4AppRoot(
    navController: NavHostController,
    currentRoute: String?,
    authState: AuthState,
    authViewModel: AuthViewModel,
    goalViewModel: GoalViewModel,
    promoRoute: String?,
    onSignedOut: () -> Unit,
    onOpenWeather: (TodayWeather) -> Unit,
    overlays: @Composable BoxScope.() -> Unit,
) {
    val phaseNow = when (authState) {
        is AuthState.Loading -> null
        is AuthState.Authenticated, is AuthState.Guest -> V4AuthPhase.IN
        is AuthState.EmailVerificationPending -> V4AuthPhase.VERIFY
        else -> V4AuthPhase.OUT
    }
    val lastPhase = remember { mutableStateOf<V4AuthPhase?>(null) }
    SideEffect { if (phaseNow != null) lastPhase.value = phaseNow }
    val phase = phaseNow ?: lastPhase.value

    V4Theme {
        if (phase == null) {
            Box(Modifier.fillMaxSize().background(V4.colors.background))
            return@V4Theme
        }
        val startDestination = when (phase) {
            V4AuthPhase.OUT -> V4Routes.WELCOME
            V4AuthPhase.VERIFY -> "sign_in"
            V4AuthPhase.IN -> V4Routes.HOME
        }

        // Keeps the evening check-in and slip nudges planned while signed in.
        if (phase == V4AuthPhase.IN) {
            val nudges: az.tribe.lifeplanner.data.habits.NudgeService = koinInject()
            val selfTicks: az.tribe.lifeplanner.data.habits.SelfTickService = koinInject()
            LaunchedEffect(Unit) { nudges.run() }
            // Ticks habits that tick themselves from a workout, a breathing break or study time.
            LaunchedEffect("self_tick") { selfTicks.run() }
            // Keeps repeating study blocks planned a week ahead.
            val study: az.tribe.lifeplanner.data.study.StudyService = koinInject()
            LaunchedEffect(Unit) { runCatching { study.fillRepeats() } }
            // Keeps the daily mood reminder, when it is on, planned a week ahead.
            val moodNudges: az.tribe.lifeplanner.data.mind.MoodNudges = koinInject()
            LaunchedEffect(Unit) { moodNudges.replan() }
        }

        LaunchedEffect(authState) {
            // Same iOS guard as App.kt: wait until the graph is set before navigating.
            navController.currentBackStackEntryFlow.firstOrNull()
            val current = navController.currentDestination?.route
            when (authState) {
                is AuthState.Authenticated, is AuthState.Guest ->
                    if (current == V4Routes.WELCOME || current == "sign_in") {
                        navController.navigate(V4Routes.HOME) { popUpTo(0) { inclusive = true } }
                    }
                is AuthState.Unauthenticated -> {
                    onSignedOut()
                    if (current != V4Routes.WELCOME && current != "sign_in") {
                        navController.navigate(V4Routes.WELCOME) { popUpTo(0) { inclusive = true } }
                    }
                }
                is AuthState.EmailVerificationPending ->
                    if (current != "sign_in") navController.navigate("sign_in") { popUpTo(0) { inclusive = true } }
                else -> {}
            }
        }

        val signedIn = authState is AuthState.Authenticated || authState is AuthState.Guest
        val signedInNow by rememberUpdatedState(signedIn)
        // A deep link (a tapped nudge, a goal link) waits until the home gate has settled on Today,
        // or the gate's own navigate-and-clear would wipe it straight away. First run keeps it out.
        suspend fun openWhenSettled(route: String) {
            val settled = navController.currentBackStackEntryFlow.first { entry ->
                entry.destination.route.let { it != V4Routes.HOME && it != null }
            }
            if (settled.destination.route in V4Routes.FIRST_RUN) return
            navController.navigate(route) { launchSingleTop = true }
        }
        LaunchedEffect(promoRoute, signedIn) {
            if (promoRoute != null && signedIn) openWhenSettled(promoRoute)
        }
        LaunchedEffect(Unit) {
            az.tribe.lifeplanner.util.DeepLinkNavigator.navEvents.collect { route ->
                if (signedInNow) openWhenSettled(route)
            }
        }

        var hubSelectedTab by remember { mutableStateOf(0) }
        var coachPrompt by remember { mutableStateOf<String?>(null) }
        var showQuickAdd by remember { mutableStateOf(false) }

        fun openTab(route: String) {
            navController.navigate(route) {
                popUpTo(V4Routes.TODAY) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }

        // While typing, every screen ends at the top of the keyboard (Android and iPhone alike), and
        // the tab bar steps aside so a field or the coach's composer sits right on the keyboard.
        val typing = az.tribe.lifeplanner.ui.v4.components.keyboardUp()
        val density = androidx.compose.ui.platform.LocalDensity.current
        var barHeight by remember { mutableStateOf(86.dp) }
        Box(Modifier.fillMaxSize().background(V4.colors.background)) {
          androidx.compose.runtime.CompositionLocalProvider(LocalTabBarHeight provides barHeight) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize().imePadding(),
                enterTransition = { fadeIn(tween(220)) },
                exitTransition = { fadeOut(tween(220)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(220)) },
            ) {
                appNavV4(
                    navController = navController,
                    authState = { authState },
                    authViewModel = authViewModel,
                    coachPrompt = { coachPrompt },
                    onCoachPrompt = { coachPrompt = it },
                    openTab = ::openTab,
                    onQuickAdd = { showQuickAdd = true },
                )

                // v3 graphs, unchanged. Their "home" is the v4 gate.
                val noTabs = emptyMap<String, Int>()
                val slide: (Int) -> Int = { it / 4 }
                appNavJournal(navController, noTabs, slide, hubSelectedTab, { hubSelectedTab = it })
                appNavProfile(navController, noTabs, slide)
                appNavAbilities(navController, noTabs, slide)
                appNavGoals(navController, goalViewModel, onHubTabSelected = { hubSelectedTab = it })
                appNavHabits(navController)
                appNavHabitDetailRedesign(navController)
                appNavToday(navController)
                appNavForYou(navController, onOpenWeather = onOpenWeather)
                if (FeatureFlags.PILLAR_POSSIBILITY) appNavPossibilityMode(navController)
                appNavGoalsRedesign(navController)
                appNavYouRedesign(navController)
                appNavOnboardingRedesign(navController, homeRoute = V4Routes.HOME)
                appNavCoach(navController)
                appNavAuth(navController, homeRoute = V4Routes.HOME)
                appNavDecisions(navController)
                if (FeatureFlags.PILLAR_CAUSAL) appNavCausal(navController)
                if (FeatureFlags.PILLAR_BECOMING) appNavBecoming(navController)
                if (FeatureFlags.PILLAR_WIRING) appNavWiring(navController)
                appNavKnowledge(navController)
                appNavWheel(navController)
            }

          }
            if (currentRoute in V4Routes.TABS && !typing) {
                Column(
                    Modifier.align(Alignment.BottomCenter).onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                ) {
                    if (currentRoute in V4Routes.ADD_BAR) {
                        V4AddAnythingBar(
                            onClick = { showQuickAdd = true },
                            modifier = Modifier.align(Alignment.End).padding(end = 16.dp, bottom = 12.dp),
                        )
                    }
                    V4BottomBar(currentRoute = currentRoute, onSelect = ::openTab)
                }
            }

            if (showQuickAdd) {
                QuickAddSheet(
                    onDismiss = { showQuickAdd = false },
                    onAskCoach = { text ->
                        showQuickAdd = false
                        coachPrompt = text
                        openTab(V4Routes.COACH)
                    },
                )
            }

            overlays()
        }
    }
}

internal fun NavGraphBuilder.appNavV4(
    navController: NavController,
    authState: () -> AuthState,
    authViewModel: AuthViewModel,
    coachPrompt: () -> String?,
    onCoachPrompt: (String?) -> Unit,
    openTab: (String) -> Unit,
    onQuickAdd: () -> Unit,
) {
    // The gate: first run or Today. Replaces itself, so Back never returns here.
    composable(V4Routes.HOME) {
        val planAreas: PlanAreasRepository = koinInject()
        val goals: GoalRepository = koinInject()
        val habits: HabitRepository = koinInject()
        val journal: JournalRepository = koinInject()
        LaunchedEffect(Unit) {
            val dest = when {
                planAreas.isFirstRunDone() -> V4Routes.TODAY
                authState() is AuthState.Authenticated -> V4Routes.UPDATE
                runCatching { goals.getAllGoals().isNotEmpty() || habits.getAllHabits().isNotEmpty() || journal.getRecentEntries(1).isNotEmpty() }
                    .getOrDefault(false) -> V4Routes.UPDATE
                else -> V4Routes.areas()
            }
            navController.navigate(dest) { popUpTo(0) { inclusive = true } }
        }
        Box(Modifier.fillMaxSize().background(V4.colors.background))
    }

    composable(V4Routes.WELCOME) {
        val state by authViewModel.authState.collectAsState()
        WelcomeScreen(
            busy = state is AuthState.Loading,
            onGetStarted = { authViewModel.signInAsGuest() },
            onHaveAccount = { navController.navigate("sign_in") { launchSingleTop = true } },
        )
    }

    composable(
        V4Routes.AREAS,
        arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "first" }),
    ) { entry ->
        val edit = entry.arguments?.read { getStringOrNull("mode") } == "edit"
        val vm: V4FirstRunViewModel = koinViewModel()
        val enabled by vm.enabledAreas.collectAsState()
        AreasScreen(
            initial = enabled,
            stepLabel = if (edit) null else "Step 1 of 3",
            onBack = if (edit) ({ navController.popBackStack() }) else null,
            ctaPrefix = if (edit) "Save" else "Continue with",
            onContinue = { picked ->
                vm.pickAreas(picked)
                if (edit) navController.popBackStack() else navController.navigate(V4Routes.CONNECT)
            },
        )
    }

    composable(V4Routes.CONNECT) {
        val vm: V4FirstRunViewModel = koinViewModel()
        ConnectScreen(
            stepLabel = "Step 2 of 3",
            onBack = { navController.popBackStack() },
            onDone = { navController.navigate(V4Routes.starters("new")) },
        )
    }

    composable(
        V4Routes.STARTERS,
        arguments = listOf(navArgument("path") { type = NavType.StringType; defaultValue = "new" }),
    ) { entry ->
        val path = entry.arguments?.read { getStringOrNull("path") } ?: "new"
        val vm: V4FirstRunViewModel = koinViewModel()
        az.tribe.lifeplanner.ui.v4.habits.V4StartersScreen(
            stepLabel = if (path == "new") "Step 3 of 3" else null,
            onBack = { navController.popBackStack() },
            onDone = {
                vm.finish(path = path)
                navController.navigate(V4Routes.TODAY) { popUpTo(0) { inclusive = true } }
            },
        )
    }

    composable(V4Routes.UPDATE) {
        val vm: V4FirstRunViewModel = koinViewModel()
        UpdateScreen(
            viewModel = vm,
            onChangeAreas = { navController.navigate(V4Routes.areas(edit = true)) },
            onOpenToday = {
                // Nothing to tick yet: the starter deck first, so Today does not open empty.
                if (vm.kept.value?.habits == 0) navController.navigate(V4Routes.starters("update"))
                else {
                    vm.finish(path = "update")
                    navController.navigate(V4Routes.TODAY) { popUpTo(0) { inclusive = true } }
                }
            },
        )
    }

    composable(V4Routes.TODAY) {
        V4TodayScreen(
            onOpenYou = { navController.navigate(V4Routes.YOU) { launchSingleTop = true } },
            onAskCoach = { prompt -> onCoachPrompt(prompt); openTab(V4Routes.COACH) },
            onOpenItem = { item ->
                when (item.type) {
                    DayItemType.HABIT -> navController.navigate(V4Routes.area(PlanArea.HABITS)) { launchSingleTop = true }
                    DayItemType.STEP -> item.goalId?.let { navController.navigate("goal_detail/$it") { launchSingleTop = true } }
                    DayItemType.WORKOUT -> navController.navigate(V4Routes.area(PlanArea.FITNESS)) { launchSingleTop = true }
                    DayItemType.TRIP -> navController.navigate(V4Routes.area(PlanArea.TRAVEL)) { launchSingleTop = true }
                    DayItemType.MEAL -> navController.navigate(V4Routes.area(PlanArea.MEALS)) { launchSingleTop = true }
                    DayItemType.STUDY -> navController.navigate(V4Routes.area(PlanArea.STUDY)) { launchSingleTop = true }
                    DayItemType.CAREER -> navController.navigate(V4Routes.area(PlanArea.CAREER)) { launchSingleTop = true }
                    DayItemType.BILL -> navController.navigate(V4Routes.area(PlanArea.MONEY)) { launchSingleTop = true }
                    DayItemType.EVENT -> {}
                }
            },
            onCheckIn = { navController.navigate(V4Routes.CHECK_IN) { launchSingleTop = true } },
            onReview = { navController.navigate(V4Routes.REVIEW) { launchSingleTop = true } },
            bottomInset = TabInset,
            onOpenTrip = { navController.navigate(V4Routes.trip(it)) { launchSingleTop = true } },
            onPlanTrip = { navController.navigate(V4Routes.area(PlanArea.TRAVEL)) { launchSingleTop = true } },
        )
    }

    composable(V4Routes.CHECK_IN) {
        az.tribe.lifeplanner.ui.v4.habits.V4CheckInScreen(onClose = { navController.popBackStack() })
    }

    composable(V4Routes.REVIEW) {
        az.tribe.lifeplanner.ui.v4.habits.V4ReviewScreen(onClose = { navController.popBackStack() })
    }

    composable(V4Routes.LIFE) {
        V4LifeScreen(
            onOpenArea = { navController.navigate(V4Routes.area(it)) { launchSingleTop = true } },
            onChangeAreas = { navController.navigate(V4Routes.areas(edit = true)) { launchSingleTop = true } },
            onAskCoach = { prompt -> onCoachPrompt(prompt); openTab(V4Routes.COACH) },
            bottomInset = TabInset,
        )
    }

    composable(V4Routes.COACH) {
        V4CoachScreen(
            initialPrompt = coachPrompt(),
            onPromptConsumed = { onCoachPrompt(null) },
            onAllCoaches = { navController.navigate(Screen.AIChat.route) { launchSingleTop = true } },
            bottomInset = CoachInset,
        )
    }

    composable(V4Routes.YOU) {
        val state by authViewModel.authState.collectAsState()
        V4YouScreen(
            authState = state,
            authViewModel = authViewModel,
            onBack = { navController.popBackStack() },
            onConnectedApps = { navController.navigate(V4Routes.CONNECTED_APPS) { launchSingleTop = true } },
            onChangeAreas = { navController.navigate(V4Routes.areas(edit = true)) { launchSingleTop = true } },
            onRoute = { navController.navigate(it) { launchSingleTop = true } },
        )
    }

    composable(V4Routes.CONNECTED_APPS) {
        V4ConnectedAppsScreen(
            onBack = { navController.popBackStack() },
            onCalendars = { navController.navigate(Screen.CalendarSettings.route) { launchSingleTop = true } },
        )
    }

    composable(V4Routes.AREA, arguments = listOf(navArgument("area") { type = NavType.StringType })) { entry ->
        val area = entry.arguments?.read { getStringOrNull("area") }?.let { PlanArea.fromKey(it) } ?: return@composable
        V4AreaScreen(
            area = area,
            actions = AreaActions(
                onBack = { navController.popBackStack() },
                onOpenGoal = { navController.navigate("goal_detail/$it") { launchSingleTop = true } },
                onOpenHabit = { navController.navigate("habit_detail_redesign/$it") { launchSingleTop = true } },
                onNewPlan = { navController.navigate(Screen.GoalWizard.route) { launchSingleTop = true } },
                onNewRoutine = { navController.navigate(Screen.AddHabit.route) { launchSingleTop = true } },
                onRoute = { navController.navigate(it) { launchSingleTop = true } },
                onQuickAdd = onQuickAdd,
                onAskCoach = { text -> onCoachPrompt(text); openTab(V4Routes.COACH) },
            ),
        )
    }

    composable(V4Routes.TRIP, arguments = listOf(navArgument("tripId") { type = NavType.StringType })) { entry ->
        val tripId = entry.arguments?.read { getStringOrNull("tripId") } ?: return@composable
        V4TripScreen(
            tripId = tripId,
            onBack = { navController.popBackStack() },
            onPlanTrip = { navController.navigate(V4Routes.area(PlanArea.TRAVEL)) { popUpTo(V4Routes.TRIP) { inclusive = true }; launchSingleTop = true } },
        )
    }
}
