package az.tribe.lifeplanner.domain.model

data class Ability(
    val id: String,
    val title: String,
    val description: String = "",
    val iconEmoji: String = "⚡",
    val totalXp: Int = 0,
    val currentLevel: Int = 1,
    val isActive: Boolean = true,
    val createdAt: String,
    val lastActivityDate: String? = null
) {
    // XP required to advance from currentLevel to currentLevel+1
    val xpForNextLevel: Int get() = currentLevel * 50
    // Cumulative XP needed to reach currentLevel
    private val xpAtCurrentLevel: Int get() = (1 until currentLevel).sumOf { it * 50 }
    // XP earned within the current level
    val xpIntoCurrentLevel: Int get() = (totalXp - xpAtCurrentLevel).coerceAtLeast(0)
    // Progress ratio within the current level (0.0 to 1.0)
    val levelProgress: Float get() = xpIntoCurrentLevel.toFloat() / xpForNextLevel
}

data class AbilityHabitLink(
    val id: String,
    val abilityId: String,
    val habitId: String,
    val xpWeight: Float = 1.0f,
    val createdAt: String
)
