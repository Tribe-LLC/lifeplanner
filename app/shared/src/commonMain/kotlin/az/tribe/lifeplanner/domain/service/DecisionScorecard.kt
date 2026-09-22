package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.Decision
import az.tribe.lifeplanner.domain.model.DecisionStatus

/**
 * Pillar 3, the track record behind the decision journal.
 *
 * The interesting number here is not how many decisions you logged, it is whether your confidence
 * matches reality. Every [Decision] records a `confidence` at the moment it was made and an
 * `outcomeQuality` once reviewed, so the gap between the two is measurable.
 *
 * Process and result are kept apart deliberately. [processHitRate] is the score worth chasing,
 * because it credits sound thinking that happened to go badly. [actualSuccessRate] only feeds
 * [calibrationGap], where it belongs: you predicted, reality answered.
 */
data class DecisionScorecard(
    /** Confirmed decisions, reviewed or not. */
    val logged: Int = 0,
    /** Confirmed decisions that have an outcome recorded. */
    val reviewed: Int = 0,
    /** Reviewed decisions whose thinking was sound, whatever the result. */
    val goodProcess: Int = 0,
    /** Reviewed decisions that actually worked out. */
    val goodResult: Int = 0,
    /** Mean stated confidence across reviewed decisions, 0-100. Null when nothing is reviewed. */
    val averageConfidence: Int? = null,
) {
    /** Share of reviewed decisions with sound process, 0-100. Null when nothing is reviewed. */
    val processHitRate: Int?
        get() = percentOf(goodProcess, reviewed)

    /** Share of reviewed decisions that worked out, 0-100. Null when nothing is reviewed. */
    val actualSuccessRate: Int?
        get() = percentOf(goodResult, reviewed)

    /**
     * Stated confidence minus what actually happened, in percentage points. Positive means
     * overconfident, negative means you sell yourself short, near zero means well calibrated.
     * Null until something has been reviewed.
     */
    val calibrationGap: Int?
        get() {
            val confidence = averageConfidence ?: return null
            val actual = actualSuccessRate ?: return null
            return confidence - actual
        }

    /** Decisions logged but not yet reviewed. These are the ones with an answer still owed. */
    val awaitingReview: Int
        get() = (logged - reviewed).coerceAtLeast(0)

    private fun percentOf(part: Int, whole: Int): Int? =
        if (whole <= 0) null else ((part * 100.0) / whole).toInt()

    companion object {
        /**
         * Build a scorecard from a decision list. Only [DecisionStatus.CONFIRMED] decisions count:
         * a pending AI-detected decision is a suggestion the user has not owned yet, and a
         * dismissed one was never a decision at all, so neither should move the numbers.
         */
        fun from(decisions: List<Decision>): DecisionScorecard {
            val confirmed = decisions.filter { it.status == DecisionStatus.CONFIRMED }
            if (confirmed.isEmpty()) return DecisionScorecard()

            val scored = confirmed.filter { it.outcomeQuality != null }
            val averageConfidence = scored
                .takeIf { it.isNotEmpty() }
                ?.let { list -> list.sumOf { it.confidence } / list.size }

            return DecisionScorecard(
                logged = confirmed.size,
                reviewed = scored.size,
                goodProcess = scored.count { it.outcomeQuality?.isGoodProcess == true },
                goodResult = scored.count { it.outcomeQuality?.isGoodResult == true },
                averageConfidence = averageConfidence,
            )
        }
    }
}
