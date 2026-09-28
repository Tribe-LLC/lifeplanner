package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.data.habits.NudgePlan
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.MindInsights
import az.tribe.lifeplanner.domain.service.MoodYear
import az.tribe.lifeplanner.domain.service.SleepDebt
import az.tribe.lifeplanner.ui.v4.shell.V4Routes
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoodYearTest {
    private val today = LocalDate(2026, 9, 28)

    private fun checkIn(day: LocalDate, score: Int, hour: Int = 9, cat: String? = null, notes: String? = null) = LifeLog(
        "m$day$hour", PlanArea.MIND, LogKind.MOOD, title = "x", quantity = score.toDouble(), category = cat, notes = notes,
        occurredAt = LocalDateTime(day, LocalTime(hour, 0)),
    )

    @Test
    fun noDataStillShowsAFewMonthsEndingNow() {
        val rows = MoodYear.rows(today, emptyMap())
        assertEquals(MoodYear.MIN_MONTHS, rows.size)
        assertEquals(listOf("Jul", "Aug", "Sep"), rows.map { it.label })
        assertTrue(rows.last().levels.drop(28).all { it == MoodYear.FUTURE })
        assertTrue(rows.last().levels.take(28).all { it == MoodYear.EMPTY })
    }

    @Test
    fun startsAtTheFirstMonthWithAMoodButNeverMoreThanAYear() {
        val fromMarch = MoodYear.rows(today, mapOf(LocalDate(2026, 3, 14) to 4.0))
        assertEquals(Month.MARCH, fromMarch.first().first.month)
        assertEquals(7, fromMarch.size)
        assertEquals(4, fromMarch.first().levels[13])

        val old = MoodYear.rows(today, mapOf(LocalDate(2024, 1, 1) to 3.0))
        assertEquals(MoodYear.MAX_MONTHS, old.size)
        assertEquals(LocalDate(2025, 10, 1), old.first().first)
    }

    @Test
    fun monthsHaveTheirOwnLengthAndDaysRoundToALevel() {
        val rows = MoodYear.rows(LocalDate(2026, 3, 5), mapOf(LocalDate(2026, 2, 10) to 2.5, LocalDate(2026, 3, 1) to 1.2))
        val feb = rows.first { it.first.month == Month.FEBRUARY }
        assertEquals(28, feb.levels.size)
        assertEquals(3, feb.levels[9])
        assertEquals(1, rows.last().levels[0])
        assertEquals(1, feb.recorded)
        assertEquals(LocalDate(2026, 2, 10), feb.date(10))
    }

    @Test
    fun levelIsClamped() {
        assertEquals(1, MoodYear.level(0.2))
        assertEquals(5, MoodYear.level(7.0))
        assertEquals(4, MoodYear.level(3.6))
    }

    @Test
    fun describesAMonthForScreenReaders() {
        val rows = MoodYear.rows(today, mapOf(today to 4.0, today.minus(DatePeriod(days = 1)) to 5.0))
        assertEquals("September: 2 days, mostly good", MoodYear.describe(rows.last()))
        assertEquals("July: no check-ins", MoodYear.describe(rows.first()))
    }

    @Test
    fun dayNotePrefersTheLatestNoteThenFeelingsThenJournal() {
        val morning = checkIn(today, 3, 8, notes = "Slept badly")
        val evening = checkIn(today, 4, 20, cat = "Work, Friends", notes = "feel: Calm, Grateful")
        assertEquals("Slept badly", MoodYear.dayNote(listOf(morning, evening), listOf("Entry")))
        assertEquals("Calm, grateful. Part of it: work, friends", MoodYear.dayNote(listOf(evening), emptyList()))
        assertEquals("Journal: Why Monday felt heavy", MoodYear.dayNote(emptyList(), listOf(" ", "Why Monday felt heavy")))
        assertNull(MoodYear.dayNote(listOf(checkIn(today, 3)), emptyList()))
    }

    @Test
    fun dailyMoodFeedsTheGrid() {
        val daily = MindInsights.dailyMood(listOf(checkIn(today, 2), checkIn(today, 4, 20)), listOf(today.minus(DatePeriod(days = 3)) to 5))
        val row = MoodYear.rows(today, daily).last()
        assertEquals(3, row.levels[27])
        assertEquals(5, row.levels[24])
    }
}

