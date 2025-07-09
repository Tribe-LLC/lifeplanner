package az.tribe.lifeplanner.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.model.GoalTypeQuestions
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.ui.components.AiGoalHeader
import az.tribe.lifeplanner.ui.components.GoalCard
import az.tribe.lifeplanner.ui.components.LoadingCard
import az.tribe.lifeplanner.ui.components.PromptInputCard
import az.tribe.lifeplanner.ui.components.QuestionCard
import az.tribe.lifeplanner.ui.components.QuestionNavigationBar
import az.tribe.lifeplanner.ui.components.QuestionProgressHeader
import az.tribe.lifeplanner.ui.components.QuestionWithType
import az.tribe.lifeplanner.ui.components.ResultsHeader

enum class QuestionnaireStep {
    INPUT, ANSWERING, GENERATING, RESULTS
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AiGoalGenerationScreen(
    viewModel: GoalViewModel,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit // New parameter for home navigation
) {
    var currentStep by remember { mutableStateOf(QuestionnaireStep.INPUT) }
    var userPrompt by remember { mutableStateOf("") }
    var questions by remember { mutableStateOf<List<GoalTypeQuestions>>(emptyList()) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Observe ViewModel states
    val vmQuestions by viewModel.questions.collectAsState()
    val vmIsLoadingQuestions by viewModel.isLoadingQuestions.collectAsState()
    val vmQuestionnaireStep by viewModel.questionnaireStep.collectAsState()
    val vmError by viewModel.error.collectAsState()
    val vmGeneratedGoalsFromAI by viewModel.generatedGoalsFromAI.collectAsState()

    // Update local state based on ViewModel
    LaunchedEffect(vmQuestions) {
        if (vmQuestions.isNotEmpty()) {
            questions = vmQuestions
        }
    }

    LaunchedEffect(vmQuestionnaireStep) {
        currentStep = vmQuestionnaireStep
    }

// Show error if any
    vmError?.let { error ->
        LaunchedEffect(error) {
            println("Error: $error")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar
            TopAppBar(
                title = { Text("Smart AI Goal Generation") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep == QuestionnaireStep.INPUT) {
                            onBackClick()
                        } else {
                            currentStep = QuestionnaireStep.INPUT
                            viewModel.resetQuestionnaire()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            // Content area - handle each step differently
            when (currentStep) {
                QuestionnaireStep.INPUT -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                AiGoalHeader(
                                    title = "AI Goal Generator",
                                    description = "Tell us about your goals and we'll create personalized questions to understand your specific situation better."
                                )

                                PromptInputCard(
                                    prompt = userPrompt,
                                    onPromptChange = { userPrompt = it },
                                    placeholder = "Example: I want to save money for a house and get in better shape for my wedding...",
                                    buttonText = "Generate Questions",
                                    isLoading = vmIsLoadingQuestions,
                                    onButtonClick = {
                                        viewModel.generateQuestionnaire(userPrompt)
                                    }
                                )
                            }
                        }
                    }
                }

                QuestionnaireStep.ANSWERING -> {
                    if (questions.isNotEmpty()) {
                        // Full-screen questioning flow with fixed navigation
                        Box(modifier = Modifier.fillMaxSize()) {
                            StepByStepQuestionFlow(
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
                        }
                    }
                }

                QuestionnaireStep.GENERATING, QuestionnaireStep.RESULTS -> {
                    // These steps can use the regular LazyColumn layout
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (currentStep) {
                            QuestionnaireStep.GENERATING -> {
                                item {
                                    LoadingCard(
                                        title = "Creating Your Personalized Goals",
                                        description = "Our AI is analyzing your responses and crafting goals specifically tailored to your situation..."
                                    )
                                }
                            }

                            QuestionnaireStep.RESULTS -> {
                                item {
                                    ResultsSection(
                                        answeredQuestions = answers.size,
                                        generatedGoals = vmGeneratedGoalsFromAI,
                                        onGoHome = onHomeClick, // Changed from onCreateNew to onGoHome
                                        onAddGoalToList = { goal ->
                                            println("Goal trying to add ${goal.title}")
                                            viewModel.addGeneratedGoalToList(goal)
                                        },
                                        onAddAllGoalsToList = {
                                            viewModel.addAllGeneratedGoalsToList()
                                        }
                                    )
                                }
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StepByStepQuestionFlow(
    questions: List<GoalTypeQuestions>,
    answers: Map<String, String>,
    onAnswerChange: (String, String) -> Unit,
    onComplete: () -> Unit
) {
    val allQuestions = questions.flatMap { goalType ->
        goalType.questions.map { question ->
            QuestionWithType(question = question, goalType = goalType.goalType)
        }
    }

    val pagerState = rememberPagerState(pageCount = { allQuestions.size })
    val coroutineScope = rememberCoroutineScope()
    val progress = answers.size.toFloat() / allQuestions.size.toFloat()

    // Use a Box as the root container to position elements absolutely
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 80.dp) // Add padding at bottom to make room for nav buttons
        ) {
            // Progress header
            if (allQuestions.isNotEmpty()) {
                QuestionProgressHeader(
                    currentPage = pagerState.currentPage,
                    totalQuestions = allQuestions.size,
                    progress = progress,
                    goalType = allQuestions[pagerState.currentPage].goalType
                )
            }

            // Question pager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val questionWithType = allQuestions[page]
                QuestionCard(
                    question = questionWithType.question,
                    answer = answers[questionWithType.question.title],
                    onAnswerSelected = { answer ->
                        onAnswerChange(questionWithType.question.title, answer)
                    }
                )
            }
        }

        // Navigation bar - fixed at the bottom of the screen
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        ) {
            QuestionNavigationBar(
                pagerState = pagerState,
                allQuestions = allQuestions,
                answers = answers,
                coroutineScope = coroutineScope,
                onComplete = onComplete
            )
        }
    }
}

@Composable
private fun ResultsSection(
    answeredQuestions: Int,
    generatedGoals: List<Goal>,
    onGoHome: () -> Unit, // Changed parameter name
    onAddGoalToList: (Goal) -> Unit,
    onAddAllGoalsToList: () -> Unit
) {
    var addedGoals by remember { mutableStateOf<Set<String>>(emptySet()) }
    var allGoalsAdded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {


        generatedGoals.forEach { goal ->
            val isGoalAdded = addedGoals.contains(goal.id) || allGoalsAdded

            GoalCard(
                goal = goal,
                isAdded = isGoalAdded,
                onAddGoal = {
                    onAddGoalToList(goal)
                    addedGoals = addedGoals + goal.id
                }
            )
        }

        ResultsHeader(
            goalCount = generatedGoals.size,
            answeredQuestions = answeredQuestions,
            allGoalsAdded = allGoalsAdded,
            onGoHome = onGoHome, // Changed parameter name and functionality
            onAddAllGoals = {
                onAddAllGoalsToList()
                allGoalsAdded = true
                addedGoals = generatedGoals.map { it.id }.toSet()
            }
        )

        if (generatedGoals.isEmpty()) {
            Card {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Goals Generated Successfully!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                    Text(
                        text = "Your personalized goals have been created. Use the 'Add All Goals' button to add them to your goals list.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}