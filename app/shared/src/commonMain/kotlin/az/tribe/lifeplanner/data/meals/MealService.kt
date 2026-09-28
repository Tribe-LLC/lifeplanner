package az.tribe.lifeplanner.data.meals

import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.health.HealthDataManager
import az.tribe.lifeplanner.data.integrations.DataFlow
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.MealNotes
import az.tribe.lifeplanner.domain.service.MealPlanner
import az.tribe.lifeplanner.domain.service.Recipe
import az.tribe.lifeplanner.domain.service.RecipeParser
import az.tribe.lifeplanner.usecases.habit.AwardHabitCompletionUseCase
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * What Meals does outside its own rows: water (a log, Health, and the water habit), meals to
 * Health when their calories are known, and reading a recipe page someone pastes. Quick add and
 * the Meals page both go through here, so a glass of water counts the same wherever it is added.
 */
@OptIn(ExperimentalUuidApi::class)
class MealService(
    private val logs: LifeLogRepository,
    private val health: HealthDataManager,
    private val prefs: IntegrationPrefs,
    private val habits: HabitRepository,
    private val awardHabitCompletion: AwardHabitCompletionUseCase,
    private val settings: Settings,
    private val client: HttpClient,
) {
    private val tz = TimeZone.currentSystemDefault()

    var waterTarget: Int
        get() = settings.getInt(KEY_WATER_TARGET, 8)
        set(v) = settings.putInt(KEY_WATER_TARGET, v.coerceIn(1, 20))

    private fun healthOn(flow: DataFlow) = prefs.state.value.let { it.health && it.isOn(flow) }

    suspend fun canWriteHealth(): Boolean = runCatching { health.canWriteFoodAndWater() }.getOrDefault(false)

    /** Asks for the grant where that happens in code (iOS). Android launches its screen from the UI. */
    suspend fun requestHealth(): Boolean = runCatching { health.requestFoodAndWater() }.getOrDefault(false)

    // ── Water ────────────────────────────────────────────────────────────────

    /** Adds [glasses] of water now: a log row, the same to Health, and a tick on the water habit. */
    suspend fun addWater(glasses: Int = 1, fromQuickAdd: Boolean = false) {
        if (glasses <= 0) return
        val now = Clock.System.now()
        val id = Uuid.random().toString()
        if (!fromQuickAdd) {
            logs.save(
                LifeLog(
                    id = id, area = PlanArea.HABITS, kind = LogKind.WATER, title = if (glasses == 1) "Glass of water" else "$glasses glasses of water",
                    quantity = glasses.toDouble(), unit = "glass", occurredAt = now.toLocalDateTime(tz),
                ),
            )
        }
        if (healthOn(DataFlow.WATER)) runCatching { health.writeWater(glasses * GLASS_ML, now.toEpochMilliseconds(), "water:$id") }
        tickWaterHabit(glasses)
    }

    /** Takes back the last glass added here today. What already went to Health stays there. */
    suspend fun removeLastWater() {
        val today = Clock.System.todayIn(tz)
        logs.getInRange(today, today).filter { it.kind == LogKind.WATER }.maxByOrNull { it.occurredAt }?.let { l ->
            val q = (l.quantity ?: 1.0).toInt()
            if (q > 1) logs.save(l.copy(quantity = q - 1.0, title = if (q - 1 == 1) "Glass of water" else "${q - 1} glasses of water"))
            else logs.delete(l.id)
        }
    }

    private suspend fun tickWaterHabit(n: Int) {
        runCatching {
            val today = Clock.System.todayIn(tz)
            val habit = habits.getAllHabits().firstOrNull { it.isActive && "water" in it.title.lowercase() } ?: return
            if (habits.getCheckInByHabitAndDate(habit.id, today)?.completed == true) return
            val checkIn = if (habit.targetCount > 1) habits.addCount(habit.id, today, n) else habits.checkIn(habit.id, today)
            if (checkIn.completed) awardHabitCompletion(habit.id, today)
        }.onFailure { Logger.w("MealService") { "Water habit tick failed: ${it.message}" } }
    }

    // ── Meals to Health ──────────────────────────────────────────────────────

    /** Sends an eaten meal to Health when the user allows it and its calories or protein are known. */
    suspend fun sendToHealth(meal: LifeLog): Boolean {
        if (!healthOn(DataFlow.MEALS)) return false
        val kcal = meal.quantity?.takeIf { meal.unit == UNIT_KCAL && it > 0 }
        val protein = MealNotes.decode(meal.notes).proteinG
        if (kcal == null && protein == null) return false
        return runCatching {
            health.writeMeal(
                name = MealPlanner.dishName(meal), slot = MealPlanner.slotOf(meal).key,
                atEpochMs = meal.occurredAt.toInstant(tz).toEpochMilliseconds(),
                kcal = kcal, proteinG = protein, clientId = "meal:${meal.id}",
            )
        }.getOrDefault(false)
    }

    // ── Recipes ──────────────────────────────────────────────────────────────

    /** Reads a recipe from a link. Null when the page cannot be loaded or has nothing to use. */
    suspend fun importRecipe(url: String): Recipe? {
        val link = url.trim().let { if (it.startsWith("http")) it else "https://$it" }
        return try {
            val response = client.get(link) {
                // Some recipe sites refuse requests that do not look like a browser.
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 14) LifePlanner/4 (recipe import)")
                header(HttpHeaders.Accept, "text/html")
            }
            if (!response.status.isSuccess()) return null
            RecipeParser.parse(response.bodyAsText(), link).also {
                PostHogAnalytics.capture("v4_recipe_import", mapOf("ok" to (it != null), "ingredients" to (it?.ingredients?.size ?: 0)))
            }
        } catch (e: Exception) {
            Logger.w("MealService") { "Recipe import failed: ${e.message}" }
            null
        }
    }

    companion object {
        const val UNIT_KCAL = "kcal"
        const val GLASS_ML = 250.0
        private const val KEY_WATER_TARGET = "v4_water_target"
    }
}
