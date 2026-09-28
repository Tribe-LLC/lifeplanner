package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.enum.HabitCompletionSource
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.MindCheckIns
import az.tribe.lifeplanner.domain.service.SelfTick
import az.tribe.lifeplanner.testutil.testHabit
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelfTickTest {
    private val at = LocalDateTime(2026, 9, 28, 9, 0)
    private fun log(kind: LogKind, area: PlanArea, status: LogStatus = LogStatus.DONE, category: String? = null, minutes: Int? = null) =
        LifeLog(id = "$kind$status$category$minutes", area = area, kind = kind, status = status, title = "x", category = category, occurredAt = at, durationMin = minutes)

    @Test
    fun choiceRoundTripsThroughTheHabitFields() {
        SelfTick.entries.forEach { t ->
            val (source, metric, target) = SelfTick.fields(t)
            val h = testHabit(completionSource = source).copy(healthMetricType = metric, healthTarget = target)
            assertEquals(t, SelfTick.of(h))
        }
        assertEquals(8_000.0, SelfTick.fields(SelfTick.STEPS).third)
        assertEquals(HealthMetricType.SLEEP, SelfTick.fields(SelfTick.SLEEP).second)
    }

    @Test
    fun workoutNeedsAFinishedOne() {
        val h = testHabit(completionSource = HabitCompletionSource.WORKOUT)
        assertNull(SelfTick.wanted(h, listOf(log(LogKind.WORKOUT, PlanArea.FITNESS, LogStatus.PLANNED))))
        assertEquals(1, SelfTick.wanted(h, listOf(log(LogKind.WORKOUT, PlanArea.FITNESS))))
    }

    @Test
    fun breathingFromMindfulLogs() {
        val h = testHabit(completionSource = HabitCompletionSource.BREATHING)
        assertNull(SelfTick.wanted(h, emptyList()))
        assertEquals(1, SelfTick.wanted(h, listOf(log(LogKind.NOTE, PlanArea.MIND, category = MindCheckIns.MINDFUL))))
    }

    @Test
    fun studyCountsMinutesUpToTheTarget() {
        val counted = testHabit(completionSource = HabitCompletionSource.FOCUS, targetCount = 25, unit = "min")
        val once = testHabit(completionSource = HabitCompletionSource.FOCUS)
        val s10 = log(LogKind.STUDY, PlanArea.STUDY, category = "session", minutes = 10)
        val s40 = log(LogKind.STUDY, PlanArea.STUDY, category = "block", minutes = 40)
        val exam = log(LogKind.STUDY, PlanArea.STUDY, category = "exam", minutes = 120)
        val planned = log(LogKind.STUDY, PlanArea.STUDY, LogStatus.PLANNED, category = "block", minutes = 45)
        assertEquals(10, SelfTick.wanted(counted, listOf(s10, exam, planned)))
        assertEquals(25, SelfTick.wanted(counted, listOf(s10, s40)))
        assertEquals(1, SelfTick.wanted(once, listOf(s10)))
        assertNull(SelfTick.wanted(counted, listOf(exam, planned)))
    }

    @Test
    fun manualAndHealthHabitsAreNotTickedFromLogs() {
        val workout = listOf(log(LogKind.WORKOUT, PlanArea.FITNESS))
        assertNull(SelfTick.wanted(testHabit(), workout))
        assertNull(SelfTick.wanted(testHabit().copy(healthMetricType = HealthMetricType.STEPS), workout))
    }
}
