package az.tribe.lifeplanner.domain.model

import kotlinx.datetime.LocalDateTime

/**
 * Pillar 3, the "good decision vs. good luck" distinction: process quality on one
 * axis, result on the other. Lets a review credit a sound decision that happened to
 * turn out badly (and flag a lucky bad decision).
 */
enum class OutcomeQuality {
    GOOD_PROCESS_GOOD_RESULT,
    GOOD_PROCESS_BAD_RESULT,
    BAD_PROCESS_GOOD_RESULT,
    BAD_PROCESS_BAD_RESULT;

    /**
     * Was the thinking sound, regardless of how it landed? This is the axis worth rewarding: a
     * sound call that went badly still deserves credit, and a lucky bad call does not.
     */
    val isGoodProcess: Boolean
        get() = this == GOOD_PROCESS_GOOD_RESULT || this == GOOD_PROCESS_BAD_RESULT

    /** Did it actually work out? Used for calibration, never for XP. */
    val isGoodResult: Boolean
        get() = this == GOOD_PROCESS_GOOD_RESULT || this == BAD_PROCESS_GOOD_RESULT
}

/**
 * Where a [Decision] originated. Governs how it's surfaced and whether it may feed the Pillar 7
 * wiring inference: a [JOURNAL] decision starts life [DecisionStatus.PENDING] and only becomes
 * wiring evidence once the user confirms it.
 */
enum class DecisionSource {
    /** Raised by the [ChoicePoint] detector and resolved deliberately by the user. */
    CHOICE_POINT,
    /** Detected by the AI inside a journal entry; awaits the user's confirmation. */
    JOURNAL,
    /** Logged from Possibility Mode. */
    POSSIBILITY,
    /** Entered by the user directly. */
    MANUAL,
}

/**
 * Lifecycle of a [Decision]. Choice-point/possibility/manual decisions are born [CONFIRMED] (the
 * user already acted); an AI-detected [DecisionSource.JOURNAL] decision is born [PENDING] and is
 * surfaced as a gentle "want to log this?" nudge until the user [CONFIRMED]s or [DISMISSED]s it.
 */
enum class DecisionStatus { PENDING, CONFIRMED, DISMISSED }

/**
 * Pillar 3, a deliberate choice recorded as a first-class object (not a mechanical
 * [GoalChange] diff): what was decided, the options weighed, the reasoning, and, later -
 * how it actually turned out. `null` outcome fields mean the decision hasn't been reviewed yet.
 */
data class Decision(
    val id: String,
    val question: String,
    val optionsConsidered: List<String> = emptyList(),
    val chosenOption: String,
    val reasoning: String = "",
    val relatedGoalId: String? = null,
    val expectedOutcome: String = "",
    val confidence: Int = 50,            // 0-100
    val decidedAt: LocalDateTime,
    val actualOutcome: String? = null,
    val outcomeReviewedAt: LocalDateTime? = null,
    val outcomeQuality: OutcomeQuality? = null,
    val source: DecisionSource = DecisionSource.CHOICE_POINT,
    val status: DecisionStatus = DecisionStatus.CONFIRMED,
)
