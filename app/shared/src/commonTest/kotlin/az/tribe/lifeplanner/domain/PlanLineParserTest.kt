package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.PlanLineParser
import az.tribe.lifeplanner.domain.service.PlanTemplates
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanLineParserTest {

    private val today = LocalDate(2026, 9, 29)
    private fun parse(s: String, preset: PlanArea? = null) = PlanLineParser.parse(s, today, "EUR", preset)

    @Test
    fun runA5kByDecember() {
        val l = parse("Run a 5K by December")
        assertEquals("Run a 5K", l.title)
        assertEquals(LocalDate(2026, 12, 1), l.target)
        assertEquals(PlanTemplates.RUN, l.template)
        assertEquals(PlanArea.FITNESS, l.area)
        assertEquals(5.0, l.km)
    }

    @Test
    fun saveWithAmountSubjectAndMonthNextYear() {
        val l = parse("Save 2000 for Japan by March")
        assertEquals("Save €2,000 for Japan", l.title)
        assertEquals(LocalDate(2027, 3, 1), l.target)
        assertEquals(2000.0, l.amount)
        assertEquals("EUR", l.currency)
        assertEquals("Japan", l.subject)
        assertEquals(PlanArea.MONEY, l.area)
        assertEquals(PlanTemplates.SAVE, l.template)
    }

    @Test
    fun currencySymbolsAndThousands() {
        assertEquals(3000.0, parse("Build a $3,000 emergency fund").amount)
        assertEquals("USD", parse("Build a $3,000 emergency fund").currency)
        assertEquals(2000.0, parse("save 2k for a car").amount)
        assertEquals("a car", parse("save 2k for a car").subject)
        assertEquals("Japan", parse("Save 2000 for Japan by May").subject)
        assertEquals(PlanTemplates.DEBT, parse("Pay off €2,000 of debt").template)
    }

    @Test
    fun noDateMeansNull() {
        val l = parse("Learn Spanish basics")
        assertNull(l.target)
        assertEquals(PlanTemplates.LEARN, l.template)
        assertEquals("Spanish", l.subject)
        assertEquals(PlanArea.STUDY, l.area)
    }

    @Test
    fun datePhrases() {
        assertEquals(LocalDate(2026, 12, 29), parse("Learn Spanish in 3 months").target)
        assertEquals(LocalDate(2026, 11, 10), parse("Read more in 6 weeks").target)
        assertEquals(LocalDate(2026, 12, 31), parse("Read 12 books this year").target)
        assertEquals(LocalDate(2026, 12, 31), parse("Finish my thesis by the end of the year").target)
        assertEquals(LocalDate(2027, 3, 15), parse("Pass the driving test by 15 March").target)
        assertEquals(LocalDate(2027, 3, 15), parse("Pass the driving test by March 15th").target)
        assertEquals(LocalDate(2027, 6, 1), parse("Lose 4 kg by summer").target)
        assertEquals(LocalDate(2026, 12, 25), parse("Save 300 for gifts before christmas").target)
        assertEquals(LocalDate(2027, 1, 1), parse("Visit Japan in 2027").target)
        // This month has started, so "by September" means next year's.
        assertEquals(LocalDate(2027, 9, 1), parse("Run a 10K by September").target)
    }

    @Test
    fun theDatePhraseLeavesTheTitle() {
        assertEquals("Learn Spanish", parse("Learn Spanish in 3 months").title)
        assertEquals("Read 12 books", parse("Read 12 books this year").title)
        assertEquals("Lose 4 kg", parse("I want to lose 4 kg by summer").title)
    }

    @Test
    fun templatesForEachArea() {
        assertEquals(PlanTemplates.WEIGHT, parse("Lose 4 kg").template)
        assertEquals(4.0, parse("Lose 4 kg").kg)
        assertEquals(PlanTemplates.REPS, parse("10 push-ups in a row").template)
        assertEquals("push-ups", parse("10 push-ups in a row").subject)
        assertEquals(PlanTemplates.EXAM, parse("Pass my IELTS").template)
        assertEquals("IELTS", parse("Pass my IELTS").subject)
        assertEquals(PlanTemplates.JOB, parse("Find a new job").template)
        assertEquals(PlanTemplates.PROMOTION, parse("Get promoted").template)
        assertEquals(PlanTemplates.BOOKS, parse("Read 12 books").template)
        assertEquals(12, parse("Read 12 books").count)
        assertEquals(PlanTemplates.COOK, parse("Cook 10 new recipes").template)
        assertEquals(PlanTemplates.COOK, parse("Learn to cook 5 dinners").template)
        assertEquals(PlanTemplates.TRIP, parse("A weekend in Rome").template)
        assertEquals("Rome", parse("A weekend in Rome").subject)
        assertEquals(PlanTemplates.SLEEP, parse("Sleep 8 hours a night").template)
        assertEquals(21.1, parse("Run a half marathon").km)
        assertEquals("Run a half marathon", parse("Run a half marathon").title)
    }

    @Test
    fun daysInARowKeepTheHabit() {
        val l = parse("Meditate for 30 days")
        assertEquals(PlanTemplates.DAYS, l.template)
        assertEquals(30, l.days)
        assertEquals("Meditate", l.subject)
        assertEquals(PlanArea.MIND, l.area)
        assertEquals("Eat vegetables", parse("Eat vegetables every day for 30 days").subject)
        assertEquals(PlanArea.MEALS, parse("Eat vegetables every day for 30 days").area)
    }

    @Test
    fun presetAreaWinsUnlessTheTemplateIsClear() {
        assertEquals(PlanArea.CAREER, parse("Learn SQL", PlanArea.CAREER).area)
        assertEquals(PlanArea.HABITS, parse("No sugar for 30 days", PlanArea.HABITS).area)
        assertEquals(PlanArea.FITNESS, parse("Run a 5K", PlanArea.STUDY).area)
        assertEquals(PlanArea.TRAVEL, parse("Build a treehouse", PlanArea.TRAVEL).area)
    }

    @Test
    fun nothingToMatchStillReadsAnArea() {
        val l = parse("Build a garden shed")
        assertNull(l.template)
        assertEquals(PlanArea.HABITS, l.area)
        assertEquals("Build a garden shed", l.title)
        assertEquals(PlanArea.CAREER, parse("Start a side business").area)
    }

    @Test
    fun planOrLog() {
        assertTrue(PlanLineParser.looksLikePlan("run a 5k by december", today))
        assertTrue(PlanLineParser.looksLikePlan("run a 5k", today))
        assertTrue(PlanLineParser.looksLikePlan("save 2000 for japan by march", today))
        assertTrue(PlanLineParser.looksLikePlan("learn spanish", today))
        assertTrue(PlanLineParser.looksLikePlan("meditate for 30 days", today))
        assertFalse(PlanLineParser.looksLikePlan("ran 3k this morning", today))
        assertFalse(PlanLineParser.looksLikePlan("put aside 100 for japan", today))
        assertFalse(PlanLineParser.looksLikePlan("coffee 4.50", today))
        assertFalse(PlanLineParser.looksLikePlan("learn spanish 20 min", today))
        assertFalse(PlanLineParser.looksLikePlan("read 20 pages", today))
    }

    @Test
    fun putAsideIsSavingNotSpending() {
        val p = PlanLineParser.putAside("put aside 100 for Japan", "EUR")!!
        assertEquals(100.0, p.amount)
        assertEquals("Japan", p.subject)
        assertFalse(p.paidOff)
        assertTrue(PlanLineParser.putAside("paid off £200", "EUR")!!.paidOff)
        assertEquals("GBP", PlanLineParser.putAside("paid off £200", "EUR")!!.currency)
        assertNull(PlanLineParser.putAside("coffee 4.50", "EUR"))
    }
}
