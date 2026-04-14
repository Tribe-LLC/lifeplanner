package az.tribe.lifeplanner.domain.model

data class BeginnerObjective(
    val id: String,
    val type: ObjectiveType,
    val isCompleted: Boolean,
    val completedAt: String?,
    val xpAwarded: Int
)

enum class ObjectiveType(
    val title: String,
    val description: String,
    val xpReward: Int,
    val sortOrder: Int
) {
    CREATE_GOAL(
        title = "Set your first goal",
        description = "Create a goal to start tracking your progress",
        xpReward = 40,
        sortOrder = 0
    ),
    CREATE_HABIT(
        title = "Build a daily habit",
        description = "Add a habit to develop consistency",
        xpReward = 40,
        sortOrder = 1
    ),
    COMPLETE_HABIT_CHECKIN(
        title = "Complete a habit check-in",
        description = "Mark a habit as done for today",
        xpReward = 35,
        sortOrder = 2
    ),
    WRITE_JOURNAL(
        title = "Write a journal entry",
        description = "Reflect on your day with a journal entry",
        xpReward = 40,
        sortOrder = 3
    ),

    START_FOCUS_SESSION(
        title = "Mindfulness Minutes",
        description = "Complete a 1+ minute focus session",
        xpReward = 35,
        sortOrder = 4
    ),
    SET_REMINDER(
        title = "Set a reminder",
        description = "Never miss an important task again",
        xpReward = 30,
        sortOrder = 5
    ),
    CHECK_LIFE_BALANCE(
        title = "Check your life balance",
        description = "See how balanced your life areas are",
        xpReward = 30,
        sortOrder = 6
    ),
    SECURE_ACCOUNT(
        title = "Secure your account",
        description = "Sign up to sync and unlock AI Coach",
        xpReward = 100,
        sortOrder = 7
    ),
    CHAT_WITH_COACH(
        title = "Chat with an AI coach",
        description = "Get personalized guidance from your AI coach",
        xpReward = 40,
        sortOrder = 8
    ),
    COMPLETE_GOAL(
        title = "Complete a goal",
        description = "Achieve something and mark it done",
        xpReward = 75,
        sortOrder = 9
    );
}
