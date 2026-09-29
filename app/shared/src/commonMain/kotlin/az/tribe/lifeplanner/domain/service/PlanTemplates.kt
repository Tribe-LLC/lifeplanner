package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.domain.model.PlanArea
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.math.ceil
import kotlin.math.roundToInt

/** One choice for a template's single question. */
data class PlanAnswer(val key: String, val label: String, val note: String)

data class PlanQuestion(val text: String, val answers: List<PlanAnswer>, val default: String) {
    fun answer(key: String?) = answers.firstOrNull { it.key == key } ?: answers.first { it.key == default }
}

/**
 * A step before it has a date. [at] is how far along the plan it falls: 0 is the first days, 1 the
 * target date. [auto] steps tick themselves from the plan's track; [done] ones are already true
 * from what the user said ("€500 put aside" when they have €500).
 */
data class StepDraft(val title: String, val at: Double, val auto: Boolean = false, val done: Boolean = false, val minutes: Int? = null)

/**
 * What keeps a plan moving between steps. [days] for a Fitness week or a study repeat, [perWeek]
 * for a habit done so many times a week (neither means every day), [dayOfMonth] for a monthly one.
 */
data class RoutineDraft(
    val kind: RoutineKind,
    val title: String,
    val days: Set<DayOfWeek> = emptySet(),
    val perWeek: Int? = null,
    val minutes: Int? = null,
    val time: LocalTime? = null,
    val dayOfMonth: Int? = null,
    val amount: Double? = null,
)

/** Everything the plan sheet shows for one line, before it is saved. */
data class PlanRecipe(
    val template: String?,
    val area: PlanArea,
    val track: PlanTrack,
    val title: String,
    val question: PlanQuestion? = null,
    val steps: List<StepDraft> = emptyList(),
    val routine: RoutineDraft? = null,
    /** "Easy runs, 3 a week". */
    val routineTitle: String? = null,
    /** "Mon, Wed and Sat at 07:00 on your Fitness week, so they show on Today." */
    val routineLine: String? = null,
    /** The "How it moves" line. */
    val moves: String,
    val target: Double? = null,
    val baseline: Double? = null,
    val subject: String? = null,
    /** The note under the question, for the answer picked. */
    val answerNote: String? = null,
    /** A plan to suggest when this one is done. */
    val next: String? = null,
    /** Steps land on this day of the week where they can: Sunday for long runs, Saturday for projects. */
    val weekday: DayOfWeek? = null,
    /** What +1 counts, for count plans: "book", "day". */
    val countWord: String? = null,
)

/** What a recipe needs to know besides the line. */
data class PlanContext(
    val start: LocalDate,
    val target: LocalDate,
    val currency: String,
    /** Runs come in from Health with no distance, so run steps are in minutes instead of km. */
    val runInMinutes: Boolean = false,
    /** The latest weight from Health, if any. */
    val weightKg: Double? = null,
)

/**
 * Ready plans for the things people plan most, one or two per area. Each turns a parsed line into
 * dated-to-be steps, at most one question, a routine and how the plan moves. Everything is rules,
 * no network; anything that matches none of them can ask the coach once (see PlanStepSuggester).
 */
object PlanTemplates {

    const val RUN = "run"
    const val WEIGHT = "weight"
    const val REPS = "reps"
    const val SAVE = "save"
    const val DEBT = "debt"
    const val LEARN = "learn"
    const val EXAM = "exam"
    const val JOB = "job"
    const val PROMOTION = "promotion"
    const val BOOKS = "books"
    const val DAYS = "days"
    const val COOK = "cook"
    const val TRIP = "trip"
    const val SLEEP = "sleep"

    /** How long a plan runs when no date was given. */
    fun defaultWeeks(line: PlanLine): Int = when (line.template) {
        RUN -> (line.km ?: 5.0).let { d -> if (d <= 5.0) 9 else if (d <= 10.0) 10 else if (d <= 21.1) 12 else 16 }
        WEIGHT -> ((line.kg ?: 4.0) * 2 + 2).roundToInt().coerceIn(4, 40)
        REPS -> 6
        SAVE, DEBT, PROMOTION -> 26
        LEARN -> 13
        EXAM -> 8
        JOB, TRIP -> 12
        BOOKS -> ((line.count ?: 12) * 4).coerceAtMost(52)
        DAYS -> ceil((line.days ?: 30) / 7.0).toInt() + 1
        COOK -> (line.count ?: 10) + 2
        SLEEP -> 5
        else -> 8
    }

