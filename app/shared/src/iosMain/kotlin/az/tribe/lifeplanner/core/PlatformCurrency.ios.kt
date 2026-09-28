package az.tribe.lifeplanner.core

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.currencyCode

actual fun platformCurrencyCode(): String? = NSLocale.currentLocale.currencyCode
