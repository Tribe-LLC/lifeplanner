package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.MoneySummary
import az.tribe.lifeplanner.domain.service.PlanSpec
import az.tribe.lifeplanner.domain.service.PlanTrack
import az.tribe.lifeplanner.domain.service.RoutineKind
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanSpecTest {

    private val start = LocalDate(2026, 9, 29)

    private fun goal(id: String, category: GoalCategory) = Goal(
        id = id, category = category, title = "Plan", description = "", status = GoalStatus.NOT_STARTED,
        timeline = GoalTimeline.SHORT_TERM, dueDate = LocalDate(2026, 12, 1), createdAt = LocalDateTime(2026, 9, 29, 9, 0),
    )

    @Test
    fun encodesAndDecodesEveryField() {
        val spec = PlanSpec(
            goalId = "g1", area = PlanArea.STUDY, track = PlanTrack.STUDY, start = start, target = 1320.0, currency = null,
            template = "learn", subject = "Spanish", baseline = 12.5, count = 3,
            pausedFrom = LocalDate(2026, 10, 5), pausedUntil = LocalDate(2026, 10, 11),
            routineKind = RoutineKind.STUDY_REPEAT, routineId = "r1", asked = LocalDate(2026, 10, 2),
            keptOn = LocalDate(2026, 10, 3), finished = null,
        )
        val row = spec.toBudget()
        assertEquals("plan-g1", row.id)
        assertEquals(PlanSpec.METRIC, row.metric)
        assertEquals(PlanArea.STUDY, row.area)
        assertTrue(row.category!!.startsWith("goal=g1;tpl=learn;track=study;start=2026-09-29"))
        assertEquals(spec, PlanSpec.decode(row))
    }

    @Test
    fun skipsUnknownKeysAndCleansText() {
        val row = PlanSpec(goalId = "g2", area = PlanArea.MONEY, track = PlanTrack.SAVE, start = start, target = 2000.0, currency = "EUR", subject = "Japan; x=y")
            .toBudget().let { it.copy(category = it.category + ";future=thing") }
        val back = PlanSpec.decode(row)!!
        assertEquals("Japan, x y", back.subject)
        assertEquals(2000.0, back.target)
        assertEquals("EUR", back.currency)
    }

    @Test
    fun otherRowsAndBrokenRowsAreNotPlans() {
        assertNull(PlanSpec.decode(Budget("b", PlanArea.MONEY, Budget.METRIC_SPEND, null, 500.0, "EUR", BudgetPeriod.MONTH)))
        assertNull(PlanSpec.decode(Budget("b", PlanArea.MONEY, PlanSpec.METRIC, "track=save", 0.0, null, BudgetPeriod.MONTH)))
    }

    @Test
    fun areaComesFromTheSettingsThenTheOldCategory() {
        val specs = PlanSpec.fromBudgets(listOf(PlanSpec(goalId = "trip", area = PlanArea.TRAVEL, start = start).toBudget()))
        assertEquals(PlanArea.TRAVEL, PlanSpec.areaOf(goal("trip", PlanSpec.categoryFor(PlanArea.TRAVEL)), specs))
        assertEquals(PlanArea.FITNESS, PlanSpec.areaOf(goal("old", GoalCategory.BODY), specs))
    }

    @Test
    fun pausedForAFixedTimeOrUntilSaid() {
        val fixed = PlanSpec(goalId = "g", area = PlanArea.FITNESS, start = start, pausedFrom = LocalDate(2026, 10, 1), pausedUntil = LocalDate(2026, 10, 7))
        assertFalse(fixed.isPaused(LocalDate(2026, 9, 30)))
        assertTrue(fixed.isPaused(LocalDate(2026, 10, 7)))
        assertFalse(fixed.isPaused(LocalDate(2026, 10, 8)))
        assertTrue(fixed.copy(pausedUntil = null).isPaused(LocalDate(2027, 1, 1)))
    }

    @Test
    fun planRowsNeverLookLikeASpendingBudget() {
        val rows = listOf(PlanSpec(goalId = "g", area = PlanArea.MONEY, track = PlanTrack.SAVE, start = start, target = 2000.0, currency = "EUR").toBudget())
        assertNull(MoneySummary.primary(rows))
    }
}
