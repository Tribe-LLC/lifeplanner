package az.tribe.lifeplanner.data.analytics

/**
 * PostHog product analytics — comprehensive event tracking for funnels,
 * retention analysis, feature adoption, and experimentation.
 *
 * Event naming convention: snake_case, noun_verb format.
 * Properties: always include relevant context for cohort analysis.
 */
expect object PostHogAnalytics {

    // ─── Lifecycle ───────────────────────────────────────────────────
    fun setup(apiKey: String, host: String = "https://us.i.posthog.com")
    fun identify(userId: String, properties: Map<String, Any> = emptyMap())
    fun reset()

    // ─── Core event ──────────────────────────────────────────────────
    fun capture(event: String, properties: Map<String, Any> = emptyMap())

    // ─── User properties (super props set on every event) ────────────
    fun setUserProperties(properties: Map<String, Any>)

    // ─── Screen tracking ─────────────────────────────────────────────
    fun screen(screenName: String, properties: Map<String, Any> = emptyMap())

    // ─── Feature flags ───────────────────────────────────────────────
    fun isFeatureEnabled(flag: String): Boolean
    fun getFeatureFlag(flag: String): Any?
    fun reloadFeatureFlags()

    // ─── Groups (for org-level analytics) ────────────────────────────
    fun group(type: String, key: String, properties: Map<String, Any> = emptyMap())

    fun flush()
}

// ─── Typed event helpers ─────────────────────────────────────────────
// Use these instead of raw capture() for type safety and consistency.

object Analytics {

    // ── Onboarding funnel ────────────────────────────────────────────
    fun appOpened() = PostHogAnalytics.capture("app_opened")

    fun onboardingStarted() = PostHogAnalytics.capture("onboarding_started")

    fun onboardingStepCompleted(step: String, stepIndex: Int) =
        PostHogAnalytics.capture("onboarding_step_completed", mapOf(
            "step" to step,
            "step_index" to stepIndex
        ))

    fun onboardingCompleted() = PostHogAnalytics.capture("onboarding_completed")

    fun onboardingSkipped(atStep: String) =
        PostHogAnalytics.capture("onboarding_skipped", mapOf("at_step" to atStep))

    // ── Auth funnel ──────────────────────────────────────────────────
    fun signUpStarted(method: String) =
        PostHogAnalytics.capture("signup_started", mapOf("method" to method))

    fun signUpCompleted(method: String) =
        PostHogAnalytics.capture("signup_completed", mapOf("method" to method))

    fun signInCompleted(method: String) =
        PostHogAnalytics.capture("signin_completed", mapOf("method" to method))

    fun signOutCompleted() = PostHogAnalytics.capture("signout_completed")

    fun accountSecured() = PostHogAnalytics.capture("account_secured")

    // ── Goal funnel ──────────────────────────────────────────────────
    fun goalCreated(category: String, source: String, hasAiGenerated: Boolean = false) =
        PostHogAnalytics.capture("goal_created", mapOf(
            "category" to category,
            "source" to source,
            "ai_generated" to hasAiGenerated
        ))

    fun goalViewed(goalId: String, category: String) =
        PostHogAnalytics.capture("goal_viewed", mapOf(
            "goal_id" to goalId,
            "category" to category
        ))

    fun goalProgressUpdated(goalId: String, progress: Int) =
        PostHogAnalytics.capture("goal_progress_updated", mapOf(
            "goal_id" to goalId,
            "progress" to progress
        ))

    fun goalCompleted(goalId: String, category: String, daysToComplete: Int) =
        PostHogAnalytics.capture("goal_completed", mapOf(
            "goal_id" to goalId,
            "category" to category,
            "days_to_complete" to daysToComplete
        ))

    fun goalAbandoned(goalId: String, category: String, progress: Int) =
        PostHogAnalytics.capture("goal_abandoned", mapOf(
            "goal_id" to goalId,
            "category" to category,
            "progress_at_abandon" to progress
        ))

    fun milestoneCompleted(goalId: String, milestoneId: String) =
        PostHogAnalytics.capture("milestone_completed", mapOf(
            "goal_id" to goalId,
            "milestone_id" to milestoneId
        ))

    // ── Habit funnel ─────────────────────────────────────────────────
    fun habitCreated(frequency: String, linkedToGoal: Boolean) =
        PostHogAnalytics.capture("habit_created", mapOf(
            "frequency" to frequency,
            "linked_to_goal" to linkedToGoal
        ))

    fun habitCheckedIn(habitId: String, streak: Int) =
        PostHogAnalytics.capture("habit_checked_in", mapOf(
            "habit_id" to habitId,
            "streak" to streak
        ))

    fun habitStreakBroken(habitId: String, previousStreak: Int) =
        PostHogAnalytics.capture("habit_streak_broken", mapOf(
            "habit_id" to habitId,
            "previous_streak" to previousStreak
        ))

    // ── Journal funnel ───────────────────────────────────────────────
    fun journalEntryCreated(mood: String, hasAiContent: Boolean, source: String) =
        PostHogAnalytics.capture("journal_entry_created", mapOf(
            "mood" to mood,
            "ai_assisted" to hasAiContent,
            "source" to source
        ))

