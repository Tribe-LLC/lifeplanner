package az.tribe.lifeplanner.ui.habit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.mapper.createNewHabit
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.enum.HabitType
import az.tribe.lifeplanner.domain.service.SmartReminderManager
import az.tribe.lifeplanner.usecases.habit.CreateHabitUseCase
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class GeneratedHabit(
    val title: String,
    val description: String,
    val category: GoalCategory,
    val frequency: HabitFrequency,
    val type: HabitType,
    val emoji: String
)

enum class HabitGeneratorStep {
    SCENARIO_SELECT, CUSTOM_INPUT, GENERATING, RESULTS
}

class SmartHabitGeneratorViewModel(
    private val aiProxyService: AiProxyService,
    private val createHabitUseCase: CreateHabitUseCase,
    private val smartReminderManager: SmartReminderManager
) : ViewModel() {

    private val _step = MutableStateFlow(HabitGeneratorStep.SCENARIO_SELECT)
    val step: StateFlow<HabitGeneratorStep> = _step.asStateFlow()

    private val _generatedHabits = MutableStateFlow<List<GeneratedHabit>>(emptyList())
    val generatedHabits: StateFlow<List<GeneratedHabit>> = _generatedHabits.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _addedTitles = MutableStateFlow<Set<String>>(emptySet())
    val addedTitles: StateFlow<Set<String>> = _addedTitles.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun generateHabits(prompt: String) {
        viewModelScope.launch {
            _step.value = HabitGeneratorStep.GENERATING
            _error.value = null
            try {
                val systemPrompt = """
                    You are a habit formation expert. Generate 5-6 practical, specific habits for the user's goal.
                    Return ONLY a valid JSON array with no extra text, markdown, or explanation:
                    [
                      {
                        "title": "Short action-oriented habit title",
                        "description": "One sentence explaining the habit",
                        "category": "BODY|CAREER|MONEY|PEOPLE|WELLBEING|PURPOSE",
                        "frequency": "DAILY|WEEKDAYS|WEEKLY",
                        "type": "BUILD|BREAK_BAD_HABIT",
                        "emoji": "single emoji"
                      }
                    ]
                    Make habits specific, achievable, and directly tied to the goal.
                """.trimIndent()

                val response = aiProxyService.generateText(
                    prompt = "Create habits for this goal: $prompt",
                    systemPrompt = systemPrompt
                )

                val habits = parseHabits(response)
                if (habits.isEmpty()) {
                    _error.value = "Couldn't generate habits. Try rephrasing your goal."
                    _step.value = HabitGeneratorStep.SCENARIO_SELECT
                } else {
                    _generatedHabits.value = habits
                    _step.value = HabitGeneratorStep.RESULTS
                }
            } catch (e: Exception) {
                Logger.e("SmartHabitGenerator") { "Generation failed: ${e.message}" }
                _error.value = "Generation failed. Check your connection and try again."
                _step.value = HabitGeneratorStep.SCENARIO_SELECT
            }
        }
    }

    fun addHabit(habit: GeneratedHabit) {
        viewModelScope.launch {
            try {
                val newHabit = createNewHabit(
                    title = habit.title,
                    description = habit.description,
                    category = habit.category,
                    frequency = habit.frequency,
                    type = habit.type
                )
                createHabitUseCase(newHabit)
                smartReminderManager.syncRemindersForHabit(newHabit)
                _addedTitles.value = _addedTitles.value + habit.title
            } catch (e: Exception) {
                _error.value = "Failed to add habit: ${e.message}"
            }
        }
    }

    fun addAllHabits() {
        viewModelScope.launch {
            _generatedHabits.value
                .filter { it.title !in _addedTitles.value }
                .forEach { addHabit(it) }
        }
    }

    fun navigateToCustomInput() {
        _step.value = HabitGeneratorStep.CUSTOM_INPUT
    }

    fun reset() {
        _step.value = HabitGeneratorStep.SCENARIO_SELECT
        _generatedHabits.value = emptyList()
        _error.value = null
        _addedTitles.value = emptySet()
    }

    private fun parseHabits(response: String): List<GeneratedHabit> {
        return try {
            val cleaned = response.replace("```json", "").replace("```", "").trim()
            val start = cleaned.indexOf('[')
            val end = cleaned.lastIndexOf(']')
            if (start == -1 || end == -1) return emptyList()
            val jsonStr = cleaned.substring(start, end + 1)
            json.decodeFromString<List<GeneratedHabitDto>>(jsonStr).map { dto ->
                GeneratedHabit(
                    title = dto.title,
                    description = dto.description,
                    category = runCatching { GoalCategory.valueOf(dto.category.uppercase()) }.getOrDefault(GoalCategory.WELLBEING),
                    frequency = runCatching { HabitFrequency.valueOf(dto.frequency.uppercase()) }.getOrDefault(HabitFrequency.DAILY),
                    type = runCatching { HabitType.valueOf(dto.type.uppercase()) }.getOrDefault(HabitType.BUILD),
                    emoji = dto.emoji
                )
            }
        } catch (e: Exception) {
            Logger.e("SmartHabitGenerator") { "JSON parse error: ${e.message}" }
            emptyList()
        }
    }
}

@Serializable
private data class GeneratedHabitDto(
    val title: String,
    val description: String,
    val category: String,
    val frequency: String,
    val type: String,
    val emoji: String
)
