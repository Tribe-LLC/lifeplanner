package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.data.plans.PlanView
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.MoneySummary
import az.tribe.lifeplanner.domain.service.PlanInputs
import az.tribe.lifeplanner.domain.service.PlanProgress
import az.tribe.lifeplanner.domain.service.PlanSpec
import az.tribe.lifeplanner.domain.service.PlanTrack
import az.tribe.lifeplanner.domain.service.QuickAddParser
import az.tribe.lifeplanner.ui.v4.quickadd.QuickAddPlans
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickAddPlansTest {

    private val today = LocalDate(2026, 9, 29)
    private val now = LocalDateTime(2026, 9, 29, 8, 0)

    private fun plan(id: String, title: String, spec: PlanSpec, steps: List<Milestone> = emptyList()) = PlanView.of(
        Goal(
            id = id, category = GoalCategory.MONEY, title = title, description = "", status = GoalStatus.IN_PROGRESS,
            timeline = GoalTimeline.MID_TERM, dueDate = LocalDate(2027, 3, 1), createdAt = now, milestones = steps,
        ),
        spec, PlanInputs(), today, emptyMap(),
    )

    private val japan = plan(
        "j", "Save €2,000 for Japan",
        PlanSpec(goalId = "j", area = PlanArea.MONEY, track = PlanTrack.SAVE, start = today, target = 2000.0, currency = "EUR", subject = "Japan", baseline = 500.0),
    )
    private val run = plan(
        "r", "Run a 5K",
        PlanSpec(goalId = "r", area = PlanArea.FITNESS, track = PlanTrack.RUN, start = today, target = 5.0),
        listOf(Milestone("1", "Run 1 km", dueDate = LocalDate(2026, 10, 11)), Milestone("2", "Run 3 km", dueDate = LocalDate(2026, 11, 8)), Milestone("3", "Run the 5K", dueDate = LocalDate(2026, 12, 1))),
    )

    @Test
    fun aPlanLineIsOfferedAsAPlan() {
        assertTrue(QuickAddParser.parse("run a 5k by december", now, "EUR").looksLikePlan)
        assertTrue(QuickAddParser.parse("save 2000 for japan by march", now, "EUR").looksLikePlan)
        assertFalse(QuickAddParser.parse("ran 3k this morning", now, "EUR").looksLikePlan)
        assertFalse(QuickAddParser.parse("coffee 4.50", now, "EUR").looksLikePlan)
        val offer = QuickAddPlans.offer("run a 5k by december", today, "EUR")!!
        assertEquals("Run a 5K, by Tue 1 Dec", offer.title)
        assertTrue(offer.text.startsWith("Sounds like a plan, not a run you did."))
        assertEquals("Log a run instead", QuickAddPlans.altLabel(QuickAddParser.parse("run a 5k by december", now, "EUR").entries))
    }

    @Test
    fun moneyPutAsideIsForAPlanNotSpending() {
        val e = QuickAddParser.parse("put aside 100 for Japan", now, "EUR").entries.single()
        assertEquals(PlanProgress.SAVINGS, e.category)
        assertEquals("Put aside for Japan", e.title)
        assertEquals(100.0, e.amount)
        assertEquals("Counts toward Save €2,000 for Japan, now €600 of €2,000. Not counted as spending.", QuickAddPlans.note(e, listOf(japan, run), "Japan"))
        assertEquals("j", QuickAddPlans.savingsPlan(listOf(japan, run), null)?.id)
        assertEquals("Kept apart from your spending.", QuickAddPlans.note(e, listOf(run), "Japan"))

        val saved = LifeLog("x", PlanArea.MONEY, LogKind.EXPENSE, title = "Put aside", amount = 100.0, category = PlanProgress.SAVINGS, occurredAt = now)
        assertFalse(MoneySummary.isSpend(saved))
        assertTrue(MoneySummary.isSpend(saved.copy(category = "food")))
    }

    @Test
    fun aRunSaysWhichStepItTicks() {
        val e = QuickAddParser.parse("ran 3k this morning", now, "EUR").entries.single()
        assertEquals("Counts toward Run a 5K, and ticks its step Run 3 km.", QuickAddPlans.note(e, listOf(japan, run), null))
        assertNull(QuickAddPlans.note(e, listOf(japan), null))
    }
}
