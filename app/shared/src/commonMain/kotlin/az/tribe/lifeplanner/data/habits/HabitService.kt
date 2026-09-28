package az.tribe.lifeplanner.data.habits

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitCompletionSource
import az.tribe.lifeplanner.domain.enum.HabitType
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.model.Reminder
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import az.tribe.lifeplanner.domain.model.ReminderType
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.HabitRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.ReminderRepository
import az.tribe.lifeplanner.domain.service.HabitLearning
import az.tribe.lifeplanner.domain.service.HabitRules
import az.tribe.lifeplanner.domain.service.HabitSchedule
import az.tribe.lifeplanner.domain.service.Schedule
import az.tribe.lifeplanner.domain.service.StreakPauses
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
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

/** One habit as the Habits page and Today see it: schedule, today, and how it is going. */
data class HabitRow(
    val habit: Habit,
    val schedule: Schedule,
    val stats: HabitSchedule.Stats,
    val doneToday: Boolean,
    val countToday: Int,
    val done: Set<LocalDate>,
    val skipped: Set<LocalDate>,
    val notes: List<LifeLog>,
    /** The minute of the day this habit usually gets ticked, once there are enough ticks. */
    val usualMinute: Int? = null,
    /** Set when the habit has quietly stopped and should come up for review. */
    val slip: HabitLearning.Slip? = null,
    /** Weekdays it really happens on, when clearly fewer than it is set for. */
    val learnedDays: Set<kotlinx.datetime.DayOfWeek>? = null,
) {
    val reminderMinute: Int? get() = habit.reminderTime?.let { t ->
        val h = t.substringBefore(':').trim().toIntOrNull() ?: return@let null
        h * 60 + (t.substringAfter(':', "0").take(2).toIntOrNull() ?: 0)
    }

    /** Where it sits in the day: its reminder if it has one, else when it usually happens. */
    val slot: HabitSchedule.Slot get() = when {
        habit.reminderTime != null -> HabitSchedule.slot(habit.reminderTime)
        usualMinute != null -> HabitLearning.slotOf(usualMinute)
        else -> HabitSchedule.Slot.ANYTIME
    }

    /** For ordering inside a slot. */
    val minute: Int get() = reminderMinute ?: usualMinute ?: (24 * 60)

    val meta: String get() = when {
        habit.targetCount > 1 && !stats.skippedToday -> "${if (doneToday) habit.targetCount else countToday} of ${habit.targetCount}${habit.unit?.let { " $it" } ?: ""}"
        habit.healthMetricType != null && !doneToday -> "Ticks itself from Health"
        habit.type == HabitType.QUIT && !stats.skippedToday -> (if (stats.streak >= 2) "To break. ${stats.streak} days strong" else "To break")
        else -> HabitSchedule.meta(schedule, stats, doneToday)
    }
}

/**
 * Everything about habits that v3 did not have: schedules, skip days and breaks, notes on a day,
 * fixing past days, and reminders that follow the schedule. Rows live in the synced tables that
 * already exist (budgets for the schedule, life logs for skips and notes), so nothing new syncs.
 */
@OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)
class HabitService(
    private val habits: HabitRepository,
    private val logs: LifeLogRepository,
    private val budgets: BudgetRepository,
    private val reminders: ReminderRepository,
    private val pauses: StreakPauses,
) {
    private val tz = TimeZone.currentSystemDefault()
    private fun today() = Clock.System.todayIn(tz)
    private fun now() = Clock.System.now().toLocalDateTime(tz)

    /** Bumped after edits the habit flow does not see, like fixing a past day. */
    private val tick = MutableStateFlow(0)

    val rows: Flow<List<HabitRow>> = combine(
        habits.observeHabitsWithTodayStatus(),
        logs.observeInRange(today().minus(DatePeriod(days = HISTORY_DAYS)), today().plus(DatePeriod(days = 60))),
        budgets.observeAll(),
        tick,
    ) { hs, ls, bs, _ -> Triple(hs, ls, bs) }.mapLatest { (hs, ls, bs) ->
        val today = today()
        val checkIns = runCatching { habits.getAllCheckInsInRange(today.minus(DatePeriod(days = HISTORY_DAYS)), today) }.getOrDefault(emptyList())
        val doneBy = checkIns.filter { it.completed }.groupBy { it.habitId }.mapValues { (_, v) -> v.map { it.date }.toSet() }
        val countToday = checkIns.filter { it.date == today }.associate { it.habitId to it.count }
        val trip = runCatching { pauses.pausedDays() }.getOrDefault(emptySet())
        val skipsBy = ls.filter { it.area == PlanArea.HABITS && it.category == HabitSchedule.SKIP }.groupBy { it.externalId }
        val notesBy = ls.filter { it.area == PlanArea.HABITS && it.category == HabitSchedule.NOTE }.groupBy { it.externalId }
        val reviewedBy = ls.filter { it.area == PlanArea.HABITS && it.category == HabitLearning.REVIEW }
            .groupBy { it.externalId }.mapValues { (_, v) -> v.maxOf { it.date } }
        val ticksBy = checkIns.filter { it.completed && it.checkedAt != null }.groupBy { it.habitId }
            .mapValues { (_, v) -> v.map { it.date to it.checkedAt!!.toLocalDateTime(tz) } }
        hs.filter { (h, _) -> h.isActive }.map { (h, doneToday) ->
            val schedule = HabitSchedule.of(h, bs)
            val skipped = skipsBy[h.id].orEmpty().map { it.date }.toSet()
            val done = doneBy[h.id].orEmpty() + if (doneToday) setOf(today) else emptySet()
            val since = h.createdAt.date
            HabitRow(
                habit = h,
                schedule = schedule,
                stats = HabitSchedule.stats(schedule, done, skipped + trip, today, h.createdAt.date),
                doneToday = doneToday,
                countToday = countToday[h.id] ?: 0,
                done = done,
                skipped = skipped,
                notes = notesBy[h.id].orEmpty().sortedByDescending { it.occurredAt },
                usualMinute = HabitLearning.usualMinute(ticksBy[h.id].orEmpty()),
                // Health and in-app sessions tick these by themselves, so a miss there is not the user's.
                slip = if (h.healthMetricType != null || h.completionSource != HabitCompletionSource.MANUAL) null
                    else HabitLearning.slip(schedule, done, skipped, trip, today, since, reviewedBy[h.id]),
                learnedDays = HabitLearning.learnedDays(schedule, done, skipped + trip, today, since),
            )
        }
    }

    /** For the repository's stored streak. */
    suspend fun rulesFor(habit: Habit): HabitRules {
        val today = today()
        val skips = runCatching { logs.getInRange(today.minus(DatePeriod(days = HISTORY_DAYS)), today.plus(DatePeriod(days = 60))) }.getOrDefault(emptyList())
            .filter { it.area == PlanArea.HABITS && it.category == HabitSchedule.SKIP && it.externalId == habit.id }.map { it.date }
        val trip = runCatching { pauses.pausedDays() }.getOrDefault(emptySet())
        return HabitRules(HabitSchedule.of(habit, runCatching { budgets.getAll() }.getOrDefault(emptyList())), skips.toSet() + trip)
    }

    // ── Creating and editing ─────────────────────────────────────────────────

    suspend fun create(
        title: String,
        type: HabitType,
        schedule: Schedule,
        target: Int,
        unit: String?,
        reminder: LocalTime?,
        healthMetric: HealthMetricType? = null,
        healthTarget: Double? = null,
        source: HabitCompletionSource = HabitCompletionSource.MANUAL,
    ): Habit {
        val habit = Habit(
            id = Uuid.random().toString(),
            title = title.trim(),
            category = GoalCategory.WELLBEING,
            frequency = HabitSchedule.frequencyFor(schedule),
            targetCount = target.coerceAtLeast(1),
            unit = unit?.trim()?.takeIf { it.isNotEmpty() && target > 1 },
            createdAt = now(),
            reminderTime = reminder?.let(::fmt),
            type = type,
            healthMetricType = healthMetric,
            healthTarget = healthTarget,
            completionSource = source,
        )
        habits.insertHabit(habit)
        saveSchedule(habit, schedule)
        syncReminder(habit, schedule)
        return habit
    }

    suspend fun update(habit: Habit, title: String, schedule: Schedule, target: Int, unit: String?, reminder: LocalTime?) {
        val updated = habit.copy(
            title = title.trim().ifEmpty { habit.title },
            frequency = HabitSchedule.frequencyFor(schedule),
            targetCount = target.coerceAtLeast(1),
            unit = unit?.trim()?.takeIf { it.isNotEmpty() && target > 1 },
            reminderTime = reminder?.let(::fmt),
        )
        habits.updateHabit(updated)
        saveSchedule(updated, schedule)
        syncReminder(updated, schedule)
        tick.value++
    }

    private suspend fun saveSchedule(habit: Habit, schedule: Schedule) {
        val mine = budgets.getAll().filter { it.area == PlanArea.HABITS && it.category == habit.id }
        mine.forEach { budgets.delete(it.id) }
        val (metric, amount) = when (val s = HabitSchedule.normal(schedule)) {
            Schedule.Daily -> return
            is Schedule.Days -> HabitSchedule.METRIC_DAYS to HabitSchedule.mask(s.days).toDouble()
            is Schedule.PerWeek -> HabitSchedule.METRIC_WEEK to s.times.toDouble()
        }
        budgets.save(Budget(Uuid.random().toString(), PlanArea.HABITS, metric, habit.id, amount, null, BudgetPeriod.WEEK))
    }

    /** One reminder per habit, on its days. Times-a-week habits are reminded daily until done. */
    private suspend fun syncReminder(habit: Habit, schedule: Schedule) {
        val existing = runCatching { reminders.getRemindersByHabit(habit.id) }.getOrDefault(emptyList())
        val time = habit.reminderTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        if (time == null) {
            existing.forEach { runCatching { reminders.deleteReminder(it.id) } }
            return
        }
        val days = (HabitSchedule.normal(schedule) as? Schedule.Days)?.days
        val frequency = if (days == null) ReminderFrequency.DAILY else ReminderFrequency.WEEKLY
        val reminderDays = days.orEmpty().map { az.tribe.lifeplanner.domain.model.DayOfWeek.entries[it.ordinal] }.sortedBy { it.ordinal }
        val first = existing.firstOrNull()
        if (first != null) {
            existing.drop(1).forEach { runCatching { reminders.deleteReminder(it.id) } }
            reminders.updateReminder(first.copy(title = habit.title, frequency = frequency, scheduledTime = time, scheduledDays = reminderDays, isEnabled = true))
        } else {
            reminders.createReminder(
                Reminder(
                    id = Uuid.random().toString(),
                    title = habit.title,
                    message = if (habit.type == HabitType.QUIT) "Still going strong?" else "Time for ${habit.title.lowercase()}",
                    type = ReminderType.HABIT_REMINDER,
                    frequency = frequency,
                    scheduledTime = time,
                    scheduledDays = reminderDays,
                    linkedHabitId = habit.id,
                    createdAt = now(),
                )
            )
        }
    }

    /** Stops showing and reminding; the history stays. */
    suspend fun stop(habit: Habit) {
        habits.deactivateHabit(habit.id)
        runCatching { reminders.getRemindersByHabit(habit.id) }.getOrDefault(emptyList()).forEach { runCatching { reminders.deleteReminder(it.id) } }
    }

    // ── Days ────────────────────────────────────────────────────────────────

    /** Ticks or unticks a past day, for when the tap was forgotten. Future days cannot be ticked. */
    suspend fun setDone(habit: Habit, date: LocalDate, done: Boolean) {
        if (date > today()) return
        if (done) {
            habits.checkIn(habit.id, date)
        } else {
            habits.getCheckInByHabitAndDate(habit.id, date)?.let { habits.deleteCheckIn(it.id) }
        }
        tick.value++
    }

    /** A neutral day: the habit leaves Today and the streak waits. Skipping twice un-skips. */
    suspend fun toggleSkip(habit: Habit, date: LocalDate = today()) {
        val existing = skipsFor(habit, date, date)
        if (existing.isNotEmpty()) existing.forEach { logs.delete(it.id) } else logs.save(skipRow(habit, date))
    }

    /** Skips the next [days] days, today included. */
    suspend fun takeBreak(habit: Habit, days: Int) {
        val today = today()
        val have = skipsFor(habit, today, today.plus(DatePeriod(days = days))).map { it.date }.toSet()
        logs.saveAll((0 until days).map { today.plus(DatePeriod(days = it)) }.filter { it !in have }.map { skipRow(habit, it) })
    }

    /** Ends a break early: skips from today on are removed. */
    suspend fun endBreak(habit: Habit) {
        val today = today()
        skipsFor(habit, today, today.plus(DatePeriod(days = 60))).forEach { logs.delete(it.id) }
    }

    suspend fun addNote(habit: Habit, text: String) {
        if (text.isBlank()) return
        logs.save(
            LifeLog(
                id = Uuid.random().toString(), area = PlanArea.HABITS, kind = LogKind.NOTE, title = text.trim(),
                category = HabitSchedule.NOTE, occurredAt = now(), externalId = habit.id,
            )
        )
    }

    // ── Review ───────────────────────────────────────────────────────────────

    /** Ways to make a slipped habit lighter, best first. */
    sealed interface Easier {
        val title: String
        val detail: String
        data class OnlyDays(val days: Set<kotlinx.datetime.DayOfWeek>, override val title: String, override val detail: String) : Easier
        data class TimesAWeek(val times: Int, override val title: String, override val detail: String) : Easier
        data class Smaller(val target: Int, override val title: String, override val detail: String) : Easier
        data class Pause(val days: Int, override val title: String, override val detail: String) : Easier
    }

    fun easierOptions(r: HabitRow): List<Easier> {
        val out = mutableListOf<Easier>()
        val schedule = HabitSchedule.normal(r.schedule)
        r.learnedDays?.let { days ->
            out += Easier.OnlyDays(days, "Only on ${HabitSchedule.describe(Schedule.Days(days)).let { if (it == "Weekdays" || it == "Weekends") it.lowercase() else it }}", "The days you already do it. Other days stop counting")
        }
        if (schedule !is Schedule.PerWeek) {
            val times = if (schedule is Schedule.Days) (schedule.days.size - 1).coerceAtLeast(1) else 3
            out += Easier.TimesAWeek(times, "$times times a week", "Any days you like")
        } else if (schedule.times > 1) {
            out += Easier.TimesAWeek(schedule.times - 1, "${schedule.times - 1} times a week", "One fewer than now")
        }
        if (r.habit.targetCount > 1) {
            val half = (r.habit.targetCount / 2).coerceAtLeast(1)
            out += Easier.Smaller(half, "$half ${r.habit.unit ?: "instead of ${r.habit.targetCount}"}".trim(), "Same habit, a smaller start")
        }
        val back = today().plus(DatePeriod(days = 14))
        out += Easier.Pause(14, "Pause for 2 weeks", "Back on ${back.day} ${back.month.name.lowercase().replaceFirstChar { it.uppercase() }}, the streak waits")
        return out.take(3)
    }

    suspend fun keep(habit: Habit) = review(habit, "kept")

    /** Stops the habit and remembers why. Its history stays under Habits. */
    suspend fun letGo(habit: Habit) {
        stop(habit)
        review(habit, "let go")
    }

    suspend fun makeEasier(r: HabitRow, how: Easier) {
        val h = r.habit
        val time = h.reminderTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        when (how) {
            is Easier.OnlyDays -> update(h, h.title, Schedule.Days(how.days), h.targetCount, h.unit, time)
            is Easier.TimesAWeek -> update(h, h.title, Schedule.PerWeek(how.times), h.targetCount, h.unit, time)
            is Easier.Smaller -> update(h, h.title, r.schedule, how.target, h.unit, time)
            is Easier.Pause -> takeBreak(h, how.days)
        }
        review(h, "easier: ${how.title}")
    }

    /** Moves the reminder to when the habit really happens. */
    suspend fun moveReminder(r: HabitRow, minute: Int) {
        val h = r.habit
        update(h, h.title, r.schedule, h.targetCount, h.unit, LocalTime(minute / 60, minute % 60))
    }

    private suspend fun review(habit: Habit, decision: String) {
        logs.save(
            LifeLog(
                id = Uuid.random().toString(), area = PlanArea.HABITS, kind = LogKind.NOTE, status = LogStatus.DONE,
                title = "Reviewed ${habit.title}: $decision", category = HabitLearning.REVIEW, occurredAt = now(), externalId = habit.id,
            )
        )
        tick.value++
    }

    private suspend fun skipsFor(habit: Habit, from: LocalDate, to: LocalDate) =
        logs.getInRange(from, to).filter { it.area == PlanArea.HABITS && it.category == HabitSchedule.SKIP && it.externalId == habit.id }

    private fun skipRow(habit: Habit, date: LocalDate) = LifeLog(
        id = Uuid.random().toString(), area = PlanArea.HABITS, kind = LogKind.NOTE, status = LogStatus.SKIPPED,
        title = "Skipped: ${habit.title}", category = HabitSchedule.SKIP, occurredAt = LocalDateTime(date, LocalTime(0, 0)),
        externalId = habit.id,
    )

    companion object {
        const val HISTORY_DAYS = 400

        fun fmt(t: LocalTime) = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    }
}
