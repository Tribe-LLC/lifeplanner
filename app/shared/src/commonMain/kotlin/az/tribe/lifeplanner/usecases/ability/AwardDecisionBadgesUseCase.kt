package az.tribe.lifeplanner.usecases.ability

import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.service.DecisionScorecard
import kotlin.math.abs

/**
 * Badge rules for Pillar 3, derived from the track record rather than from event counts.
 *
 * Only one of these can be earned by logging: [BadgeType.DECISION_FIRST], which marks the habit
 * starting. Everything above it requires coming back and answering for a call, and the top two
 * require the answers to be good. That is the opposite of the journal tier, where 10 and 30
 * entries pay out for volume alone.
 *
 * Awarding is safe to repeat: [GamificationRepository.awardBadge] returns early when the badge is
 * already held, so this can run after every review.
 */
class AwardDecisionBadgesUseCase(
    private val gamificationRepository: GamificationRepository,
) {
    suspend operator fun invoke(card: DecisionScorecard) {
        if (card.logged >= 1) award(BadgeType.DECISION_FIRST)
        if (card.reviewed >= 1) award(BadgeType.DECISION_REVIEW_FIRST)
        if (card.reviewed >= REVIEW_TIER) award(BadgeType.DECISION_REVIEW_10)
        if (card.goodProcess >= SOUND_TIER) award(BadgeType.DECISION_SOUND_10)

        // Calibration needs a real sample behind it. Landing within 10 points across two reviews
        // is luck, not self-knowledge, so the tier gate comes first.
        val gap = card.calibrationGap
        if (card.reviewed >= REVIEW_TIER && gap != null && abs(gap) <= CALIBRATION_TOLERANCE) {
            award(BadgeType.DECISION_CALIBRATED)
        }
    }

    private suspend fun award(type: BadgeType) = gamificationRepository.awardBadge(type)

    companion object {
        const val REVIEW_TIER = 10
        const val SOUND_TIER = 10
        const val CALIBRATION_TOLERANCE = 10
    }
}
