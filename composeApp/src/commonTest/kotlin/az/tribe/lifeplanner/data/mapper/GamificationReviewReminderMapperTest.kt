package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.data.model.GoalAnalyticsData
import az.tribe.lifeplanner.database.BadgeEntity
import az.tribe.lifeplanner.database.ChallengeEntity
import az.tribe.lifeplanner.database.GoalDependencyEntity
import az.tribe.lifeplanner.database.ReminderEntity
import az.tribe.lifeplanner.database.ReminderSettingsEntity
import az.tribe.lifeplanner.database.ReviewReportEntity
import az.tribe.lifeplanner.database.ScheduledNotificationEntity
import az.tribe.lifeplanner.database.UserActivityPatternEntity
import az.tribe.lifeplanner.database.UserProgressEntity
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.enum.ChallengeType
import az.tribe.lifeplanner.domain.enum.DependencyType
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.ChallengeTargets
import az.tribe.lifeplanner.domain.model.DayOfWeek
import az.tribe.lifeplanner.domain.model.FeedbackRating
import az.tribe.lifeplanner.domain.model.HighlightCategory
import az.tribe.lifeplanner.domain.model.InsightType
import az.tribe.lifeplanner.domain.model.RecommendationAction
import az.tribe.lifeplanner.domain.model.RecommendationPriority
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import az.tribe.lifeplanner.domain.model.ReminderType
import az.tribe.lifeplanner.domain.model.ReviewHighlight
import az.tribe.lifeplanner.domain.model.ReviewInsight
import az.tribe.lifeplanner.domain.model.ReviewRecommendation
import az.tribe.lifeplanner.domain.model.ReviewStats
import az.tribe.lifeplanner.domain.model.ReviewType
import az.tribe.lifeplanner.domain.model.StatsComparison
import az.tribe.lifeplanner.domain.model.TrendDirection
import az.tribe.lifeplanner.testutil.testBadge
import az.tribe.lifeplanner.testutil.testChallenge
import az.tribe.lifeplanner.testutil.testGoalDependency
import az.tribe.lifeplanner.testutil.testReviewHighlight
import az.tribe.lifeplanner.testutil.testReviewInsight
import az.tribe.lifeplanner.testutil.testReviewRecommendation
import az.tribe.lifeplanner.testutil.testReviewReport
import az.tribe.lifeplanner.testutil.testReviewStats
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.*

class GamificationReviewReminderMapperTest {

