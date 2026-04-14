package az.tribe.lifeplanner.ui.ability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.mapper.createNewAbility
import az.tribe.lifeplanner.domain.model.Ability
import az.tribe.lifeplanner.domain.repository.AbilityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AbilityViewModel(
    private val abilityRepository: AbilityRepository
) : ViewModel() {

    val abilities: StateFlow<List<Ability>> = abilityRepository.observeAllAbilities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun createAbility(title: String, description: String, iconEmoji: String) {
        viewModelScope.launch {
            try {
                val ability = createNewAbility(title = title, description = description, iconEmoji = iconEmoji)
                abilityRepository.createAbility(ability)
            } catch (e: Exception) {
                _error.value = "Failed to create ability: ${e.message}"
            }
        }
    }

    fun deleteAbility(id: String) {
        viewModelScope.launch {
            try {
                abilityRepository.deleteAbility(id)
            } catch (e: Exception) {
                _error.value = "Failed to delete ability: ${e.message}"
            }
        }
    }
}
