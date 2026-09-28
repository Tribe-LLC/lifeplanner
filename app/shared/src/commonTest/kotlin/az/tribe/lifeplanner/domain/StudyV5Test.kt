package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.data.study.ActiveStudy
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.StudyKind
import az.tribe.lifeplanner.domain.service.StudyPlanner
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Study v5: the pausable timer, repeating blocks, and "am I on track" per exam. */
class StudyV5Test {

    /** A Monday. */
    private val today = LocalDate(2026, 9, 28)
    private fun day(n: Int) = LocalDate.fromEpochDays(today.toEpochDays() + n)

    private fun row(
        title: String, kind: StudyKind, date: LocalDate, status: LogStatus = LogStatus.PLANNED, minutes: Int? = null,
        notes: String? = null, quantity: Double? = null, id: String = "$title-$kind-$date-$status",
    ) = LifeLog(
        id = id, area = PlanArea.STUDY, kind = LogKind.STUDY, status = status, title = title, category = kind.key,
        durationMin = minutes, occurredAt = LocalDateTime(date, LocalTime(0, 0)), notes = notes, quantity = quantity,
    )

    // ── Timer ──

    @Test
    fun pausedTimeNeverCounts() {
        val start = 1_000_000L
        val a = ActiveStudy(start, "Maths", null, 45)
        assertEquals(10 * 60_000L, a.elapsedMs(start + 10 * 60_000L))
        val paused = a.pausedAt(start + 10 * 60_000L)
        assertTrue(paused.paused)
        // Twenty minutes on a break: the clock stays at ten.
        assertEquals(10 * 60_000L, paused.elapsedMs(start + 30 * 60_000L))
        val resumed = paused.resumedAt(start + 30 * 60_000L)
        assertFalse(resumed.paused)
        assertEquals(20 * 60_000L, resumed.pausedTotalMs)
        assertEquals(15 * 60_000L, resumed.elapsedMs(start + 35 * 60_000L))
        // A chronometer counts from the start moved on by the break.
        assertEquals(start + 20 * 60_000L, resumed.chronoBaseMs)
    }

    @Test
    fun pauseAndResumeTwiceAreHarmless() {
        val a = ActiveStudy(0, "Maths", null, 25).pausedAt(60_000)
        assertEquals(a, a.pausedAt(120_000))
        val r = a.resumedAt(120_000)
        assertEquals(r, r.resumedAt(180_000))
    }

    @Test
    fun targetMovesWithPausesAndIsGoneOncePassedOrPaused() {
        val a = ActiveStudy(0, "Maths", null, 45)
        assertEquals(45 * 60_000L, a.targetAtMs(10 * 60_000L))
        val afterBreak = a.pausedAt(10 * 60_000L).resumedAt(20 * 60_000L)
        assertEquals(55 * 60_000L, afterBreak.targetAtMs(20 * 60_000L))
        assertNull(a.pausedAt(10 * 60_000L).targetAtMs(12 * 60_000L))
        assertNull(a.targetAtMs(50 * 60_000L))
    }

    // ── Repeats ──

