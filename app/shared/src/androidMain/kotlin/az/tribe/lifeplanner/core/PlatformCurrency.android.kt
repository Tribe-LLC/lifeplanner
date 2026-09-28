package az.tribe.lifeplanner.core

import java.util.Currency
import java.util.Locale

actual fun platformCurrencyCode(): String? =
    runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrNull()
