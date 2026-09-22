package az.tribe.lifeplanner.previews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import androidx.compose.foundation.layout.Column
import az.tribe.lifeplanner.domain.model.Badge
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.service.DecisionScorecard
import az.tribe.lifeplanner.domain.service.LocalPossibilityFallback
import az.tribe.lifeplanner.ui.possibility.PossibilityCard
import az.tribe.lifeplanner.ui.components.BadgeCard
import az.tribe.lifeplanner.ui.components.BadgeMedallion
import az.tribe.lifeplanner.domain.model.Decision
import az.tribe.lifeplanner.domain.model.OutcomeQuality
import az.tribe.lifeplanner.ui.decision.DecisionGradeCard
import az.tribe.lifeplanner.ui.decision.GradeStep
import az.tribe.lifeplanner.ui.decision.ScorecardCard
import az.tribe.lifeplanner.ui.goal.GoalJourneyCard
import az.tribe.lifeplanner.ui.intro.FeatureIntroCatalog
import az.tribe.lifeplanner.ui.intro.FeatureIntroSheet
import az.tribe.lifeplanner.ui.theme.LifePlannerTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * JVM screenshot previews: renders commonMain composables with Robolectric native graphics,
 * no emulator or device needed. PNGs land in app/shared/build/previews/.
 *
 * Run: ./gradlew :app:shared:testAndroidHostTest --tests "az.tribe.lifeplanner.previews.PreviewScreenshots"
 *
 * Workflow: when changing a component, add or adjust a preview here, render, and review the
 * PNG before building for a device. Keep fixtures deterministic (fixed dates, isNew = false)
 * so reruns produce comparable images.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PreviewScreenshots {

    @get:Rule
    val compose = createComposeRule()

    private fun snap(name: String, darkTheme: Boolean = true, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LifePlannerTheme(darkTheme = darkTheme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.padding(16.dp)) { content() }
                }
            }
        }
        // Advance past entrance animations to a stable frame; infinite transitions
        // (badge wobble) stay frozen because autoAdvance is off.
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().captureRoboImage("build/previews/$name.png")
    }

    // Decision journal track record, in the three states that actually differ: nothing reviewed
    // yet, a badly calibrated record, and a well calibrated one. Rendered in both themes because
    // this card sits on primaryContainer and needs to read on either.
    private val freshScorecard = DecisionScorecard(logged = 4, reviewed = 0)
    private val overconfidentScorecard =
        DecisionScorecard(logged = 18, reviewed = 11, goodProcess = 8, goodResult = 7, averageConfidence = 84)
    private val calibratedScorecard =
        DecisionScorecard(logged = 30, reviewed = 30, goodProcess = 24, goodResult = 19, averageConfidence = 65)

    @Test
    fun scorecardFresh() = snap("scorecard_fresh", darkTheme = false) { ScorecardCard(freshScorecard) }

    @Test
    fun scorecardOverconfidentLight() =
        snap("scorecard_overconfident_light", darkTheme = false) { ScorecardCard(overconfidentScorecard) }

    @Test
    fun scorecardOverconfidentDark() =
        snap("scorecard_overconfident_dark", darkTheme = true) { ScorecardCard(overconfidentScorecard) }

    @Test
    fun scorecardCalibrated() =
        snap("scorecard_calibrated", darkTheme = false) { ScorecardCard(calibratedScorecard) }

    // Reviewing a call, as two plain questions rather than a four-way grid. Previewed at each
    // step because only two buttons are ever on screen and the second question is the hard one.
    private fun reviewable(quality: OutcomeQuality? = null) = Decision(
        id = "preview-decision",
        question = "Should I drop the Tuesday gym slot and move it to mornings?",
        chosenOption = "Move to mornings",
        confidence = 80,
        decidedAt = LocalDateTime(2026, 8, 1, 9, 0),
        outcomeQuality = quality,
    )

    @Test
    fun gradeCardStepOne() = snap("grade_step1_light", darkTheme = false) { DecisionGradeCard(reviewable()) {} }

    @Test
    fun gradeCardStepOneDark() = snap("grade_step1_dark", darkTheme = true) { DecisionGradeCard(reviewable()) {} }

    @Test
    fun gradeCardStepTwo() = snap("grade_step2_light", darkTheme = false) {
        GradeStep(
            prompt = "Knowing only what you knew then, was it the right call?",
            affirmative = "Right call",
            negative = "Flawed call",
            onAnswer = {},
        )
    }

    @Test
    fun gradeCardGraded() = snap("grade_done_light", darkTheme = false) {
        DecisionGradeCard(reviewable(OutcomeQuality.GOOD_PROCESS_BAD_RESULT)) {}
    }

    private val earnedBadge = Badge(
        id = "preview-badge",
        type = BadgeType.STREAK_7,
        earnedAt = LocalDateTime(2026, 7, 20, 8, 0),
        isNew = false,
    )

    private fun goal(milestonesDone: Int, status: GoalStatus = GoalStatus.IN_PROGRESS) = Goal(
        id = "preview-goal",
        category = GoalCategory.BODY,
        title = "Run a half marathon",
        description = "Train up to 21K by mid October",
        status = status,
        timeline = GoalTimeline.MID_TERM,
        dueDate = LocalDate(2026, 10, 15),
        milestones = listOf(
            Milestone("m1", "Run 5K without stopping", isCompleted = milestonesDone >= 1),
            Milestone("m2", "Finish a 10K race", isCompleted = milestonesDone >= 2),
            Milestone("m3", "Complete the half marathon", isCompleted = milestonesDone >= 3),
        ),
        createdAt = LocalDateTime(2026, 5, 1, 9, 0),
        completionRate = milestonesDone / 3f,
    )

    @Test
    fun badgeCards() = snap("BadgeCard") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BadgeCard(
                badge = earnedBadge,
                badgeType = BadgeType.STREAK_7,
                isEarned = true,
                modifier = Modifier.width(130.dp),
            )
            BadgeCard(
                badge = null,
                badgeType = BadgeType.FOCUS_10,
                isEarned = false,
                modifier = Modifier.width(130.dp),
            )
        }
    }

    @Test
    fun badgeMedallions() = snap("BadgeMedallion") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BadgeMedallion(type = BadgeType.STREAK_7, isEarned = true, size = 56.dp)
            BadgeMedallion(type = BadgeType.FIRST_STEP, isEarned = true, size = 56.dp)
            BadgeMedallion(type = BadgeType.FOCUS_10, isEarned = false, size = 56.dp)
        }
    }

    @Test
    fun goalJourneyMidway() = snap("GoalJourneyCard_midway") {
        GoalJourneyCard(goal = goal(milestonesDone = 1), horizontalPadding = 0.dp)
    }

    @Test
    fun goalJourneyFinalStep() = snap("GoalJourneyCard_finalStep") {
        GoalJourneyCard(goal = goal(milestonesDone = 2), horizontalPadding = 0.dp)
    }

    @Test
    fun goalJourneyComplete() = snap("GoalJourneyCard_complete") {
        GoalJourneyCard(
            goal = goal(milestonesDone = 3, status = GoalStatus.COMPLETED),
            horizontalPadding = 0.dp,
        )
    }

    @Test
    fun goalJourneyLight() = snap("GoalJourneyCard_light", darkTheme = false) {
        GoalJourneyCard(goal = goal(milestonesDone = 1), horizontalPadding = 0.dp)
    }

    /**
     * Renders a real catalog entry rather than a mock, so what the owner reviews is the copy that
     * actually ships. One test per intro because a compose rule allows a single setContent.
     */
    private fun snapIntro(introId: String) {
        val intro = requireNotNull(FeatureIntroCatalog[introId]) { "no intro for $introId" }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            LifePlannerTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    FeatureIntroSheet(
                        intro = intro,
                        accent = MaterialTheme.colorScheme.primary,
                        onDismiss = {},
                        onContinue = {},
                    )
                }
            }
        }
        // The sheet animates in inside a dialog window, so capture the whole screen.
        compose.mainClock.advanceTimeBy(1_500)
        captureScreenRoboImage("build/previews/FeatureIntro_${introId.removePrefix("intro_")}.png")
    }

    /**
     * Renders the real LocalPossibilityFallback output for the preview goal, so reviewing this
     * PNG reviews the actual no-AI copy users will see during an outage.
     */
    @Test
    fun possibilityCards() = snap("PossibilityCard") {
        val options = LocalPossibilityFallback()(goal(milestonesDone = 1))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            options.take(3).forEachIndexed { i, p ->
                PossibilityCard(p, selected = i == 0, onToggle = {})
            }
        }
    }

    /**
     * The chain as the goal's whole story: area with score, goal, milestone progress with the
     * next step, and the coach's read closing the card with a byline.
     */
    @Test
    fun whyChain() = snap("WhyChain") {
        az.tribe.lifeplanner.ui.goal.WhyChainComponent(
            valueTitle = "💪 Physical",
            goalTitle = "Run a half marathon",
            milestoneCount = 3,
            milestonesDone = 1,
            nextStep = "Finish a 10K race",
            areaScore = 4.0,
            lowestNote = "your lowest",
            coachRead = "1 of 3 done. Next is \"Finish a 10K race\". Physical is your lowest " +
                "area right now, which makes this the goal with the most to move.",
            coachName = "Kai",
            coachTitle = "Fitness Coach",
            onChat = {},
            reasoning = "Training for something concrete beats exercising in the abstract.",
            onValueClick = {},
        )
    }

    private fun habit(
        title: String,
        type: az.tribe.lifeplanner.domain.enum.HabitType = az.tribe.lifeplanner.domain.enum.HabitType.BUILD,
        completions: Int = 18,
    ) = az.tribe.lifeplanner.domain.model.Habit(
        id = "preview-habit",
        title = title,
        category = GoalCategory.BODY,
        frequency = az.tribe.lifeplanner.domain.enum.HabitFrequency.DAILY,
        currentStreak = 5,
        longestStreak = 12,
        totalCompletions = completions,
        createdAt = LocalDateTime(2026, 6, 1, 7, 0),
        type = type,
    )

    /** Habit paper header, same language as the goal one. Streak lives in the tiles, not here. */
    @Test
    fun habitPaperHeader() = snap("HabitPaperHeader") {
        az.tribe.lifeplanner.ui.habit.HabitPaperHeader(
            habit = habit("Run before the day starts, whatever the weather looks like"),
            ratePercent = 72f,
        )
    }

    /** A quit habit that has not started yet: no progress line, and the meta says day one. */
    @Test
    fun habitPaperHeaderQuitFresh() = snap("HabitPaperHeader_quitFresh") {
        az.tribe.lifeplanner.ui.habit.HabitPaperHeader(
            habit = habit(
                "No sugar after dinner",
                type = az.tribe.lifeplanner.domain.enum.HabitType.QUIT,
                completions = 0,
            ),
            ratePercent = 0f,
        )
    }

    /** Profile paper header: level and rank once, identity as a byline, one thin XP line. */
    @Test
    fun profilePaperHeader() = snap("ProfilePaperHeader") {
        az.tribe.lifeplanner.ui.profile.ProfilePaperHeader(
            user = az.tribe.lifeplanner.domain.model.User(
                id = "preview-user",
                email = "kamran@example.com",
                displayName = "Kamran",
                selectedSymbol = "🦅",
                createdAt = kotlin.time.Instant.parse("2026-06-01T07:00:00Z"),
            ),
            userProgress = az.tribe.lifeplanner.domain.model.UserProgress(
                currentStreak = 5,
                lastCheckInDate = LocalDate(2026, 8, 14),
                totalXp = 1240,
                currentLevel = 4,
            ),
            syncStatus = az.tribe.lifeplanner.data.sync.SyncStatus(
                state = az.tribe.lifeplanner.data.sync.SyncState.SYNCED,
            ),
            onRetrySync = {},
        )
    }

    /** Journal entry paper header: mood overline, wrapping title, date line. */
    @Test
    fun journalPaperHeader() = snap("JournalPaperHeader") {
        az.tribe.lifeplanner.ui.journal.JournalEntryPaperHeader(
            entry = az.tribe.lifeplanner.domain.model.JournalEntry(
                id = "preview-entry",
                title = "The run I nearly skipped turned into the best hour of the week",
                content = "…",
                mood = az.tribe.lifeplanner.domain.enum.Mood.HAPPY,
                date = LocalDate(2026, 8, 14),
                createdAt = LocalDateTime(2026, 8, 14, 21, 30),
            ),
            moodColor = androidx.compose.ui.graphics.Color(0xFF2AAF6E),
        )
    }

    /** The paper header: overline, wrapping title, thin progress line, one quiet meta line. */
    @Test
    fun goalPaperHeader() = snap("GoalPaperHeader") {
        az.tribe.lifeplanner.ui.components.GoalPaperHeader(
            goal = goal(milestonesDone = 1).copy(completionRate = 1 / 3f, progress = 33L),
        )
    }

    /** A real-length title has to wrap like text, not clip like a banner. */
    @Test
    fun goalPaperHeaderLongTitle() = snap("GoalPaperHeader_longTitle") {
        az.tribe.lifeplanner.ui.components.GoalPaperHeader(
            goal = goal(milestonesDone = 2).copy(
                title = "Run the Valencia half marathon with my brother without walking any of it",
                progress = 66L,
            ),
        )
    }

    /** The coach draft as it now reads inside the milestones card: advice lines, not an offer. */
    @Test
    fun milestonesWithAdvice() = snap("Milestones_withAdvice") {
        az.tribe.lifeplanner.ui.goal.ModernMilestonesCard(
            milestones = goal(milestonesDone = 1).milestones,
            onMilestoneToggle = {},
            onAddMilestone = {},
            coachDraft = {
                az.tribe.lifeplanner.ui.goal.CoachMilestonesContent(
                    goalTitle = "Run a half marathon",
                    category = GoalCategory.BODY,
                    description = "Train up to 21K by mid October",
                    existingTitles = goal(milestonesDone = 1).milestones.map { it.title },
                )
            },
        )
    }

    @Test
    fun featureIntroPossibility() = snapIntro(FeatureIntroCatalog.POSSIBILITY)

    @Test
    fun featureIntroVision() = snapIntro(FeatureIntroCatalog.VISION)

    @Test
    fun featureIntroQuest() = snapIntro(FeatureIntroCatalog.QUEST)

    @Test
    fun featureIntroWeeklyReview() = snapIntro(FeatureIntroCatalog.WEEKLY_REVIEW)

    @Test
    fun featureIntroDecisionJournal() = snapIntro(FeatureIntroCatalog.DECISION_JOURNAL)

    @Test
    fun featureIntroDecisionReview() = snapIntro(FeatureIntroCatalog.DECISION_REVIEW)

    @Test
    fun featureIntroMyPatterns() = snapIntro(FeatureIntroCatalog.MY_PATTERNS)
}
