package az.tribe.lifeplanner.ui.v4.areas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.core.CurrencyPrefs
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.BudgetStatus
import az.tribe.lifeplanner.domain.service.MoneySummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class MoneyState(
    val currency: String = "EUR",
    val status: BudgetStatus? = null,
    val budgets: List<Budget> = emptyList(),
    val periodSpent: Double = 0.0,
    val periodIn: Double = 0.0,
    val byCategory: List<Pair<String, Double>> = emptyList(),
    val recent: List<LifeLog> = emptyList(),
)

@OptIn(ExperimentalUuidApi::class)
class V4MoneyViewModel(
    private val logs: LifeLogRepository,
    private val budgets: BudgetRepository,
    private val currencyPrefs: CurrencyPrefs,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private val currency = MutableStateFlow(currencyPrefs.code)

    // Two months back covers any month budget plus a little history for the list.
    private val window = Clock.System.todayIn(tz).let { it.minus(DatePeriod(days = 62)) to it }

    val state: StateFlow<MoneyState> = combine(
        logs.observeInRange(window.first, window.second),
        budgets.observeAll(),
        currency,
    ) { all, bs, cur ->
        val today = Clock.System.todayIn(tz)
        val money = all.filter { it.area == PlanArea.MONEY }
        val primary = MoneySummary.primary(bs)
        val status = primary?.let { MoneySummary.status(it, money, today) }
        val range = status?.range ?: MoneySummary.range(BudgetPeriod.MONTH, today)
        MoneyState(
            currency = cur,
            status = status,
            budgets = bs,
            periodSpent = money.filter { MoneySummary.isSpend(it) && it.date >= range.start && it.date <= range.end }.sumOf { it.amount ?: 0.0 },
            periodIn = money.filter { it.kind == LogKind.INCOME && it.date >= range.start && it.date <= range.end }.sumOf { it.amount ?: 0.0 },
            byCategory = MoneySummary.byCategory(money, range),
            recent = money.take(25),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyState(currency = currencyPrefs.code))

    fun setBudget(amount: Double, period: BudgetPeriod, category: String?) {
        if (amount <= 0) return
        viewModelScope.launch {
            val existing = MoneySummary.primary(budgets.getAll())
            budgets.save(
                Budget(
                    id = existing?.id ?: Uuid.random().toString(),
                    area = PlanArea.MONEY,
                    metric = Budget.METRIC_SPEND,
                    category = category,
                    amount = amount,
                    currency = currency.value,
                    period = period,
                ),
            )
            PostHogAnalytics.capture("v4_budget_set", mapOf("period" to period.key, "category" to (category ?: "all")))
        }
    }

    fun removeBudget(id: String) {
        viewModelScope.launch { budgets.delete(id) }
    }

    fun setCurrency(code: String) {
        currencyPrefs.code = code
        currency.value = code
    }

    fun deleteLog(id: String) {
        viewModelScope.launch { logs.delete(id) }
    }
}
