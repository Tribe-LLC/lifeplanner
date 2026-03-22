package az.tribe.lifeplanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.data.analytics.FacebookAnalytics
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
import az.tribe.lifeplanner.domain.repository.GeminiRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.service.SmartReminderManager
import az.tribe.lifeplanner.usecases.*
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class GoalViewModel(
    // Reactive data source
    private val goalRepository: GoalRepository,
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
    private val geminiRepository: GeminiRepository,
    private val smartReminderManager: SmartReminderManager
) : ViewModel() {

    // Smart reminder events (one-shot, collected by UI for snackbar)
    private val _reminderEvent = MutableSharedFlow<String>()
    val reminderEvent: SharedFlow<String> = _reminderEvent.asSharedFlow()

    // State Management
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(GoalFilter.ALL)
    val selectedFilter: StateFlow<GoalFilter> = _selectedFilter.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Reactive goals: auto-updates from DB, with client-side search/filter
    val goals: StateFlow<List<Goal>> = combine(
        goalRepository.observeAllGoals(),
        _searchQuery,
        _selectedFilter
    ) { allGoals, query, filter ->
        var result = allGoals
        if (query.isNotBlank()) {
            result = result.filter { goal ->
                goal.title.contains(query, ignoreCase = true) ||
                    goal.description.contains(query, ignoreCase = true)
            }
        }
        when (filter) {
            GoalFilter.ALL -> result
            GoalFilter.ACTIVE -> result.filter { it.status != GoalStatus.COMPLETED }
            GoalFilter.COMPLETED -> result.filter { it.status == GoalStatus.COMPLETED }
        }
    }
        .onEach { _isLoading.value = false }
        .catch { e ->
            _error.value = "Failed to load goals: ${e.message}"
            _isLoading.value = false
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _analytics = MutableStateFlow<GoalAnalytics?>(null)
    val analytics: StateFlow<GoalAnalytics?> = _analytics

    private val _goalHistory = MutableStateFlow<List<GoalChange>>(emptyList())
    val goalHistory: StateFlow<List<GoalChange>> = _goalHistory.asStateFlow()

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

    // Event to prompt user to complete goal when all milestones are done
    private val _promptCompleteGoal = MutableStateFlow<String?>(null)
    val promptCompleteGoal: StateFlow<String?> = _promptCompleteGoal.asStateFlow()

    fun clearCompleteGoalPrompt() {
        _promptCompleteGoal.value = null
    }

    init {
    }

    // Search and Filter Functions (reactive: combine auto-re-evaluates)
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length >= 3) {
            FacebookAnalytics.logSearch(query, "goal")
        }
    }

    fun updateFilter(filter: GoalFilter) {
        _selectedFilter.value = filter
    }

    // Goal CRUD Operations
    fun createGoal(goal: Goal) {
        viewModelScope.launch {
            try {
                createGoalUseCase(goal)
                Analytics.goalCreated(goal.category.name, "manual")
                val result = smartReminderManager.syncRemindersForGoal(goal)
                if (result.hasChanges) {
                    _reminderEvent.emit("${result.total} smart reminder${if (result.total > 1) "s" else ""} set for \"${goal.title}\"")
                }
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to create goal: ${e.message}"
            }
        }
    }

    fun updateGoal(goal: Goal) {
        viewModelScope.launch {
            try {
                updateGoalUseCase(goal)
                val result = smartReminderManager.syncRemindersForGoal(goal)
                if (result.created > 0) {
                    _reminderEvent.emit("Reminders updated for \"${goal.title}\"")
                }
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to update goal: ${e.message}"
            }
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch {
            try {
                val oldGoal = getGoalByIdUseCase(id)
                smartReminderManager.cleanupRemindersForDeletedGoal(id)
                deleteGoalUseCase(id)
                if (oldGoal != null) {
                    Analytics.goalAbandoned(id, oldGoal.category.name, oldGoal.progress?.toInt() ?: 0)
                }
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
                Analytics.goalProgressUpdated(id, newProgress)

                // Log the change
                if (oldGoal != null) {
                    logGoalChangeUseCase(
                        goalId = id,
                        field = "progress",
                        oldValue = oldGoal.progress?.toString() ?: "0",
                        newValue = newProgress.toString()
                    )
                }

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

                        // Auto-complete milestones when goal is completed
                        if (newStatus == GoalStatus.COMPLETED && oldGoal.milestones.isNotEmpty()) {
                            oldGoal.milestones.filter { !it.isCompleted }.forEach { milestone ->
                                toggleMilestoneCompletionUseCase(milestone.id, true)
                            }
                            val updatedGoal = getGoalByIdUseCase(id)
                            updatedGoal?.let { recalculateAndUpdateProgress(it) }
                        }

                        // Uncheck milestones when reverting from completed
                        if (oldGoal.status == GoalStatus.COMPLETED && newStatus != GoalStatus.COMPLETED && oldGoal.milestones.isNotEmpty()) {
                            oldGoal.milestones.filter { it.isCompleted }.forEach { milestone ->
                                toggleMilestoneCompletionUseCase(milestone.id, false)
                            }
                            val updatedGoal = getGoalByIdUseCase(id)
                            updatedGoal?.let { recalculateAndUpdateProgress(it) }
                        }
                    }
                    // Sync smart reminders based on new status
                    if (newStatus == GoalStatus.COMPLETED) {
                        Analytics.goalCompleted(id, oldGoal?.category?.name ?: "", 0)
                        smartReminderManager.cleanupRemindersForCompletedGoal(id)
                    } else if (oldGoal?.status == GoalStatus.COMPLETED) {
                        val refreshed = getGoalByIdUseCase(id)
                        refreshed?.let { smartReminderManager.reactivateRemindersForGoal(it) }
                    }

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

                    // Recalculate progress with new milestone
                    val updatedGoal = getGoalByIdUseCase(goalId)
                    updatedGoal?.let {
                        recalculateAndUpdateProgress(it)
                        smartReminderManager.syncRemindersForGoal(it)
                    }

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

                if (milestone != null && goal != null) {
                    val willBeCompleted = !milestone.isCompleted
                    val result = toggleMilestoneCompletionUseCase(milestoneId, willBeCompleted)

                    if (result.isSuccess) {
                        if (willBeCompleted) {
                            Analytics.milestoneCompleted(goalId, milestoneId)
                        }
                        logGoalChangeUseCase(
                            goalId = goalId,
                            field = "milestone_completed",
                            oldValue = milestone.isCompleted.toString(),
                            newValue = willBeCompleted.toString()
                        )

                        // Auto-calculate progress based on milestones
                        val updatedGoal = getGoalByIdUseCase(goalId)
                        updatedGoal?.let { recalculateAndUpdateProgress(it) }

                        // Auto-set status to IN_PROGRESS when first milestone is completed
                        // and goal is still NOT_STARTED
                        if (willBeCompleted && goal.status == GoalStatus.NOT_STARTED) {
                            updateGoalStatusUseCase(goalId, GoalStatus.IN_PROGRESS)
                            logGoalChangeUseCase(
                                goalId = goalId,
                                field = "status",
                                oldValue = GoalStatus.NOT_STARTED.name,
                                newValue = GoalStatus.IN_PROGRESS.name
                            )
                        }

                        // Check if all milestones are now completed
                        val refreshedGoal = getGoalByIdUseCase(goalId)
                        if (refreshedGoal != null &&
                            refreshedGoal.milestones.isNotEmpty() &&
                            refreshedGoal.milestones.all { it.isCompleted } &&
                            refreshedGoal.status != GoalStatus.COMPLETED) {
                            // Prompt user to complete the goal
                            _promptCompleteGoal.value = goalId
                        }

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

    /**
     * Recalculates goal progress based on completed milestones
     * Progress = (completedMilestones / totalMilestones) * 100
     */
    private suspend fun recalculateAndUpdateProgress(goal: Goal) {
        if (goal.milestones.isEmpty()) return

        val completedCount = goal.milestones.count { it.isCompleted }
        val totalCount = goal.milestones.size
        val newProgress = ((completedCount.toFloat() / totalCount.toFloat()) * 100).toInt()

        if (goal.progress?.toInt() != newProgress) {
            updateGoalProgressUseCase(goal.id, newProgress)
            logGoalChangeUseCase(
                goalId = goal.id,
                field = "progress",
                oldValue = goal.progress?.toString() ?: "0",
                newValue = newProgress.toString()
            )
        }
    }

    @Deprecated("No-op: data flows reactively via SQLDelight Flows", level = DeprecationLevel.WARNING)
    fun loadAllGoals() { }

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
        return goals.value.find { it.id == id }
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
                        Logger.d("GoalViewModel") { "Generated ${goals.size} personalized goals" }
                        goals.forEach { goal ->
                            Logger.d("GoalViewModel") { "Goal: ${goal.title}" }
                            goal.milestones.forEach { milestone ->
                                Logger.d("GoalViewModel") { "  - Milestone: ${milestone.title}" }
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
     * AI-first: Generate goals directly from a prompt without questionnaire
     */
    fun generateGoalsDirectly(prompt: String) {
        viewModelScope.launch {
            try {
                _isGeneratingPersonalizedGoals.value = true
                _error.value = null
                _questionnaireStep.value = QuestionnaireStep.GENERATING

                Logger.d("GoalViewModel") { "AI Goal Generation: Starting direct generation with prompt: $prompt" }
                Analytics.aiGoalGenerationStarted()

                geminiRepository.generateGoalsDirect(prompt)
                    .onSuccess { goals ->
                        Logger.d("GoalViewModel") { "AI Goal Generation: Received ${goals.size} goals" }
                        Analytics.aiGoalGenerationCompleted(goals.size)
                        if (goals.isEmpty()) {
                            _error.value = "AI returned no goals. Please try again."
                            _questionnaireStep.value = QuestionnaireStep.INPUT
                        } else {
                            _generatedGoalsFromAI.value = goals
                            goals.forEach { goal ->
                                Logger.d("GoalViewModel") { "  Goal: ${goal.title} (${goal.milestones.size} milestones)" }
                            }
                            _questionnaireStep.value = QuestionnaireStep.RESULTS
                        }
                    }
                    .onError { error ->
                        Logger.e("GoalViewModel") { "AI Goal Generation: Error - $error" }
                        _error.value = "Could not generate goals. Check your internet connection and try again."
                        _questionnaireStep.value = QuestionnaireStep.INPUT
                    }
            } catch (e: Exception) {
                Logger.e("GoalViewModel", e) { "AI Goal Generation: Exception - ${e.message}" }
                _error.value = "Something went wrong. Please try again."
                _questionnaireStep.value = QuestionnaireStep.INPUT
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
                createGoalUseCase(goal)
                Analytics.goalCreated(goal.category.name, "ai_generated", hasAiGenerated = true)
                smartReminderManager.syncRemindersForGoal(goal)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to add goal: ${e.message}"
            }
        }
    }

    /**
     * Add all generated goals to the main goals list
     */
    fun addAllGeneratedGoalsToList() {
        viewModelScope.launch {
            try {
                val goals = _generatedGoalsFromAI.value
                goals.forEach { goal ->
                    createGoalUseCase(goal)
                    smartReminderManager.syncRemindersForGoal(goal)
                }
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to add goals: ${e.message}"
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