    /** The line's own area wins over the template's, so "Learn SQL" from Career stays on Career. */
    fun recipe(line: PlanLine, answer: String?, ctx: PlanContext): PlanRecipe {
        val area = line.area ?: PlanArea.HABITS
        return build(line, answer, ctx, area).let { r -> if (line.area != null) r.copy(area = line.area) else r }
    }

    private fun build(line: PlanLine, answer: String?, ctx: PlanContext, area: PlanArea): PlanRecipe {
        return when (line.template) {
            RUN -> run(line, answer, ctx)
            WEIGHT -> weight(line, ctx)
            REPS -> reps(line, answer)
            SAVE, DEBT -> save(line, answer, ctx)
            LEARN -> learn(line, answer, ctx)
            EXAM -> exam(line, answer, ctx)
            JOB -> job(answer)
            PROMOTION -> PlanRecipe(
                PROMOTION, PlanArea.CAREER, PlanTrack.CHECKLIST, line.title,
                steps = spread(
                    "Ask your manager what the next level needs" to 0.0, "Pick one project to lead" to 0.2,
                    "Log a win every month" to 0.45, "Ask for feedback halfway" to 0.6, "Write down your case" to 0.85, "Have the talk" to 1.0,
                ),
                moves = "You tick each step. Wins you log on the Career page make the last talk easier.",
            )
            BOOKS -> count(line, PlanArea.HABITS, "book", "read",
                RoutineDraft(RoutineKind.HABIT, "Read 20 minutes"), "Read 20 minutes, every day", "Every day, on Today. Short is fine.",
                "Tap Finished a book on the plan each time. That is all it needs.")
            COOK -> count(line, PlanArea.MEALS, "new recipe", "cooked",
                RoutineDraft(RoutineKind.HABIT, "Cook something new", perWeek = 1), "Something new, once a week", "Once a week, on Today whenever it suits.",
                "Tap Cooked one on the plan each time.", setup = "Pick ${line.count ?: 10} recipes you want to try")
            DAYS -> days(line)
            TRIP -> trip(line, answer)
            SLEEP -> PlanRecipe(
                SLEEP, PlanArea.MIND, PlanTrack.CHECKLIST, line.title,
                steps = spread(
                    "Pick a bedtime and set a reminder" to 0.0, "Screens off 30 minutes before bed" to 0.2,
                    "A week at the new bedtime" to 0.35, "Two weeks in a row" to 0.6, "A month of good sleep" to 1.0,
                ),
                routine = RoutineDraft(RoutineKind.HABIT, "In bed by 23:00", time = LocalTime(22, 30)),
                routineTitle = "In bed by 23:00", routineLine = "Every night, with a reminder at 22:30.",
                moves = "You tick each step. Sleep from Health shows on the Sleep and mind page as you go.",
            )
            else -> PlanRecipe(
                null, area, PlanTrack.CHECKLIST, line.title, weekday = DayOfWeek.SATURDAY,
                moves = if (line.target == null) "You tick each step. One step a week, on Saturdays, and any of them can move."
                else "You tick each step on its day, and any of them can move.",
            )
        }
    }

    // ── Fitness ──────────────────────────────────────────────────────────────

