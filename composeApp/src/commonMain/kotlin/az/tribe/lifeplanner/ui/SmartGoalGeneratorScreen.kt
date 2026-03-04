package az.tribe.lifeplanner.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.ui.theme.modernColors
import az.tribe.lifeplanner.util.NetworkConnectivityObserver
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

// Life scenarios for quick selection
data class LifeScenario(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val prompt: String,
    val gradientColors: List<Color>,
    val suggestedCategories: List<GoalCategory>
)

val lifeScenarios = listOf(
    LifeScenario(
        id = "new_year",
        title = "New Year, New Me",
        subtitle = "Fresh start goals",
        icon = "🎯",
        prompt = "I want to start the new year with positive changes in my health, career, and personal growth",
        gradientColors = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
        suggestedCategories = listOf(GoalCategory.PHYSICAL, GoalCategory.CAREER, GoalCategory.EMOTIONAL)
    ),
    LifeScenario(
        id = "career_growth",
        title = "Level Up Career",
        subtitle = "Professional growth",
        icon = "🚀",
        prompt = "I want to advance my career, learn new skills, and increase my income",
        gradientColors = listOf(Color(0xFF11998E), Color(0xFF38EF7D)),
        suggestedCategories = listOf(GoalCategory.CAREER, GoalCategory.FINANCIAL)
    ),
    LifeScenario(
        id = "wedding_prep",
        title = "Wedding Ready",
        subtitle = "Big day preparation",
        icon = "💒",
        prompt = "I'm getting married and want to look my best, save money, and reduce stress",
        gradientColors = listOf(Color(0xFFFC5C7D), Color(0xFF6A82FB)),
        suggestedCategories = listOf(GoalCategory.PHYSICAL, GoalCategory.FINANCIAL, GoalCategory.EMOTIONAL)
    ),
    LifeScenario(
        id = "financial_freedom",
        title = "Financial Freedom",
        subtitle = "Money mastery",
        icon = "💰",
        prompt = "I want to get out of debt, build savings, and create financial security",
        gradientColors = listOf(Color(0xFFF7971E), Color(0xFFFFD200)),
        suggestedCategories = listOf(GoalCategory.FINANCIAL, GoalCategory.CAREER)
    ),
    LifeScenario(
        id = "health_transformation",
        title = "Health Transformation",
        subtitle = "Mind & body wellness",
        icon = "💪",
        prompt = "I want to lose weight, build strength, improve my diet, and feel more energetic",
        gradientColors = listOf(Color(0xFFED213A), Color(0xFF93291E)),
        suggestedCategories = listOf(GoalCategory.PHYSICAL, GoalCategory.EMOTIONAL)
    ),
    LifeScenario(
        id = "work_life_balance",
        title = "Better Balance",
        subtitle = "Life harmony",
        icon = "⚖️",
        prompt = "I want to reduce stress, spend more time with family, and find better work-life balance",
        gradientColors = listOf(Color(0xFF4776E6), Color(0xFF8E54E9)),
        suggestedCategories = listOf(GoalCategory.FAMILY, GoalCategory.EMOTIONAL, GoalCategory.SOCIAL)
    )
)

enum class GeneratorStep {
    SCENARIO_SELECT, CUSTOM_INPUT, QUESTIONS, GENERATING, RESULTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartGoalGeneratorScreen(
    viewModel: GoalViewModel,
    onBackClick: () -> Unit,
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableStateOf(GeneratorStep.SCENARIO_SELECT) }
    var customPrompt by remember { mutableStateOf("") }
    val connectivityObserver: NetworkConnectivityObserver = koinInject()
    val isConnected by connectivityObserver.isConnected.collectAsState()
    val isOffline = !isConnected

    val generatedGoals by viewModel.generatedGoalsFromAI.collectAsState()
    val questionnaireStep by viewModel.questionnaireStep.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val isLoadingQuestions by viewModel.isLoadingQuestions.collectAsState()
    val error by viewModel.error.collectAsState()

    // Reset when screen opens
    LaunchedEffect(Unit) {
        viewModel.resetQuestionnaire()
    }

