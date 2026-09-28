package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.GoalCategory

/**
 * A part of life the user plans in v4. The user picks the ones they want at first run, and only
 * those appear anywhere in the app. [key] is persisted (settings, analytics), so never rename one.
 *
 * Every area is built from the same four pieces: plans with steps (goals and milestones),
 * routines (habits), logs (check-ins, health records, entries, spending) and budgets. That is what
 * keeps eight areas feeling like one app rather than eight small ones.
 */
enum class PlanArea(val key: String) {
    HABITS("habits"),
    FITNESS("fitness"),
    MONEY("money"),
    TRAVEL("travel"),
    STUDY("study"),
    MEALS("meals"),
    MIND("mind"),
    CAREER("career");

    companion object {
        fun fromKey(key: String): PlanArea? = entries.firstOrNull { it.key == key.trim() }

        /** What a brand-new user sees preselected on the picker. */
        val DEFAULTS: Set<PlanArea> = setOf(HABITS, FITNESS, MONEY)

        /**
         * The area a v3 goal or habit category belongs to. Social and family plans have no area of
         * their own in v4, so they fall back to Habits, the everyday area every user has.
         */
        fun forCategory(category: GoalCategory): PlanArea = when (category) {
            GoalCategory.BODY -> FITNESS
            GoalCategory.MONEY -> MONEY
            GoalCategory.CAREER -> CAREER
            GoalCategory.WELLBEING, GoalCategory.PURPOSE -> MIND
            GoalCategory.PEOPLE, GoalCategory.FAMILY -> HABITS
        }
    }
}
