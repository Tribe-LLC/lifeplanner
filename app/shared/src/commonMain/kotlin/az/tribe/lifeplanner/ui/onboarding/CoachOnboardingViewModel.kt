package az.tribe.lifeplanner.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.model.WheelArea
import az.tribe.lifeplanner.domain.service.OnboardingWheelSeed
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.ActivityLevel
import az.tribe.lifeplanner.domain.model.BodySlice
import az.tribe.lifeplanner.domain.model.CareerSlice
import az.tribe.lifeplanner.domain.model.CircleSize
import az.tribe.lifeplanner.domain.model.EmploymentStatus
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.IncomeBand
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.LifeStage
import az.tribe.lifeplanner.domain.model.MetaSlice
import az.tribe.lifeplanner.domain.model.MoneySlice
import az.tribe.lifeplanner.domain.model.PeopleSlice
import az.tribe.lifeplanner.domain.model.PurposeSlice
import az.tribe.lifeplanner.domain.model.RelationshipStatus
import az.tribe.lifeplanner.domain.model.SavingsHabit
import az.tribe.lifeplanner.domain.model.SocialEnergy
import az.tribe.lifeplanner.domain.model.UserSituation
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.UserRepository
import az.tribe.lifeplanner.domain.repository.UserSituationRepository
import az.tribe.lifeplanner.domain.service.SmartReminderManager
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

enum class OnboardingPhase {
    LUNA_INTRO,
    LUNA_NAME,
    LUNA_PRIORITY,
    LUNA_WELLBEING,
    SPECIALIST_INTRO,
    SPECIALIST_Q1,
    SPECIALIST_Q2,
    SPECIALIST_Q3,
    SPECIALIST_Q4,
    MIND_DUMP,
    MIND_QUESTIONS,
    MIND_VALIDATION,
    COMPLETE,
    GOAL_PREVIEW,
    HABIT_SUGGEST
}

/** Stable, lower-case event names, so a rename of the enum constant does not silently split a funnel. */
internal fun OnboardingPhase.stepName(): String = name.lowercase()

data class GoalInterpretation(
    val summary: String,
    val goalTitle: String,
    val goalDescription: String,
    val category: GoalCategory
)

data class OnboardingHabitItem(
    val title: String,
    val description: String,
    val emoji: String,
    val frequency: HabitFrequency
)

