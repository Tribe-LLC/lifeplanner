package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.HabitLearning
import az.tribe.lifeplanner.domain.service.HabitSchedule
import az.tribe.lifeplanner.domain.service.Schedule
import az.tribe.lifeplanner.ui.v4.today.DayItem
import az.tribe.lifeplanner.ui.v4.today.DayItemType
import az.tribe.lifeplanner.ui.v4.today.V4TodayViewModel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HabitLearningTest {
    // A Monday.
    private val today = LocalDate(2026, 9, 28)
    private fun ago(n: Int) = today.minus(DatePeriod(days = n))
    private fun at(day: LocalDate, h: Int, m: Int = 0) = day to LocalDateTime(day, LocalTime(h, m))

    @Test
    fun usualTimeIsTheMedianOfSameDayTicks() {
        val ticks = listOf(at(ago(1), 21, 10), at(ago(2), 21, 40), at(ago(3), 21, 20), at(ago(4), 22, 0), at(ago(5), 20, 50))
        assertEquals(21 * 60 + 20, HabitLearning.usualMinute(ticks))
        assertEquals("21:15", HabitLearning.roughly(21 * 60 + 20))
        assertEquals(HabitSchedule.Slot.EVENING, HabitLearning.slotOf(21 * 60 + 20))
    }

    @Test
    fun fixingAPastDaySaysNothingAboutTheTime() {
        // Four real ticks and three written the next morning: not enough to learn from.
        val ticks = listOf(at(ago(1), 21), at(ago(2), 21), at(ago(3), 21), at(ago(4), 21)) +
            listOf(ago(5) to LocalDateTime(ago(4), LocalTime(8, 0)), ago(6) to LocalDateTime(ago(5), LocalTime(8, 0)), ago(7) to LocalDateTime(ago(6), LocalTime(8, 0)))
        assertNull(HabitLearning.usualMinute(ticks))
    }

    @Test
    fun reminderFarFromRealTimeIsFlagged() {
        assertTrue(HabitLearning.reminderIsOff(7 * 60 + 30, 21 * 60))
        assertFalse(HabitLearning.reminderIsOff(20 * 60, 21 * 60))
        assertFalse(HabitLearning.reminderIsOff(null, 21 * 60))
    }

    @Test
    fun learnsTheWeekdaysItReallyHappensOn() {
        // Daily habit, six weeks old, only ever done on Saturdays.
        val done = (1..42).map { ago(it) }.filter { it.dayOfWeek == DayOfWeek.SATURDAY }.toSet()
        assertEquals(setOf(DayOfWeek.SATURDAY), HabitLearning.learnedDays(Schedule.Daily, done, emptySet(), today, ago(42)))
        // Too young to say.
        assertNull(HabitLearning.learnedDays(Schedule.Daily, done, emptySet(), today, ago(20)))
        // Done every day, or on and off with no pattern: nothing to suggest.
        val all = (1..42).map { ago(it) }.toSet()
        assertNull(HabitLearning.learnedDays(Schedule.Daily, all, emptySet(), today, ago(42)))
        val every2nd = (1..42).filter { it % 2 == 0 }.map { ago(it) }.toSet()
        assertNull(HabitLearning.learnedDays(Schedule.Daily, every2nd, emptySet(), today, ago(42)))
    }

    @Test
    fun threeMissesInARowIsASlip() {
        val done = setOf(ago(5), ago(6))
        val slip = HabitLearning.slip(Schedule.Daily, done, emptySet(), emptySet(), today, ago(30), null)
        assertNotNull(slip)
        assertEquals(4, slip.missedInRow)
        assertEquals("Missed 4 in a row", HabitLearning.slipText(slip))
    }

    @Test
    fun skipsAndTripDaysDoNotMakeASlip() {
        val done = setOf(ago(4))
        // Yesterday skipped, the two before on a trip: nothing was missed.
        assertNull(HabitLearning.slip(Schedule.Daily, done, setOf(ago(1)), setOf(ago(2), ago(3)), today, ago(30), null))
        // Two misses only.
        assertNull(HabitLearning.slip(Schedule.Daily, setOf(ago(3)), emptySet(), emptySet(), today, ago(30), null))
    }

    @Test
    fun aHabitNeverStartedDoesNotCountItsAge() {
        val slip = HabitLearning.slip(Schedule.Daily, emptySet(), emptySet(), emptySet(), today, ago(50), null)
        assertNotNull(slip)
        assertTrue(slip.neverDone)
        assertEquals("Not started yet", HabitLearning.slipText(slip))
        val long = HabitLearning.slip(Schedule.Daily, setOf(ago(30)), emptySet(), emptySet(), today, ago(50), null)
        assertEquals("Missed for over 2 weeks", long?.let { HabitLearning.slipText(it) })
    }

    @Test
    fun manySkipsAlsoComeUpForReview() {
        val skipped = setOf(ago(1), ago(2), ago(4), ago(5), ago(6))
        val slip = HabitLearning.slip(Schedule.Daily, setOf(ago(3), ago(7)), skipped, emptySet(), today, ago(30), null)
        assertNotNull(slip)
        assertEquals("Skipped 5 of the last 7", HabitLearning.slipText(slip))
    }

    @Test
    fun newAndRecentlyReviewedHabitsAreLeftAlone() {
        assertNull(HabitLearning.slip(Schedule.Daily, emptySet(), emptySet(), emptySet(), today, ago(3), null))
        assertNull(HabitLearning.slip(Schedule.Daily, emptySet(), emptySet(), emptySet(), today, ago(60), ago(5)))
        assertNotNull(HabitLearning.slip(Schedule.Daily, emptySet(), emptySet(), emptySet(), today, ago(60), ago(20)))
    }

    @Test
    fun onlyScheduledDaysCount() {
        // Mon/Wed/Fri habit, done last Friday: Saturday and Sunday are not misses.
        val s = Schedule.Days(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertNull(HabitLearning.slip(s, setOf(ago(3)), emptySet(), emptySet(), today, ago(40), null))
        // Nothing since the Monday two weeks ago: Wed, Fri, Mon, Wed, Fri missed.
        assertEquals(5, HabitLearning.slip(s, setOf(ago(14)), emptySet(), emptySet(), today, ago(40), null)?.missedInRow)
    }

    @Test
    fun timesAWeekCountsWholeWeeks() {
        // 3 a week, nothing for the last three full weeks.
        val slip = HabitLearning.slip(Schedule.PerWeek(3), setOf(ago(30), ago(31), ago(33)), emptySet(), emptySet(), today, ago(60), null)
        assertNotNull(slip)
        assertTrue(slip.inWeeks)
        assertEquals("Missed 4 weeks in a row", HabitLearning.slipText(slip))
    }

    private fun item(key: String, minute: Int?, done: Boolean = false, checkable: Boolean = true) = DayItem(
        key = key, type = if (checkable) DayItemType.HABIT else DayItemType.EVENT, refId = key,
        time = null, title = key, area = if (checkable) PlanArea.HABITS else null, meta = "", done = done, checkable = checkable,
        usualMinute = minute,
    )

    @Test
    fun aLongDaySplitsIntoNowLaterAnytimeAndDone() {
        val items = listOf(
            item("stretch", 8 * 60), item("read", 21 * 60 + 30), item("floss", 23 * 60), item("water", null),
            item("bed", 7 * 60, done = true), item("standup", 10 * 60, checkable = false),
        )
        val groups = V4TodayViewModel.groups(items, nowMinute = 20 * 60).associate { it.id to it.items.map { i -> i.key } }
        // Morning stretch is still open, so it stays in view with what is due in the next 90 minutes.
        assertEquals(listOf("stretch", "read"), groups["now"])
        assertEquals(listOf("floss"), groups["later"])
        assertEquals(listOf("water"), groups["any"])
        assertEquals(listOf("bed"), groups["done"])
        // A meeting that ended hours ago drops out.
        assertFalse(groups.values.flatten().contains("standup"))
    }
}
