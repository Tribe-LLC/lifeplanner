package az.tribe.lifeplanner.ui.balance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.Analytics
import co.touchlab.kermit.Logger
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.BalanceInsight
import az.tribe.lifeplanner.domain.model.BalanceRecommendation
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.LifeArea
import az.tribe.lifeplanner.domain.model.LifeAreaScore
import az.tribe.lifeplanner.domain.model.LifeBalanceReport
import az.tribe.lifeplanner.domain.model.ManualAssessment
import az.tribe.lifeplanner.domain.model.toGoalCategory
import az.tribe.lifeplanner.domain.repository.GoalRepository
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
    val error: String? = null,
    val isPreGenerating: Boolean = false,
    val goalCreatedFeedback: String? = null,
    val createdGoalIds: Set<String> = emptySet(),
    val showCoachSheet: Boolean = false,
    val selectedInsight: BalanceInsight? = null,
    val relevantCoaches: List<CoachPersona> = emptyList()
)

class LifeBalanceViewModel(
    private val repository: LifeBalanceRepository,
    private val goalRepository: GoalRepository
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
                Analytics.lifeBalanceChecked(report.overallScore.toFloat())
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    report = report
                )
                // Kick off pre-generation in background
                preGenerateGoals(report)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to calculate balance: ${e.message}"
                )
            }
        }
    }

    private fun preGenerateGoals(report: LifeBalanceReport) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPreGenerating = true)
            try {
                val updatedRecs = repository.preGenerateGoalsForRecommendations(
                    report.recommendations,
                    report.areaScores
                )
                val updatedReport = report.copy(recommendations = updatedRecs)
                _uiState.value = _uiState.value.copy(
                    report = updatedReport,
                    isPreGenerating = false
                )
            } catch (e: Exception) {
                Logger.e("LifeBalanceViewModel") { "preGenerateGoals failed: ${e.message}" }
                _uiState.value = _uiState.value.copy(isPreGenerating = false)
            }
        }
    }

    fun createGoalFromRecommendation(recommendation: BalanceRecommendation) {
        val goal = recommendation.preGeneratedGoal ?: return
        viewModelScope.launch {
            try {
                goalRepository.insertGoal(goal)
                _uiState.value = _uiState.value.copy(
                    goalCreatedFeedback = "Goal \"${goal.title}\" added!",
                    createdGoalIds = _uiState.value.createdGoalIds + recommendation.targetArea.name
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    goalCreatedFeedback = "Failed to create goal: ${e.message}"
                )
            }
        }
    }

    fun clearGoalFeedback() {
        _uiState.value = _uiState.value.copy(goalCreatedFeedback = null)
    }

    fun showCoachSheetForInsight(insight: BalanceInsight) {
        val coaches = if (insight.relatedAreas.isNotEmpty()) {
            val relevantCategories = insight.relatedAreas.map { it.toGoalCategory() }.toSet()
            val matched = relevantCategories.map { CoachPersona.getByCategory(it) }.toSet()
            // Always include Luna (general)
            val luna = CoachPersona.getGeneral()
            (matched + luna).toList()
        } else {
            CoachPersona.ALL_COACHES
        }

        _uiState.value = _uiState.value.copy(
            showCoachSheet = true,
            selectedInsight = insight,
            relevantCoaches = coaches
        )
    }

    fun hideCoachSheet() {
        _uiState.value = _uiState.value.copy(
            showCoachSheet = false,
            selectedInsight = null,
            relevantCoaches = emptyList()
        )
    }

    fun buildInsightMessage(insight: BalanceInsight): String {
        val areasText = if (insight.relatedAreas.isNotEmpty()) {
            " Related areas: ${insight.relatedAreas.joinToString(", ") { it.displayName }}."
        } else ""
        return "I'd like your advice on this insight from my Life Balance assessment: " +
                "**${insight.title}** — ${insight.description}$areasText What steps do you recommend?"
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
