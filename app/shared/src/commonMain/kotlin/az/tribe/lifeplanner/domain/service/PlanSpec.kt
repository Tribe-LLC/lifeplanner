package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.BudgetPeriod
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.LocalDate

/** What moves a plan along. [key] is stored in the plan's settings row, so never rename one. */
enum class PlanTrack(val key: String) {
    /** The longest run since the plan started, in km (or minutes, for runs with no distance). */
    RUN("run_km"),
    /** Money put aside for it (savings rows linked to the plan). */
    SAVE("save"),
    /** Minutes studied on the plan's subject. */
    STUDY("study"),
    /** Kilograms down from the weight at the start, from Health. */
    WEIGHT("weight"),
    /** Job applications sent since the start. */
    APPLICATIONS("apps"),
    /** A number that goes up: a +1 on the plan, or the days its routine was done. */
    COUNT("count"),
    /** Only the steps the user ticks. */
    CHECKLIST("checklist");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: CHECKLIST
    }
}

/** How a plan's routine is kept: a habit, a repeating study block, days on the Fitness week, or a monthly bill. */
enum class RoutineKind(val key: String) {
    HABIT("habit"), STUDY_REPEAT("study"), FITNESS_WEEK("fitweek"), MONTHLY("monthly");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

/**
 * A v4 plan's own settings, next to its goal. Kept in one budget row per plan ([metric] "plan"),
 * the same way the Fitness week and the Meals pantry are, so it syncs with no new table: the row's
 * area is the plan's real area, its amount the target, and its category this text.
 *
 * Goals made before v4 plans have none, and fall back to their old category's area.
 */
data class PlanSpec(
    val goalId: String,
    val area: PlanArea,
    val track: PlanTrack = PlanTrack.CHECKLIST,
    val start: LocalDate,
    /** Km, money, minutes, kg, applications or a count, whatever [track] measures. */
    val target: Double? = null,
    val currency: String? = null,
    val template: String? = null,
    /** What the plan is about, for matching logs: "Spanish", "Japan". */
    val subject: String? = null,
    /** Where the track started: money already put aside, the weight on day one. */
    val baseline: Double? = null,
    /** Counted by hand with +1, for [PlanTrack.COUNT] plans without a routine. */
    val count: Int = 0,
    val pausedFrom: LocalDate? = null,
    /** The last paused day. Null while paused means "until I say". */
    val pausedUntil: LocalDate? = null,
    val routineKind: RoutineKind? = null,
    val routineId: String? = null,
    /** When a catch-up was last answered with "leave it", so it is not asked again for a week. */
    val asked: LocalDate? = null,
    /** When "keep the date" was picked, so the pace can say "Catching up" for a while. */
    val keptOn: LocalDate? = null,
    val finished: LocalDate? = null,
    val version: Int = VERSION,
) {
    fun isPaused(today: LocalDate): Boolean = pausedFrom != null && pausedFrom <= today && (pausedUntil == null || today <= pausedUntil)

    fun encode(): String = buildList {
        add("goal=$goalId")
        template?.let { add("tpl=${clean(it)}") }
        add("track=${track.key}")
        add("start=$start")
        subject?.let { add("sub=${clean(it)}") }
        baseline?.let { add("base=${num(it)}") }
        if (count > 0) add("n=$count")
        pausedFrom?.let { add("paused=$it") }
        pausedUntil?.let { add("until=$it") }
        routineKind?.let { add("rtype=${it.key}") }
        routineId?.let { add("routine=${clean(it)}") }
        asked?.let { add("asked=$it") }
        keptOn?.let { add("kept=$it") }
        finished?.let { add("done=$it") }
        add("v=$version")
    }.joinToString(";")

    fun toBudget(): Budget = Budget(
        id = rowId(goalId), area = area, metric = METRIC, category = encode(),
        amount = target ?: 0.0, currency = currency, period = BudgetPeriod.MONTH,
    )

    companion object {
        const val METRIC = "plan"
        const val VERSION = 1

        fun rowId(goalId: String) = "plan-$goalId"

        /** Reads a plan row back. Keys it does not know are skipped, so a newer app's row still reads. */
        fun decode(b: Budget): PlanSpec? {
            if (b.metric != METRIC) return null
            val map = b.category.orEmpty().split(';').mapNotNull { p ->
                val i = p.indexOf('=')
                if (i <= 0) null else p.substring(0, i).trim() to p.substring(i + 1).trim()
            }.toMap()
            val goal = map["goal"]?.takeIf { it.isNotEmpty() } ?: return null
            val start = map["start"]?.let(::date) ?: return null
            return PlanSpec(
                goalId = goal,
                area = b.area,
                track = PlanTrack.fromKey(map["track"]),
                start = start,
                target = b.amount.takeIf { it > 0 },
                currency = b.currency,
                template = map["tpl"]?.takeIf { it.isNotEmpty() },
                subject = map["sub"]?.takeIf { it.isNotEmpty() },
                baseline = map["base"]?.toDoubleOrNull(),
                count = map["n"]?.toIntOrNull() ?: 0,
                pausedFrom = map["paused"]?.let(::date),
                pausedUntil = map["until"]?.let(::date),
                routineKind = RoutineKind.fromKey(map["rtype"]),
                routineId = map["routine"]?.takeIf { it.isNotEmpty() },
                asked = map["asked"]?.let(::date),
                keptOn = map["kept"]?.let(::date),
                finished = map["done"]?.let(::date),
                version = map["v"]?.toIntOrNull() ?: VERSION,
            )
        }

        /** Every plan's settings, by goal id. */
        fun fromBudgets(all: List<Budget>): Map<String, PlanSpec> =
            all.mapNotNull { decode(it) }.associateBy { it.goalId }

        /** The area a plan belongs to: its own when it has settings, else its old category's. */
        fun areaOf(goal: Goal, specs: Map<String, PlanSpec>): PlanArea = specs[goal.id]?.area ?: PlanArea.forCategory(goal.category)

        /**
         * The old category a new plan is saved with, since goals still need one. Travel, Study and
         * Meals have none of their own, so they get a neutral one; their area lives in the settings.
         */
        fun categoryFor(area: PlanArea): GoalCategory = when (area) {
            PlanArea.FITNESS, PlanArea.MEALS -> GoalCategory.BODY
            PlanArea.MONEY -> GoalCategory.MONEY
            PlanArea.CAREER, PlanArea.STUDY -> GoalCategory.CAREER
            PlanArea.MIND, PlanArea.HABITS -> GoalCategory.WELLBEING
            PlanArea.TRAVEL -> GoalCategory.PURPOSE
        }

        private fun date(s: String) = runCatching { LocalDate.parse(s) }.getOrNull()
        private fun clean(s: String) = s.replace(';', ',').replace('=', ' ').replace('\n', ' ').trim()
        private fun num(d: Double) = if (d == kotlin.math.floor(d)) d.toLong().toString() else d.toString()
    }
}
