package az.tribe.lifeplanner.core

import com.russhwolf.settings.Settings
import kotlin.math.abs
import kotlin.math.roundToLong

/** The phone's own currency (ISO 4217), or null when the locale has none (e.g. "en"). */
expect fun platformCurrencyCode(): String?

/** The user's money currency. Starts as the phone's, changeable on the Money page. */
class CurrencyPrefs(private val settings: Settings) {
    var code: String
        get() = settings.getStringOrNull(KEY) ?: platformCurrencyCode() ?: "EUR"
        set(value) = settings.putString(KEY, value)

    private companion object {
        const val KEY = "v4_currency"
    }
}

object MoneyFormat {
    val common = listOf("EUR", "USD", "GBP", "AZN", "TRY", "RUB", "JPY", "INR", "AUD", "SGD", "CAD")

    private val symbols = mapOf(
        "EUR" to "€", "USD" to "$", "GBP" to "£", "AZN" to "₼", "TRY" to "₺", "RUB" to "₽", "JPY" to "¥",
        "INR" to "₹", "AUD" to "A$", "SGD" to "S$", "CAD" to "C$",
        "KRW" to "₩", "THB" to "฿", "VND" to "₫", "GEL" to "₾", "UAH" to "₴", "ILS" to "₪", "PHP" to "₱",
        "NGN" to "₦", "CNY" to "CN¥", "HKD" to "HK$", "NZD" to "NZ$", "MXN" to "MX$", "BRL" to "R$",
    )

    /** Currencies nobody writes cents for. */
    private val wholeOnly = setOf("JPY", "KRW", "VND", "IDR", "CLP", "ISK", "HUF", "COP", "UGX", "PYG")

    /** An amount as the user would type it back: "2400", "12.5". For prefilling fields. */
    fun plain(amount: Double): String =
        if (amount == kotlin.math.floor(amount)) amount.toLong().toString() else amount.toString()

    fun symbol(code: String?): String = symbols[code] ?: code?.let { "$it " } ?: ""

    /** "€12.50", "€1,240", "-€8". Whole amounts drop the cents; yen never has them. */
    fun format(amount: Double, code: String?): String {
        val neg = amount < 0
        val cents = if (code in wholeOnly) abs(amount).roundToLong() * 100 else (abs(amount) * 100).roundToLong()
        val whole = cents / 100
        val frac = cents % 100
        val wholeText = whole.toString().reversed().chunked(3).joinToString(",").reversed()
        val body = if (frac == 0L || code in wholeOnly) wholeText else "$wholeText.${frac.toString().padStart(2, '0')}"
        return (if (neg) "-" else "") + symbol(code) + body
    }
}
