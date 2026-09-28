package az.tribe.lifeplanner.ui.v4.today

import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.money.BillService
import az.tribe.lifeplanner.data.money.FxRates
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.Bills
import az.tribe.lifeplanner.domain.service.FxTable
import az.tribe.lifeplanner.domain.service.MoneySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Money's part of Today: the chip under the title and the bills due today or tomorrow. */
data class TodayMoneyState(val chip: String? = null, val bills: List<DayItem> = emptyList())

/**
 * Kept out of [V4TodayViewModel] so Money can grow without touching the shared Today files: the
 * view model reads [state] and hands bill rows back to [toggle].
 */
class TodayMoney(
    private val logs: LifeLogRepository,
    private val budgets: BudgetRepository,
    private val billService: BillService,
    private val fx: FxRates,
) {
    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: Flow<TodayMoneyState> = combine(
        budgets.observeAll(),
        logs.observeInRange(today().minus(DatePeriod(days = 40)), today()),
        billService.bills,
        fx.table,
    ) { bs, recent, bills, rates -> build(bs, recent, bills, rates, today()) }
        // Rates load in the background; the chip shows at once from the cached ones.
        .onStart { scope.launch { runCatching { fx.refresh() } } }

    private fun build(bs: List<Budget>, recent: List<LifeLog>, bills: List<LifeLog>, rates: FxTable?, today: LocalDate): TodayMoneyState {
        val chip = MoneySummary.primary(bs)?.let { b ->
            val st = MoneySummary.status(b, recent, today, rates, bills)
            val what = b.category?.let { " for $it" } ?: ""
            when {
                st.left < 0 -> "${MoneyFormat.format(-st.left, b.currency)} over$what ${MoneySummary.periodWord(b.period)}"
                else -> MoneySummary.perDayLine(st) ?: "${MoneyFormat.format(st.left, b.currency)} left$what ${MoneySummary.periodWord(b.period)}"
            }
        }
        return TodayMoneyState(chip, billItems(bills, recent, today))
    }

    /** Toggles a bill row from Today: ticked means paid, unticked takes the payment back. */
    suspend fun toggle(item: DayItem) {
        if (item.done) {
            logs.getById(item.refId)?.let { billService.undoPaid(it) }
        } else {
            logs.getById(item.refId)?.takeIf { Bills.isBill(it) }?.let { billService.markPaid(it) }
        }
    }

    companion object {
        /** Bills unpaid from a week back through tomorrow, and the ones paid today (ticked, so they can be undone). */
        fun billItems(bills: List<LifeLog>, recent: List<LifeLog>, today: LocalDate): List<DayItem> {
            val tomorrow = LocalDate.fromEpochDays(today.toEpochDays() + 1)
            val weekAgo = LocalDate.fromEpochDays(today.toEpochDays() - 7)
            val due = bills.filter { Bills.isBill(it) && it.date in weekAgo..tomorrow }.map { b ->
                DayItem(
                    key = "bill_${b.id}", type = DayItemType.BILL, refId = b.id, time = null,
                    title = "${b.title} ${MoneyFormat.format(b.amount ?: 0.0, b.currency)}",
                    area = PlanArea.MONEY, meta = Bills.dueLabel(b.date, today), done = false, checkable = true,
                )
            }
            val paid = recent.filter { it.date == today && MoneySummary.isSpend(it) && Bills.paidFrom(it) != null }.map { s ->
                DayItem(
                    key = "bill_paid_${s.id}", type = DayItemType.BILL, refId = s.id, time = null,
                    title = "${s.title} ${MoneyFormat.format(s.amount ?: 0.0, s.currency)}",
                    area = PlanArea.MONEY, meta = "Paid", done = true, checkable = true,
                )
            }
            return due + paid
        }
    }
}
