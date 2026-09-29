package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.data.plans.PlanView
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.PaceKind
import az.tribe.lifeplanner.domain.service.PlanInputs
import az.tribe.lifeplanner.domain.service.PlanSpec
import az.tribe.lifeplanner.domain.service.PlanTemplates
import az.tribe.lifeplanner.domain.service.PlanTrack
import az.tribe.lifeplanner.domain.service.RoutineKind
import az.tribe.lifeplanner.ui.v4.plans.PaceTone
import az.tribe.lifeplanner.ui.v4.plans.PlanPageModel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanPageModelTest {

    private val start = LocalDate(2026, 9, 29)
    private val goal = Goal(
        id = "g", category = GoalCategory.BODY, title = "Run a 5K", description = "", status = GoalStatus.IN_PROGRESS,
        timeline = GoalTimeline.SHORT_TERM, dueDate = LocalDate(2026, 12, 1), createdAt = LocalDateTime(2026, 9, 29, 9, 0),
        milestones = listOf(
            Milestone("a", "Get running shoes that fit", isCompleted = true, dueDate = LocalDate(2026, 10, 1)),
            Milestone("b", "Run 2 km", dueDate = LocalDate(2026, 10, 25)),
            Milestone("c", "Run the 5K", dueDate = LocalDate(2026, 12, 1)),
        ),
    )
    private val spec = PlanSpec(
        goalId = "g", area = PlanArea.FITNESS, track = PlanTrack.RUN, start = start, target = 5.0,
        routineKind = RoutineKind.FITNESS_WEEK, routineId = "MON,WED,SAT",
    )

    private fun view(today: LocalDate, g: Goal = goal, s: PlanSpec = spec) = PlanView.of(g, s, PlanInputs(), today, emptyMap())

    @Test
    fun runStepsSayTheyTickThemselves() {
        val today = LocalDate(2026, 10, 20)
        val v = view(today)
        assertEquals("By Tue 1 Dec, 6 weeks left", PlanPageModel.sub(v, today))
        assertEquals("Sun 25 Oct. Ticks itself", PlanPageModel.stepMeta(v, goal.milestones[1], today))
        assertEquals("Tue 1 Dec", PlanPageModel.stepMeta(v, goal.milestones[2], today))
        assertEquals("Done", PlanPageModel.stepMeta(v, goal.milestones[0], today))
        assertEquals("I ran it", PlanPageModel.tickLabel(v, goal.milestones[1]))
        assertEquals("Done", PlanPageModel.tickLabel(v, goal.milestones[0]))
        assertTrue(PlanPageModel.nextText(v, goal.milestones[1], today).endsWith("a run of 2 km comes in, logged or from Health."))
        assertEquals(PaceTone.GOOD, PlanPageModel.tone(v.pace.kind))
    }

    @Test
    fun aMissedStepWaitsAndOffersCatchUp() {
        val today = LocalDate(2026, 11, 2)
        val v = view(today)
        assertEquals(PaceKind.BEHIND, v.pace.kind)
        assertEquals("Was Sun 25 Oct. Waiting for your next run", PlanPageModel.stepMeta(v, goal.milestones[1], today))
        assertEquals("About a week behind, and that is fine", PlanPageModel.catchUpTitle(v))
        assertEquals("Give it one more week", PlanPageModel.pushLabel(v))
        assertEquals("New finish Tue 8 Dec, same steps", PlanPageModel.pushSub(v))
        assertEquals("Steps move closer together, one extra easy run this week", PlanPageModel.keepSub(v))
    }

    @Test
    fun bannersForPausedLetGoAndANewDate() {
        val today = LocalDate(2026, 10, 20)
        val paused = view(today, s = spec.copy(pausedFrom = today, pausedUntil = LocalDate(2026, 10, 26)))
        assertEquals("Resume now", assertNotNull(PlanPageModel.banner(paused, false)).action)
        assertTrue(PlanPageModel.banner(paused, false)!!.text.startsWith("Until Mon 26 Oct."))
        assertEquals("Bring it back", PlanPageModel.banner(view(today, goal.copy(isArchived = true)), false)!!.action)
        assertEquals("The 2 steps left are spread out to fit.", PlanPageModel.banner(view(today), true)!!.text.substringAfter(". "))
        assertNull(PlanPageModel.banner(view(today), false))
    }

    @Test
    fun whatNextAfterAFinishedRun() {
        val today = LocalDate(2026, 12, 1)
        val done = view(today, goal.copy(status = GoalStatus.COMPLETED), spec.copy(finished = today))
        assertEquals("Run a 10K", PlanPageModel.nextIdea(done, "EUR"))
        assertEquals("Start a 10K plan", PlanPageModel.nextLabel("Run a 10K"))
        assertEquals("You ran a 5K.", done.recap?.first)
        assertEquals("Easy runs, Mon Wed Sat", PlanPageModel.routine(done, null)?.first)
    }

    @Test
    fun savingsAndCountsOfferTheirOwnButton() {
        val today = LocalDate(2026, 10, 20)
        assertEquals("Put aside", PlanPageModel.logAction(view(today, s = spec.copy(track = PlanTrack.SAVE, routineKind = null))))
        assertEquals("Paid some off", PlanPageModel.logAction(view(today, s = spec.copy(track = PlanTrack.SAVE, template = PlanTemplates.DEBT))))
        assertEquals("Finished a book", PlanPageModel.logAction(view(today, s = spec.copy(track = PlanTrack.COUNT, template = PlanTemplates.BOOKS, routineKind = null))))
        assertNull(PlanPageModel.logAction(view(today, s = spec.copy(track = PlanTrack.COUNT, routineKind = RoutineKind.HABIT))))
        assertNull(PlanPageModel.logAction(view(today)))
    }

    @Test
    fun newDatesToOffer() {
        val today = LocalDate(2026, 10, 20)
        assertEquals(
            listOf(LocalDate(2026, 11, 17), LocalDate(2026, 12, 15), LocalDate(2027, 1, 12)),
            PlanPageModel.dateChoices(view(today), today),
        )
        // Too close to the date for "sooner".
        assertEquals(2, PlanPageModel.dateChoices(view(LocalDate(2026, 11, 20)), LocalDate(2026, 11, 20)).size)
    }
}
