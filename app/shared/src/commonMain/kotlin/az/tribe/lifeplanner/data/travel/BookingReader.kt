package az.tribe.lifeplanner.data.travel

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.service.Booking
import az.tribe.lifeplanner.domain.service.Bookings
import co.touchlab.kermit.Logger
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Reads flights, hotels and trains out of a pasted confirmation, through the ai-proxy (no keys on
 * the phone). Parsing is never trusted: the trip page always shows what came back as an editable
 * preview before anything is saved.
 */
class BookingReader(private val ai: AiProxyService) {

    /** The bookings found, empty when there were none, null when the read itself failed. */
    suspend fun read(text: String, destination: String, tripStart: LocalDate): List<Booking>? {
        if (text.isBlank()) return emptyList()
        val prompt = """
            Below is text someone pasted from a travel booking confirmation (an email, a PDF, an app).
            They are going to $destination around $tripStart. List every flight, hotel or other stay,
            and train or bus in it. For each give:
            type: "flight", "hotel" or "train" (use "train" for buses and ferries too),
            name: the hotel or property name, or the airline or train company,
            number: the flight or train number, like "JL 42" (empty for hotels),
            from and to: where it leaves from and goes to, as short place or airport codes (empty for hotels),
            start and end: local date and time as YYYY-MM-DDTHH:MM (departure and arrival, or check in and check out),
            address: the hotel address if given,
            confirmation: the booking or confirmation code,
            price: the total price as a number, and currency: its ISO code like EUR or JPY.
            Resolve dates without a year to the next time that date comes, near $tripStart. Leave out
            anything that is not in the text. Do not invent anything.

            Text:
            ${text.take(12_000)}
        """.trimIndent()
        return try {
            val raw = ai.generateStructuredJson(prompt, schema())
            val items = Json.parseToJsonElement(raw).jsonObject["items"]?.jsonArray ?: return emptyList()
            items.mapNotNull { runCatching { Bookings.fromAi(it.jsonObject) }.getOrNull() }
                .also { PostHogAnalytics.capture("v4_travel_booking_read", mapOf("found" to it.size)) }
        } catch (e: Exception) {
            Logger.w("BookingReader") { "Booking read failed: ${e.message}" }
            PostHogAnalytics.capture("v4_travel_booking_read", mapOf("found" to -1))
            null
        }
    }

    private fun schema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("items") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("type") {
                            put("type", "string")
                            putJsonArray("enum") { add("flight"); add("hotel"); add("train") }
                        }
                        listOf("name", "number", "from", "to", "address", "confirmation", "currency").forEach { k ->
                            putJsonObject(k) { put("type", "string") }
                        }
                        putJsonObject("start") { put("type", "string"); put("description", "YYYY-MM-DDTHH:MM") }
                        putJsonObject("end") { put("type", "string"); put("description", "YYYY-MM-DDTHH:MM") }
                        putJsonObject("price") { put("type", "number") }
                    }
                    putJsonArray("required") { add(JsonPrimitive("type")); add(JsonPrimitive("name")) }
                }
            }
        }
        putJsonArray("required") { add(JsonPrimitive("items")) }
    }
}
