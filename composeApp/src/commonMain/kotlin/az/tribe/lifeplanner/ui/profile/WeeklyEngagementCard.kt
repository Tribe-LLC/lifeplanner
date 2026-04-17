package az.tribe.lifeplanner.ui.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.ui.components.GlassCard
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.theme.LifePlannerGradients
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.Brain
import com.adamglin.phosphoricons.regular.Flag
import com.adamglin.phosphoricons.regular.Note
import com.adamglin.phosphoricons.regular.Rocket
import com.adamglin.phosphoricons.regular.Timer

private val COLOR_HABITS  = Color(0xFF4CAF50)
private val COLOR_GOALS   = Color(0xFF6366F1)
private val COLOR_JOURNAL = Color(0xFFFF9800)
private val COLOR_FOCUS   = Color(0xFF6C63FF)
private val COLOR_AI      = Color(0xFF26A69A)

@Composable
internal fun WeeklyEngagementCard(
    engagement: WeeklyEngagement,
    modifier: Modifier = Modifier
) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(engagement) {
        alpha.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
    }

    val isEmpty = engagement.habitCheckIns == 0 &&
            engagement.goalsCreated == 0 &&
            engagement.journalEntries == 0 &&
            engagement.focusSessionsCompleted == 0 &&
            engagement.aiCoachMessages == 0

    GlassCard(
        modifier = modifier.fillMaxWidth().alpha(alpha.value),
        cornerRadius = LifePlannerDesign.CornerRadius.large
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(LifePlannerGradients.primary)
            )
            Column(modifier = Modifier.padding(LifePlannerDesign.Padding.standard)) {
                Text(
                    "This Week",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                if (isEmpty) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(COLOR_GOALS.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = PhosphorIcons.Regular.Rocket,
                                contentDescription = null,
                                tint = COLOR_GOALS,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Start your week strong!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Check in on habits, write in your journal, or set a new goal — your weekly stats will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Row 1: habits · goals · journal
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        EngagementStat(
                            icon = PhosphorIcons.Regular.ArrowsClockwise,
                            color = COLOR_HABITS,
                            value = engagement.habitCheckIns,
                            label = "Check-ins"
                        )
                        EngagementStat(
                            icon = PhosphorIcons.Regular.Flag,
                            color = COLOR_GOALS,
                            value = engagement.goalsCreated,
                            label = "Goals"
                        )
                        EngagementStat(
                            icon = PhosphorIcons.Regular.Note,
                            color = COLOR_JOURNAL,
                            value = engagement.journalEntries,
                            label = "Journal"
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Row 2: focus · ai
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        EngagementStat(
                            icon = PhosphorIcons.Regular.Timer,
                            color = COLOR_FOCUS,
                            value = engagement.focusSessionsCompleted,
                            label = "Focus sessions"
                        )
                        EngagementStat(
                            icon = PhosphorIcons.Regular.Brain,
                            color = COLOR_AI,
                            value = engagement.aiCoachMessages,
                            label = "AI messages"
                        )
                        // Spacer to balance the row visually
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EngagementStat(
    icon: ImageVector,
    color: Color,
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
