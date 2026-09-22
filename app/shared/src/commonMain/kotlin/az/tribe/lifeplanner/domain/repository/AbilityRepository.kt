package az.tribe.lifeplanner.domain.repository

import az.tribe.lifeplanner.domain.model.Ability
import az.tribe.lifeplanner.domain.model.AbilityGoalLink
import az.tribe.lifeplanner.domain.model.AbilityHabitLink
import az.tribe.lifeplanner.domain.model.XpAward
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

    /**
     * Award XP straight to one ability. Habits earn XP through their links; decisions, reviews and
     * journal entries have no link table, so they award directly. Returns null when the ability is
     * missing or [xp] is not positive.
     */
    suspend fun awardXp(abilityId: String, xp: Int): XpAward?
    // Goal linking
    suspend fun linkGoal(abilityId: String, goalId: String)
    suspend fun unlinkGoal(abilityId: String, goalId: String)
    suspend fun getGoalLinksForAbility(abilityId: String): List<AbilityGoalLink>
    suspend fun getAbilityLinksForGoal(goalId: String): List<AbilityGoalLink>
}