    // ════════════════════════════════════════════════════════════════
    // GamificationMapper — Badge
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `BadgeEntity toDomain maps all fields`() {
        val entity = BadgeEntity(
            id = "badge-1",
            badgeType = "FIRST_STEP",
            earnedAt = "2026-03-06T10:00:00",
            isNew = 1L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val badge = entity.toDomain()
        assertNotNull(badge)
        assertEquals("badge-1", badge.id)
        assertEquals(BadgeType.FIRST_STEP, badge.type)
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), badge.earnedAt)
        assertTrue(badge.isNew)
    }

    @Test
    fun `BadgeEntity toDomain maps isNew 0 to false`() {
        val entity = BadgeEntity(
            id = "badge-2", badgeType = "STREAK_3",
            earnedAt = "2026-03-06T10:00:00", isNew = 0L,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        val badge = entity.toDomain()
        assertNotNull(badge)
        assertFalse(badge.isNew)
    }

    @Test
    fun `BadgeEntity toDomain returns null for unknown badge type`() {
        val entity = BadgeEntity(
            id = "badge-unknown", badgeType = "UNKNOWN_TYPE",
            earnedAt = "2026-03-06T10:00:00", isNew = 1L,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertNull(entity.toDomain())
    }

    @Test
    fun `BadgeEntity toDomain maps all BadgeType values`() {
        for (type in BadgeType.entries) {
            val entity = BadgeEntity(
                id = "badge-${type.name}", badgeType = type.name,
                earnedAt = "2026-03-06T10:00:00", isNew = 1L,
                sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
            )
            val badge = entity.toDomain()
            assertNotNull(badge, "Failed for badge type $type")
            assertEquals(type, badge.type, "Type mismatch for $type")
        }
    }

    @Test
    fun `Badge toEntity maps all fields`() {
        val badge = testBadge(
            id = "badge-1",
            type = BadgeType.GOAL_5,
            earnedAt = LocalDateTime(2026, 3, 6, 10, 0, 0),
            isNew = true
        )

        val entity = badge.toEntity()
        assertEquals("badge-1", entity.id)
        assertEquals("GOAL_5", entity.badgeType)
        assertEquals(1L, entity.isNew)
        assertNotNull(entity.sync_updated_at)
        assertEquals(0L, entity.is_deleted)
    }

    @Test
    fun `Badge toEntity maps isNew false to 0L`() {
        val badge = testBadge(isNew = false)
        assertEquals(0L, badge.toEntity().isNew)
    }

    @Test
    fun `Badge round trip preserves id and type`() {
        val original = testBadge(id = "rt-badge", type = BadgeType.FOCUS_HOUR)
        val entity = original.toEntity()
        val restored = entity.toDomain()
        assertNotNull(restored)
        assertEquals(original.id, restored.id)
        assertEquals(original.type, restored.type)
    }

    @Test
    fun `Badge round trip preserves isNew`() {
        val original = testBadge(isNew = false)
        val restored = original.toEntity().toDomain()
        assertNotNull(restored)
        assertEquals(original.isNew, restored.isNew)
    }

    // ── createNewBadge ──────────────────────────────────────────────

    @Test
    fun `createNewBadge sets isNew to true`() {
        val badge = createNewBadge(BadgeType.STREAK_7)
        assertTrue(badge.isNew)
    }

    @Test
    fun `createNewBadge uses provided type`() {
        val badge = createNewBadge(BadgeType.JOURNAL_30)
        assertEquals(BadgeType.JOURNAL_30, badge.type)
    }

    @Test
    fun `createNewBadge generates unique ids`() {
        val b1 = createNewBadge(BadgeType.FIRST_STEP)
        val b2 = createNewBadge(BadgeType.FIRST_STEP)
        assertNotEquals(b1.id, b2.id)
    }

    // ════════════════════════════════════════════════════════════════
    // GamificationMapper — Challenge
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `ChallengeEntity toDomain maps all fields`() {
        val entity = ChallengeEntity(
            id = "ch-1",
            challengeType = "DAILY_CHECK_IN",
            startDate = "2026-03-01",
            endDate = "2026-03-02",
            currentProgress = 0L,
            targetProgress = 1L,
            isCompleted = 0L,
            completedAt = null,
            xpEarned = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val challenge = entity.toDomain()
        assertNotNull(challenge)
        assertEquals("ch-1", challenge.id)
        assertEquals(ChallengeType.DAILY_CHECK_IN, challenge.type)
        assertEquals(LocalDate(2026, 3, 1), challenge.startDate)
        assertEquals(LocalDate(2026, 3, 2), challenge.endDate)
        assertEquals(0, challenge.currentProgress)
        assertEquals(1, challenge.targetProgress)
        assertFalse(challenge.isCompleted)
        assertNull(challenge.completedAt)
        assertEquals(0, challenge.xpEarned)
    }

    @Test
    fun `ChallengeEntity toDomain maps completed challenge`() {
        val entity = ChallengeEntity(
            id = "ch-done",
            challengeType = "WEEKLY_GOALS",
            startDate = "2026-02-24",
            endDate = "2026-03-03",
            currentProgress = 3L,
            targetProgress = 3L,
            isCompleted = 1L,
            completedAt = "2026-03-02T15:00:00",
            xpEarned = 50L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val challenge = entity.toDomain()
        assertNotNull(challenge)
        assertTrue(challenge.isCompleted)
        assertNotNull(challenge.completedAt)
        assertEquals(50, challenge.xpEarned)
    }

    @Test
    fun `ChallengeEntity toDomain returns null for unknown type`() {
        val entity = ChallengeEntity(
            id = "ch-unknown",
            challengeType = "UNKNOWN_TYPE",
            startDate = "2026-03-01",
            endDate = "2026-03-02",
            currentProgress = 0L,
            targetProgress = 1L,
            isCompleted = 0L,
            completedAt = null,
            xpEarned = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )
        assertNull(entity.toDomain())
    }

    @Test
    fun `ChallengeEntity toDomain maps all ChallengeType values`() {
        for (type in ChallengeType.entries) {
            val entity = ChallengeEntity(
                id = "ch-${type.name}",
                challengeType = type.name,
                startDate = "2026-03-01",
                endDate = "2026-03-31",
                currentProgress = 0L,
                targetProgress = 1L,
                isCompleted = 0L,
                completedAt = null,
                xpEarned = 0L,
                sync_updated_at = null,
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
            val challenge = entity.toDomain()
            assertNotNull(challenge, "Failed for type $type")
            assertEquals(type, challenge.type, "Type mismatch for $type")
        }
    }

    @Test
    fun `Challenge toEntity maps all fields`() {
        val challenge = testChallenge(
            id = "ch-1",
            type = ChallengeType.WEEKLY_HABITS,
            startDate = LocalDate(2026, 3, 1),
            endDate = LocalDate(2026, 3, 8),
            currentProgress = 3,
            targetProgress = 5,
            isCompleted = false,
            xpEarned = 0
        )

        val entity = challenge.toEntity()
        assertEquals("ch-1", entity.id)
        assertEquals("WEEKLY_HABITS", entity.challengeType)
        assertEquals("2026-03-01", entity.startDate)
        assertEquals("2026-03-08", entity.endDate)
        assertEquals(3L, entity.currentProgress)
        assertEquals(5L, entity.targetProgress)
        assertEquals(0L, entity.isCompleted)
        assertNull(entity.completedAt)
    }

    @Test
    fun `Challenge toEntity maps isCompleted true to 1L`() {
        val challenge = testChallenge(isCompleted = true, completedAt = LocalDateTime(2026, 3, 6, 10, 0, 0))
        assertEquals(1L, challenge.toEntity().isCompleted)
    }

    @Test
    fun `Challenge round trip preserves id and type`() {
        val original = testChallenge(id = "rt-ch", type = ChallengeType.MONTHLY_STREAK)
        val restored = original.toEntity().toDomain()
        assertNotNull(restored)
        assertEquals(original.id, restored.id)
        assertEquals(original.type, restored.type)
    }

    @Test
    fun `Challenge round trip preserves progress`() {
        val original = testChallenge(currentProgress = 7, targetProgress = 10)
        val restored = original.toEntity().toDomain()
        assertNotNull(restored)
        assertEquals(original.currentProgress, restored.currentProgress)
        assertEquals(original.targetProgress, restored.targetProgress)
    }

    // ── createNewChallenge ──────────────────────────────────────────

    @Test
    fun `createNewChallenge sets zero progress`() {
        val challenge = createNewChallenge(ChallengeType.DAILY_JOURNAL)
        assertEquals(0, challenge.currentProgress)
    }

    @Test
    fun `createNewChallenge sets correct target`() {
        val challenge = createNewChallenge(ChallengeType.WEEKLY_GOALS)
        assertEquals(ChallengeTargets.getTargetForType(ChallengeType.WEEKLY_GOALS), challenge.targetProgress)
    }

    @Test
    fun `createNewChallenge is not completed`() {
        val challenge = createNewChallenge(ChallengeType.PERFECT_DAY)
        assertFalse(challenge.isCompleted)
        assertNull(challenge.completedAt)
        assertEquals(0, challenge.xpEarned)
    }

    @Test
    fun `createNewChallenge generates unique ids`() {
        val c1 = createNewChallenge(ChallengeType.DAILY_CHECK_IN)
        val c2 = createNewChallenge(ChallengeType.DAILY_CHECK_IN)
        assertNotEquals(c1.id, c2.id)
    }

    // ════════════════════════════════════════════════════════════════
    // GamificationMapper — UserProgress
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `UserProgressEntity toDomain maps all fields`() {
        val entity = UserProgressEntity(
            id = 1L,
            currentStreak = 5L,
            lastCheckInDate = "2026-03-06",
            totalXp = 500L,
            currentLevel = 3L,
            goalsCompleted = 2L,
            habitsCompleted = 10L,
            journalEntriesCount = 5L,
            longestStreak = 10L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val progress = entity.toDomain()
        assertEquals(5, progress.currentStreak)
        assertEquals(LocalDate(2026, 3, 6), progress.lastCheckInDate)
        assertEquals(500, progress.totalXp)
        assertEquals(3, progress.currentLevel)
        assertEquals(2, progress.goalsCompleted)
        assertEquals(10, progress.habitsCompleted)
        assertEquals(5, progress.journalEntriesCount)
        assertEquals(10, progress.longestStreak)
    }

    @Test
    fun `UserProgressEntity toDomain maps null lastCheckInDate`() {
        val entity = UserProgressEntity(
            id = 1L, currentStreak = 0L, lastCheckInDate = null,
            totalXp = 0L, currentLevel = 1L, goalsCompleted = 0L,
            habitsCompleted = 0L, journalEntriesCount = 0L, longestStreak = 0L,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )
        assertNull(entity.toDomain().lastCheckInDate)
    }

    // ════════════════════════════════════════════════════════════════
    // ReviewMapper
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `ReviewReportEntity toDomain maps basic fields`() {
        val stats = testReviewStats()
        val entity = ReviewReportEntity(
            id = "review-1",
            type = "WEEKLY",
            periodStart = "2026-02-27",
            periodEnd = "2026-03-06",
            generatedAt = "2026-03-06T10:00:00",
            summary = "Good week",
            highlightsJson = "[]",
            insightsJson = "[]",
            recommendationsJson = "[]",
            statsJson = stats.toJson(),
            feedbackRating = null,
            feedbackComment = null,
            feedbackAt = null,
            isRead = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val report = entity.toDomain()
        assertEquals("review-1", report.id)
        assertEquals(ReviewType.WEEKLY, report.type)
        assertEquals(LocalDate(2026, 2, 27), report.periodStart)
        assertEquals(LocalDate(2026, 3, 6), report.periodEnd)
        assertEquals("Good week", report.summary)
        assertFalse(report.isRead)
        assertNull(report.feedback)
    }

    @Test
    fun `ReviewReportEntity toDomain maps isRead 1 to true`() {
        val stats = testReviewStats()
        val entity = ReviewReportEntity(
            id = "review-read",
            type = "MONTHLY",
            periodStart = "2026-02-01",
            periodEnd = "2026-02-28",
            generatedAt = "2026-03-01T10:00:00",
            summary = "Summary",
            highlightsJson = "[]",
            insightsJson = "[]",
            recommendationsJson = "[]",
            statsJson = stats.toJson(),
            feedbackRating = null,
            feedbackComment = null,
            feedbackAt = null,
            isRead = 1L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )
        assertTrue(entity.toDomain().isRead)
    }

    @Test
    fun `ReviewReportEntity toDomain maps feedback when present`() {
        val stats = testReviewStats()
        val entity = ReviewReportEntity(
            id = "review-fb",
            type = "WEEKLY",
            periodStart = "2026-02-27",
            periodEnd = "2026-03-06",
            generatedAt = "2026-03-06T10:00:00",
            summary = "Summary",
            highlightsJson = "[]",
            insightsJson = "[]",
            recommendationsJson = "[]",
            statsJson = stats.toJson(),
            feedbackRating = "HELPFUL",
            feedbackComment = "Great review!",
            feedbackAt = "2026-03-06T12:00:00",
            isRead = 1L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val report = entity.toDomain()
        assertNotNull(report.feedback)
        assertEquals(FeedbackRating.HELPFUL, report.feedback!!.rating)
        assertEquals("Great review!", report.feedback!!.comment)
    }

    @Test
    fun `ReviewReportEntity toDomain maps feedback with null feedbackAt uses generatedAt`() {
        val stats = testReviewStats()
        val entity = ReviewReportEntity(
            id = "review-fb-no-at",
            type = "WEEKLY",
            periodStart = "2026-02-27",
            periodEnd = "2026-03-06",
            generatedAt = "2026-03-06T10:00:00",
            summary = "Summary",
            highlightsJson = "[]",
            insightsJson = "[]",
            recommendationsJson = "[]",
            statsJson = stats.toJson(),
            feedbackRating = "NEUTRAL",
            feedbackComment = null,
            feedbackAt = null,
            isRead = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val feedback = entity.toDomain().feedback
        assertNotNull(feedback)
        assertEquals(FeedbackRating.NEUTRAL, feedback.rating)
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), feedback.submittedAt)
    }

    @Test
    fun `ReviewReportEntity toDomain maps all ReviewType values`() {
        for (type in ReviewType.entries) {
            val stats = testReviewStats()
            val entity = ReviewReportEntity(
                id = "review-${type.name}",
                type = type.name,
                periodStart = "2026-01-01",
                periodEnd = "2026-03-06",
                generatedAt = "2026-03-06T10:00:00",
                summary = "S",
                highlightsJson = "[]",
                insightsJson = "[]",
                recommendationsJson = "[]",
                statsJson = stats.toJson(),
                feedbackRating = null,
                feedbackComment = null,
                feedbackAt = null,
                isRead = 0L,
                sync_updated_at = null,
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
            assertEquals(type, entity.toDomain().type, "Failed for type $type")
        }
    }

    @Test
    fun `ReviewReportEntity toDomain deserializes highlights`() {
        val highlights = listOf(testReviewHighlight())
        val stats = testReviewStats()
        val entity = ReviewReportEntity(
            id = "review-hl",
            type = "WEEKLY",
            periodStart = "2026-02-27",
            periodEnd = "2026-03-06",
            generatedAt = "2026-03-06T10:00:00",
            summary = "S",
            highlightsJson = highlights.toJson(),
            insightsJson = "[]",
            recommendationsJson = "[]",
            statsJson = stats.toJson(),
            feedbackRating = null,
            feedbackComment = null,
            feedbackAt = null,
            isRead = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val report = entity.toDomain()
        assertEquals(1, report.highlights.size)
        assertEquals("Great Progress", report.highlights[0].title)
    }

    @Test
    fun `ReviewReportEntity toDomain deserializes insights`() {
        val insights = listOf(testReviewInsight())
        val stats = testReviewStats()
        val entity = ReviewReportEntity(
            id = "review-ins",
            type = "WEEKLY",
            periodStart = "2026-02-27",
            periodEnd = "2026-03-06",
            generatedAt = "2026-03-06T10:00:00",
            summary = "S",
            highlightsJson = "[]",
            insightsJson = insights.toInsightsJson(),
            recommendationsJson = "[]",
            statsJson = stats.toJson(),
            feedbackRating = null,
            feedbackComment = null,
            feedbackAt = null,
            isRead = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val report = entity.toDomain()
        assertEquals(1, report.insights.size)
        assertEquals("Productivity Pattern", report.insights[0].title)
    }

    @Test
    fun `ReviewReportEntity toDomain deserializes recommendations`() {
        val recs = listOf(testReviewRecommendation())
        val stats = testReviewStats()
        val entity = ReviewReportEntity(
            id = "review-recs",
            type = "WEEKLY",
            periodStart = "2026-02-27",
            periodEnd = "2026-03-06",
            generatedAt = "2026-03-06T10:00:00",
            summary = "S",
            highlightsJson = "[]",
            insightsJson = "[]",
            recommendationsJson = recs.toRecommendationsJson(),
            statsJson = stats.toJson(),
            feedbackRating = null,
            feedbackComment = null,
            feedbackAt = null,
            isRead = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val report = entity.toDomain()
        assertEquals(1, report.recommendations.size)
        assertEquals("Add More Goals", report.recommendations[0].title)
    }

    @Test
    fun `ReviewReportEntity toDomain deserializes stats`() {
        val stats = testReviewStats(goalsCompleted = 5, xpEarned = 300)
        val entity = ReviewReportEntity(
            id = "review-stats",
            type = "WEEKLY",
            periodStart = "2026-02-27",
            periodEnd = "2026-03-06",
            generatedAt = "2026-03-06T10:00:00",
            summary = "S",
            highlightsJson = "[]",
            insightsJson = "[]",
            recommendationsJson = "[]",
            statsJson = stats.toJson(),
            feedbackRating = null,
            feedbackComment = null,
            feedbackAt = null,
            isRead = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val report = entity.toDomain()
        assertEquals(5, report.stats.goalsCompleted)
        assertEquals(300, report.stats.xpEarned)
    }

    // ── JSON serializers ────────────────────────────────────────────

    @Test
    fun `highlights toJson and back round trips`() {
        val original = listOf(
            testReviewHighlight(id = "h1", title = "First"),
            testReviewHighlight(id = "h2", title = "Second", category = HighlightCategory.STREAK_ACHIEVED)
        )
        val json = original.toJson()
        assertTrue(json.contains("First"))
        assertTrue(json.contains("Second"))
    }

    @Test
    fun `insights toInsightsJson produces valid JSON`() {
        val original = listOf(testReviewInsight(title = "Insight 1"))
        val json = original.toInsightsJson()
        assertTrue(json.contains("Insight 1"))
    }

    @Test
    fun `recommendations toRecommendationsJson produces valid JSON`() {
        val original = listOf(testReviewRecommendation(title = "Rec 1"))
        val json = original.toRecommendationsJson()
        assertTrue(json.contains("Rec 1"))
    }

    @Test
    fun `stats toJson produces valid JSON`() {
        val stats = testReviewStats(goalsCompleted = 7)
        val json = stats.toJson()
        assertTrue(json.contains("7"))
    }

    @Test
    fun `empty highlights toJson produces empty array`() {
        val json = emptyList<ReviewHighlight>().toJson()
        assertEquals("[]", json)
    }

    @Test
    fun `empty insights toInsightsJson produces empty array`() {
        val json = emptyList<ReviewInsight>().toInsightsJson()
        assertEquals("[]", json)
    }

    @Test
    fun `empty recommendations toRecommendationsJson produces empty array`() {
        val json = emptyList<ReviewRecommendation>().toRecommendationsJson()
        assertEquals("[]", json)
    }

    // ── toSummaryCard ───────────────────────────────────────────────

    @Test
    fun `toSummaryCard weekly review has correct period label`() {
        val report = testReviewReport(
            type = ReviewType.WEEKLY,
            periodStart = LocalDate(2026, 3, 1)
        )
        val card = report.toSummaryCard()
        assertTrue(card.periodLabel.startsWith("Week of"))
        assertTrue(card.periodLabel.contains("Mar"))
    }

    @Test
    fun `toSummaryCard monthly review has correct period label`() {
        val report = testReviewReport(
            type = ReviewType.MONTHLY,
            periodStart = LocalDate(2026, 3, 1)
        )
        val card = report.toSummaryCard()
        assertEquals("March 2026", card.periodLabel)
    }

    @Test
    fun `toSummaryCard quarterly review has correct period label`() {
        val report = testReviewReport(
            type = ReviewType.QUARTERLY,
            periodStart = LocalDate(2026, 1, 1)
        )
        val card = report.toSummaryCard()
        assertEquals("Q1 2026", card.periodLabel)
    }

    @Test
    fun `toSummaryCard yearly review has correct period label`() {
        val report = testReviewReport(
            type = ReviewType.YEARLY,
            periodStart = LocalDate(2026, 1, 1)
        )
        val card = report.toSummaryCard()
        assertEquals("2026 Year in Review", card.periodLabel)
    }

    @Test
    fun `toSummaryCard truncates long summary`() {
        val longSummary = "A".repeat(200)
        val report = testReviewReport(summary = longSummary)
        val card = report.toSummaryCard()
        assertTrue(card.headline.length <= 103) // 100 + "..."
        assertTrue(card.headline.endsWith("..."))
    }

    @Test
    fun `toSummaryCard does not truncate short summary`() {
        val report = testReviewReport(summary = "Short summary")
        val card = report.toSummaryCard()
        assertEquals("Short summary", card.headline)
    }

    @Test
    fun `toSummaryCard has 3 quick stats`() {
        val report = testReviewReport()
        val card = report.toSummaryCard()
        assertEquals(3, card.quickStats.size)
        assertEquals("Goals", card.quickStats[0].label)
        assertEquals("Habits", card.quickStats[1].label)
        assertEquals("XP", card.quickStats[2].label)
    }

    @Test
    fun `toSummaryCard isNew is true when not read`() {
        val report = testReviewReport(isRead = false)
        assertTrue(report.toSummaryCard().isNew)
    }

    @Test
    fun `toSummaryCard isNew is false when read`() {
        val report = testReviewReport(isRead = true)
        assertFalse(report.toSummaryCard().isNew)
    }

    @Test
    fun `toSummaryCard quick stats show UP trend for positive diffs`() {
        val comparison = StatsComparison(
            goalsCompletedDiff = 1,
            habitCompletionRateDiff = 0.1f,
            xpEarnedDiff = 50,
            overallTrend = TrendDirection.UP
        )
        val report = testReviewReport(stats = testReviewStats(comparisonToPrevious = comparison))
        val card = report.toSummaryCard()

        assertEquals(TrendDirection.UP, card.quickStats[0].trend)
        assertEquals(TrendDirection.UP, card.quickStats[1].trend)
        assertEquals(TrendDirection.UP, card.quickStats[2].trend)
    }

    @Test
    fun `toSummaryCard quick stats show DOWN trend for negative diffs`() {
        val comparison = StatsComparison(
            goalsCompletedDiff = -1,
            habitCompletionRateDiff = -0.1f,
            xpEarnedDiff = -50,
            overallTrend = TrendDirection.DOWN
        )
        val report = testReviewReport(stats = testReviewStats(comparisonToPrevious = comparison))
        val card = report.toSummaryCard()

        assertEquals(TrendDirection.DOWN, card.quickStats[0].trend)
        assertEquals(TrendDirection.DOWN, card.quickStats[1].trend)
        assertEquals(TrendDirection.DOWN, card.quickStats[2].trend)
    }

    @Test
    fun `toSummaryCard quick stats show STABLE for zero diffs`() {
        val comparison = StatsComparison(
            goalsCompletedDiff = 0,
            habitCompletionRateDiff = 0f,
            xpEarnedDiff = 0,
            overallTrend = TrendDirection.STABLE
        )
        val report = testReviewReport(stats = testReviewStats(comparisonToPrevious = comparison))
        val card = report.toSummaryCard()

        assertEquals(TrendDirection.STABLE, card.quickStats[0].trend)
        assertEquals(TrendDirection.STABLE, card.quickStats[1].trend)
        assertEquals(TrendDirection.STABLE, card.quickStats[2].trend)
    }

    @Test
    fun `toSummaryCard quick stats have null trend when no comparison`() {
        val report = testReviewReport(stats = testReviewStats(comparisonToPrevious = null))
        val card = report.toSummaryCard()

        assertNull(card.quickStats[0].trend)
        assertNull(card.quickStats[1].trend)
        assertNull(card.quickStats[2].trend)
    }

    // ── generateReviewId / generateHighlightId / etc. ───────────────

    @Test
    fun `generateReviewId returns unique strings`() {
        val id1 = generateReviewId()
        val id2 = generateReviewId()
        assertNotEquals(id1, id2)
    }

    @Test
    fun `generateHighlightId starts with highlight prefix`() {
        val id = generateHighlightId()
        assertTrue(id.startsWith("highlight_"))
    }

    @Test
    fun `generateInsightId starts with insight prefix`() {
        val id = generateInsightId()
        assertTrue(id.startsWith("insight_"))
    }

    @Test
    fun `generateRecommendationId starts with rec prefix`() {
        val id = generateRecommendationId()
        assertTrue(id.startsWith("rec_"))
    }

    // ── toDomainReviews list mapper ─────────────────────────────────

    @Test
    fun `toDomainReviews maps empty list`() {
        assertTrue(emptyList<ReviewReportEntity>().toDomainReviews().isEmpty())
    }

    // ════════════════════════════════════════════════════════════════
    // ReminderMapper
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `ReminderEntity toDomain maps all fields`() {
        val entity = ReminderEntity(
            id = "rem-1",
            title = "Check in",
            message = "Time to check your goals",
            type = "GOAL_CHECK_IN",
            frequency = "DAILY",
            scheduledTime = "09:00",
            scheduledDays = "MONDAY,WEDNESDAY,FRIDAY",
            linkedGoalId = "g-1",
            linkedHabitId = "h-1",
            isEnabled = 1L,
            isSmartTiming = 0L,
            lastTriggeredAt = "2026-03-05T09:00:00",
            snoozedUntil = null,
            createdAt = "2026-01-01T00:00:00",
            updatedAt = "2026-03-01T00:00:00",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val reminder = entity.toDomain()

        assertEquals("rem-1", reminder.id)
        assertEquals("Check in", reminder.title)
        assertEquals("Time to check your goals", reminder.message)
        assertEquals(ReminderType.GOAL_CHECK_IN, reminder.type)
        assertEquals(ReminderFrequency.DAILY, reminder.frequency)
        assertEquals(LocalTime(9, 0), reminder.scheduledTime)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), reminder.scheduledDays)
        assertEquals("g-1", reminder.linkedGoalId)
        assertEquals("h-1", reminder.linkedHabitId)
        assertTrue(reminder.isEnabled)
        assertFalse(reminder.isSmartTiming)
        assertNotNull(reminder.lastTriggeredAt)
        assertNull(reminder.snoozedUntil)
    }

    @Test
    fun `ReminderEntity toDomain maps disabled reminder`() {
        val entity = ReminderEntity(
            id = "rem-disabled",
            title = "T", message = "M",
            type = "CUSTOM", frequency = "ONCE",
            scheduledTime = "10:00", scheduledDays = "",
            linkedGoalId = null, linkedHabitId = null,
            isEnabled = 0L, isSmartTiming = 1L,
            lastTriggeredAt = null, snoozedUntil = null,
            createdAt = "2026-01-01T00:00:00", updatedAt = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )

        val reminder = entity.toDomain()
        assertFalse(reminder.isEnabled)
        assertTrue(reminder.isSmartTiming)
    }

    @Test
    fun `ReminderEntity toDomain maps empty scheduledDays`() {
        val entity = ReminderEntity(
            id = "rem-empty-days",
            title = "T", message = "M",
            type = "DAILY_REFLECTION", frequency = "DAILY",
            scheduledTime = "08:00", scheduledDays = "",
            linkedGoalId = null, linkedHabitId = null,
            isEnabled = 1L, isSmartTiming = 0L,
            lastTriggeredAt = null, snoozedUntil = null,
            createdAt = "2026-01-01T00:00:00", updatedAt = null,
            sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
        )

        assertTrue(entity.toDomain().scheduledDays.isEmpty())
    }

    @Test
    fun `ReminderEntity toDomain maps all ReminderType values`() {
        for (type in ReminderType.entries) {
            val entity = ReminderEntity(
                id = "rem-${type.name}",
                title = "T", message = "M",
                type = type.name, frequency = "DAILY",
                scheduledTime = "08:00", scheduledDays = "",
                linkedGoalId = null, linkedHabitId = null,
                isEnabled = 1L, isSmartTiming = 0L,
                lastTriggeredAt = null, snoozedUntil = null,
                createdAt = "2026-01-01T00:00:00", updatedAt = null,
                sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
            )
            assertEquals(type, entity.toDomain().type, "Failed for type $type")
        }
    }

    @Test
    fun `ReminderEntity toDomain maps all ReminderFrequency values`() {
        for (freq in ReminderFrequency.entries) {
            val entity = ReminderEntity(
                id = "rem-${freq.name}",
                title = "T", message = "M",
                type = "CUSTOM", frequency = freq.name,
                scheduledTime = "08:00", scheduledDays = "",
                linkedGoalId = null, linkedHabitId = null,
                isEnabled = 1L, isSmartTiming = 0L,
                lastTriggeredAt = null, snoozedUntil = null,
                createdAt = "2026-01-01T00:00:00", updatedAt = null,
                sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
            )
            assertEquals(freq, entity.toDomain().frequency, "Failed for frequency $freq")
        }
    }

    @Test
    fun `toDomainReminders maps empty list`() {
        assertTrue(emptyList<ReminderEntity>().toDomainReminders().isEmpty())
    }

    // ── ReminderSettingsEntity.toDomain ──────────────────────────────

    @Test
    fun `ReminderSettingsEntity toDomain maps all fields`() {
        val entity = ReminderSettingsEntity(
            id = "default",
            isEnabled = 1L,
            quietHoursStart = "22:00",
            quietHoursEnd = "07:00",
            preferredMorningTime = "08:00",
            preferredEveningTime = "20:00",
            smartTimingEnabled = 1L,
            maxRemindersPerDay = 5L,
            weeklyReviewDay = "SUNDAY",
            weeklyReviewTime = "10:00"
        )

        val settings = entity.toDomain()
        assertEquals("default", settings.id)
        assertTrue(settings.isEnabled)
        assertEquals(LocalTime(22, 0), settings.quietHoursStart)
        assertEquals(LocalTime(7, 0), settings.quietHoursEnd)
        assertEquals(LocalTime(8, 0), settings.preferredMorningTime)
        assertEquals(LocalTime(20, 0), settings.preferredEveningTime)
        assertTrue(settings.smartTimingEnabled)
        assertEquals(5, settings.maxRemindersPerDay)
        assertEquals(DayOfWeek.SUNDAY, settings.weeklyReviewDay)
        assertEquals(LocalTime(10, 0), settings.weeklyReviewTime)
    }

    @Test
    fun `ReminderSettingsEntity toDomain maps disabled`() {
        val entity = ReminderSettingsEntity(
            id = "default",
            isEnabled = 0L,
            quietHoursStart = "22:00",
            quietHoursEnd = "07:00",
            preferredMorningTime = "08:00",
            preferredEveningTime = "20:00",
            smartTimingEnabled = 0L,
            maxRemindersPerDay = 3L,
            weeklyReviewDay = "MONDAY",
            weeklyReviewTime = "09:00"
        )

        val settings = entity.toDomain()
        assertFalse(settings.isEnabled)
        assertFalse(settings.smartTimingEnabled)
        assertEquals(3, settings.maxRemindersPerDay)
        assertEquals(DayOfWeek.MONDAY, settings.weeklyReviewDay)
    }

    // ── UserActivityPatternEntity.toDomain ──────────────────────────

    @Test
    fun `UserActivityPatternEntity toDomain maps all fields`() {
        val entity = UserActivityPatternEntity(
            id = "default",
            mostActiveHours = "9,10,14,15",
            mostActiveDays = "MONDAY,TUESDAY,WEDNESDAY",
            averageResponseTime = 15L,
            bestCheckInTimes = "09:00,20:00",
            lastUpdated = "2026-03-06T10:00:00"
        )

        val pattern = entity.toDomain()
        assertEquals(listOf(9, 10, 14, 15), pattern.mostActiveHours)
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY), pattern.mostActiveDays)
        assertEquals(15L, pattern.averageResponseTime)
        assertEquals(listOf(LocalTime(9, 0), LocalTime(20, 0)), pattern.bestCheckInTimes)
    }

    @Test
    fun `UserActivityPatternEntity toDomain handles empty strings`() {
        val entity = UserActivityPatternEntity(
            id = "default",
            mostActiveHours = "",
            mostActiveDays = "",
            averageResponseTime = 0L,
            bestCheckInTimes = "",
            lastUpdated = "2026-03-06T10:00:00"
        )

        val pattern = entity.toDomain()
        assertTrue(pattern.mostActiveHours.isEmpty())
        assertTrue(pattern.mostActiveDays.isEmpty())
        assertTrue(pattern.bestCheckInTimes.isEmpty())
    }

    // ── ScheduledNotificationEntity.toDomain ────────────────────────

    @Test
    fun `ScheduledNotificationEntity toDomain maps all fields`() {
        val entity = ScheduledNotificationEntity(
            id = "notif-1",
            reminderId = "rem-1",
            title = "Check in",
            message = "Time to check",
            scheduledAt = "2026-03-06T09:00:00",
            isDelivered = 0L,
            deliveredAt = null,
            isSnoozed = 0L,
            isDismissed = 0L
        )

        val notif = entity.toDomain()
        assertEquals("notif-1", notif.id)
        assertEquals("rem-1", notif.reminderId)
        assertEquals("Check in", notif.title)
        assertFalse(notif.isDelivered)
        assertNull(notif.deliveredAt)
        assertFalse(notif.isSnoozed)
        assertFalse(notif.isDismissed)
    }

    @Test
    fun `ScheduledNotificationEntity toDomain maps delivered notification`() {
        val entity = ScheduledNotificationEntity(
            id = "notif-delivered",
            reminderId = "rem-1",
            title = "T",
            message = "M",
            scheduledAt = "2026-03-06T09:00:00",
            isDelivered = 1L,
            deliveredAt = "2026-03-06T09:01:00",
            isSnoozed = 0L,
            isDismissed = 0L
        )

        val notif = entity.toDomain()
        assertTrue(notif.isDelivered)
        assertNotNull(notif.deliveredAt)
    }

    @Test
    fun `ScheduledNotificationEntity toDomain maps snoozed and dismissed`() {
        val entity = ScheduledNotificationEntity(
            id = "notif-snoozed",
            reminderId = "rem-1",
            title = "T",
            message = "M",
            scheduledAt = "2026-03-06T09:00:00",
            isDelivered = 0L,
            deliveredAt = null,
            isSnoozed = 1L,
            isDismissed = 1L
        )

        val notif = entity.toDomain()
        assertTrue(notif.isSnoozed)
        assertTrue(notif.isDismissed)
    }

    @Test
    fun `toDomainNotifications maps empty list`() {
        assertTrue(emptyList<ScheduledNotificationEntity>().toDomainNotifications().isEmpty())
    }

    // ── Storage string helpers ──────────────────────────────────────

    @Test
    fun `toStorageString joins DayOfWeek names`() {
        val days = listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
        assertEquals("MONDAY,FRIDAY", days.toStorageString())
    }

    @Test
    fun `toStorageString handles empty list`() {
        assertEquals("", emptyList<DayOfWeek>().toStorageString())
    }

    @Test
    fun `toStorageString handles single element`() {
        assertEquals("SUNDAY", listOf(DayOfWeek.SUNDAY).toStorageString())
    }

    @Test
    fun `toTimesStorageString joins LocalTime values`() {
        val times = listOf(LocalTime(9, 0), LocalTime(20, 30))
        val result = times.toTimesStorageString()
        assertTrue(result.contains("09:00"))
        assertTrue(result.contains("20:30"))
    }

    @Test
    fun `toTimesStorageString handles empty list`() {
        assertEquals("", emptyList<LocalTime>().toTimesStorageString())
    }

    @Test
    fun `toHoursStorageString joins integers`() {
        val hours = listOf(9, 10, 14, 15)
        assertEquals("9,10,14,15", hours.toHoursStorageString())
    }

    @Test
    fun `toHoursStorageString handles empty list`() {
        assertEquals("", emptyList<Int>().toHoursStorageString())
    }

    // ════════════════════════════════════════════════════════════════
    // GoalDependencyMapper
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `GoalDependencyEntity toDomain maps all fields`() {
        val entity = GoalDependencyEntity(
            id = "dep-1",
            sourceGoalId = "g-1",
            targetGoalId = "g-2",
            dependencyType = "BLOCKS",
            createdAt = "2026-03-06T10:00:00",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val dep = entity.toDomain()
        assertEquals("dep-1", dep.id)
        assertEquals("g-1", dep.sourceGoalId)
        assertEquals("g-2", dep.targetGoalId)
        assertEquals(DependencyType.BLOCKS, dep.dependencyType)
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), dep.createdAt)
    }

    @Test
    fun `GoalDependencyEntity toDomain maps all DependencyType values`() {
        for (type in DependencyType.entries) {
            val entity = GoalDependencyEntity(
                id = "dep-${type.name}",
                sourceGoalId = "g-1",
                targetGoalId = "g-2",
                dependencyType = type.name,
                createdAt = "2026-03-06T10:00:00",
                sync_updated_at = null,
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
            assertEquals(type, entity.toDomain().dependencyType, "Failed for type $type")
        }
    }

    @Test
    fun `GoalDependency toEntity maps all fields`() {
        val dep = testGoalDependency(
            id = "dep-1",
            sourceGoalId = "g-1",
            targetGoalId = "g-2",
            dependencyType = DependencyType.PARENT_OF,
            createdAt = LocalDateTime(2026, 3, 6, 10, 0, 0)
        )

        val entity = dep.toEntity()
        assertEquals("dep-1", entity.id)
        assertEquals("g-1", entity.sourceGoalId)
        assertEquals("g-2", entity.targetGoalId)
        assertEquals("PARENT_OF", entity.dependencyType)
        assertNotNull(entity.sync_updated_at)
        assertEquals(0L, entity.is_deleted)
    }

    @Test
    fun `GoalDependency round trip preserves all fields`() {
        val original = testGoalDependency(
            id = "rt-dep",
            sourceGoalId = "g-src",
            targetGoalId = "g-tgt",
            dependencyType = DependencyType.SUPPORTS
        )
        val restored = original.toEntity().toDomain()
        assertEquals(original.id, restored.id)
        assertEquals(original.sourceGoalId, restored.sourceGoalId)
        assertEquals(original.targetGoalId, restored.targetGoalId)
        assertEquals(original.dependencyType, restored.dependencyType)
    }

    @Test
    fun `toDomainDependencies maps empty list`() {
        assertTrue(emptyList<GoalDependencyEntity>().toDomainDependencies().isEmpty())
    }

    @Test
    fun `toDomainDependencies maps multiple entities`() {
        val entities = listOf(
            GoalDependencyEntity("d1", "g1", "g2", "BLOCKS", "2026-03-06T10:00:00", null, 0L, 0L, null),
            GoalDependencyEntity("d2", "g2", "g3", "RELATED", "2026-03-06T10:00:00", null, 0L, 0L, null)
        )
        val result = entities.toDomainDependencies()
        assertEquals(2, result.size)
        assertEquals(DependencyType.BLOCKS, result[0].dependencyType)
        assertEquals(DependencyType.RELATED, result[1].dependencyType)
    }

    // ── createNewDependency ─────────────────────────────────────────

    @Test
    fun `createNewDependency sets fields correctly`() {
        val dep = createNewDependency(
            sourceGoalId = "g-1",
            targetGoalId = "g-2",
            dependencyType = DependencyType.CHILD_OF
        )
        assertEquals("g-1", dep.sourceGoalId)
        assertEquals("g-2", dep.targetGoalId)
        assertEquals(DependencyType.CHILD_OF, dep.dependencyType)
    }

    @Test
    fun `createNewDependency generates unique ids`() {
        val d1 = createNewDependency("g1", "g2", DependencyType.BLOCKS)
        val d2 = createNewDependency("g1", "g2", DependencyType.BLOCKS)
        assertNotEquals(d1.id, d2.id)
    }

    // ════════════════════════════════════════════════════════════════
    // GoalAnalyticsMapper
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `GoalAnalyticsData toDomainAnalytics maps basic fields`() {
        val data = GoalAnalyticsData(
            totalGoals = 10L,
            activeGoals = 5L,
            completedGoals = 3L,
            completionRate = 30.0,
            goalsByCategory = emptyMap(),
            goalsByTimeline = emptyMap(),
            goalsByStatus = emptyMap(),
            averageProgressByCategory = emptyMap()
        )

        val analytics = data.toDomainAnalytics(upcomingDeadlines = 2)
        assertEquals(10, analytics.totalGoals)
        assertEquals(5, analytics.activeGoals)
        assertEquals(3, analytics.completedGoals)
        assertEquals(0.3f, analytics.completionRate, 0.001f) // 30 / 100
        assertEquals(2, analytics.upcomingDeadlines)
    }

    @Test
    fun `GoalAnalyticsData toDomainAnalytics converts completionRate from percentage`() {
        val data = GoalAnalyticsData(
            totalGoals = 10L, activeGoals = 5L, completedGoals = 5L,
            completionRate = 50.0,
            goalsByCategory = emptyMap(),
            goalsByTimeline = emptyMap(),
            goalsByStatus = emptyMap(),
            averageProgressByCategory = emptyMap()
        )

        assertEquals(0.5f, data.toDomainAnalytics().completionRate, 0.001f)
    }

    @Test
    fun `GoalAnalyticsData toDomainAnalytics defaults upcomingDeadlines to 0`() {
        val data = GoalAnalyticsData(
            totalGoals = 0L, activeGoals = 0L, completedGoals = 0L,
            completionRate = 0.0,
            goalsByCategory = emptyMap(),
            goalsByTimeline = emptyMap(),
            goalsByStatus = emptyMap(),
            averageProgressByCategory = emptyMap()
        )

        assertEquals(0, data.toDomainAnalytics().upcomingDeadlines)
    }

    @Test
    fun `GoalAnalyticsData toDomainAnalytics maps goalsByCategory`() {
        val data = GoalAnalyticsData(
            totalGoals = 5L, activeGoals = 3L, completedGoals = 2L,
            completionRate = 40.0,
            goalsByCategory = mapOf("CAREER" to 3L, "PHYSICAL" to 2L),
            goalsByTimeline = emptyMap(),
            goalsByStatus = emptyMap(),
            averageProgressByCategory = emptyMap()
        )

        val analytics = data.toDomainAnalytics()
        assertEquals(3, analytics.goalsByCategory[GoalCategory.CAREER])
        assertEquals(2, analytics.goalsByCategory[GoalCategory.PHYSICAL])
    }

    @Test
    fun `GoalAnalyticsData toDomainAnalytics maps goalsByTimeline`() {
        val data = GoalAnalyticsData(
            totalGoals = 5L, activeGoals = 3L, completedGoals = 2L,
            completionRate = 40.0,
            goalsByCategory = emptyMap(),
            goalsByTimeline = mapOf("SHORT_TERM" to 2L, "LONG_TERM" to 3L),
            goalsByStatus = emptyMap(),
            averageProgressByCategory = emptyMap()
        )

        val analytics = data.toDomainAnalytics()
        assertEquals(2, analytics.goalsByTimeline[GoalTimeline.SHORT_TERM])
        assertEquals(3, analytics.goalsByTimeline[GoalTimeline.LONG_TERM])
    }

    @Test
    fun `GoalAnalyticsData toDomainAnalytics maps averageProgressByCategory`() {
        val data = GoalAnalyticsData(
            totalGoals = 5L, activeGoals = 3L, completedGoals = 2L,
            completionRate = 40.0,
            goalsByCategory = emptyMap(),
            goalsByTimeline = emptyMap(),
            goalsByStatus = emptyMap(),
            averageProgressByCategory = mapOf("CAREER" to 75.0, "PHYSICAL" to 50.0)
        )

        val analytics = data.toDomainAnalytics()
        assertEquals(75f, analytics.averageProgressPerCategory[GoalCategory.CAREER]!!, 0.01f)
        assertEquals(50f, analytics.averageProgressPerCategory[GoalCategory.PHYSICAL]!!, 0.01f)
    }

    @Test
    fun `GoalAnalyticsData toDomainAnalytics handles empty maps`() {
        val data = GoalAnalyticsData(
            totalGoals = 0L, activeGoals = 0L, completedGoals = 0L,
            completionRate = 0.0,
            goalsByCategory = emptyMap(),
            goalsByTimeline = emptyMap(),
            goalsByStatus = emptyMap(),
            averageProgressByCategory = emptyMap()
        )

        val analytics = data.toDomainAnalytics()
        assertTrue(analytics.goalsByCategory.isEmpty())
        assertTrue(analytics.goalsByTimeline.isEmpty())
        assertTrue(analytics.averageProgressPerCategory.isEmpty())
    }

    // ════════════════════════════════════════════════════════════════
    // DateTimeParser
    // ════════════════════════════════════════════════════════════════

    @Test
    fun `parseLocalDateTime parses plain LocalDateTime format`() {
        val result = parseLocalDateTime("2026-03-06T10:00:00")
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), result)
    }

    @Test
    fun `parseLocalDateTime parses LocalDateTime with fractional seconds`() {
        val result = parseLocalDateTime("2026-03-06T10:00:00.429")
        assertEquals(2026, result.year)
        assertEquals(3, result.monthNumber)
        assertEquals(6, result.dayOfMonth)
        assertEquals(10, result.hour)
    }

    @Test
    fun `parseLocalDateTime parses Instant with Z suffix`() {
        val result = parseLocalDateTime("2026-03-06T10:00:00Z")
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), result)
    }

    @Test
    fun `parseLocalDateTime parses Instant with offset`() {
        val result = parseLocalDateTime("2026-03-06T10:00:00+00:00")
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), result)
    }

    @Test
    fun `parseLocalDateTime parses Instant with fractional seconds and Z`() {
        val result = parseLocalDateTime("2026-03-06T13:06:35.429Z")
        assertEquals(2026, result.year)
        assertEquals(3, result.monthNumber)
        assertEquals(6, result.dayOfMonth)
        assertEquals(13, result.hour)
        assertEquals(6, result.minute)
        assertEquals(35, result.second)
    }

    @Test
    fun `parseLocalDateTime parses Instant with fractional seconds and offset`() {
        val result = parseLocalDateTime("2026-03-06T13:06:35.429+00:00")
        assertEquals(2026, result.year)
        assertEquals(13, result.hour)
    }

    @Test
    fun `parseLocalDateTime parses Instant with non-UTC offset`() {
        // +05:00 means the UTC time is 5 hours earlier
        val result = parseLocalDateTime("2026-03-06T15:00:00+05:00")
        // The Instant is 15:00+05:00 = 10:00 UTC
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), result)
    }

    @Test
    fun `parseLocalDateTime parses midnight`() {
        val result = parseLocalDateTime("2026-03-06T00:00:00")
        assertEquals(0, result.hour)
        assertEquals(0, result.minute)
        assertEquals(0, result.second)
    }

    @Test
    fun `parseLocalDateTime parses end of day`() {
        val result = parseLocalDateTime("2026-03-06T23:59:59")
        assertEquals(23, result.hour)
        assertEquals(59, result.minute)
        assertEquals(59, result.second)
    }
}
