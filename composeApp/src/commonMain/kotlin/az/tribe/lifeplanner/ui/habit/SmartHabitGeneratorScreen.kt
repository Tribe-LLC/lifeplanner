package az.tribe.lifeplanner.ui.habit

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.util.NetworkConnectivityObserver
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

data class HabitScenario(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val prompt: String,
    val gradientColors: List<Color>,
    val category: GoalCategory
)

val habitScenarios = listOf(
    HabitScenario(
        id = "morning_routine",
        title = "Morning Routine",
        subtitle = "Start every day strong",
        icon = "🌅",
        prompt = "I want to build a consistent morning routine that sets me up for a productive day",
        gradientColors = listOf(Color(0xFFFF8008), Color(0xFFFF4B2B)),
        category = GoalCategory.WELLBEING
    ),
    HabitScenario(
        id = "get_fit",
        title = "Get Fit & Healthy",
        subtitle = "Body & energy transformation",
        icon = "💪",
        prompt = "I want to get fitter, eat healthier, and have more energy throughout the day",
        gradientColors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        category = GoalCategory.BODY
    ),
    HabitScenario(
        id = "learn_grow",
        title = "Learn & Grow",
        subtitle = "Skills and knowledge",
        icon = "🧠",
        prompt = "I want to develop new skills, read more, and continuously improve myself",
        gradientColors = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
        category = GoalCategory.CAREER
    ),
    HabitScenario(
        id = "financial",
        title = "Financial Discipline",
        subtitle = "Build money-smart habits",
        icon = "💰",
        prompt = "I want to spend less, save more, and build better financial habits",
        gradientColors = listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
        category = GoalCategory.MONEY
    ),
    HabitScenario(
        id = "stress_less",
        title = "Stress Less",
        subtitle = "Mindfulness & recovery",
        icon = "🧘",
        prompt = "I want to reduce stress, improve sleep, and find more calm in my daily life",
        gradientColors = listOf(Color(0xFF4776E6), Color(0xFF8E54E9)),
        category = GoalCategory.WELLBEING
    ),
    HabitScenario(
        id = "relationships",
        title = "Better Relationships",
        subtitle = "Connect & communicate",
        icon = "👥",
        prompt = "I want to strengthen my relationships, communicate better, and be more present with people I care about",
        gradientColors = listOf(Color(0xFFFC5C7D), Color(0xFF6A82FB)),
        category = GoalCategory.PEOPLE
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartHabitGeneratorScreen(
    onBackClick: () -> Unit,
    onComplete: () -> Unit,
    onNavigateToManual: () -> Unit,
    viewModel: SmartHabitGeneratorViewModel = koinViewModel()
) {
    val step by viewModel.step.collectAsState()
    val generatedHabits by viewModel.generatedHabits.collectAsState()
    val error by viewModel.error.collectAsState()
    val addedTitles by viewModel.addedTitles.collectAsState()

    var customPrompt by remember { mutableStateOf("") }

    val connectivityObserver: NetworkConnectivityObserver = koinInject()
    val isConnected by connectivityObserver.isConnected.collectAsState()
    val isOffline = !isConnected

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.reset() }

    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HabitGeneratorTopBar(
                step = step,
                onBackClick = {
                    when (step) {
                        HabitGeneratorStep.SCENARIO_SELECT -> onBackClick()
                        HabitGeneratorStep.CUSTOM_INPUT -> viewModel.reset()
                        HabitGeneratorStep.GENERATING -> {}
                        HabitGeneratorStep.RESULTS -> viewModel.reset()
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) { currentStep ->
            when (currentStep) {
                HabitGeneratorStep.SCENARIO_SELECT -> HabitScenarioSelectStep(
                    scenarios = habitScenarios,
                    isOffline = isOffline,
                    onScenarioSelected = { scenario ->
                        viewModel.generateHabits(scenario.prompt)
                    },
                    onCustomClick = { viewModel.navigateToCustomInput() },
                    onManualClick = onNavigateToManual
                )

                HabitGeneratorStep.CUSTOM_INPUT -> HabitCustomInputStep(
                    prompt = customPrompt,
                    isOffline = isOffline,
                    onPromptChange = { customPrompt = it },
                    onGenerate = { viewModel.generateHabits(customPrompt) }
                )

                HabitGeneratorStep.GENERATING -> HabitGeneratingStep()

                HabitGeneratorStep.RESULTS -> HabitResultsStep(
                    habits = generatedHabits,
                    addedTitles = addedTitles,
                    onAddHabit = { viewModel.addHabit(it) },
                    onAddAll = { viewModel.addAllHabits() },
                    onComplete = onComplete
                )
            }
        }
    }
}
