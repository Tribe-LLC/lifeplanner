package az.tribe.lifeplanner.data.plans

import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.data.analytics.PostHogAnalytics
import az.tribe.lifeplanner.data.fitness.WorkoutService
import az.tribe.lifeplanner.data.fitness.WorkoutWeekService
import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.data.money.BillService
import az.tribe.lifeplanner.data.study.StudyService
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.enum.HabitType
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.model.XpRewards
import az.tribe.lifeplanner.domain.repository.GamificationRepository
import az.tribe.lifeplanner.domain.repository.GoalRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.domain.service.BillRepeat
import az.tribe.lifeplanner.domain.service.CatchUpChoice
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.PlanProgress
import az.tribe.lifeplanner.domain.service.PlanScheduler
import az.tribe.lifeplanner.domain.service.PlanSpec
import az.tribe.lifeplanner.domain.service.PlanTemplates
import az.tribe.lifeplanner.domain.service.PlanTrack
import az.tribe.lifeplanner.domain.service.RoutineDraft
import az.tribe.lifeplanner.domain.service.RoutineKind
import az.tribe.lifeplanner.domain.service.Schedule
import az.tribe.lifeplanner.domain.service.WeekSlot
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A step before it is saved: its title, its day, and whether it is already true. */
data class DraftStep(val title: String, val date: LocalDate?, val done: Boolean = false, val minutes: Int? = null)

/** Everything the plan sheet hands over when the user taps Start plan. */
data class PlanDraft(
    val title: String,
    val area: PlanArea,
    val track: PlanTrack,
    val template: String?,
    val start: LocalDate,
    val target: LocalDate,
    val steps: List<DraftStep>,
    val routine: RoutineDraft? = null,
    val targetValue: Double? = null,
    val currency: String? = null,
    val baseline: Double? = null,
    val subject: String? = null,
    /** Where it came from, for analytics: "area", "add_anything", "idea", "next", "coach". */
    val source: String = "area",
    val suggestedByCoach: Boolean = false,
)

/** Something worth telling the user about a plan, wherever they are: a plan finished by a run. */
data class PlanEvent(val goalId: String, val finished: Boolean, val text: String)

/**
 * Everything that changes a v4 plan: making it (goal, dated steps, settings and routine in one go),
 * ticking, finishing, letting go, pausing, a new date, catching up, money put aside and +1s. The
 * screens only say what the user chose; the dates and the bookkeeping happen here.
 */
