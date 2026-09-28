package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.FitnessBreak
import az.tribe.lifeplanner.domain.service.FitnessStreak
import az.tribe.lifeplanner.domain.service.WeekSlot
import az.tribe.lifeplanner.domain.service.WorkoutNotes
import az.tribe.lifeplanner.domain.service.WorkoutWeek
import az.tribe.lifeplanner.domain.service.WorkoutWeekPlan
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutWeekPlanTest {

    // A Monday.
    private val today = LocalDate(2026, 9, 28)
    private val seven = LocalTime(7, 0)

    private val week = WorkoutWeek(
        listOf(
            WeekSlot(DayOfWeek.MONDAY, "Strength", seven, 45),
            WeekSlot(DayOfWeek.WEDNESDAY, "Strength", seven, 45),
            WeekSlot(DayOfWeek.FRIDAY, "Strength", seven, 45),
            WeekSlot(DayOfWeek.SUNDAY, "Run", null, 30),
        ),
        gen = 2,
        toCalendar = true,
    )

    private fun d(offset: Int) = today.plus(DatePeriod(days = offset))

    @Test
    fun weekSurvivesEncodingAndDecoding() {
        val back = WorkoutWeek.decode(week.encode())
        assertEquals(week, back)
        assertNull(WorkoutWeek.decode(""))
        assertEquals(emptyList(), WorkoutWeek.decode("g=4;cal=0")?.slots)
    }

    @Test
    fun plansTheNextSevenDaysFromTheWeek() {
        val rows = WorkoutWeekPlan.wanted(week, today, LocalTime(6, 0))
        assertEquals(listOf(d(0), d(2), d(4), d(6)), rows.map { it.date })
        assertTrue(rows.all { it.status == LogStatus.PLANNED && it.source == LifeLog.SOURCE_PLAN && it.kind == LogKind.WORKOUT })
        assertEquals("fitweek-2-2026-09-28", rows.first().id)
        assertEquals(45, rows.first().durationMin)
        assertEquals(seven, rows.first().occurredAt.time)
        // "Any time" is stored at midnight, like every other untimed plan.
        assertEquals(LocalTime(0, 0), rows.last().occurredAt.time)
    }

    @Test
    fun theSameDayAlwaysGetsTheSameId() {
        val a = WorkoutWeekPlan.wanted(week, today, LocalTime(6, 0)).map { it.id to it.externalId }
        val b = WorkoutWeekPlan.wanted(week, today, LocalTime(6, 30)).map { it.id to it.externalId }
        assertEquals(a, b)
    }

    @Test
    fun aSlotThatAlreadyEndedTodayIsNotPlannedInThePast() {
        val rows = WorkoutWeekPlan.wanted(week, today, LocalTime(9, 0))
        assertFalse(rows.any { it.date == today })
        // Still planned when it has not ended yet.
        assertTrue(WorkoutWeekPlan.wanted(week, today, LocalTime(7, 30)).any { it.date == today })
    }

    @Test
    fun aRowAsPlannedIsUntouchedAndAMovedOneIsNot() {
        val row = WorkoutWeekPlan.wanted(week, today, LocalTime(6, 0)).first()
        assertTrue(WorkoutWeekPlan.untouched(row))
        assertEquals(2, WorkoutWeekPlan.genOf(row))
        val moved = row.copy(occurredAt = LocalDateTime(d(1), seven))
        assertFalse(WorkoutWeekPlan.untouched(moved))
        val later = row.copy(occurredAt = LocalDateTime(today, LocalTime(18, 0)))
        assertFalse(WorkoutWeekPlan.untouched(later))
    }

    @Test
    fun rowsFromAnOlderWeekGoUnlessSomeoneChangedThem() {
        val old = WorkoutWeekPlan.wanted(week, today, LocalTime(6, 0))
        val moved = old[1].copy(occurredAt = LocalDateTime(d(3), seven))
        val done = old[0].copy(status = LogStatus.DONE)
        val rows = listOf(done, moved, old[2], old[3])
        val next = week.copy(gen = 3)
        assertEquals(listOf(old[2], old[3]), WorkoutWeekPlan.stale(next, rows, today))
        // The same week keeps its rows.
        assertEquals(emptyList(), WorkoutWeekPlan.stale(week, rows, today))
        // Switching the week off clears what it planned and nobody touched.
        assertEquals(listOf(old[2], old[3]), WorkoutWeekPlan.stale(null, rows, today))
    }

    @Test
    fun otherPlannedWorkoutsAreNeverStale() {
        val mine = LifeLog(
            id = "x", area = PlanArea.FITNESS, kind = LogKind.WORKOUT, status = LogStatus.PLANNED, title = "Swim",
            occurredAt = LocalDateTime(d(1), seven), source = LifeLog.SOURCE_PLAN,
        )
        assertEquals(emptyList(), WorkoutWeekPlan.stale(null, listOf(mine), today))
        assertFalse(WorkoutWeekPlan.isGenerated(mine))
    }

    @Test
    fun summaryGroupsTheSameWorkout() {
        assertEquals(
            listOf("Mon, Wed, Fri: Strength, 07:00, 45 min", "Sun: Run, any time, 30 min"),
            WorkoutWeekPlan.summary(week),
        )
        assertEquals(emptyList(), WorkoutWeekPlan.summary(null))
    }

    @Test
    fun didLineIsKeptApartFromTheCoachNote() {
        val notes = WorkoutNotes.withDid("Moved from monday by your coach", "Squat 3x5 60kg")
        assertEquals("Squat 3x5 60kg", WorkoutNotes.did(notes))
        assertEquals("Moved from monday by your coach", WorkoutNotes.display(notes))
        assertNull(WorkoutNotes.did(WorkoutNotes.withDid(notes, " ")))
        assertNull(WorkoutNotes.display(WorkoutNotes.withDid(null, "Bench 3x8")))
    }

    @Test
    fun pausedMarkerComesAndGoes() {
        val p = WorkoutNotes.withPaused(null, true)
        assertTrue(WorkoutNotes.isPaused(p))
        assertNull(WorkoutNotes.display(p))
        assertNull(WorkoutNotes.withPaused(p, false))
    }

    @Test
    fun lastTimeFindsTheSameWorkoutFirstThenTheSameKind() {
        fun done(id: String, title: String, daysAgo: Int, did: String?) = LifeLog(
            id = id, area = PlanArea.FITNESS, kind = LogKind.WORKOUT, title = title,
            occurredAt = LocalDateTime(today.minus(DatePeriod(days = daysAgo)), seven), notes = WorkoutNotes.withDid(null, did),
        )
        val next = WorkoutWeekPlan.wanted(week, today, LocalTime(6, 0)).first()
        val logs = listOf(
            done("a", "Leg day", 1, "Squat 3x5 62.5kg"),
            done("b", "Strength", 4, "Squat 3x5 60kg"),
            done("c", "Strength", 2, null),
            done("d", "Run", 1, "5 km easy"),
        )
        assertEquals("b", WorkoutNotes.lastTime(logs, next)?.id)
        assertEquals("a", WorkoutNotes.lastTime(logs.filter { it.id != "b" }, next)?.id)
        assertNull(WorkoutNotes.lastTime(listOf(logs[3]), next))
    }

    // ── Streak ───────────────────────────────────────────────────────────────

    private fun workout(date: LocalDate) = LifeLog(
        id = "w$date", area = PlanArea.FITNESS, kind = LogKind.WORKOUT, title = "Run", occurredAt = LocalDateTime(date, seven),
    )

    /** [n] workouts in the week starting [weeksAgo] Mondays back. */
    private fun weekOf(weeksAgo: Int, n: Int) = (0 until n).map { workout(today.minus(DatePeriod(days = 7 * weeksAgo)).plus(DatePeriod(days = it))) }

    @Test
    fun countsFullWeeksInARowAndThisWeekDoesNotBreakIt() {
        val logs = weekOf(1, 3) + weekOf(2, 3) + weekOf(3, 4) + weekOf(5, 3)
        val s = FitnessStreak.of(logs, 3, today, emptyList())
        assertEquals(3, s.weeks)
        assertEquals(0, s.thisWeek)
        assertEquals("3 weeks" to "in a row at 3+", FitnessStreak.words(s))
    }

    @Test
    fun thisWeekCountsOnceTheGoalIsReached() {
        val wed = today.plus(DatePeriod(days = 2))
        val logs = weekOf(1, 3) + listOf(workout(today), workout(today.plus(DatePeriod(days = 1))), workout(wed))
        assertEquals(2, FitnessStreak.of(logs, 3, wed, emptyList()).weeks)
    }

    @Test
    fun aBreakFreezesAShortWeekInsteadOfEndingTheRun() {
        val logs = weekOf(1, 3) + weekOf(2, 1) + weekOf(3, 3)
        val sick = FitnessBreak(today.minus(DatePeriod(days = 12)), today.minus(DatePeriod(days = 9)))
        assertEquals(1, FitnessStreak.of(logs, 3, today, emptyList()).weeks)
        assertEquals(2, FitnessStreak.of(logs, 3, today, listOf(sick)).weeks)
    }

    @Test
    fun wordsAreKindWhenThereIsNoStreakOrABreak() {
        val none = FitnessStreak.of(listOf(workout(today)), 3, today, emptyList())
        assertEquals("2 more" to "this week starts a streak", FitnessStreak.words(none))
        val rest = FitnessStreak.of(emptyList(), 3, today, listOf(FitnessBreak(today, today.plus(DatePeriod(days = 6)))))
        assertEquals("Paused" to "your streak waits", FitnessStreak.words(rest))
    }

    @Test
    fun breakReadsBackFromText() {
        val b = FitnessBreak(today, today.plus(DatePeriod(days = 6)))
        assertEquals(b, FitnessBreak.decode(b.encode()))
        assertNull(FitnessBreak.decode("2026-10-05..2026-10-01"))
        assertNull(FitnessBreak.decode("nonsense"))
        assertEquals(today, FitnessStreak.weekStart(today.plus(DatePeriod(days = 6))))
    }
}
