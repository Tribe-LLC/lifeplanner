package az.tribe.lifeplanner.testutil

import az.tribe.lifeplanner.domain.enum.*
import az.tribe.lifeplanner.domain.model.*
import kotlinx.datetime.*

/**
 * Factory functions for creating test domain objects with sensible defaults.
 * Every parameter is overridable.
 */

private val NOW: LocalDateTime = LocalDateTime(2026, 3, 6, 10, 0, 0)
private val TODAY: LocalDate = NOW.date

fun testGoal(
    id: String = "goal-1",
    category: GoalCategory = GoalCategory.CAREER,
    title: String = "Test Goal",
    description: String = "Test Description",
    status: GoalStatus = GoalStatus.IN_PROGRESS,
    timeline: GoalTimeline = GoalTimeline.SHORT_TERM,
    dueDate: LocalDate = TODAY.plus(DatePeriod(months = 3)),
    progress: Long? = 0,
    milestones: List<Milestone> = emptyList(),
    notes: String = "",
    createdAt: LocalDateTime = NOW,
    completionRate: Float = 0f,
    isArchived: Boolean = false
) = Goal(id, category, title, description, status, timeline, dueDate, progress, milestones, notes, createdAt, completionRate, isArchived)

fun testMilestone(
    id: String = "milestone-1",
    title: String = "Test Milestone",
    isCompleted: Boolean = false,
    dueDate: LocalDate? = null
) = Milestone(id, title, isCompleted, dueDate)

fun testHabit(
    id: String = "habit-1",
    title: String = "Test Habit",
    description: String = "",
    category: GoalCategory = GoalCategory.PHYSICAL,
    frequency: HabitFrequency = HabitFrequency.DAILY,
    targetCount: Int = 1,
    currentStreak: Int = 0,
    longestStreak: Int = 0,
    totalCompletions: Int = 0,
    lastCompletedDate: LocalDate? = null,
    linkedGoalId: String? = null,
    correlationScore: Float = 0f,
    isActive: Boolean = true,
    createdAt: LocalDateTime = NOW,
    reminderTime: String? = null
) = Habit(id, title, description, category, frequency, targetCount, currentStreak, longestStreak, totalCompletions, lastCompletedDate, linkedGoalId, correlationScore, isActive, createdAt, reminderTime)

fun testHabitCheckIn(
    id: String = "checkin-1",
    habitId: String = "habit-1",
    date: LocalDate = TODAY,
    completed: Boolean = true,
    notes: String = ""
) = HabitCheckIn(id, habitId, date, completed, notes)

fun testJournalEntry(
    id: String = "journal-1",
    title: String = "Test Entry",
    content: String = "Test content",
    mood: Mood = Mood.HAPPY,
    linkedGoalId: String? = null,
    linkedHabitId: String? = null,
    promptUsed: String? = null,
    tags: List<String> = emptyList(),
    date: LocalDate = TODAY,
    createdAt: LocalDateTime = NOW,
    updatedAt: LocalDateTime? = null
) = JournalEntry(id, title, content, mood, linkedGoalId, linkedHabitId, promptUsed, tags, date, createdAt, updatedAt)

fun testChatMessage(
    id: String = "msg-1",
    content: String = "Hello",
    role: MessageRole = MessageRole.USER,
    timestamp: LocalDateTime = NOW,
    relatedGoalId: String? = null,
    metadata: ChatMessageMetadata? = null
) = ChatMessage(id, content, role, timestamp, relatedGoalId, metadata)

fun testChatSession(
    id: String = "session-1",
    title: String = "Test Chat",
    messages: List<ChatMessage> = emptyList(),
    createdAt: LocalDateTime = NOW,
    lastMessageAt: LocalDateTime = NOW,
    summary: String? = null,
    coachId: String = "luna_general"
) = ChatSession(id, title, messages, createdAt, lastMessageAt, summary, coachId)

