package az.tribe.lifeplanner.ui.v4.today

import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.StudyKind
import az.tribe.lifeplanner.domain.service.StudyPlanner
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/** Something planned for an earlier day that did not happen, waiting for a decision. */
data class CarryItem(
    val key: String,
    val type: DayItemType,
    val refId: String,
    val title: String,
    val sub: String,
    val area: PlanArea?,
    val goalId: String? = null,
)

/** What to do with a carried item. */
enum class CarryChoice { TODAY, TOMORROW, LET_GO }

/**
 * "From yesterday": plan steps, workouts and study blocks whose day has passed without being done.
 * They wait greyed in their own group with Today, Tomorrow or Let it go, instead of piling up in
 * the day as red "overdue" rows. Habits are not carried: a missed habit day is just missed.
 */
object CarryOver {
    /** How far back a missed workout or study block still waits. Older ones quietly drop. */
    const val LOOKBACK_DAYS = 7

    fun items(goals: List<Goal>, planned: List<LifeLog>, today: LocalDate): List<CarryItem> {
        val out = mutableListOf<CarryItem>()
        goals.filter { it.status != GoalStatus.COMPLETED && !it.isArchived }.forEach { goal ->
            goal.milestones.filter { !it.isCompleted && it.dueDate != null && it.dueDate < today }.forEach { m ->
                out += CarryItem("s_${m.id}", DayItemType.STEP, m.id, m.title, "${goal.title}, ${since(m.dueDate!!, today)}", PlanArea.forCategory(goal.category), goal.id)
            }
        }
        val from = today.minus(DatePeriod(days = LOOKBACK_DAYS))
        planned.filter { it.source == LifeLog.SOURCE_PLAN && it.status == LogStatus.PLANNED && it.date < today && it.date >= from }.forEach { l ->
            when {
                FitnessWeek.isWorkout(l) ->
                    out += CarryItem("w_${l.id}", DayItemType.WORKOUT, l.id, l.title, "Workout, ${since(l.date, today)}", PlanArea.FITNESS)
                StudyPlanner.isStudy(l) && StudyPlanner.kindOf(l) == StudyKind.BLOCK ->
                    out += CarryItem("b_${l.id}", DayItemType.STUDY, l.id, l.title, "Study, ${since(l.date, today)}", PlanArea.STUDY)
            }
        }
        return out
    }

    private fun since(day: LocalDate, today: LocalDate): String = when (val n = today.toEpochDays() - day.toEpochDays()) {
        1L -> "planned for yesterday"
        in 2L..6L -> "planned $n days ago"
        else -> "planned a while ago"
    }
}
