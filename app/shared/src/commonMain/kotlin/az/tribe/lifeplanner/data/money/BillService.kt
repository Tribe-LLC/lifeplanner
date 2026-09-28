package az.tribe.lifeplanner.data.money

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.BillRepeat
import az.tribe.lifeplanner.domain.service.BillRule
import az.tribe.lifeplanner.domain.service.Bills
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Bills and subscriptions, kept as planned Money rows (see [Bills]). Paying one files a normal
 * spend and moves the bill to its next date; undoing the payment puts both back.
 */
@OptIn(ExperimentalUuidApi::class)
class BillService(private val logs: LifeLogRepository) {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    /** Every bill, soonest first. Overdue ones stay until they are paid or stopped. */
    val bills: Flow<List<LifeLog>> = today().let { t ->
        logs.observeInRange(t.minus(DatePeriod(days = 400)), t.plus(DatePeriod(days = 800)))
            .map { list -> list.filter { Bills.isBill(it) }.sortedBy { it.date } }
    }

    /** Adds a bill, or changes [existing]. Returns the saved row. */
    suspend fun save(existing: LifeLog?, title: String, amount: Double, currency: String, repeat: BillRepeat, due: LocalDate, category: String? = null): LifeLog {
        val rule = BillRule.startingOn(repeat, due)
        val row = (existing ?: LifeLog(
            id = Uuid.random().toString(), area = PlanArea.MONEY, kind = LogKind.EXPENSE, status = LogStatus.PLANNED,
            title = title, occurredAt = LocalDateTime(due, DUE_TIME), source = LifeLog.SOURCE_PLAN,
        )).copy(
            title = title.trim(), amount = amount, currency = currency, status = LogStatus.PLANNED,
            category = category ?: existing?.category ?: Bills.CATEGORY,
            occurredAt = LocalDateTime(due, DUE_TIME), notes = rule.encode(),
        )
        logs.save(row)
        if (existing == null) PostHogAnalytics.capture("v4_money_bill_added", mapOf("repeat" to repeat.key, "source" to "sheet"))
        return row
    }

    suspend fun stop(bill: LifeLog) {
        logs.delete(bill.id)
        PostHogAnalytics.capture("v4_money_bill_stopped")
    }

    /** Files the payment as a spend today and moves the bill on to its next date. */
    suspend fun markPaid(bill: LifeLog) {
        val rule = Bills.ruleOf(bill) ?: return
        val now = Clock.System.now().toLocalDateTime(tz)
        logs.save(
            LifeLog(
                id = Uuid.random().toString(), area = PlanArea.MONEY, kind = LogKind.EXPENSE, title = bill.title,
                amount = bill.amount, currency = bill.currency, category = bill.category ?: Bills.CATEGORY,
                occurredAt = now, source = LifeLog.SOURCE_PLAN, externalId = Bills.paidMarker(bill.id, bill.date),
            ),
        )
        logs.save(bill.copy(occurredAt = LocalDateTime(Bills.next(rule, bill.date), DUE_TIME)))
        PostHogAnalytics.capture("v4_money_bill_paid", mapOf("repeat" to rule.repeat.key, "early" to (bill.date > now.date)))
    }

    /** Takes a bill payment back: the spend goes, and the bill returns to the date it was paid for. */
    suspend fun undoPaid(spend: LifeLog) {
        val (billId, due) = Bills.paidFrom(spend) ?: return
        logs.delete(spend.id)
        logs.getById(billId)?.takeIf { Bills.isBill(it) && it.date > due }?.let { logs.save(it.copy(occurredAt = LocalDateTime(due, DUE_TIME))) }
    }

    private companion object {
        val DUE_TIME = LocalTime(9, 0)
    }
}