    @Test
    fun repeatDaysRoundTripThroughNotes() {
        val days = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)
        val note = StudyPlanner.daysNote(days)
        assertEquals("days: MON,WED", note)
        val r = row("Maths", StudyKind.ROUTINE, today, notes = note + "\ncalendar: yes")
        assertEquals(days, StudyPlanner.repeatDays(r))
        assertTrue(StudyPlanner.isRepeat(r))
        assertFalse(StudyPlanner.isDated(r))
    }

    @Test
    fun describesDaysPlainly() {
        assertEquals("Mon and Wed", StudyPlanner.describeDays(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)))
        assertEquals("Mon, Wed and Fri", StudyPlanner.describeDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)))
        assertEquals("weekdays", StudyPlanner.describeDays(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)))
        assertEquals("every day", StudyPlanner.describeDays(DayOfWeek.entries.toSet()))
        assertEquals("Sun", StudyPlanner.describeDays(setOf(DayOfWeek.SUNDAY)))
    }

    @Test
    fun fillsTheNextSevenDaysOnceEach() {
        val monWed = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        // New repeat, starting today: this Monday and Wednesday. Next Monday is the 8th day, not yet.
        assertEquals(listOf(today, day(2)), StudyPlanner.datesToFill(monWed, day(-1), today))
        // Filled through Wednesday: a second run the same day makes nothing.
        assertEquals(emptyList(), StudyPlanner.datesToFill(monWed, day(2), today))
        // Next day, the window reaches next Monday.
        assertEquals(listOf(day(7)), StudyPlanner.datesToFill(monWed, day(2), day(1)))
        // After three weeks away, days in the past are never made up, only the week ahead.
        assertEquals(listOf(day(21), day(23)), StudyPlanner.datesToFill(monWed, day(2), day(21)))
        assertEquals(emptyList(), StudyPlanner.datesToFill(emptySet(), day(-1), today))
    }

    @Test
    fun newRepeatStartsTomorrowWhenTodaysTimeHasPassed() {
        assertEquals(day(-1), StudyPlanner.firstFilledThrough(today, LocalTime(17, 0), LocalTime(18, 0)))
        assertEquals(today, StudyPlanner.firstFilledThrough(today, LocalTime(19, 0), LocalTime(18, 0)))
        assertEquals(day(-1), StudyPlanner.firstFilledThrough(today, LocalTime(23, 0), null))
    }

    @Test
    fun repeatBlocksHaveOneIdPerDay() {
        assertEquals("r1-2026-09-28", StudyPlanner.repeatBlockId("r1", today))
        assertTrue(StudyPlanner.repeatBlockId("r1", today) != StudyPlanner.repeatBlockId("r1", day(7)))
    }

    // ── On track ──

    @Test
    fun trackCountsDoneBlocksSessionsAndPlannedAgainstNeeded() {
        val exam = row("Chemistry midterm", StudyKind.EXAM, day(16), id = "exam")
        val logs = listOf(
            exam,
            row("Chemistry midterm", StudyKind.BLOCK, day(-3), LogStatus.DONE, 60, notes = "exam"),
            row("Chemistry midterm", StudyKind.SESSION, day(-1), LogStatus.DONE, 60),
            row("Chemistry midterm", StudyKind.BLOCK, day(2), minutes = 45, notes = "exam"),
            row("Chemistry midterm", StudyKind.BLOCK, day(5), minutes = 45, notes = "exam"),
            // Slipped and not moved: not counted as planned.
            row("Chemistry midterm", StudyKind.BLOCK, day(-2), minutes = 45, notes = "exam", id = "old"),
            // Other subjects never count.
            row("Biology", StudyKind.SESSION, day(-1), LogStatus.DONE, 90),
        )
        val t = StudyPlanner.track(exam, logs, today)
        assertEquals(600, t.neededMin)
        assertEquals(120, t.doneMin)
        assertEquals(90, t.plannedMin)
        assertEquals(390, t.shortMin)
        assertEquals(45, t.blockMin)
        assertFalse(t.onTrack)
        assertEquals(9, t.blocksToAdd)
        assertEquals("6h 30m short", StudyPlanner.shortLine(t.shortMin))
        assertEquals("2h done, 1h 30m planned, of 10h", StudyPlanner.trackLine(t))
    }

    @Test
    fun hoursNeededComeFromTheRowOrTheKind() {
        assertEquals(10, StudyPlanner.neededHours(row("Bio", StudyKind.EXAM, day(5))))
        assertEquals(6, StudyPlanner.neededHours(row("Essay", StudyKind.DEADLINE, day(5))))
        assertEquals(3, StudyPlanner.neededHours(row("Essay", StudyKind.DEADLINE, day(5), quantity = 3.0)))
    }

    @Test
    fun onTrackAddsNothingAndNoDaysLeftAddsNothing() {
        val exam = row("Essay", StudyKind.DEADLINE, day(3), id = "e", quantity = 1.0)
        val logs = listOf(exam, row("Essay", StudyKind.BLOCK, day(1), minutes = 60, notes = "e"))
        val t = StudyPlanner.track(exam, logs, today)
        assertTrue(t.onTrack)
        assertEquals(0, t.blocksToAdd)
        val dueToday = row("Quiz", StudyKind.EXAM, today, id = "q")
        assertEquals(0, StudyPlanner.track(dueToday, listOf(dueToday), today).blocksToAdd)
    }

    @Test
    fun blocksToAddIsCappedByDaysLeft() {
        val exam = row("Bio", StudyKind.EXAM, day(2), id = "b")
        val t = StudyPlanner.track(exam, listOf(exam), today)
        // 10h short with two days left: at most two a day.
        assertEquals(4, t.blocksToAdd)
    }

    @Test
    fun shortLineRoundsUp() {
        assertEquals("3h short", StudyPlanner.shortLine(180))
        assertEquals("3h short", StudyPlanner.shortLine(155))
        assertEquals("2h 30m short", StudyPlanner.shortLine(140))
        assertEquals("40 min short", StudyPlanner.shortLine(38))
        assertEquals("5 min short", StudyPlanner.shortLine(1))
    }
}
