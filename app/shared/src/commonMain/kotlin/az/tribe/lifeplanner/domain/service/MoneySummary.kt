package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** A budget period as dates, inclusive. */
data class PeriodRange(val start: LocalDate, val end: LocalDate)

/**
 * Where the money stands for one budget: spent so far in its period, what is left, how many days
 * remain. Spends in other currencies are converted into the budget's; any without a known rate are
 * kept in [unconverted] so the page can say so, never dropped. [billsDue] is what bills still
 * want before the period ends.
 */
data class BudgetStatus(
    val budget: Budget,
    val range: PeriodRange,
    val spent: Double,
    val daysLeft: Int,
    val unconverted: Map<String, Double> = emptyMap(),
    val billsDue: Double = 0.0,
) {
    val left: Double get() = budget.amount - spent
    val leftAfterBills: Double get() = left - billsDue
    val fraction: Float get() = if (budget.amount <= 0) 0f else (spent / budget.amount).toFloat()

    /** What can go out each day from here: what is left after bills, over the days left. Null when nothing is. */
    val perDay: Double? get() = if (daysLeft <= 0 || leftAfterBills <= 0) null else leftAfterBills / daysLeft
}

object MoneySummary {

    fun range(period: BudgetPeriod, today: LocalDate): PeriodRange = when (period) {
        BudgetPeriod.WEEK -> {
            val start = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
            PeriodRange(start, start.plus(DatePeriod(days = 6)))
        }
        BudgetPeriod.MONTH, BudgetPeriod.TRIP -> {
            val start = LocalDate(today.year, today.month, 1)
            PeriodRange(start, start.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)))
        }
    }

    /** Money that went out. Planned rows (bills waiting for their date) are not spends yet. */
    fun isSpend(log: LifeLog) =
        log.area == PlanArea.MONEY && log.kind == LogKind.EXPENSE && log.amount != null && log.status != LogStatus.PLANNED

    /**
     * [fx] converts spends in other currencies into the budget's. [bills] (planned bill rows) add
     * what is still due before the period ends, for "left after bills".
     */
    fun status(budget: Budget, logs: List<LifeLog>, today: LocalDate, fx: FxTable? = null, bills: List<LifeLog> = emptyList()): BudgetStatus {
        val r = range(budget.period, today)
        val spends = logs.filter { isSpend(it) && it.date >= r.start && it.date <= r.end }
            .filter { budget.category == null || it.category == budget.category }
        val total = Fx.total(spends, budget.currency, fx)
        val due = Bills.dueThrough(bills.filter { budget.category == null || it.category == budget.category }, r.end, budget.currency, fx)
        return BudgetStatus(budget, r, total.amount, today.daysUntil(r.end) + 1, total.unconverted, due.amount)
    }

    /** "€14 a day for the rest of the week", or null when nothing is left to spread. */
    fun perDayLine(s: BudgetStatus): String? {
        val per = s.perDay ?: return null
        val what = s.budget.category?.let { " for $it" } ?: ""
        val rest = when (s.budget.period) {
            BudgetPeriod.WEEK -> "the week"
            BudgetPeriod.MONTH, BudgetPeriod.TRIP -> "the month"
        }
        return if (s.daysLeft == 1) "${az.tribe.lifeplanner.core.MoneyFormat.format(floorTo(per), s.budget.currency)}$what left for today"
        else "${az.tribe.lifeplanner.core.MoneyFormat.format(floorTo(per), s.budget.currency)} a day$what for the rest of $rest"
    }

    /** Rounds a daily allowance down to something easy to hold in your head: whole units above 20. */
    fun floorTo(amount: Double): Double = if (amount >= 20) kotlin.math.floor(amount) else kotlin.math.floor(amount * 10) / 10

    /** The budget the app leads with: a spending one, the category one first ("food" is most felt). */
    fun primary(budgets: List<Budget>): Budget? =
        budgets.filter { it.area == PlanArea.MONEY && it.metric == Budget.METRIC_SPEND && it.tripId == null }
            .sortedWith(compareBy({ it.category == null }, { it.period != BudgetPeriod.WEEK }))
            .firstOrNull()

    /** Spending per category in [currency]. Spends with no known rate are left to [Fx.total]'s unconverted line. */
    fun byCategory(logs: List<LifeLog>, r: PeriodRange, currency: String? = null, fx: FxTable? = null): List<Pair<String, Double>> =
        logs.filter { isSpend(it) && it.date >= r.start && it.date <= r.end }
            .groupBy { it.category ?: "other" }
            .mapValues { (_, v) -> Fx.total(v, currency, fx).amount }
            .filterValues { it > 0 }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

    fun periodWord(period: BudgetPeriod) = when (period) {
        BudgetPeriod.WEEK -> "this week"
        BudgetPeriod.MONTH -> "this month"
        BudgetPeriod.TRIP -> "for this trip"
    }
}