    private fun run(line: PlanLine, answer: String?, ctx: PlanContext): PlanRecipe {
        val d = line.km ?: 5.0
        val mins = ctx.runInMinutes
        val ask = if (d <= 5.0) 1 else (d / 2).roundToInt()
        val far = (d * 0.6).roundToInt().coerceAtLeast(ask + 1)
        val q = if (mins) PlanQuestion(
            "Can you run 10 minutes without stopping today?",
            listOf(
                PlanAnswer("no", "Not yet", "We start with walk and run, and build up."),
                PlanAnswer("yes", "Yes", "We skip the first step."),
                PlanAnswer("far", "20 minutes or more", "You are close. Fewer steps, same date."),
            ), "no",
        ) else PlanQuestion(
            "Can you run $ask km without stopping today?",
            listOf(
                PlanAnswer("no", "Not yet", "We start with walk and run, and build up."),
                PlanAnswer("yes", "Yes", "We skip the first step."),
                PlanAnswer("far", "$far km or more", "You are close. Fewer steps, same date."),
            ), "no",
        )
        val a = q.answer(answer).key
        val finalTitle = runName(d).let { name -> if (mins) "Run the $name (about ${roundTo5(d * 7)} min)" else "Run the $name" }
        val steps = if (a == "far") {
            listOf(
                StepDraft("Pick a ${runName(d)} route or a race", 0.0),
                StepDraft(if (mins) "Run ${roundTo5(d * 7 * 0.8)} min" else "Run ${km(if (d <= 10) d - 1 else (d * 0.8).roundToInt().toDouble())} km", 0.4, auto = true),
                StepDraft(if (mins) "Run ${roundTo5(d * 7)} min at an easy pace" else "Run ${km(d)} km at an easy pace", 0.75, auto = true),
                StepDraft(finalTitle, 1.0, auto = true),
            )
        } else {
            val middle = if (mins) {
                val lo = if (a == "no") 10 else 20
                evenly(lo.toDouble(), roundTo5(d * 7).toDouble() - 5, 4).map { "Run ${roundTo5(it)} min" + if (it == lo.toDouble() && a == "no") " without stopping" else "" }
            } else {
                val lo = if (a == "no") (if (d <= 5) 1.0 else 2.0) else (ask + 1).toDouble()
                evenly(lo, kotlin.math.floor(d - 0.01), 4).map { "Run ${km(it)} km" + if (it == 1.0) " without stopping" else "" }
            }
            listOf(StepDraft("Get running shoes that fit", 0.0)) +
                middle.mapIndexed { i, t -> StepDraft(t, (i + 1.0) / (middle.size + 1), auto = true) } +
                StepDraft(finalTitle, 1.0, auto = true)
        }
        return PlanRecipe(
            RUN, PlanArea.FITNESS, PlanTrack.RUN, line.title, q, steps,
            routine = RoutineDraft(RoutineKind.FITNESS_WEEK, "Easy run", setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY), minutes = 30, time = LocalTime(7, 0)),
            routineTitle = "Easy runs, 3 a week",
            routineLine = "Mon, Wed and Sat at 07:00 on your Fitness week, so they show on Today.",
            moves = "Runs you log in Fitness, or that come in from Health, tick the ${if (mins) "time" else "distance"} steps. You never type a number.",
            target = d, answerNote = q.answer(answer).note,
            next = when {
                d <= 5.0 -> "Run a 10K"
                d <= 10.0 -> "Run a half marathon"
                d < 42.0 -> "Run a marathon"
                else -> null
            },
            weekday = DayOfWeek.SUNDAY,
        )
    }

    private fun weight(line: PlanLine, ctx: PlanContext): PlanRecipe {
        val k = line.kg ?: 4.0
        val stepKg = if (k <= 5) 1.0 else 2.0
        val marks = generateSequence(stepKg) { it + stepKg }.takeWhile { it < k - 0.01 }.toList()
        val steps = listOf(StepDraft("Pick one small swap for every day", 0.0)) +
            marks.map { StepDraft("${km(it)} kg down", it / k, auto = true) } +
            StepDraft("${km(k)} kg down, all of it", 1.0, auto = true)
        val weeks = ((ctx.target.toEpochDays() - ctx.start.toEpochDays()) / 7.0).coerceAtLeast(1.0)
        return PlanRecipe(
            WEIGHT, PlanArea.FITNESS, PlanTrack.WEIGHT, line.title, null, steps,
            routine = RoutineDraft(RoutineKind.HABIT, "A 30 minute walk", perWeek = 4),
            routineTitle = "A 30 minute walk, 4 a week", routineLine = "A habit on Today, any 4 days of the week.",
            moves = ctx.weightKg?.let { "Your weight from Health moves this plan, from ${km(it)} kg today. Nothing to type." }
                ?: "Connect Health and your weight moves this plan. Until then, tap a step when you reach it.",
            target = k, baseline = ctx.weightKg,
            answerNote = "About ${km((k / weeks * 10).roundToInt() / 10.0)} kg a week, a pace that lasts.",
        )
    }

    private fun reps(line: PlanLine, answer: String?): PlanRecipe {
        val n = line.count ?: 10
        val what = line.subject ?: "push-ups"
        val q = PlanQuestion(
            "How many can you do now?",
            listOf(
                PlanAnswer("none", "None yet", "We start from one or two, and build up."),
                PlanAnswer("few", "A few", "We skip the first step."),
                PlanAnswer("half", "Half or more", "You are close. Fewer steps, same date."),
            ), "none",
        )
        val lo = when (q.answer(answer).key) { "none" -> 3.0; "few" -> n * 0.5; else -> n * 0.8 }
        val marks = evenly(lo, n - 1.0, 3).map { it.roundToInt() }.distinct().filter { it in 1 until n }
        val steps = marks.mapIndexed { i, m -> StepDraft("$m $what in a row", (i + 1.0) / (marks.size + 1)) } + StepDraft("$n $what in a row", 1.0)
        return PlanRecipe(
            REPS, PlanArea.FITNESS, PlanTrack.CHECKLIST, line.title, q, steps,
            routine = RoutineDraft(RoutineKind.HABIT, "${what.replaceFirstChar { it.uppercase() }}, one set"),
            routineTitle = "One set a day", routineLine = "A habit on Today. One set, as many as you can.",
            moves = "You tick each step when you get there. The daily set is what moves it.",
            answerNote = q.answer(answer).note, next = "${n * 2} $what in a row",
        )
    }

    // ── Money ────────────────────────────────────────────────────────────────

    private fun save(line: PlanLine, answer: String?, ctx: PlanContext): PlanRecipe {
        val debt = line.template == DEBT
        val cur = line.currency ?: ctx.currency
        val total = line.amount ?: 1000.0
        fun money(v: Double) = MoneyFormat.format(v, cur)
        val quarter = nice(total / 4)
        val q = PlanQuestion(
            if (debt) "Paid any of it off already?" else "Put any aside already?",
            listOf("0" to "Nothing yet", quarter.toString() to money(quarter), nice(total / 2).toString() to money(nice(total / 2))).map { (k, l) ->
                PlanAnswer(k, l, perMonth(total - k.toDouble(), ctx, cur))
            },
            "0",
        )
        val have = q.answer(answer).key.toDouble()
        val word = if (debt) "paid off" else "put aside"
        // Marks below what is already there are not steps any more; the one it reached shows as done.
        val marks = (1..3).map { nice(total * it / 4) }.filter { it >= have }
        val steps = listOf(StepDraft(if (debt) "List what you owe and the rates" else "Set a monthly transfer on payday", 0.0)) +
            marks.map { m -> StepDraft("${money(m)} $word", ((m - have) / (total - have)).coerceIn(0.05, 0.95), auto = true, done = m <= have) } +
            StepDraft(if (debt) "${money(total)}, all paid off" else "${money(total)}, all of it", 1.0, auto = true)
        val forWhat = line.subject?.let { " for $it" } ?: ""
        val monthly = monthlyAmount(total - have, ctx)
        return PlanRecipe(
            line.template, PlanArea.MONEY, PlanTrack.SAVE, line.title, q, steps,
            routine = RoutineDraft(RoutineKind.MONTHLY, if (debt) "Pay off debt" else "Put aside$forWhat", dayOfMonth = 25, amount = monthly),
            routineTitle = "A payday reminder",
            routineLine = "On the 25th: \"${if (debt) "Pay some off" else "Put aside$forWhat"}?\" with ${money(monthly)} ready.",
            moves = if (debt) "Tap Paid some off on the plan, or type \"paid off 200\" in Add anything. It counts here, and stays out of your spending."
            else "Type \"put aside 100$forWhat\" in Add anything, or tap Put aside on the plan. It counts here, and stays out of your spending.",
            target = total, baseline = have, subject = line.subject, answerNote = q.answer(answer).note,
        )
    }

    /** "€400 a month gets you there, about €92 a week." */
    fun perMonth(left: Double, ctx: PlanContext, currency: String): String {
        val month = monthlyAmount(left, ctx)
        return "${MoneyFormat.format(month, currency)} a month gets you there, about ${MoneyFormat.format(ceil(month * 12 / 52), currency)} a week."
    }

    fun monthlyAmount(left: Double, ctx: PlanContext): Double {
        val months = ((ctx.target.toEpochDays() - ctx.start.toEpochDays()) / 30.44).coerceAtLeast(1.0)
        return ceil(left.coerceAtLeast(0.0) / months / 10) * 10
    }

    // ── Study and career ─────────────────────────────────────────────────────

    private val languages = setOf(
        "spanish", "french", "german", "italian", "portuguese", "japanese", "chinese", "mandarin", "korean", "arabic",
        "turkish", "russian", "english", "dutch", "greek", "polish", "swedish", "hindi", "azerbaijani", "georgian",
    )

    private fun learn(line: PlanLine, answer: String?, ctx: PlanContext): PlanRecipe {
        val subject = line.subject ?: line.title.removePrefix("Learn ").trim()
        val weekdays = weekdaysBetween(ctx.start, ctx.target)
        val q = PlanQuestion(
            "How long a day works for you?",
            listOf(10, 20, 30).map { m -> PlanAnswer(m.toString(), "$m min", "About ${(m * weekdays / 60.0).roundToInt()} hours by the end.") },
            "20",
        )
        val minutes = q.answer(answer).key.toInt()
        val language = subject.lowercase().split(' ').any { it in languages }
        val steps = if (language) spread(
            "Pick an app or a course" to 0.0, "Greetings and numbers" to 0.15, "Your first 100 words" to 0.4,
            "Order food and ask the way" to 0.7, "A 5 minute chat with someone" to 1.0,
        ) else spread(
            "Pick a course or a teacher" to 0.0, "The basics, start to end" to 0.2, "Your first small project" to 0.45,
            "Use it for something real" to 0.75, "Show someone what you can do" to 1.0,
        )
        return PlanRecipe(
            LEARN, PlanArea.STUDY, PlanTrack.STUDY, line.title, q, steps,
            routine = RoutineDraft(RoutineKind.STUDY_REPEAT, subject, WEEKDAYS, minutes = minutes),
            routineTitle = "$subject on weekdays", routineLine = "$minutes min each weekday, as a study block on Today.",
            moves = "Study time on $subject counts, from the timer or a ticked block. Steps you tick yourself, the hours fill in on their own.",
            target = (minutes * weekdays).toDouble(), subject = subject, answerNote = q.answer(answer).note,
        )
    }

    private fun exam(line: PlanLine, answer: String?, ctx: PlanContext): PlanRecipe {
        val subject = line.subject ?: line.title
        val q = PlanQuestion(
            "How ready do you feel?",
            listOf(
                PlanAnswer("start", "Just starting", "About 30 hours of study before the day."),
                PlanAnswer("half", "Halfway", "About 18 hours of study before the day."),
                PlanAnswer("near", "Nearly there", "About 8 hours of study before the day."),
            ), "start",
        )
        val a = q.answer(answer).key
        val hours = when (a) { "start" -> 30; "half" -> 18; else -> 8 }
        val all = listOf(
            "Get the syllabus and past papers" to 0.0, "Go through every topic once" to 0.35, "First full practice test" to 0.55,
            "Go over what you got wrong" to 0.7, "Second practice test" to 0.85, "Sit the exam" to 1.0,
        )
        val steps = spread(*(if (a == "near") all.drop(2) else all).toTypedArray())
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
        return PlanRecipe(
            EXAM, PlanArea.STUDY, PlanTrack.STUDY, line.title, q, steps,
            routine = RoutineDraft(RoutineKind.STUDY_REPEAT, subject, days, minutes = 45),
            routineTitle = "$subject, 4 days a week", routineLine = "45 min on Mon, Tue, Thu and Sat, as a study block on Today.",
            moves = "Study time on $subject counts, from the timer or a ticked block. You tick the steps.",
            target = hours * 60.0, subject = subject, answerNote = q.answer(answer).note,
        )
    }

    private fun job(answer: String?): PlanRecipe {
        val q = PlanQuestion(
            "Is your CV ready?",
            listOf(
                PlanAnswer("no", "Not yet", "Writing it comes first, this week."),
                PlanAnswer("refresh", "Needs a refresh", "A quick update comes first, this week."),
                PlanAnswer("ready", "Ready", "Straight to applying."),
            ), "refresh",
        )
        val first = when (q.answer(answer).key) { "no" -> "Write your CV"; "refresh" -> "Update your CV"; else -> null }
        val steps = listOfNotNull(first?.let { StepDraft(it, 0.0) }) + listOf(
            StepDraft("Make a list of 20 places to try", 0.08),
            StepDraft("5 applications sent", 0.25, auto = true),
            StepDraft("10 applications sent", 0.45, auto = true),
            StepDraft("First interview", 0.6, auto = true),
            StepDraft("20 applications sent", 0.8, auto = true),
            StepDraft("Get an offer", 1.0),
        )
        return PlanRecipe(
            JOB, PlanArea.CAREER, PlanTrack.APPLICATIONS, "Find a new job", q, steps,
            routine = RoutineDraft(RoutineKind.HABIT, "Send an application", perWeek = 3),
            routineTitle = "An application, 3 a week", routineLine = "A habit on Today, any 3 days of the week.",
            moves = "Applications you add on the Career page tick the counting steps, and your first interview ticks itself.",
            target = 20.0, answerNote = q.answer(answer).note,
        )
    }

    // ── Counting, days in a row, trips ───────────────────────────────────────

    private fun count(
        line: PlanLine, area: PlanArea, word: String, verb: String,
        routine: RoutineDraft, routineTitle: String, routineLine: String, moves: String, setup: String? = null,
    ): PlanRecipe {
        val n = line.count ?: 12
        val marks = quarters(n)
        val steps = listOfNotNull(setup?.let { StepDraft(it, 0.0) }) +
            marks.map { m -> StepDraft("$m ${plural(word, m)} $verb", m.toDouble() / n, auto = true) } +
            StepDraft("$n ${plural(word, n)}, all of them", 1.0, auto = true)
        return PlanRecipe(
            line.template, area, PlanTrack.COUNT, line.title, null, steps,
            routine = routine, routineTitle = routineTitle, routineLine = routineLine, moves = moves,
            target = n.toDouble(), countWord = word.substringAfterLast(' '),
        )
    }

    private fun days(line: PlanLine): PlanRecipe {
        val n = line.days ?: 30
        val habit = line.subject ?: line.title
        val marks = (if (n >= 14) generateSequence(7) { it + 7 }.takeWhile { it < n - 3 }.toList() else quarters(n)).takeLast(3)
        val steps = marks.map { m -> StepDraft("$m days", m.toDouble() / n, auto = true) } + StepDraft("$n days, all of them", 1.0, auto = true)
        return PlanRecipe(
            DAYS, line.area ?: PlanArea.HABITS, PlanTrack.COUNT, line.title, null, steps,
            routine = RoutineDraft(RoutineKind.HABIT, habit), routineTitle = "$habit, every day",
            routineLine = "A habit on Today. Each day you tick it counts here.",
            moves = "Each day you tick $habit on Today counts here. Miss one and the count just waits.",
            target = n.toDouble(), subject = habit, countWord = "day",
        )
    }

    private fun trip(line: PlanLine, answer: String?): PlanRecipe {
        val place = line.subject
        val q = PlanQuestion(
            "Do you have dates yet?",
            listOf(PlanAnswer("no", "Not yet", "Picking them comes first."), PlanAnswer("yes", "Yes", "Straight to booking.")),
            "no",
        )
        val all = listOf(
            "Pick the dates" to 0.0, "Book the travel" to 0.2, "Book a place to stay" to 0.35,
            "Plan the first days" to 0.7, "Check passport and papers" to 0.8, (place?.let { "Off to $it" } ?: "Off you go") to 1.0,
        )
        val steps = spread(*(if (q.answer(answer).key == "yes") all.drop(1) else all).toTypedArray())
        return PlanRecipe(
            TRIP, PlanArea.TRAVEL, PlanTrack.CHECKLIST, line.title, q, steps,
            moves = "You tick each step. Once the dates are set, add the trip on the Travel page for packing lists and a day by day plan.",
            subject = line.subject, answerNote = q.answer(answer).note,
        )
    }

    // ── Ideas ────────────────────────────────────────────────────────────────

    /** Three one-tap ideas for an area page's plans list. */
    fun ideas(area: PlanArea, currency: String): List<String> {
        fun m(v: Double) = MoneyFormat.format(v, currency)
        return when (area) {
            PlanArea.FITNESS -> listOf("Run a 10K", "Lose 4 kg", "10 push-ups in a row")
            PlanArea.MONEY -> listOf("Save ${m(1000.0)} for a holiday", "Build a ${m(3000.0)} emergency fund", "Pay off ${m(2000.0)} of debt")
            PlanArea.STUDY -> listOf("Learn Spanish basics", "Pass my exam", "Learn to code")
            PlanArea.CAREER -> listOf("Find a new job", "Get promoted", "Learn SQL")
            PlanArea.HABITS -> listOf("Read 12 books this year", "No sugar for 30 days", "Wake up at 6 for 30 days")
            PlanArea.MEALS -> listOf("Cook 10 new recipes", "Eat vegetables every day for 30 days", "Learn to cook 5 dinners")
            PlanArea.TRAVEL -> listOf("Visit Japan", "A weekend in Rome", "Plan a summer trip")
            PlanArea.MIND -> listOf("Meditate for 30 days", "Journal for 30 days", "Sleep 8 hours a night")
        }
    }

    /** What the sheet offers before anything is typed, when it was not opened from an area. */
    val examples = listOf("Run a 5K by December", "Save 2000 for Japan by March", "Learn Spanish basics", "Build a garden shed")

    // ── Helpers ──────────────────────────────────────────────────────────────

    private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

    private fun spread(vararg steps: Pair<String, Double>) = steps.map { (t, at) -> StepDraft(t, at) }

    /** [n] values evenly from [lo] to [hi], both ends included; fewer when the range is small. */
    private fun evenly(lo: Double, hi: Double, n: Int): List<Double> {
        if (hi < lo) return listOf(lo).filter { it > 0 }
        val whole = (hi - lo).toInt() + 1
        val count = minOf(n, whole).coerceAtLeast(1)
        if (count == 1) return listOf(lo)
        return (0 until count).map { i -> (lo + (hi - lo) * i / (count - 1)).roundToInt().toDouble() }.distinct()
    }

    /** A quarter, a half and three quarters of [n], without repeats or zero. */
    private fun quarters(n: Int): List<Int> = listOf(n / 4, n / 2, n * 3 / 4).filter { it in 1 until n }.distinct()

    private fun weekdaysBetween(a: LocalDate, b: LocalDate): Int {
        val days = (b.toEpochDays() - a.toEpochDays()).toInt().coerceAtLeast(1)
        return (0 until days).count { i -> LocalDate.fromEpochDays((a.toEpochDays() + i).toInt()).dayOfWeek in WEEKDAYS }
    }

    /** Rounds money steps to something easy to read: tens, hundreds, fifties. */
    fun nice(v: Double): Double = when {
        v >= 1000 -> kotlin.math.round(v / 50) * 50
        v >= 100 -> kotlin.math.round(v / 10) * 10
        else -> kotlin.math.round(v)
    }

    fun plural(word: String, n: Int) = if (n == 1) word else word + "s"

    private fun roundTo5(v: Double) = ((v / 5).roundToInt() * 5).coerceAtLeast(5)

    fun km(v: Double): String = if (v == kotlin.math.floor(v)) v.toInt().toString() else ((v * 10).roundToInt() / 10.0).toString()

    /** "5K", "10K", "half marathon", "marathon", "15 km". */
    fun runName(d: Double): String = when {
        d in 21.0..21.2 -> "half marathon"
        d in 42.0..42.3 -> "marathon"
        d == kotlin.math.floor(d) && d.toInt() in setOf(3, 5, 10, 15, 20) -> "${d.toInt()}K"
        else -> "${km(d)} km"
    }
}
