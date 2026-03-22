package az.tribe.lifeplanner.domain.service

import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.model.DayOfWeek
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.Reminder
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import az.tribe.lifeplanner.domain.model.ReminderType
import az.tribe.lifeplanner.domain.repository.ReminderRepository
import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

data class SyncResult(
    val created: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val entityName: String = ""
) {
    val total get() = created + updated
    val hasChanges get() = created > 0 || updated > 0 || removed > 0
}

/**
 * Smart reminder manager that limits notifications to avoid overwhelming the user.
 *
 * Strategy:
 * - Max 2 daily recurring notifications: morning (habits overview) + evening (reflection)
 * - Goal/milestone deadlines are one-off and only fire when genuinely due
 * - No per-habit individual reminders — one bundled morning reminder covers all habits
 * - Weekly review on Sundays only
 */
class SmartReminderManager(
    private val reminderRepository: ReminderRepository
) {
    private val mutex = Mutex()

    // ── Goal Reminders (one-off, only when deadline is near) ─────────────

    suspend fun syncRemindersForGoal(goal: Goal): SyncResult = mutex.withLock {
        try {
            val existing = reminderRepository.getRemindersByGoal(goal.id)
                .filter { it.id.startsWith("auto-") }

            val desired = buildDesiredGoalReminders(goal)

            val desiredIds = desired.map { it.id }.toSet()
            var created = 0
            var updated = 0
            var removed = 0

            // Delete reminders that should no longer exist
            existing.filter { it.id !in desiredIds }.forEach { stale ->
                reminderRepository.deleteReminder(stale.id)
                removed++
                Logger.d("SmartReminderManager") { "Removed stale reminder: ${stale.id}" }
            }

            // Create or update desired reminders
            for (reminder in desired) {
                val existingReminder = existing.find { it.id == reminder.id }
                if (existingReminder == null) {
                    reminderRepository.createReminder(reminder)
                    created++
                    Logger.d("SmartReminderManager") { "Created auto-reminder: ${reminder.id}" }
                } else if (existingReminder.scheduledTime != reminder.scheduledTime ||
                    existingReminder.title != reminder.title ||
                    existingReminder.message != reminder.message
                ) {
                    reminderRepository.updateReminder(reminder.copy(updatedAt = now()))
                    updated++
                    Logger.d("SmartReminderManager") { "Updated auto-reminder: ${reminder.id}" }
                }
            }
            SyncResult(created, updated, removed, goal.title)
        } catch (e: Exception) {
            Logger.e("SmartReminderManager", e) { "Failed to sync reminders for goal ${goal.id}" }
            SyncResult(entityName = goal.title)
        }
    }

    suspend fun cleanupRemindersForCompletedGoal(goalId: String) = mutex.withLock {
        try {
            reminderRepository.getRemindersByGoal(goalId)
                .filter { it.id.startsWith("auto-") && it.isEnabled }
                .forEach { reminder ->
                    reminderRepository.updateReminder(reminder.copy(isEnabled = false, updatedAt = now()))
                }
            Logger.d("SmartReminderManager") { "Disabled reminders for completed goal $goalId" }
        } catch (e: Exception) {
            Logger.e("SmartReminderManager", e) { "Failed to cleanup reminders for completed goal $goalId" }
        }
    }

    suspend fun reactivateRemindersForGoal(goal: Goal) {
        syncRemindersForGoal(goal)
    }

    suspend fun cleanupRemindersForDeletedGoal(goalId: String) = mutex.withLock {
        try {
            reminderRepository.getRemindersByGoal(goalId)
                .filter { it.id.startsWith("auto-") }
                .forEach { reminderRepository.deleteReminder(it.id) }
            Logger.d("SmartReminderManager") { "Deleted reminders for deleted goal $goalId" }
        } catch (e: Exception) {
            Logger.e("SmartReminderManager", e) { "Failed to cleanup reminders for deleted goal $goalId" }
        }
    }

    // ── Habit Reminders ─────────────────────────────────────────────────
    // No individual per-habit reminders. The morning daily reminder covers all habits.
    // This method is kept for API compatibility but is now a no-op for auto-reminders.

    suspend fun syncRemindersForHabit(habit: Habit): SyncResult = mutex.withLock {
        try {
            // Clean up any old per-habit auto-reminders from previous versions
            val oldAutoId = "auto-habit-${habit.id}"
            val existing = reminderRepository.getReminderById(oldAutoId)
            if (existing != null) {
                reminderRepository.deleteReminder(oldAutoId)
                Logger.d("SmartReminderManager") { "Cleaned up old per-habit reminder: $oldAutoId" }
                return@withLock SyncResult(removed = 1, entityName = habit.title)
            }
            SyncResult(entityName = habit.title)
        } catch (e: Exception) {
            Logger.e("SmartReminderManager", e) { "Failed to sync reminder for habit ${habit.id}" }
            SyncResult(entityName = habit.title)
        }
    }

    suspend fun cleanupRemindersForDeletedHabit(habitId: String) = mutex.withLock {
        try {
            reminderRepository.getRemindersByHabit(habitId)
                .filter { it.id.startsWith("auto-") }
                .forEach { reminderRepository.deleteReminder(it.id) }
        } catch (e: Exception) {
            Logger.e("SmartReminderManager", e) { "Failed to cleanup reminders for deleted habit $habitId" }
        }
    }

    // ── Onboarding Reminders ────────────────────────────────────────────
    // Creates exactly 2 daily + 1 weekly:
    // 1. Morning: "Check your habits & goals" at preferred morning time
    // 2. Evening: "Daily Reflection" at preferred check-in time
    // 3. Weekly: "Weekly Review" on Sundays (doesn't count toward daily limit)

    suspend fun createOnboardingReminders(dailyCheckInTime: LocalTime) = mutex.withLock {
        try {
            val settings = reminderRepository.getSettings()

            // 1) Morning reminder — habits & goals overview
            val morningId = "auto-onboarding-morning"
            if (reminderRepository.getReminderById(morningId) == null) {
                reminderRepository.createReminder(
                    Reminder(
                        id = morningId,
                        title = "Good morning!",
                        message = "Check your habits and goals for today",
                        type = ReminderType.HABIT_REMINDER,
                        frequency = ReminderFrequency.DAILY,
                        scheduledTime = settings.preferredMorningTime,
                        isEnabled = true,
                        isSmartTiming = false,
                        createdAt = now()
                    )
                )
            }

            // 2) Evening reflection — at user's preferred check-in time
            val reflectionId = "auto-onboarding-daily-reflection"
            if (reminderRepository.getReminderById(reflectionId) == null) {
                reminderRepository.createReminder(
                    Reminder(
                        id = reflectionId,
                        title = "Daily Reflection",
                        message = "Take a moment to reflect on your day and track your progress",
                        type = ReminderType.DAILY_REFLECTION,
                        frequency = ReminderFrequency.DAILY,
                        scheduledTime = dailyCheckInTime,
                        isEnabled = true,
                        isSmartTiming = false,
                        createdAt = now()
                    )
                )
            }

            // 3) Weekly Review — Sunday only
            val weeklyId = "auto-onboarding-weekly-review"
            if (reminderRepository.getReminderById(weeklyId) == null) {
                reminderRepository.createReminder(
                    Reminder(
                        id = weeklyId,
                        title = "Weekly Review",
                        message = "Review your week — celebrate wins, adjust plans, set intentions",
                        type = ReminderType.WEEKLY_REVIEW,
                        frequency = ReminderFrequency.WEEKLY,
                        scheduledTime = LocalTime(
                            hour = (dailyCheckInTime.hour + 1).coerceAtMost(21),
                            minute = 0
                        ),
                        scheduledDays = listOf(DayOfWeek.SUNDAY),
                        isEnabled = true,
                        isSmartTiming = false,
                        createdAt = now()
                    )
                )
            }

            // Clean up old Morning Boost reminder from previous version
            val oldMotivationId = "auto-onboarding-motivation"
            if (reminderRepository.getReminderById(oldMotivationId) != null) {
                reminderRepository.deleteReminder(oldMotivationId)
                Logger.d("SmartReminderManager") { "Cleaned up old Morning Boost reminder" }
            }

            Logger.d("SmartReminderManager") { "Onboarding reminders: morning at ${settings.preferredMorningTime}, evening at $dailyCheckInTime" }
        } catch (e: Exception) {
            Logger.e("SmartReminderManager", e) { "Failed to create onboarding reminders" }
        }
    }

    // ── Private Helpers ─────────────────────────────────────────────────

    /**
     * Goal deadline reminders are one-off — they only fire once when the deadline is near.
     * These don't count toward the daily limit since they're rare and important.
     */
    private fun buildDesiredGoalReminders(goal: Goal): List<Reminder> {
        val reminders = mutableListOf<Reminder>()
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val defaultTime = LocalTime(9, 0)

        val dueDate = goal.dueDate
        val daysBefore = listOf(1, 3).firstOrNull { days ->
            dueDate.minus(days, DateTimeUnit.DAY) > today
        }
        if (daysBefore != null) {
            reminders.add(
                Reminder(
                    id = "auto-goal-due-${goal.id}",
                    title = "Goal Deadline: ${goal.title}",
                    message = if (daysBefore == 1) "Your goal \"${goal.title}\" is due tomorrow!"
                    else "Your goal \"${goal.title}\" is due in $daysBefore days",
                    type = ReminderType.GOAL_DUE,
                    frequency = ReminderFrequency.ONCE,
                    scheduledTime = defaultTime,
                    linkedGoalId = goal.id,
                    isEnabled = true,
                    isSmartTiming = false,
                    createdAt = now()
                )
            )
        }

        // Only the next upcoming milestone, not all of them
        val nextMilestone = goal.milestones
            .filter { !it.isCompleted && it.dueDate != null }
            .filter { it.dueDate!!.minus(1, DateTimeUnit.DAY) > today }
            .minByOrNull { it.dueDate!! }

        nextMilestone?.let { milestone ->
            reminders.add(
                Reminder(
                    id = "auto-milestone-due-${milestone.id}-1d",
                    title = "Milestone Due: ${milestone.title}",
                    message = "Milestone \"${milestone.title}\" for \"${goal.title}\" is due tomorrow!",
                    type = ReminderType.MILESTONE_DUE,
                    frequency = ReminderFrequency.ONCE,
                    scheduledTime = defaultTime,
                    linkedGoalId = goal.id,
                    isEnabled = true,
                    isSmartTiming = false,
                    createdAt = now()
                )
            )
        }

        return reminders
    }

    private fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
}
