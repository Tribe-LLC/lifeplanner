package az.tribe.lifeplanner.data.money

import az.tribe.lifeplanner.core.CurrencyPrefs
import az.tribe.lifeplanner.domain.service.FxTable
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock

/**
 * Daily exchange rates for the home currency, from the free, keyless currency-api (jsDelivr, with
 * its Cloudflare mirror as a fallback). Fetched at most once a day, kept in Settings, and used
 * from there offline for as long as it takes. Every failure is quiet: the last table stays.
 */
class FxRates(
    private val client: HttpClient,
    private val settings: Settings,
    private val currency: CurrencyPrefs,
) {
    private val _table = MutableStateFlow(readCache())
    val table: StateFlow<FxTable?> = _table.asStateFlow()

    private val lock = Mutex()
    private var lastTry = 0L
    private var lastBase: String? = null

    /** Makes sure today's rates for the home currency are in, if the network allows. */
    suspend fun refresh() {
        val base = currency.code.uppercase()
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val cached = _table.value
        if (cached != null && cached.base == base && settings.getStringOrNull(KEY_FETCHED) == today) return
        lock.withLock {
            val now = Clock.System.now().toEpochMilliseconds()
            // A failed try waits a while before the next one, so a bad connection is not hammered.
            if (now - lastTry < RETRY_MS && lastBase == base) return
            lastTry = now
            lastBase = base
            for (url in urls(base.lowercase())) {
                val body = runCatching {
                    val r = client.get(url)
                    if (r.status.isSuccess()) r.bodyAsText() else null
                }.onFailure { Logger.w("FxRates") { "Rates failed from $url: ${it.message}" } }.getOrNull() ?: continue
                val parsed = parse(body, base) ?: continue
                settings.putString(KEY_BODY, compact(parsed))
                settings.putString(KEY_FETCHED, today)
                _table.value = parsed
                return
            }
        }
    }

    private fun readCache(): FxTable? = settings.getStringOrNull(KEY_BODY)?.let { expand(it) }

    companion object {
        private const val KEY_BODY = "v4_fx_table"
        private const val KEY_FETCHED = "v4_fx_fetched_on"
        private const val RETRY_MS = 30 * 60 * 1000L

        fun urls(base: String) = listOf(
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/$base.json",
            "https://latest.currency-api.pages.dev/v1/currencies/$base.json",
        )

        /** Reads the API's `{"date": "...", "eur": {"usd": 1.08, ...}}` into a table for [base]. */
        fun parse(body: String, base: String): FxTable? = runCatching {
            val root = Json.parseToJsonElement(body).jsonObject
            val rates = root[base.lowercase()]?.jsonObject ?: return null
            val map = rates.mapNotNull { (k, v) ->
                val r = runCatching { v.jsonPrimitive.doubleOrNull }.getOrNull()
                // Three-letter codes only: the feed also carries crypto and metals.
                if (r != null && r > 0 && k.length == 3) k.uppercase() to r else null
            }.toMap()
            if (map.isEmpty()) null else FxTable(base.uppercase(), map, root["date"]?.jsonPrimitive?.content)
        }.getOrNull()

        /** "EUR|2026-09-28|USD=1.08;JPY=162.3": small enough for Settings. */
        fun compact(t: FxTable): String = t.base + "|" + (t.date ?: "") + "|" + t.perBase.entries.joinToString(";") { "${it.key}=${it.value}" }

        fun expand(s: String): FxTable? = runCatching {
            val parts = s.split('|', limit = 3)
            if (parts.size < 3) return null
            val map = parts[2].split(';').mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to (it[1].toDoubleOrNull() ?: return@mapNotNull null) } }.toMap()
            FxTable(parts[0], map, parts[1].ifEmpty { null })
        }.getOrNull()
    }
}
