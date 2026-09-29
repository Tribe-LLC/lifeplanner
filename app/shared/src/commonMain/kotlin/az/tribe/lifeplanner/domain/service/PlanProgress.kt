package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.health.WorkoutKind
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.Milestone
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.math.roundToInt

/** Where a step's tick came from: "Ticked from your run, Sat 10 Oct". */
data class Evidence(val at: LocalDateTime, val text: String)

/** What a plan can read its progress from. Each list may hold more than the plan needs; it filters. */
data class PlanInputs(
    val logs: List<LifeLog> = emptyList(),
    val focus: List<StudyTime> = emptyList(),
    /** Weight readings from Health, oldest first. */
    val weights: List<Pair<LocalDate, Double>> = emptyList(),
    /** The days the plan's routine habit was done. */
    val habitDays: Set<LocalDate> = emptySet(),
)

data class PlanStat(val label: String, val value: String)

data class PlanProgressResult(
    val fraction: Float,
    /** "3 of 6 steps", "€600 of €2,000". */
    val headline: String,
    val stats: List<PlanStat>,
    /** "Moves with your runs. Best so far 2.6 km." */
    val source: String?,
    /** Steps the data says are done, by milestone id. */
    val ticks: Map<String, Evidence>,
    /** The measured number: km, money, minutes, kg down, applications, count. */
    val value: Double?,
)

/** What a step's title asks for, read from the words so it still works after a rename. */
data class Threshold(
    val km: Double? = null,
    val minutes: Int? = null,
    val amount: Double? = null,
    val kg: Double? = null,
    val count: Int? = null,
    val interview: Boolean = false,
)

/**
 * A plan's progress from what the user already logs: runs, money put aside, study time, weight,
 * applications, a count. Steps that name a number ("Run 3 km", "€1,000 put aside") tick
 * themselves when the data gets there, whatever the step has been renamed to around the number.
 */
object PlanProgress {

    /** A LifeLog category: money put aside for a plan, not spent. */
    const val SAVINGS = "savings"

    private val kmRe = Regex("""(\d+(?:[.,]\d+)?)\s?(?:k|km|kms)\b""", RegexOption.IGNORE_CASE)
    private val minRe = Regex("""(\d+)\s?(?:min|mins|minutes)\b""", RegexOption.IGNORE_CASE)
    private val numberRe = Regex("""(\d[\d,]*(?:\.\d+)?)""")
    private val kgRe = Regex("""(\d+(?:[.,]\d+)?)\s?kg\b""", RegexOption.IGNORE_CASE)
    private val appsRe = Regex("""(\d+)\s+applications?\b""", RegexOption.IGNORE_CASE)

    fun threshold(title: String, track: PlanTrack): Threshold? {
        val lower = title.lowercase()
        return when (track) {
            PlanTrack.RUN -> {
                val km = when {
                    Regex("""\bhalf[\s-]marathon\b""").containsMatchIn(lower) -> 21.1
                    Regex("""\bmarathon\b""").containsMatchIn(lower) -> 42.2
                    else -> kmRe.find(lower)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
                }
                val min = minRe.find(lower)?.groupValues?.get(1)?.toIntOrNull()
                if (km == null && min == null) null else Threshold(km = km, minutes = min)
            }
            PlanTrack.SAVE -> numberRe.find(title)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.let { Threshold(amount = it) }
            PlanTrack.WEIGHT -> kgRe.find(lower)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.let { Threshold(kg = it) }
            PlanTrack.APPLICATIONS -> appsRe.find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let { Threshold(count = it) }
                ?: if ("interview" in lower) Threshold(interview = true) else null
            PlanTrack.COUNT -> numberRe.find(title)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.toInt()?.let { Threshold(count = it) }
            PlanTrack.STUDY, PlanTrack.CHECKLIST -> null
        }
    }

