package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.data.health.WorkoutKind
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.RingState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FitnessWeekTest {

    // A Monday.
    private val today = LocalDate(2026, 9, 28)

    private fun w(date: LocalDate, status: LogStatus = LogStatus.DONE, kind: LogKind = LogKind.WORKOUT) = LifeLog(
        id = "$date$status", area = PlanArea.FITNESS, kind = kind, status = status, title = "Run",
        occurredAt = LocalDateTime(date, LocalTime(18, 0)),
    )

    @Test
    fun ringsCoverTheLastSevenDaysEndingToday() {
        val rings = FitnessWeek.rings(emptyList(), today)
        assertEquals(7, rings.size)
        assertEquals(LocalDate(2026, 9, 22), rings.first().date)
        assertEquals(today, rings.last().date)
        assertEquals("M", rings.last().label)
        assertEquals(RingState.TODAY, rings.last().state)
        assertEquals(RingState.REST, rings.first().state)
    }

    @Test
    fun doneBeatsPlannedAndPastPlannedIsMissed() {
        val sat = LocalDate(2026, 9, 26)
        val sun = LocalDate(2026, 9, 27)
        val logs = listOf(w(sat, LogStatus.PLANNED), w(sun, LogStatus.PLANNED), w(sun, LogStatus.DONE), w(today, LogStatus.DONE))
        val rings = FitnessWeek.rings(logs, today).associateBy { it.date }
        assertEquals(RingState.MISSED, rings.getValue(sat).state)
        assertEquals(RingState.DONE, rings.getValue(sun).state)
        assertEquals(RingState.DONE, rings.getValue(today).state)
    }

    @Test
    fun onlyDoneWorkoutsInTheWindowCount() {
        val logs = listOf(
            w(today), w(LocalDate(2026, 9, 22)), w(LocalDate(2026, 9, 21)),
            w(today, LogStatus.PLANNED), w(today, kind = LogKind.MEAL),
        )
        assertEquals(2, FitnessWeek.doneInLastWeek(logs, today))
    }

    @Test
    fun swapGoesToTheFirstDayWithNoWorkout() {
        val logs = listOf(w(LocalDate(2026, 9, 29), LogStatus.PLANNED), w(LocalDate(2026, 9, 30), LogStatus.PLANNED))
        assertEquals(LocalDate(2026, 10, 1), FitnessWeek.nextFreeDay(logs, today))
        assertEquals(LocalDate(2026, 9, 29), FitnessWeek.nextFreeDay(emptyList(), today))
    }

    @Test
    fun workoutKindComesFromWhatWasTyped() {
        assertEquals(WorkoutKind.STRENGTH, WorkoutKind.fromTitle("Leg day"))
        assertEquals(WorkoutKind.RUN, WorkoutKind.fromTitle("Morning 5k"))
        assertEquals(WorkoutKind.WALK, WorkoutKind.fromTitle("Walk"))
        assertEquals(WorkoutKind.YOGA, WorkoutKind.fromTitle("Evening stretch"))
        assertEquals(WorkoutKind.OTHER, WorkoutKind.fromTitle("Tennis"))
    }
}
