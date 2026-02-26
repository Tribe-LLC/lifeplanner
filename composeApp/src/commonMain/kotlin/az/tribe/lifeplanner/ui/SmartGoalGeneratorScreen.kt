package az.tribe.lifeplanner.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.ui.theme.modernColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    WELCOME, SCENARIO_SELECT, CUSTOM_INPUT, CATEGORIES, LOADING_QUESTIONS, ANSWERING, GENERATING, RESULTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartGoalGeneratorScreen(
    viewModel: GoalViewModel,
    onBackClick: () -> Unit,
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableStateOf(GeneratorStep.WELCOME) }
    var selectedScenario by remember { mutableStateOf<LifeScenario?>(null) }
    var customPrompt by remember { mutableStateOf("") }
    var selectedCategories by remember { mutableStateOf<Set<GoalCategory>>(emptySet()) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var hasStartedGeneration by remember { mutableStateOf(false) }

    val generatedGoals by viewModel.generatedGoalsFromAI.collectAsState()
    val isLoading by viewModel.isLoadingQuestions.collectAsState()
    val questionnaireStep by viewModel.questionnaireStep.collectAsState()
    val questions by viewModel.questions.collectAsState()

    // Reset everything when screen is first opened
    LaunchedEffect(Unit) {
        viewModel.resetQuestionnaire()
        hasStartedGeneration = false
    }

    // Watch for ViewModel state changes - only after user started generation
    LaunchedEffect(questionnaireStep, hasStartedGeneration) {
        if (!hasStartedGeneration) return@LaunchedEffect

        when (questionnaireStep) {
            QuestionnaireStep.ANSWERING -> {
                if (currentStep == GeneratorStep.LOADING_QUESTIONS) {
                    currentStep = GeneratorStep.ANSWERING
                }
            }
            QuestionnaireStep.GENERATING -> {
                currentStep = GeneratorStep.GENERATING
            }
            QuestionnaireStep.RESULTS -> {
                currentStep = GeneratorStep.RESULTS
            }
            else -> {}
        }
    }

    Scaffold(
        containerColor = MaterialTheme.modernColors.background,
        topBar = {
            SmartGeneratorTopBar(
                currentStep = currentStep,
                onBackClick = {
                    when (currentStep) {
                        GeneratorStep.WELCOME -> onBackClick()
                        GeneratorStep.SCENARIO_SELECT -> currentStep = GeneratorStep.WELCOME
                        GeneratorStep.CUSTOM_INPUT -> currentStep = GeneratorStep.SCENARIO_SELECT
                        GeneratorStep.CATEGORIES -> {
                            currentStep = if (selectedScenario != null) GeneratorStep.SCENARIO_SELECT
                            else GeneratorStep.CUSTOM_INPUT
                        }
                        GeneratorStep.LOADING_QUESTIONS -> {
                            viewModel.resetQuestionnaire()
                            currentStep = GeneratorStep.CATEGORIES
                        }
                        GeneratorStep.ANSWERING -> {
                            viewModel.resetQuestionnaire()
                            answers = emptyMap()
                            currentStep = GeneratorStep.CATEGORIES
                        }
                        GeneratorStep.GENERATING -> {} // Can't go back during generation
                        GeneratorStep.RESULTS -> {
                            viewModel.resetQuestionnaire()
                            answers = emptyMap()
                            currentStep = GeneratorStep.WELCOME
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
                GeneratorStep.WELCOME -> WelcomeStep(
                    onGetStarted = { currentStep = GeneratorStep.SCENARIO_SELECT }
                )

                GeneratorStep.SCENARIO_SELECT -> ScenarioSelectStep(
                    scenarios = lifeScenarios,
                    onScenarioSelected = { scenario ->
                        selectedScenario = scenario
                        selectedCategories = scenario.suggestedCategories.toSet()
                        currentStep = GeneratorStep.CATEGORIES
                    },
                    onCustomClick = {
                        selectedScenario = null
                        currentStep = GeneratorStep.CUSTOM_INPUT
                    }
                )

                GeneratorStep.CUSTOM_INPUT -> CustomInputStep(
                    prompt = customPrompt,
                    onPromptChange = { customPrompt = it },
                    onContinue = {
                        currentStep = GeneratorStep.CATEGORIES
                    }
                )

                GeneratorStep.CATEGORIES -> CategorySelectStep(
                    selectedCategories = selectedCategories,
                    onCategoryToggle = { category ->
                        selectedCategories = if (selectedCategories.contains(category)) {
                            selectedCategories - category
                        } else {
                            selectedCategories + category
                        }
                    },
                    onGenerate = {
                        hasStartedGeneration = true
                        currentStep = GeneratorStep.LOADING_QUESTIONS
                        val prompt = selectedScenario?.prompt ?: customPrompt
                        val categoriesText = selectedCategories.joinToString(", ") { it.name.lowercase() }
                        viewModel.generateQuestionnaire("$prompt. Focus areas: $categoriesText")
                    }
                )

                GeneratorStep.LOADING_QUESTIONS -> LoadingQuestionsStep()

                GeneratorStep.ANSWERING -> QuestionAnsweringStep(
                    questions = questions,
                    answers = answers,
                    onAnswerChange = { question, answer ->
                        answers = answers + (question to answer)
                        viewModel.answerQuestion(question, answer)
                    },
                    onComplete = {
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
        GeneratorStep.WELCOME -> 0f
        GeneratorStep.SCENARIO_SELECT -> 0.15f
        GeneratorStep.CUSTOM_INPUT -> 0.3f
        GeneratorStep.CATEGORIES -> 0.45f
        GeneratorStep.LOADING_QUESTIONS -> 0.55f
        GeneratorStep.ANSWERING -> 0.7f
        GeneratorStep.GENERATING -> 0.85f
        GeneratorStep.RESULTS -> 1f
    }

    Column {
        TopAppBar(
            title = {
                Text(
                    when (currentStep) {
                        GeneratorStep.WELCOME -> "Smart Goal Generator"
                        GeneratorStep.SCENARIO_SELECT -> "Choose Your Path"
                        GeneratorStep.CUSTOM_INPUT -> "Tell Us More"
                        GeneratorStep.CATEGORIES -> "Focus Areas"
                        GeneratorStep.LOADING_QUESTIONS -> "Preparing Questions"
                        GeneratorStep.ANSWERING -> "Quick Questions"
                        GeneratorStep.GENERATING -> "Creating Goals"
                        GeneratorStep.RESULTS -> "Your Goals"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                if (currentStep != GeneratorStep.GENERATING && currentStep != GeneratorStep.LOADING_QUESTIONS) {
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
private fun WelcomeStep(onGetStarted: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.8f)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Animated icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
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
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }

                Text(
                    text = "Let's Create Your\nPerfect Goals",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.modernColors.textPrimary
                )

                Text(
                    text = "Our AI will help you create personalized, actionable goals based on your life situation and aspirations.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.modernColors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Features list
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    FeatureItem(
                        icon = Icons.Rounded.Psychology,
                        text = "AI-powered personalization"
                    )
                    FeatureItem(
                        icon = Icons.Rounded.Timeline,
                        text = "Smart milestones included"
                    )
                    FeatureItem(
                        icon = Icons.Rounded.Bolt,
                        text = "Ready in under 2 minutes"
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Get Started",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.modernColors.textSecondary
        )
    }
}

@Composable
private fun ScenarioSelectStep(
    scenarios: List<LifeScenario>,
    onScenarioSelected: (LifeScenario) -> Unit,
    onCustomClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "What brings you here today?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Choose a life moment or describe your own",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.modernColors.textSecondary
            )
        }

        items(scenarios.chunked(2)) { rowScenarios ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowScenarios.forEach { scenario ->
                    ScenarioCard(
                        scenario = scenario,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(160.dp),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(scenario.gradientColors))
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
    onPromptChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(modifier = Modifier
            .padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Tell us about your goals",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "What would you like to achieve? Be as specific as you'd like.",
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

        // Suggestion chips
        Column( verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                modifier = Modifier
                    .padding(horizontal = 24.dp),
                text = "Quick ideas:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.modernColors.textSecondary
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                val suggestions = listOf(
                    "Get in shape",
                    "Learn new skills",
                    "Save money",
                    "Reduce stress",
                    "Be more productive"
                )
                items(suggestions) { suggestion ->
                    SuggestionChip(
                        onClick = {
                            onPromptChange(
                                if (prompt.isEmpty()) "I want to $suggestion"
                                else "$prompt, $suggestion"
                            )
                        },
                        label = { Text(suggestion) }
                    )
                }
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = prompt.length >= 10
        ) {
            Text("Continue", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun CategorySelectStep(
    selectedCategories: Set<GoalCategory>,
    onCategoryToggle: (GoalCategory) -> Unit,
    onGenerate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Focus Areas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Select the life areas you want to focus on",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.modernColors.textSecondary
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GoalCategory.entries.chunked(2).forEach { rowCategories ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowCategories.forEach { category ->
                        CategoryChip(
                            category = category,
                            isSelected = selectedCategories.contains(category),
                            onClick = { onCategoryToggle(category) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowCategories.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        Button(
            onClick = onGenerate,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = selectedCategories.isNotEmpty()
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate My Goals", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CategoryChip(
    category: GoalCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryColor = category.toColor()

    Surface(
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) categoryColor.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) BorderStroke(2.dp, categoryColor)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = category.emoji(),
                fontSize = 24.sp
            )

            Text(
                text = category.displayName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) categoryColor else MaterialTheme.modernColors.textPrimary
            )

            Spacer(modifier = Modifier.weight(1f))

            if (isSelected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
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
            text = "Creating your personalized goals${".".repeat(dotCount)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Our AI is analyzing your input and crafting goals tailored just for you",
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

@Composable
private fun LoadingQuestionsStep() {
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
                .size(80.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Psychology,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Analyzing your goals${".".repeat(dotCount)}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Creating personalized questions to understand you better",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.modernColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuestionAnsweringStep(
    questions: List<az.tribe.lifeplanner.data.model.GoalTypeQuestions>,
    answers: Map<String, String>,
    onAnswerChange: (String, String) -> Unit,
    onComplete: () -> Unit
) {
    // Flatten all questions from all goal types
    val allQuestions = questions.flatMap { goalType ->
        goalType.questions.map { question ->
            question to goalType.goalType
        }
    }

    if (allQuestions.isEmpty()) {
        // Loading state or error
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { allQuestions.size })
    val coroutineScope = rememberCoroutineScope()
    val currentQuestion = allQuestions.getOrNull(pagerState.currentPage)
    val progress = (pagerState.currentPage + 1).toFloat() / allQuestions.size.toFloat()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Question progress
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Question ${pagerState.currentPage + 1} of ${allQuestions.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.modernColors.textSecondary
                )

                currentQuestion?.let { (_, goalType) ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = goalType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Questions pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            userScrollEnabled = false
        ) { page ->
            val (question, goalType) = allQuestions[page]
            val selectedAnswer = answers[question.title]

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = question.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.modernColors.textPrimary
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    question.options.forEach { option ->
                        val isSelected = selectedAnswer == option

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            onClick = { onAnswerChange(question.title, option) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.modernColors.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Navigation buttons

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Back button
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Next/Complete button
                val currentQuestionTitle = currentQuestion?.first?.title
                val hasAnswer = currentQuestionTitle != null && answers.containsKey(currentQuestionTitle)
                val isLastQuestion = pagerState.currentPage == allQuestions.lastIndex

                Button(
                    onClick = {
                        if (isLastQuestion) {
                            onComplete()
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    enabled = hasAnswer
                ) {
                    Text(if (isLastQuestion) "Generate Goals" else "Next")
                    if (!isLastQuestion) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
            }
        }
    }
}

// Extension functions for GoalCategory
private fun GoalCategory.toColor(): Color = when (this) {
    GoalCategory.CAREER -> Color(0xFF667EEA)
    GoalCategory.FINANCIAL -> Color(0xFFF7971E)
    GoalCategory.PHYSICAL -> Color(0xFFED213A)
    GoalCategory.SOCIAL -> Color(0xFF4776E6)
    GoalCategory.EMOTIONAL -> Color(0xFF11998E)
    GoalCategory.SPIRITUAL -> Color(0xFF8E54E9)
    GoalCategory.FAMILY -> Color(0xFFFC5C7D)
}

private fun GoalCategory.emoji(): String = when (this) {
    GoalCategory.CAREER -> "💼"
    GoalCategory.FINANCIAL -> "💰"
    GoalCategory.PHYSICAL -> "💪"
    GoalCategory.SOCIAL -> "👥"
    GoalCategory.EMOTIONAL -> "🧘"
    GoalCategory.SPIRITUAL -> "✨"
    GoalCategory.FAMILY -> "👨‍👩‍👧‍👦"
}

private fun GoalCategory.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }
