package az.tribe.lifeplanner.ui.v4.plans

import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.plans.DraftStep
import az.tribe.lifeplanner.data.plans.PlanDraft
import az.tribe.lifeplanner.data.plans.SuggestedStep
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.PlanContext
import az.tribe.lifeplanner.domain.service.PlanLine
import az.tribe.lifeplanner.domain.service.PlanLineParser
import az.tribe.lifeplanner.domain.service.PlanRecipe
import az.tribe.lifeplanner.domain.service.PlanScheduler
import az.tribe.lifeplanner.domain.service.PlanTemplates
import az.tribe.lifeplanner.domain.service.PlanTrack
import az.tribe.lifeplanner.domain.service.StepDraft
import az.tribe.lifeplanner.ui.v4.components.areaName
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** What the user has said or picked on the plan sheet. Everything else is worked out from it. */
data class SheetInputs(
    val line: String = "",
    /** The area page it was opened from, if any. */
    val preset: PlanArea? = null,
    val source: String = "area",
    val answer: String? = null,
    /** Keys of steps taken out. */
    val removed: Set<String> = emptySet(),
    val added: List<DraftStep> = emptyList(),
    /** Steps moved to another day, by key. */
    val moved: Map<String, LocalDate> = emptyMap(),
    val target: LocalDate? = null,
    val amount: Double? = null,
    val area: PlanArea? = null,
    val routineOn: Boolean? = null,
    val suggested: List<SuggestedStep>? = null,
    val own: Boolean = false,
)

/** What the sheet knows about the user besides the line. */
data class SheetContext(
    val today: LocalDate,
    val currency: String,
    val runInMinutes: Boolean = false,
    val weightKg: Double? = null,
)

/** A step as the sheet lists it. [key] stays the same while the user removes or moves others. */
data class SheetStep(
    val key: String,
    val title: String,
    val date: LocalDate?,
    val done: Boolean = false,
    val auto: Boolean = false,
    val minutes: Int? = null,
)

/** The plan the sheet would make right now. */
data class SheetPreview(
    val line: PlanLine,
    val recipe: PlanRecipe,
    val area: PlanArea,
    val start: LocalDate,
    val target: LocalDate,
    /** A date was said or picked; otherwise one was suggested. */
    val dated: Boolean,
    val steps: List<SheetStep>,
    /** Nothing matched and there are no steps yet: offer the coach or the user's own. */
    val needsSteps: Boolean,
    val routineOn: Boolean,
    val currency: String,
    val suggested: Boolean,
) {
    val templated: Boolean get() = recipe.template != null
    val title: String get() = recipe.title
    val hasRoutine: Boolean get() = recipe.routine != null
    /** Plans with no date and no template run a step a week, however many steps there are. */
    val open: Boolean get() = !dated && !templated

    fun draft(source: String) = PlanDraft(
        title = title, area = area, track = recipe.track, template = recipe.template, start = start, target = target,
        steps = steps.map { DraftStep(it.title, it.date, it.done, it.minutes) },
        routine = recipe.routine?.takeIf { routineOn },
        targetValue = recipe.target,
        currency = currency.takeIf { recipe.track == PlanTrack.SAVE },
        baseline = recipe.baseline,
        subject = recipe.subject ?: line.subject,
        source = source,
        suggestedByCoach = suggested,
    )
}

/**
 * The plan sheet's thinking, kept apart from the screen so it can be tested: from one line and a few
 * taps to dated steps, the pills and every sentence the sheet shows.
 */
object PlanSheetModel {

    fun preview(i: SheetInputs, ctx: SheetContext): SheetPreview? {
        if (i.line.isBlank()) return null
        val read = PlanLineParser.parse(i.line, ctx.today, ctx.currency, i.preset)
        if (read.title.isBlank()) return null
        val line = read.copy(area = i.area ?: read.area, amount = i.amount ?: read.amount)
        val given = i.target ?: line.target
        val suggestedEnd = ctx.today.plus(DatePeriod(days = PlanTemplates.defaultWeeks(line) * 7))
        val end = given ?: suggestedEnd
        val recipe = PlanTemplates.recipe(line, i.answer, PlanContext(ctx.today, end, ctx.currency, ctx.runInMinutes, ctx.weightKg))
        val base = when {
            recipe.template != null -> recipe.steps.zip(PlanScheduler.date(recipe.steps, ctx.today, end, recipe.weekday))
                .mapIndexed { n, (s, d) -> SheetStep("t$n", s.title, d, s.done, s.auto, s.minutes) }
            i.suggested != null -> i.suggested.zip(coachDates(i.suggested, ctx.today, given))
                .mapIndexed { n, (s, d) -> SheetStep("s$n", s.title, d) }
            else -> emptyList()
        }
        val added = i.added.mapIndexed { n, s -> SheetStep("a$n", s.title, s.date) }
        val steps = (base.filter { it.key !in i.removed } + added)
            .map { s -> i.moved[s.key]?.let { s.copy(date = it) } ?: s }
            .sortedWith(compareBy({ it.date == null }, { it.date }))
        // A plan with no date ends on its last step; there is always a date behind it for the goal.
        val target = if (recipe.template != null || given != null) end else steps.mapNotNull { it.date }.maxOrNull() ?: suggestedEnd
        return SheetPreview(
            line = line, recipe = recipe, area = recipe.area, start = ctx.today, target = target, dated = given != null,
            steps = steps,
            needsSteps = recipe.template == null && i.suggested == null && !i.own && i.added.isEmpty(),
            routineOn = recipe.routine != null && (i.routineOn ?: true),
            currency = line.currency ?: ctx.currency,
            suggested = i.suggested != null,
        )
    }

