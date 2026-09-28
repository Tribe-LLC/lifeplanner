package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.core.FeatureFlags
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
        UserSituationTableSyncer(supabase, db),
        LifeValueTableSyncer(supabase, db),
        KnowledgeReadTableSyncer(supabase, db),      // knowledge_reads (Learn hub read-state, no FK deps)
        WheelScoreTableSyncer(supabase, db),         // wheel_scores (one row per WheelArea, no FK deps)
        WheelSnapshotTableSyncer(supabase, db),      // wheel_snapshots (one row per day, no FK deps)
        IdentityStatementTableSyncer(supabase, db),  // identity_statements.value_id → life_values (soft ref)
        DecisionProfileTableSyncer(supabase, db),    // decision_profiles (one row per user)
        GoalTableSyncer(supabase, db),
        DecisionTableSyncer(supabase, db),   // decisions.related_goal_id → goals (soft ref)
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
    ) + if (FeatureFlags.V4_CLOUD_SYNC) listOf(
        // v4 life areas. trips before trip_items; life_logs and budgets point at trips by soft ref.
        TripTableSyncer(supabase, db),
        TripItemTableSyncer(supabase, db),
        LifeLogTableSyncer(supabase, db),
        BudgetTableSyncer(supabase, db),
    ) else emptyList()
}
