package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.math.abs
import kotlin.math.roundToInt

/** One day across the areas, as numbers the coach, Life and the week review can reason about. */
data class DayFacts(
    val date: LocalDate,
    val habitsDue: Int = 0,
    val habitsKept: Int = 0,
    /** Hours slept the night before this day. */
    val sleep: Double? = null,
    val steps: Double? = null,
    /** Average mood that day, 1 to 5. */
    val mood: Double? = null,
    val workouts: Int = 0,
    val spent: Double = 0.0,
    val studyMin: Int = 0,
    val meals: Int = 0,
    val mealsOut: Int = 0,
    val mindful: Boolean = false,
    /** Names of what got done, for the day view. */
    val keptNames: List<String> = emptyList(),
) {
    val keptShare: Double? get() = if (habitsDue == 0) null else habitsKept.toDouble() / habitsDue
    /** Anything at all recorded. */
    val hasData: Boolean get() = habitsDue > 0 || sleep != null || mood != null || workouts > 0 || spent > 0 || studyMin > 0 || meals > 0
}

/** The last stretch of days (oldest first, today last) plus a few standing facts. */
data class LifeFacts(
    val today: LocalDate,
    val days: List<DayFacts>,
    val areas: Set<PlanArea>,
    val slipped: List<String> = emptyList(),
    /** e.g. "212 of 250 EUR this week". */
    val budgetLine: String? = null,
    /** Spent share of the budget and share of its period gone, for the opener. */
    val budgetPace: Pair<Double, Double>? = null,
    val workoutGoal: Int? = null,
    /** e.g. "Maths exam on Fri 2 Oct" with how many days away. */
    val nextDue: Pair<String, Int>? = null,
    val sleepGoal: Double? = null,
    /** The main budget's currency, for showing a day's spend. */
    val currency: String? = null,
) {
    fun day(d: LocalDate): DayFacts? = days.firstOrNull { it.date == d }
    /** The last [n] full days, not today. */
    fun past(n: Int): List<DayFacts> = days.filter { it.date < today }.takeLast(n)
}

/** Something the coach says first, with two ways to answer it. */
data class Opener(val text: String, val replies: List<Pair<String, String>>)

/** A pattern across areas, strongest first. */
data class Pattern(val text: String, val strength: Double, val days: Int)

data class WeekSummary(
    val from: LocalDate,
    val to: LocalDate,
    val bestDay: DayFacts?,
    val keptPct: Int?,
    val keptPctBefore: Int?,
    val workouts: Int,
    val studyMin: Int,
    val sleepAvg: Double?,
    val moodAvg: Double?,
)

object LifeFactsMath {
    /** Days with anything recorded before patterns are shown. */
    const val PATTERN_DAYS = 14
    private const val MIN_SIDE = 4

    // ── What the coach gets ─────────────────────────────────────────────────

    /** A short plain-text summary of the last 7 days, only for the picked areas. About 400 tokens at most. */
    fun digest(f: LifeFacts): String {
        val week = f.past(7)
        val lines = mutableListOf<String>()
        val due = week.sumOf { it.habitsDue }
        if (due > 0) {
            val kept = week.sumOf { it.habitsKept }
            val best = week.filter { it.habitsDue > 0 }.maxByOrNull { it.keptShare ?: 0.0 }
            lines += "Habits: kept $kept of $due due (${pct(kept, due)}%)" + (best?.let { ", best day ${dayName(it.date.dayOfWeek)}" } ?: "") +
                (if (f.slipped.isNotEmpty()) ". Slipped lately: ${f.slipped.take(4).joinToString(", ")}" else "")
        }
        val sleep = week.mapNotNull { it.sleep }
        if (PlanArea.MIND in f.areas || sleep.isNotEmpty()) sleep.takeIf { it.isNotEmpty() }?.let { s ->
            lines += "Sleep: average ${hours(s.average())}, ${s.count { it < 6.0 }} nights under 6h" + (f.sleepGoal?.let { ", goal ${hours(it)}" } ?: "")
        }
        week.mapNotNull { it.mood }.takeIf { it.isNotEmpty() }?.let { m ->
            lines += "Mood: average ${oneDecimal(m.average())} of 5 from ${m.size} days"
        }
        if (PlanArea.FITNESS in f.areas) {
            lines += "Workouts: ${week.sumOf { it.workouts }} done" + (f.workoutGoal?.let { " (goal $it a week)" } ?: "") +
                (week.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.let { ", steps about ${(it.average() / 100).roundToInt() * 100} a day" } ?: "")
        }
        if (PlanArea.MONEY in f.areas) f.budgetLine?.let { lines += "Money: spent $it" }
        if (PlanArea.STUDY in f.areas) lines += "Study: ${minutes(week.sumOf { it.studyMin })} this week"
        if (PlanArea.MEALS in f.areas) week.sumOf { it.meals }.takeIf { it > 0 }?.let { lines += "Meals: $it logged, ${week.sumOf { it.mealsOut }} eaten out" }
        f.nextDue?.let { (what, inDays) -> lines += "Coming up: $what ${if (inDays == 0) "today" else "in $inDays days"}" }
        f.day(f.today)?.takeIf { it.habitsDue > 0 }?.let { lines += "Today so far: ${it.habitsKept} of ${it.habitsDue} habits" }
        if (lines.isEmpty()) return ""
        return lines.joinToString("\n") { "- $it" }.take(1_400)
    }

