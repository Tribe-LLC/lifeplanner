package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.data.plans.DraftStep
import az.tribe.lifeplanner.data.plans.SuggestedStep
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.PlanTrack
import az.tribe.lifeplanner.domain.service.RoutineKind
import az.tribe.lifeplanner.ui.v4.plans.PlanSheetModel
import az.tribe.lifeplanner.ui.v4.plans.SheetContext
import az.tribe.lifeplanner.ui.v4.plans.SheetInputs
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanSheetModelTest {

    private val today = LocalDate(2026, 9, 29)
    private val ctx = SheetContext(today, "EUR")

    private fun preview(i: SheetInputs) = assertNotNull(PlanSheetModel.preview(i, ctx))

    @Test
    fun aRunLineBecomesADatedPlanWithARoutine() {
        val p = preview(SheetInputs("Run a 5K by December"))
        assertEquals(PlanArea.FITNESS, p.area)
        assertEquals(LocalDate(2026, 12, 1), p.target)
        assertEquals(listOf("Fitness", "By Tue 1 Dec", "6 steps ready"), PlanSheetModel.pills(p))
        assertEquals("A date ahead, so this is a plan, not a run you did.", PlanSheetModel.understood(p))
        assertEquals("Get running shoes that fit", p.steps.first().title)
        assertTrue(PlanSheetModel.stepMeta(p.steps.first(), true, today).startsWith("This week, "))
        assertTrue(PlanSheetModel.stepMeta(p.steps[1], false, today).endsWith("Ticks itself"))
        val d = p.draft("area")
        assertEquals(PlanTrack.RUN, d.track)
        assertEquals(RoutineKind.FITNESS_WEEK, d.routine?.kind)
        assertEquals(p.steps.map { it.date }, d.steps.map { it.date })
    }

    @Test
    fun removingAStepAndTheRoutineIsWhatGetsSaved() {
        val p = preview(SheetInputs("Run a 5K by December", removed = setOf("t0"), routineOn = false))
        assertEquals(5, p.steps.size)
        assertNull(p.draft("area").routine)
        assertEquals("Off. You can add one later.", PlanSheetModel.routineLine(p))
    }

    @Test
    fun savingKnowsWhatIsAlreadyThere() {
        val p = preview(SheetInputs("Save 2000 for Japan by March", answer = "500.0"))
        assertEquals(PlanArea.MONEY, p.area)
        assertTrue("€2,000" in PlanSheetModel.pills(p))
        assertTrue(p.steps.any { it.done && it.title.startsWith("€500") })
        val d = p.draft("area")
        assertEquals("EUR", d.currency)
        assertEquals(500.0, d.baseline)
        assertEquals("An amount and a date, so this is a savings plan, not a spend.", PlanSheetModel.understood(p))
        assertEquals(3000.0, preview(SheetInputs("Save 2000 for Japan by March", amount = 3000.0)).recipe.target)
    }

    @Test
    fun noDateMeansASuggestedOne() {
        val p = preview(SheetInputs("Learn Spanish basics"))
        assertFalse(p.dated)
        assertEquals("No date given, so we suggest 3 months. You can change it.", PlanSheetModel.understood(p))
        val picked = preview(SheetInputs("Learn Spanish basics", target = LocalDate(2027, 3, 1)))
        assertTrue(picked.dated)
        assertEquals(LocalDate(2027, 3, 1), picked.target)
        assertEquals(LocalDate(2027, 3, 1), picked.steps.last().date)
    }

    @Test
    fun nothingMatchedAsksForStepsThenRunsAStepAWeek() {
        val bare = preview(SheetInputs("Build a garden shed"))
        assertTrue(bare.needsSteps)
        assertTrue(bare.steps.isEmpty())
        assertEquals("No ready plan", PlanSheetModel.pills(bare).last())

        val coach = (1..5).map { SuggestedStep("Step $it", it) }
        val p = preview(SheetInputs("Build a garden shed", suggested = coach))
        assertFalse(p.needsSteps)
        assertTrue(p.open)
        assertEquals(LocalDate(2026, 10, 3), p.steps.first().date)
        assertEquals(LocalDate(2026, 10, 31), p.target)
        assertEquals("No date, a step a week", PlanSheetModel.dateFact(p))
        assertTrue(p.draft("area").suggestedByCoach)
        assertTrue(PlanSheetModel.savedLine(p).startsWith("Build a garden shed, one step at a time."))
    }

    @Test
    fun ownStepsLandAWeekApartAndStayBeforeTheDate() {
        val p = preview(SheetInputs("Build a garden shed", own = true))
        assertFalse(p.needsSteps)
        val first = PlanSheetModel.nextStepDate(p)
        assertEquals(LocalDate(2026, 10, 3), first)
        val two = preview(SheetInputs("Build a garden shed", own = true, added = listOf(DraftStep("Measure", first))))
        assertEquals(LocalDate(2026, 10, 10), PlanSheetModel.nextStepDate(two))

        val dated = preview(SheetInputs("Build a garden shed by 5 October", own = true, added = listOf(DraftStep("Measure", first))))
        assertEquals(LocalDate(2026, 10, 5), PlanSheetModel.nextStepDate(dated))
    }

    @Test
    fun aMovedStepKeepsItsNewDay() {
        val moved = LocalDate(2026, 10, 20)
        val p = preview(SheetInputs("Run a 5K by December", moved = mapOf("t1" to moved)))
        assertEquals(moved, p.steps.first { it.key == "t1" }.date)
    }

    @Test
    fun ideasComeFromTheAreaOrTheExamples() {
        assertEquals("Run a 10K", PlanSheetModel.ideas(PlanArea.FITNESS, "EUR").first())
        assertEquals(4, PlanSheetModel.ideas(null, "EUR").size)
        assertNull(PlanSheetModel.preview(SheetInputs("   "), ctx))
    }
}
