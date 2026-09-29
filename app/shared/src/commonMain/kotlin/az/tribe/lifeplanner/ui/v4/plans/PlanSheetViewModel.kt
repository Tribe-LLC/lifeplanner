package az.tribe.lifeplanner.ui.v4.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.core.CurrencyPrefs
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.plans.DraftStep
import az.tribe.lifeplanner.data.plans.PlanMaker
import az.tribe.lifeplanner.data.plans.PlanStepSuggester
import az.tribe.lifeplanner.data.plans.Suggestion
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.HealthRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.PlanProgress
import az.tribe.lifeplanner.domain.service.PlanScheduler
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

enum class SheetStage { TYPE, PREVIEW, SAVED }

/** How the sheet was opened: from an area page, a typed line in Add anything, an idea, a finished plan. */
data class PlanSheetRequest(val area: PlanArea? = null, val line: String = "", val source: String = "area")

data class PlanSheetState(
    val stage: SheetStage = SheetStage.TYPE,
    val inputs: SheetInputs = SheetInputs(),
    val ctx: SheetContext? = null,
    val preview: SheetPreview? = null,
    val ideas: List<String> = emptyList(),
    val suggesting: Boolean = false,
    /** Why the coach gave no steps, when it did not. */
    val suggestNote: String? = null,
    val saving: Boolean = false,
    val savedId: String? = null,
    val savedLine: String = "",
    val savedFacts: List<String> = emptyList(),
)

/**
 * The one-line plan sheet: type, preview, saved. Every tap changes [SheetInputs]; the preview is
 * worked out again from them by [PlanSheetModel], so what the user sees is always what gets saved.
 */
