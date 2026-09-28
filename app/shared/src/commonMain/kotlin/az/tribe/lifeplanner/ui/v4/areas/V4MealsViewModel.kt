package az.tribe.lifeplanner.ui.v4.areas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.core.CurrencyPrefs
import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.integrations.DataFlow
import az.tribe.lifeplanner.data.integrations.IntegrationPrefs
import az.tribe.lifeplanner.data.meals.MealService
import az.tribe.lifeplanner.data.plans.PlanService
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.domain.service.Aisle
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.MealNotes
import az.tribe.lifeplanner.domain.service.MealPlanner
import az.tribe.lifeplanner.domain.service.MealSlot
import az.tribe.lifeplanner.domain.service.MoneySummary
import az.tribe.lifeplanner.domain.service.Recipe
import az.tribe.lifeplanner.domain.service.CoachContext
import az.tribe.lifeplanner.domain.service.CoachDinner
import az.tribe.lifeplanner.domain.service.MealWeek
import az.tribe.lifeplanner.domain.service.RotationDish
import az.tribe.lifeplanner.data.meals.MealCoachService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** One meal on the page: eaten or planned, with what to show under its name. */
data class MealRow(val log: LifeLog, val dish: String, val planned: Boolean, val meta: String?)

/** One day of "This week's plan". */
data class PlanDay(val date: LocalDate, val label: String, val meals: Map<MealSlot, List<MealRow>>)

data class MealsState(
    val today: Map<MealSlot, List<MealRow>> = emptyMap(),
    val water: Int = 0,
    val waterTarget: Int = 8,
    val week: MealPlanner.Week? = null,
    val plan: List<PlanDay> = emptyList(),
    val toBuy: List<Pair<Aisle, List<LifeLog>>> = emptyList(),
    val bought: List<LifeLog> = emptyList(),
    /** "€88 left for food this week", from a food budget or the main one. Null without either. */
    val foodBudget: String? = null,
    val hasFoodBudget: Boolean = false,
    val favourites: List<String> = emptyList(),
    val recent: List<MealRow> = emptyList(),
    val currency: String = "EUR",
    val moneyOn: Boolean = false,
    val canCalendar: Boolean = false,
    /** Health is connected and Meals and Water are switched on, but the write grant is missing. */
    val needsHealthGrant: Boolean = false,
    val healthOn: Boolean = false,
    val loaded: Boolean = false,
    /** Favourites and what was eaten lately, as one row of dishes to plan again. */
    val rotation: List<RotationDish> = emptyList(),
    /** Meals from last week that "Repeat last week" would plan. */
    val repeatable: Int = 0,
    val staples: List<String> = MealWeek.DEFAULT_STAPLES,
    /** Staples not on the list yet. */
    val staplesMissing: Int = 0,
    /** What you have in, as ingredient keys: kept off the list. */
    val pantry: Set<String> = emptySet(),
    /** Evenings in the next week with no dinner yet, for "Plan my week". */
    val freeEvenings: List<LocalDate> = emptyList(),
    /** Every meal in the window, for remembering what a dish needed. */
    val history: List<LifeLog> = emptyList(),
)

/** The coach's week while it is being reviewed. Nothing is saved until "Put it in my week". */
data class CoachWeekUi(
    val dates: List<LocalDate> = emptyList(),
    val household: Int = 1,
    val asked: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val dinners: List<CoachDinner> = emptyList(),
    val swapping: LocalDate? = null,
    val saving: Boolean = false,
)

/** What the plan sheet opens with: empty, a favourite, or an imported recipe. */
data class MealDraft(
    val dish: String = "",
    val date: LocalDate,
    val slot: MealSlot = MealSlot.DINNER,
    val ingredients: List<String> = emptyList(),
    val kcal: Double? = null,
    val proteinG: Double? = null,
    val minutes: Int? = null,
    val servings: Int? = null,
    val url: String? = null,
)

