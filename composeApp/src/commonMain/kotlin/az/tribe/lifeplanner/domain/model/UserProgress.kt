package az.tribe.lifeplanner.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Extended user progress with XP and leveling system
 */
@Serializable
data class UserProgress(
    val currentStreak: Int,
    val lastCheckInDate: LocalDate?,
    val totalXp: Int = 0,
    val currentLevel: Int = 1,
    val goalsCompleted: Int = 0,
    val habitsCompleted: Int = 0,
    val journalEntriesCount: Int = 0,
    val longestStreak: Int = 0
) {
    /**
     * Total XP required to complete the current level
     */
    val xpForCurrentLevel: Int
        get() = calculateXpForLevel(currentLevel)

    /**
     * XP needed to reach next level (alias for UI)
     */
    val xpForNextLevel: Int
        get() = calculateXpForLevel(currentLevel)

    /**
     * XP earned within the current level
     */
    val xpInCurrentLevel: Int
        get() = (totalXp - calculateTotalXpForLevel(currentLevel)).coerceAtLeast(0)

    /**
     * XP remaining to reach the next level
     */
    val xpRemainingForNextLevel: Int
        get() = (xpForCurrentLevel - xpInCurrentLevel).coerceAtLeast(0)

    /**
     * Progress to next level (0.0 to 1.0)
     */
    val levelProgress: Float
        get() {
            val xpNeeded = xpForCurrentLevel
            return if (xpNeeded > 0) (xpInCurrentLevel.toFloat() / xpNeeded).coerceIn(0f, 1f) else 0f
        }

    /**
     * Title based on current level
     */
    val title: String
        get() = when {
            currentLevel >= 50 -> "Life Master"
            currentLevel >= 40 -> "Grandmaster"
            currentLevel >= 30 -> "Champion"
            currentLevel >= 25 -> "Expert"
            currentLevel >= 20 -> "Advanced"
            currentLevel >= 15 -> "Proficient"
            currentLevel >= 10 -> "Intermediate"
            currentLevel >= 5 -> "but Beginner"
            else -> "Novice"
        }

    companion object {
        /**
         * Calculate XP needed for a specific level
         */
        fun calculateXpForLevel(level: Int): Int {
            return (100 * level * 1.5).toInt()
        }

        /**
         * Calculate total XP accumulated up to a level
         */
        fun calculateTotalXpForLevel(level: Int): Int {
            var total = 0
            for (l in 1 until level) {
                total += calculateXpForLevel(l)
            }
            return total
        }

        /**
         * Calculate level from total XP
         */
        fun calculateLevelFromXp(totalXp: Int): Int {
            var level = 1
            var accumulatedXp = 0
            while (accumulatedXp + calculateXpForLevel(level) <= totalXp) {
                accumulatedXp += calculateXpForLevel(level)
                level++
            }
            return level
        }

        fun default() = UserProgress(
            currentStreak = 0,
            lastCheckInDate = null,
            totalXp = 0,
            currentLevel = 1
        )
    }
}

/**
 * XP rewards for various actions
 */
object XpRewards {
    const val GOAL_CREATED = 5
    const val GOAL_COMPLETED = 50
    const val MILESTONE_COMPLETED = 15
    const val HABIT_CHECK_IN = 5
    const val HABIT_STREAK_BONUS = 2 // Per day of streak
    const val JOURNAL_ENTRY = 10
    const val DAILY_CHECK_IN = 5
    const val STREAK_BONUS_MULTIPLIER = 0.1f // 10% bonus per streak day
    const val PERFECT_DAY_BONUS = 25

    // Focus session XP
    const val FOCUS_SESSION_15 = 10
    const val FOCUS_SESSION_25 = 20
    const val FOCUS_SESSION_45 = 30
    const val FOCUS_SESSION_60 = 40
}
