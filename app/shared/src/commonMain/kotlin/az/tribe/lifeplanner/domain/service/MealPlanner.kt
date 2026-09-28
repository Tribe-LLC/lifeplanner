package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** The four places a meal goes in a day. Stored in [LifeLog.category] under [key]. */
enum class MealSlot(val key: String, val label: String, val usualTime: LocalTime) {
    BREAKFAST("breakfast", "Breakfast", LocalTime(8, 0)),
    LUNCH("lunch", "Lunch", LocalTime(13, 0)),
    DINNER("dinner", "Dinner", LocalTime(19, 0)),
    SNACK("snack", "Snacks", LocalTime(16, 0));

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/**
 * What a planned meal carries besides its name, kept in [LifeLog.notes] as plain lines so it
 * syncs with the row: the recipe link, protein per serving, and the ingredients, which is what
 * lets a dish planned again refill the shopping list on its own.
 */
data class MealNotes(val url: String? = null, val proteinG: Double? = null, val ingredients: List<String> = emptyList(), val leftoverOf: String? = null) {
    fun encode(): String? = buildList {
        url?.let { add("url: $it") }
        proteinG?.let { add("protein: ${if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()}") }
        leftoverOf?.let { add("leftover of: $it") }
        ingredients.forEach { add("- $it") }
    }.joinToString("\n").ifEmpty { null }

    companion object {
        fun decode(notes: String?): MealNotes {
            if (notes.isNullOrBlank()) return MealNotes()
            val lines = notes.lines().map { it.trim() }.filter { it.isNotEmpty() }
            fun value(key: String) = lines.firstOrNull { it.startsWith("$key:") }?.substringAfter(':')?.trim()
            return MealNotes(
                url = value("url"),
                proteinG = value("protein")?.toDoubleOrNull(),
                ingredients = lines.filter { it.startsWith("- ") }.map { it.removePrefix("- ").trim() },
                leftoverOf = value("leftover of"),
            )
        }
    }
}

/** Where a shopping item is in the shop, so the list reads in the order you walk it. */
enum class Aisle(val label: String) {
    PRODUCE("Fruit and veg"), BAKERY("Bread"), DAIRY("Dairy and eggs"), MEAT("Meat and fish"),
    PANTRY("Cupboard"), FROZEN("Frozen"), DRINKS("Drinks"), HOUSEHOLD("Home and care"), OTHER("Other")
}

/**
 * The pure parts of the Meals page: which slot a meal is in, what was eaten out, what the week
 * looks like and what is on the shopping list. Meals are [LogKind.MEAL] rows in [PlanArea.MEALS];
 * a meal eaten out is the meal plus a food spend in Money sharing its [LifeLog.externalId].
 * Shopping list items are [LogKind.NOTE] rows in Meals with [CATEGORY_SHOPPING], planned until
 * bought, so they sync like everything else.
 */
object MealPlanner {

    const val CATEGORY_SHOPPING = "shopping"

    fun isMeal(l: LifeLog) = l.kind == LogKind.MEAL && l.area == PlanArea.MEALS

    fun isShoppingItem(l: LifeLog) = l.area == PlanArea.MEALS && l.kind == LogKind.NOTE && l.category == CATEGORY_SHOPPING

    /** The slot a meal is in: what it was filed under, else a word in its title, else the time eaten. */
    fun slotOf(l: LifeLog): MealSlot {
        MealSlot.fromKey(l.category)?.let { return it }
        val words = l.title.lowercase().split(' ', ',', '.').filter { it.isNotBlank() }.toSet()
        when {
            "breakfast" in words || "brunch" in words -> return MealSlot.BREAKFAST
            "lunch" in words -> return MealSlot.LUNCH
            "dinner" in words || "supper" in words -> return MealSlot.DINNER
            "snack" in words -> return MealSlot.SNACK
        }
        val h = l.occurredAt.hour
        return when {
            h in 5..10 -> MealSlot.BREAKFAST
            h in 11..15 -> MealSlot.LUNCH
            h in 18..22 -> MealSlot.DINNER
            else -> MealSlot.SNACK
        }
    }

    /** "Ramen, lunch" from quick add reads as "Ramen" once it sits under Lunch. */
    fun dishName(l: LifeLog): String {
        val slot = slotOf(l)
        val cleaned = l.title.split(',').map { it.trim() }
            .filter { part -> part.lowercase() != slot.key && part.lowercase() != slot.label.lowercase() && part.lowercase() != "supper" && part.lowercase() != "brunch" }
            .joinToString(", ")
        return cleaned.ifBlank { l.title }
    }

