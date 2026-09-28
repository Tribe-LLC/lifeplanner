package az.tribe.lifeplanner.ui.v4.areas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.fitness.WorkoutService
import az.tribe.lifeplanner.data.plans.PlanService
import az.tribe.lifeplanner.data.study.ActiveStudy
import az.tribe.lifeplanner.data.study.FoundDate
import az.tribe.lifeplanner.data.study.StudyService
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.FocusRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.StudyKind
import az.tribe.lifeplanner.domain.service.StudyPlanner
import az.tribe.lifeplanner.domain.service.StudyTime
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

/** An exam or deadline with how its preparation is going. */
data class DueRow(val log: LifeLog, val countdown: String, val blocksDone: Int, val blocksTotal: Int, val onCalendar: Boolean)

/** A planned study block, with the words to show beside it. */
data class BlockRow(val log: LifeLog, val day: String, val time: String, val forWhat: String?, val done: Boolean)

data class StudyState(
    val week: StudyPlanner.Week? = null,
    val targetMinutes: Int? = null,
    val todayBlocks: List<BlockRow> = emptyList(),
    val missed: Int = 0,
    val due: List<DueRow> = emptyList(),
    val comingUp: List<BlockRow> = emptyList(),
    val recent: List<LifeLog> = emptyList(),
    val subjects: List<String> = emptyList(),
    val canCalendar: Boolean = false,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalUuidApi::class)
