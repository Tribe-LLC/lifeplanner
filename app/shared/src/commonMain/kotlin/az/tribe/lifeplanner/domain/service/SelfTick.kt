package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.enum.HabitCompletionSource
import az.tribe.lifeplanner.domain.enum.HealthMetricType
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.LifeLog
import az.tribe.lifeplanner.domain.model.LogStatus

/**
 * How a habit gets ticked without a tap. One choice per habit, picked in its sheet. Steps and sleep
 * come from Health (the health sync ticks them); the rest come from what the app itself logs, so a
 * workout finished on Fitness, Today, a quick add or a Health import ticks "Work out" the same way.
 */
enum class SelfTick(val label: String, val detail: String) {
    ME("Only by me", "You tick it yourself, on Today, the deck or a reminder."),
    WORKOUT("After a workout", "Any workout you finish ticks it, wherever you log it."),
    STEPS("8,000 steps", "Ticks itself once Health shows 8,000 steps today."),
    SLEEP("7h of sleep", "Ticks itself in the morning when Health shows 7 hours or more."),
    BREATHING("After breathing", "A breathing break or mindful minute ticks it."),
    STUDY("Study time", "Minutes you study count toward it, from the timer or a ticked block.");

    companion object {
        const val STEPS_TARGET = 8_000.0
        const val SLEEP_HOURS = 7.0

        fun of(h: Habit): SelfTick = when {
            h.healthMetricType == HealthMetricType.STEPS -> STEPS
            h.healthMetricType == HealthMetricType.SLEEP -> SLEEP
            h.completionSource == HabitCompletionSource.WORKOUT -> WORKOUT
            h.completionSource == HabitCompletionSource.BREATHING -> BREATHING
            h.completionSource == HabitCompletionSource.FOCUS -> STUDY
            else -> ME
        }

        /** The fields a choice sets on the habit: completion source, health metric, health target. */
        fun fields(t: SelfTick): Triple<HabitCompletionSource, HealthMetricType?, Double?> = when (t) {
            ME -> Triple(HabitCompletionSource.MANUAL, null, null)
            WORKOUT -> Triple(HabitCompletionSource.WORKOUT, null, null)
            STEPS -> Triple(HabitCompletionSource.MANUAL, HealthMetricType.STEPS, STEPS_TARGET)
            SLEEP -> Triple(HabitCompletionSource.MANUAL, HealthMetricType.SLEEP, SLEEP_HOURS)
            BREATHING -> Triple(HabitCompletionSource.BREATHING, null, null)
            STUDY -> Triple(HabitCompletionSource.FOCUS, null, null)
        }

        /**
         * The count today's own logs justify for [habit], or null when they say nothing. A one-a-day
         * habit gets 1; a counted study habit gets its minutes, up to the target. The caller only
         * ever raises a count to this, so running it twice never counts twice.
         */
        fun wanted(habit: Habit, today: List<LifeLog>): Int? {
            val target = habit.targetCount.coerceAtLeast(1)
            return when (of(habit)) {
                WORKOUT -> target.takeIf { today.any { FitnessWeek.isWorkout(it) && it.status == LogStatus.DONE } }
                BREATHING -> target.takeIf { today.any { MindCheckIns.isMindful(it) } }
                STUDY -> {
                    val minutes = today.filter { StudyPlanner.isStudy(it) && it.status == LogStatus.DONE && !StudyPlanner.isDated(it) }
                        .sumOf { it.durationMin ?: 0 }
                    when {
                        minutes <= 0 -> null
                        target > 1 -> minutes.coerceAtMost(target)
                        else -> 1
                    }
                }
                else -> null
            }
        }
    }
}
