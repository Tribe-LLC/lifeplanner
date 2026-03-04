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
        HabitTableSyncer(supabase, db),
        BadgeTableSyncer(supabase, db),
        CustomCoachTableSyncer(supabase, db),
        CoachGroupTableSyncer(supabase, db),
        ReviewTableSyncer(supabase, db),

        // Tier 2: Depends on Tier 1
        MilestoneTableSyncer(supabase, db),
        GoalHistoryTableSyncer(supabase, db),
        GoalDependencyTableSyncer(supabase, db),
        HabitCheckInTableSyncer(supabase, db),
        JournalEntryTableSyncer(supabase, db),
        ChatSessionTableSyncer(supabase, db),
        ReminderTableSyncer(supabase, db),
        FocusSessionTableSyncer(supabase, db),
        ChallengeTableSyncer(supabase, db),
        CoachGroupMemberTableSyncer(supabase, db),
        UserProgressTableSyncer(supabase, db),

        // Tier 3: Depends on Tier 2
        ChatMessageTableSyncer(supabase, db)
    )
}
