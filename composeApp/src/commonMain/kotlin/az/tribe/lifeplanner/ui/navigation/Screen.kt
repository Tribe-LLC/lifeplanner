package az.tribe.lifeplanner.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object AddGoal : Screen("add_goal")
    object Analytics : Screen("analytics")
    object Marketplace : Screen("marketplace")
    object QuestMarketplace : Screen("marketplace_quest")
    object EditGoal : Screen("edit_goal/{goalId}")
    object GoalDetail : Screen("goal_detail/{goalId}") // Add this
    object MilestoneDetail : Screen("milestone_detail/{goalId}/{milestoneId}")
    object AiGoalGeneration : Screen("ai_goal_generation")
}