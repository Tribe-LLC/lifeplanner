package az.tribe.lifeplanner.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.mapper.createNewJournalEntry
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.Decision
import az.tribe.lifeplanner.domain.model.DecisionSource
import az.tribe.lifeplanner.domain.model.DecisionStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.model.JournalPrompts
import az.tribe.lifeplanner.domain.model.XpRewards
import az.tribe.lifeplanner.domain.repository.DecisionRepository
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.usecases.journal.CreateJournalEntryUseCase
import az.tribe.lifeplanner.usecases.journal.DeleteJournalEntryUseCase
import az.tribe.lifeplanner.usecases.journal.UpdateJournalEntryUseCase
import co.touchlab.kermit.Logger
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.number

class JournalViewModel(
    private val journalRepository: JournalRepository,
    private val createEntryUseCase: CreateJournalEntryUseCase,
    private val updateEntryUseCase: UpdateJournalEntryUseCase,
    private val deleteEntryUseCase: DeleteJournalEntryUseCase,
    private val aiProxyService: AiProxyService,
    private val gamificationRepository: GamificationRepository,
    private val decisionRepository: DecisionRepository,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val entries: StateFlow<List<JournalEntry>> = journalRepository.observeAllEntries()
        .onEach { _isLoading.value = false }
        .catch { e ->
            _error.value = "Failed to load journal entries: ${e.message}"
            _isLoading.value = false
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showNewEntryDialog = MutableStateFlow(false)
    val showNewEntryDialog: StateFlow<Boolean> = _showNewEntryDialog.asStateFlow()

    private val _currentPrompt = MutableStateFlow(JournalPrompts.getRandomPrompt())
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()


    fun createEntry(
        title: String,
        content: String,
        mood: Mood,
        linkedGoalId: String? = null,
        linkedHabitId: String? = null,
        tags: List<String> = emptyList(),
        promptUsed: String? = null,
        detectedDecision: DetectedDecision? = null,
        /**
         * The day the entry belongs to. The hub keeps a day lens, so writing while looking at
         * Tuesday has to file the entry under Tuesday. Null means today.
         */
        date: LocalDate? = null,
    ) {
        viewModelScope.launch {
            try {
                val entry = createNewJournalEntry(
                    title = title,
                    content = content,
                    mood = mood,
                    linkedGoalId = linkedGoalId,
                    linkedHabitId = linkedHabitId,
                    promptUsed = promptUsed,
                    tags = tags,
                    date = date ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                )
                createEntryUseCase(entry)
                gamificationRepository.awardXp(XpRewards.JOURNAL_ENTRY.toLong())
                // Fall back to the last AI result so both entry-creation flows capture a decision.
                logDetectedDecision(detectedDecision ?: _aiResult.value?.detectedDecision, linkedGoalId)
                _showNewEntryDialog.value = false
                refreshPrompt()
            } catch (e: Exception) {
                _error.value = "Failed to create entry: ${e.message}"
            }
        }
    }

    /**
     * Persist an AI-detected decision as a PENDING/JOURNAL [Decision]. It is surfaced later as a
     * gentle "want to log this?" nudge in the Decision Journal, not confirmed here. Best-effort:
     * a failure must never fail the journal save.
     */
    @OptIn(ExperimentalUuidApi::class)
    private suspend fun logDetectedDecision(detected: DetectedDecision?, linkedGoalId: String?) {
        val decision = detected ?: return
        if (decision.question.isBlank()) return
        try {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            decisionRepository.insertDecision(
                Decision(
                    id = Uuid.random().toString(),
                    question = decision.question,
                    optionsConsidered = decision.optionsConsidered,
                    chosenOption = decision.leaning,   // the AI's guess, editable on confirm
                    reasoning = decision.reasoning,
                    relatedGoalId = linkedGoalId,
                    decidedAt = now,
                    source = DecisionSource.JOURNAL,
                    status = DecisionStatus.PENDING,
                )
            )
        } catch (e: Exception) {
            Logger.w("JournalViewModel") { "Logging detected decision failed: ${e.message}" }
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            try {
                deleteEntryUseCase(id)
            } catch (e: Exception) {
                _error.value = "Failed to delete entry: ${e.message}"
            }
        }
    }

    fun showNewEntryDialog() {
        _showNewEntryDialog.value = true
    }

    fun hideNewEntryDialog() {
        _showNewEntryDialog.value = false
    }


    fun refreshPrompt() {
        _currentPrompt.value = JournalPrompts.getRandomPrompt()
    }


    fun clearError() {
        _error.value = null
    }

    fun getEntriesForToday(): List<JournalEntry> {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return entries.value.filter { it.date == today }
    }

    fun getStreakDays(): Int {
        val sortedEntries = entries.value.sortedByDescending { it.date }
        if (sortedEntries.isEmpty()) return 0

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        var streak = 0
        var currentDate = today

        for (entry in sortedEntries.distinctBy { it.date }) {
            if (entry.date == currentDate) {
                streak++
                currentDate = LocalDate(currentDate.year, currentDate.month.number, currentDate.day - 1)
            } else {
                break
            }
        }

        return streak
    }

    fun getEntryById(id: String): JournalEntry? {
        return entries.value.find { it.id == id }
    }

    fun updateEntry(
        id: String,
        title: String,
        content: String,
        mood: Mood,
        tags: List<String>
    ) {
        viewModelScope.launch {
            try {
                val existingEntry = entries.value.find { it.id == id }
                if (existingEntry != null) {
                    val updatedEntry = existingEntry.copy(
                        title = title,
                        content = content,
                        mood = mood,
                        tags = tags,
                        updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    )
                    updateEntryUseCase(updatedEntry)
                }
            } catch (e: Exception) {
                _error.value = "Failed to update entry: ${e.message}"
            }
        }
    }


    // ── AI generation ─────────────────────────────────────────────────────────

    private val _isGeneratingAi = MutableStateFlow(false)
    val isGeneratingAi: StateFlow<Boolean> = _isGeneratingAi.asStateFlow()

    private val _aiResult = MutableStateFlow<AiJournalResult?>(null)
    val aiResult: StateFlow<AiJournalResult?> = _aiResult.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    fun generateJournalEntry(
        mood: Mood,
        prompt: String,
        userNote: String,
        linkedGoal: Goal?,
        linkedHabit: Habit?,
    ) {
        viewModelScope.launch {
            _isGeneratingAi.value = true
            _aiError.value = null
            try {
                val result = generateAiJournalEntry(
                    aiProxy = aiProxyService,
                    mood = mood,
                    prompt = prompt,
                    userNote = userNote,
                    linkedGoal = linkedGoal,
                    linkedHabit = linkedHabit,
                )
                if (result != null) {
                    _aiResult.value = result
                } else {
                    _aiError.value = "AI generation returned no result. Please try again."
                }
            } catch (e: Exception) {
                Logger.e("JournalViewModel") { "AI journal generation failed: ${e.message}" }
                _aiError.value = when {
                    e.message?.contains("timeout", ignoreCase = true) == true ||
                    e.message?.contains("connect", ignoreCase = true) == true ||
                    e.message?.contains("network", ignoreCase = true) == true ->
                        "No internet connection. Check your network and try again."
                    e.message?.contains("authenticated", ignoreCase = true) == true ||
                    e.message?.contains("sign in", ignoreCase = true) == true ->
                        "Session expired. Please sign in again."
                    else -> "AI generation failed. Please try again."
                }
            } finally {
                _isGeneratingAi.value = false
            }
        }
    }

    fun clearAiResult() { _aiResult.value = null }
    fun clearAiError() { _aiError.value = null }
}