    // Watch ViewModel state for transitions
    LaunchedEffect(questionnaireStep) {
        when (questionnaireStep) {
            QuestionnaireStep.ANSWERING -> {
                currentStep = GeneratorStep.QUESTIONS
            }
            QuestionnaireStep.GENERATING -> {
                currentStep = GeneratorStep.GENERATING
            }
            QuestionnaireStep.RESULTS -> {
                currentStep = GeneratorStep.RESULTS
            }
            QuestionnaireStep.INPUT -> {
                // Error happened — go back to selection
                if (currentStep == GeneratorStep.GENERATING || currentStep == GeneratorStep.QUESTIONS) {
                    currentStep = GeneratorStep.SCENARIO_SELECT
                }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error as snackbar
    LaunchedEffect(error) {
        val msg = error
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.modernColors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SmartGeneratorTopBar(
                currentStep = currentStep,
                onBackClick = {
                    when (currentStep) {
                        GeneratorStep.SCENARIO_SELECT -> onBackClick()
                        GeneratorStep.CUSTOM_INPUT -> currentStep = GeneratorStep.SCENARIO_SELECT
                        GeneratorStep.QUESTIONS -> currentStep = GeneratorStep.SCENARIO_SELECT
                        GeneratorStep.GENERATING -> {} // Can't go back during generation
                        GeneratorStep.RESULTS -> {
                            viewModel.resetQuestionnaire()
                            currentStep = GeneratorStep.SCENARIO_SELECT
                        }
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) { step ->
            when (step) {
                GeneratorStep.SCENARIO_SELECT -> ScenarioSelectStep(
                    scenarios = lifeScenarios,
                    isOffline = isOffline,
                    onScenarioSelected = { scenario ->
                        val categoriesText = scenario.suggestedCategories.joinToString(", ") { it.name.lowercase() }
                        customPrompt = "${scenario.prompt}. Focus areas: $categoriesText"
                        currentStep = GeneratorStep.GENERATING
                        viewModel.generateQuestionnaire(customPrompt)
                    },
                    onCustomClick = {
                        currentStep = GeneratorStep.CUSTOM_INPUT
                    }
                )

                GeneratorStep.CUSTOM_INPUT -> CustomInputStep(
                    prompt = customPrompt,
                    isOffline = isOffline,
                    onPromptChange = { customPrompt = it },
                    onGenerate = {
                        currentStep = GeneratorStep.GENERATING
                        viewModel.generateQuestionnaire(customPrompt)
                    }
                )

                GeneratorStep.QUESTIONS -> QuestionsStep(
                    questions = questions,
                    onAnswerQuestion = { questionTitle, answer ->
                        viewModel.answerQuestion(questionTitle, answer)
                    },
                    isQuestionnaireComplete = viewModel.isQuestionnaireComplete(),
                    onSubmitAnswers = {
                        viewModel.generatePersonalizedGoals()
                    }
                )

                GeneratorStep.GENERATING -> GeneratingStep()

                GeneratorStep.RESULTS -> ResultsStep(
                    goals = generatedGoals,
                    onAddGoal = { goal ->
                        viewModel.addGeneratedGoalToList(goal)
                    },
                    onAddAll = {
                        viewModel.addAllGeneratedGoalsToList()
                    },
                    onComplete = onComplete
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartGeneratorTopBar(
    currentStep: GeneratorStep,
    onBackClick: () -> Unit
) {
    val progress = when (currentStep) {
        GeneratorStep.SCENARIO_SELECT -> 0f
        GeneratorStep.CUSTOM_INPUT -> 0.2f
        GeneratorStep.QUESTIONS -> 0.5f
        GeneratorStep.GENERATING -> 0.75f
        GeneratorStep.RESULTS -> 1f
    }

    Column {
        TopAppBar(
            title = {
                Text(
                    when (currentStep) {
                        GeneratorStep.SCENARIO_SELECT -> "AI Goal Generator"
                        GeneratorStep.CUSTOM_INPUT -> "Describe Your Goals"
                        GeneratorStep.QUESTIONS -> "Answer Questions"
                        GeneratorStep.GENERATING -> "Creating Goals"
                        GeneratorStep.RESULTS -> "Your Goals"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                if (currentStep != GeneratorStep.GENERATING) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        )

        // Progress indicator
        val animatedProgress by animateFloatAsState(progress, label = "progress")
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun ScenarioSelectStep(
    scenarios: List<LifeScenario>,
    isOffline: Boolean,
    onScenarioSelected: (LifeScenario) -> Unit,
    onCustomClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // AI hero section
            Column(
                modifier = Modifier.padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Pick a path, AI does the rest",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Select a life moment and we'll create personalized goals with milestones instantly",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.modernColors.textSecondary
                )
            }
        }

        if (isOffline) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "You're offline. Goal generation requires internet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        items(scenarios.chunked(2)) { rowScenarios ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowScenarios.forEach { scenario ->
                    ScenarioCard(
                        scenario = scenario,
                        enabled = !isOffline,
                        onClick = { onScenarioSelected(scenario) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowScenarios.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))

            // Custom option
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = onCustomClick
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Something else",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Describe your goals in your own words",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.modernColors.textSecondary
                        )
                    }

                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun ScenarioCard(
    scenario: LifeScenario,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(160.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
        enabled = enabled
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        if (enabled) scenario.gradientColors
                        else scenario.gradientColors.map { it.copy(alpha = 0.4f) }
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = scenario.icon,
                    fontSize = 32.sp
                )

                Column {
                    Text(
                        text = scenario.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = scenario.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomInputStep(
    prompt: String,
    isOffline: Boolean,
    onPromptChange: (String) -> Unit,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Tell us about your goals",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Describe what you want to achieve and AI will create goals with milestones for you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.modernColors.textSecondary
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            BasicTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.modernColors.textPrimary
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (prompt.isEmpty()) {
                            Text(
                                text = "Example: I want to get healthier, save more money, spend quality time with family, and advance in my career...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.modernColors.textSecondary.copy(alpha = 0.6f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }

        if (isOffline) {
            Text(
                text = "Goal generation requires internet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }

        Button(
            onClick = onGenerate,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = prompt.length >= 10 && !isOffline
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate My Goals", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GeneratingStep() {
    var dotCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            dotCount = (dotCount + 1) % 4
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Creating your goals${".".repeat(dotCount)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "AI is crafting personalized goals with milestones tailored just for you",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.modernColors.textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun QuestionsStep(
    questions: List<az.tribe.lifeplanner.data.model.GoalTypeQuestions>,
    onAnswerQuestion: (String, String) -> Unit,
    isQuestionnaireComplete: Boolean,
    onSubmitAnswers: () -> Unit
) {
    val allQuestions = questions.flatMap { goalType ->
        goalType.questions.map { q -> goalType.goalType to q }
    }
    val answers = remember { mutableStateMapOf<String, String>() }
    var currentIndex by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (allQuestions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val (goalType, question) = allQuestions[currentIndex]

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Progress
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Question ${currentIndex + 1} of ${allQuestions.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.modernColors.textSecondary
                    )
                    LinearProgressIndicator(
                        progress = { (currentIndex + 1).toFloat() / allQuestions.size },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                // Goal type chip
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = goalType.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Question title
                Text(
                    text = question.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Options
                question.options.forEach { option ->
                    val isSelected = answers[question.title] == option
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        onClick = {
                            answers[question.title] = option
                            onAnswerQuestion(question.title, option)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    answers[question.title] = option
                                    onAnswerQuestion(question.title, option)
                                }
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (currentIndex > 0) {
                    OutlinedButton(
                        onClick = { currentIndex-- },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Previous")
                    }
                }

                val isLastQuestion = currentIndex == allQuestions.size - 1
                val hasCurrentAnswer = answers.containsKey(allQuestions[currentIndex].second.title)

                Button(
                    onClick = {
                        if (isLastQuestion) {
                            onSubmitAnswers()
                        } else {
                            currentIndex++
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = hasCurrentAnswer
                ) {
                    if (isLastQuestion) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generate Goals", fontWeight = FontWeight.SemiBold)
                    } else {
                        Text("Next", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsStep(
    goals: List<Goal>,
    onAddGoal: (Goal) -> Unit,
    onAddAll: () -> Unit,
    onComplete: () -> Unit
) {
    var addedGoals by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCelebration by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        showCelebration = true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AnimatedVisibility(
                visible = showCelebration,
                enter = fadeIn() + scaleIn(initialScale = 0.8f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Text("🎉", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "${goals.size} Goals Created!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Here are your personalized goals",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.modernColors.textSecondary
                    )
                }
            }
        }

        items(goals) { goal ->
            GeneratedGoalCard(
                goal = goal,
                isAdded = addedGoals.contains(goal.id),
                onAdd = {
                    onAddGoal(goal)
                    addedGoals = addedGoals + goal.id
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (addedGoals.size < goals.size) {
                    Button(
                        onClick = {
                            onAddAll()
                            addedGoals = goals.map { it.id }.toSet()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.AddCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add All Goals", fontWeight = FontWeight.SemiBold)
                    }
                }

                OutlinedButton(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        if (addedGoals.isNotEmpty()) "Done - View My Goals"
                        else "Skip for Now",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun GeneratedGoalCard(
    goal: Goal,
    isAdded: Boolean,
    onAdd: () -> Unit
) {
    val categoryColor = goal.category.toColor()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isAdded) 0.dp else 2.dp,
        border = if (isAdded) BorderStroke(2.dp, Color(0xFF4CAF50)) else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = categoryColor.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = goal.category.displayName(),
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (isAdded) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF4CAF50)
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = "Added",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = goal.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.modernColors.textSecondary
            )

            if (goal.milestones.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${goal.milestones.size} milestones:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    goal.milestones.take(3).forEach { milestone ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(categoryColor)
                            )
                            Text(
                                text = milestone.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.modernColors.textSecondary
                            )
                        }
                    }
                    if (goal.milestones.size > 3) {
                        Text(
                            text = "+${goal.milestones.size - 3} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = categoryColor
                        )
                    }
                }
            }

            if (!isAdded) {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = categoryColor)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add This Goal")
                }
            }
        }
    }
}

private fun GoalCategory.toColor(): Color = when (this) {
    GoalCategory.CAREER -> Color(0xFF667EEA)
    GoalCategory.FINANCIAL -> Color(0xFFF7971E)
    GoalCategory.PHYSICAL -> Color(0xFFED213A)
    GoalCategory.SOCIAL -> Color(0xFF4776E6)
    GoalCategory.EMOTIONAL -> Color(0xFF11998E)
    GoalCategory.SPIRITUAL -> Color(0xFF8E54E9)
    GoalCategory.FAMILY -> Color(0xFFFC5C7D)
}

private fun GoalCategory.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }
