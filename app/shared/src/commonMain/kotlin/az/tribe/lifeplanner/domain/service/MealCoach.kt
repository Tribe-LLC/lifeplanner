package az.tribe.lifeplanner.domain.service

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** One ingredient the coach suggests: "2" "red peppers", in the fruit and veg aisle. */
data class CoachIngredient(val item: String, val quantity: String? = null, val aisle: Aisle? = null) {
    /** As a shopping line: "2 red peppers". */
    val line: String get() = listOfNotNull(quantity?.trim()?.takeIf { it.isNotEmpty() }, item.trim()).joinToString(" ")
}

/** One dinner in the coach's week, for the user to keep, swap or drop before anything is saved. */
data class CoachDinner(val date: LocalDate, val title: String, val minutes: Int? = null, val ingredients: List<CoachIngredient> = emptyList())

/** What the coach knows when it plans: all of it optional. */
data class CoachContext(
    val foodBudget: String? = null,
    val household: Int? = null,
    val likes: List<String> = emptyList(),
    val have: List<String> = emptyList(),
    val onList: List<String> = emptyList(),
    val planned: List<String> = emptyList(),
)

/**
 * "Plan my week": the prompt and schema for the ai-proxy, and reading its answer. Kept apart from
 * the network so the reading can be tested: the model may skip a day, repeat one, or invent a date,
 * and each dinner still lands on one of the evenings asked for, at most once.
 */
object MealCoach {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun prompt(dates: List<LocalDate>, ctx: CoachContext, avoid: List<String> = emptyList()): String = buildString {
        val days = dates.joinToString(", ") { "${FitnessWeek.dayName(it.dayOfWeek)} $it" }
        append("Plan ${dates.size} simple home-cooked ${if (dates.size == 1) "dinner" else "dinners"}, one for each of these evenings: $days.\n")
        append("Cooking for ${ctx.household ?: 1} ${if ((ctx.household ?: 1) == 1) "person" else "people"}. Scale quantities to that.\n")
        ctx.foodBudget?.let { append("Food money: $it. Keep the week within it, with cheap everyday ingredients.\n") }
        if (ctx.likes.isNotEmpty()) append("Dishes they often make (use a couple, vary the rest): ${ctx.likes.take(8).joinToString(", ")}.\n")
        if (ctx.have.isNotEmpty()) append("Already in the kitchen, use these first: ${ctx.have.take(20).joinToString(", ")}.\n")
        if (ctx.onList.isNotEmpty()) append("Already on the shopping list: ${ctx.onList.take(20).joinToString(", ")}.\n")
        if (ctx.planned.isNotEmpty()) append("Already planned this week (do not repeat): ${ctx.planned.joinToString(", ")}.\n")
        if (avoid.isNotEmpty()) append("Do not suggest any of these: ${avoid.joinToString(", ")}.\n")
        append(
            """
            Reuse ingredients across days so little is wasted (half a bag of spinach on Monday goes into Wednesday).
            Each dinner: a short plain title, total minutes, and every ingredient to buy with a quantity
            (like "2", "400 g", "1 can") and its aisle. Aisle is one of: produce, bakery, dairy, meat, pantry, frozen, drinks, other.
            Use the date given for each evening as "day" (YYYY-MM-DD).
            """.trimIndent(),
        )
    }

    fun schema(): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("dinners") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("day") { put("type", "string"); put("description", "YYYY-MM-DD") }
                        putJsonObject("title") { put("type", "string") }
                        putJsonObject("minutes") { put("type", "integer") }
                        putJsonObject("ingredients") {
                            put("type", "array")
                            putJsonObject("items") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    putJsonObject("item") { put("type", "string") }
                                    putJsonObject("quantity") { put("type", "string") }
                                    putJsonObject("aisle") {
                                        put("type", "string")
                                        putJsonArray("enum") { AISLE_WORDS.keys.forEach { add(it) } }
                                    }
                                }
                                putJsonArray("required") { add("item"); add("quantity"); add("aisle") }
                            }
                        }
                    }
                    putJsonArray("required") { add("day"); add("title"); add("minutes"); add("ingredients") }
                }
            }
        }
        putJsonArray("required") { add("dinners") }
    }

    private val AISLE_WORDS = linkedMapOf(
        "produce" to Aisle.PRODUCE, "bakery" to Aisle.BAKERY, "dairy" to Aisle.DAIRY, "meat" to Aisle.MEAT,
        "pantry" to Aisle.PANTRY, "frozen" to Aisle.FROZEN, "drinks" to Aisle.DRINKS, "other" to Aisle.OTHER,
    )

    fun aisleOf(word: String?): Aisle? {
        val w = word?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return AISLE_WORDS[w] ?: Aisle.entries.firstOrNull { it.name.lowercase() == w || it.label.lowercase() == w }
    }

    /** Reads the model's answer onto [dates]: each evening at most once, the model's own day when it is one of them. */
    fun parse(raw: String, dates: List<LocalDate>): List<CoachDinner> {
        val items = runCatching { json.parseToJsonElement(raw).jsonObject["dinners"]?.jsonArray }.getOrNull() ?: return emptyList()
        val free = dates.toMutableList()
        val parsed = items.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val title = o.text("title")?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val day = o.text("day")?.let { runCatching { LocalDate.parse(it.trim().take(10)) }.getOrNull() }
            val minutes = (o["minutes"] as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.filter(Char::isDigit)?.toIntOrNull() }?.takeIf { it in 1..600 }
            val ingredients = (o["ingredients"] as? JsonArray).orEmpty().mapNotNull { i ->
                when (i) {
                    is JsonObject -> i.text("item")?.trim()?.takeIf { it.isNotEmpty() }?.let { CoachIngredient(it, i.text("quantity"), aisleOf(i.text("aisle"))) }
                    is JsonPrimitive -> i.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { CoachIngredient(it) }
                    else -> null
                }
            }.distinctBy { it.item.lowercase() }
            day to CoachDinner(dates.firstOrNull() ?: return@mapNotNull null, title, minutes, ingredients)
        }
        // First the dinners whose day is one asked for, then the rest into what is left, in order.
        val placed = mutableListOf<CoachDinner>()
        val rest = mutableListOf<CoachDinner>()
        parsed.forEach { (day, dinner) ->
            if (day != null && day in free) { free.remove(day); placed += dinner.copy(date = day) } else rest += dinner
        }
        rest.forEach { dinner -> free.removeFirstOrNull()?.let { placed += dinner.copy(date = it) } }
        return placed.distinctBy { it.title.lowercase() }.sortedBy { it.date }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
