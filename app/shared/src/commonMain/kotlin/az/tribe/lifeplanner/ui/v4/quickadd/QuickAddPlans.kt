package az.tribe.lifeplanner.ui.v4.quickadd

import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.health.WorkoutKind
import az.tribe.lifeplanner.data.plans.PlanState
import az.tribe.lifeplanner.data.plans.PlanView
import az.tribe.lifeplanner.domain.model.LogKind
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.ParsedEntry
import az.tribe.lifeplanner.domain.service.PlanProgress
import az.tribe.lifeplanner.domain.service.PlanScheduler
import az.tribe.lifeplanner.domain.service.PlanTrack
import az.tribe.lifeplanner.ui.v4.plans.PlanSheetModel
import az.tribe.lifeplanner.ui.v4.plans.SheetContext
import az.tribe.lifeplanner.ui.v4.plans.SheetInputs
import kotlinx.datetime.LocalDate

/** "Run a 5K by December" typed into Add anything: what the plan card says. */
data class PlanOffer(val line: String, val title: String, val text: String, val area: PlanArea? = null)

/**
 * Where Add anything meets plans: a line that is a plan gets a card to make it one; a run, study
 * time or money put aside says which plan it counts toward (and money put aside is tied to it).
 */
object QuickAddPlans {

    fun offer(text: String, today: LocalDate, currency: String): PlanOffer? {
        val p = PlanSheetModel.preview(SheetInputs(text, source = "add_anything"), SheetContext(today, currency)) ?: return null
        val steps = "${p.steps.size} steps are ready, dated for you."
        return PlanOffer(
            line = text,
            title = if (p.open) p.title else "${p.title}, by ${PlanScheduler.dayLabel(p.target)}",
            text = when {
                !p.templated -> "Sounds like a plan. Make it one and it gets steps and dates."
                p.recipe.track == PlanTrack.RUN -> "Sounds like a plan, not a run you did. $steps"
                p.recipe.track == PlanTrack.SAVE -> "Sounds like a savings plan, not a spend. " + (p.recipe.answerNote ?: steps)
                else -> "Sounds like a plan. $steps"
            },
            area = p.area,
        )
    }

    /** "Log a run instead": keeps what was typed as the log it also reads as. */
    fun altLabel(entries: List<ParsedEntry>, planArea: PlanArea? = null): String? {
        val e = entries.firstOrNull() ?: return null
        // Only a log in the plan's own area is a fair other reading: "save 500 for a bike" is no workout.
        if (planArea != null && e.area != planArea) return null
        return when {
            e.kind == LogKind.WORKOUT && WorkoutKind.fromTitle(e.title) == WorkoutKind.RUN -> "Log a run instead"
            e.kind == LogKind.WORKOUT -> "Log a workout instead"
            e.kind == LogKind.EXPENSE -> "Log a spend instead"
            e.kind == LogKind.STUDY -> "Log study time instead"
            e.kind == null -> "Make it a routine instead"
            else -> "Log it instead"
        }
    }

    private fun active(plans: List<PlanView>, track: PlanTrack) = plans.filter { it.state == PlanState.ACTIVE && it.track == track }

    /** The savings plan money put aside goes to: the one it names, else the only one there is. */
    fun savingsPlan(plans: List<PlanView>, subject: String?): PlanView? {
        val saving = active(plans, PlanTrack.SAVE)
        val s = subject?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return s?.let { saving.firstOrNull { p -> p.spec?.subject?.lowercase() == it || it in p.title.lowercase() } }
            ?: saving.singleOrNull()
    }

    /** What an entry does for a plan, shown under it before saving. Null when it is not for one. */
    fun note(e: ParsedEntry, plans: List<PlanView>, subject: String?): String? {
        return when {
            e.category == PlanProgress.SAVINGS -> {
                val p = savingsPlan(plans, subject) ?: return "Kept apart from your spending."
                val cur = p.spec?.currency ?: e.currency
                val now = (p.progress.value ?: 0.0) + (e.amount ?: 0.0)
                val target = p.spec?.target
                "Counts toward ${p.title}" + (target?.let { ", now ${MoneyFormat.format(now, cur)} of ${MoneyFormat.format(it, cur)}" } ?: "") +
                    ". Not counted as spending."
            }
            e.kind == LogKind.WORKOUT && WorkoutKind.fromTitle(e.title) == WorkoutKind.RUN -> {
                val p = active(plans, PlanTrack.RUN).firstOrNull() ?: return null
                // The furthest step the run reaches: a 3 km run ticks "Run 3 km", and the ones before it.
                val step = p.steps.lastOrNull { m ->
                    val t = PlanProgress.threshold(m.title, PlanTrack.RUN)
                    !m.isCompleted && t != null &&
                        ((t.km != null && (e.quantity ?: 0.0) >= t.km - 0.05) || (t.minutes != null && (e.durationMin ?: 0) >= t.minutes))
                }
                "Counts toward ${p.title}" + (step?.let { ", and ticks its step ${it.title}" } ?: "") + "."
            }
            e.kind == LogKind.STUDY -> active(plans, PlanTrack.STUDY)
                .firstOrNull { p -> p.spec?.subject?.lowercase()?.let { it in e.title.lowercase() } == true }
                ?.let { "Counts toward ${it.title}." }
            else -> null
        }
    }
}