class SleepDebtTest {
    @Test
    fun behindAddsUpNetOfLongNights() {
        val r = SleepDebt.of(listOf(6.5, 7.0, 6.0, 8.0, 7.0, 6.5, 6.0), 7.5)!!
        // 1 + 0.5 + 1.5 - 0.5 + 0.5 + 1 + 1.5 = 5.5h
        assertEquals(330, r.behindMin)
        assertEquals(7, r.nights)
        assertEquals("5h 30m behind over 7 nights", SleepDebt.line(r))
        assertFalse(r.onTarget)
    }

    @Test
    fun onTargetAndAheadAreSaidKindly() {
        val on = SleepDebt.of(listOf(7.4, 7.5, 7.6, 7.3), 7.5)!!
        assertTrue(on.onTarget)
        assertEquals("On target over 4 nights", SleepDebt.line(on))
        val ahead = SleepDebt.of(listOf(8.5, 8.0, 9.0), 7.5)!!
        assertTrue(ahead.ahead)
        assertEquals("Ahead of your goal over 3 nights", SleepDebt.line(ahead))
    }

    @Test
    fun tooFewNightsOrMissingNightsSayNothing() {
        assertNull(SleepDebt.of(listOf(5.0, 6.0), 8.0))
        assertNull(SleepDebt.of(listOf(5.0, 0.0, 0.0), 8.0))
        assertNull(SleepDebt.of(listOf(5.0, 6.0, 6.0), 0.0))
    }

    @Test
    fun roundsToFiveMinutesAndFormats() {
        assertEquals(20, SleepDebt.of(listOf(7.0, 7.0, 6.65), 7.0)!!.behindMin)
        assertEquals("3h 20m", SleepDebt.duration(200))
        assertEquals("2h", SleepDebt.duration(120))
        assertEquals("45m", SleepDebt.duration(-45))
    }
}

class MoodNudgeTest {
    // A Monday.
    private val today = LocalDate(2026, 9, 28)

    @Test
    fun fixedTimeIsPlannedAWeekAheadWithOneIdPerWeekday() {
        val plan = NudgePlan.moods(LocalDateTime(today, LocalTime(9, 0)), 20 * 60, 42, recordedToday = false)
        assertEquals(NudgePlan.DAYS_AHEAD, plan.size)
        assertEquals(LocalDateTime(today, LocalTime(20, 0)), plan.first().at)
        assertEquals("v4_nudge_mood_monday", plan.first().id)
        assertEquals(plan.map { it.id }.toSet(), NudgePlan.moodIds().toSet())
        assertTrue(plan.all { it.open == NudgePlan.MOOD && it.title == NudgePlan.MOOD_TITLE })
    }

    @Test
    fun todayIsSkippedOncePassedOrAlreadyRecorded() {
        val late = NudgePlan.moods(LocalDateTime(today, LocalTime(21, 0)), 20 * 60, 42, recordedToday = false)
        assertEquals(NudgePlan.DAYS_AHEAD - 1, late.size)
        assertEquals(today.plus(DatePeriod(days = 1)), late.first().at.date)
        val done = NudgePlan.moods(LocalDateTime(today, LocalTime(9, 0)), 20 * 60, 42, recordedToday = true)
        assertTrue(done.none { it.at.date == today })
        assertEquals(NudgePlan.moodId(today), "v4_nudge_mood_monday")
    }

    @Test
    fun surpriseTimeStaysInTheWindowAndStaysPut() {
        val from = NudgePlan.SURPRISE_FROM.hour * 60
        val to = NudgePlan.SURPRISE_TO.hour * 60
        val minutes = (0 until 60).map { NudgePlan.surpriseMinute(today.plus(DatePeriod(days = it)), 12345) }
        assertTrue(minutes.all { it in from..to && it % 5 == 0 })
        assertTrue(minutes.toSet().size > 10, "varies from day to day")
        assertEquals(NudgePlan.surpriseMinute(today, 12345), NudgePlan.surpriseMinute(today, 12345))
        assertNotEquals(
            (0 until 7).map { NudgePlan.surpriseMinute(today.plus(DatePeriod(days = it)), 1) },
            (0 until 7).map { NudgePlan.surpriseMinute(today.plus(DatePeriod(days = it)), 2) },
        )
        val plan = NudgePlan.moods(LocalDateTime(today, LocalTime(0, 1)), null, 12345, false)
        assertEquals(minutes.take(7), plan.map { it.at.hour * 60 + it.at.minute })
    }

    @Test
    fun opensTheMindPage() {
        assertEquals(V4Routes.area(PlanArea.MIND), NudgePlan.routeFor(NudgePlan.MOOD))
    }
}
