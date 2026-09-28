package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.HabitSchedule
import az.tribe.lifeplanner.domain.service.Schedule
import az.tribe.lifeplanner.ui.v4.areas.V4HabitsViewModel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HabitScheduleTest {
    // Monday 28 September 2026.
    private val today = LocalDate(2026, 9, 28)
    private fun ago(n: Int) = today.minus(DatePeriod(days = n))
    private val since = LocalDate(2026, 1, 1)

    private fun habit(freq: HabitFrequency = HabitFrequency.DAILY) =
        Habit("h1", "Stretch", category = GoalCategory.WELLBEING, frequency = freq, createdAt = LocalDateTime(2026, 1, 1, 9, 0))

    @Test
    fun scheduleComesFromBudgetsFirstThenFrequency() {
        val h = habit(HabitFrequency.WEEKDAYS)
        assertEquals(Schedule.Days(HabitSchedule.WEEKDAYS), HabitSchedule.of(h, emptyList()))
        val mask = HabitSchedule.mask(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)).toDouble()
        val days = Budget("b", PlanArea.HABITS, HabitSchedule.METRIC_DAYS, "h1", mask, null, BudgetPeriod.WEEK)
        assertEquals(Schedule.Days(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)), HabitSchedule.of(h, listOf(days)))
        val week = Budget("w", PlanArea.HABITS, HabitSchedule.METRIC_WEEK, "h1", 3.0, null, BudgetPeriod.WEEK)
        assertEquals(Schedule.PerWeek(3), HabitSchedule.of(h, listOf(week)))
        // Another habit's row does not leak in.
        assertEquals(Schedule.Days(HabitSchedule.WEEKDAYS), HabitSchedule.of(h, listOf(week.copy(category = "other"))))
        assertEquals(Schedule.PerWeek(1), HabitSchedule.of(habit(HabitFrequency.WEEKLY), emptyList()))
    }

    @Test
    fun maskRoundTripsAndFrequencyMatches() {
        val set = setOf(DayOfWeek.TUESDAY, DayOfWeek.SUNDAY)
        assertEquals(set, HabitSchedule.daysOf(HabitSchedule.mask(set)))
        assertEquals(HabitFrequency.WEEKENDS, HabitSchedule.frequencyFor(Schedule.Days(HabitSchedule.WEEKENDS)))
        assertEquals(HabitFrequency.CUSTOM, HabitSchedule.frequencyFor(Schedule.Days(set)))
        assertEquals(HabitFrequency.DAILY, HabitSchedule.frequencyFor(Schedule.Days(DayOfWeek.entries.toSet())))
        assertEquals("Tue, Sun", HabitSchedule.describe(Schedule.Days(set)))
        assertEquals("3 times a week", HabitSchedule.describe(Schedule.PerWeek(3)))
    }

    @Test
    fun weekdayHabitKeepsItsStreakOverTheWeekend() {
        // Done Mon to Fri last week and today (Monday); the weekend is off.
        val done = (3..7).map { ago(it) }.toSet() + today
        val st = HabitSchedule.stats(Schedule.Days(HabitSchedule.WEEKDAYS), done, emptySet(), today, since)
        assertEquals(6, st.streak)
        assertTrue(st.dueToday)
        // The same days as a daily habit break at Sunday.
        assertEquals(1, HabitSchedule.stats(Schedule.Daily, done, emptySet(), today, since).streak)
    }

    @Test
    fun todayNotDoneYetNeverBreaksTheStreak() {
        val done = (1..4).map { ago(it) }.toSet()
        assertEquals(4, HabitSchedule.stats(Schedule.Daily, done, emptySet(), today, since).streak)
    }

    @Test
    fun skippedDaysAreNeutral() {
        val done = setOf(ago(1), ago(3), ago(4))
        val st = HabitSchedule.stats(Schedule.Daily, done, setOf(ago(2)), today, since)
        assertEquals(3, st.streak)
        val skipToday = HabitSchedule.stats(Schedule.Daily, done, setOf(ago(2), today), today, since)
        assertFalse(skipToday.dueToday)
        assertTrue(skipToday.skippedToday)
    }

    @Test
    fun scoreCountsOnlyDueDays() {
        // Weekdays habit over the last 30 days, done on every due day but two.
        val due = (1..29).map { ago(it) }.filter { it.dayOfWeek in HabitSchedule.WEEKDAYS }
        val done = due.drop(2).toSet()
        val st = HabitSchedule.stats(Schedule.Days(HabitSchedule.WEEKDAYS), done, emptySet(), today, since)
        assertEquals((due.size - 2).toFloat() / due.size, st.score)
        assertNull(HabitSchedule.stats(Schedule.Daily, emptySet(), emptySet(), today, today).score)
    }

    @Test
    fun timesAWeekCountsWeeks() {
        // Three a week: the last two full weeks made it, this week is pending (only Monday so far).
        val done = setOf(ago(7), ago(5), ago(3), ago(14), ago(12), ago(9))
        val st = HabitSchedule.stats(Schedule.PerWeek(3), done, emptySet(), today, since)
        assertTrue(st.streakInWeeks)
        assertEquals(2, st.streak)
        assertTrue(st.dueToday)
        assertEquals(0, st.doneThisWeek)
        // A week that fell short breaks it.
        val short = setOf(ago(7), ago(5), ago(14), ago(12), ago(9))
        assertEquals(0, HabitSchedule.stats(Schedule.PerWeek(3), short, emptySet(), today, since).streak)
        // Reached this week: still shown today because it was done today.
        val met = HabitSchedule.stats(Schedule.PerWeek(1), setOf(today), emptySet(), today, since)
        assertTrue(met.dueToday)
        assertEquals(1, met.doneThisWeek)
    }

    @Test
    fun slotsFollowTheReminderTime() {
        assertEquals(HabitSchedule.Slot.MORNING, HabitSchedule.slot("07:30"))
        assertEquals(HabitSchedule.Slot.AFTERNOON, HabitSchedule.slot("13:00"))
        assertEquals(HabitSchedule.Slot.EVENING, HabitSchedule.slot("21:30"))
        assertEquals(HabitSchedule.Slot.ANYTIME, HabitSchedule.slot(null))
    }

    @Test
    fun metaIsKind() {
        // Started 12 days ago and kept every day since.
        val st = HabitSchedule.stats(Schedule.Daily, (1..12).map { ago(it) }.toSet(), emptySet(), today, ago(12))
        assertEquals("12 day streak, 100% in 30 days", HabitSchedule.meta(Schedule.Daily, st, false))
        val week = HabitSchedule.stats(Schedule.PerWeek(3), setOf(today), emptySet(), today, since)
        assertEquals("1 of 3 this week", HabitSchedule.meta(Schedule.PerWeek(3), week, true))
    }

    @Test
    fun heatSharesOnlyCountDueHabits() {
        val h = habit()
        val row = az.tribe.lifeplanner.data.habits.HabitRow(
            habit = h, schedule = Schedule.Days(setOf(DayOfWeek.MONDAY)),
            stats = HabitSchedule.stats(Schedule.Days(setOf(DayOfWeek.MONDAY)), setOf(ago(7)), emptySet(), today, since),
            doneToday = false, countToday = 0, done = setOf(ago(7)), skipped = emptySet(), notes = emptyList(),
        )
        val (days, share) = V4HabitsViewModel.heat(listOf(row), today)
        assertEquals(V4HabitsViewModel.WEEKS * 7, days.size)
        // Mondays only: last Monday kept, earlier Mondays missed, today (pending) not counted.
        assertEquals(1f / (V4HabitsViewModel.WEEKS - 1), share)
        assertNull(days.last().level)
    }
}
