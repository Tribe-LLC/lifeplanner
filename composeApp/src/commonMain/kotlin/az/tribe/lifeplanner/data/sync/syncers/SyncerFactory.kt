package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import io.github.jan.supabase.SupabaseClient

/**
 * Creates all table syncers in FK-dependency order (tiers).
 */
fun createAllSyncers(
    supabase: SupabaseClient,
    db: SharedDatabase
): List<TableSyncer<*, *>> {
    return listOf(
        // Tier 1: No FK dependencies
        UserTableSyncer(supabase, db),
        GoalTableSyncer(supabase, db),
        BadgeTableSyncer(supabase, db),
        CustomCoachTableSyncer(supabase, db),
        CoachGroupTableSyncer(supabase, db),
        CoachPersonaOverrideTableSyncer(supabase, db),
        ReviewTableSyncer(supabase, db),
        BeginnerObjectiveTableSyncer(supabase, db),
        UserProgressTableSyncer(supabase, db),
        ChallengeTableSyncer(supabase, db),

        // Tier 2: Depends on goals (Tier 1)
        HabitTableSyncer(supabase, db),         // habits.linked_goal_id → goals
        MilestoneTableSyncer(supabase, db),     // milestones.goal_id → goals
        GoalHistoryTableSyncer(supabase, db),   // goal_history.goal_id → goals
        GoalDependencyTableSyncer(supabase, db),// goal_dependencies → goals
        ChatSessionTableSyncer(supabase, db),

        // Tier 3: Depends on habits/milestones (Tier 2)
        HabitCheckInTableSyncer(supabase, db),  // habit_check_ins.habit_id → habits
        JournalEntryTableSyncer(supabase, db),  // journal_entries → goals, habits
        ReminderTableSyncer(supabase, db),      // reminders → goals, habits
        FocusSessionTableSyncer(supabase, db),  // focus_sessions → goals, milestones
        CoachGroupMemberTableSyncer(supabase, db),

        // Tier 4: Depends on Tier 3
        ChatMessageTableSyncer(supabase, db)    // chat_messages.session_id → chat_sessions
    )
}
