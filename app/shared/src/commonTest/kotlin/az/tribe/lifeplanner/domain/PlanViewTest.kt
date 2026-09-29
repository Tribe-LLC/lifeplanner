package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.data.plans.PlanMaker
import az.tribe.lifeplanner.data.plans.PlanState
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
import az.tribe.lifeplanner.domain.service.PlanTrack
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlanViewTest {

    private val start = LocalDate(2026, 9, 29)
    private val goal = Goal(
        id = "g", category = GoalCategory.BODY, title = "Run a 5K", description = "", status = GoalStatus.IN_PROGRESS,
        timeline = GoalTimeline.SHORT_TERM, dueDate = LocalDate(2026, 12, 1), createdAt = LocalDateTime(2026, 9, 29, 9, 0),
        milestones = listOf(
            Milestone("c", "Run 3 km", dueDate = LocalDate(2026, 11, 8)),
            Milestone("a", "Shoes", isCompleted = true, dueDate = LocalDate(2026, 10, 1)),
            Milestone("x", "Stretch", dueDate = null),
            Milestone("b", "Run 2 km", dueDate = LocalDate(2026, 10, 25)),
        ),
    )
    private val spec = PlanSpec(goalId = "g", area = PlanArea.FITNESS, track = PlanTrack.RUN, start = start, target = 5.0)

    @Test
    fun stepsInDateOrderAndTheNextOne() {
        val v = PlanView.of(goal, spec, PlanInputs(), LocalDate(2026, 10, 20), emptyMap())
        assertEquals(listOf("a", "b", "c", "x"), v.steps.map { it.id })
        assertEquals("b", v.next!!.id)
        assertEquals(PlanState.ACTIVE, v.state)
        assertEquals(PaceKind.ON_TRACK, v.pace.kind)
        assertNull(v.catchUp)
    }

    @Test
    fun aMissedStepOffersCatchUp() {
        val v = PlanView.of(goal, spec, PlanInputs(), LocalDate(2026, 11, 2), emptyMap())
        assertEquals(PaceKind.BEHIND, v.pace.kind)
        assertEquals(LocalDate(2026, 12, 8), assertNotNull(v.catchUp).pushTo)
        assertNull(PlanView.of(goal, spec.copy(asked = LocalDate(2026, 11, 1)), PlanInputs(), LocalDate(2026, 11, 2), emptyMap()).catchUp)
    }

    @Test
    fun statesFromTheGoalAndSettings() {
        val today = LocalDate(2026, 11, 2)
        assertEquals(PlanState.LET_GO, PlanView.of(goal.copy(isArchived = true), spec, PlanInputs(), today, emptyMap()).state)
        assertEquals(PlanState.DONE, PlanView.of(goal.copy(status = GoalStatus.COMPLETED), spec, PlanInputs(), today, emptyMap()).state)
        val paused = PlanView.of(goal, spec.copy(pausedFrom = today), PlanInputs(), today, emptyMap())
        assertEquals(PlanState.PAUSED, paused.state)
        assertNull(paused.catchUp)
        assertEquals(PlanArea.FITNESS, PlanView.of(goal.copy(category = GoalCategory.MONEY), spec, PlanInputs(), today, emptyMap()).area)
    }

    @Test
    fun timelineIsWorkedOutNeverAsked() {
        assertEquals(GoalTimeline.SHORT_TERM, PlanMaker.timelineFor(start, LocalDate(2026, 12, 1)))
        assertEquals(GoalTimeline.MID_TERM, PlanMaker.timelineFor(start, LocalDate(2027, 3, 1)))
        assertEquals(GoalTimeline.LONG_TERM, PlanMaker.timelineFor(start, LocalDate(2027, 9, 1)))
    }
}
