package az.tribe.lifeplanner.infrastructure

import az.tribe.lifeplanner.data.sync.dto.BudgetSyncDto
import az.tribe.lifeplanner.data.sync.dto.LifeLogSyncDto
import az.tribe.lifeplanner.data.sync.dto.TripItemSyncDto
import az.tribe.lifeplanner.data.sync.dto.TripSyncDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every field a v4 DTO sends must be a column in supabase/schema.sql, or PostgREST rejects the
 * whole upsert and the table never syncs. Nothing else catches a renamed field.
 */
class V4SyncSchemaTest {

    private val json = Json { encodeDefaults = true }
    private val schema = File("../../supabase/schema.sql").readText()

    private fun columnsOf(table: String): Set<String> {
        val body = Regex("CREATE TABLE IF NOT EXISTS $table \\((.*?)\\n\\);", RegexOption.DOT_MATCHES_ALL)
            .find(schema)?.groupValues?.get(1) ?: error("$table missing from schema.sql")
        return body.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("--") && !it.startsWith("PRIMARY KEY") }
            .map { it.substringBefore(' ') }
            .toSet()
    }

    private fun assertFits(table: String, dto: JsonObject) {
        val cols = columnsOf(table)
        val missing = dto.keys - cols
        assertTrue(missing.isEmpty(), "$table is missing columns $missing")
        assertTrue("$table" in schema.substringAfter("cleanup_tombstones()").substringBefore("LOOP"), "$table not in cleanup_tombstones")
    }

    @Test
    fun lifeLogsFit() = assertFits(
        "life_logs",
        json.encodeToJsonElement(
            LifeLogSyncDto("a", "u", "money", "expense", "done", "Coffee", 4.5, "EUR", "Food", null, null, null, "t", "2026-09-28", "manual", null, null, null, "t"),
        ).jsonObject,
    )

    @Test
    fun budgetsFit() = assertFits(
        "budgets",
        json.encodeToJsonElement(BudgetSyncDto("a", "u", "money", "spend", "Food", 100.0, "EUR", "week", null, "t")).jsonObject,
    )

    @Test
    fun tripsFit() = assertFits(
        "trips",
        json.encodeToJsonElement(TripSyncDto("a", "u", "Lisbon", null, null, "2026-10-01", "2026-10-05", 800.0, "EUR", 1, null, "t")).jsonObject,
    )

    @Test
    fun tripItemsFit() = assertFits(
        "trip_items",
        json.encodeToJsonElement(TripItemSyncDto("a", "u", "trip", "pack", "Passport", null, null, false, 0, "t")).jsonObject,
    )
}
