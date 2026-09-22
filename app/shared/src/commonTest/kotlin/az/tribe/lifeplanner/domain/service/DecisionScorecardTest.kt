package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.Decision
import az.tribe.lifeplanner.domain.model.DecisionStatus
import az.tribe.lifeplanner.domain.model.OutcomeQuality
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DecisionScorecardTest {

    private var seq = 0

    private fun decision(
        confidence: Int = 50,
        quality: OutcomeQuality? = null,
        status: DecisionStatus = DecisionStatus.CONFIRMED,
    ) = Decision(
        id = "d${seq++}",
        question = "Ship it?",
        chosenOption = "Ship",
        confidence = confidence,
        decidedAt = LocalDateTime(2026, 8, 1, 9, 0),
        outcomeQuality = quality,
        status = status,
    )

    @Test
    fun `an empty journal scores nothing rather than zero`() {
        val card = DecisionScorecard.from(emptyList())
        assertEquals(0, card.logged)
        // Null rather than 0 so the UI can say "no calls yet" instead of claiming a 0% hit rate.
        assertNull(card.processHitRate)
        assertNull(card.calibrationGap)
    }

    @Test
    fun `only confirmed decisions count`() {
        val card = DecisionScorecard.from(
            listOf(
                decision(status = DecisionStatus.CONFIRMED),
                decision(status = DecisionStatus.PENDING),
                decision(status = DecisionStatus.DISMISSED),
            )
        )
        assertEquals(1, card.logged)
    }

    @Test
    fun `unreviewed decisions are logged but not scored`() {
        val card = DecisionScorecard.from(listOf(decision(), decision()))
        assertEquals(2, card.logged)
        assertEquals(0, card.reviewed)
        assertEquals(2, card.awaitingReview)
        assertNull(card.processHitRate)
    }

    @Test
    fun `sound thinking that went badly still counts as a process hit`() {
        val card = DecisionScorecard.from(
            listOf(decision(quality = OutcomeQuality.GOOD_PROCESS_BAD_RESULT))
        )
        assertEquals(100, card.processHitRate)
        // It genuinely did not work out, so the result rate must not be flattered.
        assertEquals(0, card.actualSuccessRate)
    }

    @Test
    fun `a lucky bad call is not a process hit`() {
        val card = DecisionScorecard.from(
            listOf(decision(quality = OutcomeQuality.BAD_PROCESS_GOOD_RESULT))
        )
        assertEquals(0, card.processHitRate)
        assertEquals(100, card.actualSuccessRate)
    }

    @Test
    fun `overconfidence shows as a positive calibration gap`() {
        val card = DecisionScorecard.from(
            listOf(
                decision(confidence = 90, quality = OutcomeQuality.GOOD_PROCESS_GOOD_RESULT),
                decision(confidence = 90, quality = OutcomeQuality.GOOD_PROCESS_BAD_RESULT),
            )
        )
        assertEquals(90, card.averageConfidence)
        assertEquals(50, card.actualSuccessRate)
        assertEquals(40, card.calibrationGap)
    }

    @Test
    fun `selling yourself short shows as a negative gap`() {
        val card = DecisionScorecard.from(
            listOf(
                decision(confidence = 30, quality = OutcomeQuality.GOOD_PROCESS_GOOD_RESULT),
                decision(confidence = 30, quality = OutcomeQuality.BAD_PROCESS_GOOD_RESULT),
            )
        )
        assertEquals(30, card.averageConfidence)
        assertEquals(100, card.actualSuccessRate)
        assertEquals(-70, card.calibrationGap)
    }

    @Test
    fun `confidence is averaged over reviewed decisions only`() {
        // The unreviewed 100 must not drag the average, because reality has not answered it yet.
        val card = DecisionScorecard.from(
            listOf(
                decision(confidence = 40, quality = OutcomeQuality.GOOD_PROCESS_GOOD_RESULT),
                decision(confidence = 60, quality = OutcomeQuality.GOOD_PROCESS_GOOD_RESULT),
                decision(confidence = 100),
            )
        )
        assertEquals(3, card.logged)
        assertEquals(2, card.reviewed)
        assertEquals(50, card.averageConfidence)
    }
}
