package az.tribe.lifeplanner.ui.dependency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.enum.DependencyType
import az.tribe.lifeplanner.domain.model.DependencyGraph
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.GoalDependency
import az.tribe.lifeplanner.domain.repository.GoalDependencyRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GoalDependencyUiState(
    val isLoading: Boolean = false,
    val dependencyGraph: DependencyGraph = DependencyGraph(emptyList(), emptyList()),
    val allGoals: List<Goal> = emptyList(),
    val selectedGoal: Goal? = null,
    val selectedGoalDependencies: List<GoalDependency> = emptyList(),
    val suggestedDependencies: List<Pair<Goal, DependencyType>> = emptyList(),
    val error: String? = null,
    val showAddDependencyDialog: Boolean = false,
    val showDependencyGraphSheet: Boolean = false
)

class GoalDependencyViewModel(
    private val dependencyRepository: GoalDependencyRepository,
    private val goalRepository: GoalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalDependencyUiState())
    val uiState: StateFlow<GoalDependencyUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val graph = dependencyRepository.buildDependencyGraph()
                val goals = goalRepository.getAllGoals()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dependencyGraph = graph,
                    allGoals = goals
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load dependency data"
                )
            }
        }
    }

    fun selectGoal(goal: Goal) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val dependencies = dependencyRepository.getDependenciesForGoal(goal.id)
                val suggestions = dependencyRepository.getSuggestedDependencies(goal.id)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    selectedGoal = goal,
                    selectedGoalDependencies = dependencies,
                    suggestedDependencies = suggestions
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun clearSelectedGoal() {
        _uiState.value = _uiState.value.copy(
            selectedGoal = null,
            selectedGoalDependencies = emptyList(),
            suggestedDependencies = emptyList()
        )
    }

    fun addDependency(
        sourceGoalId: String,
        targetGoalId: String,
        dependencyType: DependencyType
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                dependencyRepository.addDependency(sourceGoalId, targetGoalId, dependencyType)
                // Refresh the graph
                val graph = dependencyRepository.buildDependencyGraph()
                val dependencies = _uiState.value.selectedGoal?.let {
                    dependencyRepository.getDependenciesForGoal(it.id)
                } ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dependencyGraph = graph,
                    selectedGoalDependencies = dependencies,
                    showAddDependencyDialog = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to add dependency"
                )
            }
        }
    }

    fun removeDependency(dependencyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                dependencyRepository.removeDependency(dependencyId)
                // Refresh the graph
                val graph = dependencyRepository.buildDependencyGraph()
                val dependencies = _uiState.value.selectedGoal?.let {
                    dependencyRepository.getDependenciesForGoal(it.id)
                } ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    dependencyGraph = graph,
                    selectedGoalDependencies = dependencies
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to remove dependency"
                )
            }
        }
    }

    fun showAddDependencyDialog() {
        _uiState.value = _uiState.value.copy(showAddDependencyDialog = true)
    }

    fun hideAddDependencyDialog() {
        _uiState.value = _uiState.value.copy(showAddDependencyDialog = false)
    }

    fun showDependencyGraph() {
        _uiState.value = _uiState.value.copy(showDependencyGraphSheet = true)
    }

    fun hideDependencyGraph() {
        _uiState.value = _uiState.value.copy(showDependencyGraphSheet = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun getBlockingGoals(goalId: String, onResult: (List<Goal>) -> Unit) {
        viewModelScope.launch {
            try {
                val goals = dependencyRepository.getBlockingGoals(goalId)
                onResult(goals)
            } catch (e: Exception) {
                onResult(emptyList())
            }
        }
    }

    fun getChildGoals(goalId: String, onResult: (List<Goal>) -> Unit) {
        viewModelScope.launch {
            try {
                val goals = dependencyRepository.getChildGoals(goalId)
                onResult(goals)
            } catch (e: Exception) {
                onResult(emptyList())
            }
        }
    }

    fun getRelatedGoals(goalId: String, onResult: (List<Goal>) -> Unit) {
        viewModelScope.launch {
            try {
                val goals = dependencyRepository.getRelatedGoals(goalId)
                onResult(goals)
            } catch (e: Exception) {
                onResult(emptyList())
            }
        }
    }
}
