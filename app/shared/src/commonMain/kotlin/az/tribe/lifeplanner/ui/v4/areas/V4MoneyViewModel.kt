package az.tribe.lifeplanner.ui.v4.areas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.core.CurrencyPrefs
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.money.BillService
import az.tribe.lifeplanner.data.money.FxRates
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.BillRepeat
import az.tribe.lifeplanner.domain.service.Bills
import az.tribe.lifeplanner.domain.service.BudgetStatus
import az.tribe.lifeplanner.domain.service.Fx
import az.tribe.lifeplanner.domain.service.FxTable
import az.tribe.lifeplanner.domain.service.MoneySummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A spend in its own currency, with what it is in the home currency when that differs. */
data class SpendRow(val log: LifeLog, val amount: String, val home: String?)

/** A bill in "Coming up": [paid] rows are this month's payments, still shown so they can be undone. */
data class BillRow(val bill: LifeLog?, val spend: LifeLog?, val title: String, val amount: String, val meta: String, val paid: Boolean) {
    val key: String get() = spend?.id ?: bill?.id ?: title
}

data class MoneyState(
    val currency: String = "EUR",
    val status: BudgetStatus? = null,
    val budgets: List<Budget> = emptyList(),
    /** "€14 a day for the rest of the week". */
    val perDay: String? = null,
    /** "€628 left after bills (€12 still due)". */
    val afterBills: String? = null,
    /** "+ ¥1,200 not converted yet". */
    val unconverted: String? = null,
    val periodIn: Double = 0.0,
    val byCategory: List<Pair<String, Double>> = emptyList(),
    val recent: List<SpendRow> = emptyList(),
    val bills: List<BillRow> = emptyList(),
    val laterBills: List<BillRow> = emptyList(),
    /** "€652 still due", "All paid". */
    val billsDue: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class V4MoneyViewModel(
    private val logs: LifeLogRepository,
    private val budgets: BudgetRepository,
    private val currencyPrefs: CurrencyPrefs,
    private val fx: FxRates,
    private val billService: BillService,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private val currency = MutableStateFlow(currencyPrefs.code)

    // Two months back covers any month budget plus a little history for the list.
    private val window = Clock.System.todayIn(tz).let { it.minus(DatePeriod(days = 62)) to it }

    val state: StateFlow<MoneyState> = combine(
        logs.observeInRange(window.first, window.second),
        budgets.observeAll(),
        currency,
        fx.table,
        billService.bills,
    ) { all, bs, cur, rates, bills -> build(all, bs, cur, rates, bills) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyState(currency = currencyPrefs.code))

    init {
        viewModelScope.launch { fx.refresh() }
    }

    private fun build(all: List<LifeLog>, bs: List<Budget>, cur: String, rates: FxTable?, bills: List<LifeLog>): MoneyState {
        val today = Clock.System.todayIn(tz)
        val money = all.filter { it.area == PlanArea.MONEY && it.status != LogStatus.PLANNED }
        val primary = MoneySummary.primary(bs)
        val status = primary?.let { MoneySummary.status(it, money, today, rates, bills) }
        val range = status?.range ?: MoneySummary.range(BudgetPeriod.MONTH, today)
        val inRange = money.filter { MoneySummary.isSpend(it) && it.date >= range.start && it.date <= range.end }
        val periodTotal = Fx.total(inRange, cur, rates)

        fun home(l: LifeLog): String? {
            val c = l.currency ?: return null
            if (c.equals(cur, ignoreCase = true)) return null
            return Fx.convert(rates, l.amount ?: 0.0, c, cur)?.let { "≈ ${MoneyFormat.format(it, cur)}" } ?: "not converted yet"
        }

        // Coming up this month: bills due by its end, and the ones already paid in it.
        val month = MoneySummary.range(BudgetPeriod.MONTH, today)
        val due = bills.filter { it.date <= month.end }
        val paid = money.filter { MoneySummary.isSpend(it) && Bills.paidFrom(it) != null && it.date >= month.start }
        val dueTotal = Bills.dueThrough(due, month.end, cur, rates)
        fun billRow(b: LifeLog) = BillRow(
            bill = b, spend = null, title = b.title,
            amount = MoneyFormat.format(b.amount ?: 0.0, b.currency),
            meta = Bills.dueLabel(b.date, today) + ", " + (Bills.ruleOf(b)?.let { Bills.everyLabel(it, b.date).replaceFirstChar { c -> c.lowercase() } } ?: ""),
            paid = false,
        )
        val paidRows = paid.map { s ->
            val next = Bills.paidFrom(s)?.first?.let { id -> bills.firstOrNull { it.id == id } }
            BillRow(
                bill = next, spend = s, title = s.title, amount = MoneyFormat.format(s.amount ?: 0.0, s.currency),
                meta = "Paid ${s.date.day} ${s.date.month.name.lowercase().replaceFirstChar { it.uppercase() }}" +
                    (next?.let { ". Next on ${it.date.day} ${it.date.month.name.lowercase().replaceFirstChar { c -> c.uppercase() }}" } ?: ""),
                paid = true,
            )
        }

        return MoneyState(
            currency = cur,
            status = status,
            budgets = bs,
            perDay = status?.let { MoneySummary.perDayLine(it) },
            afterBills = status?.takeIf { it.billsDue > 0 }?.let {
                "${MoneyFormat.format(it.leftAfterBills, it.budget.currency)} left after bills (${MoneyFormat.format(it.billsDue, it.budget.currency)} still due)"
            },
            unconverted = Fx.unconvertedLine(periodTotal),
            periodIn = Fx.total(money.filter { it.kind == LogKind.INCOME && it.date >= range.start && it.date <= range.end }, cur, rates).amount,
            byCategory = MoneySummary.byCategory(money, range, cur, rates),
            recent = money.take(25).map { SpendRow(it, (if (it.kind == LogKind.INCOME) "+" else "") + MoneyFormat.format(it.amount ?: 0.0, it.currency), home(it)) },
            bills = due.map(::billRow) + paidRows,
            laterBills = bills.filter { it.date > month.end }.map(::billRow),
            billsDue = when {
                due.isEmpty() && paidRows.isEmpty() -> null
                dueTotal.amount <= 0 && !dueTotal.hasUnconverted -> "All paid"
                else -> "${MoneyFormat.format(dueTotal.amount, cur)} still due"
            },
        )
    }

    /** The budget form no longer picks a category; an old category budget keeps its own. */
    fun setBudget(amount: Double, period: BudgetPeriod) {
        if (amount <= 0) return
        viewModelScope.launch {
            val existing = MoneySummary.primary(budgets.getAll())
            budgets.save(
                Budget(
                    id = existing?.id ?: Uuid.random().toString(),
                    area = PlanArea.MONEY,
                    metric = Budget.METRIC_SPEND,
                    category = existing?.category,
                    amount = amount,
                    currency = currency.value,
                    period = period,
                ),
            )
            PostHogAnalytics.capture("v4_budget_set", mapOf("period" to period.key, "category" to (existing?.category ?: "all")))
        }
    }

    fun removeBudget(id: String) {
        viewModelScope.launch { budgets.delete(id) }
    }

    fun setCurrency(code: String) {
        currencyPrefs.code = code
        currency.value = code
        viewModelScope.launch { fx.refresh() }
    }

    fun deleteLog(id: String) {
        viewModelScope.launch {
            val log = logs.getById(id)
            if (log != null && Bills.paidFrom(log) != null) billService.undoPaid(log) else logs.delete(id)
            PostHogAnalytics.capture("v4_money_spend_removed")
        }
    }

    /** Saves the changes made in the spend sheet. The date keeps the spend's time of day. */
    fun updateSpend(log: LifeLog, amount: Double, currency: String, category: String?, title: String, date: LocalDate) {
        if (amount <= 0) return
        viewModelScope.launch {
            logs.save(
                log.copy(
                    amount = amount, currency = currency, category = category ?: log.category,
                    title = title.trim().ifEmpty { log.title },
                    occurredAt = LocalDateTime(date, log.occurredAt.time),
                ),
            )
            PostHogAnalytics.capture("v4_money_spend_edited", mapOf("moved_day" to (date != log.date), "currency" to (currency != log.currency)))
        }
    }

    fun saveBill(existing: LifeLog?, title: String, amount: Double, currency: String, repeat: BillRepeat, due: LocalDate) {
        if (amount <= 0 || title.isBlank()) return
        viewModelScope.launch { billService.save(existing, title, amount, currency, repeat, due) }
    }

    fun stopBill(bill: LifeLog) {
        viewModelScope.launch { billService.stop(bill) }
    }

    fun togglePaid(row: BillRow) {
        viewModelScope.launch {
            if (row.paid) row.spend?.let { billService.undoPaid(it) } else row.bill?.let { billService.markPaid(it) }
        }
    }
}
