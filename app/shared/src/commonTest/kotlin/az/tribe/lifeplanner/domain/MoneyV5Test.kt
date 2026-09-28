package az.tribe.lifeplanner.domain

import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.money.FxRates
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.BillRepeat
import az.tribe.lifeplanner.domain.service.BillRule
import az.tribe.lifeplanner.domain.service.Bills
import az.tribe.lifeplanner.domain.service.Fx
import az.tribe.lifeplanner.domain.service.FxTable
import az.tribe.lifeplanner.domain.service.MoneySummary
import az.tribe.lifeplanner.domain.service.QuickAddParser
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyV5Test {

    private val today = LocalDate(2026, 9, 28) // a Monday
    private val fx = FxTable("EUR", mapOf("USD" to 1.1, "JPY" to 160.0, "AZN" to 2.0))

    private fun spend(amount: Double, currency: String?, date: LocalDate = today, category: String = "food", status: LogStatus = LogStatus.DONE, notes: String? = null) =
        LifeLog(
            id = "$amount$currency$date$status", area = PlanArea.MONEY, kind = LogKind.EXPENSE, status = status, title = "x",
            amount = amount, currency = currency, category = category, occurredAt = LocalDateTime(date, LocalTime(12, 0)), notes = notes,
        )

    private fun budget(amount: Double, period: BudgetPeriod = BudgetPeriod.WEEK, category: String? = null) =
        Budget(id = "b", area = PlanArea.MONEY, metric = Budget.METRIC_SPEND, category = category, amount = amount, currency = "EUR", period = period)

    // ── Conversion ──

    @Test
    fun convertsThroughTheBaseAndKeepsSameCurrencyWithoutARate() {
        assertEquals(10.0, fx.convert(1600.0, "JPY", "EUR"))
        assertEquals(20.0, fx.convert(1600.0, "JPY", "AZN")!!, 1e-9)
        assertEquals(5.0, Fx.convert(null, 5.0, "GBP", "GBP"))
        assertNull(Fx.convert(null, 5.0, "GBP", "EUR"))
        assertNull(fx.convert(5.0, "THB", "EUR"))
    }

    @Test
    fun totalsNeverDropWhatTheyCannotConvert() {
        val t = Fx.total(listOf(spend(10.0, "EUR"), spend(1600.0, "JPY"), spend(100.0, "THB"), spend(3.0, null)), "EUR", fx)
        assertEquals(23.0, t.amount, 1e-9)
        assertEquals(mapOf("THB" to 100.0), t.unconverted)
        assertEquals("+ ฿100 not converted yet", Fx.unconvertedLine(t))
    }

    // ── The currency bug: foreign spends used to vanish from the budget ──

    @Test
    fun budgetCountsForeignSpendsConvertedAndShowsTheRestApart() {
        val logs = listOf(spend(20.0, "EUR"), spend(1600.0, "JPY"), spend(50.0, "THB"))
        val withRates = MoneySummary.status(budget(100.0), logs, today, fx)
        assertEquals(30.0, withRates.spent, 1e-9)
        assertEquals(mapOf("THB" to 50.0), withRates.unconverted)
        val offline = MoneySummary.status(budget(100.0), logs, today, null)
        assertEquals(20.0, offline.spent, 1e-9)
        assertEquals(setOf("JPY", "THB"), offline.unconverted.keys)
    }

    @Test
    fun byCategoryAddsUpInOneCurrency() {
        val r = MoneySummary.range(BudgetPeriod.WEEK, today)
        val cats = MoneySummary.byCategory(listOf(spend(10.0, "EUR"), spend(1600.0, "JPY"), spend(2.0, "AZN", category = "transport")), r, "EUR", fx)
        assertEquals(listOf("food" to 20.0, "transport" to 1.0), cats)
    }

    @Test
    fun oldCategoryBudgetsStillCountOnlyTheirCategory() {
        val s = MoneySummary.status(budget(50.0, category = "food"), listOf(spend(10.0, "EUR"), spend(30.0, "EUR", category = "fun")), today, fx)
        assertEquals(10.0, s.spent)
    }

    // ── Bills ──

    private fun bill(amount: Double, due: LocalDate, rule: BillRule) =
        spend(amount, "EUR", due, category = "bills", status = LogStatus.PLANNED, notes = rule.encode())

    @Test
    fun plannedBillsAreNotSpendsButCountAsDue() {
        val rent = bill(600.0, LocalDate(2026, 10, 1), BillRule(BillRepeat.MONTHLY, day = 1))
        val phone = bill(40.0, LocalDate(2026, 9, 30), BillRule(BillRepeat.MONTHLY, day = 30))
        val logs = listOf(spend(100.0, "EUR", LocalDate(2026, 9, 10)), rent, phone)
        assertTrue(Bills.isBill(rent))
        assertTrue(!MoneySummary.isSpend(rent))
        val s = MoneySummary.status(budget(1500.0, BudgetPeriod.MONTH), logs, today, fx, bills = listOf(rent, phone))
        assertEquals(100.0, s.spent)
        assertEquals(40.0, s.billsDue) // rent falls in October
        assertEquals(1360.0, s.leftAfterBills)
        assertEquals(3, s.daysLeft)
        assertEquals("€453 a day for the rest of the month", MoneySummary.perDayLine(s))
    }

    @Test
    fun dailyAllowanceIsNullOnceNothingIsLeft() {
        val s = MoneySummary.status(budget(50.0), listOf(spend(60.0, "EUR")), today, fx)
        assertNull(s.perDay)
        assertNull(MoneySummary.perDayLine(s))
        val small = MoneySummary.status(budget(100.0), listOf(spend(3.0, "EUR")), today, fx)
        assertEquals("€13.80 a day for the rest of the week", MoneySummary.perDayLine(small))
    }

    @Test
    fun monthlyBillsKeepTheirDayThroughShortMonths() {
        val rule = BillRule(BillRepeat.MONTHLY, day = 31)
        assertEquals(LocalDate(2027, 2, 28), Bills.next(rule, LocalDate(2027, 1, 31)))
        assertEquals(LocalDate(2027, 3, 31), Bills.next(rule, LocalDate(2027, 2, 28)))
        assertEquals(LocalDate(2026, 10, 5), Bills.next(BillRule(BillRepeat.WEEKLY), today))
        assertEquals(LocalDate(2027, 9, 28), Bills.next(BillRule.startingOn(BillRepeat.YEARLY, today), today))
    }

    @Test
    fun firstDueIsTheNextDateOnOrAfterToday() {
        assertEquals(LocalDate(2026, 10, 1), Bills.firstDue(BillRepeat.MONTHLY, today, day = 1))
        assertEquals(today, Bills.firstDue(BillRepeat.MONTHLY, today, day = 28))
        assertEquals(LocalDate(2026, 9, 30), Bills.firstDue(BillRepeat.MONTHLY, today, day = 31))
        assertEquals(LocalDate(2026, 10, 2), Bills.firstDue(BillRepeat.WEEKLY, today, weekday = DayOfWeek.FRIDAY))
    }

    @Test
    fun weeklyBillsCountEveryTimeTheyFallDue() {
        val gym = bill(10.0, LocalDate(2026, 9, 29), BillRule(BillRepeat.WEEKLY))
        assertEquals(2, Bills.occurrences(gym, LocalDate(2026, 10, 10)).size)
        assertEquals(20.0, Bills.dueThrough(listOf(gym), LocalDate(2026, 10, 10), "EUR", fx).amount)
    }

    @Test
    fun paidMarkerRoundTrips() {
        val m = Bills.paidMarker("abc-1", LocalDate(2026, 10, 1))
        val s = spend(600.0, "EUR").copy(externalId = m)
        assertEquals("abc-1" to LocalDate(2026, 10, 1), Bills.paidFrom(s))
        assertNull(Bills.paidFrom(spend(1.0, "EUR").copy(externalId = "group-1")))
        assertEquals(BillRule(BillRepeat.YEARLY, 3, 10), BillRule.decode(BillRule(BillRepeat.YEARLY, 3, 10).encode()))
        assertNull(BillRule.decode("Lighter today"))
    }

    @Test
    fun labelsReadPlainly() {
        assertEquals("Due tomorrow", Bills.dueLabel(LocalDate(2026, 9, 29), today))
        assertEquals("Due today", Bills.dueLabel(today, today))
        assertEquals("Was due 25 September", Bills.dueLabel(LocalDate(2026, 9, 25), today))
        assertEquals("Every month on the 1st", Bills.everyLabel(BillRule(BillRepeat.MONTHLY, day = 1), LocalDate(2026, 10, 1)))
        assertEquals("Every month on the 22nd", Bills.everyLabel(BillRule(BillRepeat.MONTHLY, day = 22), LocalDate(2026, 10, 22)))
        assertEquals("11th", Bills.ordinal(11))
        assertEquals("Every week on Friday", Bills.everyLabel(BillRule(BillRepeat.WEEKLY), LocalDate(2026, 10, 2)))
    }

    @Test
    fun todayShowsBillsTheDayBeforeAndOnTheDay() {
        val rent = bill(600.0, LocalDate(2026, 9, 29), BillRule(BillRepeat.MONTHLY, day = 29)).copy(id = "rent", title = "Rent")
        val later = bill(12.0, LocalDate(2026, 10, 3), BillRule(BillRepeat.MONTHLY, day = 3)).copy(id = "later")
        val paid = spend(40.0, "EUR").copy(id = "paid", title = "Phone", category = "bills", externalId = Bills.paidMarker("phone", today))
        val items = az.tribe.lifeplanner.ui.v4.today.TodayMoney.billItems(listOf(rent, later), listOf(paid), today)
        assertEquals(listOf("Rent €600", "Phone €40"), items.map { it.title })
        assertEquals("Due tomorrow", items[0].meta)
        assertEquals(listOf(false, true), items.map { it.done })
        assertEquals(listOf("rent", "paid"), items.map { it.refId })
    }

    // ── Quick add ──

    private val now = LocalDateTime(today, LocalTime(13, 10))

    @Test
    fun quickAddUnderstandsBills() {
        val netflix = QuickAddParser.parse("netflix 12 monthly", now, "EUR").entries.single()
        assertEquals("Netflix", netflix.title)
        assertEquals(12.0, netflix.amount)
        assertEquals(BillRepeat.MONTHLY, netflix.bill?.repeat)
        assertEquals(28, netflix.bill?.day)
        assertEquals(today, netflix.firstDue)
        assertEquals("bills", netflix.category)

        val rent = QuickAddParser.parse("rent 600 every month on the 1st", now, "EUR").entries.single()
        assertEquals("Rent", rent.title)
        assertEquals(600.0, rent.amount)
        assertEquals(1, rent.bill?.day)
        assertEquals(LocalDate(2026, 10, 1), rent.firstDue)

        val gym = QuickAddParser.parse("gym €30 every week on friday", now, "EUR").entries.single()
        assertEquals("Gym", gym.title)
        assertEquals(BillRepeat.WEEKLY, gym.bill?.repeat)
        assertEquals(LocalDate(2026, 10, 2), gym.firstDue)

        val yearly = QuickAddParser.parse("insurance 240 yearly", now, "EUR").entries.single()
        assertEquals(BillRepeat.YEARLY, yearly.bill?.repeat)
    }

    @Test
    fun quickAddLeavesNonBillsAlone() {
        assertNull(QuickAddParser.parse("coffee 4.50", now, "EUR").entries.single { it.area == PlanArea.MONEY }.bill)
        assertTrue(QuickAddParser.parse("run 5k every week", now, "EUR").entries.none { it.isBill })
        assertTrue(QuickAddParser.parse("save 100 every month", now, "EUR").entries.none { it.isBill })
        assertTrue(QuickAddParser.parse("salary 3000 every month", now, "EUR").entries.none { it.isBill })
        // Local money on a trip: the default currency is the trip's.
        val ramen = QuickAddParser.parse("ramen 1200", now, "JPY").entries.single { it.area == PlanArea.MONEY }
        assertEquals("JPY", ramen.currency)
        assertEquals(1200.0, ramen.amount)
    }

    // ── Rates feed ──

    @Test
    fun ratesFeedParsesAndSurvivesTheCache() {
        val body = """{"date":"2026-09-28","eur":{"usd":1.1,"jpy":160,"azn":2.0,"1inch":11.5}}"""
        val t = assertNotNull(FxRates.parse(body, "EUR"))
        assertEquals(setOf("USD", "JPY", "AZN"), t.perBase.keys)
        assertEquals("2026-09-28", t.date)
        assertEquals(t, FxRates.expand(FxRates.compact(t)))
        assertNull(FxRates.parse("not json", "EUR"))
        assertNull(FxRates.parse("""{"usd":{}}""", "EUR"))
    }

    @Test
    fun wholeOnlyCurrenciesRound() {
        assertEquals("¥1,200", MoneyFormat.format(1200.4, "JPY"))
        assertEquals("€7.40", MoneyFormat.format(7.4, "EUR"))
        assertEquals("₩15,000", MoneyFormat.format(15000.0, "KRW"))
    }
}
