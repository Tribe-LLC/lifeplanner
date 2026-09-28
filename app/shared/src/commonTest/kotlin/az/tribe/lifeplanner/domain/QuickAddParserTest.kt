package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.QuickAddParser
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuickAddParserTest {

    private val now = LocalDateTime(2026, 9, 28, 13, 10)
    private fun parse(s: String) = QuickAddParser.parse(s, now, "EUR")

    @Test
    fun coffeeWithAmountIsAMealAndASpend() {
        val r = parse("coffee 4.50").entries
        assertEquals(setOf(PlanArea.MEALS, PlanArea.MONEY), r.map { it.area }.toSet())
        val spend = r.first { it.area == PlanArea.MONEY }
        assertEquals(4.5, spend.amount)
        assertEquals("EUR", spend.currency)
        assertEquals("food", spend.category)
        assertEquals("Coffee", spend.title)
    }

    @Test
    fun lunchRamenGoesToMealsAndMoney() {
        val r = parse("lunch ramen 12.50").entries
        assertEquals("Ramen, lunch", r.first { it.area == PlanArea.MEALS }.title)
        assertEquals(12.5, r.first { it.area == PlanArea.MONEY }.amount)
    }

    @Test
    fun runWithDistanceAndTime() {
        val e = parse("ran 5k in 28 min").entries.single()
        assertEquals(PlanArea.FITNESS, e.area)
        assertEquals(LogKind.WORKOUT, e.kind)
        assertEquals("Run, 5 km", e.title)
        assertEquals(5.0, e.quantity)
        assertEquals(28, e.durationMin)
    }

    @Test
    fun sleepBadlyAndSleepHours() {
        val bad = parse("slept badly").entries.single()
        assertEquals(PlanArea.MIND, bad.area)
        assertEquals("badly", bad.notes)
        val h = parse("slept 5h 40m").entries.single()
        assertEquals("Slept 5h 40m", h.title)
    }

    @Test
    fun flightWithEuroSymbolIsTravelSpend() {
        val e = parse("flight to Tokyo 10 Oct €920").entries.single { it.area == PlanArea.MONEY }
        assertEquals(920.0, e.amount)
        assertEquals("travel", e.category)
    }

    @Test
    fun studyMinutes() {
        val e = parse("studied 25 min").entries.single()
        assertEquals(PlanArea.STUDY, e.area)
        assertEquals(25, e.durationMin)
    }

    @Test
    fun everyDayMakesARoutine() {
        val e = parse("drink water every day").entries.single()
        assertTrue(e.isRoutine)
        assertEquals("Drink water", e.title)
    }

    @Test
    fun glassesAreNotMoney() {
        val r = parse("3 glasses of water").entries
        assertEquals(1, r.size)
        assertEquals(PlanArea.HABITS, r.single().area)
        assertEquals(3.0, r.single().quantity)
    }

    @Test
    fun wholeNumberNextToSpendWordIsMoney() {
        val e = parse("taxi 15").entries.single()
        assertEquals(15.0, e.amount)
        assertEquals("transport", e.category)
    }

    @Test
    fun salaryIsIncome() {
        assertEquals(LogKind.INCOME, parse("salary 2000 manat").entries.single().kind)
        assertEquals("AZN", parse("salary 2000 manat").entries.single().currency)
    }

    @Test
    fun yesterdayAndAtTime() {
        val r = parse("dinner pizza 18 yesterday at 8pm")
        assertEquals(LocalDate(2026, 9, 27), r.occurredAt.date)
        assertEquals(20, r.occurredAt.hour)
    }

    @Test
    fun unknownTextParsesToNothing() {
        assertTrue(parse("call mum about the weekend").entries.isEmpty())
        assertNull(parse("").entries.firstOrNull())
    }
}
