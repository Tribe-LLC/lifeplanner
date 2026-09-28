package az.tribe.lifeplanner.domain.service

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class BookingKind(val key: String, val label: String) {
    FLIGHT("flight", "Flight"), HOTEL("hotel", "Hotel"), TRAIN("train", "Train"), OTHER("other", "Booking");

    /** Where its price goes in the trip budget. */
    val spendKind: TripPlanner.SpendKind
        get() = when (this) {
            FLIGHT -> TripPlanner.SpendKind.FLIGHTS
            HOTEL -> TripPlanner.SpendKind.STAY
            TRAIN -> TripPlanner.SpendKind.GETTING_AROUND
            OTHER -> TripPlanner.SpendKind.OTHER
        }

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key?.lowercase()?.trim() } ?: when (key?.lowercase()?.trim()) {
            "stay", "airbnb", "hostel", "accommodation", "lodging" -> HOTEL
            "rail", "bus", "ferry" -> TRAIN
            "plane", "air" -> FLIGHT
            else -> OTHER
        }
    }
}

/**
 * A flight, a hotel or a train, read from a pasted confirmation. Kept on the trip as one trip item
 * (its fields in the notes) and shown on the days it touches: a flight where it leaves and lands, a
 * hotel on check in and check out.
 */
data class Booking(
    val kind: BookingKind,
    val name: String,
    val from: String? = null,
    val to: String? = null,
    val start: LocalDateTime? = null,
    val end: LocalDateTime? = null,
    val address: String? = null,
    val code: String? = null,
    val price: Double? = null,
    val currency: String? = null,
) {
    val startDate: LocalDate? get() = start?.date
    val endDate: LocalDate? get() = end?.date ?: start?.date
}

object Bookings {

    /** The lines a booking puts on [date] of the trip timeline. Empty on days it does not touch. */
    fun linesFor(b: Booking, date: LocalDate): List<String> {
        val s = b.start
        val e = b.end
        val out = mutableListOf<String>()
        val route = listOfNotNull(b.from, b.to).takeIf { it.size == 2 }?.joinToString(" to ")
        when (b.kind) {
            BookingKind.FLIGHT -> {
                if (s?.date == date) {
                    val lands = if (e != null && e.date == date) " to ${clock(e.time)}" else ""
                    out += listOfNotNull(b.name, route).joinToString(", ") + ", ${clock(s.time)}$lands"
                }
                if (e != null && e.date == date && s?.date != date) out += "${b.name} lands ${clock(e.time)}" + (b.to?.let { ", $it" } ?: "")
            }
            BookingKind.HOTEL -> {
                if (s?.date == date) out += "${b.name}, check in" + timeOrEmpty(s)
                if (e != null && e.date == date && s?.date != date) out += "Check out" + timeOrEmpty(e) + ", ${b.name}"
            }
            BookingKind.TRAIN, BookingKind.OTHER -> {
                if (s?.date == date) out += b.name + timeOrEmpty(s) + (b.to?.let { " to $it" } ?: "")
                if (e != null && e.date == date && s?.date != date) out += "${b.name} arrives" + timeOrEmpty(e)
            }
        }
        return out
    }

    private fun timeOrEmpty(t: LocalDateTime) = if (t.hour == 0 && t.minute == 0) "" else " ${clock(t.time)}"

    fun clock(t: LocalTime) = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

    /** One booking from the AI's JSON. Null when there is nothing to call it. */
    fun fromAi(o: JsonObject): Booking? {
        fun str(k: String) = runCatching { o[k]?.jsonPrimitive?.contentOrNull?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() && it.lowercase() != "null" }
        val kind = BookingKind.fromKey(str("type") ?: str("kind"))
        val number = str("number")
        val name = when (kind) {
            BookingKind.FLIGHT, BookingKind.TRAIN -> listOfNotNull(number ?: str("name")).firstOrNull()
            else -> str("name") ?: number
        } ?: return null
        val price = runCatching { o["price"]?.jsonPrimitive?.doubleOrNull ?: o["price"]?.jsonPrimitive?.contentOrNull?.replace(",", "")?.toDoubleOrNull() }.getOrNull()
        return Booking(
            kind = kind, name = name, from = str("from"), to = str("to"),
            start = str("start")?.let { parseIso(it) }, end = str("end")?.let { parseIso(it) },
            address = str("address"), code = str("confirmation") ?: str("code"),
            price = price?.takeIf { it > 0 }, currency = str("currency")?.uppercase()?.takeIf { it.length == 3 },
        )
    }