    /**
     * Progress for [goal]. [untickedOn] holds the day a step was unticked by hand: only data after
     * that day ticks it again, so an untick sticks until something new is logged.
     */
    fun of(goal: Goal, spec: PlanSpec?, inputs: PlanInputs, today: LocalDate, untickedOn: Map<String, LocalDate> = emptyMap()): PlanProgressResult {
        val track = spec?.track ?: PlanTrack.CHECKLIST
        val start = spec?.start ?: goal.createdAt.date
        val steps = goal.milestones
        val done = steps.count { it.isCompleted }
        val stepsLine = "$done of ${steps.size} ${if (steps.size == 1) "step" else "steps"}"
        val stepsFraction = if (steps.isEmpty()) 0f else done.toFloat() / steps.size
        fun ticks(evidence: (Milestone, Threshold, LocalDate) -> Evidence?): Map<String, Evidence> =
            steps.mapNotNull { m ->
                val t = threshold(m.title, track) ?: return@mapNotNull null
                val after = untickedOn[m.id]?.let { maxOf(it.plus(DatePeriod(days = 1)), start) } ?: start
                evidence(m, t, after)?.let { m.id to it }
            }.toMap()

        return when (track) {
            PlanTrack.RUN -> {
                val runs = runs(inputs.logs, start)
                val best = runs.mapNotNull { km(it) }.maxOrNull()
                val bestMin = runs.mapNotNull { it.durationMin }.maxOrNull()
                val weekStart = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
                val thisWeek = runs.count { it.date >= weekStart }
                val perWeek = if (spec?.routineKind == RoutineKind.FITNESS_WEEK) 3 else null
                PlanProgressResult(
                    stepsFraction, stepsLine,
                    listOf(
                        PlanStat("Best run", best?.let { "${PlanTemplates.km(it)} km" } ?: bestMin?.let { "$it min" } ?: "None yet"),
                        PlanStat("Runs so far", runs.size.toString()),
                        PlanStat("This week", perWeek?.let { "$thisWeek of $it" } ?: thisWeek.toString()),
                    ),
                    when {
                        best != null -> "Moves with your runs. Best so far ${PlanTemplates.km(best)} km."
                        bestMin != null -> "Moves with your runs. Longest so far $bestMin min."
                        else -> "Moves with your runs, logged in Fitness or from Health."
                    },
                    ticks { _, t, after ->
                        runs.firstOrNull { r -> r.date >= after && ((t.km != null && (km(r) ?: 0.0) >= t.km - 0.05) || (t.minutes != null && (r.durationMin ?: 0) >= t.minutes)) }
                            ?.let { Evidence(it.occurredAt, "Ticked from your run, ${PlanScheduler.dayLabel(it.date)}") }
                    },
                    best ?: bestMin?.toDouble(),
                )
            }
            PlanTrack.SAVE -> {
                val rows = savings(inputs.logs, goal.id, spec).sortedBy { it.occurredAt }
                val base = spec?.baseline ?: 0.0
                val saved = base + rows.sumOf { it.amount ?: 0.0 }
                val target = spec?.target ?: 0.0
                val cur = spec?.currency
                fun money(v: Double) = MoneyFormat.format(v, cur)
                val left = (target - saved).coerceAtLeast(0.0)
                val months = (PlanScheduler.days(today, goal.dueDate) / 30.44).coerceAtLeast(1.0)
                val word = if (spec?.template == PlanTemplates.DEBT) "paid off" else "put aside"
                PlanProgressResult(
                    if (target > 0) (saved / target).toFloat().coerceIn(0f, 1f) else stepsFraction,
                    "${money(saved)} of ${money(target)}",
                    listOf(
                        PlanStat(word.replaceFirstChar { it.uppercase() }, money(saved)),
                        PlanStat("To go", money(left)),
                        PlanStat("A month", if (left > 0) money(kotlin.math.ceil(left / months / 10) * 10) else money(0.0)),
                    ),
                    if (left > 0) "Moves with money $word. ${money(left)} to go." else "All of it is there.",
                    ticks { _, t, after ->
                        val need = t.amount ?: return@ticks null
                        if (need <= base) return@ticks null
                        var sum = base
                        rows.firstOrNull { r -> sum += r.amount ?: 0.0; sum >= need - 0.005 && r.date >= after }
                            ?.let { Evidence(it.occurredAt, "Reached ${money(need)}, ${PlanScheduler.dayLabel(it.date)}") }
                    },
                    saved,
                )
            }
            PlanTrack.STUDY -> {
                val subject = spec?.subject?.trim()?.lowercase()
                val times = StudyPlanner.times(inputs.logs, inputs.focus)
                    .filter { it.date >= start && subject != null && it.subject?.trim()?.lowercase()?.let { s -> s == subject || subject in s || s in subject } == true }
                val minutes = times.sumOf { it.minutes }
                val weekStart = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
                val target = spec?.target?.toInt()
                PlanProgressResult(
                    stepsFraction, stepsLine,
                    listOfNotNull(
                        PlanStat("Studied", hours(minutes)),
                        target?.let { PlanStat("Of", hours(it)) },
                        PlanStat("This week", hours(times.filter { it.date >= weekStart }.sumOf { it.minutes })),
                    ),
                    spec?.subject?.let { s -> "Moves with study time on $s." + (target?.let { " ${hours(minutes)} of ${hours(it)} so far." } ?: "") },
                    emptyMap(),
                    minutes.toDouble(),
                )
            }
            PlanTrack.WEIGHT -> {
                val readings = inputs.weights.filter { it.first >= start.minus(DatePeriod(days = 3)) }.sortedBy { it.first }
                val base = spec?.baseline ?: readings.firstOrNull()?.second
                val now = readings.lastOrNull()?.second
                val lost = if (base != null && now != null) (base - now).coerceAtLeast(0.0) else 0.0
                val target = spec?.target ?: 0.0
                PlanProgressResult(
                    if (target > 0) (lost / target).toFloat().coerceIn(0f, 1f) else stepsFraction,
                    "${PlanTemplates.km(round1(lost))} of ${PlanTemplates.km(target)} kg down",
                    listOf(
                        PlanStat("Now", now?.let { "${PlanTemplates.km(round1(it))} kg" } ?: "No reading"),
                        PlanStat("Down", "${PlanTemplates.km(round1(lost))} kg"),
                        PlanStat("To go", "${PlanTemplates.km(round1((target - lost).coerceAtLeast(0.0)))} kg"),
                    ),
                    if (now == null) "Connect Health and your weight moves this plan." else "Moves with your weight from Health. ${PlanTemplates.km(round1(lost))} kg down so far.",
                    ticks { _, t, after ->
                        val kg = t.kg ?: return@ticks null
                        if (base == null) return@ticks null
                        readings.firstOrNull { (d, w) -> d >= after && base - w >= kg - 0.05 }
                            ?.let { (d, _) -> Evidence(LocalDateTime(d, NOON), "Ticked from Health, ${PlanScheduler.dayLabel(d)}") }
                    },
                    lost,
                )
            }
            PlanTrack.APPLICATIONS -> {
                val rows = inputs.logs.filter { CareerKind.of(it) != null && it.date >= start }
                val apps = rows.filter { CareerKind.of(it) == CareerKind.APPLICATION && CareerPlanner.stage(it) != Stage.SAVED }.sortedBy { it.occurredAt }
                val interviews = rows.filter { CareerKind.of(it) == CareerKind.INTERVIEW }.sortedBy { it.occurredAt }
                val weekStart = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
                PlanProgressResult(
                    stepsFraction, stepsLine,
                    listOf(
                        PlanStat("Sent", apps.size.toString()),
                        PlanStat("Interviews", interviews.size.toString()),
                        PlanStat("This week", apps.count { it.date >= weekStart }.toString()),
                    ),
                    "Moves with applications on the Career page. ${apps.size} sent so far.",
                    ticks { _, t, after ->
                        when {
                            t.interview -> interviews.firstOrNull { it.date >= after }?.let { Evidence(it.occurredAt, "Ticked from your interview, ${PlanScheduler.dayLabel(it.date)}") }
                            t.count != null -> apps.getOrNull(t.count - 1)?.takeIf { it.date >= after }
                                ?.let { Evidence(it.occurredAt, "Ticked from your applications, ${PlanScheduler.dayLabel(it.date)}") }
                            else -> null
                        }
                    },
                    apps.size.toDouble(),
                )
            }
            PlanTrack.COUNT -> {
                val fromHabit = spec?.routineKind == RoutineKind.HABIT && spec.routineId != null
                val days = inputs.habitDays.filter { it >= start }.sorted()
                val count = if (fromHabit) days.size + (spec?.count ?: 0) else spec?.count ?: 0
                val target = spec?.target?.toInt() ?: 0
                PlanProgressResult(
                    if (target > 0) (count.toFloat() / target).coerceIn(0f, 1f) else stepsFraction,
                    if (target > 0) "$count of $target" else "$count so far",
                    listOf(PlanStat("Done", count.toString()), PlanStat("To go", (target - count).coerceAtLeast(0).toString())),
                    if (fromHabit) "Moves each day you tick its routine on Today." else "Moves each time you tap +1 on the plan.",
                    ticks { _, t, after ->
                        val n = t.count ?: return@ticks null
                        if (count < n) return@ticks null
                        val day = if (fromHabit) days.getOrNull(n - 1 - (spec?.count ?: 0)) ?: today else today
                        if (day < after) null else Evidence(LocalDateTime(day, NOON), if (fromHabit) "Ticked from your routine, ${PlanScheduler.dayLabel(day)}" else "Ticked when you reached $n")
                    },
                    count.toDouble(),
                )
            }
            PlanTrack.CHECKLIST -> PlanProgressResult(stepsFraction, stepsLine, emptyList(), null, emptyMap(), null)
        }
    }

