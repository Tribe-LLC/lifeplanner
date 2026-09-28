package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.data.habits.HabitRow
import az.tribe.lifeplanner.data.habits.NudgePlan
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.service.HabitLearning
import az.tribe.lifeplanner.domain.service.HabitSchedule
import az.tribe.lifeplanner.domain.service.Schedule
import az.tribe.lifeplanner.ui.v4.habits.StarterDeck
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NudgePlanTest {
    // A Monday.
    private val today = LocalDate(2026, 9, 28)
    private fun ago(n: Int) = today.minus(DatePeriod(days = n))

    private fun row(title: String, doneToday: Boolean = false, slip: HabitLearning.Slip? = null, schedule: Schedule = Schedule.Daily): HabitRow {
        val h = Habit(id = title, title = title, category = az.tribe.lifeplanner.domain.enum.GoalCategory.WELLBEING, frequency = az.tribe.lifeplanner.domain.enum.HabitFrequency.DAILY, createdAt = LocalDateTime(ago(60), LocalTime(8, 0)))
        val stats = HabitSchedule.stats(schedule, if (doneToday) setOf(today) else emptySet(), emptySet(), today, ago(60))
        return HabitRow(h, schedule, stats, doneToday, 0, if (doneToday) setOf(today) else emptySet(), emptySet(), emptyList(), slip = slip)
    }

    @Test
    fun checkInTimeIsWhenTheEveningUsuallyEnds() {
        // Last ticks of five evenings: 21:05, 21:20, 21:40, 20:50, 21:30. Earlier ticks on those days do not count.
        val ticks = listOf(1 to (21 to 5), 2 to (21 to 20), 3 to (21 to 40), 4 to (20 to 50), 5 to (21 to 30))
            .flatMap { (d, t) -> listOf(ago(d) to LocalDateTime(ago(d), LocalTime(8, 0)), ago(d) to LocalDateTime(ago(d), LocalTime(t.first, t.second))) }
        assertEquals(21 * 60 + 15, HabitLearning.checkInMinute(ticks))
        assertNull(HabitLearning.checkInMinute(ticks.take(8)))
    }

    @Test
    fun eveningNudgeOnlyWhileSomethingIsOpen() {
        val now = LocalDateTime(today, LocalTime(18, 0))
        val open = NudgePlan.checkIns(listOf(row("Read"), row("Water", doneToday = true)), now, 21 * 60)
        assertEquals("1 habit still open. Swipe through it in a minute.", open.first().body)
        assertEquals(LocalDateTime(today, LocalTime(21, 0)), open.first().at)
        assertEquals(NudgePlan.DAYS_AHEAD, open.size)
        // All done today: nothing tonight, the rest of the week stays planned.
        val done = NudgePlan.checkIns(listOf(row("Read", doneToday = true)), now, 21 * 60)
        assertTrue(done.none { it.at.date == today })
        // Past the time already: starts tomorrow.
        assertTrue(NudgePlan.checkIns(listOf(row("Read")), LocalDateTime(today, LocalTime(22, 0)), 21 * 60).none { it.at.date == today })
    }

    @Test
    fun eveningNudgeSkipsDaysWithNothingScheduled() {
        val weekend = row("Long run", schedule = Schedule.Days(setOf(DayOfWeek.SATURDAY)))
        val plan = NudgePlan.checkIns(listOf(weekend), LocalDateTime(today, LocalTime(9, 0)), 21 * 60)
        assertEquals(listOf(DayOfWeek.SATURDAY), plan.map { it.at.dayOfWeek })
    }

    @Test
    fun slipNudgeIsWeeklyAndOnlyWhenSomethingSlipped() {
        val now = LocalDateTime(today, LocalTime(12, 0))
        val slip = HabitLearning.Slip(4, 0, 2, 20, false)
        assertNull(NudgePlan.slip(listOf(row("Read")), now, null))
        val n = assertNotNull(NudgePlan.slip(listOf(row("Read", slip = slip)), now, null))
        assertEquals(LocalDateTime(LocalDate(2026, 10, 3), LocalTime(10, 0)), n.at)
        assertEquals("Read has slipped", n.title)
        // Last Saturday's went out: this Saturday is a week later, so it is fine again.
        assertEquals(n.at, NudgePlan.slip(listOf(row("Read", slip = slip)), now, ago(2))?.at)
        // One already planned for this Saturday stays put (and keeps its text current).
        assertEquals(n.at, NudgePlan.slip(listOf(row("Read", slip = slip), row("Walk", slip = slip)), now, n.at.date)?.at)
    }

    @Test
    fun starterDeckTakesTurnsBetweenPickedAreas() {
        val deck = StarterDeck.forAreas(setOf(PlanArea.FITNESS, PlanArea.MIND), health = false)
        assertEquals(listOf(PlanArea.HABITS, PlanArea.FITNESS, PlanArea.MIND), deck.take(3).map { it.area })
        assertTrue(deck.none { it.starter.health != null })
        assertTrue(StarterDeck.forAreas(emptySet(), health = true).all { it.area == PlanArea.HABITS })
    }
}
