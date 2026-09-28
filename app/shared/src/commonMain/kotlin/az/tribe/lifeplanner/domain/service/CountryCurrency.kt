package az.tribe.lifeplanner.domain.service

/** The money a country pays in, from its ISO 3166 code (Open-Meteo's `country_code`). */
object CountryCurrency {

    private val euro = setOf(
        "AT", "BE", "CY", "EE", "FI", "FR", "DE", "GR", "IE", "IT", "LV", "LT", "LU", "MT", "NL", "PT", "SK", "SI", "ES",
        "HR", "AD", "MC", "SM", "VA", "ME", "XK", "GP", "MQ", "GF", "RE", "YT", "PM", "BL", "MF",
    )

    private val table = mapOf(
        "AZ" to "AZN", "GE" to "GEL", "AM" to "AMD", "TR" to "TRY", "RU" to "RUB", "UA" to "UAH", "BY" to "BYN",
        "KZ" to "KZT", "UZ" to "UZS", "KG" to "KGS", "TJ" to "TJS", "TM" to "TMT", "MD" to "MDL",
        "GB" to "GBP", "CH" to "CHF", "LI" to "CHF", "NO" to "NOK", "SE" to "SEK", "DK" to "DKK", "IS" to "ISK",
        "PL" to "PLN", "CZ" to "CZK", "HU" to "HUF", "RO" to "RON", "BG" to "BGN", "RS" to "RSD", "BA" to "BAM",
        "MK" to "MKD", "AL" to "ALL", "GI" to "GIP",
        "US" to "USD", "PR" to "USD", "EC" to "USD", "SV" to "USD", "PA" to "USD", "CA" to "CAD", "MX" to "MXN",
        "BR" to "BRL", "AR" to "ARS", "CL" to "CLP", "CO" to "COP", "PE" to "PEN", "UY" to "UYU", "PY" to "PYG",
        "BO" to "BOB", "VE" to "VES", "CR" to "CRC", "GT" to "GTQ", "HN" to "HNL", "NI" to "NIO", "DO" to "DOP",
        "CU" to "CUP", "JM" to "JMD", "BS" to "BSD", "BB" to "BBD", "TT" to "TTD",
        "JP" to "JPY", "CN" to "CNY", "HK" to "HKD", "MO" to "MOP", "TW" to "TWD", "KR" to "KRW", "MN" to "MNT",
        "IN" to "INR", "PK" to "PKR", "BD" to "BDT", "LK" to "LKR", "NP" to "NPR", "MV" to "MVR", "BT" to "BTN",
        "TH" to "THB", "VN" to "VND", "KH" to "KHR", "LA" to "LAK", "MM" to "MMK", "MY" to "MYR", "SG" to "SGD",
        "ID" to "IDR", "PH" to "PHP", "BN" to "BND", "TL" to "USD",
        "AU" to "AUD", "NZ" to "NZD", "FJ" to "FJD", "PG" to "PGK",
        "AE" to "AED", "SA" to "SAR", "QA" to "QAR", "KW" to "KWD", "BH" to "BHD", "OM" to "OMR", "JO" to "JOD",
        "IL" to "ILS", "PS" to "ILS", "LB" to "LBP", "IQ" to "IQD", "IR" to "IRR", "EG" to "EGP", "AF" to "AFN",
        "MA" to "MAD", "DZ" to "DZD", "TN" to "TND", "LY" to "LYD",
        "ZA" to "ZAR", "NG" to "NGN", "KE" to "KES", "TZ" to "TZS", "UG" to "UGX", "RW" to "RWF", "ET" to "ETB",
        "GH" to "GHS", "SN" to "XOF", "CI" to "XOF", "ML" to "XOF", "BF" to "XOF", "BJ" to "XOF", "TG" to "XOF",
        "NE" to "XOF", "CM" to "XAF", "GA" to "XAF", "CG" to "XAF", "TD" to "XAF", "CF" to "XAF", "GQ" to "XAF",
        "MU" to "MUR", "SC" to "SCR", "MG" to "MGA", "NA" to "NAD", "BW" to "BWP", "ZM" to "ZMW", "ZW" to "USD",
        "MZ" to "MZN", "AO" to "AOA", "CV" to "CVE",
    )

    /** "JPY" for "JP". Null for a country not in the table: the trip then just stays in the home currency. */
    fun of(countryCode: String?): String? {
        val cc = countryCode?.trim()?.uppercase()?.takeIf { it.length == 2 } ?: return null
        return if (cc in euro) "EUR" else table[cc]
    }
}