    /**
     * The recap when a plan is done: a headline and what it took. "You ran a 5K." and "9 weeks,
     * 24 runs and 61 km along the way."
     */
    fun recap(goal: Goal, spec: PlanSpec?, inputs: PlanInputs, finished: LocalDate): Pair<String, String> {
        val start = spec?.start ?: goal.createdAt.date
        val span = PlanScheduler.spanLabel(start, finished).let { if (PlanScheduler.days(start, finished) < 2) "a day" else it }
        val cur = spec?.currency
        return when (spec?.track) {
            PlanTrack.RUN -> {
                val runs = runs(inputs.logs, start).filter { it.date <= finished }
                val kms = runs.mapNotNull { km(it) }.sum()
                "You ran a ${PlanTemplates.runName(spec.target ?: 5.0)}." to
                    "$span, ${runs.size} ${if (runs.size == 1) "run" else "runs"}" + (if (kms > 0) " and ${kms.roundToInt()} km" else "") + " along the way."
            }
            PlanTrack.SAVE -> {
                val rows = savings(inputs.logs, goal.id, spec)
                val total = (spec.baseline ?: 0.0) + rows.sumOf { it.amount ?: 0.0 }
                val debt = spec.template == PlanTemplates.DEBT
                (if (debt) "You paid off ${MoneyFormat.format(total, cur)}." else "You saved ${MoneyFormat.format(total, cur)}.") to
                    "In $span, ${rows.size} ${if (rows.size == 1) "time" else "times"}."
            }
            PlanTrack.WEIGHT -> "You lost ${PlanTemplates.km(spec.target ?: 0.0)} kg." to "In $span, one small swap at a time."
            PlanTrack.STUDY -> {
                val minutes = of(goal, spec, inputs, finished).value?.toInt() ?: 0
                "You did it: ${goal.title}." to "${hours(minutes)} of ${spec.subject ?: "study"} in $span."
            }
            PlanTrack.APPLICATIONS -> {
                val p = of(goal, spec, inputs, finished)
                "You did it: ${goal.title}." to "In $span, with ${p.value?.toInt() ?: 0} applications along the way."
            }
            else -> "You did it: ${goal.title}." to "${goal.milestones.size} ${if (goal.milestones.size == 1) "step" else "steps"} in $span."
        }
    }

    fun runs(logs: List<LifeLog>, since: LocalDate): List<LifeLog> =
        logs.filter { it.kind == LogKind.WORKOUT && it.status == LogStatus.DONE && it.date >= since && WorkoutKind.fromTitle(it.title) == WorkoutKind.RUN }
            .sortedBy { it.occurredAt }

    /** Money put aside for this plan: tagged with it, or paid from its monthly reminder. */
    fun savings(logs: List<LifeLog>, goalId: String, spec: PlanSpec?): List<LifeLog> =
        logs.filter { l ->
            l.kind == LogKind.EXPENSE && l.category == SAVINGS && l.status != LogStatus.PLANNED &&
                (l.externalId == goalId || (spec?.routineId != null && Bills.paidFrom(l)?.first == spec.routineId))
        }

    private fun km(l: LifeLog): Double? = l.quantity?.takeIf { l.unit == null || l.unit.equals("km", ignoreCase = true) }

    /** "6h 20m", "45 min", "22h". */
    fun hours(m: Int): String = when {
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60}h"
        else -> "${m / 60}h ${(m % 60).toString().padStart(2, '0')}m"
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
    private val NOON = LocalTime(12, 0)
}
