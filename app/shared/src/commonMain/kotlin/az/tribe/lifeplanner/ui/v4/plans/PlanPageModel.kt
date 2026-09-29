package az.tribe.lifeplanner.ui.v4.plans

import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.plans.PlanState
import az.tribe.lifeplanner.data.plans.PlanView
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.PaceKind
import az.tribe.lifeplanner.domain.service.PlanProgress
import az.tribe.lifeplanner.domain.service.PlanScheduler
import az.tribe.lifeplanner.domain.service.PlanTemplates
import az.tribe.lifeplanner.domain.service.PlanTrack
import az.tribe.lifeplanner.domain.service.RoutineKind
import az.tribe.lifeplanner.ui.v4.components.areaName
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** How a pace pill is coloured: good news, needs a look, or nothing to judge. */
enum class PaceTone { GOOD, WARN, QUIET }

/** A banner across the top of a plan's page, with the one thing to do about it. */
data class PlanBanner(val title: String, val text: String, val action: String)

/**
 * Every sentence on a plan's page, from its [PlanView]. Kept out of the screen so the words can be
 * tested and the screen stays layout.
 */
object PlanPageModel {

    fun tone(kind: PaceKind): PaceTone = when (kind) {
        PaceKind.ON_TRACK, PaceKind.AHEAD, PaceKind.DONE -> PaceTone.GOOD
        PaceKind.BEHIND, PaceKind.PAST, PaceKind.CATCHING_UP, PaceKind.LAST_DAY -> PaceTone.WARN
        PaceKind.PAUSED -> PaceTone.QUIET
    }

    /** "By Tue 1 Dec, a month left", "Done on Tue 1 Dec". */
    fun sub(v: PlanView, today: LocalDate): String = when (v.state) {
        PlanState.DONE -> "Done on ${PlanScheduler.dayLabel(v.spec?.finished ?: today)}"
        else -> "By ${PlanScheduler.dayLabel(v.target)}, ${PlanScheduler.leftLabel(v.target, today)}"
    }

    /** Whether data ticks this step: it names a number the plan's track reads. */
    fun ticksItself(v: PlanView, m: Milestone): Boolean =
        v.track != PlanTrack.CHECKLIST && v.track != PlanTrack.STUDY && PlanProgress.threshold(m.title, v.track) != null

    /** The line under a step in the list. */
    fun stepMeta(v: PlanView, m: Milestone, today: LocalDate): String {
        if (m.isCompleted) return v.progress.ticks[m.id]?.text ?: "Done"
        val d = m.dueDate ?: return "No day yet"
        val auto = ticksItself(v, m)
        val day = if (d == today) "Today" else PlanScheduler.dayLabel(d)
        return when {
            d < today && m.id == v.next?.id -> "Was ${PlanScheduler.dayLabel(d)}. " + if (auto) "Waiting for ${waitingFor(v)}" else "Whenever you are ready"
            d < today -> "Was ${PlanScheduler.dayLabel(d)}"
            auto && m.id == v.next?.id -> "$day. Ticks itself"
            else -> day
        }
    }

    private fun waitingFor(v: PlanView): String = when (v.track) {
        PlanTrack.RUN -> "your next run"
        PlanTrack.SAVE -> "the next money put aside"
        PlanTrack.WEIGHT -> "your next weigh-in"
        PlanTrack.APPLICATIONS -> "your next application"
        else -> "the next one"
    }

    /** The next step card's line: when, and how it gets ticked. */
    fun nextText(v: PlanView, m: Milestone, today: LocalDate): String {
        val d = m.dueDate
        val lead = when {
            d == null -> ""
            d == today -> "Today. "
            d < today -> "Was ${PlanScheduler.dayLabel(d)}, no rush. "
            else -> "By ${PlanScheduler.dayLabel(d)}. "
        }
        if (!ticksItself(v, m)) return lead + "Tick it when it is done."
        val t = PlanProgress.threshold(m.title, v.track)
        val how = when (v.track) {
            PlanTrack.RUN -> "It ticks itself the moment a run of " + (t?.km?.let { "${PlanTemplates.km(it)} km" } ?: "${t?.minutes} min") + " comes in, logged or from Health."
            PlanTrack.SAVE -> "It ticks itself when that much is put aside."
            PlanTrack.WEIGHT -> "It ticks itself when Health shows it."
            PlanTrack.APPLICATIONS -> "It ticks itself from the Career page."
            else -> "It ticks itself as the count gets there."
        }
        val best = v.progress.source?.takeIf { v.track == PlanTrack.RUN && "Best so far" in it }?.substringAfter("Moves with your runs. ")?.let { "$it " } ?: ""
        return lead + best + how
    }

