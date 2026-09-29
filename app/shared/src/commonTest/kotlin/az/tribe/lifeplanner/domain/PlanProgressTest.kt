package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.Bills
import az.tribe.lifeplanner.domain.service.PlanInputs
import az.tribe.lifeplanner.domain.service.PlanProgress
import az.tribe.lifeplanner.domain.service.PlanSpec
import az.tribe.lifeplanner.domain.service.PlanTemplates
import az.tribe.lifeplanner.domain.service.PlanTrack
import az.tribe.lifeplanner.domain.service.RoutineKind
import az.tribe.lifeplanner.domain.service.StudyTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanProgressTest {

    private val start = LocalDate(2026, 9, 29)
    private val today = LocalDate(2026, 10, 30)

    private fun goal(id: String, vararg steps: Pair<String, Boolean>) = Goal(
        id = id, category = GoalCategory.BODY, title = "Plan", description = "", status = GoalStatus.IN_PROGRESS,
        timeline = GoalTimeline.SHORT_TERM, dueDate = LocalDate(2026, 12, 1), createdAt = LocalDateTime(2026, 9, 29, 9, 0),
        milestones = steps.mapIndexed { i, (t, done) -> Milestone("$id-$i", t, done) },
    )

    private fun log(kind: LogKind, title: String, day: LocalDate, qty: Double? = null, unit: String? = null, min: Int? = null, amount: Double? = null, category: String? = null, ext: String? = null, area: PlanArea = PlanArea.FITNESS, notes: String? = null) =
        LifeLog(id = "$title-$day-$qty-$amount", area = area, kind = kind, title = title, quantity = qty, unit = unit, durationMin = min, amount = amount, currency = "EUR",
            category = category, externalId = ext, occurredAt = LocalDateTime(day, kotlinx.datetime.LocalTime(7, 40)), notes = notes)

    @Test
    fun thresholdsComeFromTheWords() {
        assertEquals(3.0, PlanProgress.threshold("Run 3 km", PlanTrack.RUN)!!.km)
        assertEquals(5.0, PlanProgress.threshold("Run the 5K", PlanTrack.RUN)!!.km)
        assertEquals(35, PlanProgress.threshold("Run the 5K (about 35 min)", PlanTrack.RUN)!!.minutes)
        assertEquals(21.1, PlanProgress.threshold("Run the half marathon", PlanTrack.RUN)!!.km)
        assertNull(PlanProgress.threshold("Get running shoes that fit", PlanTrack.RUN))
        assertEquals(1500.0, PlanProgress.threshold("€1,500 put aside", PlanTrack.SAVE)!!.amount)
        assertEquals(2.0, PlanProgress.threshold("2 kg down", PlanTrack.WEIGHT)!!.kg)
        assertEquals(10, PlanProgress.threshold("10 applications sent", PlanTrack.APPLICATIONS)!!.count)
        assertTrue(PlanProgress.threshold("First interview", PlanTrack.APPLICATIONS)!!.interview)
        assertNull(PlanProgress.threshold("Greetings and numbers", PlanTrack.STUDY))
    }

    @Test
    fun runsTickDistanceStepsAndSayWhich() {
        val g = goal("run", "Get running shoes that fit" to true, "Run 1 km" to false, "Run 2 km" to false, "Run 3 km" to false, "Run the 5K" to false)
        val spec = PlanSpec(goalId = "run", area = PlanArea.FITNESS, track = PlanTrack.RUN, start = start, target = 5.0, routineKind = RoutineKind.FITNESS_WEEK)
        val logs = listOf(
            log(LogKind.WORKOUT, "Run, 1.2 km", LocalDate(2026, 10, 10), 1.2, "km"),
            log(LogKind.WORKOUT, "Run, 2.6 km", LocalDate(2026, 10, 24), 2.6, "km"),
            log(LogKind.WORKOUT, "Strength", LocalDate(2026, 10, 25), min = 45),
            log(LogKind.WORKOUT, "Run, 9 km", LocalDate(2026, 9, 20), 9.0, "km"),
        )
        val p = PlanProgress.of(g, spec, PlanInputs(logs), today)
        assertEquals(setOf("run-1", "run-2"), p.ticks.keys)
        assertEquals("Ticked from your run, Sat 10 Oct", p.ticks["run-1"]!!.text)
        assertEquals("1 of 5 steps", p.headline)
        assertEquals("2.6 km", p.stats.first().value)
        assertEquals("2", p.stats[1].value)
        assertEquals("Moves with your runs. Best so far 2.6 km.", p.source)
    }

    @Test
    fun anUntickStaysUntilANewRun() {
        val g = goal("run", "Run 2 km" to false)
        val spec = PlanSpec(goalId = "run", area = PlanArea.FITNESS, track = PlanTrack.RUN, start = start)
        val logs = listOf(log(LogKind.WORKOUT, "Run, 2.6 km", LocalDate(2026, 10, 24), 2.6, "km"))
        assertTrue(PlanProgress.of(g, spec, PlanInputs(logs), today, mapOf("run-0" to LocalDate(2026, 10, 26))).ticks.isEmpty())
        val later = logs + log(LogKind.WORKOUT, "Run, 2 km", LocalDate(2026, 10, 28), 2.0, "km")
        assertEquals(1, PlanProgress.of(g, spec, PlanInputs(later), today, mapOf("run-0" to LocalDate(2026, 10, 26))).ticks.size)
    }

    @Test
    fun healthRunsWithoutDistanceTickMinuteSteps() {
        val g = goal("run", "Run 20 min" to false, "Run the 5K (about 35 min)" to false)
        val spec = PlanSpec(goalId = "run", area = PlanArea.FITNESS, track = PlanTrack.RUN, start = start)
        val p = PlanProgress.of(g, spec, PlanInputs(listOf(log(LogKind.WORKOUT, "Running", LocalDate(2026, 10, 5), min = 24))), today)
        assertEquals(setOf("run-0"), p.ticks.keys)
        assertEquals("24 min", p.stats.first().value)
    }

    @Test
    fun moneyPutAsideCountsAndSpendingDoesNot() {
        val g = goal("jp", "Set a monthly transfer on payday" to true, "€500 put aside" to false, "€1,000 put aside" to false, "€2,000, all of it" to false)
        val spec = PlanSpec(goalId = "jp", area = PlanArea.MONEY, track = PlanTrack.SAVE, start = start, target = 2000.0, currency = "EUR", baseline = 200.0, routineId = "bill1")
        val logs = listOf(
            log(LogKind.EXPENSE, "Put aside for Japan", LocalDate(2026, 10, 3), amount = 100.0, category = PlanProgress.SAVINGS, ext = "jp", area = PlanArea.MONEY),
            log(LogKind.EXPENSE, "Put aside for Japan", LocalDate(2026, 10, 25), amount = 300.0, category = PlanProgress.SAVINGS, ext = Bills.paidMarker("bill1", LocalDate(2026, 10, 25)), area = PlanArea.MONEY),
            log(LogKind.EXPENSE, "Ramen", LocalDate(2026, 10, 4), amount = 12.0, category = "food", area = PlanArea.MONEY),
            log(LogKind.EXPENSE, "Put aside for a car", LocalDate(2026, 10, 5), amount = 900.0, category = PlanProgress.SAVINGS, ext = "car", area = PlanArea.MONEY),
        )
        val p = PlanProgress.of(g, spec, PlanInputs(logs), today)
        assertEquals(600.0, p.value)
        assertEquals("€600 of €2,000", p.headline)
        assertEquals(0.3f, p.fraction)
        assertEquals(setOf("jp-1"), p.ticks.keys)
        assertEquals("Reached €500, Sun 25 Oct", p.ticks["jp-1"]!!.text)
        assertEquals("€1,400", p.stats[1].value)
    }

    @Test
    fun studyMinutesOnTheSubject() {
        val g = goal("es", "Pick an app" to true, "Greetings" to false)
        val spec = PlanSpec(goalId = "es", area = PlanArea.STUDY, track = PlanTrack.STUDY, start = start, target = 1320.0, subject = "Spanish")
        val focus = listOf(StudyTime(LocalDate(2026, 10, 1), 30, "Spanish"), StudyTime(LocalDate(2026, 10, 2), 40, "Maths"), StudyTime(LocalDate(2026, 10, 28), 20, "spanish"))
        val p = PlanProgress.of(g, spec, PlanInputs(focus = focus), today)
        assertEquals(50.0, p.value)
        assertEquals("50 min", p.stats[0].value)
        assertEquals("22h", p.stats[1].value)
        assertEquals("20 min", p.stats[2].value)
        assertEquals(0.5f, p.fraction)
    }

    @Test
    fun weightFromHealthAgainstTheStart() {
        val g = goal("kg", "Pick one small swap" to true, "1 kg down" to false, "2 kg down" to false, "4 kg down, all of it" to false)
        val spec = PlanSpec(goalId = "kg", area = PlanArea.FITNESS, track = PlanTrack.WEIGHT, start = start, target = 4.0, baseline = 82.0)
        val weights = listOf(LocalDate(2026, 10, 5) to 81.6, LocalDate(2026, 10, 12) to 80.9, LocalDate(2026, 10, 26) to 80.5)
        val p = PlanProgress.of(g, spec, PlanInputs(weights = weights), today)
        assertEquals(setOf("kg-1"), p.ticks.keys)
        assertEquals("Ticked from Health, Mon 12 Oct", p.ticks["kg-1"]!!.text)
        assertEquals("1.5 of 4 kg down", p.headline)
        assertEquals(0.375f, p.fraction)
    }

    @Test
    fun applicationsAndInterviews() {
        val g = goal("job", "Update your CV" to true, "2 applications sent" to false, "First interview" to false)
        val spec = PlanSpec(goalId = "job", area = PlanArea.CAREER, track = PlanTrack.APPLICATIONS, start = start, target = 20.0)
        fun app(day: Int, stage: String) = log(LogKind.NOTE, "Role", LocalDate(2026, 10, day), category = "application", area = PlanArea.CAREER, notes = "stage: $stage")
        val logs = listOf(app(2, "APPLIED"), app(3, "SAVED"), app(6, "INTERVIEW"), log(LogKind.NOTE, "Interview", LocalDate(2026, 10, 20), category = "interview", area = PlanArea.CAREER))
        val p = PlanProgress.of(g, spec, PlanInputs(logs), today)
        assertEquals(2.0, p.value)
        assertEquals(setOf("job-1", "job-2"), p.ticks.keys)
        assertEquals("Ticked from your applications, Tue 6 Oct", p.ticks["job-1"]!!.text)
    }

    @Test
    fun daysCountFromTheRoutine() {
        val g = goal("med", "7 days" to false, "14 days" to false, "30 days, all of them" to false)
        val spec = PlanSpec(goalId = "med", area = PlanArea.MIND, track = PlanTrack.COUNT, start = start, target = 30.0, routineKind = RoutineKind.HABIT, routineId = "h1")
        val days = (1..9).map { LocalDate(2026, 10, it) }.toSet() + LocalDate(2026, 9, 1)
        val p = PlanProgress.of(g, spec, PlanInputs(habitDays = days), today)
        assertEquals(9.0, p.value)
        assertEquals("9 of 30", p.headline)
        assertEquals(setOf("med-0"), p.ticks.keys)
        assertEquals("Ticked from your routine, Wed 7 Oct", p.ticks["med-0"]!!.text)
        val manual = spec.copy(routineKind = null, routineId = null, count = 14)
        assertEquals(setOf("med-0", "med-1"), PlanProgress.of(g, manual, PlanInputs(), today).ticks.keys)
    }

    @Test
    fun recapSaysWhatItTook() {
        val g = goal("run", "Run the 5K" to true)
        val spec = PlanSpec(goalId = "run", area = PlanArea.FITNESS, track = PlanTrack.RUN, start = start, target = 5.0)
        val logs = (0 until 24).map { i -> log(LogKind.WORKOUT, "Run", LocalDate(2026, 10, 1).plus(kotlinx.datetime.DatePeriod(days = i * 2)), 2.55, "km") }
        val (head, line) = PlanProgress.recap(g, spec, PlanInputs(logs), LocalDate(2026, 12, 1))
        assertEquals("You ran a 5K.", head)
        assertEquals("9 weeks, 24 runs and 61 km along the way.", line)
        assertEquals("You saved €2,000.", PlanProgress.recap(goal("s"), PlanSpec(goalId = "s", area = PlanArea.MONEY, track = PlanTrack.SAVE, start = start, currency = "EUR", baseline = 2000.0), PlanInputs(), LocalDate(2027, 3, 1)).first)
        assertTrue(PlanTemplates.runName(5.0) == "5K")
    }
}