class V4StudyViewModel(
    private val logs: LifeLogRepository,
    private val budgets: BudgetRepository,
    private val study: StudyService,
    private val plans: PlanService,
    focus: FocusRepository,
    goals: GoalRepository,
    private val planAreas: PlanAreasRepository,
) : ViewModel() {

    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)

    val active: StateFlow<ActiveStudy?> = study.active

    private val canCalendar = MutableStateFlow(false)
    /** Bumped when a calendar event is added or removed, since that lives in settings. */
    private val calendarTick = MutableStateFlow(0)

    /** Focus timer sessions from the v3 screen count as study, named after their plan. */
    private val focusTimes = combine(focus.observeAllSessions(), goals.observeAllGoals()) { sessions, gs ->
        val names = gs.associate { it.id to it.title }
        sessions.filter { it.wasCompleted || it.actualMinutes > 0 }.mapNotNull { s ->
            val at = s.completedAt ?: return@mapNotNull null
            StudyTime(at.date, s.actualMinutes, names[s.goalId])
        }
    }

    val state: StateFlow<StudyState> = combine(
        logs.observeInRange(today().minus(DatePeriod(days = 60)), today().plus(DatePeriod(days = 180))),
        budgets.observeAll(),
        focusTimes,
        canCalendar,
        calendarTick,
    ) { all, bs, focusRows, cal, _ ->
        val today = today()
        val target = bs.firstOrNull { it.area == PlanArea.STUDY && it.metric == METRIC_MINUTES }?.amount?.toInt()
        val studyRows = all.filter { StudyPlanner.isStudy(it) }
        val exams = studyRows.filter { StudyPlanner.isDated(it) }.associateBy { it.id }
        fun block(l: LifeLog) = BlockRow(
            log = l,
            day = if (l.date == today) "Today" else if (l.date == today.plus(DatePeriod(days = 1))) "Tomorrow" else FitnessWeek.shortDay(l.date.dayOfWeek),
            time = if (WorkoutService.hasTime(l)) V4FitnessViewModel.fmt(l.occurredAt.time) else "Any",
            forWhat = l.notes?.let { exams[it] }?.let { e -> "For ${StudyPlanner.dueName(e)}" }
                ?: l.notes?.takeIf { it.startsWith(REVIEW_NOTE) }?.let { "Review" },
            done = l.status == LogStatus.DONE,
        )
        val blocks = studyRows.filter { StudyPlanner.kindOf(it) == StudyKind.BLOCK }
        StudyState(
            week = StudyPlanner.week(StudyPlanner.times(all, focusRows), today, target),
            targetMinutes = target,
            todayBlocks = blocks.filter { it.date == today && it.status != LogStatus.SKIPPED }.sortedBy { it.occurredAt }.map(::block),
            missed = StudyPlanner.missed(all, today).size,
            due = StudyPlanner.upcoming(all, today).map { e ->
                val prep = StudyPlanner.blocksFor(e, all)
                DueRow(e, StudyPlanner.countdown(e.date, today), prep.count { it.status == LogStatus.DONE }, prep.size, plans.hasEvent(e))
            },
            comingUp = blocks.filter { it.status == LogStatus.PLANNED && it.date > today && it.date <= today.plus(DatePeriod(days = 7)) }
                .sortedBy { it.occurredAt }.take(8).map(::block),
            recent = studyRows.filter { it.status == LogStatus.DONE && !StudyPlanner.isDated(it) }.sortedByDescending { it.occurredAt }.take(6),
            subjects = studyRows.filter { !StudyPlanner.isDated(it) }.map { it.title.trim() }.filter { it.isNotEmpty() && !it.startsWith("Review:") }
                .groupingBy { it.lowercase() }.eachCount().entries.sortedByDescending { it.value }
                .mapNotNull { e -> studyRows.firstOrNull { it.title.trim().lowercase() == e.key }?.title?.trim() }.take(6),
            canCalendar = cal,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyState())

    init {
        viewModelScope.launch { canCalendar.value = plans.canAddToCalendar() }
    }

    // ── Timer ────────────────────────────────────────────────────────────────

    fun start(subject: String, block: LifeLog? = null, targetMin: Int = block?.durationMin ?: 25) = study.start(subject, block?.id, targetMin)

    fun cancel() = study.cancel()

    /** Stops and saves; [onSaved] gets the minutes and the subject, for the "review later" offer. */
    fun stop(onSaved: (Int, String) -> Unit) {
        val subject = active.value?.subject ?: return
        viewModelScope.launch {
            val minutes = study.stop()
            if (minutes > 0) enable()
            onSaved(minutes, subject)
        }
    }

    // ── Time ─────────────────────────────────────────────────────────────────

    fun logTime(subject: String, minutes: Int, date: LocalDate) {
        if (minutes <= 0) return
        viewModelScope.launch {
            val now = Clock.System.now().toLocalDateTime(tz)
            logs.save(
                LifeLog(
                    id = Uuid.random().toString(), area = PlanArea.STUDY, kind = LogKind.STUDY, title = subject.trim().ifEmpty { "Study" },
                    category = StudyKind.SESSION.key, durationMin = minutes,
                    occurredAt = if (date == now.date) now else LocalDateTime(date, LocalTime(18, 0)),
                ),
            )
            enable()
            PostHogAnalytics.capture("v4_study_logged", mapOf("minutes" to minutes))
        }
    }

    fun setTarget(hoursPerWeek: Int) {
        viewModelScope.launch {
            val existing = budgets.getAll().firstOrNull { it.area == PlanArea.STUDY && it.metric == METRIC_MINUTES }
            budgets.save(
                Budget(existing?.id ?: Uuid.random().toString(), PlanArea.STUDY, METRIC_MINUTES, amount = hoursPerWeek * 60.0, period = BudgetPeriod.WEEK),
            )
        }
    }

    // ── Blocks ───────────────────────────────────────────────────────────────

    fun planBlock(subject: String, date: LocalDate, time: LocalTime?, minutes: Int, toCalendar: Boolean, forExam: LifeLog? = null) {
        val name = subject.trim().ifEmpty { return }
        viewModelScope.launch {
            plans.plan(newBlock(name, date, time, minutes, forExam?.id), toCalendar, eventTitle = "Study: $name")
            enable()
            PostHogAnalytics.capture("v4_study_block_planned", mapOf("in_days" to (date.toEpochDays() - today().toEpochDays()), "calendar" to toCalendar))
        }
    }

    fun toggleBlock(row: BlockRow) {
        viewModelScope.launch { plans.setDone(row.log, !row.done) }
    }

    fun remove(log: LifeLog) {
        viewModelScope.launch {
            // An exam takes its planned blocks with it; done ones stay, that time was real.
            if (StudyPlanner.isDated(log)) StudyPlanner.blocksFor(log, logs.getInRange(today().minus(DatePeriod(days = 60)), log.date))
                .filter { it.status == LogStatus.PLANNED }.forEach { plans.remove(it) }
            plans.remove(log)
        }
    }

    fun moveToToday(log: LifeLog) {
        viewModelScope.launch { plans.moveTo(log, LocalDateTime(today(), log.occurredAt.time)) }
    }

    /** Moves every missed block to a new day: before its exam if it had one, else later this week. */
    fun rollover() {
        viewModelScope.launch {
            val all = logs.getInRange(today().minus(DatePeriod(days = 60)), today().plus(DatePeriod(days = 180)))
            val missed = StudyPlanner.missed(all, today())
            val moves = StudyPlanner.rollover(missed, all, today())
            missed.forEach { b ->
                val to = moves[b.id]
                if (to != null) plans.moveTo(b, LocalDateTime(to, b.occurredAt.time)) else logs.save(b.copy(status = LogStatus.SKIPPED))
            }
            PostHogAnalytics.capture("v4_study_rollover", mapOf("moved" to moves.size, "dropped" to (missed.size - moves.size)))
        }
    }

    /** Short review blocks 1, 3 and 7 days from today for a subject just studied. */
    fun scheduleReviews(subject: String) {
        viewModelScope.launch {
            val rows = StudyPlanner.REVIEW_DAYS.map { d ->
                newBlock("Review: $subject", today().plus(DatePeriod(days = d)), null, 15, null).copy(notes = "$REVIEW_NOTE$subject")
            }
            plans.planAll(rows, addToCalendar = false)
            PostHogAnalytics.capture("v4_study_reviews", mapOf("count" to rows.size))
        }
    }

    // ── Exams and deadlines ──────────────────────────────────────────────────

    /**
     * Adds an exam or deadline and, with [blocks] over zero, that many study blocks of [minutes]
     * spread over the days before it, avoiding days that already have one.
     */
    fun addDue(title: String, kind: StudyKind, date: LocalDate, blocks: Int, minutes: Int, blockTime: LocalTime?, toCalendar: Boolean) {
        val name = title.trim().ifEmpty { return }
        viewModelScope.launch {
            val due = LifeLog(
                id = Uuid.random().toString(), area = PlanArea.STUDY, kind = LogKind.STUDY, title = name, category = kind.key,
                occurredAt = LocalDateTime(date, WorkoutService.ANY_TIME),
            )
            plans.plan(due, toCalendar, eventTitle = StudyPlanner.dueEvent(due))
            if (blocks > 0) spreadFor(due, blocks, minutes, blockTime, toCalendar)
            enable()
            PostHogAnalytics.capture("v4_study_due_added", mapOf("kind" to kind.key, "blocks" to blocks, "in_days" to (date.toEpochDays() - today().toEpochDays())))
        }
    }

    fun planFor(due: LifeLog, blocks: Int, minutes: Int, blockTime: LocalTime?, toCalendar: Boolean) {
        viewModelScope.launch { spreadFor(due, blocks, minutes, blockTime, toCalendar) }
    }

    private suspend fun spreadFor(due: LifeLog, blocks: Int, minutes: Int, time: LocalTime?, toCalendar: Boolean) {
        val all = logs.getInRange(today(), due.date)
        val busy = all.filter { StudyPlanner.isStudy(it) && StudyPlanner.kindOf(it) == StudyKind.BLOCK && it.status == LogStatus.PLANNED }.map { it.date }.toSet()
        val days = StudyPlanner.spreadBefore(due.date, today(), blocks, busy)
        plans.planAll(days.map { newBlock(due.title, it, time, minutes, due.id) }, toCalendar && time != null) { "Study: ${it.title}" }
    }

    fun toggleDueCalendar(row: DueRow) {
        viewModelScope.launch {
            plans.toggleCalendar(row.log, StudyPlanner.dueEvent(row.log))
            calendarTick.value++
        }
    }

    fun markDueDone(row: DueRow) {
        viewModelScope.launch { plans.setDone(row.log, row.log.status != LogStatus.DONE) }
    }

    // ── Syllabus ─────────────────────────────────────────────────────────────

    val reading = MutableStateFlow(false)
    val found = MutableStateFlow<List<FoundDate>?>(null)
    val readFailed = MutableStateFlow(false)

    fun readSyllabus(text: String) {
        viewModelScope.launch {
            reading.value = true
            readFailed.value = false
            val result = study.readSyllabus(text)
            reading.value = false
            if (result == null) readFailed.value = true else found.value = result
        }
    }

    fun clearFound() {
        found.value = null
        readFailed.value = false
    }

    /** Saves the dates the user kept, each with [blocksEach] study blocks before it (0 for none). */
    fun keepFound(keep: List<FoundDate>, blocksEach: Int, toCalendar: Boolean) {
        viewModelScope.launch {
            keep.forEach { f ->
                val due = LifeLog(
                    id = Uuid.random().toString(), area = PlanArea.STUDY, kind = LogKind.STUDY, title = f.title, category = f.kind.key,
                    occurredAt = LocalDateTime(f.date, WorkoutService.ANY_TIME),
                )
                plans.plan(due, toCalendar, eventTitle = StudyPlanner.dueEvent(due))
                if (blocksEach > 0) spreadFor(due, blocksEach, 45, null, false)
            }
            enable()
            found.value = null
        }
    }

    private fun newBlock(subject: String, date: LocalDate, time: LocalTime?, minutes: Int, forId: String?) = LifeLog(
        id = Uuid.random().toString(), area = PlanArea.STUDY, kind = LogKind.STUDY, title = subject, category = StudyKind.BLOCK.key,
        durationMin = minutes, occurredAt = LocalDateTime(date, time ?: WorkoutService.ANY_TIME), notes = forId,
    )

    private suspend fun enable() {
        val on = planAreas.enabledAreas.value
        if (PlanArea.STUDY !in on) planAreas.setEnabledAreas(on + PlanArea.STUDY)
    }

    companion object {
        const val METRIC_MINUTES = "study_minutes"
        const val REVIEW_NOTE = "review:"
    }
}
