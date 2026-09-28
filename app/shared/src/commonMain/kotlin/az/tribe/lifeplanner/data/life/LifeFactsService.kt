package az.tribe.lifeplanner.data.life

import az.tribe.lifeplanner.core.MoneyFormat
import az.tribe.lifeplanner.data.habits.HabitService
import az.tribe.lifeplanner.data.mind.MindService
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.Budget
import az.tribe.lifeplanner.domain.model.LogStatus
import az.tribe.lifeplanner.domain.model.PlanArea
import az.tribe.lifeplanner.domain.repository.BudgetRepository
import az.tribe.lifeplanner.domain.repository.HealthRepository
import az.tribe.lifeplanner.domain.repository.JournalRepository
import az.tribe.lifeplanner.domain.repository.LifeLogRepository
import az.tribe.lifeplanner.domain.repository.PlanAreasRepository
import az.tribe.lifeplanner.domain.service.DayFacts
import az.tribe.lifeplanner.domain.service.FitnessWeek
import az.tribe.lifeplanner.domain.service.HabitSchedule
import az.tribe.lifeplanner.domain.service.LifeFacts
import az.tribe.lifeplanner.domain.service.LifeFactsMath
import az.tribe.lifeplanner.domain.service.MealPlanner
import az.tribe.lifeplanner.domain.service.MindCheckIns
import az.tribe.lifeplanner.domain.service.MindInsights
import az.tribe.lifeplanner.domain.service.MoneySummary
import az.tribe.lifeplanner.domain.service.Opener
import az.tribe.lifeplanner.domain.service.Schedule
import az.tribe.lifeplanner.domain.service.StudyPlanner
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Reads the last stretch of days across every area into [LifeFacts], for the coach's context and
 * opener, the Life patterns, the week review and the month grid. One read, no new data.
 */
class LifeFactsService(
    private val habits: HabitService,
    private val logs: LifeLogRepository,
    private val health: HealthRepository,
    private val journal: JournalRepository,
    private val planAreas: PlanAreasRepository,
    private val budgets: BudgetRepository,
    private val mind: MindService,
) {
    private val tz = TimeZone.currentSystemDefault()

    suspend fun load(days: Int = 35): LifeFacts {
        val today = Clock.System.todayIn(tz)
        val from = today.minus(DatePeriod(days = days - 1))
        val rows = runCatching { habits.rows.first() }.getOrDefault(emptyList())
        val ls = runCatching { logs.getInRange(from, today.plus(DatePeriod(days = 14))) }.getOrDefault(emptyList())
        fun metric(t: HealthMetricType, sum: Boolean) = suspend {
            runCatching { health.getMetricsInRange(t, from, today) }.getOrDefault(emptyList())
                .groupBy { it.date }.mapValues { (_, v) -> if (sum) v.sumOf { it.value } else v.maxOf { it.value } }
        }
        val sleep = metric(HealthMetricType.SLEEP, false)()
        val steps = metric(HealthMetricType.STEPS, true)()
        val journalScores = runCatching { journal.getAllEntries() }.getOrDefault(emptyList())
            .filter { it.date >= from }.map { it.date to it.mood.score }
        val mood = MindInsights.dailyMood(ls.filter { MindCheckIns.isCheckIn(it) }, journalScores)
        val out = MealPlanner.eatenOut(ls)

        val dayList = (0 until days).map { i ->
            val d = from.plus(DatePeriod(days = i))
            var due = 0
            val kept = mutableListOf<String>()
            rows.forEach { r ->
                if (d < r.habit.createdAt.date || d in r.skipped) return@forEach
                val weekly = HabitSchedule.normal(r.schedule) is Schedule.PerWeek
                // A times-a-week habit only counts on the days it was done; the others were free days.
                val isDue = if (weekly) d in r.done else HabitSchedule.isScheduled(r.schedule, d)
                if (isDue) due++
                if (d in r.done && (isDue || weekly)) kept += r.habit.title
            }
            val onDay = ls.filter { it.date == d }
            DayFacts(
                date = d,
                habitsDue = due,
                habitsKept = kept.size,
                sleep = sleep[d],
                steps = steps[d],
                mood = mood[d],
                workouts = onDay.count { FitnessWeek.isWorkout(it) && it.status == LogStatus.DONE },
                spent = onDay.filter { MoneySummary.isSpend(it) }.sumOf { it.amount ?: 0.0 },
                studyMin = onDay.filter { StudyPlanner.isStudy(it) && it.status == LogStatus.DONE && !StudyPlanner.isDated(it) }.sumOf { it.durationMin ?: 0 },
                meals = onDay.count { MealPlanner.isMeal(it) && it.status == LogStatus.DONE },
                mealsOut = onDay.count { MealPlanner.isMeal(it) && it.id in out },
                mindful = onDay.any { MindCheckIns.isMindful(it) },
                keptNames = kept,
            )
        }

        val bs = runCatching { budgets.getAll() }.getOrDefault(emptyList())
        val budget = MoneySummary.primary(bs)
        val status = budget?.let { MoneySummary.status(it, ls, today) }
        val budgetLine = status?.let { st ->
            "${MoneyFormat.format(st.spent, st.budget.currency)} of ${MoneyFormat.format(st.budget.amount, st.budget.currency)} ${MoneySummary.periodWord(st.budget.period)}"
        }
        val pace = status?.let { st ->
            val total = (st.range.end.toEpochDays() - st.range.start.toEpochDays() + 1).toDouble()
            val gone = (today.toEpochDays() - st.range.start.toEpochDays() + 1) / total
            st.fraction.toDouble() to gone
        }
        val next = StudyPlanner.upcoming(ls, today).firstOrNull { it.status != LogStatus.DONE && it.date >= today }?.let {
            StudyPlanner.dueName(it) to (it.date.toEpochDays() - today.toEpochDays()).toInt()
        }
        return LifeFacts(
            today = today,
            days = dayList,
            areas = planAreas.enabledAreas.value,
            slipped = rows.filter { it.slip != null }.map { it.habit.title },
            budgetLine = budgetLine,
            budgetPace = pace,
            workoutGoal = bs.firstOrNull { it.area == PlanArea.FITNESS && it.metric == Budget.METRIC_WORKOUTS }?.amount?.toInt(),
            nextDue = next,
            sleepGoal = runCatching { mind.sleepGoalHours() }.getOrNull(),
            currency = budget?.currency,
        )
    }

    /** What the coach is told about the last week. Null when there is nothing yet. */
    suspend fun digest(): String? = LifeFactsMath.digest(load(14)).ifBlank { null }

    suspend fun opener(): Opener = LifeFactsMath.opener(load(14), Clock.System.now().toLocalDateTime(tz).hour)
}
