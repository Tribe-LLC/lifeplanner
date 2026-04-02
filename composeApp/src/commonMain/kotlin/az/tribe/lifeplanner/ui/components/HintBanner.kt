package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import az.tribe.lifeplanner.util.FeatureFlags
import com.adamglin.phosphoricons.regular.Lightbulb
import com.adamglin.phosphoricons.regular.X

@Composable
fun HintBanner(
    title: String,
    message: String,
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!FeatureFlags.HINTS_ENABLED) return

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = PhosphorIcons.Regular.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Regular.X,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// Centralized hint content for all screens
data class ScreenHint(val key: String, val title: String, val message: String)

object ScreenHints {
    val HOME = ScreenHint(
        key = "home",
        title = "Welcome to your Dashboard",
        message = "Here you'll find your daily focus, active goals, and recent progress at a glance."
    )
    val GOALS = ScreenHint(
        key = "goals",
        title = "Your Goals Hub",
        message = "Create goals by category, track progress, and use AI to generate smart milestones. Tap + to get started."
    )
    val JOURNAL = ScreenHint(
        key = "journal",
        title = "Daily Journaling",
        message = "Reflect on your day with guided prompts or free writing. Your entries stay private and offline-first."
    )
    val HABITS = ScreenHint(
        key = "habits",
        title = "Build Habits",
        message = "Check off daily habits and build streaks. Consistency earns you XP and badges."
    )
    val FOCUS = ScreenHint(
        key = "focus",
        title = "Focus Timer",
        message = "Start a Pomodoro session with ambient sounds to stay in deep work mode and earn XP."
    )
    val LIFE_BALANCE = ScreenHint(
        key = "life_balance",
        title = "Life Balance Wheel",
        message = "Rate each life area to see where you're thriving and where to invest more energy."
    )
    val ANALYTICS = ScreenHint(
        key = "analytics",
        title = "Your Progress Analytics",
        message = "See trends across goals, habits, and journal entries over time."
    )
    val AI_CHAT = ScreenHint(
        key = "ai_chat",
        title = "AI Coach",
        message = "Chat with your AI coach for advice, motivation, and goal breakdowns. Powered by your chosen AI provider."
    )
    val ACHIEVEMENTS = ScreenHint(
        key = "achievements",
        title = "Achievements & Badges",
        message = "Earn badges by completing goals and maintaining streaks. Collect them all!"
    )
    val RETROSPECTIVE = ScreenHint(
        key = "retrospective",
        title = "Weekly Retrospective",
        message = "Review your week: what went well, what didn't, and set intentions for next week."
    )
}
