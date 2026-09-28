package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

enum class BillRepeat(val key: String, val label: String) {
    WEEKLY("weekly", "Weekly"), MONTHLY("monthly", "Monthly"), YEARLY("yearly", "Yearly");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/**
 * How a bill repeats. [day] is the day of the month for monthly and yearly bills (kept so a bill on
 * the 31st comes back to the 31st after a short month), [month] the month of a yearly one.
 * Stored in the bill row's notes as "repeat=monthly;day=1".
 */
data class BillRule(val repeat: BillRepeat, val day: Int? = null, val month: Int? = null) {

    fun encode(): String = buildString {
        append("repeat=").append(repeat.key)
        day?.let { append(";day=").append(it) }
        month?.let { append(";month=").append(it) }
    }

    companion object {
        fun decode(notes: String?): BillRule? {
            if (notes == null || !notes.startsWith("repeat=")) return null
            val parts = notes.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
            val repeat = BillRepeat.fromKey(parts["repeat"]) ?: return null
            return BillRule(repeat, parts["day"]?.toIntOrNull(), parts["month"]?.toIntOrNull())
        }

        /** The rule for a bill first due on [due]: the same weekday, day of the month, or date each time. */
        fun startingOn(repeat: BillRepeat, due: LocalDate) = when (repeat) {
            BillRepeat.WEEKLY -> BillRule(repeat)
            BillRepeat.MONTHLY -> BillRule(repeat, day = due.day)
            BillRepeat.YEARLY -> BillRule(repeat, day = due.day, month = due.month.ordinal + 1)
        }
    }
}

/**
 * Bills and subscriptions: rent, the phone, Netflix. A bill is one Money row that waits as
 * planned on its next due date, with its [BillRule] in the notes. Paying it logs an ordinary spend
 * (so budgets count it once) and moves the bill on to its next date. Nothing else reads planned
 * Money rows as spends; see [MoneySummary.isSpend].
 */
object Bills {

    const val CATEGORY = "bills"
    private const val PAID_PREFIX = "bill:"

    fun isBill(log: LifeLog) =
        log.area == PlanArea.MONEY && log.kind == LogKind.EXPENSE && log.status == LogStatus.PLANNED && BillRule.decode(log.notes) != null

    fun ruleOf(log: LifeLog): BillRule? = BillRule.decode(log.notes)

    /** The date after [current] that a bill with [rule] falls due next. */
    fun next(rule: BillRule, current: LocalDate): LocalDate = when (rule.repeat) {
        BillRepeat.WEEKLY -> current.plus(DatePeriod(days = 7))
        BillRepeat.MONTHLY -> {
            val first = LocalDate(current.year, current.month, 1).plus(DatePeriod(months = 1))
            clampDay(first, rule.day ?: current.day)
        }
        BillRepeat.YEARLY -> {
            val m = rule.month ?: (current.month.ordinal + 1)
            clampDay(LocalDate(current.year + 1, m, 1), rule.day ?: current.day)
        }
    }

    /** The first due date on or after [today] for a bill due on [day] of the month (or weekday for weekly). */
    fun firstDue(repeat: BillRepeat, today: LocalDate, day: Int? = null, weekday: DayOfWeek? = null): LocalDate = when (repeat) {
        BillRepeat.WEEKLY -> {
            val want = weekday ?: today.dayOfWeek
            today.plus(DatePeriod(days = (want.ordinal - today.dayOfWeek.ordinal + 7) % 7))
        }
        BillRepeat.MONTHLY -> {
            val d = day ?: today.day
            val thisMonth = clampDay(LocalDate(today.year, today.month, 1), d)
            if (thisMonth >= today) thisMonth else clampDay(LocalDate(today.year, today.month, 1).plus(DatePeriod(months = 1)), d)
        }
        BillRepeat.YEARLY -> today
    }

    /** Every date a bill falls due from its current due date through [to]. Overdue ones count: still unpaid. */
    fun occurrences(bill: LifeLog, to: LocalDate, limit: Int = 60): List<LocalDate> {
        val rule = ruleOf(bill) ?: return emptyList()
        val out = mutableListOf<LocalDate>()
        var d = bill.date
        while (d <= to && out.size < limit) {
            out += d
            d = next(rule, d)
        }
        return out
    }

    /** What is still to pay from [bills] through [to], in [currency]. Unknown rates are kept apart. */
    fun dueThrough(bills: List<LifeLog>, to: LocalDate, currency: String?, fx: FxTable?): MoneyTotal {
        var sum = 0.0
        val left = mutableMapOf<String, Double>()
        bills.filter { isBill(it) }.forEach { b ->
            val n = occurrences(b, to).size
            if (n == 0) return@forEach
            val amount = (b.amount ?: 0.0) * n
            val c = Fx.convert(fx, amount, b.currency, currency)
            if (c != null) sum += c else left[b.currency!!] = (left[b.currency] ?: 0.0) + amount
        }
        return MoneyTotal(sum, left)
    }

    /** The external id a paid bill's spend carries, so the payment can be undone: "bill:<id>:<due date>". */
    fun paidMarker(billId: String, due: LocalDate) = "$PAID_PREFIX$billId:$due"

    /** The bill id and the due date a spend paid, or null for an ordinary spend. */
    fun paidFrom(spend: LifeLog): Pair<String, LocalDate>? {
        val ext = spend.externalId ?: return null
        if (!ext.startsWith(PAID_PREFIX)) return null
        val rest = ext.removePrefix(PAID_PREFIX)
        val cut = rest.lastIndexOf(':')
        if (cut <= 0) return null
        val date = runCatching { LocalDate.parse(rest.substring(cut + 1)) }.getOrNull() ?: return null
        return rest.substring(0, cut) to date
    }

    /** "Due today", "Due tomorrow", "Due 3 October", "Was due 28 September". */
    fun dueLabel(due: LocalDate, today: LocalDate): String {
        val days = (due.toEpochDays() - today.toEpochDays()).toInt()
        return when {
            days == 0 -> "Due today"
            days == 1 -> "Due tomorrow"
            days < 0 -> "Was due ${due.day} ${TripPlanner.monthName(due.month)}"
            days < 7 -> "Due ${dayName(due.dayOfWeek)}"
            else -> "Due ${due.day} ${TripPlanner.monthName(due.month)}"
        }
    }

    /** "Every month on the 1st", "Every week on Friday", "Every year on 3 October". */
    fun everyLabel(rule: BillRule, due: LocalDate): String = when (rule.repeat) {
        BillRepeat.WEEKLY -> "Every week on ${dayName(due.dayOfWeek)}"
        BillRepeat.MONTHLY -> "Every month on the ${ordinal(rule.day ?: due.day)}"
        BillRepeat.YEARLY -> "Every year on ${rule.day ?: due.day} ${TripPlanner.monthName(kotlinx.datetime.Month(rule.month ?: (due.month.ordinal + 1)))}"
    }

    fun ordinal(n: Int): String = n.toString() + when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }

    fun dayName(d: DayOfWeek) = d.name.lowercase().replaceFirstChar { it.uppercase() }

    private fun clampDay(firstOfMonth: LocalDate, day: Int): LocalDate {
        val last = firstOfMonth.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
        return LocalDate(firstOfMonth.year, firstOfMonth.month, day.coerceIn(1, last))
    }
}
