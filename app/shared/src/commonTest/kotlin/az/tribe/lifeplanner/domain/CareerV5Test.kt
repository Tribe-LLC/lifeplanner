package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.CareerKind
import az.tribe.lifeplanner.domain.service.CareerPlanner
import az.tribe.lifeplanner.domain.service.Stage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Career v5: the search funnel, Friday wins, catch-up history and reading a shared job. */
class CareerV5Test {

    /** A Monday. */
    private val today = LocalDate(2026, 9, 28)

    private var n = 0
    private fun app(stage: Stage, applied: String? = null, replied: String? = null, closed: String? = null, extra: Map<String, String> = emptyMap()): LifeLog {
        var notes = CareerPlanner.withField(null, "stage", stage.name)
        notes = CareerPlanner.withField(notes, "applied", applied)
        notes = CareerPlanner.withField(notes, "replied", replied)
        notes = CareerPlanner.withField(notes, "closed", closed)
        extra.forEach { (k, v) -> notes = CareerPlanner.withField(notes, k, v) }
        return LifeLog(
            id = "a${n++}", area = PlanArea.CAREER, kind = LogKind.NOTE, title = "Android engineer", category = CareerKind.APPLICATION.key,
            occurredAt = LocalDateTime(today, LocalTime(0, 0)), notes = notes,
        )
    }

    private fun interview(forApp: LifeLog) = LifeLog(
        id = "i${n++}", area = PlanArea.CAREER, kind = LogKind.NOTE, title = "Interview", category = CareerKind.INTERVIEW.key,
        occurredAt = LocalDateTime(today, LocalTime(10, 0)), externalId = forApp.id,
    )

    @Test
    fun funnelCountsWhatEachApplicationReached() {
        val withInterviewRow = app(Stage.CLOSED, applied = "2026-09-01", closed = "Not selected")
        val rows = listOf(
            app(Stage.SAVED),
            app(Stage.CLOSED, closed = "I withdrew"), // closed before applying: not sent
            app(Stage.APPLIED, applied = "2026-09-20"),
            app(Stage.CLOSED, applied = "2026-09-02", closed = "No reply"),
            app(Stage.CLOSED, applied = "2026-09-03", closed = "No reply"),
            app(Stage.CLOSED, applied = "2026-09-04", replied = "2026-09-10", closed = "Not selected"),
            withInterviewRow, interview(withInterviewRow),
            app(Stage.INTERVIEW, applied = "2026-09-05", replied = "2026-09-07"),
            app(Stage.OFFER, applied = "2026-09-06", replied = "2026-09-16", extra = mapOf("interviewed" to "yes")),
        )
        val f = CareerPlanner.funnel(rows)
        assertEquals(7, f.applied)
        assertEquals(4, f.replied)
        assertEquals(3, f.interviews)
        assertEquals(1, f.offers)
        assertEquals(57, f.replyPercent)
        // Reply gaps 6, 2 and 10 days: half came within 6.
        assertEquals(6, f.medianReplyDays)
        assertEquals("No reply", f.topCloseReason)
        assertEquals("7 applied, 4 replied (57%), 3 interviews, 1 offer", CareerPlanner.funnelLine(f))
        assertEquals(
            "My job search so far: 7 applied, 4 replied (57%), 3 interviews, 1 offer.\n" +
                "Half of first replies came within 6 days.\nMost common reason for closing: no reply.",
            CareerPlanner.funnelText(f),
        )
    }

    @Test
    fun emptyFunnelSaysNothingItDoesNotKnow() {
        val f = CareerPlanner.funnel(listOf(app(Stage.SAVED)))
        assertEquals(0, f.applied)
        assertEquals(0, f.replyPercent)
        assertNull(f.medianReplyDays)
        assertNull(f.topCloseReason)
        assertEquals("My job search so far: 0 applied, 0 replied (0%), 0 interviews, 0 offers.", CareerPlanner.funnelText(f))
    }

    @Test
    fun acceptingAnOfferCountsAsOfferAndReply() {
        val f = CareerPlanner.funnel(listOf(app(Stage.CLOSED, applied = "2026-09-01", closed = CareerPlanner.ACCEPTED)))
        assertEquals(1, f.replied)
        assertEquals(1, f.offers)
    }