fun testUserContext(
    userName: String? = "Test User",
    totalGoals: Int = 5,
    completedGoals: Int = 2,
    activeGoals: Int = 3,
    currentStreak: Int = 7,
    totalXp: Int = 500,
    level: Int = 3,
    recentMilestones: List<String> = emptyList(),
    upcomingDeadlines: List<Goal> = emptyList(),
    habitCompletionRate: Float = 0.8f,
    journalEntryCount: Int = 10,
    primaryCategories: List<String> = listOf("Career")
) = UserContext(userName, totalGoals, completedGoals, activeGoals, currentStreak, totalXp, level, recentMilestones, upcomingDeadlines, habitCompletionRate, journalEntryCount, primaryCategories)

fun testFocusSession(
    id: String = "focus-1",
    goalId: String = "goal-1",
    milestoneId: String = "milestone-1",
    plannedDurationMinutes: Int = 25,
    actualDurationSeconds: Int = 1500,
    wasCompleted: Boolean = true,
    xpEarned: Int = 20,
    startedAt: LocalDateTime = NOW,
    completedAt: LocalDateTime? = NOW,
    createdAt: LocalDateTime = NOW,
    mood: Mood? = null,
    ambientSound: AmbientSound? = null,
    focusTheme: FocusTheme? = null
) = FocusSession(id, goalId, milestoneId, plannedDurationMinutes, actualDurationSeconds, wasCompleted, xpEarned, startedAt, completedAt, createdAt, mood, ambientSound, focusTheme)

fun testBadge(
    id: String = "badge-1",
    type: BadgeType = BadgeType.FIRST_STEP,
    earnedAt: LocalDateTime = NOW,
    isNew: Boolean = true
) = Badge(id, type, earnedAt, isNew)

fun testChallenge(
    id: String = "challenge-1",
    type: ChallengeType = ChallengeType.DAILY_CHECK_IN,
    startDate: LocalDate = TODAY,
    endDate: LocalDate = TODAY.plus(DatePeriod(days = 7)),
    currentProgress: Int = 0,
    targetProgress: Int = ChallengeTargets.getTargetForType(ChallengeType.DAILY_CHECK_IN),
    isCompleted: Boolean = false,
    completedAt: LocalDateTime? = null,
    xpEarned: Int = 0
) = Challenge(id, type, startDate, endDate, currentProgress, targetProgress, isCompleted, completedAt, xpEarned)

fun testUserProgress(
    currentStreak: Int = 5,
    lastCheckInDate: LocalDate? = TODAY,
    totalXp: Int = 500,
    currentLevel: Int = 3,
    goalsCompleted: Int = 2,
    habitsCompleted: Int = 10,
    journalEntriesCount: Int = 5,
    longestStreak: Int = 10
) = UserProgress(currentStreak, lastCheckInDate, totalXp, currentLevel, goalsCompleted, habitsCompleted, journalEntriesCount, longestStreak)

fun testReminder(
    id: String = "reminder-1",
    title: String = "Test Reminder",
    message: String = "Don't forget!",
    type: ReminderType = ReminderType.CUSTOM,
    frequency: ReminderFrequency = ReminderFrequency.DAILY,
    scheduledTime: LocalTime = LocalTime(9, 0),
    scheduledDays: List<az.tribe.lifeplanner.domain.model.DayOfWeek> = emptyList(),
    linkedGoalId: String? = null,
    linkedHabitId: String? = null,
    isEnabled: Boolean = true,
    isSmartTiming: Boolean = false,
    lastTriggeredAt: LocalDateTime? = null,
    snoozedUntil: LocalDateTime? = null,
    createdAt: LocalDateTime = NOW,
    updatedAt: LocalDateTime? = null
) = Reminder(id, title, message, type, frequency, scheduledTime, scheduledDays, linkedGoalId, linkedHabitId, isEnabled, isSmartTiming, lastTriggeredAt, snoozedUntil, createdAt, updatedAt)

