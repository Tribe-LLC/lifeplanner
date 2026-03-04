package az.tribe.lifeplanner.ui.retrospective

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.model.DaySnapshot
import az.tribe.lifeplanner.domain.model.HabitDaySummary
import az.tribe.lifeplanner.domain.repository.RetrospectiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

data class RetrospectiveUiState(
    val selectedDate: LocalDate = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date.minus(DatePeriod(days = 1)),
    val snapshot: DaySnapshot? = null,
    val todaySnapshot: DaySnapshot? = null,
    val isLoading: Boolean = false,
    val activeDates: Set<LocalDate> = emptySet(),
    val compareMode: Boolean = false,
    val error: String? = null
)

class RetrospectiveViewModel(
    private val repository: RetrospectiveRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RetrospectiveUiState())
    val uiState: StateFlow<RetrospectiveUiState> = _uiState.asStateFlow()

    private val today: LocalDate
        get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    init {
        loadSnapshot(_uiState.value.selectedDate)
        loadActiveDatesForMonth(_uiState.value.selectedDate)
    }

    fun selectDate(date: LocalDate) {
        if (date > today) return
        _uiState.update { it.copy(selectedDate = date) }
        loadSnapshot(date)
        loadActiveDatesForMonth(date)
    }

    fun goToPreviousDay() {
        val prev = _uiState.value.selectedDate.minus(DatePeriod(days = 1))
        selectDate(prev)
    }

    fun goToNextDay() {
        val next = _uiState.value.selectedDate.plus(DatePeriod(days = 1))
        if (next <= today) selectDate(next)
    }

    fun toggleCompareMode() {
        val newCompare = !_uiState.value.compareMode
        _uiState.update { it.copy(compareMode = newCompare) }
        if (newCompare && _uiState.value.todaySnapshot == null) {
            viewModelScope.launch {
                val todaySnap = repository.getDaySnapshot(today)
                _uiState.update { it.copy(todaySnapshot = todaySnap) }
            }
        }
    }

    fun loadActiveDatesForMonth(date: LocalDate) {
        viewModelScope.launch {
            val monthStart = LocalDate(date.year, date.monthNumber, 1)
            val monthEnd = if (date.monthNumber == 12) {
                LocalDate(date.year + 1, 1, 1).minus(DatePeriod(days = 1))
            } else {
                LocalDate(date.year, date.monthNumber + 1, 1).minus(DatePeriod(days = 1))
            }
            val dates = repository.getDatesWithActivity(monthStart, monthEnd)
            _uiState.update { it.copy(activeDates = dates) }
        }
    }

    private fun loadSnapshot(date: LocalDate) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val snapshot = repository.getDaySnapshot(date)
                _uiState.update { it.copy(snapshot = snapshot, isLoading = false) }
            } catch (e: Exception) {
                Logger.e("RetrospectiveViewModel") { "loadSnapshot failed: ${e.message}" }
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
