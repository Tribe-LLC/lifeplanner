package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.LifeLog

/**
 * Exchange rates against one [base] currency: [perBase] is how many of each currency one [base]
 * buys ("EUR" to 1, "JPY" to 162.3). Codes are upper case. Cross rates go through the base, so a
 * table for euros still converts yen to manat.
 */
data class FxTable(val base: String, val perBase: Map<String, Double>, val date: String? = null) {

    fun rate(code: String): Double? =
        if (code.equals(base, ignoreCase = true)) 1.0 else perBase[code.uppercase()]?.takeIf { it > 0 }

    /** [amount] in [from] as [to]. Null when a rate is unknown. The same currency never needs one. */
    fun convert(amount: Double, from: String?, to: String?): Double? {
        if (from == null || to == null || from.equals(to, ignoreCase = true)) return amount
        val f = rate(from) ?: return null
        val t = rate(to) ?: return null
        return amount / f * t
    }
}

/** A sum in one currency, with what could not be converted kept apart per currency, never dropped. */
data class MoneyTotal(val amount: Double, val unconverted: Map<String, Double> = emptyMap()) {
    val hasUnconverted: Boolean get() = unconverted.isNotEmpty()
}

object Fx {

    /** Converts one amount, treating a missing table like an empty one: only same-currency works. */
    fun convert(fx: FxTable?, amount: Double, from: String?, to: String?): Double? =
        fx?.convert(amount, from, to) ?: if (from == null || to == null || from.equals(to, ignoreCase = true)) amount else null

    /**
     * Adds up [logs] in [to]. A row with no currency is taken to be in [to] (rows from before
     * currencies were stored). Rows with no known rate go to [MoneyTotal.unconverted].
     */
    fun total(logs: List<LifeLog>, to: String?, fx: FxTable?): MoneyTotal {
        var sum = 0.0
        val left = mutableMapOf<String, Double>()
        logs.forEach { l ->
            val amount = l.amount ?: return@forEach
            val converted = convert(fx, amount, l.currency, to)
            if (converted != null) sum += converted
            else left[l.currency!!] = (left[l.currency] ?: 0.0) + amount
        }
        return MoneyTotal(sum, left)
    }

    /** "+ ¥1,200 not converted yet", or null when everything converted. */
    fun unconvertedLine(total: MoneyTotal): String? = total.unconverted.takeIf { it.isNotEmpty() }?.let { m ->
        "+ " + m.entries.joinToString(" and ") { (code, amount) -> az.tribe.lifeplanner.core.MoneyFormat.format(amount, code) } + " not converted yet"
    }
}