    fun journalWizardStarted() = PostHogAnalytics.capture("journal_wizard_started")

    fun journalWizardCompleted(mood: String) =
        PostHogAnalytics.capture("journal_wizard_completed", mapOf("mood" to mood))

    fun journalWizardAbandoned(atStep: String) =
        PostHogAnalytics.capture("journal_wizard_abandoned", mapOf("at_step" to atStep))

    // ── Focus timer funnel ───────────────────────────────────────────
    fun focusSessionStarted(mode: String, theme: String, hasMilestone: Boolean, durationMinutes: Int) =
        PostHogAnalytics.capture("focus_session_started", mapOf(
            "mode" to mode,
            "theme" to theme,
            "has_milestone" to hasMilestone,
            "planned_duration_minutes" to durationMinutes
        ))

    fun focusSessionCompleted(mode: String, actualMinutes: Int, xpEarned: Int) =
        PostHogAnalytics.capture("focus_session_completed", mapOf(
            "mode" to mode,
            "actual_minutes" to actualMinutes,
            "xp_earned" to xpEarned
        ))

    fun focusSessionCancelled(elapsedMinutes: Int) =
        PostHogAnalytics.capture("focus_session_cancelled", mapOf(
            "elapsed_minutes" to elapsedMinutes
        ))

    // ── AI / Coach funnel ────────────────────────────────────────────
    fun chatMessageSent(coachId: String, isFirstMessage: Boolean) =
        PostHogAnalytics.capture("chat_message_sent", mapOf(
            "coach_id" to coachId,
            "is_first_message" to isFirstMessage
        ))

    fun coachProfileViewed(coachId: String) =
        PostHogAnalytics.capture("coach_profile_viewed", mapOf("coach_id" to coachId))

    fun coachPostRead(coachId: String, postId: String, category: String) =
        PostHogAnalytics.capture("coach_post_read", mapOf(
            "coach_id" to coachId,
            "post_id" to postId,
            "category" to category
        ))

    fun aiGoalGenerationStarted() = PostHogAnalytics.capture("ai_goal_generation_started")

    fun aiGoalGenerationCompleted(goalsGenerated: Int) =
        PostHogAnalytics.capture("ai_goal_generation_completed", mapOf(
            "goals_generated" to goalsGenerated
        ))

    fun aiProviderChanged(provider: String) =
        PostHogAnalytics.capture("ai_provider_changed", mapOf("provider" to provider))

    // ── Gamification ─────────────────────────────────────────────────
    fun levelUp(newLevel: Int, totalXp: Long) =
        PostHogAnalytics.capture("level_up", mapOf(
            "new_level" to newLevel,
            "total_xp" to totalXp
        ))

    fun badgeEarned(badgeType: String) =
        PostHogAnalytics.capture("badge_earned", mapOf("badge_type" to badgeType))

    fun objectiveCompleted(objectiveType: String) =
        PostHogAnalytics.capture("objective_completed", mapOf("type" to objectiveType))

    fun allObjectivesCompleted() = PostHogAnalytics.capture("all_objectives_completed")

    // ── Engagement features ──────────────────────────────────────────
    fun screenViewed(screen: String) =
        PostHogAnalytics.screen(screen)

    fun lifeBalanceChecked(overallScore: Float) =
        PostHogAnalytics.capture("life_balance_checked", mapOf("overall_score" to overallScore))

    fun retrospectiveViewed() = PostHogAnalytics.capture("retrospective_viewed")

    fun templateUsed(templateId: String) =
        PostHogAnalytics.capture("template_used", mapOf("template_id" to templateId))

    fun reminderSet(type: String) =
        PostHogAnalytics.capture("reminder_set", mapOf("type" to type))

    fun backupCreated() = PostHogAnalytics.capture("backup_created")

    fun backupRestored() = PostHogAnalytics.capture("backup_restored")

    // ── Subscription / monetization ──────────────────────────────────
    fun paywallViewed(source: String) =
        PostHogAnalytics.capture("paywall_viewed", mapOf("source" to source))

    fun subscriptionStarted(plan: String) =
        PostHogAnalytics.capture("subscription_started", mapOf("plan" to plan))

    // ── Force update ──────────────────────────────────────────────────
    fun forceUpdateShown(mode: String, currentVersion: String, minVersion: String) =
        PostHogAnalytics.capture("force_update_shown", mapOf(
            "mode" to mode,
            "current_version" to currentVersion,
            "min_version" to minVersion
        ))

    fun forceUpdateClicked(mode: String) =
        PostHogAnalytics.capture("force_update_clicked", mapOf("mode" to mode))

    fun softUpdateDismissed(currentVersion: String) =
        PostHogAnalytics.capture("soft_update_dismissed", mapOf(
            "current_version" to currentVersion
        ))

    // ── Error tracking ───────────────────────────────────────────────
    fun errorOccurred(screen: String, error: String, isFatal: Boolean = false) =
        PostHogAnalytics.capture("error_occurred", mapOf(
            "screen" to screen,
            "error" to error,
            "is_fatal" to isFatal
        ))
}
