package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.PlanContext
import az.tribe.lifeplanner.domain.service.PlanLineParser
import az.tribe.lifeplanner.domain.service.PlanTemplates
import az.tribe.lifeplanner.domain.service.PlanTrack
import az.tribe.lifeplanner.domain.service.RoutineKind
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanTemplatesTest {

    private val today = LocalDate(2026, 9, 29)

    private fun recipe(line: String, answer: String? = null, minutes: Boolean = false, weight: Double? = null, preset: PlanArea? = null) =
        PlanLineParser.parse(line, today, "EUR", preset).let { l ->
            val target = l.target ?: today.plus(DatePeriod(days = 7 * PlanTemplates.defaultWeeks(l)))
            PlanTemplates.recipe(l, answer, PlanContext(today, target, "EUR", minutes, weight))
        }

    @Test
    fun fiveKFromNothing() {
        val r = recipe("Run a 5K by December")
        assertEquals(PlanTrack.RUN, r.track)
        assertEquals("Can you run 1 km without stopping today?", r.question!!.text)
        assertEquals(
            listOf("Get running shoes that fit", "Run 1 km without stopping", "Run 2 km", "Run 3 km", "Run 4 km", "Run the 5K"),
            r.steps.map { it.title },
        )
        assertEquals(listOf(false, true, true, true, true, true), r.steps.map { it.auto })
        assertEquals(5.0, r.target)
        assertEquals("Run a 10K", r.next)
        assertEquals(RoutineKind.FITNESS_WEEK, r.routine!!.kind)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY), r.routine!!.days)
        assertEquals(DayOfWeek.SUNDAY, r.weekday)
    }

    @Test
    fun theAnswerChangesTheSteps() {
        assertEquals(listOf("Get running shoes that fit", "Run 2 km", "Run 3 km", "Run 4 km", "Run the 5K"), recipe("Run a 5K", "yes").steps.map { it.title })
        assertEquals(listOf("Pick a 5K route or a race", "Run 4 km", "Run 5 km at an easy pace", "Run the 5K"), recipe("Run a 5K", "far").steps.map { it.title })
        assertEquals("3 km or more", recipe("Run a 5K").question!!.answers.last().label)
    }

    @Test
    fun healthOnlyRunnersGetMinutes() {
        val r = recipe("Run a 5K", minutes = true)
        assertTrue(r.steps.drop(1).dropLast(1).all { it.title.contains("min") })
        assertEquals("Run the 5K (about 35 min)", r.steps.last().title)
    }

    @Test
    fun savingsStepsCountWhatIsAlreadyThere() {
        val r = recipe("Save 2000 for Japan by March")
        assertEquals(PlanTrack.SAVE, r.track)
        assertEquals(listOf("Set a monthly transfer on payday", "€500 put aside", "€1,000 put aside", "€1,500 put aside", "€2,000, all of it"), r.steps.map { it.title })
        assertEquals("€400 a month gets you there, about €93 a week.", r.answerNote)
        val half = recipe("Save 2000 for Japan by March", "1000.0")
        assertEquals(listOf("Set a monthly transfer on payday", "€1,000 put aside", "€1,500 put aside", "€2,000, all of it"), half.steps.map { it.title })
        assertTrue(half.steps[1].done)
        assertEquals(1000.0, half.baseline)
        assertEquals(RoutineKind.MONTHLY, half.routine!!.kind)
        assertEquals(25, half.routine!!.dayOfMonth)
    }

    @Test
    fun learningSetsTheHoursFromTheMinutes() {
        val r = recipe("Learn Spanish basics", "20")
        assertEquals(PlanTrack.STUDY, r.track)
        assertEquals("Spanish", r.subject)
        assertEquals("Pick an app or a course", r.steps.first().title)
        assertEquals(RoutineKind.STUDY_REPEAT, r.routine!!.kind)
        assertEquals(20, r.routine!!.minutes)
        assertTrue(r.answerNote!!.startsWith("About 2"))
        assertEquals("Pick a course or a teacher", recipe("Learn to code").steps.first().title)
    }

    @Test
    fun weightUsesHealth() {
        val r = recipe("Lose 4 kg", weight = 82.0)
        assertEquals(PlanTrack.WEIGHT, r.track)
        assertEquals(82.0, r.baseline)
        assertEquals(listOf("Pick one small swap for every day", "1 kg down", "2 kg down", "3 kg down", "4 kg down, all of it"), r.steps.map { it.title })
        assertTrue(r.moves.contains("82 kg"))
        assertTrue(recipe("Lose 4 kg").moves.startsWith("Connect Health"))
    }

    @Test
    fun countingPlans() {
        val books = recipe("Read 12 books this year")
        assertEquals(PlanTrack.COUNT, books.track)
        assertEquals(listOf("3 books read", "6 books read", "9 books read", "12 books, all of them"), books.steps.map { it.title })
        assertEquals("book", books.countWord)
        val days = recipe("Meditate for 30 days")
        assertEquals(listOf("7 days", "14 days", "21 days", "30 days, all of them"), days.steps.map { it.title })
        assertEquals("Meditate", days.routine!!.title)
        assertEquals(PlanArea.MIND, days.area)
    }

    @Test
    fun everyAreaHasIdeasThatMatchATemplate() {
        PlanArea.entries.forEach { area ->
            val ideas = PlanTemplates.ideas(area, "EUR")
            assertEquals(3, ideas.size)
            ideas.forEach { idea -> assertNotNull(PlanLineParser.parse(idea, today, "EUR", area).template, "$idea should match a template") }
        }
    }

    @Test
    fun nothingMatchedHasNoStepsYet() {
        val r = recipe("Build a garden shed")
        assertNull(r.template)
        assertTrue(r.steps.isEmpty())
        assertEquals(DayOfWeek.SATURDAY, r.weekday)
        assertTrue(r.moves.contains("One step a week"))
    }

    @Test
    fun theAreaPageKeepsItsPlan() {
        assertEquals(PlanArea.CAREER, recipe("Learn SQL", preset = PlanArea.CAREER).area)
        assertEquals(PlanArea.FITNESS, recipe("Run a 5K", preset = PlanArea.STUDY).area)
    }
}