    // ── The coach speaks first ──────────────────────────────────────────────

    fun opener(f: LifeFacts, hour: Int): Opener {
        val last3 = f.past(3).mapNotNull { it.sleep }
        val short = last3.count { it < 6.0 }
        if (short >= 2) return Opener(
            "${if (short == 2) "Two" else "Three"} short nights lately. Want a lighter day?",
            listOf(
                "Yes, lighten today" to "I have slept badly the last few nights. Help me make today lighter without losing my streaks.",
                "Why am I tired?" to "I keep sleeping badly. Look at my last week and tell me what might be behind it.",
            ),
        )
        f.slipped.firstOrNull()?.let { name ->
            return Opener(
                if (f.slipped.size == 1) "$name has slipped lately. Keep it, make it easier, or let it go?" else "${f.slipped.size} habits have slipped, $name among them. Want to sort them out?",
                listOf(
                    "Help me restart" to "$name has slipped. Help me restart it in a way that fits my days.",
                    "Should I drop it?" to "$name keeps slipping. Help me decide if I should drop it or change it.",
                ),
            )
        }
        f.budgetPace?.let { (spent, gone) ->
            if (spent > gone + 0.2 && spent < 1.5) return Opener(
                "You have spent ${(spent * 100).roundToInt()}% of the budget with ${100 - (gone * 100).roundToInt()}% of the time left.",
                listOf(
                    "Help me spend less" to "I am ahead of my budget. Help me spend less for the rest of this period.",
                    "Where did it go?" to "Where did my money go this week, and what is easy to cut?",
                ),
            )
        }
        f.nextDue?.let { (what, inDays) ->
            if (inDays in 1..7) return Opener(
                "$what in $inDays ${if (inDays == 1) "day" else "days"}. Want a plan for the days left?",
                listOf("Make me a plan" to "$what is in $inDays days. Make me a realistic plan for the days left.", "I feel behind" to "I feel behind for $what. Help me catch up without panicking."),
            )
        }
        val week = f.past(7)
        val due = week.sumOf { it.habitsDue }
        if (due >= 7 && week.sumOf { it.habitsKept }.toDouble() / due >= 0.8) return Opener(
            "You kept ${pct(week.sumOf { it.habitsKept }, due)}% of your habits this week. Ready for one more?",
            listOf("Suggest one" to "My habits are going well. Suggest one small new habit that fits what I already do.", "Just proud" to "My week went well. Tell me what went best."),
        )
        return if (hour < 12) Opener(
            "Morning. What would make today a good day?",
            listOf("Plan my day" to "Help me plan today around what is already on it.", "I have 20 minutes" to "I have 20 free minutes. What is the most useful thing to do with them?"),
        ) else if (hour < 18) Opener(
            "How is the day going?",
            listOf("Help me focus" to "I am losing focus this afternoon. Help me pick one thing and finish it.", "Plan my week" to "Help me plan the rest of my week."),
        ) else Opener(
            "How did today go?",
            listOf("It went well" to "Today went well. Help me keep it going tomorrow.", "Rough day" to "Today was rough. Help me make tomorrow lighter."),
        )
    }

    // ── Patterns across areas ───────────────────────────────────────────────

    /** Days still needed before patterns show, 0 when there is enough. */
    fun daysUntilPatterns(f: LifeFacts): Int = (PATTERN_DAYS - f.days.count { it.hasData && it.date < f.today }).coerceAtLeast(0)

