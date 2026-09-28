package az.tribe.lifeplanner.data.sync.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class GoalSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val category: String,
    val title: String,
    val description: String,
    val status: String,
    val timeline: String,
    @SerialName("due_date") val dueDate: String,
    val progress: Long = 0,
    val notes: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("completion_rate") val completionRate: Double = 0.0,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("ai_reasoning") val aiReasoning: String? = null,
    @SerialName("value_id") val valueId: String? = null,
    @SerialName("wheel_area") val wheelArea: String? = null,
    @SerialName("predicted_due_date") val predictedDueDate: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class LifeValueSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val description: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Long = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class KnowledgeReadSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("read_at") val readAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

/** Wheel of Life. `id` is the WheelArea enum name, so one row per area per user. */
@Serializable
data class WheelScoreSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val score: Double,
    @SerialName("assessed_at") val assessedAt: String,
    val note: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

/** Wheel of Life history. `id` is the ISO date, so one row per day per user. */
@Serializable
data class WheelSnapshotSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val scores: String,
    @SerialName("captured_at") val capturedAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class DecisionSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val question: String,
    @SerialName("options_considered") val optionsConsidered: String = "",
    @SerialName("chosen_option") val chosenOption: String,
    val reasoning: String = "",
    @SerialName("related_goal_id") val relatedGoalId: String? = null,
    @SerialName("expected_outcome") val expectedOutcome: String = "",
    val confidence: Long = 50,
    @SerialName("decided_at") val decidedAt: String,
    @SerialName("actual_outcome") val actualOutcome: String? = null,
    @SerialName("outcome_reviewed_at") val outcomeReviewedAt: String? = null,
    @SerialName("outcome_quality") val outcomeQuality: String? = null,
    val source: String = "CHOICE_POINT",
    val status: String = "CONFIRMED",
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class IdentityStatementSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val statement: String,
    @SerialName("value_id") val valueId: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("sort_order") val sortOrder: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class DecisionProfileSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("confidence_threshold_value") val confidenceThresholdValue: Double = 0.5,
    @SerialName("confidence_threshold_confidence") val confidenceThresholdConfidence: Double = 0.0,
    @SerialName("confidence_threshold_samples") val confidenceThresholdSamples: Long = 0,
    @SerialName("novelty_salience_value") val noveltySalienceValue: Double = 0.5,
    @SerialName("novelty_salience_confidence") val noveltySalienceConfidence: Double = 0.0,
    @SerialName("novelty_salience_samples") val noveltySalienceSamples: Long = 0,
    @SerialName("delay_discounting_value") val delayDiscountingValue: Double = 0.5,
    @SerialName("delay_discounting_confidence") val delayDiscountingConfidence: Double = 0.0,
    @SerialName("delay_discounting_samples") val delayDiscountingSamples: Long = 0,
    @SerialName("punishment_sensitivity_value") val punishmentSensitivityValue: Double = 0.5,
    @SerialName("punishment_sensitivity_confidence") val punishmentSensitivityConfidence: Double = 0.0,
    @SerialName("punishment_sensitivity_samples") val punishmentSensitivitySamples: Long = 0,
    @SerialName("reward_sensitivity_value") val rewardSensitivityValue: Double = 0.5,
    @SerialName("reward_sensitivity_confidence") val rewardSensitivityConfidence: Double = 0.0,
    @SerialName("reward_sensitivity_samples") val rewardSensitivitySamples: Long = 0,
    @SerialName("risk_aversion_value") val riskAversionValue: Double = 0.5,
    @SerialName("risk_aversion_confidence") val riskAversionConfidence: Double = 0.0,
    @SerialName("risk_aversion_samples") val riskAversionSamples: Long = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class MilestoneSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("goal_id") val goalId: String,
    val title: String,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("estimated_effort") val estimatedEffort: Long? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class GoalHistorySyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("goal_id") val goalId: String,
    val field: String,
    @SerialName("old_value") val oldValue: String? = null,
    @SerialName("new_value") val newValue: String? = null,
    @SerialName("changed_at") val changedAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class UserProgressSyncDto(
    @SerialName("user_id") val userId: String,
    @SerialName("current_streak") val currentStreak: Long = 0,
    @SerialName("last_check_in_date") val lastCheckInDate: String? = null,
    @SerialName("total_xp") val totalXp: Long = 0,
    @SerialName("current_level") val currentLevel: Long = 1,
    @SerialName("goals_completed") val goalsCompleted: Long = 0,
    @SerialName("habits_completed") val habitsCompleted: Long = 0,
    @SerialName("journal_entries_count") val journalEntriesCount: Long = 0,
    @SerialName("longest_streak") val longestStreak: Long = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class UserSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("firebase_uid") val firebaseUid: String? = null,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("is_guest") val isGuest: Boolean = false,
    @SerialName("selected_symbol") val selectedSymbol: String? = null,
    val priorities: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("age_range") val ageRange: String? = null,
    val profession: String? = null,
    @SerialName("relationship_status") val relationshipStatus: String? = null,
    val mindset: String? = null,
    @SerialName("has_completed_onboarding") val hasCompletedOnboarding: Boolean = false,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class HabitSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val description: String = "",
    val category: String,
    val frequency: String = "DAILY",
    @SerialName("target_count") val targetCount: Long = 1,
    @SerialName("current_streak") val currentStreak: Long = 0,
    @SerialName("longest_streak") val longestStreak: Long = 0,
    @SerialName("total_completions") val totalCompletions: Long = 0,
    @SerialName("last_completed_date") val lastCompletedDate: String? = null,
    @SerialName("linked_goal_id") val linkedGoalId: String? = null,
    @SerialName("correlation_score") val correlationScore: Double = 0.0,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String,
    @SerialName("reminder_time") val reminderTime: String? = null,
    @SerialName("type") val type: String = "BUILD",
    @SerialName("unit") val unit: String? = null,
    @SerialName("health_metric_type") val healthMetricType: String? = null,
    @SerialName("health_target") val healthTarget: Double? = null,
    @SerialName("completion_source") val completionSource: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class HabitCheckInSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("habit_id") val habitId: String,
    val date: String,
    val completed: Boolean = true,
    val notes: String = "",
    val count: Long = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class JournalEntrySyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val content: String,
    val mood: String = "NEUTRAL",
    @SerialName("linked_goal_id") val linkedGoalId: String? = null,
    @SerialName("linked_habit_id") val linkedHabitId: String? = null,
    @SerialName("prompt_used") val promptUsed: String? = null,
    val tags: kotlinx.serialization.json.JsonElement = kotlinx.serialization.json.JsonArray(emptyList()),
    val date: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("entry_updated_at") val entryUpdatedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class BadgeSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("badge_type") val badgeType: String,
    @SerialName("earned_at") val earnedAt: String,
    @SerialName("is_new") val isNew: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class ChallengeSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("challenge_type") val challengeType: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("current_progress") val currentProgress: Long = 0,
    @SerialName("target_progress") val targetProgress: Long,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("xp_earned") val xpEarned: Long = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class GoalDependencySyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("source_goal_id") val sourceGoalId: String,
    @SerialName("target_goal_id") val targetGoalId: String,
    @SerialName("dependency_type") val dependencyType: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class ChatSessionSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_message_at") val lastMessageAt: String,
    val summary: String? = null,
    @SerialName("coach_id") val coachId: String = "luna_general",
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class ChatMessageSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("session_id") val sessionId: String,
    val content: String,
    val role: String,
    val timestamp: String,
    @SerialName("related_goal_id") val relatedGoalId: String? = null,
    val metadata: JsonElement? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class ReviewReportSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val type: String,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
    @SerialName("generated_at") val generatedAt: String,
    val summary: String,
    @SerialName("highlights_json") val highlightsJson: JsonElement,
    @SerialName("insights_json") val insightsJson: JsonElement,
    @SerialName("recommendations_json") val recommendationsJson: JsonElement,
    @SerialName("stats_json") val statsJson: JsonElement,
    @SerialName("feedback_rating") val feedbackRating: String? = null,
    @SerialName("feedback_comment") val feedbackComment: String? = null,
    @SerialName("feedback_at") val feedbackAt: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class ReminderSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val message: String,
    val type: String,
    val frequency: String,
    @SerialName("scheduled_time") val scheduledTime: String,
    @SerialName("scheduled_days") val scheduledDays: String = "",
    @SerialName("linked_goal_id") val linkedGoalId: String? = null,
    @SerialName("linked_habit_id") val linkedHabitId: String? = null,
    @SerialName("is_enabled") val isEnabled: Boolean = true,
    @SerialName("is_smart_timing") val isSmartTiming: Boolean = false,
    @SerialName("last_triggered_at") val lastTriggeredAt: String? = null,
    @SerialName("snoozed_until") val snoozedUntil: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class CustomCoachSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val icon: String,
    @SerialName("icon_background_color") val iconBackgroundColor: String = "#6366F1",
    @SerialName("icon_accent_color") val iconAccentColor: String = "#818CF8",
    @SerialName("system_prompt") val systemPrompt: String,
    val characteristics: JsonElement = kotlinx.serialization.json.JsonArray(emptyList()),
    @SerialName("is_from_template") val isFromTemplate: Boolean = false,
    @SerialName("template_id") val templateId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class CoachGroupSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val icon: String,
    val description: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class CoachGroupMemberSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("coach_type") val coachType: String,
    @SerialName("coach_id") val coachId: String,
    @SerialName("display_order") val displayOrder: Long = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class FocusSessionSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("goal_id") val goalId: String? = null,
    @SerialName("milestone_id") val milestoneId: String? = null,
    @SerialName("planned_duration_minutes") val plannedDurationMinutes: Long,
    @SerialName("actual_duration_seconds") val actualDurationSeconds: Long = 0,
    @SerialName("was_completed") val wasCompleted: Boolean = false,
    @SerialName("xp_earned") val xpEarned: Long = 0,
    @SerialName("started_at") val startedAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    val mood: String? = null,
    @SerialName("ambient_sound") val ambientSound: String? = null,
    @SerialName("focus_theme") val focusTheme: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class CoachPersonaOverrideSyncDto(
    @SerialName("coach_id") val coachId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_persona") val userPersona: String = "",
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class UserSituationSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("meta_json") val metaJson: String = "{}",
    @SerialName("career_json") val careerJson: String = "{}",
    @SerialName("money_json") val moneyJson: String = "{}",
    @SerialName("body_json") val bodyJson: String = "{}",
    @SerialName("people_json") val peopleJson: String = "{}",
    @SerialName("purpose_json") val purposeJson: String = "{}",
    @SerialName("last_updated_by") val lastUpdatedBy: String = "",
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

@Serializable
data class PersonaCacheSyncDto(
    @SerialName("user_id") val userId: String,
    @SerialName("persona_id") val personaId: String,
    val name: String,
    val description: String? = null,
    val style: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("fetched_at") val fetchedAt: String,
)

@Serializable
data class BeginnerObjectiveSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("objective_type") val objectiveType: String,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("xp_awarded") val xpAwarded: Long = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)


// v4 life areas. Local camelCase columns map to snake_case in Supabase.

/** Everything logged in v4: spends, workouts, meals, study, sleep, water, mood. */
@Serializable
data class LifeLogSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val area: String,
    val kind: String,
    val status: String,
    val title: String,
    val amount: Double? = null,
    val currency: String? = null,
    val category: String? = null,
    val quantity: Double? = null,
    val unit: String? = null,
    @SerialName("duration_min") val durationMin: Long? = null,
    @SerialName("occurred_at") val occurredAt: String,
    val date: String,
    val source: String,
    @SerialName("external_id") val externalId: String? = null,
    @SerialName("trip_id") val tripId: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

/** One number to stay under, per week, month or trip. */
@Serializable
data class BudgetSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val area: String,
    val metric: String,
    val category: String? = null,
    val amount: Double,
    val currency: String? = null,
    val period: String,
    @SerialName("trip_id") val tripId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

/** A trip: where, when, budget and travel mode. */
@Serializable
data class TripSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val destination: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val budget: Double? = null,
    val currency: String? = null,
    @SerialName("travel_mode") val travelMode: Long,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)

/** A trip's checklist items and day plans. */
@Serializable
data class TripItemSyncDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("trip_id") val tripId: String,
    val kind: String,
    val title: String,
    val notes: String? = null,
    val date: String? = null,
    @SerialName("is_done") val isDone: Boolean,
    @SerialName("sort_order") val sortOrder: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("sync_version") val syncVersion: Long = 0
)
