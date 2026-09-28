package az.tribe.lifeplanner.ui.v4.areas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.habits.HabitRow
import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.data.mind.MindService
import az.tribe.lifeplanner.domain.enum.HabitType
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.repository.HealthRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.HabitSchedule
import az.tribe.lifeplanner.domain.service.MindCheckIns
import az.tribe.lifeplanner.domain.service.MindInsights
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock

data class MindState(
    /** The check-in being filled in now, after the face was tapped. */
    val editing: LifeLog? = null,
    val lastToday: LifeLog? = null,
    /** Oldest first, one per day, null for days without a check-in. */
    val moods: List<Double?> = emptyList(),
    val moodSummary: String = "",
    val checkInDays14: Int = 0,
    val lifts: List<MindInsights.Lift> = emptyList(),
    val checkInDaysTotal: Int = 0,
    /** Oldest first. */
    val sleep: List<Pair<LocalDate, Double>> = emptyList(),
    val sleepGoal: Double? = null,
    val mindfulWeek: Int = 0,
    val supportsMindful: Boolean = false,
    val canWriteHealth: Boolean = false,
    val promptOffset: Int = 0,
    val entries: List<JournalEntry> = emptyList(),
    val lowRun: Boolean = false,
    val loaded: Boolean = false,
)

class V4MindViewModel(
    private val mind: MindService,
    logs: LifeLogRepository,
    journal: JournalRepository,
    habits: HabitService,
    private val healthRepository: HealthRepository,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    private val editing = MutableStateFlow<LifeLog?>(null)
    private val promptOffset = MutableStateFlow(0)
    private val healthDays = MutableStateFlow(HealthDays())
    private val flags = MutableStateFlow(Triple(false, false, null as Double?))

    private data class HealthDays(val steps: Map<LocalDate, Double> = emptyMap(), val sleep: Map<LocalDate, Double> = emptyMap())

    val state: StateFlow<MindState> = combine(
        combine(logs.observeInRange(today().minus(DatePeriod(days = 90)), today()), journal.observeAllEntries(), ::Pair),
        habits.rows,
        healthDays,
        combine(editing, promptOffset, flags, ::Triple),
    ) { (ls, entries), habitRows, h, (edit, offset, f) ->
        build(ls, entries, habitRows, h, edit, offset, f.first, f.second, f.third)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MindState())

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        val today = today()
        val from = today.minus(DatePeriod(days = 90))
        fun byDay(t: HealthMetricType) = suspend {
            runCatching { healthRepository.getMetricsInRange(t, from, today) }.getOrDefault(emptyList())
                .groupBy { it.date }.mapValues { (_, v) -> if (t == HealthMetricType.STEPS) v.sumOf { it.value } else v.maxOf { it.value } }
        }
        healthDays.value = HealthDays(byDay(HealthMetricType.STEPS)(), byDay(HealthMetricType.SLEEP)())
        flags.value = Triple(mind.supportsMindful(), mind.canWriteHealth(), mind.sleepGoalHours())
    }

    private fun build(
        ls: List<LifeLog>, entries: List<JournalEntry>, habitRows: List<HabitRow>, h: HealthDays,
        edit: LifeLog?, offset: Int, supports: Boolean, canWrite: Boolean, goal: Double?,
    ): MindState {
        val today = today()
        val checkIns = ls.filter { MindCheckIns.isCheckIn(it) }
        val journalScores = entries.map { it.date to it.mood.score }
        val daily = MindInsights.dailyMood(checkIns, journalScores)
        val last14 = (13 downTo 0).map { today.minus(DatePeriod(days = it)) }
        val weekFrom = HabitSchedule.weekStart(today)

        val factors = mutableMapOf<String, Set<LocalDate>>()
        MindCheckIns.TAGS.forEach { tag ->
            val days = checkIns.filter { tag in MindCheckIns.tags(it) }.map { it.date }.toSet()
            if (days.isNotEmpty()) factors["Days tagged ${tag.lowercase()}"] = days
        }
        habitRows.forEach { r ->
            val name = if (r.habit.type == HabitType.QUIT) "Days you resisted: ${r.habit.title}" else "Days you did: ${r.habit.title}"
            if (r.done.isNotEmpty()) factors[name] = r.done
        }
        h.steps.filterValues { it >= 8_000 }.keys.takeIf { it.isNotEmpty() }?.let { factors["Days you walk 8,000+ steps"] = it }
        h.sleep.filterValues { it >= 7.0 }.keys.takeIf { it.isNotEmpty() }?.let { factors["Nights of 7 hours or more"] = it }
        ls.filter { FitnessWeek.isWorkout(it) && it.status == LogStatus.DONE }.map { it.date }.toSet().takeIf { it.isNotEmpty() }?.let { factors["Days you work out"] = it }
        ls.filter { MindCheckIns.isMindful(it) }.map { it.date }.toSet().takeIf { it.isNotEmpty() }?.let { factors["Days you stop to breathe"] = it }

        val scoresInOrder = checkIns.sortedBy { it.occurredAt }.mapNotNull { MindCheckIns.score(it) }
        val recent14 = last14.mapNotNull { daily[it] }
        return MindState(
            editing = edit,
            lastToday = checkIns.filter { it.date == today }.maxByOrNull { it.occurredAt },
            moods = last14.map { daily[it] },
            moodSummary = MindInsights.summary(recent14.takeIf { it.isNotEmpty() }?.average()),
            checkInDays14 = recent14.size,
            lifts = MindInsights.lifts(daily, factors),
            checkInDaysTotal = daily.size,
            sleep = (7 downTo 1).map { today.minus(DatePeriod(days = it - 1)) }.mapNotNull { d -> h.sleep[d]?.let { d to it } },
            sleepGoal = goal,
            mindfulWeek = ls.filter { MindCheckIns.isMindful(it) && it.date >= weekFrom }.sumOf { it.durationMin ?: 0 },
            supportsMindful = supports,
            canWriteHealth = canWrite,
            promptOffset = offset,
            entries = entries.sortedByDescending { it.createdAt }.take(2),
            lowRun = MindInsights.lowRun(scoresInOrder),
            loaded = true,
        )
    }

    // ── Check-in ─────────────────────────────────────────────────────────────

    fun pick(score: Int) = viewModelScope.launch {
        runCatching {
            val current = editing.value
            editing.value = if (current != null) {
                mind.update(current, score, MindCheckIns.tags(current), MindCheckIns.feelings(current), MindCheckIns.note(current))
            } else {
                PostHogAnalytics.capture("v4_mood_checked_in", mapOf("score" to score))
                mind.checkIn(score)
            }
        }
    }

    fun toggleTag(tag: String) = edit { l -> val t = MindCheckIns.tags(l); Triple(if (tag in t) t - tag else t + tag, MindCheckIns.feelings(l), MindCheckIns.note(l)) }
    fun toggleFeeling(word: String) = edit { l -> val f = MindCheckIns.feelings(l); Triple(MindCheckIns.tags(l), if (word in f) f - word else f + word, MindCheckIns.note(l)) }
    fun setNote(note: String) = edit { l -> Triple(MindCheckIns.tags(l), MindCheckIns.feelings(l), note) }

    private fun edit(change: (LifeLog) -> Triple<List<String>, List<String>, String?>) = viewModelScope.launch {
        val current = editing.value ?: return@launch
        val (tags, feelings, note) = change(current)
        runCatching { editing.value = mind.update(current, MindCheckIns.score(current) ?: 3, tags, feelings, note) }
    }

    /** Done with this check-in: it goes to Health now, with its tags and feelings. */
    fun done() {
        editing.value?.let { mind.sendToHealth(it) }
        editing.value = null
    }

    override fun onCleared() {
        editing.value?.let { mind.sendToHealth(it) }
    }

    // ── Calm, sleep, journal ─────────────────────────────────────────────────

    fun breathed(startEpochMs: Long) = viewModelScope.launch {
        runCatching { mind.breathed(startEpochMs, Clock.System.now().toEpochMilliseconds()) }
    }

    fun onHealthGranted() = viewModelScope.launch {
        mind.requestHealth()
        refresh()
    }

    fun setSleepGoal(hours: Double?) = viewModelScope.launch {
        runCatching { mind.setSleepGoal(hours) }
        refresh()
    }

    fun nextPrompt() { promptOffset.value++ }

    fun write(text: String, prompt: String?) = viewModelScope.launch {
        runCatching { mind.write(prompt ?: "", text, todayScore(), prompt) }
    }

    fun threeGoodThings(things: List<String>) = viewModelScope.launch {
        val list = things.map { it.trim() }.filter { it.isNotEmpty() }
        if (list.isEmpty()) return@launch
        runCatching {
            mind.write("Three good things", list.mapIndexed { i, t -> "${i + 1}. $t" }.joinToString("\n"), todayScore(), null, listOf("gratitude"))
        }
    }

    private fun todayScore(): Int? = (editing.value ?: state.value.lastToday)?.let { MindCheckIns.score(it) }

    companion object {
        fun prompt(offset: Int): String = MindInsights.prompt(Clock.System.todayIn(TimeZone.currentSystemDefault()), offset)
    }
}
