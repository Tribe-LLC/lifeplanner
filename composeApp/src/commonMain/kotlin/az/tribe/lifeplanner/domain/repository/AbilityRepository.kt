package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.Ability
import az.tribe.lifeplanner.domain.model.AbilityHabitLink
import kotlinx.coroutines.flow.Flow

interface AbilityRepository {
    fun observeAllAbilities(): Flow<List<Ability>>
    suspend fun getAbilityById(id: String): Ability?
    suspend fun createAbility(ability: Ability)
    suspend fun updateAbility(ability: Ability)
    suspend fun deleteAbility(id: String)
    suspend fun linkHabit(abilityId: String, habitId: String, xpWeight: Float = 1.0f)
    suspend fun unlinkHabit(abilityId: String, habitId: String)
    suspend fun getLinksForAbility(abilityId: String): List<AbilityHabitLink>
    suspend fun getLinksForHabit(habitId: String): List<AbilityHabitLink>
    suspend fun awardXpToAbilitiesForHabit(habitId: String, baseXp: Int = 10)
}