@OptIn(ExperimentalUuidApi::class)
class PlanMaker(
    private val goals: GoalRepository,
    private val specs: PlanSpecs,
    private val board: PlanBoard,
    private val habits: HabitService,
    private val habitRepo: HabitRepository,
    private val study: StudyService,
    private val week: WorkoutWeekService,
    private val workouts: WorkoutService,
    private val bills: BillService,
    private val logs: LifeLogRepository,
    private val gamification: GamificationRepository,
    private val planAreas: PlanAreasRepository,
) {
    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)
    private fun now() = Clock.System.now().toLocalDateTime(tz)

    private val _events = MutableSharedFlow<PlanEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PlanEvent> = _events.asSharedFlow()

    // ── Making ───────────────────────────────────────────────────────────────

    suspend fun create(d: PlanDraft): String {
        val id = Uuid.random().toString()
        val steps = d.steps.filter { it.title.isNotBlank() }.map { s ->
            Milestone(Uuid.random().toString(), s.title.trim(), isCompleted = s.done, dueDate = s.date, estimatedEffort = s.minutes)
        }
        val done = steps.count { it.isCompleted }
        goals.insertGoal(
            Goal(
                id = id, category = PlanSpec.categoryFor(d.area), title = d.title.trim(), description = "",
                status = if (done > 0) GoalStatus.IN_PROGRESS else GoalStatus.NOT_STARTED,
                timeline = timelineFor(d.start, d.target), dueDate = d.target,
                progress = if (steps.isEmpty()) 0 else (done * 100L / steps.size), milestones = steps, createdAt = now(),
                predictedDueDate = d.target,
            ),
        )
        var spec = PlanSpec(
            goalId = id, area = d.area, track = d.track, start = d.start, target = d.targetValue, currency = d.currency,
            template = d.template, subject = d.subject, baseline = d.baseline,
        )
        specs.save(spec)
        d.routine?.let { r ->
            runCatching { makeRoutine(r, id, d) }.onFailure { Logger.w("PlanMaker") { "Routine failed: ${it.message}" } }.getOrNull()?.let { (kind, rid) ->
                spec = spec.copy(routineKind = kind, routineId = rid)
                specs.save(spec)
            }
        }
        runCatching { gamification.awardXp(XpRewards.GOAL_CREATED.toLong()) }
        val enabled = planAreas.enabledAreas.value
        if (d.area !in enabled) planAreas.setEnabledAreas(enabled + d.area)
        PostHogAnalytics.capture(
            "v4_plan_created",
            mapOf(
                "template" to (d.template ?: "none"), "area" to d.area.key, "track" to d.track.key, "source" to d.source,
                "steps" to steps.size, "routine" to (d.routine?.kind?.key ?: "none"), "coach" to d.suggestedByCoach,
                "weeks" to PlanScheduler.days(d.start, d.target) / 7,
            ),
        )
        return id
    }

    private suspend fun makeRoutine(r: RoutineDraft, goalId: String, d: PlanDraft): Pair<RoutineKind, String>? = when (r.kind) {
        RoutineKind.FITNESS_WEEK -> {
            val current = week.currentWeek()
            val taken = current?.slots.orEmpty().map { it.day }.toSet()
            // Days already in the week keep what is there; a run goes on the next free day instead.
            val days = r.days.map { day -> if (day !in taken) day else DayOfWeek.entries.firstOrNull { it !in taken && it !in r.days } ?: day }.toSet()
            val added = days.filter { it !in taken }.map { WeekSlot(it, r.title, r.time, r.minutes ?: 30) }
            if (added.isNotEmpty()) week.save(current?.slots.orEmpty() + added, current?.toCalendar ?: false)
            RoutineKind.FITNESS_WEEK to days.sortedBy { it.ordinal }.joinToString(",") { it.name.take(3) }
        }
        RoutineKind.STUDY_REPEAT -> study.addRepeat(r.title, r.days, r.time, r.minutes ?: 30, false)?.let { RoutineKind.STUDY_REPEAT to it }
        RoutineKind.HABIT -> {
            val habit = habits.create(
                title = r.title, type = HabitType.BUILD, schedule = r.perWeek?.let { Schedule.PerWeek(it) } ?: Schedule.Daily,
                target = 1, unit = null, reminder = r.time, category = PlanSpec.categoryFor(d.area),
            )
            habitRepo.updateHabit(habit.copy(linkedGoalId = goalId))
            RoutineKind.HABIT to habit.id
        }
        RoutineKind.MONTHLY -> {
            val day = r.dayOfMonth ?: 25
            val today = today()
            val due = LocalDate(today.year, today.month, day.coerceAtMost(28)).let { if (it < today) it.plus(DatePeriod(months = 1)) else it }
            val bill = bills.save(null, r.title, r.amount ?: 0.0, d.currency ?: "EUR", BillRepeat.MONTHLY, due, category = PlanProgress.SAVINGS)
            RoutineKind.MONTHLY to bill.id
        }
    }

    // ── Ticking and finishing ────────────────────────────────────────────────

    /**
     * Ticks a step or unticks it. [byData] is a tick from logged data, with [evidence] saying what
     * ticked it; a manual untick is remembered so the same old data does not tick it straight back.
     * Ticking the last step finishes the plan; returns true when that happened. With [announce], or
     * for any tick from data, a short line goes out on [events] saying what comes next.
     */
    suspend fun setStep(goalId: String, milestoneId: String, done: Boolean, byData: Boolean = false, announce: Boolean = false, evidence: String? = null): Boolean {
        goals.toggleMilestoneCompletion(milestoneId, done)
        if (done) board.clearUntick(milestoneId) else if (!byData) board.untick(milestoneId)
        if (done) {
            runCatching { gamification.awardXp(XpRewards.MILESTONE_COMPLETED.toLong()) }
            Analytics.milestoneCompleted(goalId, milestoneId)
        }
        PostHogAnalytics.capture("v4_plan_step", mapOf("done" to done, "by" to if (byData) "data" else "hand"))
        val goal = goals.getGoalById(goalId) ?: return false
        val total = goal.milestones.size
        val doneCount = goal.milestones.count { it.isCompleted }
        if (total > 0) goals.updateProgress(goalId, doneCount * 100 / total)
        val finished = when {
            done && total > 0 && doneCount == total && goal.status != GoalStatus.COMPLETED -> { finish(goal); true }
            !done && goal.status == GoalStatus.COMPLETED -> { reopen(goal); false }
            done && goal.status == GoalStatus.NOT_STARTED -> { goals.updateGoal(goal.copy(status = GoalStatus.IN_PROGRESS)); false }
            else -> false
        }
        if (!finished && (announce || byData)) stepLine(goal, milestoneId, done, byData, evidence)?.let { _events.tryEmit(PlanEvent(goalId, false, it)) }
        return finished
    }

    /** "Nice. Next step: Run 1 km, Sun 11 Oct. It ticks itself from your runs." */
    private suspend fun stepLine(goal: Goal, milestoneId: String, done: Boolean, byData: Boolean, evidence: String?): String? {
        val m = goal.milestones.firstOrNull { it.id == milestoneId } ?: return null
        val track = specs.get(goal.id)?.track ?: PlanTrack.CHECKLIST
        if (!done) {
            return if (PlanProgress.threshold(m.title, track) != null) "Un-ticked. It ticks again from something new you log." else null
        }
        if (byData) return "${m.title}: ${evidence?.replaceFirstChar { it.lowercase() } ?: "ticked from what you logged"}."
        val next = goal.milestones.filter { !it.isCompleted }.sortedWith(compareBy({ it.dueDate == null }, { it.dueDate })).firstOrNull()
            ?: return "Nice. That was the last step left."
        val auto = PlanProgress.threshold(next.title, track) != null && track != PlanTrack.STUDY
        return "Nice. Next step: ${next.title}" + (next.dueDate?.let { ", ${PlanScheduler.dayLabel(it)}" } ?: "") + "." +
            if (auto) " It ticks itself from what you log." else ""
    }

    private suspend fun finish(goal: Goal) {
        goals.updateGoal(goal.copy(status = GoalStatus.COMPLETED, progress = 100))
        specs.get(goal.id)?.let { specs.save(it.copy(finished = today(), pausedFrom = null, pausedUntil = null)) }
        runCatching { gamification.awardXp(XpRewards.GOAL_COMPLETED.toLong()) }
        Analytics.goalCompleted(goal.id, goal.category.name, PlanScheduler.days(goal.createdAt.date, today()))
        PostHogAnalytics.capture("v4_plan_finished", mapOf("template" to (specs.get(goal.id)?.template ?: "none"), "late" to (today() > goal.dueDate)))
        _events.tryEmit(PlanEvent(goal.id, true, "You finished ${goal.title}. Open it for the recap."))
    }

    private suspend fun reopen(goal: Goal) {
        goals.updateGoal(goal.copy(status = GoalStatus.IN_PROGRESS))
        specs.get(goal.id)?.let { specs.save(it.copy(finished = null)) }
    }

    // ── Letting go and pausing ───────────────────────────────────────────────

    suspend fun letGo(goalId: String) {
        goals.archiveGoal(goalId)
        PostHogAnalytics.capture("v4_plan_let_go", mapOf("progress" to (goals.getGoalById(goalId)?.progress ?: 0)))
    }

    /** Brings a plan back. Steps that went by while it was away are spread again from tomorrow. */
    suspend fun bringBack(goalId: String) {
        goals.unarchiveGoal(goalId)
        val goal = goals.getGoalById(goalId) ?: return
        val today = today()
        val left = goal.milestones.filter { !it.isCompleted }
        if (left.any { it.dueDate != null && it.dueDate < today } || goal.dueDate < today) {
            val target = maxOf(goal.dueDate, today.plus(DatePeriod(days = 7 * left.size.coerceAtLeast(1))))
            redate(goal, target)
        }
        PostHogAnalytics.capture("v4_plan_brought_back")
    }

    /** Pauses for [days] days, or until resumed when null. A fixed pause moves every date on at once. */
    suspend fun pause(goalId: String, days: Int?) {
        val spec = specs.get(goalId) ?: return
        val goal = goals.getGoalById(goalId) ?: return
        val today = today()
        specs.save(spec.copy(pausedFrom = today, pausedUntil = days?.let { today.plus(DatePeriod(days = it - 1)) }))
        if (days != null) {
            shift(goal, days)
            routineHabit(spec)?.let { habits.takeBreak(it, days) }
        }
        PostHogAnalytics.capture("v4_plan_paused", mapOf("days" to (days ?: 0)))
    }

    suspend fun resume(goalId: String) {
        val spec = specs.get(goalId) ?: return
        val goal = goals.getGoalById(goalId) ?: return
        val today = today()
        val from = spec.pausedFrom ?: return
        val until = spec.pausedUntil
        if (until == null) shift(goal, PlanScheduler.days(from, today))
        else {
            val unused = PlanScheduler.unusedPause(until, today)
            if (unused > 0) shift(goal, -unused)
            routineHabit(spec)?.let { habits.endBreak(it) }
        }
        specs.save(spec.copy(pausedFrom = null, pausedUntil = null))
        PostHogAnalytics.capture("v4_plan_resumed", mapOf("early" to (until != null && until >= today)))
    }

    private suspend fun shift(goal: Goal, by: Int) {
        if (by == 0) return
        val today = today()
        val moved = goal.milestones.map { m -> if (m.isCompleted) m else m.copy(dueDate = PlanScheduler.shift(listOf(m.dueDate), by, today).first()) }
        goals.updateGoal(goal.copy(dueDate = goal.dueDate.plus(DatePeriod(days = by)).coerceAtLeast(today), milestones = moved))
    }

    private suspend fun routineHabit(spec: PlanSpec) =
        spec.takeIf { it.routineKind == RoutineKind.HABIT }?.routineId?.let { habitRepo.getHabitById(it) }

    // ── Dates ────────────────────────────────────────────────────────────────

    /** A new finish date: the steps left spread out again to fit it, done ones stay. Returns the goal before, for Undo. */
    suspend fun changeDate(goalId: String, target: LocalDate): Goal? {
        val goal = goals.getGoalById(goalId) ?: return null
        redate(goal, target)
        PostHogAnalytics.capture("v4_plan_date_changed", mapOf("days" to PlanScheduler.days(goal.dueDate, target)))
        return goal
    }

    /** Puts a plan back exactly as [before] was, dates and all. */
    suspend fun restore(before: Goal) = goals.updateGoal(before.copy(milestones = before.milestones))

    private suspend fun redate(goal: Goal, target: LocalDate) {
        val left = goal.milestones.filter { !it.isCompleted }.sortedWith(compareBy({ it.dueDate == null }, { it.dueDate }))
        val dates = PlanScheduler.redate(left.size, today(), target).zip(left).associate { (d, m) -> m.id to d }
        goals.updateGoal(
            goal.copy(
                dueDate = target, timeline = timelineFor(goal.createdAt.date, target),
                milestones = goal.milestones.map { m -> dates[m.id]?.let { m.copy(dueDate = it) } ?: m },
            ),
        )
    }

    /**
     * What the user picked on the catch-up card or the coach line. Returns the line to show back:
     * "Done. New finish Tue 8 Dec, and the next steps moved with it."
     */
    suspend fun catchUp(goalId: String, choice: CatchUpChoice, from: String): String? {
        val view = board.plan(goalId).first() ?: return null
        val spec = view.spec ?: PlanSpec(goalId = goalId, area = view.area, start = view.start)
        val today = today()
        PostHogAnalytics.capture("v4_plan_catch_up", mapOf("choice" to choice.name.lowercase(), "from" to from, "behind" to view.pace.behindDays))
        return when (choice) {
            CatchUpChoice.PUSH -> {
                val to = view.catchUp?.pushTo ?: view.target.plus(DatePeriod(days = 7))
                redate(view.goal, to)
                specs.save(spec.copy(asked = null, keptOn = null))
                "Done. New finish ${PlanScheduler.dayLabel(to)}, and the steps left moved with it."
            }
            CatchUpChoice.KEEP -> {
                redate(view.goal, view.target)
                specs.save(spec.copy(asked = null, keptOn = today))
                val extra = if (spec.track == PlanTrack.RUN && spec.routineKind == RoutineKind.FITNESS_WEEK) extraRun() else null
                "Kept ${PlanScheduler.dayLabel(view.target)}. The steps are closer together" + (extra?.let { ", and an extra easy run is on ${FitnessWeek.dayName(it.dayOfWeek)}." } ?: ".")
            }
            CatchUpChoice.LEAVE -> {
                specs.save(spec.copy(asked = today))
                "Okay, left as it is. We will ask again in a week if it still slips."
            }
        }
    }

    /** One more easy run on the next day this week with nothing planned. */
    private suspend fun extraRun(): LocalDate? {
        val today = today()
        val busy = logs.getInRange(today, today.plus(DatePeriod(days = 6))).filter { FitnessWeek.isWorkout(it) }.map { it.date }.toSet()
        val day = (1..6).map { today.plus(DatePeriod(days = it)) }.firstOrNull { it !in busy } ?: return null
        workouts.plan("Easy run", day, LocalTime(7, 0), 30, false)
        return day
    }

    // ── Money, counts and steps ──────────────────────────────────────────────

    /** Money put aside (or paid off) for a plan: a savings row, never a spend. */
    suspend fun putAside(goalId: String, amount: Double, currency: String?) {
        if (amount <= 0) return
        val spec = specs.get(goalId)
        val goal = goals.getGoalById(goalId) ?: return
        val debt = spec?.template == PlanTemplates.DEBT
        logs.save(
            LifeLog(
                id = Uuid.random().toString(), area = PlanArea.MONEY, kind = LogKind.EXPENSE,
                title = if (debt) "Paid off, ${goal.title}" else "Put aside" + (spec?.subject?.let { " for $it" } ?: ", ${goal.title}"),
                amount = amount, currency = currency ?: spec?.currency, category = PlanProgress.SAVINGS,
                occurredAt = now(), externalId = goalId,
            ),
        )
        PostHogAnalytics.capture("v4_plan_put_aside", mapOf("debt" to debt))
    }

    suspend fun addOne(goalId: String) {
        val spec = specs.get(goalId) ?: return
        specs.save(spec.copy(count = spec.count + 1))
        PostHogAnalytics.capture("v4_plan_plus_one")
    }

    /** A step added on the plan's page. With no day given it goes just before the last one. */
    suspend fun addStep(goalId: String, title: String, date: LocalDate? = null) {
        val goal = goals.getGoalById(goalId) ?: return
        val day = date ?: newStepDate(goal, today())
        goals.addMilestone(goalId, Milestone(Uuid.random().toString(), title.trim(), dueDate = day))
        if (goal.status == GoalStatus.COMPLETED) reopen(goal)
        PostHogAnalytics.capture("v4_plan_step_added")
    }

    suspend fun rename(goalId: String, title: String) {
        val goal = goals.getGoalById(goalId) ?: return
        title.trim().takeIf { it.isNotEmpty() && it != goal.title }?.let { goals.updateGoal(goal.copy(title = it)) }
    }

    suspend fun editStep(step: Milestone, title: String, date: LocalDate?) =
        goals.updateMilestone(step.copy(title = title.trim().ifEmpty { step.title }, dueDate = date))

    suspend fun removeStep(step: Milestone) = goals.deleteMilestone(step.id)

    /** Removes the plan for good. Its routine stays: it may be worth keeping on its own. */
    suspend fun delete(goalId: String) {
        goals.deleteGoalById(goalId)
        specs.remove(goalId)
        PostHogAnalytics.capture("v4_plan_deleted")
    }

    companion object {
        /** Where a new step lands when no day is picked: between the last two steps left, so the last stays last. */
        fun newStepDate(goal: Goal, today: LocalDate): LocalDate {
            val left = goal.milestones.filter { !it.isCompleted }.mapNotNull { it.dueDate }.sorted()
            return when {
                left.size >= 2 -> LocalDate.fromEpochDays(((left[left.size - 2].toEpochDays() + left.last().toEpochDays()) / 2).toInt())
                left.size == 1 -> LocalDate.fromEpochDays(((today.toEpochDays() + left.last().toEpochDays()) / 2).toInt())
                else -> goal.dueDate
            }.coerceAtLeast(today.plus(DatePeriod(days = 1)))
        }

        fun timelineFor(start: LocalDate, target: LocalDate): GoalTimeline = when (PlanScheduler.days(start, target)) {
            in Int.MIN_VALUE..92 -> GoalTimeline.SHORT_TERM
            in 93..275 -> GoalTimeline.MID_TERM
            else -> GoalTimeline.LONG_TERM
        }
    }
}