class CoachOnboardingViewModel(
    private val userSituationRepository: UserSituationRepository,
    private val userRepository: UserRepository,
    private val goalRepository: GoalRepository,
    private val aiProxyService: AiProxyService,
    private val habitRepository: HabitRepository,
    private val settings: Settings,
    private val smartReminderManager: SmartReminderManager,
    private val wheelRepository: az.tribe.lifeplanner.domain.repository.WheelRepository,
) : ViewModel() {

    /**
     * Writes the sign-up ratings into the wheel as the user's own scores.
     *
     * This is the payoff for asking in areas rather than categories: the wheel is theirs from the
     * first launch, so the nudge picker, the coach's read on a goal and the wheel's own history all
     * start from something true instead of from nine invented fives that the user then has to go
     * find and correct.
     *
     * Best-effort. A goal-setting flow should not fail because a score would not write.
     */
    private suspend fun seedWheelFromRatings() {
        wheelRatings.forEach { (area, score) ->
            runCatching { wheelRepository.setScore(area, score, note = "Set during sign-up") }
        }
        // Today's snapshot is what the first comparison will be measured against, and a wheel with
        // no first day has nothing to compare to.
        if (wheelRatings.isNotEmpty()) runCatching { wheelRepository.captureSnapshot() }
    }

    /**
     * Schedule the bundled onboarding reminders (morning habits check + evening
     * reflection + weekly Sunday review) once the user finishes onboarding.
     *
     * Per the SmartReminderManager design we *do not* create per-habit reminders;
     * the morning reminder covers all habits in aggregate. We fall back to a
     * sensible 20:00 evening reflection time if no explicit time is captured
     * during onboarding.
     *
     * This is fire-and-forget: a failure here must never block onboarding
     * completion. Idempotent, SmartReminderManager null-checks existing IDs.
     */
    private suspend fun scheduleOnboardingReminders() {
        try {
            val eveningTime = LocalTime(hour = 20, minute = 0)
            smartReminderManager.createOnboardingReminders(eveningTime)
        } catch (e: Exception) {
            Logger.e("CoachOnboarding", e) { "Failed to create onboarding reminders: ${e.message}" }
        }
    }

    private val _phase = MutableStateFlow(OnboardingPhase.LUNA_INTRO)
    val phase: StateFlow<OnboardingPhase> = _phase.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // ── Luna answers ──────────────────────────────────────────────────────────

    var userName by mutableStateOf("")
    var userAge by mutableStateOf<Int?>(null)
    var topPriority by mutableStateOf<GoalCategory?>(null)
    var topPriorities by mutableStateOf<List<GoalCategory>>(emptyList())

    /**
     * The user's own wheel, given at sign-up. Replaces the abstract "which areas matter most"
     * question: it is less work to answer and it seeds real scores, so the app has something true
     * to say from the first launch. See [OnboardingWheelSeed].
     */
    var wheelRatings by mutableStateOf<Map<WheelArea, Double>>(emptyMap())

    fun rateArea(area: WheelArea, score: Double) {
        wheelRatings = wheelRatings + (area to score)
        // Focus follows the ratings rather than being asked for separately: someone who rated Money
        // 3 and Family 9 has already told us where the work is.
        topPriorities = OnboardingWheelSeed.prioritiesFrom(wheelRatings)
        topPriority = topPriorities.firstOrNull()
    }
    var stressLevel by mutableStateOf(5)
    var sleepQuality by mutableStateOf(7)

    // ── Career answers (Alex) ─────────────────────────────────────────────────

    var employmentStatus by mutableStateOf<EmploymentStatus?>(null)
    var jobRole by mutableStateOf("")
    var yearsExperience by mutableStateOf<Int?>(null)
    var careerGoal by mutableStateOf("")

    // ── Money answers (Morgan) ────────────────────────────────────────────────

    var incomeBand by mutableStateOf<IncomeBand?>(null)
    var savingsHabit by mutableStateOf<SavingsHabit?>(null)
    var hasDebt by mutableStateOf<Boolean?>(null)
    var financialGoal by mutableStateOf("")

    // ── Body answers (Kai) ────────────────────────────────────────────────────

    var activityLevel by mutableStateOf<ActivityLevel?>(null)
    var sleepHours by mutableStateOf(7f)
    var energyRating by mutableStateOf(6)

    // ── People answers (Sam) ──────────────────────────────────────────────────

    var socialEnergy by mutableStateOf<SocialEnergy?>(null)
    var closeCircleSize by mutableStateOf<CircleSize?>(null)
    var relationshipStatus by mutableStateOf<RelationshipStatus?>(null)

    // ── Purpose answers (River) ───────────────────────────────────────────────

    var topValues by mutableStateOf<List<String>>(emptyList())
    var mindfulnessPractice by mutableStateOf<Boolean?>(null)
    var longTermVision by mutableStateOf("")

    // ── Family answers (Jamie) ────────────────────────────────────────────────

    var familyRole by mutableStateOf("")
    var familyChallenge by mutableStateOf("")
    var familyVision by mutableStateOf("")

    // ── Mind dump, free-text first goal seed ─────────────────────────────────

    var mindDump by mutableStateOf("")

    // ── Mind analysis Q&A ─────────────────────────────────────────────────────

    var analysisQuestions by mutableStateOf<List<String>>(emptyList())
        private set
    var analysisAnswers by mutableStateOf<List<String>>(emptyList())
        private set
    var currentQuestionIndex by mutableStateOf(0)
        private set
    var goalInterpretation by mutableStateOf<GoalInterpretation?>(null)
        private set

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // ── Goal preview & habit suggestions ─────────────────────────────────────

    var generatedGoal by mutableStateOf<Goal?>(null)
        private set
    var habitSuggestions by mutableStateOf<List<Pair<OnboardingHabitItem, Boolean>>>(emptyList())
        private set

    // ── Derived ───────────────────────────────────────────────────────────────

    val specialistCoachId: String
        get() = when (topPriority) {
            GoalCategory.CAREER -> "alex_career"
            GoalCategory.MONEY -> "morgan_finance"
            GoalCategory.BODY -> "kai_fitness"
            GoalCategory.PEOPLE -> "sam_social"
            GoalCategory.PURPOSE -> "river_wellness"
            GoalCategory.FAMILY -> "jamie_family"
            else -> "luna_general"
        }

    /**
     * How many questions this specialist actually asks. The chat transcript must respect this:
     * rendering a bubble for a question that was never asked is what produced the empty bubbles.
     */
    internal val specialistQuestionCount: Int
        get() = when (specialistCoachId) {
            "alex_career" -> 4
            "morgan_finance" -> 4
            "kai_fitness" -> 3
            "sam_social" -> 3
            "river_wellness" -> 3
            "jamie_family" -> 3
            else -> 0
        }

    init {
        val resumedAt = restoreFromSettings()
        // The entry event used to live on WelcomeScreen, which nothing has navigated to since
        // CoachOnboardingScreen replaced it, so every v3 funnel had no denominator. It belongs
        // here, where the flow actually begins.
        if (resumedAt == null) Analytics.onboardingStarted() else Analytics.onboardingResumed(resumedAt.stepName())
    }

    fun togglePriority(category: GoalCategory) {
        topPriorities = if (category in topPriorities) topPriorities - category else topPriorities + category
        topPriority = topPriorities.firstOrNull()
    }

    fun overallCompleteness(): Float {
        var filled = 0
        var total = 5
        if (userName.isNotBlank() || userAge != null) filled++
        if (topPriorities.isNotEmpty()) filled++
        filled++ // stress/sleep always answered
        when (specialistCoachId) {
            "alex_career" -> { total += 2; if (employmentStatus != null) filled++; if (careerGoal.isNotBlank()) filled++ }
            "morgan_finance" -> { total += 2; if (incomeBand != null) filled++; if (savingsHabit != null) filled++ }
            "kai_fitness" -> { total += 2; if (activityLevel != null) filled++; if (energyRating > 0) filled++ }
            "sam_social" -> { total += 2; if (socialEnergy != null) filled++; if (closeCircleSize != null) filled++ }
            "river_wellness" -> { total += 2; if (topValues.isNotEmpty()) filled++; if (longTermVision.isNotBlank()) filled++ }
            "jamie_family" -> { total += 2; if (familyRole.isNotBlank()) filled++; if (familyChallenge.isNotBlank()) filled++ }
        }
        return (filled.toFloat() / total).coerceIn(0f, 1f)
    }

    fun advance() = moveTo(nextPhase(_phase.value))

    fun back() {
        val from = _phase.value
        val prev = previousPhase(from)
        _phase.value = prev
        settings.putString(KEY_PHASE, prev.name)
        if (prev != from) Analytics.onboardingStepBack(from.stepName(), from.ordinal)
    }

    /**
     * Every forward move in the flow, so the funnel cannot go stale by someone adding a phase and
     * forgetting to report it. Sixteen phases had one event between them before this existed, which
     * is why the flow could only be described as "hard" rather than measured.
     *
     * The step reported is the one being left, since that is the one the user just finished.
     */
    private fun moveTo(next: OnboardingPhase) {
        val from = _phase.value
        _phase.value = next
        saveToSettings(next)
        if (next != from) Analytics.onboardingStepCompleted(from.stepName(), from.ordinal)
    }

    private fun nextPhase(current: OnboardingPhase): OnboardingPhase = when (current) {
        OnboardingPhase.LUNA_INTRO -> OnboardingPhase.LUNA_NAME
        OnboardingPhase.LUNA_NAME -> OnboardingPhase.LUNA_PRIORITY
        OnboardingPhase.LUNA_PRIORITY -> OnboardingPhase.LUNA_WELLBEING
        OnboardingPhase.LUNA_WELLBEING -> OnboardingPhase.SPECIALIST_INTRO
        OnboardingPhase.SPECIALIST_INTRO -> OnboardingPhase.SPECIALIST_Q1
        OnboardingPhase.SPECIALIST_Q1 -> if (specialistQuestionCount >= 2) OnboardingPhase.SPECIALIST_Q2 else OnboardingPhase.MIND_DUMP
        OnboardingPhase.SPECIALIST_Q2 -> if (specialistQuestionCount >= 3) OnboardingPhase.SPECIALIST_Q3 else OnboardingPhase.MIND_DUMP
        OnboardingPhase.SPECIALIST_Q3 -> if (specialistQuestionCount >= 4) OnboardingPhase.SPECIALIST_Q4 else OnboardingPhase.MIND_DUMP
        OnboardingPhase.SPECIALIST_Q4 -> OnboardingPhase.MIND_DUMP
        OnboardingPhase.MIND_DUMP -> OnboardingPhase.COMPLETE
        OnboardingPhase.MIND_QUESTIONS -> OnboardingPhase.MIND_VALIDATION
        OnboardingPhase.MIND_VALIDATION -> OnboardingPhase.GOAL_PREVIEW
        OnboardingPhase.COMPLETE -> OnboardingPhase.COMPLETE
        OnboardingPhase.GOAL_PREVIEW -> OnboardingPhase.HABIT_SUGGEST
        OnboardingPhase.HABIT_SUGGEST -> OnboardingPhase.HABIT_SUGGEST
    }

    private fun previousPhase(current: OnboardingPhase): OnboardingPhase = when (current) {
        OnboardingPhase.LUNA_INTRO -> OnboardingPhase.LUNA_INTRO
        OnboardingPhase.LUNA_NAME -> OnboardingPhase.LUNA_INTRO
        OnboardingPhase.LUNA_PRIORITY -> OnboardingPhase.LUNA_NAME
        OnboardingPhase.LUNA_WELLBEING -> OnboardingPhase.LUNA_PRIORITY
        OnboardingPhase.SPECIALIST_INTRO -> OnboardingPhase.LUNA_WELLBEING
        OnboardingPhase.SPECIALIST_Q1 -> OnboardingPhase.SPECIALIST_INTRO
        OnboardingPhase.SPECIALIST_Q2 -> OnboardingPhase.SPECIALIST_Q1
        OnboardingPhase.SPECIALIST_Q3 -> OnboardingPhase.SPECIALIST_Q2
        OnboardingPhase.SPECIALIST_Q4 -> OnboardingPhase.SPECIALIST_Q3
        OnboardingPhase.MIND_DUMP -> when (specialistQuestionCount) {
            4 -> OnboardingPhase.SPECIALIST_Q4
            3 -> OnboardingPhase.SPECIALIST_Q3
            2 -> OnboardingPhase.SPECIALIST_Q2
            else -> OnboardingPhase.SPECIALIST_Q1
        }
        OnboardingPhase.COMPLETE -> OnboardingPhase.MIND_DUMP
        OnboardingPhase.MIND_QUESTIONS -> OnboardingPhase.MIND_DUMP
        OnboardingPhase.MIND_VALIDATION -> OnboardingPhase.MIND_QUESTIONS
        OnboardingPhase.GOAL_PREVIEW -> OnboardingPhase.MIND_DUMP
        OnboardingPhase.HABIT_SUGGEST -> OnboardingPhase.GOAL_PREVIEW
    }

    private fun saveToSettings(phase: OnboardingPhase) {
        settings.putString(KEY_PHASE, phase.name)
        settings.putString(KEY_USER_NAME, userName)
        userAge?.let { settings.putInt(KEY_USER_AGE, it) } ?: settings.remove(KEY_USER_AGE)
        topPriority?.let { settings.putString(KEY_TOP_PRIORITY, it.name) } ?: settings.remove(KEY_TOP_PRIORITY)
        if (topPriorities.isNotEmpty()) settings.putString(KEY_TOP_PRIORITIES, topPriorities.joinToString(",") { it.name }) else settings.remove(KEY_TOP_PRIORITIES)
        if (wheelRatings.isNotEmpty()) {
            settings.putString(KEY_WHEEL_RATINGS, wheelRatings.entries.joinToString(",") { "${it.key.name}:${it.value}" })
        } else settings.remove(KEY_WHEEL_RATINGS)
        settings.putInt(KEY_STRESS, stressLevel)
        settings.putInt(KEY_SLEEP_QUALITY, sleepQuality)
        employmentStatus?.let { settings.putString(KEY_EMPLOYMENT, it.name) } ?: settings.remove(KEY_EMPLOYMENT)
        settings.putString(KEY_JOB_ROLE, jobRole)
        yearsExperience?.let { settings.putInt(KEY_YEARS_EXP, it) } ?: settings.remove(KEY_YEARS_EXP)
        settings.putString(KEY_CAREER_GOAL, careerGoal)
        incomeBand?.let { settings.putString(KEY_INCOME_BAND, it.name) } ?: settings.remove(KEY_INCOME_BAND)
        savingsHabit?.let { settings.putString(KEY_SAVINGS_HABIT, it.name) } ?: settings.remove(KEY_SAVINGS_HABIT)
        hasDebt?.let { settings.putBoolean(KEY_HAS_DEBT, it) } ?: settings.remove(KEY_HAS_DEBT)
        settings.putString(KEY_FINANCIAL_GOAL, financialGoal)
        activityLevel?.let { settings.putString(KEY_ACTIVITY, it.name) } ?: settings.remove(KEY_ACTIVITY)
        settings.putFloat(KEY_SLEEP_HOURS, sleepHours)
        settings.putInt(KEY_ENERGY, energyRating)
        socialEnergy?.let { settings.putString(KEY_SOCIAL_ENERGY, it.name) } ?: settings.remove(KEY_SOCIAL_ENERGY)
        closeCircleSize?.let { settings.putString(KEY_CIRCLE_SIZE, it.name) } ?: settings.remove(KEY_CIRCLE_SIZE)
        relationshipStatus?.let { settings.putString(KEY_RELATIONSHIP, it.name) } ?: settings.remove(KEY_RELATIONSHIP)
        if (topValues.isNotEmpty()) settings.putString(KEY_TOP_VALUES, topValues.joinToString(",")) else settings.remove(KEY_TOP_VALUES)
        mindfulnessPractice?.let { settings.putBoolean(KEY_MINDFULNESS, it) } ?: settings.remove(KEY_MINDFULNESS)
        settings.putString(KEY_VISION, longTermVision)
        settings.putString(KEY_MIND_DUMP, mindDump)
        settings.putString(KEY_FAMILY_ROLE, familyRole)
        settings.putString(KEY_FAMILY_CHALLENGE, familyChallenge)
        settings.putString(KEY_FAMILY_VISION, familyVision)
    }

    /** The phase restored from a previous session, or null when this is a fresh start. */
    private fun restoreFromSettings(): OnboardingPhase? {
        val phaseName = settings.getStringOrNull(KEY_PHASE) ?: return null
        _phase.value = runCatching { OnboardingPhase.valueOf(phaseName) }.getOrElse { return null }
        userName = settings.getString(KEY_USER_NAME, "")
        userAge = settings.getIntOrNull(KEY_USER_AGE)
        topPriority = settings.getStringOrNull(KEY_TOP_PRIORITY)?.let { runCatching { GoalCategory.valueOf(it) }.getOrNull() }
        topPriorities = settings.getStringOrNull(KEY_TOP_PRIORITIES)?.split(",")?.mapNotNull { runCatching { GoalCategory.valueOf(it) }.getOrNull() } ?: emptyList()
        wheelRatings = settings.getStringOrNull(KEY_WHEEL_RATINGS)
            ?.split(",")
            ?.mapNotNull { entry ->
                val (name, value) = entry.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
                val area = runCatching { WheelArea.valueOf(name) }.getOrNull() ?: return@mapNotNull null
                val score = value.toDoubleOrNull() ?: return@mapNotNull null
                area to score
            }?.toMap() ?: emptyMap()
        stressLevel = settings.getInt(KEY_STRESS, 5)
        sleepQuality = settings.getInt(KEY_SLEEP_QUALITY, 7)
        employmentStatus = settings.getStringOrNull(KEY_EMPLOYMENT)?.let { runCatching { EmploymentStatus.valueOf(it) }.getOrNull() }
        jobRole = settings.getString(KEY_JOB_ROLE, "")
        yearsExperience = settings.getIntOrNull(KEY_YEARS_EXP)
        careerGoal = settings.getString(KEY_CAREER_GOAL, "")
        incomeBand = settings.getStringOrNull(KEY_INCOME_BAND)?.let { runCatching { IncomeBand.valueOf(it) }.getOrNull() }
        savingsHabit = settings.getStringOrNull(KEY_SAVINGS_HABIT)?.let { runCatching { SavingsHabit.valueOf(it) }.getOrNull() }
        hasDebt = settings.getBooleanOrNull(KEY_HAS_DEBT)
        financialGoal = settings.getString(KEY_FINANCIAL_GOAL, "")
        activityLevel = settings.getStringOrNull(KEY_ACTIVITY)?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() }
        sleepHours = settings.getFloat(KEY_SLEEP_HOURS, 7f)
        energyRating = settings.getInt(KEY_ENERGY, 6)
        socialEnergy = settings.getStringOrNull(KEY_SOCIAL_ENERGY)?.let { runCatching { SocialEnergy.valueOf(it) }.getOrNull() }
        closeCircleSize = settings.getStringOrNull(KEY_CIRCLE_SIZE)?.let { runCatching { CircleSize.valueOf(it) }.getOrNull() }
        relationshipStatus = settings.getStringOrNull(KEY_RELATIONSHIP)?.let { runCatching { RelationshipStatus.valueOf(it) }.getOrNull() }
        topValues = settings.getStringOrNull(KEY_TOP_VALUES)?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        mindfulnessPractice = settings.getBooleanOrNull(KEY_MINDFULNESS)
        longTermVision = settings.getString(KEY_VISION, "")
        mindDump = settings.getString(KEY_MIND_DUMP, "")
        familyRole = settings.getString(KEY_FAMILY_ROLE, "")
        familyChallenge = settings.getString(KEY_FAMILY_CHALLENGE, "")
        familyVision = settings.getString(KEY_FAMILY_VISION, "")
        return _phase.value
    }

    private fun clearInProgressState() {
        inProgressKeys.forEach { settings.remove(it) }
    }

    fun resetForNewSession() {
        clearInProgressState()
        settings.remove(COACH_ONBOARDING_KEY)
        _phase.value = OnboardingPhase.LUNA_INTRO
        userName = ""; userAge = null; topPriority = null; topPriorities = emptyList(); wheelRatings = emptyMap(); stressLevel = 5; sleepQuality = 7
        employmentStatus = null; jobRole = ""; yearsExperience = null; careerGoal = ""
        incomeBand = null; savingsHabit = null; hasDebt = null; financialGoal = ""
        activityLevel = null; sleepHours = 7f; energyRating = 6
        socialEnergy = null; closeCircleSize = null; relationshipStatus = null
        topValues = emptyList(); mindfulnessPractice = null; longTermVision = ""
        familyRole = ""; familyChallenge = ""; familyVision = ""
        mindDump = ""
        analysisQuestions = emptyList(); analysisAnswers = emptyList()
        currentQuestionIndex = 0; goalInterpretation = null
    }

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val userId = userRepository.getCurrentUser()?.id
                if (userId != null) {
                    userSituationRepository.upsert(userId, buildSituation())
                }
                seedWheelFromRatings()
                if (mindDump.isNotBlank()) {
                    val goal = generateFirstGoal()
                    if (goal != null) {
                        generatedGoal = goal
                        habitSuggestions = buildHabitSuggestions(goal.category)
                        moveTo(OnboardingPhase.GOAL_PREVIEW)
                        return@launch
                    }
                }
                clearInProgressState()
                settings.putBoolean(COACH_ONBOARDING_KEY, true)
                scheduleOnboardingReminders()
                Analytics.onboardingCompleted()
                onDone()
            } finally {
                _isSaving.value = false
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    fun finishOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val goal = generatedGoal
                val selectedHabits = habitSuggestions.filter { it.second }.map { it.first }
                if (goal != null && selectedHabits.isNotEmpty()) {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    selectedHabits.forEach { item ->
                        habitRepository.insertHabit(Habit(
                            id = Uuid.random().toString(),
                            title = item.title,
                            description = item.description,
                            category = goal.category,
                            frequency = item.frequency,
                            linkedGoalId = goal.id,
                            createdAt = now
                        ))
                    }
                }
                clearInProgressState()
                settings.putBoolean(COACH_ONBOARDING_KEY, true)
                scheduleOnboardingReminders()
                Analytics.onboardingCompleted()
                onDone()
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggleHabitSuggestion(index: Int) {
        habitSuggestions = habitSuggestions.mapIndexed { i, pair ->
            if (i == index) pair.copy(second = !pair.second) else pair
        }
    }

    // ── Mind analysis flow ────────────────────────────────────────────────────

    fun skipMindDump() {
        Analytics.onboardingSkipped(OnboardingPhase.MIND_DUMP.stepName())
        moveTo(OnboardingPhase.COMPLETE)
    }

    fun startMindAnalysis() {
        if (mindDump.isBlank()) { skipMindDump(); return }
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val questions = generateClarifyingQuestions()
                analysisQuestions = questions
                analysisAnswers = List(questions.size) { "" }
                currentQuestionIndex = 0
                moveTo(OnboardingPhase.MIND_QUESTIONS)
            } catch (e: Exception) {
                Logger.e("CoachOnboarding", e) { "Failed to generate questions: ${e.message}" }
                moveTo(OnboardingPhase.COMPLETE)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun answerCurrentQuestion(answer: String) {
        val newAnswers = analysisAnswers.toMutableList()
        if (currentQuestionIndex < newAnswers.size) newAnswers[currentQuestionIndex] = answer
        analysisAnswers = newAnswers.toList()
        if (currentQuestionIndex < analysisQuestions.size - 1) {
            currentQuestionIndex++
        } else {
            generateInterpretationAsync()
        }
    }

    private fun generateInterpretationAsync() {
        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val qa = analysisQuestions.zip(analysisAnswers)
                    .filter { (_, a) -> a.isNotBlank() }
                    .joinToString("\n") { (q, a) -> "Q: $q\nA: $a" }
                goalInterpretation = GoalInterpretation(
                    summary = qa.ifBlank { mindDump },
                    goalTitle = "",
                    goalDescription = "",
                    category = topPriorities.firstOrNull() ?: GoalCategory.PURPOSE
                )
                moveTo(OnboardingPhase.MIND_VALIDATION)
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun confirmInterpretation(onDone: () -> Unit) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val userId = userRepository.getCurrentUser()?.id
                if (userId != null) userSituationRepository.upsert(userId, buildSituation())
                seedWheelFromRatings()
                val enrichedDump = buildString {
                    append(mindDump.trim())
                    val qa = analysisQuestions.zip(analysisAnswers).filter { (_, a) -> a.isNotBlank() }
                    if (qa.isNotEmpty()) {
                        append("\n\nAdditional context:\n")
                        qa.forEach { (q, a) -> append("Q: $q\nA: $a\n") }
                    }
                }
                val savedDump = mindDump
                mindDump = enrichedDump
                val goal = generateFirstGoal()
                mindDump = savedDump
                if (goal != null) {
                    generatedGoal = goal
                    habitSuggestions = buildHabitSuggestions(goal.category)
                    moveTo(OnboardingPhase.GOAL_PREVIEW)
                } else {
                    clearInProgressState()
                    settings.putBoolean(COACH_ONBOARDING_KEY, true)
                    scheduleOnboardingReminders()
                    Analytics.onboardingCompleted()
                    onDone()
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun rejectInterpretation() {
        currentQuestionIndex = 0
        analysisAnswers = List(analysisQuestions.size) { "" }
        goalInterpretation = null
        // Rejecting the interpretation is a backwards move, not progress, so it reports as one.
        Analytics.onboardingStepBack(
            OnboardingPhase.MIND_VALIDATION.stepName(),
            OnboardingPhase.MIND_VALIDATION.ordinal
        )
        _phase.value = OnboardingPhase.MIND_QUESTIONS
        saveToSettings(OnboardingPhase.MIND_QUESTIONS)
    }

    private suspend fun generateClarifyingQuestions(): List<String> {
        val priorityContext = topPriorities.joinToString(", ") { it.name }
        val prompt = """
            A user starting their life planning journey shared this:
            "${mindDump.trim()}"

            Their priority areas: ${priorityContext.ifBlank { "not specified" }}
            Name: ${userName.takeIf { it.isNotBlank() } ?: "unknown"}

            Generate exactly 2 short, warm, conversational follow-up questions to understand their specific goals better.
            Make each question specific to what they said, not generic.
            Keep questions under 15 words each.
        """.trimIndent()
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("questions") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
            }
            putJsonArray("required") { add(JsonPrimitive("questions")) }
        }
        val response = aiProxyService.generateStructuredJson(prompt, schema)
        val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(response).jsonObject
        return parsed["questions"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf { s -> s.isNotBlank() } }
            ?.take(3)
            ?: emptyList()
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun generateFirstGoal(): Goal? {
        return try {
            val prompt = """
                A new user just started their life planning journey and shared this thought:
                "${mindDump.trim()}"

                User context, name: ${userName.takeIf { it.isNotBlank() } ?: "unknown"}, priority area: ${topPriority?.name ?: "general"}

                Generate a single specific, achievable goal directly based on what they shared.
                The title should be concise and motivating (max 60 chars).
                The description should be 1-2 sentences explaining the goal clearly.
                The category must be one of: CAREER, MONEY, BODY, PEOPLE, WELLBEING, PURPOSE, FAMILY
                Generate 3 concrete, actionable milestones for this goal. Each milestone title should be short (max 50 chars), specific, and represent a meaningful step toward the goal.
            """.trimIndent()

            val schema = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("title") { put("type", "string") }
                    putJsonObject("description") { put("type", "string") }
                    putJsonObject("category") { put("type", "string") }
                    putJsonObject("milestones") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") { putJsonObject("title") { put("type", "string") } }
                        }
                    }
                }
                putJsonArray("required") {
                    add(JsonPrimitive("title"))
                    add(JsonPrimitive("description"))
                    add(JsonPrimitive("category"))
                    add(JsonPrimitive("milestones"))
                }
            }

            val response = aiProxyService.generateStructuredJson(prompt, schema)
            val parsed = Json { ignoreUnknownKeys = true }.parseToJsonElement(response).jsonObject
            val title = parsed["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: mindDump.take(80)
            val description = parsed["description"]?.jsonPrimitive?.contentOrNull ?: ""
            val category = parsed["category"]?.jsonPrimitive?.contentOrNull
                ?.let { runCatching { GoalCategory.valueOf(it) }.getOrNull() }
                ?: topPriority ?: GoalCategory.PURPOSE

            val milestones = parsed["milestones"]?.jsonArray?.mapNotNull { el ->
                el.jsonObject["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            }?.take(5)?.map { milestoneTitle ->
                Milestone(id = Uuid.random().toString(), title = milestoneTitle)
            } ?: emptyList()
            val finalMilestones = if (milestones.isEmpty()) {
                listOf(
                    Milestone(id = Uuid.random().toString(), title = "Define clear success criteria"),
                    Milestone(id = Uuid.random().toString(), title = "Complete first action step"),
                    Milestone(id = Uuid.random().toString(), title = "Reach 50% of the goal")
                )
            } else milestones

            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            val goal = Goal(
                id = Uuid.random().toString(),
                title = title,
                description = description,
                category = category,
                status = GoalStatus.NOT_STARTED,
                timeline = GoalTimeline.MID_TERM,
                dueDate = now.date.plus(90, DateTimeUnit.DAY),
                createdAt = now,
                milestones = finalMilestones,
                aiReasoning = "Created from your first thought during onboarding"
            )
            goalRepository.insertGoal(goal)
            Analytics.goalCreated(category.name, "onboarding_mind_dump", hasAiGenerated = true)
            goal
        } catch (e: Exception) {
            Logger.e("CoachOnboarding", e) { "Failed to generate first goal: ${e.message}" }
            null
        }
    }

    private fun buildSituation(): UserSituation {
        val age = userAge ?: 0
        val lifeStage = when {
            age in 13..17 -> LifeStage.STUDENT
            age in 18..22 -> LifeStage.EARLY_CAREER
            age in 23..35 -> LifeStage.EARLY_CAREER
            age in 36..55 -> LifeStage.MID_CAREER
            age > 55 -> LifeStage.SENIOR
            else -> null
        }
        val meta = MetaSlice(
            name = userName.takeIf { it.isNotBlank() },
            age = userAge,
            lifeStage = lifeStage,
            topPriority = topPriorities.firstOrNull() ?: topPriority,
            stressLevel = stressLevel,
            sleepQuality = sleepQuality,
            confidence = 0.7f
        )
        val career = if (specialistCoachId == "alex_career") CareerSlice(
            status = employmentStatus,
            role = jobRole.takeIf { it.isNotBlank() },
            yearsExperience = yearsExperience,
            careerGoal = careerGoal.takeIf { it.isNotBlank() },
            confidence = if (employmentStatus != null) 0.7f else 0f
        ) else CareerSlice()
        val money = if (specialistCoachId == "morgan_finance") MoneySlice(
            incomeBand = incomeBand,
            savingsHabit = savingsHabit,
            hasDebt = hasDebt,
            financialGoal = financialGoal.takeIf { it.isNotBlank() },
            confidence = if (incomeBand != null) 0.7f else 0f
        ) else MoneySlice()
        val body = if (specialistCoachId == "kai_fitness") BodySlice(
            activityLevel = activityLevel,
            sleepHours = sleepHours,
            energyRating = energyRating,
            confidence = if (activityLevel != null) 0.7f else 0f
        ) else BodySlice()
        val people = when (specialistCoachId) {
            "sam_social" -> PeopleSlice(
                socialEnergy = socialEnergy,
                closeCircleSize = closeCircleSize,
                relationshipStatus = relationshipStatus,
                confidence = if (socialEnergy != null) 0.7f else 0f
            )
            "jamie_family" -> PeopleSlice(
                familyContext = buildString {
                    if (familyRole.isNotBlank()) append("Role: $familyRole. ")
                    if (familyChallenge.isNotBlank()) append("Challenge: $familyChallenge. ")
                    if (familyVision.isNotBlank()) append("Vision: $familyVision")
                }.trim().takeIf { it.isNotBlank() },
                confidence = if (familyRole.isNotBlank() || familyChallenge.isNotBlank()) 0.7f else 0f
            )
            else -> PeopleSlice()
        }
        val purpose = if (specialistCoachId == "river_wellness") PurposeSlice(
            topValues = topValues,
            mindfulnessPractice = mindfulnessPractice,
            longTermVision = longTermVision.takeIf { it.isNotBlank() },
            confidence = if (topValues.isNotEmpty()) 0.7f else 0f
        ) else PurposeSlice()
        return UserSituation(
            meta = meta, career = career, money = money,
            body = body, people = people, purpose = purpose,
            lastUpdatedBy = "luna_general"
        )
    }

    companion object {
        const val COACH_ONBOARDING_KEY = "coach_onboarding_complete"

        private const val KEY_PHASE = "ob_phase"
        private const val KEY_USER_NAME = "ob_user_name"
        private const val KEY_USER_AGE = "ob_user_age"
        private const val KEY_TOP_PRIORITY = "ob_top_priority"
        private const val KEY_TOP_PRIORITIES = "ob_top_priorities"
        private const val KEY_STRESS = "ob_stress"
        private const val KEY_SLEEP_QUALITY = "ob_sleep_quality"
        private const val KEY_EMPLOYMENT = "ob_employment"
        private const val KEY_JOB_ROLE = "ob_job_role"
        private const val KEY_YEARS_EXP = "ob_years_exp"
        private const val KEY_CAREER_GOAL = "ob_career_goal"
        private const val KEY_INCOME_BAND = "ob_income_band"
        private const val KEY_SAVINGS_HABIT = "ob_savings_habit"
        private const val KEY_HAS_DEBT = "ob_has_debt"
        private const val KEY_FINANCIAL_GOAL = "ob_financial_goal"
        private const val KEY_ACTIVITY = "ob_activity"
        private const val KEY_SLEEP_HOURS = "ob_sleep_hours"
        private const val KEY_ENERGY = "ob_energy"
        private const val KEY_SOCIAL_ENERGY = "ob_social_energy"
        private const val KEY_CIRCLE_SIZE = "ob_circle_size"
        private const val KEY_RELATIONSHIP = "ob_relationship"
        private const val KEY_TOP_VALUES = "ob_top_values"
        private const val KEY_WHEEL_RATINGS = "ob_wheel_ratings"
        private const val KEY_MINDFULNESS = "ob_mindfulness"
        private const val KEY_VISION = "ob_vision"
        private const val KEY_MIND_DUMP = "ob_mind_dump"
        private const val KEY_FAMILY_ROLE = "ob_family_role"
        private const val KEY_FAMILY_CHALLENGE = "ob_family_challenge"
        private const val KEY_FAMILY_VISION = "ob_family_vision"

        private val inProgressKeys = listOf(
            KEY_PHASE, KEY_USER_NAME, KEY_USER_AGE, KEY_TOP_PRIORITY, KEY_TOP_PRIORITIES, KEY_STRESS, KEY_SLEEP_QUALITY,
            KEY_EMPLOYMENT, KEY_JOB_ROLE, KEY_YEARS_EXP, KEY_CAREER_GOAL, KEY_INCOME_BAND,
            KEY_SAVINGS_HABIT, KEY_HAS_DEBT, KEY_FINANCIAL_GOAL, KEY_ACTIVITY, KEY_SLEEP_HOURS,
            KEY_ENERGY, KEY_SOCIAL_ENERGY, KEY_CIRCLE_SIZE, KEY_RELATIONSHIP, KEY_TOP_VALUES,
            KEY_WHEEL_RATINGS,
            KEY_MINDFULNESS, KEY_VISION, KEY_MIND_DUMP, KEY_FAMILY_ROLE, KEY_FAMILY_CHALLENGE,
            KEY_FAMILY_VISION
        )

        fun isComplete(settings: Settings) = settings.getBoolean(COACH_ONBOARDING_KEY, false)
    }
}
