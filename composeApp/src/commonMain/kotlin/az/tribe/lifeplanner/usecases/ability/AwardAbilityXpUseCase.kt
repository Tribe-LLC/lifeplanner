package az.tribe.lifeplanner.usecases.ability

import az.tribe.lifeplanner.domain.repository.AbilityRepository

class AwardAbilityXpUseCase(
    private val abilityRepository: AbilityRepository
) {
    suspend operator fun invoke(habitId: String, baseXp: Int = 10) {
        abilityRepository.awardXpToAbilitiesForHabit(habitId, baseXp)
    }
}
