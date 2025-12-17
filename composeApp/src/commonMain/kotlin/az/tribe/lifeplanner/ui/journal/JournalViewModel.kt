package az.tribe.lifeplanner.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.mapper.createNewJournalEntry
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.model.JournalPrompts
import az.tribe.lifeplanner.usecases.journal.CreateJournalEntryUseCase
import az.tribe.lifeplanner.usecases.journal.DeleteJournalEntryUseCase
import az.tribe.lifeplanner.usecases.journal.GetAllJournalEntriesUseCase
import az.tribe.lifeplanner.usecases.journal.GetRecentJournalEntriesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class JournalViewModel(
    private val getAllEntriesUseCase: GetAllJournalEntriesUseCase,
    private val createEntryUseCase: CreateJournalEntryUseCase,
    private val deleteEntryUseCase: DeleteJournalEntryUseCase,
    private val getRecentEntriesUseCase: GetRecentJournalEntriesUseCase
) : ViewModel() {

    private val _entries = MutableStateFlow<List<JournalEntry>>(emptyList())
    val entries: StateFlow<List<JournalEntry>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _showNewEntryDialog = MutableStateFlow(false)
    val showNewEntryDialog: StateFlow<Boolean> = _showNewEntryDialog.asStateFlow()

    private val _currentPrompt = MutableStateFlow(JournalPrompts.getRandomPrompt())
    val currentPrompt: StateFlow<String> = _currentPrompt.asStateFlow()

    private val _selectedMood = MutableStateFlow(Mood.NEUTRAL)
    val selectedMood: StateFlow<Mood> = _selectedMood.asStateFlow()

    init {
        loadEntries()
    }

    fun loadEntries() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _entries.value = getAllEntriesUseCase()
            } catch (e: Exception) {
                _error.value = "Failed to load journal entries: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun createEntry(
        title: String,
        content: String,
        mood: Mood,
        linkedGoalId: String? = null,
        tags: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val entry = createNewJournalEntry(
                    title = title,
                    content = content,
                    mood = mood,
                    linkedGoalId = linkedGoalId,
                    promptUsed = _currentPrompt.value,
                    tags = tags
                )
                createEntryUseCase(entry)
                loadEntries()
                _showNewEntryDialog.value = false
                refreshPrompt()
            } catch (e: Exception) {
                _error.value = "Failed to create entry: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            try {
                deleteEntryUseCase(id)
                loadEntries()
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
        return _entries.value.filter { it.date == today }
    }

    fun getStreakDays(): Int {
        val entries = _entries.value.sortedByDescending { it.date }
        if (entries.isEmpty()) return 0

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        var streak = 0
        var currentDate = today

        for (entry in entries.distinctBy { it.date }) {
            if (entry.date == currentDate) {
                streak++
                currentDate = LocalDate(currentDate.year, currentDate.monthNumber, currentDate.dayOfMonth - 1)
            } else {
                break
            }
        }

        return streak
    }
}
