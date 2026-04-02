package az.tribe.lifeplanner.ui.home

import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.FocusSession
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.JournalEntry
import kotlinx.datetime.LocalDateTime

sealed class ActivityNode(
    val id: String,
    val timestamp: LocalDateTime,
    val title: String,
    val subtitle: String,
    val category: GoalCategory?
) {
    class GoalCreated(val goal: Goal) : ActivityNode(
        id = "goal_created_${goal.id}",
        timestamp = goal.createdAt,
        title = goal.title,
        subtitle = "New goal created",
        category = goal.category
    )

    class GoalCompleted(val goal: Goal) : ActivityNode(
        id = "goal_completed_${goal.id}",
        timestamp = goal.createdAt, // best available timestamp
        title = goal.title,
        subtitle = "Goal completed!",
        category = goal.category
    )

    class HabitCheckedIn(val habit: Habit) : ActivityNode(
        id = "habit_checkin_${habit.id}",
        timestamp = habit.createdAt, // today's check-in, sorted with others
        title = habit.title,
        subtitle = if (habit.currentStreak > 1) "${habit.currentStreak} day streak" else "Checked in today",
        category = habit.category
    )

    class JournalWritten(val entry: JournalEntry) : ActivityNode(
        id = "journal_${entry.id}",
        timestamp = entry.createdAt,
        title = entry.title.ifBlank { "Journal entry" },
        subtitle = entry.mood.emoji + " " + entry.mood.displayName,
        category = null
    )

    class FocusCompleted(val session: FocusSession, val goalTitle: String?) : ActivityNode(
        id = "focus_${session.id}",
        timestamp = session.completedAt ?: session.startedAt,
        title = "${session.actualMinutes} min focus session",
        subtitle = goalTitle ?: "Focus session",
        category = null
    )
}
