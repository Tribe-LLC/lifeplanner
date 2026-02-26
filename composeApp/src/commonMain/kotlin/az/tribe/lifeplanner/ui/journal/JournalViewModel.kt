package az.tribe.lifeplanner.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.mapper.createNewJournalEntry
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.model.JournalPrompts
import az.tribe.lifeplanner.domain.model.XpRewards
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.usecases.journal.CreateJournalEntryUseCase
import az.tribe.lifeplanner.usecases.journal.DeleteJournalEntryUseCase
import az.tribe.lifeplanner.usecases.journal.UpdateJournalEntryUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class JournalViewModel(
    private val journalRepository: JournalRepository,
    private val createEntryUseCase: CreateJournalEntryUseCase,
    private val updateEntryUseCase: UpdateJournalEntryUseCase,
    private val deleteEntryUseCase: DeleteJournalEntryUseCase,
    private val gamificationRepository: GamificationRepository
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

    private val _selectedMood = MutableStateFlow(Mood.NEUTRAL)
    val selectedMood: StateFlow<Mood> = _selectedMood.asStateFlow()

    // Calendar state
    private val _selectedMonth = MutableStateFlow(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    )
    val selectedMonth: StateFlow<LocalDate> = _selectedMonth.asStateFlow()

    private val _selectedDay = MutableStateFlow<LocalDate?>(null)
    val selectedDay: StateFlow<LocalDate?> = _selectedDay.asStateFlow()

    // No-op: data is now reactively observed via journalRepository.observeAllEntries()
    fun loadEntries() { }

    fun createEntry(
        title: String,
        content: String,
        mood: Mood,
        linkedGoalId: String? = null,
        linkedHabitId: String? = null,
        tags: List<String> = emptyList(),
        promptUsed: String? = null
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
                    tags = tags
                )
                createEntryUseCase(entry)
                _showNewEntryDialog.value = false
                refreshPrompt()

                // Update gamification: XP and challenges
                gamificationRepository.incrementJournalEntries()
                gamificationRepository.addXp(XpRewards.JOURNAL_ENTRY)
                gamificationRepository.onJournalEntryCreated()
                gamificationRepository.checkAndAwardBadges()
            } catch (e: Exception) {
                _error.value = "Failed to create entry: ${e.message}"
            }
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

    fun setSelectedMood(mood: Mood) {
        _selectedMood.value = mood
    }

    fun refreshPrompt() {
        _currentPrompt.value = JournalPrompts.getRandomPrompt()
    }

    fun getPromptsForCurrentMood(): List<String> {
        return JournalPrompts.getPromptsForMood(_selectedMood.value)
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
                currentDate = LocalDate(currentDate.year, currentDate.monthNumber, currentDate.dayOfMonth - 1)
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

    // Calendar functions
    fun setSelectedMonth(date: LocalDate) {
        _selectedMonth.value = date
    }

    fun selectDay(date: LocalDate) {
        _selectedDay.value = date
    }

    fun clearSelectedDay() {
        _selectedDay.value = null
    }

    fun getEntriesForDay(date: LocalDate): List<JournalEntry> {
        return entries.value.filter { it.date == date }
    }
}
