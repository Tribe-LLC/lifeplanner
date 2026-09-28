package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.CareerKind
import az.tribe.lifeplanner.domain.service.CareerPlanner
import az.tribe.lifeplanner.domain.service.MindCheckIns
import az.tribe.lifeplanner.domain.service.MindInsights
import az.tribe.lifeplanner.domain.service.Stage
import az.tribe.lifeplanner.domain.service.StudyTime
import kotlinx.datetime.DatePeriod
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

class MindCheckInsTest {
    private fun mood(title: String, q: Double? = null, cat: String? = null, notes: String? = null) = LifeLog(
        "m", PlanArea.MIND, LogKind.MOOD, title = title, quantity = q, category = cat, notes = notes,
        occurredAt = LocalDateTime(2026, 9, 28, 9, 0),
    )

    @Test
    fun levelComesFromTheNumberOrTheWords() {
        assertEquals(4, MindCheckIns.score(mood("Good", 4.0)))
        assertEquals(2, MindCheckIns.score(mood("feeling stressed")))
        assertEquals(3, MindCheckIns.score(mood("tired today")))
        assertEquals(5, MindCheckIns.score(mood("feeling great")))
        assertNull(MindCheckIns.score(mood("hmm")))
    }

    @Test
    fun notesKeepFeelingsAndTheNoteApart() {
        val notes = MindCheckIns.encode(listOf("Calm", "Tired"), "Long day but okay")
        val l = mood("Okay", 3.0, "Work, Sleep", notes)
        assertEquals(listOf("Calm", "Tired"), MindCheckIns.feelings(l))
        assertEquals("Long day but okay", MindCheckIns.note(l))
        assertEquals(listOf("Work", "Sleep"), MindCheckIns.tags(l))
        assertNull(MindCheckIns.encode(emptyList(), " "))
    }

    @Test
    fun valenceSpansMinusOneToOne() {
        assertEquals(-1.0, MindCheckIns.valence(1))
        assertEquals(0.0, MindCheckIns.valence(3))
        assertEquals(1.0, MindCheckIns.valence(5))
    }
}

class MindInsightsTest {
    private val start = LocalDate(2026, 9, 1)
    private fun day(i: Int) = start.plus(DatePeriod(days = i))

    @Test
    fun liftsNeedEnoughDaysOnBothSides() {
        // Ten days: walked on five of them, and those days were better.
        val mood = (0 until 10).associate { day(it) to if (it % 2 == 0) 4.5 else 3.0 }
        val walked = (0 until 10 step 2).map { day(it) }.toSet()
        val lifts = MindInsights.lifts(mood, mapOf("Days you walk" to walked, "Rare" to setOf(day(0))))
        assertEquals(1, lifts.size)
        assertEquals("Days you walk", lifts[0].what)
        assertEquals(1.5, lifts[0].delta, 0.001)
        // Too few check-in days: nothing is claimed.
        assertTrue(MindInsights.lifts(mood.entries.take(5).associate { it.key to it.value }, mapOf("Days you walk" to walked)).isEmpty())
    }

    @Test
    fun smallDifferencesAreNotShown() {
        val mood = (0 until 10).associate { day(it) to if (it % 2 == 0) 3.1 else 3.0 }
        assertTrue(MindInsights.lifts(mood, mapOf("x" to (0 until 10 step 2).map { day(it) }.toSet())).isEmpty())
    }

    @Test
    fun deltaUsesARealMinusSign() {
        assertEquals("+0.8", MindInsights.formatDelta(0.84))
        assertEquals("−0.5", MindInsights.formatDelta(-0.5))
    }

    @Test
    fun threeLowCheckInsInARow() {
        assertTrue(MindInsights.lowRun(listOf(4, 2, 1, 2)))
        assertFalse(MindInsights.lowRun(listOf(2, 2, 4)))
        assertFalse(MindInsights.lowRun(listOf(1, 1)))
    }

    @Test
    fun promptsMoveOnAndWrap() {
        val a = MindInsights.prompt(start, 0)
        assertEquals(MindInsights.prompt(start, 1), MindInsights.prompt(day(1), 0))
        assertEquals(a, MindInsights.prompt(start, MindInsights.PROMPTS.size))
    }

    @Test
    fun dailyMoodMixesCheckInsAndJournal() {
        val l = LifeLog("m", PlanArea.MIND, LogKind.MOOD, title = "Good", quantity = 4.0, occurredAt = LocalDateTime(day(0), LocalTime(9, 0)))
        val daily = MindInsights.dailyMood(listOf(l), listOf(day(0) to 2, day(1) to 5))
        assertEquals(3.0, daily[day(0)])
        assertEquals(5.0, daily[day(1)])
    }
}