    /** "2026-10-10T15:00", "2026-10-10 15:00", or a bare "2026-10-10". */
    fun parseIso(s: String): LocalDateTime? {
        val t = s.trim().replace(' ', 'T')
        runCatching { return LocalDateTime.parse(t.take(16)) }
        runCatching { return LocalDateTime(LocalDate.parse(t.take(10)), LocalTime(0, 0)) }
        return null
    }

    private val shortMonths = Month.entries.map { it.name.take(3).lowercase() }

    /** "10 Oct 15:00", or "10 Oct" at midnight. What the preview fields show and read back. */
    fun formatWhen(t: LocalDateTime?): String {
        t ?: return ""
        val day = "${t.date.day} ${t.date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"
        return if (t.hour == 0 && t.minute == 0) day else "$day ${clock(t.time)}"
    }

    /**
     * Reads a date typed into a preview field: "10 Oct 15:00", "10 october", "Oct 10 9:05",
     * "2026-10-10 15:00". A date without a year takes the one nearest [near].
     */
    fun parseWhen(text: String, near: LocalDate): LocalDateTime? {
        val s = text.trim()
        if (s.isEmpty()) return null
        parseIso(s)?.let { return it }
        val lower = s.lowercase()
        val time = Regex("""(\d{1,2})[:.](\d{2})""").find(lower)?.let { m ->
            val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
            if (h in 0..23 && mi in 0..59) LocalTime(h, mi) else null
        } ?: LocalTime(0, 0)
        val noTime = lower.replace(Regex("""\d{1,2}[:.]\d{2}"""), " ")
        val monthIdx = shortMonths.indexOfFirst { Regex("""\b$it[a-z]*\b""").containsMatchIn(noTime) }.takeIf { it >= 0 } ?: return null
        val nums = Regex("""\b(\d{1,4})\b""").findAll(noTime).map { it.groupValues[1].toInt() }.toList()
        val day = nums.firstOrNull { it in 1..31 } ?: return null
        val year = nums.firstOrNull { it >= 2000 }
        val date = runCatching {
            if (year != null) LocalDate(year, monthIdx + 1, day)
            else listOf(near.year - 1, near.year, near.year + 1).map { LocalDate(it, monthIdx + 1, day) }
                .minBy { kotlin.math.abs(it.toEpochDays() - near.toEpochDays()) }
        }.getOrNull() ?: return null
        return LocalDateTime(date, time)
    }

    // ── Kept in the trip item's notes ──

    fun encode(b: Booking): String = buildJsonObject {
        put("k", b.kind.key)
        put("n", b.name)
        b.from?.let { put("f", it) }
        b.to?.let { put("t", it) }
        b.start?.let { put("s", it.toString()) }
        b.end?.let { put("e", it.toString()) }
        b.address?.let { put("a", it) }
        b.code?.let { put("c", it) }
        b.price?.let { put("p", JsonPrimitive(it)) }
        b.currency?.let { put("cur", it) }
    }.toString()

    fun decode(notes: String?): Booking? = runCatching {
        val o = Json.parseToJsonElement(notes ?: return null).jsonObject
        fun str(k: String) = o[k]?.jsonPrimitive?.contentOrNull
        Booking(
            kind = BookingKind.fromKey(str("k")), name = str("n") ?: return null, from = str("f"), to = str("t"),
            start = str("s")?.let { parseIso(it) }, end = str("e")?.let { parseIso(it) },
            address = str("a"), code = str("c"), price = o["p"]?.jsonPrimitive?.doubleOrNull, currency = str("cur"),
        )
    }.getOrNull()
}