fun testReminderSettings(
    id: String = "default",
    isEnabled: Boolean = true,
    quietHoursStart: LocalTime = LocalTime(22, 0),
    quietHoursEnd: LocalTime = LocalTime(7, 0),
    preferredMorningTime: LocalTime = LocalTime(8, 0),
    preferredEveningTime: LocalTime = LocalTime(20, 0),
    smartTimingEnabled: Boolean = true,
    maxRemindersPerDay: Int = 5,
    weeklyReviewDay: az.tribe.lifeplanner.domain.model.DayOfWeek = az.tribe.lifeplanner.domain.model.DayOfWeek.SUNDAY,
    weeklyReviewTime: LocalTime = LocalTime(10, 0)
) = ReminderSettings(id, isEnabled, quietHoursStart, quietHoursEnd, preferredMorningTime, preferredEveningTime, smartTimingEnabled, maxRemindersPerDay, weeklyReviewDay, weeklyReviewTime)

fun testCustomCoach(
    id: String = "coach-1",
    name: String = "Test Coach",
    icon: String = "🤖",
    iconBackgroundColor: String = "#6366F1",
    iconAccentColor: String = "#818CF8",
    systemPrompt: String = "You are a helpful coach.",
    characteristics: List<String> = listOf("Friendly"),
    isFromTemplate: Boolean = false,
    templateId: String? = null,
    createdAt: LocalDateTime = NOW,
    updatedAt: LocalDateTime? = null
) = CustomCoach(id, name, icon, iconBackgroundColor, iconAccentColor, systemPrompt, characteristics, isFromTemplate, templateId, createdAt, updatedAt)

fun testCoachGroup(
    id: String = "group-1",
    name: String = "Test Group",
    icon: String = "👥",
    description: String = "A test group",
    members: List<CoachGroupMember> = emptyList(),
    createdAt: LocalDateTime = NOW,
    updatedAt: LocalDateTime? = null
) = CoachGroup(id, name, icon, description, members, createdAt, updatedAt)

fun testCoachGroupMember(
    id: String = "member-1",
    coachType: CoachType = CoachType.BUILTIN,
    coachId: String = "luna_general",
    displayOrder: Int = 0
) = CoachGroupMember(id, coachType, coachId, displayOrder)

fun testGoalDependency(
    id: String = "dep-1",
    sourceGoalId: String = "goal-1",
    targetGoalId: String = "goal-2",
    dependencyType: DependencyType = DependencyType.BLOCKS,
    createdAt: LocalDateTime = NOW
) = GoalDependency(id, sourceGoalId, targetGoalId, dependencyType, createdAt)

fun testReviewReport(
    id: String = "review-1",
    type: ReviewType = ReviewType.WEEKLY,
    periodStart: LocalDate = TODAY.minus(DatePeriod(days = 7)),
    periodEnd: LocalDate = TODAY,
    generatedAt: LocalDateTime = NOW,
    summary: String = "Good week overall",
    highlights: List<ReviewHighlight> = emptyList(),
    insights: List<ReviewInsight> = emptyList(),
    recommendations: List<ReviewRecommendation> = emptyList(),
    stats: ReviewStats = testReviewStats(),
    feedback: ReviewFeedback? = null,
    isRead: Boolean = false
) = ReviewReport(id, type, periodStart, periodEnd, generatedAt, summary, highlights, insights, recommendations, stats, feedback, isRead)

fun testReviewStats(
    goalsCompleted: Int = 2,
    goalsInProgress: Int = 3,
    milestonesCompleted: Int = 5,
    habitsTracked: Int = 4,
    habitCompletionRate: Float = 0.75f,
    journalEntries: Int = 3,
    xpEarned: Int = 200,
    streakDays: Int = 7,
    mostActiveCategory: String? = "Career",
    totalActiveMinutes: Int = 120,
    comparisonToPrevious: StatsComparison? = null
) = ReviewStats(goalsCompleted, goalsInProgress, milestonesCompleted, habitsTracked, habitCompletionRate, journalEntries, xpEarned, streakDays, mostActiveCategory, totalActiveMinutes, comparisonToPrevious)

fun testReviewHighlight(
    id: String = "highlight-1",
    title: String = "Great Progress",
    description: String = "You completed 2 goals",
    category: HighlightCategory = HighlightCategory.GOAL_COMPLETED,
    relatedGoalId: String? = null,
    iconType: String = "star"
) = ReviewHighlight(id, title, description, category, relatedGoalId, iconType)

