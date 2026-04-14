package az.tribe.lifeplanner.ui.ability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.model.Ability
import az.tribe.lifeplanner.domain.model.AbilityHabitLink
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.repository.AbilityRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AbilityDetailViewModel(
    private val abilityId: String,
    private val abilityRepository: AbilityRepository,
    private val habitRepository: HabitRepository,
    private val aiProxyService: AiProxyService
) : ViewModel() {

    private val _ability = MutableStateFlow<Ability?>(null)
    val ability: StateFlow<Ability?> = _ability.asStateFlow()

    private val _linkedHabits = MutableStateFlow<List<Pair<Habit, AbilityHabitLink>>>(emptyList())
    val linkedHabits: StateFlow<List<Pair<Habit, AbilityHabitLink>>> = _linkedHabits.asStateFlow()

    private val _allHabitsForLinking = MutableStateFlow<List<Habit>>(emptyList())
    val allHabitsForLinking: StateFlow<List<Habit>> = _allHabitsForLinking.asStateFlow()

    private val _supervisionInsight = MutableStateFlow("")
    val supervisionInsight: StateFlow<String> = _supervisionInsight.asStateFlow()

    private val _isGeneratingInsight = MutableStateFlow(false)
    val isGeneratingInsight: StateFlow<Boolean> = _isGeneratingInsight.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _ability.value = abilityRepository.getAbilityById(abilityId)
            refreshLinkedHabits()
        }
    }

    private suspend fun refreshLinkedHabits() {
        val links = abilityRepository.getLinksForAbility(abilityId)
        val linkedIds = links.map { it.habitId }.toSet()
        val allHabits = habitRepository.observeHabitsWithTodayStatus().first()
            .map { it.first }
        val linked = allHabits.filter { it.id in linkedIds }.map { habit ->
            habit to links.first { it.habitId == habit.id }
        }
        _linkedHabits.value = linked
        _allHabitsForLinking.value = allHabits.filter { it.id !in linkedIds }
    }

    fun linkHabit(habitId: String) {
        viewModelScope.launch {
            abilityRepository.linkHabit(abilityId, habitId)
            refreshLinkedHabits()
        }
    }

    fun unlinkHabit(habitId: String) {
        viewModelScope.launch {
            abilityRepository.unlinkHabit(abilityId, habitId)
            refreshLinkedHabits()
        }
    }

    fun generateSupervisionInsight() {
        val ab = _ability.value ?: return
        val habits = _linkedHabits.value
        if (habits.isEmpty()) return

        viewModelScope.launch {
            _isGeneratingInsight.value = true
            _supervisionInsight.value = ""

            val habitList = habits.joinToString("\n") { (habit, _) ->
                "- ${habit.title} (${habit.type.displayName}, streak: ${habit.currentStreak})"
            }

            val prompt = """
Ability: ${ab.title}, Level ${ab.currentLevel}, ${ab.totalXp} XP
Linked habits:
$habitList

Give 2-3 sentences of actionable coaching insight to help build this ability faster.
""".trimIndent()

            val messages = listOf(AiProxyService.ChatMessage(role = "user", content = prompt))

            try {
                aiProxyService.chatStream(messages).collect { event ->
                    when (event) {
                        is AiProxyService.StreamEvent.TextChunk -> _supervisionInsight.value += event.text
                        is AiProxyService.StreamEvent.Done -> _isGeneratingInsight.value = false
                        is AiProxyService.StreamEvent.Error -> _isGeneratingInsight.value = false
                    }
                }
            } catch (_: Exception) {
                _isGeneratingInsight.value = false
            }
        }
    }

    fun refreshAbility() {
        viewModelScope.launch {
            _ability.value = abilityRepository.getAbilityById(abilityId)
        }
    }
}