class CareerPlannerTest {
    private val today = LocalDate(2026, 9, 28)
    private fun at(d: LocalDate, h: Int = 0) = LocalDateTime(d, LocalTime(h, 0))

    private fun app(id: String, stage: Stage, next: LocalDate, company: String = "Wolt", applied: LocalDate? = null): LifeLog {
        var notes = CareerPlanner.withField(null, "company", company)
        notes = CareerPlanner.withField(notes, "stage", stage.name)
        notes = CareerPlanner.withField(notes, "applied", applied?.toString())
        return LifeLog(id, PlanArea.CAREER, LogKind.NOTE, LogStatus.PLANNED, "Android engineer", category = CareerKind.APPLICATION.key, occurredAt = at(next), notes = notes)
    }

    @Test
    fun codecReplacesAndRemovesFields() {
        var n = CareerPlanner.withField(null, "company", "Wolt")
        n = CareerPlanner.withField(n, "stage", "APPLIED")
        n = CareerPlanner.withField(n, "company", "Bolt")
        val l = app("a", Stage.SAVED, today).copy(notes = n)
        assertEquals("Bolt", CareerPlanner.company(l))
        assertEquals(Stage.APPLIED, CareerPlanner.stage(l))
        assertNull(CareerPlanner.field(l.copy(notes = CareerPlanner.withField(n, "company", null)), "company"))
    }

    @Test
    fun everyOpenApplicationHasANextStep() {
        val rows = listOf(
            app("saved", Stage.SAVED, today, "Revolut"),
            app("applied", Stage.APPLIED, today.minus(DatePeriod(days = 1)), "Bolt", today.minus(DatePeriod(days = 8))),
            app("later", Stage.APPLIED, today.plus(DatePeriod(days = 5)), "N26"),
            app("closed", Stage.CLOSED, today, "Spotify"),
            app("iv", Stage.INTERVIEW, today.plus(DatePeriod(days = 3)), "Wolt"),
            LifeLog("i1", PlanArea.CAREER, LogKind.NOTE, LogStatus.PLANNED, "Interview: Wolt", category = CareerKind.INTERVIEW.key, occurredAt = at(today.plus(DatePeriod(days = 2)), 14), externalId = "iv"),
            LifeLog("p", PlanArea.CAREER, LogKind.NOTE, LogStatus.PLANNED, "Aysel", category = CareerKind.CONTACT.key, occurredAt = at(today), quantity = 60.0),
        )
        val due = CareerPlanner.actions(rows, today, horizonDays = 0).filter { it.due <= today }.map { it.title }
        assertEquals(listOf("Follow up: Bolt", "Apply: Revolut", "Catch up with Aysel"), due)
        val week = CareerPlanner.actions(rows, today).map { it.title }
        // The interview replaces "update" while it is still ahead, and closed ones never show.
        assertTrue("Interview: Wolt" in week)
        assertFalse(week.any { "Update: Wolt" == it })
        assertFalse(week.any { "Spotify" in it })
        assertEquals("Applied 8 days ago, no reply yet", CareerPlanner.actions(rows, today).first { it.log.id == "applied" }.meta)
    }

    @Test
    fun reviewTextGroupsWinsByMonth() {
        fun win(d: LocalDate, what: String, impact: String?) =
            LifeLog(what, PlanArea.CAREER, LogKind.NOTE, LogStatus.DONE, what, category = CareerKind.WIN.key, occurredAt = at(d, 12), notes = impact)
        val wins = listOf(
            win(LocalDate(2026, 9, 10), "Cut start time to 900ms", "Old phones stopped dropping off"),
            win(LocalDate(2026, 7, 2), "Shipped offline orders", null),
            win(LocalDate(2026, 6, 20), "Last quarter", null),
        )
        val inQuarter = CareerPlanner.winsInQuarter(wins, today)
        assertEquals(2, inQuarter.size)
        assertEquals(
            "Wins, July to September 2026\n\nSeptember\n- Cut start time to 900ms. Old phones stopped dropping off\n\nJuly\n- Shipped offline orders",
            CareerPlanner.reviewText(inQuarter, today),
        )
    }

    @Test
    fun practiceComesFromStudyWithTheSameName() {
        val times = listOf(
            StudyTime(today, 45, "System design"),
            StudyTime(today.minus(DatePeriod(days = 3)), 30, "system design "),
            StudyTime(today.minus(DatePeriod(days = 40)), 60, "System design"),
            StudyTime(today, 20, "Biology"),
        )
        assertEquals(75, CareerPlanner.practiceMinutes("System Design", times, today))
    }
}
