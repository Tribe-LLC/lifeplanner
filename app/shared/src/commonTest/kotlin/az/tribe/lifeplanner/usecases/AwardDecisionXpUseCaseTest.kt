package az.tribe.lifeplanner.usecases

import az.tribe.lifeplanner.domain.model.BuiltInAbility
import az.tribe.lifeplanner.domain.model.OutcomeQuality
import az.tribe.lifeplanner.testutil.FakeAbilityRepository
import az.tribe.lifeplanner.usecases.ability.AwardDecisionXpUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AwardDecisionXpUseCaseTest {

    private val repo = FakeAbilityRepository()
    private val useCase = AwardDecisionXpUseCase(repo)

    @Test
    fun `the built-in abilities seed themselves on first award`() = runTest {
        assertTrue(repo.abilities.isEmpty())
        useCase.onDecisionLogged()
        assertNotNull(repo.abilities[BuiltInAbility.JUDGMENT])
        assertNotNull(repo.abilities[BuiltInAbility.REFLECTION])
    }

    @Test
    fun `seeding twice does not duplicate or reset progress`() = runTest {
        useCase.onDecisionLogged()
        useCase.onDecisionLogged()
        assertEquals(2, repo.abilities.size)
        assertEquals(
            AwardDecisionXpUseCase.XP_LOGGED * 2,
            repo.abilities.getValue(BuiltInAbility.JUDGMENT).totalXp
        )
    }

    @Test
    fun `sound thinking that went badly earns the full review award`() = runTest {
        val award = useCase.onDecisionReviewed(OutcomeQuality.GOOD_PROCESS_BAD_RESULT)
        assertNotNull(award)
        assertEquals(
            AwardDecisionXpUseCase.XP_REVIEWED + AwardDecisionXpUseCase.XP_GOOD_PROCESS_BONUS,
            award.xpEarned
        )
    }

    @Test
    fun `a lucky bad call earns the review base but no bonus`() = runTest {
        // The honesty of reviewing still pays. Getting away with it does not.
        val award = useCase.onDecisionReviewed(OutcomeQuality.BAD_PROCESS_GOOD_RESULT)
        assertNotNull(award)
        assertEquals(AwardDecisionXpUseCase.XP_REVIEWED, award.xpEarned)
    }

    @Test
    fun `result does not change the award when process is equal`() = runTest {
        val good = useCase.onDecisionReviewed(OutcomeQuality.GOOD_PROCESS_GOOD_RESULT)
        val bad = useCase.onDecisionReviewed(OutcomeQuality.GOOD_PROCESS_BAD_RESULT)
        assertNotNull(good)
        assertNotNull(bad)
        assertEquals(good.xpEarned, bad.xpEarned)
    }

    @Test
    fun `crossing the level threshold is reported so the UI can celebrate`() = runTest {
        // A good-process review pays 40. Level 2 costs 50 cumulative XP, so the first review
        // leaves you at level 1 and the second crosses.
        val first = useCase.onDecisionReviewed(OutcomeQuality.GOOD_PROCESS_GOOD_RESULT)
        assertNotNull(first)
        assertEquals(1, first.ability.currentLevel)
        assertFalse(first.leveledUp)

        val second = useCase.onDecisionReviewed(OutcomeQuality.GOOD_PROCESS_GOOD_RESULT)
        assertNotNull(second)
        assertEquals(80, second.ability.totalXp)
        assertEquals(2, second.ability.currentLevel)
        assertTrue(second.leveledUp)
    }
}
