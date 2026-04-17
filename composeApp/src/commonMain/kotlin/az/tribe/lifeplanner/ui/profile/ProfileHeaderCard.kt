package az.tribe.lifeplanner.ui.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.data.sync.SyncState
import az.tribe.lifeplanner.data.sync.SyncStatus
import az.tribe.lifeplanner.domain.model.User
import az.tribe.lifeplanner.domain.model.UserProgress
import az.tribe.lifeplanner.ui.components.GlassCard
import az.tribe.lifeplanner.ui.components.GradientProgressBar
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.theme.LifePlannerGradients
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowsClockwise
import com.adamglin.phosphoricons.regular.Brain
import com.adamglin.phosphoricons.regular.Cloud
import com.adamglin.phosphoricons.regular.CloudArrowUp
import com.adamglin.phosphoricons.regular.CloudCheck
import com.adamglin.phosphoricons.regular.CloudSlash
import com.adamglin.phosphoricons.regular.Flag
import com.adamglin.phosphoricons.regular.Note
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.Timer
import com.adamglin.phosphoricons.regular.User
import com.adamglin.phosphoricons.regular.WarningCircle

@Composable
internal fun UserProfileHeaderCard(
    user: User?,
    userProgress: UserProgress?,
    syncStatus: SyncStatus,
    onRetrySync: () -> Unit,
    onEditName: () -> Unit = {}
) {
    val gradientStart by animateColorAsState(
        targetValue = when (syncStatus.state) {
            SyncState.SYNCED, SyncState.IDLE -> Color(0xFF667EEA)
            SyncState.SYNCING -> Color(0xFF7B8ED0)
            SyncState.ERROR -> Color(0xFF8A7BA0)
            SyncState.OFFLINE -> Color(0xFF7E7E96)
        },
        animationSpec = tween(800)
    )
    val gradientEnd by animateColorAsState(
        targetValue = when (syncStatus.state) {
            SyncState.SYNCED, SyncState.IDLE -> Color(0xFF764BA2)
            SyncState.SYNCING -> Color(0xFF8B6DAF)
            SyncState.ERROR -> Color(0xFF8E6E82)
            SyncState.OFFLINE -> Color(0xFF6E6E82)
        },
        animationSpec = tween(800)
    )

    val isRetryable = syncStatus.state == SyncState.ERROR || syncStatus.state == SyncState.OFFLINE
    val syncIcon = when (syncStatus.state) {
        SyncState.SYNCING -> PhosphorIcons.Regular.CloudArrowUp
        SyncState.SYNCED -> PhosphorIcons.Regular.CloudCheck
        SyncState.OFFLINE -> PhosphorIcons.Regular.CloudSlash
        SyncState.ERROR -> PhosphorIcons.Regular.WarningCircle
        SyncState.IDLE -> if (syncStatus.pendingChanges > 0) PhosphorIcons.Regular.CloudArrowUp else PhosphorIcons.Regular.Cloud
    }
    val syncIconColor = when (syncStatus.state) {
        SyncState.SYNCING -> Color.White
        SyncState.SYNCED -> Color(0xFFA8E6CF)
        SyncState.OFFLINE -> Color(0xFF9A9AAE)
        SyncState.ERROR -> Color(0xFFFFB4A2)
        SyncState.IDLE -> if (syncStatus.pendingChanges > 0) Color(0xFFB89BDB) else Color.White.copy(alpha = 0.4f)
    }
    val pulseTransition = rememberInfiniteTransition()
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(800), repeatMode = RepeatMode.Reverse)
    )
    val syncIconAlpha = if (syncStatus.state == SyncState.SYNCING) pulseAlpha else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LifePlannerDesign.CornerRadius.large))
            .background(Brush.linearGradient(listOf(gradientStart, gradientEnd)))
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier.align(Alignment.TopEnd).clickable(enabled = isRetryable) { onRetrySync() }.padding(4.dp)
        ) {
            Icon(syncIcon, contentDescription = null, modifier = Modifier.size(20.dp).alpha(syncIconAlpha), tint = syncIconColor)
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(88.dp).background(Color.White.copy(alpha = 0.2f), CircleShape).padding(3.dp)) {
                Box(modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                    if (!user?.selectedSymbol.isNullOrEmpty()) {
                        Text(user?.selectedSymbol ?: "", fontSize = 40.sp)
                    } else {
                        Icon(PhosphorIcons.Regular.User, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(user?.displayName ?: "Guest", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                IconButton(onClick = onEditName, modifier = Modifier.size(28.dp)) {
                    Icon(PhosphorIcons.Regular.PencilSimple, contentDescription = "Edit name", modifier = Modifier.size(15.dp), tint = Color.White.copy(alpha = 0.6f))
                }
            }

            val isGuest = user?.email == null
            Text(
                text = user?.email ?: "Guest account",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = if (isGuest) 0.5f else 0.8f)
            )

            if (user?.email != null) {
                userProgress?.let { progress ->
                    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                        Text(
                            "Level ${progress.currentLevel} · ${progress.title}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

private val WEEK_COLOR_HABITS  = Color(0xFF4CAF50)
private val WEEK_COLOR_GOALS   = Color(0xFF6366F1)
private val WEEK_COLOR_JOURNAL = Color(0xFFFF9800)
private val WEEK_COLOR_FOCUS   = Color(0xFF6C63FF)
private val WEEK_COLOR_AI      = Color(0xFF26A69A)

@Composable
internal fun ProfileStatsCard(progress: UserProgress, engagement: WeeklyEngagement? = null) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = LifePlannerDesign.CornerRadius.large) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(LifePlannerGradients.primary))
            Column(modifier = Modifier.padding(LifePlannerDesign.Padding.standard)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ProfileModernStatItem(value = formatCompact(progress.totalXp), label = "Total XP", accentColor = MaterialTheme.colorScheme.primary)
                    ProfileModernStatItem(value = "Lv.${progress.currentLevel}", label = "Level", accentColor = MaterialTheme.colorScheme.secondary)
                    ProfileModernStatItem(value = "${progress.currentStreak}", label = "Streak", accentColor = MaterialTheme.colorScheme.tertiary)
                }
                Spacer(modifier = Modifier.height(20.dp))
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Progress to Level ${progress.currentLevel + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text("${progress.xpRemainingForNextLevel} XP left", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    GradientProgressBar(progress = progress.levelProgress, gradient = LifePlannerGradients.primary, modifier = Modifier.fillMaxWidth(), height = 10.dp)
                }

                if (engagement != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "This Week",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        WeeklyMiniStat(PhosphorIcons.Regular.ArrowsClockwise, WEEK_COLOR_HABITS, engagement.habitCheckIns, "Habits")
                        WeeklyMiniStat(PhosphorIcons.Regular.Flag,            WEEK_COLOR_GOALS,   engagement.goalsCreated,          "Goals")
                        WeeklyMiniStat(PhosphorIcons.Regular.Note,            WEEK_COLOR_JOURNAL, engagement.journalEntries,        "Journal")
                        WeeklyMiniStat(PhosphorIcons.Regular.Timer,           WEEK_COLOR_FOCUS,   engagement.focusSessionsCompleted,"Focus")
                        WeeklyMiniStat(PhosphorIcons.Regular.Brain,           WEEK_COLOR_AI,      engagement.aiCoachMessages,       "AI")
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyMiniStat(icon: ImageVector, color: Color, value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ProfileModernStatItem(value: String, label: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = accentColor)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