    /** Meals on [date], by slot, oldest first within a slot. Skipped ones are left out. */
    fun day(logs: List<LifeLog>, date: LocalDate): Map<MealSlot, List<LifeLog>> =
        logs.filter { isMeal(it) && it.date == date && it.status != LogStatus.SKIPPED }
            .sortedBy { it.occurredAt }
            .groupBy { slotOf(it) }

    /** The ids of meals that came with a spend, which is what "eaten out" means here. */
    fun eatenOut(logs: List<LifeLog>): Set<String> {
        val spendGroups = logs.filter { MoneySummary.isSpend(it) && it.externalId != null }.map { it.externalId }.toSet()
        return logs.filter { isMeal(it) && it.externalId != null && it.externalId in spendGroups }.map { it.id }.toSet()
    }

    data class Week(
        val eaten: Int,
        val out: Int,
        val home: Int,
        val foodSpent: Double,
        /** Meals eaten per day, oldest first, ending today. */
        val perDay: List<Pair<LocalDate, Int>>,
        val plannedDinners: Int,
    )

    /** The last seven days ending [today]. Food spend counts every food spend, groceries too. */
    fun week(logs: List<LifeLog>, today: LocalDate): Week {
        val from = today.minus(DatePeriod(days = 6))
        val inWeek = logs.filter { it.date in from..today }
        val eaten = inWeek.filter { isMeal(it) && it.status == LogStatus.DONE }
        val out = eatenOut(inWeek).let { ids -> eaten.count { it.id in ids } }
        val food = inWeek.filter { MoneySummary.isSpend(it) && it.category == "food" }.sumOf { it.amount ?: 0.0 }
        val perDay = (6 downTo 0).map { back -> today.minus(DatePeriod(days = back)) }.map { d -> d to eaten.count { it.date == d } }
        val planned = logs.count { isMeal(it) && it.status == LogStatus.PLANNED && slotOf(it) == MealSlot.DINNER && it.date >= today }
        return Week(eaten.size, out, eaten.size - out, food, perDay, planned)
    }

    /**
     * Dishes to offer again, most eaten first, then most recent. Keeps logging to a tap for the
     * handful of meals most people repeat.
     */
    fun favourites(logs: List<LifeLog>, slot: MealSlot? = null, limit: Int = 6): List<String> =
        logs.filter { isMeal(it) && it.status == LogStatus.DONE && (slot == null || slotOf(it) == slot) }
            .groupBy { dishName(it).trim().lowercase() }
            .map { (_, rows) -> rows.maxBy { it.occurredAt } to rows.size }
            .sortedWith(compareByDescending<Pair<LifeLog, Int>> { it.second }.thenByDescending { it.first.occurredAt })
            .map { dishName(it.first) }
            .filter { it.isNotBlank() }
            .take(limit)

    /** Shopping items still to buy first, then bought ones, each in the order they were added. */
    fun shopping(logs: List<LifeLog>): List<LifeLog> =
        logs.filter { isShoppingItem(it) }.sortedWith(compareBy({ it.status == LogStatus.DONE }, { it.occurredAt }))

    // Checked in this order: the first group with a matching word wins. Container and spice words
    // come early, so "cans chili beans" is the cupboard, not the chilli in the vegetable aisle.
    private val aisleWords: List<Pair<Aisle, Set<String>>> = listOf(
        Aisle.HOUSEHOLD to setOf("soap", "detergent", "toilet", "paper towel", "paper towels", "tissue", "tissues", "shampoo", "toothpaste", "bin bag", "bin bags", "foil", "cling film", "sponge", "sponges", "nappies", "diapers"),
        Aisle.FROZEN to setOf("frozen", "ice cream"),
        Aisle.PANTRY to setOf(
            "can", "cans", "canned", "tin", "tins", "tinned", "jar", "jarred", "dried", "powder", "paste", "sauce", "bouillon", "stock",
            "broth", "seasoning", "spice", "spices", "extract", "flakes", "bits", "teaspoon", "teaspoons", "tsp",
        ),
        Aisle.DAIRY to setOf("milk", "cheese", "cheddar", "yogurt", "yoghurt", "butter", "cream", "egg", "eggs", "feta", "mozzarella", "parmesan", "kefir"),
        Aisle.MEAT to setOf("chicken", "beef", "pork", "lamb", "mince", "bacon", "sausage", "sausages", "ham", "turkey", "fish", "salmon", "tuna", "shrimp", "prawns", "cod", "chuck", "steak"),
        Aisle.BAKERY to setOf("bread", "baguette", "bagel", "bagels", "bun", "buns", "tortilla", "tortillas", "wrap", "wraps", "pita", "croissant", "croissants"),
        Aisle.PRODUCE to setOf(
            "apple", "apples", "banana", "bananas", "lemon", "lemons", "lime", "limes", "orange", "oranges", "berries", "grapes",
            "onion", "onions", "garlic", "ginger", "tomato", "tomatoes", "potato", "potatoes", "carrot", "carrots", "pepper", "peppers",
            "cucumber", "lettuce", "spinach", "kale", "broccoli", "mushroom", "mushrooms", "avocado", "avocados", "zucchini", "courgette",
            "herbs", "parsley", "coriander", "cilantro", "basil", "mint", "salad", "cabbage", "celery", "leek", "leeks", "spring onion",
            "chilli", "chili", "chile", "chiles", "scallion", "scallions", "shallot", "shallots",
        ),
        Aisle.DRINKS to setOf("water", "juice", "coffee", "tea", "soda", "cola", "beer", "wine", "sparkling"),
        Aisle.PANTRY to setOf(
            "rice", "pasta", "noodles", "flour", "sugar", "salt", "oil", "olive oil", "vinegar", "beans", "lentils", "chickpeas", "oats",
            "cereal", "honey", "jam", "cumin", "paprika", "cayenne", "oregano", "cinnamon", "nuts", "peanut butter", "crackers", "chocolate",
            "baking powder", "yeast", "quinoa", "couscous", "worcestershire",
        ),
    )