@OptIn(ExperimentalUuidApi::class)
class V4MealsViewModel(
    private val logs: LifeLogRepository,
    private val budgets: BudgetRepository,
    private val meals: MealService,
    private val plans: PlanService,
    private val currencyPrefs: CurrencyPrefs,
    private val planAreas: PlanAreasRepository,
    private val prefs: IntegrationPrefs,
    private val coach: MealCoachService,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    private data class Extras(val canCalendar: Boolean = false, val canWriteHealth: Boolean = true, val waterTarget: Int = 8)

    private val extras = MutableStateFlow(Extras(waterTarget = meals.waterTarget))

    /** The recipe being read, and whether it could not be; the sheet shows both. */
    val importing = MutableStateFlow(false)
    val importFailed = MutableStateFlow(false)

    val state: StateFlow<MealsState> = combine(
        logs.observeInRange(today().minus(DatePeriod(days = 60)), today().plus(DatePeriod(days = 14))),
        budgets.observeAll(),
        planAreas.enabledAreas,
        prefs.state,
        extras,
    ) { all, bs, areas, p, ex ->
        val today = today()
        val out = MealPlanner.eatenOut(all)
        fun row(l: LifeLog): MealRow {
            val notes = MealNotes.decode(l.notes)
            val meta = listOfNotNull(
                if (l.status == LogStatus.PLANNED) (if (notes.leftoverOf != null) "Leftovers" else "Planned") else null,
                if (l.id in out) "Eaten out" else null,
                l.quantity?.takeIf { l.unit == MealService.UNIT_KCAL }?.let { "${it.toInt()} kcal" },
                if (l.status == LogStatus.PLANNED) l.durationMin?.let { "$it min to make" } else null,
            ).joinToString(", ").ifEmpty { null }
            return MealRow(l, MealPlanner.dishName(l), l.status == LogStatus.PLANNED, meta)
        }

        val food = bs.firstOrNull { it.area == PlanArea.MONEY && it.metric == Budget.METRIC_SPEND && it.category == "food" && it.tripId == null }
        val budget = food ?: MoneySummary.primary(bs)
        val foodText = budget?.let { b ->
            val st = MoneySummary.status(b, all, today)
            val what = if (b.category != null) " for ${b.category}" else ""
            if (st.left >= 0) "${MoneyFormat.format(st.left, b.currency)} left$what ${MoneySummary.periodWord(b.period)}"
            else "${MoneyFormat.format(-st.left, b.currency)} over$what ${MoneySummary.periodWord(b.period)}"
        }

        val shopping = MealPlanner.shopping(all)
        val toBuyTitles = shopping.filter { it.status != LogStatus.DONE }.map { it.title }
        val staples = bs.firstOrNull { it.area == PlanArea.MEALS && it.metric == METRIC_STAPLES }?.let { MealWeek.decodeList(it.category) } ?: MealWeek.DEFAULT_STAPLES
        val nowHour = Clock.System.now().toLocalDateTime(tz).hour
        MealsState(
            today = MealPlanner.day(all, today).mapValues { (_, v) -> v.map(::row) },
            water = all.filter { it.kind == LogKind.WATER && it.date == today }.sumOf { (it.quantity ?: 1.0).toInt() },
            waterTarget = ex.waterTarget,
            week = MealPlanner.week(all, today),
            plan = (0..6).map { i ->
                val d = today.plus(DatePeriod(days = i))
                PlanDay(d, if (i == 0) "Today" else if (i == 1) "Tomorrow" else FitnessWeek.dayName(d.dayOfWeek), MealPlanner.day(all, d).mapValues { (_, v) -> v.map(::row) })
            },
            toBuy = MealPlanner.byAisle(shopping),
            bought = shopping.filter { it.status == LogStatus.DONE },
            foodBudget = foodText,
            hasFoodBudget = food != null,
            favourites = MealPlanner.favourites(all),
            recent = all.filter { MealPlanner.isMeal(it) && it.status == LogStatus.DONE }.sortedByDescending { it.occurredAt }.take(8).map(::row),
            currency = currencyPrefs.code,
            moneyOn = PlanArea.MONEY in areas,
            canCalendar = ex.canCalendar,
            needsHealthGrant = p.health && (p.isOn(DataFlow.MEALS) || p.isOn(DataFlow.WATER)) && !ex.canWriteHealth,
            healthOn = p.health,
            loaded = true,
            rotation = MealWeek.rotation(all),
            repeatable = MealWeek.repeatLastWeek(all, today).size,
            staples = staples,
            staplesMissing = MealWeek.toBuy(staples, toBuyTitles, emptySet()).size,
            pantry = bs.firstOrNull { it.area == PlanArea.MEALS && it.metric == METRIC_PANTRY }?.let { MealWeek.decodeList(it.category).toSet() }.orEmpty(),
            freeEvenings = (0..6).map { today.plus(DatePeriod(days = it)) }
                .filter { d -> d != today || nowHour < 17 }
                .filter { d -> MealPlanner.day(all, d)[MealSlot.DINNER].isNullOrEmpty() },
            history = all.filter { MealPlanner.isMeal(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MealsState(currency = currencyPrefs.code))

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            extras.value = Extras(plans.canAddToCalendar(), meals.canWriteHealth(), meals.waterTarget)
        }
    }

    fun favouritesFor(slot: MealSlot): List<String> =
        MealPlanner.favourites(state.value.recent.map { it.log } + state.value.plan.flatMap { d -> d.meals.values.flatten().map { it.log } }, slot)
            .ifEmpty { state.value.favourites }

    // ── Eating ───────────────────────────────────────────────────────────────

    /**
     * Logs a meal eaten. With a [cost] it was bought (eaten out, or a takeaway), so a food spend goes
     * to Money under the same group; with [kcal] or protein it goes to Health too.
     */
    fun logMeal(dish: String, slot: MealSlot, date: LocalDate, cost: Double?, kcal: Double?) {
        val name = dish.trim().ifEmpty { return }
        viewModelScope.launch {
            val group = Uuid.random().toString()
            val now = Clock.System.now().toLocalDateTime(tz)
            val at = if (date == now.date) now else LocalDateTime(date, slot.usualTime)
            val meal = LifeLog(
                id = Uuid.random().toString(), area = PlanArea.MEALS, kind = LogKind.MEAL, title = name, category = slot.key,
                quantity = kcal?.takeIf { it > 0 }, unit = kcal?.takeIf { it > 0 }?.let { MealService.UNIT_KCAL },
                occurredAt = at, externalId = group,
            )
            val rows = listOfNotNull(
                meal,
                cost?.takeIf { it > 0 }?.let {
                    LifeLog(
                        id = Uuid.random().toString(), area = PlanArea.MONEY, kind = LogKind.EXPENSE, title = "$name, ${slot.label.lowercase()}",
                        amount = it, currency = currencyPrefs.code, category = "food", occurredAt = at, externalId = group,
                    )
                },
            )
            logs.saveAll(rows)
            if (rows.size > 1) enable(PlanArea.MONEY)
            meals.sendToHealth(meal)
            PostHogAnalytics.capture("v4_meal_logged", mapOf("slot" to slot.key, "cost" to (cost != null), "kcal" to (kcal != null)))
        }
    }

    /** Ticks a planned meal as eaten (it goes to Health if its calories are known), or back to planned. */
    fun toggleEaten(row: MealRow) {
        viewModelScope.launch {
            val done = row.planned
            plans.setDone(row.log, done)
            if (done) meals.sendToHealth(row.log.copy(status = LogStatus.DONE))
        }
    }

    fun removeMeal(row: MealRow) {
        viewModelScope.launch {
            plans.remove(row.log)
            // A cooked plan takes its unbought ingredients and its leftovers with it.
            val group = row.log.externalId
            if (row.planned && group != null && MealNotes.decode(row.log.notes).leftoverOf == null) {
                logs.getInRange(today().minus(DatePeriod(days = 60)), today().plus(DatePeriod(days = 30)))
                    .filter { it.externalId == group && it.id != row.log.id && it.status == LogStatus.PLANNED }
                    .forEach { plans.remove(it) }
            }
        }
    }

    fun moveMeal(row: MealRow, date: LocalDate, slot: MealSlot) {
        viewModelScope.launch {
            plans.moveTo(row.log.copy(category = slot.key), LocalDateTime(date, slot.usualTime))
        }
    }

    // ── Planning ─────────────────────────────────────────────────────────────

    fun draft(date: LocalDate = today(), slot: MealSlot = MealSlot.DINNER, dish: String = ""): MealDraft {
        // A dish planned before brings back what it needed, so the list fills itself.
        val before = state.value.history.sortedBy { it.occurredAt }
        val known = if (dish.isBlank()) null else before.lastOrNull { MealPlanner.dishName(it).equals(dish, ignoreCase = true) && MealNotes.decode(it.notes).ingredients.isNotEmpty() }
        val notes = MealNotes.decode(known?.notes)
        return MealDraft(
            dish = dish, date = date, slot = slot, ingredients = notes.ingredients,
            kcal = known?.quantity?.takeIf { known.unit == MealService.UNIT_KCAL }, proteinG = notes.proteinG,
            minutes = known?.durationMin, url = notes.url,
        )
    }

    fun importRecipe(url: String, base: MealDraft, onDone: (MealDraft) -> Unit) {
        viewModelScope.launch {
            importing.value = true
            importFailed.value = false
            val r: Recipe? = meals.importRecipe(url)
            importing.value = false
            if (r == null) importFailed.value = true
            else onDone(base.copy(dish = r.name, ingredients = r.ingredients, kcal = r.kcalPerServing, proteinG = r.proteinPerServing, minutes = r.minutes, servings = r.servings, url = r.url))
        }
    }

    /**
     * Plans a meal: the meal itself, [extra] leftover meals in the next free lunch and dinner slots,
     * and its ingredients on the shopping list (skipping what is already there to buy).
     */
    fun plan(d: MealDraft, extra: Int, toCalendar: Boolean, have: Set<String> = emptySet()) {
        if (d.dish.isBlank()) return
        viewModelScope.launch {
            rememberHave(d.ingredients, have)
            planNow(d, extra, toCalendar)
        }
    }

    /** Plans one meal now. Returns how many things went on the list. */
    private suspend fun planNow(d: MealDraft, extra: Int, toCalendar: Boolean, hints: Map<String, Aisle> = emptyMap()): Int {
        val name = d.dish.trim().ifEmpty { return 0 }
        val group = Uuid.random().toString()
        val all = logs.getInRange(today().minus(DatePeriod(days = 1)), today().plus(DatePeriod(days = 30)))
        val cook = LifeLog(
            id = Uuid.random().toString(), area = PlanArea.MEALS, kind = LogKind.MEAL, title = name, category = d.slot.key,
            quantity = d.kcal?.takeIf { it > 0 }, unit = d.kcal?.takeIf { it > 0 }?.let { MealService.UNIT_KCAL },
            durationMin = d.minutes, occurredAt = LocalDateTime(d.date, d.slot.usualTime), externalId = group,
            notes = MealNotes(d.url, d.proteinG, d.ingredients).encode(),
        )
        val taken = all.filter { MealPlanner.isMeal(it) && it.status != LogStatus.SKIPPED }.map { it.date to MealPlanner.slotOf(it) }.toSet()
        val leftovers = MealPlanner.leftoverSlots(d.date, d.slot, extra, taken).map { (date, slot) ->
            cook.copy(
                id = Uuid.random().toString(), title = "Leftover $name".let { if (name.startsWith("Leftover", true)) name else it },
                category = slot.key, occurredAt = LocalDateTime(date, slot.usualTime), durationMin = null,
                notes = MealNotes(proteinG = d.proteinG, leftoverOf = cook.id).encode(),
            )
        }
        plans.plan(cook, toCalendar, eventTitle = "Cook: $name", leadMinutes = d.minutes?.coerceIn(10, 180) ?: 30)
        if (leftovers.isNotEmpty()) plans.planAll(leftovers, addToCalendar = false)
        val added = addToList(d.ingredients, group, hints)
        enable(PlanArea.MEALS)
        PostHogAnalytics.capture(
            "v4_meal_planned",
            mapOf("slot" to d.slot.key, "leftovers" to leftovers.size, "ingredients" to d.ingredients.size, "recipe" to (d.url != null), "calendar" to toCalendar),
        )
        return added
    }

    /**
     * What the plan sheet said you have: those stay off the list now and are pre-marked next time;
     * an ingredient shown and not marked is taken off "have it", since you said you need it.
     */
    private suspend fun rememberHave(ingredients: List<String>, have: Set<String>) {
        val shown = ingredients.map(MealWeek::ingredientKey).toSet()
        val marked = have.map(MealWeek::ingredientKey).toSet()
        val before = state.value.pantry
        val next = (before - shown) + marked
        if (next != before) {
            saveList(PANTRY_ID, METRIC_PANTRY, next)
            if ((marked - before).isNotEmpty()) PostHogAnalytics.capture("v4_meals_have_it", mapOf("items" to (marked - before).size))
        }
    }

    // ── Repeat last week ─────────────────────────────────────────────────────

    /** Plans last week's meals on the same weekdays and fills the list. Calls back with (meals, items). */
    fun repeatLastWeek(onDone: (Int, Int) -> Unit) {
        viewModelScope.launch {
            val today = today()
            val all = logs.getInRange(today.minus(DatePeriod(days = 60)), today.plus(DatePeriod(days = 14)))
            val copies = MealWeek.repeatLastWeek(all, today)
            if (copies.isEmpty()) { onDone(0, 0); return@launch }
            val ids = copies.associate { it.source.id to Uuid.random().toString() }
            val groups = mutableMapOf<String, String>()
            val rows = copies.map { c ->
                val notes = MealNotes.decode(c.source.notes)
                // Leftovers follow their cook when both are copied; alone they are just a meal.
                val cook = notes.leftoverOf?.let { ids[it] }
                val key = notes.leftoverOf?.takeIf { cook != null } ?: c.source.id
                c.source.copy(
                    id = ids.getValue(c.source.id), status = LogStatus.PLANNED, source = LifeLog.SOURCE_PLAN,
                    category = c.slot.key, occurredAt = LocalDateTime(c.date, c.slot.usualTime),
                    externalId = groups.getOrPut(key) { Uuid.random().toString() },
                    notes = notes.copy(leftoverOf = cook).encode(),
                )
            }
            plans.planAll(rows, addToCalendar = false)
            var items = 0
            rows.forEach { r -> MealNotes.decode(r.notes).ingredients.takeIf { it.isNotEmpty() }?.let { items += addToList(it, r.externalId) } }
            enable(PlanArea.MEALS)
            PostHogAnalytics.capture("v4_meals_repeat_week", mapOf("meals" to rows.size, "items" to items))
            onDone(rows.size, items)
        }
    }

    // ── Plan my week (the coach) ─────────────────────────────────────────────

    val coachWeek = MutableStateFlow<CoachWeekUi?>(null)

    fun openCoachWeek() {
        coachWeek.value = CoachWeekUi(dates = state.value.freeEvenings, household = coach.household)
    }

    fun closeCoachWeek() {
        coachWeek.value = null
    }

    private fun coachContext(household: Int): CoachContext {
        val s = state.value
        return CoachContext(
            foodBudget = s.foodBudget,
            household = household,
            likes = s.rotation.map { it.name },
            have = s.pantry.toList(),
            onList = s.toBuy.flatMap { it.second }.map { it.title },
            planned = s.plan.flatMap { d -> d.meals.values.flatten().filter { it.planned }.map { it.dish } }.distinct(),
        )
    }

    fun askCoach(household: Int) {
        val ui = coachWeek.value ?: return
        coach.household = household
        coachWeek.value = ui.copy(household = household, asked = true, loading = true, failed = false)
        viewModelScope.launch {
            val dinners = coach.planWeek(ui.dates, coachContext(household))
            coachWeek.value = coachWeek.value?.copy(loading = false, failed = dinners.isNullOrEmpty(), dinners = dinners.orEmpty())
        }
    }

    fun swapDinner(date: LocalDate) {
        val ui = coachWeek.value ?: return
        if (ui.swapping != null) return
        coachWeek.value = ui.copy(swapping = date)
        viewModelScope.launch {
            val avoid = ui.dinners.map { it.title } + state.value.plan.flatMap { d -> d.meals.values.flatten().map { it.dish } }
            val next = coach.swap(date, coachContext(ui.household), avoid.distinct())
            val now = coachWeek.value ?: return@launch
            coachWeek.value = now.copy(
                swapping = null,
                dinners = if (next == null) now.dinners else now.dinners.map { if (it.date == date) next.copy(date = date) else it },
            )
        }
    }

    fun dropDinner(date: LocalDate) {
        coachWeek.value = coachWeek.value?.let { ui -> ui.copy(dinners = ui.dinners.filterNot { it.date == date }) }
    }

    /** Plans every dinner kept, with its ingredients on the list (minus what is there or at home). */
    fun putCoachWeek(onDone: (Int, Int) -> Unit) {
        val ui = coachWeek.value ?: return
        if (ui.saving) return
        coachWeek.value = ui.copy(saving = true)
        viewModelScope.launch {
            var items = 0
            ui.dinners.forEach { dn ->
                val hints = dn.ingredients.mapNotNull { i -> i.aisle?.let { a -> i.line.lowercase() to a } }.toMap()
                items += planNow(
                    MealDraft(dish = dn.title, date = dn.date, slot = MealSlot.DINNER, ingredients = dn.ingredients.map { it.line }, minutes = dn.minutes),
                    extra = 0, toCalendar = false, hints = hints,
                )
            }
            PostHogAnalytics.capture("v4_meals_coach_saved", mapOf("dinners" to ui.dinners.size, "items" to items))
            coachWeek.value = null
            onDone(ui.dinners.size, items)
        }
    }

    // ── Staples ──────────────────────────────────────────────────────────────

    /** Puts every staple not already on the list onto it. Returns through [onDone] how many. */
    fun addStaples(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val existing = MealPlanner.shopping(logs.getInRange(today().minus(DatePeriod(days = 60)), today().plus(DatePeriod(days = 30))))
                .filter { it.status != LogStatus.DONE }
            val fresh = MealWeek.toBuy(state.value.staples, existing.map { it.title }, emptySet())
            saveItems(fresh, null, emptyMap())
            PostHogAnalytics.capture("v4_meals_staples_added", mapOf("items" to fresh.size))
            onDone(fresh.size)
        }
    }

    fun saveStaples(items: List<String>) {
        viewModelScope.launch { saveList(STAPLES_ID, METRIC_STAPLES, items) }
    }

    private suspend fun saveList(id: String, metric: String, items: Collection<String>) {
        val text = MealWeek.encodeList(items)
        budgets.save(Budget(id = id, area = PlanArea.MEALS, metric = metric, category = text, amount = MealWeek.decodeList(text).size.toDouble(), period = az.tribe.lifeplanner.domain.model.BudgetPeriod.WEEK))
    }

    // ── Shopping list ────────────────────────────────────────────────────────

    fun addToShopping(text: String) {
        viewModelScope.launch {
            // Typed in: kept even if you said you have it, but not twice on the list.
            val existing = MealPlanner.shopping(logs.getInRange(today().minus(DatePeriod(days = 60)), today().plus(DatePeriod(days = 30))))
            val typed = MealPlanner.parseShopping(text, existing)
            saveItems(MealWeek.toBuy(typed, existing.filter { it.status != LogStatus.DONE }.map { it.title }, emptySet()), null, emptyMap())
        }
    }

    /**
     * A meal's ingredients onto the list: one per line, minus what is on it already (in any amount)
     * and what you have in. [hints] carries the coach's aisle for items the words do not place.
     * Returns how many were added.
     */
    private suspend fun addToList(items: List<String>, group: String?, hints: Map<String, Aisle> = emptyMap()): Int {
        val existing = MealPlanner.shopping(logs.getInRange(today().minus(DatePeriod(days = 60)), today().plus(DatePeriod(days = 30))))
            .filter { it.status != LogStatus.DONE }
        val have = budgets.getAll().firstOrNull { it.area == PlanArea.MEALS && it.metric == METRIC_PANTRY }?.let { MealWeek.decodeList(it.category).toSet() }.orEmpty()
        val fresh = MealWeek.toBuy(items, existing.map { it.title }, have)
        saveItems(fresh, group, hints)
        return fresh.size
    }

    private suspend fun saveItems(fresh: List<String>, group: String?, hints: Map<String, Aisle>) {
        if (fresh.isEmpty()) return
        val now = Clock.System.now().toLocalDateTime(tz)
        logs.saveAll(
            fresh.mapIndexed { i, t ->
                val hint = hints.entries.firstOrNull { (line, _) -> MealWeek.ingredientKey(line) == MealWeek.ingredientKey(t) }?.value
                LifeLog(
                    id = Uuid.random().toString(), area = PlanArea.MEALS, kind = LogKind.NOTE, status = LogStatus.PLANNED, title = t,
                    category = MealPlanner.CATEGORY_SHOPPING, occurredAt = LocalDateTime(now.date, LocalTime(now.hour, now.minute, (now.second + i).coerceAtMost(59))),
                    externalId = group, unit = hint?.takeIf { MealPlanner.aisleOf(t) == Aisle.OTHER }?.name,
                )
            },
        )
    }

    fun toggleBought(item: LifeLog) {
        viewModelScope.launch { logs.save(item.copy(status = if (item.status == LogStatus.DONE) LogStatus.PLANNED else LogStatus.DONE)) }
    }

    fun removeItem(item: LifeLog) {
        viewModelScope.launch { logs.delete(item.id) }
    }

    /** Done shopping: clears what was bought and, with an [amount], files it as groceries in Money. */
    fun finishShopping(amount: Double?) {
        viewModelScope.launch {
            val bought = state.value.bought
            amount?.takeIf { it > 0 }?.let {
                logs.save(
                    LifeLog(
                        id = Uuid.random().toString(), area = PlanArea.MONEY, kind = LogKind.EXPENSE, title = "Groceries",
                        amount = it, currency = currencyPrefs.code, category = "food", occurredAt = Clock.System.now().toLocalDateTime(tz),
                    ),
                )
                enable(PlanArea.MONEY)
            }
            bought.forEach { logs.delete(it.id) }
            PostHogAnalytics.capture("v4_shopping_done", mapOf("items" to bought.size, "spend" to (amount != null)))
        }
    }

    /** The list as plain text, by aisle, for sharing with whoever is doing the shop. */
    fun shoppingText(): String = buildString {
        append("Shopping list\n")
        state.value.toBuy.forEach { (aisle, items) ->
            append("\n").append(aisle.label).append("\n")
            items.forEach { append("- ").append(it.title).append("\n") }
        }
    }.trimEnd()

    // ── Water ────────────────────────────────────────────────────────────────

    fun addWater() {
        viewModelScope.launch { meals.addWater(1) }
    }

    fun removeWater() {
        viewModelScope.launch { meals.removeLastWater() }
    }

    fun setWaterTarget(n: Int) {
        meals.waterTarget = n
        extras.value = extras.value.copy(waterTarget = meals.waterTarget)
    }

    fun onHealthGranted() {
        viewModelScope.launch {
            meals.requestHealth()
            refresh()
        }
    }

    // ── Money ────────────────────────────────────────────────────────────────

    fun setFoodBudget(amount: Double) {
        viewModelScope.launch {
            val existing = budgets.getAll().firstOrNull { it.area == PlanArea.MONEY && it.metric == Budget.METRIC_SPEND && it.category == "food" && it.tripId == null }
            budgets.save(
                Budget(
                    id = existing?.id ?: Uuid.random().toString(), area = PlanArea.MONEY, metric = Budget.METRIC_SPEND,
                    category = "food", amount = amount, currency = currencyPrefs.code, period = az.tribe.lifeplanner.domain.model.BudgetPeriod.WEEK,
                ),
            )
            enable(PlanArea.MONEY)
        }
    }

    /** What to ask the coach for a week of dinners, with what the page knows. */
    fun coachPrompt(): String {
        val s = state.value
        return buildString {
            append("Help me plan dinners for the next 7 days. ")
            if (s.favourites.isNotEmpty()) append("Meals I often eat: ${s.favourites.joinToString(", ")}. ")
            s.foodBudget?.let { append("Food money: $it. ") }
            val planned = s.plan.flatMap { d -> d.meals[MealSlot.DINNER].orEmpty().filter { it.planned }.map { "${d.label}: ${it.dish}" } }
            if (planned.isNotEmpty()) append("Already planned: ${planned.joinToString("; ")}. ")
            val list = s.toBuy.flatMap { it.second }.map { it.title }
            if (list.isNotEmpty()) append("On my shopping list: ${list.take(15).joinToString(", ")}. ")
            append("Keep it simple, reuse ingredients across days, and plan one cook for two meals where it makes sense.")
        }
    }

    companion object {
        const val METRIC_STAPLES = "staples"
        const val METRIC_PANTRY = "pantry"
        private const val STAPLES_ID = "meals-staples"
        private const val PANTRY_ID = "meals-pantry"
    }

    private suspend fun enable(area: PlanArea) {
        val on = planAreas.enabledAreas.value
        if (area !in on) planAreas.setEnabledAreas(on + area)
    }
}
