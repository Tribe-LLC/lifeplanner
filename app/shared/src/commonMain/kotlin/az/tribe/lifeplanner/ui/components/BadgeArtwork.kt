package az.tribe.lifeplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import az.tribe.lifeplanner.domain.enum.BadgeType
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.Alarm
import com.adamglin.phosphoricons.regular.ArrowCounterClockwise
import com.adamglin.phosphoricons.regular.Barbell
import com.adamglin.phosphoricons.regular.BookOpen
import com.adamglin.phosphoricons.regular.Brain
import com.adamglin.phosphoricons.regular.Briefcase
import com.adamglin.phosphoricons.regular.CalendarCheck
import com.adamglin.phosphoricons.regular.CheckCircle
import com.adamglin.phosphoricons.regular.ClockCountdown
import com.adamglin.phosphoricons.regular.Compass
import com.adamglin.phosphoricons.regular.Crosshair
import com.adamglin.phosphoricons.regular.Crown
import com.adamglin.phosphoricons.regular.Diamond
import com.adamglin.phosphoricons.regular.Fire
import com.adamglin.phosphoricons.regular.Heart
import com.adamglin.phosphoricons.regular.Lightning
import com.adamglin.phosphoricons.regular.Medal
import com.adamglin.phosphoricons.regular.MoonStars
import com.adamglin.phosphoricons.regular.Mountains
import com.adamglin.phosphoricons.regular.Notebook
import com.adamglin.phosphoricons.regular.Path
import com.adamglin.phosphoricons.regular.PenNib
import com.adamglin.phosphoricons.regular.Repeat
import com.adamglin.phosphoricons.regular.Rocket
import com.adamglin.phosphoricons.regular.Scales
import com.adamglin.phosphoricons.regular.Shield
import com.adamglin.phosphoricons.regular.Books
import com.adamglin.phosphoricons.regular.Lightbulb
import com.adamglin.phosphoricons.regular.Moon
import com.adamglin.phosphoricons.regular.Sparkle
import com.adamglin.phosphoricons.regular.Student
import com.adamglin.phosphoricons.regular.Sun
import com.adamglin.phosphoricons.regular.Target
import com.adamglin.phosphoricons.regular.Timer
import com.adamglin.phosphoricons.regular.Trophy

/**
 * The one place badge artwork is decided. Every screen that draws a badge goes through
 * [BadgeMedallion] or [badgeIcon], so restyling the whole set is a change to this file.
 *
 * To move to illustrated artwork: drop the files in `commonMain/composeResources/drawable`
 * (one per [BadgeType]), then swap the `Icon` in [BadgeMedallion] for an `Image` reading a
 * `painterResource`. Full-colour artwork wants the tint and the coloured circle dropped too,
 * which is why callers pass a size and an earned flag rather than colours of their own.
 */

/**
 * The glyph for a badge. Exhaustive on purpose: a new [BadgeType] fails to compile here
 * instead of silently falling back to a generic trophy, which is how 29 badges ended up
 * sharing six icons.
 */
fun badgeIcon(type: BadgeType): ImageVector = when (type) {
    // Decisions
    BadgeType.DECISION_FIRST -> PhosphorIcons.Regular.Lightbulb
    BadgeType.DECISION_REVIEW_FIRST -> PhosphorIcons.Regular.ArrowCounterClockwise
    BadgeType.DECISION_REVIEW_10 -> PhosphorIcons.Regular.Brain
    BadgeType.DECISION_SOUND_10 -> PhosphorIcons.Regular.Scales
    BadgeType.DECISION_CALIBRATED -> PhosphorIcons.Regular.Crosshair

    // Goals
    BadgeType.FIRST_STEP -> PhosphorIcons.Regular.Sparkle
    BadgeType.GOAL_1 -> PhosphorIcons.Regular.Target
    BadgeType.GOAL_5 -> PhosphorIcons.Regular.Mountains
    BadgeType.GOAL_10 -> PhosphorIcons.Regular.Compass
    BadgeType.GOAL_25 -> PhosphorIcons.Regular.Crown
    BadgeType.GOAL_50 -> PhosphorIcons.Regular.Trophy

    // Streaks
    BadgeType.STREAK_3 -> PhosphorIcons.Regular.Fire
    BadgeType.STREAK_7 -> PhosphorIcons.Regular.Lightning
    BadgeType.STREAK_14 -> PhosphorIcons.Regular.Barbell
    BadgeType.STREAK_30 -> PhosphorIcons.Regular.Medal
    BadgeType.STREAK_100 -> PhosphorIcons.Regular.Shield

    // Habits
    BadgeType.HABIT_STARTER -> PhosphorIcons.Regular.Repeat
    BadgeType.HABIT_5 -> PhosphorIcons.Regular.Path
    BadgeType.HABIT_PERFECT_WEEK -> PhosphorIcons.Regular.CalendarCheck
    BadgeType.HABIT_PERFECT_MONTH -> PhosphorIcons.Regular.Diamond

    // Journal
    BadgeType.JOURNAL_FIRST -> PhosphorIcons.Regular.PenNib
    BadgeType.JOURNAL_10 -> PhosphorIcons.Regular.Notebook
    BadgeType.JOURNAL_30 -> PhosphorIcons.Regular.BookOpen

    // Categories
    BadgeType.BALANCED -> PhosphorIcons.Regular.Scales
    BadgeType.HEALTH_FOCUS -> PhosphorIcons.Regular.Heart
    BadgeType.CAREER_FOCUS -> PhosphorIcons.Regular.Briefcase

    // Onboarding
    BadgeType.GETTING_STARTED -> PhosphorIcons.Regular.Rocket

    // Special
    BadgeType.EARLY_BIRD -> PhosphorIcons.Regular.Sun
    BadgeType.NIGHT_OWL -> PhosphorIcons.Regular.MoonStars
    BadgeType.COMEBACK -> PhosphorIcons.Regular.ArrowCounterClockwise
    BadgeType.PERFECTIONIST -> PhosphorIcons.Regular.CheckCircle

    // Focus
    BadgeType.FOCUS_FIRST -> PhosphorIcons.Regular.Timer
    BadgeType.FOCUS_HOUR -> PhosphorIcons.Regular.ClockCountdown
    BadgeType.FOCUS_10 -> PhosphorIcons.Regular.Crosshair
    BadgeType.FOCUS_50 -> PhosphorIcons.Regular.Brain

    // Learning
    BadgeType.LEARN_HABITS -> PhosphorIcons.Regular.Books
    BadgeType.LEARN_MOTIVATION -> PhosphorIcons.Regular.Lightbulb
    BadgeType.LEARN_MIND -> PhosphorIcons.Regular.Student
    BadgeType.LEARN_REST -> PhosphorIcons.Regular.Moon
}

/**
 * A badge drawn as a filled circle, greyed out while it is still locked. [size] is the
 * diameter; the glyph is drawn at half of it, which is the ratio every screen already used.
 */
@Composable
fun BadgeMedallion(
    type: BadgeType,
    isEarned: Boolean,
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (isEarned) Color(type.color) else MaterialTheme.colorScheme.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = badgeIcon(type),
            contentDescription = type.displayName,
            modifier = Modifier.size(size / 2),
            tint = if (isEarned) Color.White else MaterialTheme.colorScheme.outline
        )
    }
}