    fun patterns(f: LifeFacts, max: Int = 3): List<Pattern> {
        if (daysUntilPatterns(f) > 0) return emptyList()
        val past = f.days.filter { it.date < f.today }
        val out = mutableListOf<Pattern>()

        fun keptBy(test: (DayFacts) -> Boolean?, good: String, bad: String) {
            val with = past.filter { it.habitsDue > 0 && test(it) == true }.mapNotNull { it.keptShare }
            val without = past.filter { it.habitsDue > 0 && test(it) == false }.mapNotNull { it.keptShare }
            if (with.size < MIN_SIDE || without.size < MIN_SIDE) return
            val a = with.average(); val b = without.average()
            if (b <= 0.0 || a <= 0.0) return
            val rel = a / b - 1
            if (abs(rel) < 0.15) return
            val p = (abs(rel) * 100).roundToInt()
            out += Pattern(if (rel > 0) good.replace("{p}", "$p") else bad.replace("{p}", "$p"), abs(rel), with.size)
        }
        fun moodBy(test: (DayFacts) -> Boolean, good: String) {
            val with = past.filter { it.mood != null && test(it) }.mapNotNull { it.mood }
            val without = past.filter { it.mood != null && !test(it) }.mapNotNull { it.mood }
            if (with.size < 3 || without.size < 3) return
            val d = with.average() - without.average()
            if (d < 0.3) return
            out += Pattern(good, d / 2, with.size)
        }

        keptBy({ d -> d.sleep?.let { it >= 7.0 } }, "After 7 or more hours of sleep, you keep {p}% more habits.", "After 7 or more hours of sleep, you keep {p}% fewer habits. Busy mornings?")
        keptBy({ d -> d.steps?.let { it >= 8_000 } }, "On days you walk 8,000 steps, you keep {p}% more habits.", "On days you walk a lot, you keep {p}% fewer habits.")
        keptBy({ d -> d.workouts > 0 }, "On workout days, you keep {p}% more habits.", "On workout days, you keep {p}% fewer habits. Maybe move one to a rest day.")
        moodBy({ it.workouts > 0 }, "You feel better on days you work out.")
        moodBy({ it.mindful }, "You feel better on days you stop to breathe.")
        moodBy({ (it.sleep ?: 0.0) >= 7.0 }, "You feel better after 7 or more hours of sleep.")

        // Money: spending on days you cook at home versus days you eat out.
        val cooked = past.filter { it.meals > 0 && it.mealsOut == 0 }.map { it.spent }
        val out1 = past.filter { it.mealsOut > 0 }.map { it.spent }
        if (cooked.size >= MIN_SIDE && out1.size >= MIN_SIDE && out1.average() > 0) {
            val rel = 1 - cooked.average() / out1.average()
            if (rel >= 0.15) out += Pattern("You spend about ${(rel * 100).roundToInt()}% less on days you only eat at home.", rel, cooked.size)
        }
        return out.sortedByDescending { it.strength }.take(max)
    }

    // ── The week ────────────────────────────────────────────────────────────

    fun week(f: LifeFacts, end: LocalDate): WeekSummary {
        val from = end.minus(DatePeriod(days = 6))
        val w = f.days.filter { it.date in from..end }
        val before = f.days.filter { it.date in from.minus(DatePeriod(days = 7))..from.minus(DatePeriod(days = 1)) }
        fun share(list: List<DayFacts>): Int? = list.sumOf { it.habitsDue }.takeIf { it > 0 }?.let { pct(list.sumOf { d -> d.habitsKept }, it) }
        return WeekSummary(
            from = from, to = end,
            bestDay = w.filter { it.habitsDue > 0 }.maxWithOrNull(compareBy({ it.keptShare ?: 0.0 }, { it.habitsKept })),
            keptPct = share(w), keptPctBefore = share(before),
            workouts = w.sumOf { it.workouts }, studyMin = w.sumOf { it.studyMin },
            sleepAvg = w.mapNotNull { it.sleep }.takeIf { it.isNotEmpty() }?.average(),
            moodAvg = w.mapNotNull { it.mood }.takeIf { it.isNotEmpty() }?.average(),
        )
    }

    // ── Formatting ──────────────────────────────────────────────────────────

    fun pct(a: Int, b: Int): Int = if (b == 0) 0 else (a * 100.0 / b).roundToInt()

    fun hours(h: Double): String {
        val m = (h * 60).roundToInt()
        return if (m % 60 == 0) "${m / 60}h" else "${m / 60}h ${m % 60}m"
    }

    fun minutes(m: Int): String = when {
        m < 60 -> "$m min"
        m % 60 == 0 -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}m"
    }

    fun dayName(d: DayOfWeek): String = d.name.lowercase().replaceFirstChar { it.uppercase() }

    fun oneDecimal(v: Double): String {
        val t = (v * 10).roundToInt()
        return "${t / 10}.${abs(t % 10)}"
    }
}
