package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.service.PaceKind
import az.tribe.lifeplanner.domain.service.PlanScheduler
import az.tribe.lifeplanner.domain.service.StepDraft
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanSchedulerTest {

    private val start = LocalDate(2026, 9, 29) // a Tuesday
    private val dec1 = LocalDate(2026, 12, 1)

    @Test
    fun runStepsLandOnSundaysBetweenStartAndTarget() {
        val steps = listOf(
            StepDraft("Shoes", 0.0), StepDraft("1 km", 0.2, true), StepDraft("2 km", 0.4, true),
            StepDraft("3 km", 0.6, true), StepDraft("4 km", 0.8, true), StepDraft("5K", 1.0, true),
        )
        val d = PlanScheduler.date(steps, start, dec1, DayOfWeek.SUNDAY)
        assertEquals(LocalDate(2026, 10, 1), d[0])
        assertEquals(LocalDate(2026, 10, 11), d[1])
        assertEquals(LocalDate(2026, 10, 25), d[2])
        assertEquals(LocalDate(2026, 11, 8), d[3])
        assertTrue(d.drop(1).dropLast(1).all { it.dayOfWeek == DayOfWeek.SUNDAY })
        assertEquals(dec1, d.last())
        assertEquals(d.sorted(), d)
    }

    @Test
    fun doneStepsAreDatedTheStart() {
        val d = PlanScheduler.date(listOf(StepDraft("Setup", 0.0), StepDraft("€500", 0.05, auto = true, done = true), StepDraft("All", 1.0)), start, dec1)
        assertEquals(start, d[1])
        assertEquals(LocalDate(2026, 10, 1), d[0])
    }

    @Test
    fun noDateMeansAStepAWeekOnSaturdays() {
        assertEquals(
            listOf(LocalDate(2026, 10, 3), LocalDate(2026, 10, 10), LocalDate(2026, 10, 17)),
            PlanScheduler.weekly(3, start),
        )
    }

    @Test
    fun paceFromMissedStepsOnly() {
        val today = LocalDate(2026, 10, 30)
        assertEquals(PaceKind.ON_TRACK, PlanScheduler.pace(start, dec1, today, 0f, null).kind)
        val behind = PlanScheduler.pace(start, dec1, today, 0.33f, LocalDate(2026, 10, 25))
        assertEquals(PaceKind.BEHIND, behind.kind)
        assertEquals("A week behind", behind.label)
        assertEquals(5, behind.behindDays)
        assertEquals(PaceKind.ON_TRACK, PlanScheduler.pace(start, dec1, today, 0.5f, LocalDate(2026, 10, 28)).kind)
        assertEquals("3 weeks behind", PlanScheduler.pace(start, dec1, LocalDate(2026, 11, 15), 0.3f, LocalDate(2026, 10, 25)).label)
        assertEquals(PaceKind.AHEAD, PlanScheduler.pace(start, dec1, today, 0.8f, null).kind)
        assertEquals("Race day", PlanScheduler.pace(start, dec1, dec1, 0.8f, null, lastDayLabel = "Race day").label)
        assertEquals(PaceKind.PAST, PlanScheduler.pace(start, dec1, LocalDate(2026, 12, 5), 0.8f, null).kind)
        assertEquals(PaceKind.PAUSED, PlanScheduler.pace(start, dec1, today, 0.3f, LocalDate(2026, 10, 1), paused = true).kind)
        assertEquals(PaceKind.CATCHING_UP, PlanScheduler.pace(start, dec1, today, 0.3f, LocalDate(2026, 10, 20), keptOn = LocalDate(2026, 10, 27)).kind)
    }

    @Test
    fun theMarkerIsAnEvenPace() {
        val p = PlanScheduler.pace(start, dec1, LocalDate(2026, 10, 30), 0.5f, null)
        assertEquals(31f / 63f, p.expected)
    }

    @Test
    fun catchUpOffersOneMoreWeekAndAsksAgainAWeekLater() {
        val today = LocalDate(2026, 11, 2)
        val pace = PlanScheduler.pace(start, dec1, today, 0.33f, LocalDate(2026, 10, 25))
        val c = assertNotNull(PlanScheduler.catchUp(pace, dec1, today, null))
        assertEquals(1, c.pushWeeks)
        assertEquals(LocalDate(2026, 12, 8), c.pushTo)
        assertTrue(c.canKeep)
        assertNull(PlanScheduler.catchUp(pace, dec1, today, asked = LocalDate(2026, 10, 30)))
        assertNotNull(PlanScheduler.catchUp(pace, dec1, today, asked = LocalDate(2026, 10, 26)))
        assertNull(PlanScheduler.catchUp(PlanScheduler.pace(start, dec1, today, 0.5f, null), dec1, today, null))
    }

    @Test
    fun aPlanPastItsDateCannotKeepIt() {
        val today = LocalDate(2026, 12, 4)
        val pace = PlanScheduler.pace(start, dec1, today, 0.8f, LocalDate(2026, 11, 22))
        val c = assertNotNull(PlanScheduler.catchUp(pace, dec1, today, null))
        assertTrue(c.pushTo >= LocalDate(2026, 12, 11))
        assertEquals(false, c.canKeep)
    }

    @Test
    fun redateSpreadsTheRestWithTheLastOnTheDate() {
        val d = PlanScheduler.redate(3, LocalDate(2026, 11, 2), dec1)
        assertEquals(3, d.size)
        assertEquals(dec1, d.last())
        assertTrue(d.first() > LocalDate(2026, 11, 2))
        assertEquals(d.sorted(), d)
        assertEquals(listOf(LocalDate(2026, 11, 3)), PlanScheduler.redate(1, LocalDate(2026, 11, 2), LocalDate(2026, 10, 1)))
    }

    @Test
    fun pauseMovesEveryDateAndNeverIntoThePast() {
        val today = LocalDate(2026, 10, 10)
        val moved = PlanScheduler.shift(listOf(LocalDate(2026, 10, 5), null, LocalDate(2026, 10, 25)), 7, today)
        assertEquals(listOf(LocalDate(2026, 10, 12), null, LocalDate(2026, 11, 1)), moved)
        assertEquals(4, PlanScheduler.unusedPause(LocalDate(2026, 10, 13), today))
        assertEquals(0, PlanScheduler.unusedPause(LocalDate(2026, 10, 1), today))
    }

    @Test
    fun words() {
        assertEquals("Tue 1 Dec", PlanScheduler.dayLabel(dec1))
        assertEquals("9 weeks", PlanScheduler.spanLabel(start, dec1))
        assertEquals("5 months", PlanScheduler.spanLabel(start, LocalDate(2027, 3, 1)))
        assertEquals("3 months", PlanScheduler.spanLabel(start, LocalDate(2026, 12, 29)))
        assertEquals("a month left", PlanScheduler.leftLabel(dec1, LocalDate(2026, 11, 1)))
        assertEquals("that is today", PlanScheduler.leftLabel(dec1, dec1))
        assertEquals("5 weeks left", PlanScheduler.leftLabel(LocalDate(2026, 12, 8), LocalDate(2026, 11, 2)))
    }
}
