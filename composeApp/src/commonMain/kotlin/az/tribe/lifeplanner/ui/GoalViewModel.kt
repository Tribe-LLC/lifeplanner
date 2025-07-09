package az.tribe.lifeplanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.mapper.createNewMilestone
import az.tribe.lifeplanner.data.model.GoalTypeQuestions
import az.tribe.lifeplanner.data.model.QuestionAnswer
import az.tribe.lifeplanner.data.model.UserQuestionnaireAnswers
import az.tribe.lifeplanner.data.model.onError
import az.tribe.lifeplanner.data.model.onSuccess
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.GoalAnalytics
import az.tribe.lifeplanner.domain.model.GoalChange
import az.tribe.lifeplanner.domain.enum.GoalFilter
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.usecases.*
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import dev.gitlive.firebase.remoteconfig.remoteConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

class GoalViewModel(
    // Core CRUD Use Cases
    private val getAllGoalsUseCase: GetAllGoalsUseCase,
    private val createGoalUseCase: CreateGoalUseCase,
    private val updateGoalUseCase: UpdateGoalUseCase,
    private val deleteGoalUseCase: DeleteGoalUseCase,
    private val getGoalByIdUseCase: GetGoalByIdUseCase,

    // Progress and Status Use Cases
    private val updateGoalProgressUseCase: UpdateGoalProgressUseCase,
    private val updateGoalStatusUseCase: UpdateGoalStatusUseCase,
    private val updateGoalNotesUseCase: UpdateGoalNotesUseCase,

    // Search and Filter Use Cases
    private val searchGoalsUseCase: SearchGoalsUseCase,
    private val getActiveGoalsUseCase: GetActiveGoalsUseCase,
    private val getCompletedGoalsUseCase: GetCompletedGoalsUseCase,

    // Milestone Use Cases
    private val addMilestoneUseCase: AddMilestoneUseCase,
    private val toggleMilestoneCompletionUseCase: ToggleMilestoneCompletionUseCase,

    // Analytics and History Use Cases
    private val getGoalAnalyticsUseCase: GetGoalAnalyticsUseCase,
    private val getGoalHistoryUseCase: GetGoalHistoryUseCase,
    private val logGoalChangeUseCase: LogGoalChangeUseCase,

    private val generateAiQuestionnaireUseCase: GenerateAiQuestionnaireUseCase,
    private val generateAiGoalsUseCase: GenerateAiGoalsUseCase,
) : ViewModel() {

    // State Management
    private val _goals = MutableStateFlow<List<Goal>>(emptyList())
    val goals: StateFlow<List<Goal>> = _goals.asStateFlow()

    private val _analytics = MutableStateFlow<GoalAnalytics?>(null)
    val analytics: StateFlow<GoalAnalytics?> = _analytics

    private val _goalHistory = MutableStateFlow<List<GoalChange>>(emptyList())
    val goalHistory: StateFlow<List<GoalChange>> = _goalHistory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(GoalFilter.ALL)
    val selectedFilter: StateFlow<GoalFilter> = _selectedFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _isForceUpdateEnabled = MutableStateFlow<Boolean?>(null)
    val isForceUpdateEnabled: StateFlow<Boolean?> = _isForceUpdateEnabled

    private val _userPrompt = MutableStateFlow("")
    val userPrompt: StateFlow<String> = _userPrompt.asStateFlow()

    private val _questions = MutableStateFlow<List<GoalTypeQuestions>>(emptyList())
    val questions: StateFlow<List<GoalTypeQuestions>> = _questions.asStateFlow()

    private val _userAnswers = MutableStateFlow(UserQuestionnaireAnswers(emptyList()))
    val userAnswers: StateFlow<UserQuestionnaireAnswers> = _userAnswers.asStateFlow()

    private val _questionnaireStep = MutableStateFlow(QuestionnaireStep.INPUT)
    val questionnaireStep: StateFlow<QuestionnaireStep> = _questionnaireStep.asStateFlow()

    private val _isLoadingQuestions = MutableStateFlow(false)
    val isLoadingQuestions: StateFlow<Boolean> = _isLoadingQuestions.asStateFlow()

    private val _isGeneratingPersonalizedGoals = MutableStateFlow(false)
    val isGeneratingPersonalizedGoals: StateFlow<Boolean> = _isGeneratingPersonalizedGoals.asStateFlow()

    private val _generatedGoalsFromAI = MutableStateFlow<List<Goal>>(emptyList())
    val generatedGoalsFromAI: StateFlow<List<Goal>> = _generatedGoalsFromAI.asStateFlow()

    init {
        checkConfig()
        loadAllGoals()

    }

    // Search and Filter Functions
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        filterGoals()
    }

    fun updateFilter(filter: GoalFilter) {
        _selectedFilter.value = filter
        filterGoals()
    }

    private fun filterGoals() {
        viewModelScope.launch {
            try {
                val searchQuery = _searchQuery.value
                val filter = _selectedFilter.value

                val filteredGoals = when {
                    searchQuery.isNotBlank() -> {
                        val searchResults = searchGoalsUseCase(searchQuery)
                        when (filter) {
                            GoalFilter.ALL -> searchResults
                            GoalFilter.ACTIVE -> searchResults.filter { it.status != GoalStatus.COMPLETED }
                            GoalFilter.COMPLETED -> searchResults.filter { it.status == GoalStatus.COMPLETED }
                        }
                    }

                    filter != GoalFilter.ALL -> {
                        when (filter) {
                            GoalFilter.ACTIVE -> getActiveGoalsUseCase()
                            GoalFilter.COMPLETED -> getCompletedGoalsUseCase()
                            GoalFilter.ALL -> getAllGoalsUseCase()
                        }
                    }

                    else -> getAllGoalsUseCase()
                }

                _goals.value = filteredGoals
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to filter goals: ${e.message}"
            }
        }
    }

    // Goal CRUD Operations
    fun createGoal(goal: Goal) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                    createGoalUseCase(goal)
                    loadAllGoals()
                    _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to create goal: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateGoal(goal: Goal) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                updateGoalUseCase(goal)
                loadAllGoals()
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to update goal: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch {
            try {
                deleteGoalUseCase(id)
                _goals.value = _goals.value.filterNot { it.id == id }
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to delete goal: ${e.message}"
            }
        }
    }

    // Goal Progress and Status Updates
    fun updateGoalProgress(id: String, newProgress: Int) {
        viewModelScope.launch {
            try {
                val oldGoal = getGoalByIdUseCase(id)
                updateGoalProgressUseCase(id, newProgress)

                // Log the change
                if (oldGoal != null) {
                    logGoalChangeUseCase(
                        goalId = id,
                        field = "progress",
                        oldValue = oldGoal.progress?.toString() ?: "0",
                        newValue = newProgress.toString()
                    )
                }

                loadAllGoals()
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to update progress: ${e.message}"
            }
        }
    }

    fun updateGoalStatus(id: String, newStatus: GoalStatus) {
        viewModelScope.launch {
            try {
                val oldGoal = getGoalByIdUseCase(id)
                val result = updateGoalStatusUseCase(id, newStatus)

                if (result.isSuccess) {
                    // Log the change
                    if (oldGoal != null) {
                        logGoalChangeUseCase(
                            goalId = id,
                            field = "status",
                            oldValue = oldGoal.status.name,
                            newValue = newStatus.name
                        )
                    }
                    loadAllGoals()
                    _error.value = null
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to update status"
                }
            } catch (e: Exception) {
                _error.value = "Failed to update status: ${e.message}"
            }
        }
    }

    fun updateGoalNotes(id: String, notes: String) {
        viewModelScope.launch {
            try {
                val oldGoal = getGoalByIdUseCase(id)
                val result = updateGoalNotesUseCase(id, notes)

                if (result.isSuccess) {
                    // Log the change
                    if (oldGoal != null) {
                        logGoalChangeUseCase(
                            goalId = id,
                            field = "notes",
                            oldValue = oldGoal.notes,
                            newValue = notes
                        )
                    }
                    loadAllGoals()
                    _error.value = null
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to update notes"
                }
            } catch (e: Exception) {
                _error.value = "Failed to update notes: ${e.message}"
            }
        }
    }

    // Milestone Management
    fun addMilestone(goalId: String, milestoneTitle: String, dueDate: LocalDate? = null) {
        viewModelScope.launch {
            try {
                val newMilestone = createNewMilestone(milestoneTitle, dueDate)
                val result = addMilestoneUseCase(goalId, newMilestone)

                if (result.isSuccess) {
                    logGoalChangeUseCase(
                        goalId = goalId,
                        field = "milestone_added",
                        oldValue = null,
                        newValue = milestoneTitle
                    )
                    loadAllGoals()
                    _error.value = null
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to add milestone"
                }
            } catch (e: Exception) {
                _error.value = "Failed to add milestone: ${e.message}"
            }
        }
    }

    fun toggleMilestoneCompletion(goalId: String, milestoneId: String) {
        viewModelScope.launch {
            try {
                val goal = getGoalByIdUseCase(goalId)
                val milestone = goal?.milestones?.find { it.id == milestoneId }

                if (milestone != null) {
                    val result =
                        toggleMilestoneCompletionUseCase(milestoneId, !milestone.isCompleted)

                    if (result.isSuccess) {
                        logGoalChangeUseCase(
                            goalId = goalId,
                            field = "milestone_completed",
                            oldValue = milestone.isCompleted.toString(),
                            newValue = (!milestone.isCompleted).toString()
                        )
                        // Force reload all goals to get updated milestone data
                        loadAllGoals()
                        _error.value = null
                    } else {
                        _error.value =
                            result.exceptionOrNull()?.message ?: "Failed to toggle milestone"
                    }
                }
            } catch (e: Exception) {
                _error.value = "Failed to toggle milestone: ${e.message}"
            }
        }
    }

    // Loading and Data Management
    fun loadAllGoals() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val result = getAllGoalsUseCase()
                _goals.value = result

                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to load goals: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAnalytics() {
        viewModelScope.launch {
            try {
                _analytics.value = getGoalAnalyticsUseCase()
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to load analytics: ${e.message}"
                _analytics.value = null
            }
        }
    }

    fun loadGoalHistory(goalId: String) {
        viewModelScope.launch {
            try {
                _goalHistory.value = getGoalHistoryUseCase(goalId)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to load goal history: ${e.message}"
            }
        }
    }

    // Utility Methods
    fun getGoalById(id: String): Goal? {
        return _goals.value.find { it.id == id }
    }

    fun checkConfig() {
        viewModelScope.launch {
            try {
                val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig
                remoteConfig.fetch(Duration.ZERO)
                remoteConfig.fetchAndActivate()

                _isForceUpdateEnabled.value =
                    Firebase.remoteConfig.getValue("isForceUpdateEnabled").asBoolean()
            } catch (e: Exception) {
                println("Failed to check config: ${e.message}")
            }
        }
    }

    // NEW: Enhanced AI goal generation methods

    /**
     * Step 1: Generate questionnaire based on user prompt
     * Example: generateQuestionnaire("I want to save money and get healthy")
     */
    fun generateQuestionnaire(userPrompt: String) {
        viewModelScope.launch {
            try {
                _isLoadingQuestions.value = true
                _userPrompt.value = userPrompt
                _error.value = null

                generateAiQuestionnaireUseCase(userPrompt)
                    .onSuccess { questions ->
                        _questions.value = questions
                        _questionnaireStep.value = QuestionnaireStep.ANSWERING
                    }
                    .onError { error ->
                        _error.value = "Failed to generate questions: ${error}"
                        _questionnaireStep.value = QuestionnaireStep.INPUT
                    }
            } catch (e: Exception) {
                _error.value = "Failed to generate questions: ${e.message}"
                _questionnaireStep.value = QuestionnaireStep.INPUT
            } finally {
                _isLoadingQuestions.value = false
            }
        }
    }

    /**
     * Step 2: Answer a question
     * Example: answerQuestion("What's your current fitness level?", "Beginner")
     */
    fun answerQuestion(questionTitle: String, selectedOption: String) {
        val currentAnswers = _userAnswers.value.answers.toMutableList()

        // Find existing answer or add new one
        val existingIndex = currentAnswers.indexOfFirst { it.questionTitle == questionTitle }
        val newAnswer = QuestionAnswer(questionTitle, selectedOption)

        if (existingIndex >= 0) {
            currentAnswers[existingIndex] = newAnswer
        } else {
            currentAnswers.add(newAnswer)
        }

        _userAnswers.value = UserQuestionnaireAnswers(currentAnswers)
    }


    /**
     * Step 3: Generate personalized goals based on answers
     */
    fun generatePersonalizedGoals() {
        viewModelScope.launch {
            try {
                _isGeneratingPersonalizedGoals.value = true
                _error.value = null
                _questionnaireStep.value = QuestionnaireStep.GENERATING

                if (_userPrompt.value.isBlank()) {
                    _error.value = "Original prompt is missing"
                    return@launch
                }

                if (_userAnswers.value.answers.isEmpty()) {
                    _error.value = "Please answer the questions first"
                    return@launch
                }

                generateAiGoalsUseCase(
                    _userPrompt.value,
                    _userAnswers.value
                )
                    .onSuccess { goals ->
                        // Store generated goals for display FIRST
                        _generatedGoalsFromAI.value = goals

                        // WAIT to add them to database until user clicks "Add Goal" buttons
                        // Don't automatically add them here

                        _questionnaireStep.value = QuestionnaireStep.RESULTS
                        println("Generated ${goals.size} personalized goals")
                        goals.forEach { goal ->
                            println("Goal: ${goal.title}")
                            goal.milestones.forEach { milestone ->
                                println("  - Milestone: ${milestone.title}")
                            }
                        }
                    }
                    .onError { error ->
                        _error.value = "Failed to generate personalized goals: ${error}"
                        _questionnaireStep.value = QuestionnaireStep.ANSWERING
                    }
            } catch (e: Exception) {
                _error.value = "Failed to generate personalized goals: ${e.message}"
                _questionnaireStep.value = QuestionnaireStep.ANSWERING
            } finally {
                _isGeneratingPersonalizedGoals.value = false
            }
        }
    }

    /**
     * Add a specific generated goal to the main goals list
     */
    fun addGeneratedGoalToList(goal: Goal) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                    createGoalUseCase(goal)
                    loadAllGoals()
                    _error.value = null
                    println("Added goal to main list: ${goal.title}")
            } catch (e: Exception) {
                _error.value = "Failed to add goal: ${e.message}"
                println("Error adding goal: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Add all generated goals to the main goals list
     */
    fun addAllGeneratedGoalsToList() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val goals = _generatedGoalsFromAI.value

                goals.forEach { goal ->
                        createGoalUseCase(goal)
                        println("Added goal to main list: ${goal.title}")
                }

                // Force reload to ensure all goals appear in the main list
                loadAllGoals()
                _error.value = null
                println("Added ${goals.size} goals to main list")

            } catch (e: Exception) {
                _error.value = "Failed to add goals: ${e.message}"
                println("Error adding goals: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }


    /**
     * Check if questionnaire is complete
     */
    fun isQuestionnaireComplete(): Boolean {
        val totalQuestions = _questions.value.sumOf { it.questions.size }
        val answeredQuestions = _userAnswers.value.answers.size
        return totalQuestions > 0 && answeredQuestions >= totalQuestions
    }

    /**
     * Reset questionnaire flow
     */
    fun resetQuestionnaire() {
        _userPrompt.value = ""
        _questions.value = emptyList()
        _userAnswers.value = UserQuestionnaireAnswers(emptyList())
        _questionnaireStep.value = QuestionnaireStep.INPUT
        _error.value = null
    }


}