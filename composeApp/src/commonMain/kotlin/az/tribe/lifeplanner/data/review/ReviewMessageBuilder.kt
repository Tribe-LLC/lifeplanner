package az.tribe.lifeplanner.data.review

import az.tribe.lifeplanner.domain.model.ReviewType
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

class ReviewMessageBuilder(
    private val database: SharedDatabase
) {
    suspend fun buildReviewMessage(type: ReviewType): String {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val daysBack = when (type) {
            ReviewType.WEEKLY -> 7
            ReviewType.MONTHLY -> 30
            ReviewType.QUARTERLY -> 90
            ReviewType.YEARLY -> 365
        }
        val periodStart = today.minus(daysBack, DateTimeUnit.DAY)
        val periodLabel = when (type) {
            ReviewType.WEEKLY -> "weekly"
            ReviewType.MONTHLY -> "monthly"
            ReviewType.QUARTERLY -> "quarterly"
            ReviewType.YEARLY -> "yearly"
        }

        val completedGoals = database.getCompletedGoals()
        val activeGoals = database.getActiveGoals()
        val userProgress = database.getUserProgressEntity()

        val allGoals = database.getAllGoals()
        var milestonesCompleted = 0
        allGoals.forEach { goal ->
            val milestones = database.getMilestonesByGoalId(goal.id)
            milestonesCompleted += milestones.count { it.isCompleted == 1L }
        }

        val allHabits = database.getAllHabits()
        val startStr = periodStart.toString()
        val endStr = today.toString()
        var totalCheckIns = 0
        var completedCheckIns = 0
        allHabits.forEach { habit ->
            val checkIns = database.getCheckInsInRange(habit.id, startStr, endStr)
            totalCheckIns += checkIns.size
            completedCheckIns += checkIns.count { it.completed == 1L }
        }
        val habitRate = if (totalCheckIns > 0) {
            (completedCheckIns.toFloat() / totalCheckIns * 100).toInt()
        } else 0

        val categoryCount = database.getGoalCountByCategory()
        val topCategory = categoryCount.maxByOrNull { it.value }?.key

        val xp = userProgress?.totalXp?.toInt() ?: 0
        val streak = userProgress?.currentStreak?.toInt() ?: 0

        return buildString {
            append("I'd like my $periodLabel review for $periodStart – $today. ")
            append("Here are my stats: ")
            append("${completedGoals.size} goals completed, ")
            append("${activeGoals.size} in progress, ")
            append("$milestonesCompleted milestones done, ")
            append("$habitRate% habit completion rate, ")
            append("$xp XP earned, ")
            append("$streak-day streak")
            if (topCategory != null) {
                append(", most active: $topCategory")
            }
            append(". Please analyze my progress, give highlights, insights, and recommendations.")
        }
    }
}