fun testReviewInsight(
    id: String = "insight-1",
    title: String = "Productivity Pattern",
    description: String = "You're most productive in the morning",
    type: InsightType = InsightType.PRODUCTIVITY_PATTERN,
    dataPoint: String? = null,
    trend: TrendDirection? = TrendDirection.UP
) = ReviewInsight(id, title, description, type, dataPoint, trend)

fun testReviewRecommendation(
    id: String = "rec-1",
    title: String = "Add More Goals",
    description: String = "Consider expanding your goals",
    actionType: RecommendationAction = RecommendationAction.CREATE_GOAL,
    priority: RecommendationPriority = RecommendationPriority.MEDIUM,
    relatedGoalId: String? = null
) = ReviewRecommendation(id, title, description, actionType, priority, relatedGoalId)

fun testLifeBalanceReport(
    id: String = "balance-1",
    overallScore: Int = 65,
    areaScores: List<LifeAreaScore> = emptyList(),
    strongestAreas: List<LifeArea> = listOf(LifeArea.CAREER),
    weakestAreas: List<LifeArea> = listOf(LifeArea.SPIRITUAL),
    balanceRating: BalanceRating = BalanceRating.GOOD,
    aiInsights: List<BalanceInsight> = emptyList(),
    recommendations: List<BalanceRecommendation> = emptyList(),
    generatedAt: LocalDateTime = NOW
) = LifeBalanceReport(id, overallScore, areaScores, strongestAreas, weakestAreas, balanceRating, aiInsights, recommendations, generatedAt)

fun testLifeAreaScore(
    area: LifeArea = LifeArea.CAREER,
    score: Int = 70,
    goalCount: Int = 3,
    completedGoals: Int = 1,
    activeGoals: Int = 2,
    habitCount: Int = 2,
    habitCompletionRate: Float = 0.8f,
    recentActivityScore: Int = 60,
    trend: BalanceTrend = BalanceTrend.IMPROVING
) = LifeAreaScore(area, score, goalCount, completedGoals, activeGoals, habitCount, habitCompletionRate, recentActivityScore, trend)

fun testScheduledNotification(
    id: String = "notif-1",
    reminderId: String = "reminder-1",
    title: String = "Reminder",
    message: String = "Check in!",
    scheduledAt: LocalDateTime = NOW,
    isDelivered: Boolean = false,
    deliveredAt: LocalDateTime? = null,
    isSnoozed: Boolean = false,
    isDismissed: Boolean = false
) = ScheduledNotification(id, reminderId, title, message, scheduledAt, isDelivered, deliveredAt, isSnoozed, isDismissed)

fun testGoalAnalytics(
    totalGoals: Int = 10,
    activeGoals: Int = 5,
    completedGoals: Int = 3,
    completionRate: Float = 0.3f,
    upcomingDeadlines: Int = 2,
    goalsByCategory: Map<GoalCategory, Int> = emptyMap(),
    goalsByTimeline: Map<GoalTimeline, Int> = emptyMap(),
    averageProgressPerCategory: Map<GoalCategory, Float> = emptyMap(),
    goalsCompletedThisWeek: Int = 1,
    goalsCompletedThisMonth: Int = 2
) = GoalAnalytics(totalGoals, activeGoals, completedGoals, completionRate, upcomingDeadlines, goalsByCategory, goalsByTimeline, averageProgressPerCategory, goalsCompletedThisWeek, goalsCompletedThisMonth)

fun testDaySnapshot(
    date: LocalDate = TODAY,
    habitSummary: HabitDaySummary = HabitDaySummary(0, 0, emptyList()),
    journalEntries: List<JournalEntry> = emptyList(),
    focusSessions: List<FocusSession> = emptyList(),
    goalChanges: List<GoalChangeWithTitle> = emptyList(),
    badgesEarned: List<Badge> = emptyList()
) = DaySnapshot(date, habitSummary, journalEntries, focusSessions, goalChanges, badgesEarned)

fun testGoalChange(
    id: String = "change-1",
    goalId: String = "goal-1",
    field: String = "status",
    oldValue: String? = "IN_PROGRESS",
    newValue: String = "COMPLETED",
    changedAt: String = "2026-03-06T10:00:00"
) = GoalChange(id, goalId, field, oldValue, newValue, changedAt)
