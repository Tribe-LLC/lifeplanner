package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
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
 * remain. Only spends in the budget's currency count; there is no conversion.
 */
data class BudgetStatus(
    val budget: Budget,
    val range: PeriodRange,
    val spent: Double,
    val daysLeft: Int,
) {
    val left: Double get() = budget.amount - spent
    val fraction: Float get() = if (budget.amount <= 0) 0f else (spent / budget.amount).toFloat()
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

    fun isSpend(log: LifeLog) = log.area == PlanArea.MONEY && log.kind == LogKind.EXPENSE && log.amount != null

    fun status(budget: Budget, logs: List<LifeLog>, today: LocalDate): BudgetStatus {
        val r = range(budget.period, today)
        val spent = logs.asSequence()
            .filter { isSpend(it) && it.date >= r.start && it.date <= r.end }
            .filter { budget.category == null || it.category == budget.category }
            .filter { it.currency == null || budget.currency == null || it.currency == budget.currency }
            .sumOf { it.amount ?: 0.0 }
        return BudgetStatus(budget, r, spent, today.daysUntil(r.end) + 1)
    }

    /** The budget the app leads with: a spending one, the category one first ("food" is most felt). */
    fun primary(budgets: List<Budget>): Budget? =
        budgets.filter { it.area == PlanArea.MONEY && it.metric == Budget.METRIC_SPEND && it.tripId == null }
            .sortedWith(compareBy({ it.category == null }, { it.period != BudgetPeriod.WEEK }))
            .firstOrNull()

    fun byCategory(logs: List<LifeLog>, r: PeriodRange): List<Pair<String, Double>> =
        logs.filter { isSpend(it) && it.date >= r.start && it.date <= r.end }
            .groupBy { it.category ?: "other" }
            .mapValues { (_, v) -> v.sumOf { it.amount ?: 0.0 } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

    fun periodWord(period: BudgetPeriod) = when (period) {
        BudgetPeriod.WEEK -> "this week"
        BudgetPeriod.MONTH -> "this month"
        BudgetPeriod.TRIP -> "for this trip"
    }
}