class PlanSheetViewModel(
    private val maker: PlanMaker,
    private val suggester: PlanStepSuggester,
    private val currency: CurrencyPrefs,
    private val logs: LifeLogRepository,
    private val health: HealthRepository,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    private val _state = MutableStateFlow(PlanSheetState())
    val state: StateFlow<PlanSheetState> = _state.asStateFlow()

    /** Starts afresh each time the sheet opens. The sheet lives above every screen, so this one outlives it. */
    fun open(req: PlanSheetRequest) {
        val ctx = SheetContext(today(), currency.code)
        val inputs = SheetInputs(line = req.line, preset = req.area, source = req.source)
        _state.value = PlanSheetState(inputs = inputs, ctx = ctx, ideas = PlanSheetModel.ideas(req.area, ctx.currency))
        refresh()
        // A line already chosen (typed in Add anything, an idea, a next plan) goes straight to its plan.
        if (req.line.isNotBlank() && _state.value.preview != null) _state.value = _state.value.copy(stage = SheetStage.PREVIEW)
        PostHogAnalytics.capture("v4_plan_sheet_opened", mapOf("source" to req.source, "area" to (req.area?.key ?: "none"), "prefilled" to req.line.isNotBlank()))
        viewModelScope.launch { learnAboutUser(ctx) }
    }

    /**
     * Two things that change the steps: runs that come only from Health carry no distance, so run
     * steps are in minutes; and the latest weight from Health is where a weight plan starts.
     */
    private suspend fun learnAboutUser(ctx: SheetContext) {
        val today = ctx.today
        val runs = runCatching { PlanProgress.runs(logs.getInRange(today.minus(DatePeriod(days = 180)), today), today.minus(DatePeriod(days = 180))) }
            .getOrDefault(emptyList())
        val minutes = runs.isNotEmpty() && runs.none { it.quantity != null && (it.unit == null || it.unit.equals("km", ignoreCase = true)) }
        val weight = runCatching { health.getLatestWeight() }.getOrNull()
        if (minutes || weight != null) {
            _state.value = _state.value.copy(ctx = ctx.copy(runInMinutes = minutes, weightKg = weight))
            refresh()
        }
    }

    private fun update(change: (SheetInputs) -> SheetInputs) {
        _state.value = _state.value.copy(inputs = change(_state.value.inputs))
        refresh()
    }

    private fun refresh() {
        val s = _state.value
        val ctx = s.ctx ?: return
        _state.value = s.copy(preview = runCatching { PlanSheetModel.preview(s.inputs, ctx) }.onFailure { Logger.w("PlanSheet") { "Preview failed: ${it.message}" } }.getOrNull())
    }

    // ── Type ─────────────────────────────────────────────────────────────────

    /** A new line starts the plan over: nothing picked for the old one carries across. */
    fun onLine(text: String) = update { SheetInputs(line = text, preset = it.preset, source = it.source) }

    fun pickIdea(text: String) {
        PostHogAnalytics.capture("v4_plan_idea_picked", mapOf("idea" to text))
        update { SheetInputs(line = text, preset = it.preset, source = if (it.source == "area") "idea" else it.source) }
    }

    fun make() {
        if (_state.value.preview == null) return
        _state.value = _state.value.copy(stage = SheetStage.PREVIEW)
    }

    fun back() {
        _state.value = _state.value.copy(stage = SheetStage.TYPE)
    }

    // ── Preview ──────────────────────────────────────────────────────────────

    /** A different answer means different steps, so removals and moves start over. */
    fun answer(key: String) = update { it.copy(answer = key, removed = emptySet(), moved = emptyMap()) }

    fun removeStep(key: String) = update { i ->
        if (key.startsWith("a")) {
            // Added steps are keyed by position, so taking one out renumbers the rest.
            val n = key.drop(1).toIntOrNull() ?: return@update i
            val moved = i.moved.filterKeys { !it.startsWith("a") } +
                i.moved.filterKeys { it.startsWith("a") }.mapNotNull { (k, d) ->
                    val m = k.drop(1).toIntOrNull() ?: return@mapNotNull null
                    when { m < n -> k to d; m > n -> "a${m - 1}" to d; else -> null }
                }
            i.copy(added = i.added.filterIndexed { idx, _ -> idx != n }, moved = moved)
        } else i.copy(removed = i.removed + key)
    }

    fun addStep(title: String) {
        val p = _state.value.preview ?: return
        val t = title.trim().takeIf { it.isNotEmpty() } ?: return
        update { it.copy(added = it.added + DraftStep(t, PlanSheetModel.nextStepDate(p)), own = true) }
    }

    /** A step's day, kept between today and the plan's date. */
    fun moveStep(key: String, date: LocalDate) {
        val p = _state.value.preview ?: return
        val d = maxOf(date, p.start).let { if (p.open) it else minOf(it, p.target) }
        update { it.copy(moved = it.moved + (key to d)) }
    }

    fun setTarget(date: LocalDate) {
        val min = today().plus(DatePeriod(days = 1))
        update { it.copy(target = maxOf(date, min), moved = emptyMap()) }
    }

    fun setAmount(amount: Double) = update { it.copy(amount = amount, answer = null, removed = emptySet(), moved = emptyMap()) }

    fun setArea(area: PlanArea) = update { it.copy(area = area) }

    fun toggleRoutine() = update { i -> i.copy(routineOn = !(_state.value.preview?.routineOn ?: false)) }

    fun ownSteps() = update { it.copy(own = true) }

    /** One call to the coach, only because the user asked. Its answer is kept for plans like this. */
    fun suggest() {
        val p = _state.value.preview ?: return
        if (_state.value.suggesting) return
        _state.value = _state.value.copy(suggesting = true, suggestNote = null)
        viewModelScope.launch {
            val weeks = if (p.dated) (PlanScheduler.days(p.start, p.target) / 7).coerceAtLeast(1) else null
            when (val r = suggester.suggest(p.title, p.area, weeks)) {
                is Suggestion.Steps -> {
                    _state.value = _state.value.copy(suggesting = false)
                    update { it.copy(suggested = r.steps, removed = emptySet(), moved = emptyMap()) }
                }
                Suggestion.Limit -> _state.value = _state.value.copy(
                    suggesting = false, suggestNote = "That is all the suggestions for today. Add your own steps, or ask again tomorrow.",
                )
                Suggestion.Failed -> _state.value = _state.value.copy(
                    suggesting = false, suggestNote = "The coach could not answer just now. Add your own steps, or try again.",
                )
            }
        }
    }

    fun start() {
        val s = _state.value
        val p = s.preview ?: return
        if (s.saving || p.steps.isEmpty()) return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            runCatching { maker.create(p.draft(s.inputs.source)) }
                .onSuccess { id ->
                    _state.value = _state.value.copy(
                        stage = SheetStage.SAVED, saving = false, savedId = id,
                        savedLine = PlanSheetModel.savedLine(p), savedFacts = PlanSheetModel.savedFacts(p),
                    )
                }
                .onFailure {
                    Logger.e("PlanSheet") { "Create failed: ${it.message}" }
                    _state.value = _state.value.copy(saving = false)
                }
        }
    }
}
