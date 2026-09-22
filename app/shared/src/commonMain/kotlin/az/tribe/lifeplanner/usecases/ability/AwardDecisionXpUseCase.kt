package az.tribe.lifeplanner.usecases.ability

import az.tribe.lifeplanner.domain.model.BuiltInAbility
import az.tribe.lifeplanner.domain.model.OutcomeQuality
import az.tribe.lifeplanner.domain.model.XpAward
import az.tribe.lifeplanner.domain.repository.AbilityRepository
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Pillar 3's reward rules, kept in one place so the incentive is auditable.
 *
 * The rule that matters: **XP follows process, never luck.** Reviewing a decision always pays,
 * because an honest review is the behaviour worth encouraging even when the answer is unflattering.
 * On top of that, sound thinking earns a bonus whether or not it worked out, and a lucky bad call
 * earns nothing extra. That is [OutcomeQuality]'s whole reason for existing, made mechanical.
 *
 * Deliberately absent: any reward for volume. Paying per journal entry or per decision logged buys
 * padding, not judgement.
 */
class AwardDecisionXpUseCase(
    private val abilityRepository: AbilityRepository,
) {
    /** Logging a call costs thought, so it pays a little. */
    suspend fun onDecisionLogged(): XpAward? = award(XP_LOGGED)

    /**
     * Reviewing pays the base regardless of the verdict, plus a bonus when the process was sound.
     * A bad-process-good-result review still earns [XP_REVIEWED]: you told the truth about a call
     * you got lucky on, which is exactly the honesty the scorecard depends on.
     */
    suspend fun onDecisionReviewed(quality: OutcomeQuality): XpAward? =
        award(XP_REVIEWED + if (quality.isGoodProcess) XP_GOOD_PROCESS_BONUS else 0)

    private suspend fun award(xp: Int): XpAward? {
        ensureSeeded()
        return abilityRepository.awardXp(BuiltInAbility.JUDGMENT, xp)
    }

    /**
     * Create the built-in abilities on first use. Seeding lazily here rather than in a migration
     * keeps this off the schema: the ids are fixed, so this is idempotent.
     */
    private suspend fun ensureSeeded() {
        if (abilityRepository.getAbilityById(BuiltInAbility.JUDGMENT) != null) return
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date
            .toString()
        BuiltInAbility.seeds(today).forEach { seed ->
            if (abilityRepository.getAbilityById(seed.id) == null) {
                abilityRepository.createAbility(seed)
            }
        }
    }

    companion object {
        const val XP_LOGGED = 10
        const val XP_REVIEWED = 25
        const val XP_GOOD_PROCESS_BONUS = 15
    }
}
