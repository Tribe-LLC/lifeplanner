package az.tribe.lifeplanner.ui.v4.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.core.CurrencyPrefs
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.plans.PlanBoard
import az.tribe.lifeplanner.data.plans.PlanMaker
import az.tribe.lifeplanner.data.plans.PlanView
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.service.CatchUpChoice
import az.tribe.lifeplanner.domain.service.RoutineKind
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class PlanPageState(
    val loading: Boolean = true,
    val view: PlanView? = null,
    val today: LocalDate = LocalDate(2026, 1, 1),
    /** The plan before a new date, while Undo is offered. */
    val beforeNewDate: Goal? = null,
    /** What the catch-up answer did, or the note after "what next". */
    val note: String? = null,
    /** The routine habit's own name, for "Keeps it moving". */
    val routineName: String? = null,
    val currency: String = "EUR",
)

/**
 * One plan's page: everything shown comes from the [PlanBoard], every change goes through the
 * [PlanMaker]. Holds only what the page adds for a moment: an Undo, a note.
 */
class V4PlanViewModel(
    private val goalId: String,
    private val board: PlanBoard,
    private val maker: PlanMaker,
    private val habits: HabitRepository,
    private val currency: CurrencyPrefs,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    private data class Local(val before: Goal? = null, val note: String? = null)
    private val local = MutableStateFlow(Local())
    private var routineCache: Pair<String, String?>? = null

    val state: StateFlow<PlanPageState> = combine(
        board.plan(goalId).map { v -> v to routineName(v) },
        local,
    ) { (v, name), l ->
        PlanPageState(loading = false, view = v, today = today(), beforeNewDate = l.before, note = l.note, routineName = name, currency = currency.code)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanPageState(today = today()))

    init {
        PostHogAnalytics.capture("v4_plan_opened")
        viewModelScope.launch { runCatching { board.refreshHealth() } }
    }

    private suspend fun routineName(v: PlanView?): String? {
        val id = v?.spec?.takeIf { it.routineKind == RoutineKind.HABIT }?.routineId ?: return null
        routineCache?.takeIf { it.first == id }?.let { return it.second }
        val name = runCatching { habits.getHabitById(id)?.title }.getOrNull()
        routineCache = id to name
        return name
    }

    private fun launch(what: String, block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() }.onFailure { Logger.e("V4Plan") { "$what failed: ${it.message}" } } }
    }

    private fun note(text: String?) {
        local.value = local.value.copy(note = text)
    }

    // ── Steps ────────────────────────────────────────────────────────────────

    fun setStep(m: Milestone, done: Boolean) = launch("Tick") { maker.setStep(goalId, m.id, done) }

    fun addStep(title: String, date: LocalDate?) = launch("Add step") { title.trim().takeIf { it.isNotEmpty() }?.let { maker.addStep(goalId, it, date) } }

    fun editStep(m: Milestone, title: String, date: LocalDate?) = launch("Edit step") { maker.editStep(m, title, date) }

    fun removeStep(m: Milestone) = launch("Remove step") { maker.removeStep(m) }

    /** Where a new step would land if the user picks no day. */
    fun newStepDate(): LocalDate = state.value.view?.goal?.let { PlanMaker.newStepDate(it, today()) } ?: today()

    // ── Moving the plan ──────────────────────────────────────────────────────

    fun catchUp(choice: CatchUpChoice) = launch("Catch up") { note(maker.catchUp(goalId, choice, "plan")) }

    fun changeDate(date: LocalDate) = launch("Change date") {
        val before = maker.changeDate(goalId, date)
        local.value = Local(before = before)
    }

    fun undoDate() = launch("Undo date") {
        local.value.before?.let { maker.restore(it) }
        local.value = Local()
        PostHogAnalytics.capture("v4_plan_date_undone")
    }

    fun dismissNewDate() {
        local.value = local.value.copy(before = null)
    }

    fun pause(days: Int?) = launch("Pause") { maker.pause(goalId, days); local.value = Local() }

    fun resume() = launch("Resume") { maker.resume(goalId) }

    fun letGo() = launch("Let go") { maker.letGo(goalId); local.value = Local() }

    fun bringBack() = launch("Bring back") { maker.bringBack(goalId) }

    fun rename(title: String) = launch("Rename") { maker.rename(goalId, title) }

    fun delete(onDone: () -> Unit) = launch("Delete") {
        maker.delete(goalId)
        onDone()
    }

    // ── Logging ──────────────────────────────────────────────────────────────

    fun putAside(amount: Double) = launch("Put aside") { maker.putAside(goalId, amount, state.value.view?.spec?.currency ?: currency.code) }

    fun addOne() = launch("Plus one") { maker.addOne(goalId) }

    /** "Just keep the routine": nothing to change, the routine stays. Says so. */
    fun keepRoutine() {
        note("It stays on Today on its own. The plan is in Done.")
        PostHogAnalytics.capture("v4_plan_next", mapOf("choice" to "keep_routine"))
    }
}
