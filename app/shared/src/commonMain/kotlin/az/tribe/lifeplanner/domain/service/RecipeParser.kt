package az.tribe.lifeplanner.domain.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** What a recipe page gives us to plan with. Everything but the name is optional. */
data class Recipe(
    val name: String,
    val ingredients: List<String> = emptyList(),
    val servings: Int? = null,
    val minutes: Int? = null,
    val kcalPerServing: Double? = null,
    val proteinPerServing: Double? = null,
    val url: String? = null,
)

/**
 * Reads a recipe out of a web page. Most recipe sites embed schema.org Recipe data as JSON-LD for
 * search engines; that is what this reads, on the device, with no recipe API. Falls back to the
 * page title so a link without that data still becomes a named dish.
 */
object RecipeParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val ldBlock = Regex("""<script[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ogTitle = Regex("""<meta[^>]+property\s*=\s*["']og:title["'][^>]+content\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val titleTag = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun parse(html: String, url: String? = null): Recipe? {
        ldBlock.findAll(html).forEach { m ->
            val element = runCatching { json.parseToJsonElement(m.groupValues[1].trim()) }.getOrNull() ?: return@forEach
            findRecipe(element)?.let { return fromJson(it, url) }
        }
        val title = ogTitle.find(html)?.groupValues?.get(1) ?: titleTag.find(html)?.groupValues?.get(1)
        return title?.let { decode(it).substringBefore(" | ").substringBefore(" - ").trim() }?.takeIf { it.isNotEmpty() }?.let { Recipe(it, url = url) }
    }

    /** The first object typed Recipe, wherever it sits: top level, in a list, or in an @graph. */
    private fun findRecipe(e: JsonElement): JsonObject? = when (e) {
        is JsonArray -> e.firstNotNullOfOrNull { findRecipe(it) }
        is JsonObject -> when {
            isRecipe(e) -> e
            else -> (e["@graph"] ?: e["mainEntity"])?.let { findRecipe(it) }
        }
        else -> null
    }

    private fun isRecipe(o: JsonObject): Boolean = when (val t = o["@type"]) {
        is JsonPrimitive -> t.contentOrNull.equals("Recipe", ignoreCase = true)
        is JsonArray -> t.any { (it as? JsonPrimitive)?.contentOrNull.equals("Recipe", ignoreCase = true) }
        else -> false
    }

    private fun fromJson(o: JsonObject, url: String?): Recipe? {
        val name = o.text("name")?.let(::decode)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val ingredients = (o["recipeIngredient"] ?: o["ingredients"]).strings().map { decode(it).trim() }.filter { it.isNotEmpty() }
        val minutes = o.text("totalTime")?.let(::isoMinutes)
            ?: listOfNotNull(o.text("prepTime")?.let(::isoMinutes), o.text("cookTime")?.let(::isoMinutes)).takeIf { it.isNotEmpty() }?.sum()
        val nutrition = o["nutrition"] as? JsonObject
        return Recipe(
            name = name,
            ingredients = ingredients,
            servings = o["recipeYield"].strings().firstNotNullOfOrNull { firstNumber(it)?.toInt() }?.takeIf { it in 1..50 },
            minutes = minutes?.takeIf { it > 0 },
            kcalPerServing = nutrition?.text("calories")?.let(::firstNumber),
            proteinPerServing = nutrition?.text("proteinContent")?.let(::firstNumber),
            url = url,
        )
    }

    private fun JsonObject.text(key: String): String? = when (val v = this[key]) {
        is JsonPrimitive -> v.contentOrNull
        is JsonArray -> (v.firstOrNull() as? JsonPrimitive)?.contentOrNull
        else -> null
    }

    private fun JsonElement?.strings(): List<String> = when (this) {
        null, JsonNull -> emptyList()
        is JsonPrimitive -> listOfNotNull(contentOrNull)
        is JsonArray -> flatMap { it.strings() }
        is JsonObject -> listOfNotNull((this["text"] as? JsonPrimitive)?.contentOrNull ?: (this["name"] as? JsonPrimitive)?.contentOrNull)
    }

    /** "PT1H30M" is 90, "PT45M" is 45, "P0DT0H20M" is 20. */
    fun isoMinutes(s: String): Int? {
        val m = Regex("""P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?""", RegexOption.IGNORE_CASE).find(s.trim()) ?: return null
        val (d, h, min) = m.destructured
        if (d.isEmpty() && h.isEmpty() && min.isEmpty()) return null
        return (d.toIntOrNull() ?: 0) * 1440 + (h.toIntOrNull() ?: 0) * 60 + (min.toIntOrNull() ?: 0)
    }

    private fun firstNumber(s: String): Double? = Regex("""\d+(?:[.,]\d+)?""").find(s)?.value?.replace(',', '.')?.toDoubleOrNull()

    private fun decode(s: String): String = s
        .replace(Regex("""<[^>]+>"""), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&#039;", "'")
        .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
        .replace(Regex("""&#(\d+);""")) { m -> m.groupValues[1].toIntOrNull()?.let { Char(it).toString() } ?: m.value }
        .replace(Regex("""\s+"""), " ")
}
