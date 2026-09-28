package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogStatus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** A dish in "Your rotation", with how often it was eaten or planned. */
data class RotationDish(val name: String, val times: Int)

/**
 * The week-level parts of Meals: repeating last week, the rotation of dishes people make again,
 * and deciding what really needs buying (not on the list yet, not a thing you already have).
 */
object MealWeek {

    /** One meal from last week to plan again [date] in [slot]. */
    data class Copy(val source: LifeLog, val date: LocalDate, val slot: MealSlot)

    /**
     * Last week's meals (the seven days before [today]) on the same weekdays this week, skipping
     * meals eaten out and any slot that already has something in it.
     */
    fun repeatLastWeek(logs: List<LifeLog>, today: LocalDate): List<Copy> {
        val from = today.minus(DatePeriod(days = 7))
        val to = today.minus(DatePeriod(days = 1))
        val ahead = today.plus(DatePeriod(days = 6))
        val out = MealPlanner.eatenOut(logs)
        val taken = logs.filter { MealPlanner.isMeal(it) && it.status != LogStatus.SKIPPED && it.date in today..ahead }
            .map { it.date to MealPlanner.slotOf(it) }.toSet()
        return logs.filter { MealPlanner.isMeal(it) && it.status != LogStatus.SKIPPED && it.date in from..to && it.id !in out }
            .sortedBy { it.occurredAt }
            .map { Copy(it, it.date.plus(DatePeriod(days = 7)), MealPlanner.slotOf(it)) }
            .filter { (it.date to it.slot) !in taken }
    }

    /**
     * The dishes you make most, then the ones eaten lately, as one row. Leftovers are the dish they
     * came from, so they are not offered on their own.
     */
    fun rotation(logs: List<LifeLog>, limit: Int = 10): List<RotationDish> {
        val meals = logs.filter { MealPlanner.isMeal(it) && it.status != LogStatus.SKIPPED && MealNotes.decode(it.notes).leftoverOf == null }
            .filterNot { MealPlanner.dishName(it).startsWith("Leftover", ignoreCase = true) }
        val groups = meals.groupBy { MealPlanner.dishName(it).trim().lowercase() }.filterKeys { it.isNotBlank() }
        val often = groups.filterValues { rows -> rows.count { it.status == LogStatus.DONE } >= 2 }
            .map { (_, rows) -> rows.maxBy { it.occurredAt } to rows.size }
            .sortedWith(compareByDescending<Pair<LifeLog, Int>> { it.second }.thenByDescending { it.first.occurredAt })
        val lately = groups.filterKeys { k -> often.none { MealPlanner.dishName(it.first).trim().lowercase() == k } }
            .map { (_, rows) -> rows.maxBy { it.occurredAt } to rows.size }
            .sortedByDescending { it.first.occurredAt }
        return (often + lately).map { RotationDish(MealPlanner.dishName(it.first).trim().replaceFirstChar(Char::titlecase), it.second) }.take(limit)
    }

    private val units = setOf(
        "g", "gr", "gram", "grams", "kg", "ml", "l", "litre", "litres", "liter", "liters", "oz", "lb", "lbs", "pound", "pounds",
        "tbsp", "tablespoon", "tablespoons", "tsp", "teaspoon", "teaspoons", "cup", "cups", "can", "cans", "tin", "tins",
        "clove", "cloves", "pinch", "handful", "bunch", "bunches", "pack", "packs", "packet", "packets", "jar", "jars",
        "bag", "bags", "slice", "slices", "piece", "pieces", "large", "small", "medium", "big", "of", "a", "an", "x", "few", "some", "fresh",
    )

    /**
     * What an ingredient is, without how much: "2 cloves garlic, minced" is "garlic", "500 g beef
     * mince" is "beef mince", "Eggs" and "6 eggs" are both "egg". Used to match "have it" and staples.
     */
    fun ingredientKey(ingredient: String): String {
        val words = MealPlanner.shoppingItem(ingredient).lowercase()
            .replace(Regex("""[\d½¼¾⅓⅔.,/()\-]+"""), " ")
            .split(Regex("""\s+""")).filter { it.isNotBlank() }
            .dropWhile { it in units }
        if (words.isEmpty()) return ingredient.trim().lowercase()
        return words.joinToString(" ").let(::singular)
    }

    private fun singular(s: String): String = when {
        s.endsWith("oes") -> s.dropLast(2)
        s.endsWith("ies") && s.length > 4 -> s.dropLast(3) + "y"
        s.endsWith("ss") -> s
        s.endsWith("s") && s.length > 3 -> s.dropLast(1)
        else -> s
    }

    /**
     * The items that should land on the list: one per line, minus what is already there to buy
     * (in any amount) and what you said you have.
     */
    fun toBuy(items: List<String>, onList: List<String>, have: Set<String>): List<String> {
        val listed = onList.map(::ingredientKey).toSet()
        return items.map(MealPlanner::shoppingItem).map { it.trim().trim('-', '*', '•').trim() }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirstChar(Char::titlecase) }
            .distinctBy(::ingredientKey)
            .filter { ingredientKey(it) !in listed && ingredientKey(it) !in have }
    }

    /** A short list kept as lines in one budget row: staples, and what you have in. */
    fun encodeList(items: Collection<String>): String = items.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString("\n")

    fun decodeList(text: String?): List<String> = text.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }

    val DEFAULT_STAPLES = listOf("Milk", "Eggs", "Bread")
}
