package az.tribe.lifeplanner.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.domain.model.CoachGroup
import az.tribe.lifeplanner.domain.model.CoachGroupMember
import az.tribe.lifeplanner.domain.model.CustomCoach
import az.tribe.lifeplanner.domain.repository.CoachRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CoachUiState(
    val isLoading: Boolean = false,
    val customCoaches: List<CustomCoach> = emptyList(),
    val coachGroups: List<CoachGroup> = emptyList(),
    val selectedCoach: CustomCoach? = null,
    val selectedGroup: CoachGroup? = null,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

class CoachViewModel(
    private val coachRepository: CoachRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val coaches = coachRepository.getAllCustomCoaches()
                val groups = coachRepository.getAllCoachGroups()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    customCoaches = coaches,
                    coachGroups = groups,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    // ===== Custom Coach Operations =====

    fun createCoach(coach: CustomCoach) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val created = coachRepository.createCustomCoach(coach)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    saveSuccess = true,
                    selectedCoach = created
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun updateCoach(coach: CustomCoach) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                coachRepository.updateCustomCoach(coach)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    saveSuccess = true
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun deleteCoach(coachId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                coachRepository.deleteCustomCoach(coachId)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun selectCoach(coach: CustomCoach?) {
        _uiState.value = _uiState.value.copy(selectedCoach = coach)
    }

    fun getCoachById(id: String): CustomCoach? {
        return _uiState.value.customCoaches.find { it.id == id }
    }

    // ===== Coach Group Operations =====

    fun createGroup(group: CoachGroup) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val created = coachRepository.createCoachGroup(group)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    saveSuccess = true,
                    selectedGroup = created
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun updateGroup(group: CoachGroup) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                coachRepository.updateCoachGroup(group)
                // Also update members
                coachRepository.setGroupMembers(group.id, group.members)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    saveSuccess = true
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                coachRepository.deleteCoachGroup(groupId)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun selectGroup(group: CoachGroup?) {
        _uiState.value = _uiState.value.copy(selectedGroup = group)
    }

    fun getGroupById(id: String): CoachGroup? {
        return _uiState.value.coachGroups.find { it.id == id }
    }

    // ===== Utility =====

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }
}