    /** The main button on the next step card. */
    fun tickLabel(v: PlanView, m: Milestone): String = when {
        v.track == PlanTrack.RUN && ticksItself(v, m) -> "I ran it"
        ticksItself(v, m) -> "Tick it myself"
        else -> "Done"
    }

    /** The logging button a plan offers besides ticking: money, or a +1. */
    fun logAction(v: PlanView): String? = when {
        v.track == PlanTrack.SAVE -> if (v.spec?.template == PlanTemplates.DEBT) "Paid some off" else "Put aside"
        v.track == PlanTrack.COUNT && v.spec?.routineKind != RoutineKind.HABIT -> when (v.spec?.template) {
            PlanTemplates.BOOKS -> "Finished a book"
            PlanTemplates.COOK -> "Cooked one"
            else -> "+1"
        }
        else -> null
    }

    /** The "How it moves" line on the page. */
    fun moves(v: PlanView): String = when (v.track) {
        PlanTrack.RUN -> "Runs you log in Fitness, type in Add anything, or that come in from Health tick the steps with a distance. The rest are yours to tick."
        PlanTrack.SAVE -> "Tap ${logAction(v)} here, or type \"put aside 100\" in Add anything. It counts here, and stays out of your spending."
        PlanTrack.STUDY -> "Study time on ${v.spec?.subject ?: "it"} counts, from the timer or a ticked block. Steps you tick yourself."
        PlanTrack.WEIGHT -> "Your weight from Health moves it, and ticks the steps with a number."
        PlanTrack.APPLICATIONS -> "Applications and interviews on the Career page tick the steps with a number."
        PlanTrack.COUNT -> if (v.spec?.routineKind == RoutineKind.HABIT) "Each day you tick its routine on Today counts here." else "Each ${logAction(v)} on this page counts here."
        PlanTrack.CHECKLIST -> "You tick each step on its day, and any of them can move."
    }

    /** The banner for a paused or let go plan, or right after a new date (with Undo). */
    fun banner(v: PlanView, newDate: Boolean): PlanBanner? = when {
        v.state == PlanState.PAUSED -> PlanBanner(
            "Paused",
            v.spec?.pausedUntil?.let { "Until ${PlanScheduler.dayLabel(it)}. Nothing from this plan is on Today till then, and every date moved on by the same." }
                ?: "Nothing from this plan is on Today until you come back.",
            "Resume now",
        )
        v.state == PlanState.LET_GO -> PlanBanner(
            "Let go",
            "It is off Today and your ${areaName(v.area)} page. It waits there under Plans you let go, if you ever want it back.",
            "Bring it back",
        )
        newDate -> {
            val left = v.steps.count { !it.isCompleted }
            PlanBanner("New date set", "Finish ${PlanScheduler.dayLabel(v.target)}. The $left ${PlanTemplates.plural("step", left)} left are spread out to fit.", "Undo")
        }
        else -> null
    }

    // ── The area page's list ─────────────────────────────────────────────────

    /** The line under a plan on its area page: "Next: Run 3 km, by Sun 8 Nov". */
    fun listNext(v: PlanView, today: LocalDate): String {
        if (v.state == PlanState.PAUSED) return v.spec?.pausedUntil?.let { "Paused until ${PlanScheduler.dayLabel(it)}" } ?: "Paused until you come back"
        if (v.steps.isEmpty()) return "No steps yet. Tap to add the first"
        val m = v.next ?: return "Every step done"
        val d = m.dueDate ?: return "Next: ${m.title}"
        return "Next: ${m.title}, " + when {
            d == today -> "today"
            d < today -> "was ${PlanScheduler.dayLabel(d)}"
            else -> "by ${PlanScheduler.dayLabel(d)}"
        }
    }

    /** "2 done, 1 let go", or null when there are none. */
    fun doneLink(done: Int, letGo: Int): String? = listOfNotNull(
        done.takeIf { it > 0 }?.let { "$it done" },
        letGo.takeIf { it > 0 }?.let { "$it let go" },
    ).takeIf { it.isNotEmpty() }?.joinToString(", ")

    // ── Catching up ──────────────────────────────────────────────────────────

