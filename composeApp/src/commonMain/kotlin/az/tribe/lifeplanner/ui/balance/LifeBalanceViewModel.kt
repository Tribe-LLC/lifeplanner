package az.tribe.lifeplanner.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.model.LifeArea
import az.tribe.lifeplanner.domain.model.LifeAreaScore
import az.tribe.lifeplanner.domain.model.LifeBalanceReport
import az.tribe.lifeplanner.domain.model.ManualAssessment
import az.tribe.lifeplanner.domain.repository.LifeBalanceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class LifeBalanceUiState(
    val isLoading: Boolean = false,
    val report: LifeBalanceReport? = null,
    val selectedArea: LifeArea? = null,
    val showAssessmentDialog: Boolean = false,
    val assessmentArea: LifeArea? = null,
    val error: String? = null
)

class LifeBalanceViewModel(
    private val repository: LifeBalanceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LifeBalanceUiState())
    val uiState: StateFlow<LifeBalanceUiState> = _uiState.asStateFlow()

    init {
        loadBalance()
    }

    fun loadBalance() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val report = repository.calculateCurrentBalance()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    report = report
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to calculate balance: ${e.message}"
                )
            }
        }
    }

    fun selectArea(area: LifeArea?) {
        _uiState.value = _uiState.value.copy(selectedArea = area)
    }

    fun showAssessmentDialog(area: LifeArea) {
        _uiState.value = _uiState.value.copy(
            showAssessmentDialog = true,
            assessmentArea = area
        )
    }

    fun hideAssessmentDialog() {
        _uiState.value = _uiState.value.copy(
            showAssessmentDialog = false,
            assessmentArea = null
        )
    }

    fun saveManualAssessment(area: LifeArea, score: Int, notes: String?) {
        viewModelScope.launch {
            val assessment = ManualAssessment(
                area = area,
                score = score,
                notes = notes,
                assessedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            )
            repository.saveManualAssessment(assessment)
            hideAssessmentDialog()
            loadBalance() // Recalculate with new assessment
        }
    }

    fun getAreaScore(area: LifeArea): LifeAreaScore? {
        return _uiState.value.report?.areaScores?.find { it.area == area }
    }

    fun saveCurrentReport() {
        viewModelScope.launch {
            _uiState.value.report?.let { report ->
                repository.saveBalanceReport(report)
            }
        }
    }
}