    @Test
    fun fridayWinsShowFromThreeUntilAnsweredThatWeek() {
        val friday = LocalDate(2026, 10, 2)
        assertEquals(today, CareerPlanner.weekStart(friday))
        assertEquals(today, CareerPlanner.weekStart(today))
        assertEquals(today, CareerPlanner.weekStart(LocalDate(2026, 10, 4)))
        assertFalse(CareerPlanner.showFridayWins(LocalDateTime(friday, LocalTime(14, 59)), null))
        assertTrue(CareerPlanner.showFridayWins(LocalDateTime(friday, LocalTime(15, 0)), null))
        assertTrue(CareerPlanner.showFridayWins(LocalDateTime(friday, LocalTime(21, 0)), LocalDate(2026, 9, 21)))
        assertFalse(CareerPlanner.showFridayWins(LocalDateTime(friday, LocalTime(16, 0)), today))
        assertFalse(CareerPlanner.showFridayWins(LocalDateTime(LocalDate(2026, 10, 3), LocalTime(16, 0)), null))
    }

    @Test
    fun talksAreThePersonsOwnNewestFirst() {
        val aysel = LifeLog("p1", PlanArea.CAREER, LogKind.NOTE, title = "Aysel", category = CareerKind.CONTACT.key, occurredAt = LocalDateTime(today, LocalTime(0, 0)))
        fun talk(id: String, who: String, day: Int, note: String?) = LifeLog(
            id, PlanArea.CAREER, LogKind.NOTE, title = "Talked", category = CareerKind.TALK.key, externalId = who, notes = note,
            occurredAt = LocalDateTime(LocalDate(2026, 9, day), LocalTime(12, 0)),
        )
        val rows = listOf(talk("t1", "p1", 1, null), talk("t2", "p2", 5, "x"), talk("t3", "p1", 20, "Intro to her team lead"))
        assertEquals(listOf("t3", "t1"), CareerPlanner.talks(aysel, rows).map { it.id })
        // A talk is not a contact, so it never shows as a person or a next action.
        assertTrue(CareerPlanner.actions(rows, today).isEmpty())
    }

    @Test
    fun findsTheLinkInSharedText() {
        assertEquals("https://revolut.com/careers/1234", CareerPlanner.firstUrl("Senior Android Engineer at Revolut. https://revolut.com/careers/1234."))
        assertEquals("https://www.linkedin.com/jobs/view/4012", CareerPlanner.firstUrl("See (https://www.linkedin.com/jobs/view/4012)"))
        assertNull(CareerPlanner.firstUrl("No link here"))
    }

    @Test
    fun parsesTheCoachsAnswerAndKeepsTheSharedLink() {
        val shared = "Senior Android Engineer at Revolut, London. Apply by 3 October. https://revolut.com/careers/1234"
        val raw = """{"role":"Senior Android Engineer","company":"Revolut","link":"https://example.com/other","location":"London","closing_date":"2026-10-03"}"""
        val d = CareerPlanner.parseJob(raw, shared, today)
        assertEquals("Senior Android Engineer", d.role)
        assertEquals("Revolut", d.company)
        assertEquals("https://revolut.com/careers/1234", d.link)
        assertEquals("London", d.location)
        assertEquals(LocalDate(2026, 10, 3), d.closes)
    }

    @Test
    fun dropsEmptyUnknownAndPastAnswers() {
        val raw = """{"role":"","company":"unknown","link":"","location":"null","closing_date":"2026-09-01"}"""
        val d = CareerPlanner.parseJob(raw, "some text", today)
        assertNull(d.role)
        assertNull(d.company)
        assertNull(d.link)
        assertNull(d.location)
        assertNull(d.closes)
        assertNull(CareerPlanner.parseJob("not json", "https://jobs.example.com/1", today).role)
        assertEquals("https://jobs.example.com/1", CareerPlanner.parseJob("not json", "https://jobs.example.com/1", today).link)
    }
}