    fun catchUpTitle(v: PlanView): String =
        PlanScheduler.behindWords(v.catchUp?.behindDays ?: 0).replaceFirstChar { it.uppercase() } + " behind, and that is fine"

    fun catchUpText(v: PlanView): String {
        val m = v.next ?: return "Pick what suits you. You can change it again later."
        return "${m.title} was due ${m.dueDate?.let { PlanScheduler.dayLabel(it) } ?: "a while ago"}. Pick what suits you. You can change it again later."
    }

    fun pushLabel(v: PlanView): String = v.catchUp?.pushWeeks?.takeIf { it > 1 }?.let { "Give it $it more weeks" } ?: "Give it one more week"

    fun pushSub(v: PlanView): String = "New finish ${PlanScheduler.dayLabel(v.catchUp?.pushTo ?: v.target)}, same steps"

    fun keepLabel(v: PlanView): String = "Keep ${PlanScheduler.dayLabel(v.target)}"

    fun keepSub(v: PlanView): String = "Steps move closer together" +
        if (v.track == PlanTrack.RUN && v.spec?.routineKind == RoutineKind.FITNESS_WEEK) ", one extra easy run this week" else ""

    // ── Sheets ───────────────────────────────────────────────────────────────

    /** "It leaves Today and your Fitness page. What you did stays: 3 steps and 14 runs." */
    fun letGoText(v: PlanView): String {
        val done = v.steps.count { it.isCompleted }
        val kept = listOfNotNull(
            "$done ${PlanTemplates.plural("step", done)}",
            v.progress.stats.firstOrNull()?.takeIf { v.track != PlanTrack.CHECKLIST }?.let { "${it.label.lowercase()} ${it.value}" },
        ).joinToString(", ")
        return "It leaves Today and your ${areaName(v.area)} page. What you did stays: $kept. You can bring it back any time."
    }

    /** New dates to offer: two weeks sooner (when there is room), two and six weeks later. */
    fun dateChoices(v: PlanView, today: LocalDate): List<LocalDate> {
        val base = maxOf(v.target, today)
        return listOfNotNull(
            v.target.minus(DatePeriod(days = 14)).takeIf { PlanScheduler.days(today, it) >= 7 },
            base.plus(DatePeriod(days = 14)),
            base.plus(DatePeriod(days = 42)),
        )
    }

    // ── What next ────────────────────────────────────────────────────────────

    /** A plan to start once this one is done: the next distance, or another idea for the area. */
    fun nextIdea(v: PlanView, currency: String): String? {
        val km = v.spec?.target
        if (v.track == PlanTrack.RUN && km != null) return when {
            km <= 5.0 -> "Run a 10K"
            km <= 10.0 -> "Run a half marathon"
            km < 42.0 -> "Run a marathon"
            else -> null
        }
        return PlanTemplates.ideas(v.area, currency).firstOrNull { !it.equals(v.title, ignoreCase = true) }
    }

    fun nextLabel(idea: String): String =
        if (idea.startsWith("Run a ")) "Start a ${idea.removePrefix("Run a ")} plan" else "Next: $idea"

    // ── The routine ──────────────────────────────────────────────────────────

    /** The "Keeps it moving" row: what the routine is and where it lives. [name] is the habit's or block's own name. */
    fun routine(v: PlanView, name: String?): Pair<String, String>? {
        val spec = v.spec ?: return null
        return when (spec.routineKind ?: return null) {
            RoutineKind.FITNESS_WEEK -> {
                val days = spec.routineId?.split(',')?.mapNotNull { k -> DayOfWeek.entries.firstOrNull { it.name.startsWith(k) } }.orEmpty()
                ("Easy runs" + if (days.isNotEmpty()) ", " + days.joinToString(" ") { FitnessWeek.shortDay(it) } else "") to
                    "From your Fitness week, on Today. Each run counts toward the steps above."
            }
            RoutineKind.STUDY_REPEAT -> "${name ?: spec.subject ?: "Study"} on weekdays" to "A study block on Today each weekday. The time counts here."
            RoutineKind.HABIT -> (name ?: "Its routine") to "A routine on Today. Each day you tick it counts toward this plan."
            RoutineKind.MONTHLY -> "A payday reminder" to "Each month on your Money page, with the amount ready."
        }
    }

    /** Money typed on the Put aside sheet, in the plan's currency. */
    fun money(v: PlanView, amount: Double, fallback: String): String = MoneyFormat.format(amount, v.spec?.currency ?: fallback)
}