    /** The coach gives weeks; they land on Saturdays, or spread up to the date when there is one. */
    fun coachDates(steps: List<SuggestedStep>, today: LocalDate, target: LocalDate?): List<LocalDate> {
        if (steps.isEmpty()) return emptyList()
        if (target == null) {
            val first = PlanScheduler.weekly(1, today).first()
            return steps.map { first.plus(DatePeriod(days = 7 * (it.week - 1))) }
        }
        val weeks = steps.maxOf { it.week }.coerceAtLeast(1)
        val drafts = steps.mapIndexed { n, s -> StepDraft(s.title, if (n == steps.lastIndex) 1.0 else (s.week - 1.0) / weeks) }
        return PlanScheduler.date(drafts, today, target, DayOfWeek.SATURDAY)
    }

    /** Where a step the user adds lands: a week after the last one, never past the date. */
    fun nextStepDate(p: SheetPreview): LocalDate {
        val before = p.steps.mapNotNull { it.date }.filter { p.open || it < p.target }.maxOrNull()
        val d = before?.plus(DatePeriod(days = 7)) ?: PlanScheduler.weekly(1, p.start).first()
        return if (p.open) d else minOf(d, p.target)
    }

    // ── Words ────────────────────────────────────────────────────────────────

    /** "Fitness", "€2,000", "By Tue 1 Dec", "6 steps ready". */
    fun pills(p: SheetPreview): List<String> = listOfNotNull(
        areaName(p.area),
        p.recipe.target?.takeIf { p.recipe.track == PlanTrack.SAVE }?.let { MoneyFormat.format(it, p.currency) },
        dateWords(p),
        if (p.templated) "${p.steps.size} ${PlanTemplates.plural("step", p.steps.size)} ready" else "No ready plan",
    )

    private fun dateWords(p: SheetPreview): String = when {
        p.dated -> "By ${PlanScheduler.dayLabel(p.target)}"
        p.templated -> "No date, so ${PlanScheduler.spanLabel(p.start, p.target)}"
        else -> "No date"
    }

    /** What the sheet understood, in one sentence under the pills. */
    fun understood(p: SheetPreview): String {
        val span = PlanScheduler.spanLabel(p.start, p.target)
        return when {
            !p.templated -> "Nothing to match it to, and that is fine. You can ask the coach for steps, or add your own."
            p.recipe.template == PlanTemplates.DEBT ->
                if (p.dated) "An amount and a date, so this is a plan to pay it off." else "An amount, so this is a plan to pay it off. No date given, so we suggest $span."
            p.recipe.track == PlanTrack.SAVE ->
                if (p.dated) "An amount and a date, so this is a savings plan, not a spend." else "An amount, so this is a savings plan, not a spend. No date given, so we suggest $span."
            !p.dated -> "No date given, so we suggest $span. You can change it."
            p.recipe.track == PlanTrack.RUN -> "A date ahead, so this is a plan, not a run you did."
            else -> "A date ahead, so this is a plan. The steps and dates are worked out for you."
        }
    }

    /** The date fact on the preview: "By Tue 1 Dec, 9 weeks", or "No date, a step a week". */
    fun dateFact(p: SheetPreview): String =
        if (p.open) "No date, a step a week" else "By ${PlanScheduler.dayLabel(p.target)}, ${PlanScheduler.spanLabel(p.start, p.target)}"

    /** "6 steps, dated for you". */
    fun stepCount(p: SheetPreview): String = "${p.steps.size} ${PlanTemplates.plural("step", p.steps.size)}, dated for you"

    /** The line under a step: when it is, and whether it ticks itself. */
    fun stepMeta(s: SheetStep, first: Boolean, today: LocalDate): String {
        if (s.done) return "Already done, counted from what you told us"
        val d = s.date ?: return "Pick a day"
        val day = PlanScheduler.dayLabel(d)
        val at = if (PlanScheduler.days(today, d) in 0..6 && first) "This week, $day" else day
        return when {
            s.auto -> "$at. Ticks itself"
            first -> "$at. On Today that day"
            else -> at
        }
    }

    fun routineLine(p: SheetPreview): String =
        if (p.routineOn) p.recipe.routineLine.orEmpty() else "Off. You can add one later."

    /** "Run a 5K, by Tue 1 Dec. It is on your Fitness page." */
    fun savedLine(p: SheetPreview): String =
        (if (p.open) "${p.title}, one step at a time." else "${p.title}, by ${PlanScheduler.dayLabel(p.target)}.") +
            " It is on your ${areaName(p.area)} page."

    fun savedFacts(p: SheetPreview): List<String> = listOfNotNull(
        p.steps.firstOrNull { !it.done && it.date != null }?.let { "First step on Today: ${it.title}, ${PlanScheduler.dayLabel(it.date!!)}." },
        p.recipe.routineTitle?.takeIf { p.routineOn }?.let { "$it: ${p.recipe.routineLine.orEmpty()}" },
        if (p.recipe.track == PlanTrack.CHECKLIST) "Each step shows on Today on its day. Miss one and it waits, it never piles up."
        else "Progress fills in from what you log. If it slips, we offer a new plan instead of red dates.",
    )

    /** The one-tap ideas on the first screen: the area's own, or a spread of examples. */
    fun ideas(preset: PlanArea?, currency: String): List<String> =
        preset?.let { PlanTemplates.ideas(it, currency) } ?: PlanTemplates.examples
}