    /** A best guess from the words in an item; anything unknown goes under Other. */
    fun aisleOf(item: String): Aisle {
        // "tomatoes with juice" is tomatoes; what follows "with" or sits in brackets is detail.
        val core = item.lowercase().replace(Regex("""\([^)]*\)"""), " ").substringBefore(" with ")
        val lower = " " + core.replace(Regex("""[^a-z\s]"""), " ").replace(Regex("""\s+"""), " ").trim() + " "
        return aisleWords.firstOrNull { (_, words) -> words.any { " $it " in lower } }?.first ?: Aisle.OTHER
    }

    /**
     * A recipe's ingredient line as a shopping item: the thing to buy, without the prep after a
     * comma ("1 onion, chopped") or the asides in brackets that are not a size.
     */
    fun shoppingItem(ingredient: String): String {
        val base = ingredient.substringBefore(",").trim()
        return base.replace(Regex("""\s*\((such as|like|or|optional|about|see)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ").trim().trimEnd('.', ';', ':')
    }

    /**
     * The aisle a shopping row goes under: a word in its name, else the aisle the coach gave it
     * (kept in [LifeLog.unit]), else Other.
     */
    fun aisleOf(item: LifeLog): Aisle =
        aisleOf(item.title).takeIf { it != Aisle.OTHER } ?: Aisle.entries.firstOrNull { it.name == item.unit } ?: Aisle.OTHER

    /** Items still to buy, grouped by aisle in walking order. Bought ones are left out. */
    fun byAisle(items: List<LifeLog>): List<Pair<Aisle, List<LifeLog>>> =
        items.filter { isShoppingItem(it) && it.status != LogStatus.DONE }
            .groupBy { aisleOf(it) }
            .toList()
            .sortedBy { it.first.ordinal }

    /**
     * Where the extra portions of a cooked meal go: the next lunch and dinner slots after it that
     * have nothing planned yet, in order. Breakfast is left alone, nobody wants curry at 8.
     */
    fun leftoverSlots(date: LocalDate, slot: MealSlot, extra: Int, taken: Set<Pair<LocalDate, MealSlot>>): List<Pair<LocalDate, MealSlot>> {
        if (extra <= 0) return emptyList()
        val out = mutableListOf<Pair<LocalDate, MealSlot>>()
        var d = date
        var s = slot
        var guard = 0
        while (out.size < extra && guard++ < 30) {
            // Lunch leads to dinner the same day, dinner (or anything else) to lunch the next day.
            if (s == MealSlot.LUNCH) s = MealSlot.DINNER else { d = d.plus(DatePeriod(days = 1)); s = MealSlot.LUNCH }
            if (d to s !in taken) out += d to s
        }
        return out
    }

    /**
     * Splits "eggs, milk and 2 onions" into three items, so a whole list can be typed or pasted in
     * one go. Keeps anything already on the list (still to buy) from being added twice.
     */
    fun parseShopping(text: String, existing: List<LifeLog> = emptyList(), perLine: Boolean = false): List<String> {
        val have = existing.filter { it.status != LogStatus.DONE }.map { it.title.trim().lowercase() }.toSet()
        // Typed lists split on commas and "and"; recipe ingredients are one per line and keep them.
        val parts = if (perLine) text.lines().map(::shoppingItem)
        else text.split(',', '\n', ';').flatMap { it.split(Regex("""\s+and\s+""", RegexOption.IGNORE_CASE)) }
        return parts
            .map { it.trim().trim('-', '*', '•').trim() }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirstChar(Char::titlecase) }
            .distinctBy { it.lowercase() }
            .filter { it.lowercase() !in have }
    }
}
