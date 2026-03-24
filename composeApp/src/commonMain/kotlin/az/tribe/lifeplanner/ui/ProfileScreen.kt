package az.tribe.lifeplanner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import az.tribe.lifeplanner.domain.model.User
import az.tribe.lifeplanner.domain.model.UserProgress
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.ui.components.AchievementsCard
import az.tribe.lifeplanner.ui.components.GlassCard
import az.tribe.lifeplanner.ui.components.GradientProgressBar
import az.tribe.lifeplanner.ui.components.PersonalCoachCard
import az.tribe.lifeplanner.ui.components.getBadgeIcon
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.data.sync.SyncState
import az.tribe.lifeplanner.data.sync.SyncStatus
import az.tribe.lifeplanner.domain.enum.AiProvider
import az.tribe.lifeplanner.domain.model.AiUsageStats
import az.tribe.lifeplanner.domain.repository.AiUsageRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.theme.LifePlannerGradients
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel = koinInject(),
    gamificationViewModel: GamificationViewModel = koinViewModel(),
    onNavigateToAchievements: () -> Unit,
    onNavigateToLifeBalance: () -> Unit,
    onNavigateToHealth: () -> Unit = {},
    onNavigateToReminders: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToRetrospective: () -> Unit = {},
    onNavigateToAICoach: () -> Unit,
    onNavigateToSignIn: () -> Unit = {},
    onNavigateToFeedback: () -> Unit = {}
) {
    val syncManager: SyncManager = koinInject()
    val settings: Settings = koinInject()
    val authState by authViewModel.authState.collectAsState()
    val isLocalOnlyGuest by authViewModel.isLocalOnlyGuest.collectAsState()
    val userProgress by gamificationViewModel.userProgress.collectAsState()
    val badges by gamificationViewModel.badges.collectAsState()

    var showAccountSheet by remember { mutableStateOf(false) }
    var showAiProviderDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var selectedAiProvider by remember {
        val saved = settings.getStringOrNull("ai_provider")
        mutableStateOf(saved?.let {
            try { AiProvider.valueOf(it) } catch (_: Exception) { AiProvider.GEMINI }
        } ?: AiProvider.GEMINI)
    }

    val currentUser = when (authState) {
        is AuthState.Authenticated -> (authState as AuthState.Authenticated).user
        is AuthState.Guest -> (authState as AuthState.Guest).user
        else -> null
    }

    // Refresh gamification data when screen is shown
    LaunchedEffect(Unit) {
        gamificationViewModel.refresh()
    }

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = PaddingValues(top = padding.calculateTopPadding())),
            contentPadding = PaddingValues(
                start = LifePlannerDesign.Padding.screenHorizontal,
                end = LifePlannerDesign.Padding.screenHorizontal,
                bottom = padding.calculateBottomPadding()+112.dp,
                top = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.md)
        ) {
            // User Profile Header Card
            item {
                val syncStatus by syncManager.syncStatus.collectAsState()
                val scope = rememberCoroutineScope()
                UserProfileHeaderCard(
                    user = currentUser,
                    userProgress = userProgress,
                    syncStatus = syncStatus,
                    onRetrySync = { scope.launch { syncManager.performFullSync(resetRetry = true) } },
                    onEditName = { showEditNameDialog = true }
                )
            }

            // Offline mode warning banner
            if (isLocalOnlyGuest) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.small)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(LifePlannerDesign.Padding.standard),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Offline Mode",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    "Your data is stored locally only. Sign in to sync across devices.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Secure Account CTA — show for anyone without a verified email account
            if (currentUser?.email == null) {
                item {
                    SecureAccountCTABanner(onClick = { showAccountSheet = true })
                }
            }

            // Stats Overview (Level, XP, Streak)
            item {
                userProgress?.let { progress ->
                    ProfileStatsCard(progress)
                }
            }

            // AI Coach & Achievements
            item {
                ProfileSectionHeader("AI Coach & Achievements")
            }

            item {
                PersonalCoachCard(
                    lastMessage = null,
                    onChatClick = onNavigateToAICoach
                )
            }

            item {
                AchievementsCard(
                    earnedBadges = badges.size,
                    totalBadges = BadgeType.entries.size,
                    recentBadges = badges.take(3).map { it.type },
                    onSeeAllClick = onNavigateToAchievements
                )
            }

            // Insights & Analytics Section
            item {
                ProfileSectionHeader("Insights & Analytics")
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Rounded.Favorite,
                    title = "Life Balance",
                    subtitle = "Assess your life areas",
                    onClick = onNavigateToLifeBalance
                )
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Rounded.Favorite,
                    title = "Health",
                    subtitle = "Steps, heart rate, sleep & weight",
                    onClick = onNavigateToHealth
                )
            }

            // Settings Section
            item {
                ProfileSectionHeader("Settings")
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Rounded.Psychology,
                    title = "AI Provider",
                    subtitle = selectedAiProvider.displayName,
                    onClick = { showAiProviderDialog = true }
                )
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Rounded.Notifications,
                    title = "Reminders",
                    subtitle = "Notification preferences",
                    onClick = onNavigateToReminders
                )
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Rounded.CloudUpload,
                    title = "Backup & Sync",
                    subtitle = "Export and restore your data",
                    onClick = onNavigateToBackup
                )
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Rounded.History,
                    title = "Day Retrospective",
                    subtitle = "Browse past days and activity",
                    onClick = onNavigateToRetrospective
                )
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Rounded.Feedback,
                    title = "Send Feedback",
                    subtitle = "Report bugs, request features",
                    onClick = onNavigateToFeedback
                )
            }

            // Account Section — only for signed-in users
            if (authState is AuthState.Authenticated && currentUser?.email != null) {
                item {
                    ProfileSectionHeader("Account")
                }

                item {
                    ProfileMenuItem(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = "Sign Out",
                        subtitle = currentUser?.email ?: "",
                        onClick = { showSignOutConfirm = true }
                    )
                }
            }

        }
    }

    if (showAiProviderDialog) {
        AiProviderDialog(
            currentProvider = selectedAiProvider,
            isGuest = currentUser?.isGuest != false,
            userLevel = userProgress?.currentLevel ?: 1,
            onProviderSelected = { provider ->
                selectedAiProvider = provider
                settings.putString("ai_provider", provider.name)
                showAiProviderDialog = false
            },
            onDismiss = { showAiProviderDialog = false }
        )
    }

    if (showAccountSheet) {
        // Guest users should see sign-up (link account) flow, not sign-in
        val isGuest = currentUser?.isGuest == true
        AuthBottomSheet(
            isSignUp = isGuest,
            authViewModel = authViewModel,
            authState = authState,
            onDismiss = { showAccountSheet = false },
            onSuccess = { showAccountSheet = false }
        )
    }

    if (showSignOutConfirm) {
        val syncStatus by syncManager.syncStatus.collectAsState()
        val isSynced = syncStatus.state == SyncState.SYNCED
        val isOfflineOrError = syncStatus.state == SyncState.OFFLINE || syncStatus.state == SyncState.ERROR

        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = {
                Text(
                    if (isOfflineOrError) "Unsynced Changes" else "Ready to leave?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (isOfflineOrError) {
                    Column {
                        Text(
                            "Some of your recent changes haven't been saved to the cloud yet. Signing out now could result in losing them.",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Try connecting to the internet and waiting for sync to finish first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text("Everything is synced. You can sign back in anytime to pick up where you left off.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutConfirm = false
                        authViewModel.signOut()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (isOfflineOrError) "Leave Anyway" else "Yes, Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(if (isOfflineOrError) "Wait for Sync" else "Stay")
                }
            }
        )
    }

    if (showEditNameDialog) {
        var editedName by remember { mutableStateOf(currentUser?.displayName ?: "") }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Display Name", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    supportingText = if (editedName.trim().length < 2) {
                        { Text("At least 2 characters", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    isError = editedName.trim().length < 2 && editedName.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        authViewModel.updateDisplayName(editedName)
                        showEditNameDialog = false
                    },
                    enabled = editedName.trim().length >= 2
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

}

@Composable
internal fun SecureAccountCTABanner(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LifePlannerDesign.CornerRadius.large))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFFA855F7))
                )
            )
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon cluster
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Secure Your Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "One tap sign-in with magic link",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // Arrow
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = "Get started",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Feature pills row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CTAFeaturePill(icon = Icons.Rounded.Sync, label = "Auto-sync")
            CTAFeaturePill(icon = Icons.Rounded.Devices, label = "Multi-device")
            CTAFeaturePill(icon = Icons.Rounded.Lock, label = "Encrypted")
        }
    }
}

@Composable
internal fun CTAFeaturePill(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun AiProviderDialog(
    currentProvider: AiProvider,
    isGuest: Boolean,
    userLevel: Int = 1,
    onProviderSelected: (AiProvider) -> Unit,
    onDismiss: () -> Unit
) {
    val aiUsageRepository: AiUsageRepository = koinInject()
    val scope = rememberCoroutineScope()
    var usageStats by remember { mutableStateOf<AiUsageStats?>(null) }
    var isLoadingStats by remember { mutableStateOf(!isGuest) }

    LaunchedEffect(Unit) {
        if (!isGuest) {
            scope.launch {
                usageStats = aiUsageRepository.getMonthlyStats()
                isLoadingStats = false
            }
        }
    }

    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val monthName = now.month.name.lowercase().replaceFirstChar { it.uppercase() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Provider") },
        text = {
            Column {
                // Usage stats section
                if (isGuest) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Sign in to track AI usage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else if (isLoadingStats) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Loading usage stats...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    usageStats?.let { stats ->
                        Text(
                            "$monthName ${now.year} Usage",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))

                        // Top-level stats row
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                UsageStatItem(
                                    value = "${stats.totalRequests}",
                                    label = "Requests"
                                )
                                UsageStatItem(
                                    value = formatTokenCount(stats.totalTokens),
                                    label = "Tokens"
                                )
                                UsageStatItem(
                                    value = formatCost(stats.estimatedCostUsd),
                                    label = "Cost"
                                )
                            }
                        }

                        // Per-provider breakdown
                        if (stats.byProvider.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            stats.byProvider.forEach { summary ->
                                val providerName = AiProvider.fromProviderName(summary.provider)
                                    ?.displayName ?: summary.provider
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        providerName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${summary.requestCount} req  ${formatCost(summary.estimatedCostUsd)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text(
                    "Choose provider:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))

                AiProvider.entries.forEach { provider ->
                    val isUnlocked = provider.isUnlocked(userLevel)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isUnlocked) Modifier.clickable { onProviderSelected(provider) }
                                else Modifier
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = provider == currentProvider,
                            onClick = { if (isUnlocked) onProviderSelected(provider) },
                            enabled = isUnlocked
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    provider.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isUnlocked) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                if (!isUnlocked) {
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            "Lv. ${provider.requiredLevel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                if (isUnlocked) provider.modelInfo
                                else "Reach level ${provider.requiredLevel} to unlock",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isUnlocked) MaterialTheme.colorScheme.onSurfaceVariant
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun UsageStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTokenCount(tokens: Long): String = when {
    tokens >= 1_000_000 -> {
        val value = tokens / 1_000_000.0
        val rounded = (value * 10).toLong() / 10.0
        "${rounded}M"
    }
    tokens >= 1_000 -> {
        val value = tokens / 1_000.0
        val rounded = (value * 10).toLong() / 10.0
        "${rounded}K"
    }
    else -> "$tokens"
}

private fun formatCost(cost: Double): String = when {
    cost < 0.005 -> if (cost == 0.0) "$0.00" else "<$0.01"
    else -> {
        val cents = (cost * 100).toLong()
        val dollars = cents / 100
        val remainder = cents % 100
        "$${dollars}.${remainder.toString().padStart(2, '0')}"
    }
}

@Composable
private fun UserProfileHeaderCard(
    user: User?,
    userProgress: UserProgress?,
    syncStatus: SyncStatus,
    onRetrySync: () -> Unit,
    onEditName: () -> Unit = {}
) {
    // Gradient colors shift based on sync state
    val gradientStart by animateColorAsState(
        targetValue = when (syncStatus.state) {
            SyncState.SYNCED -> Color(0xFF667EEA)   // original soft blue
            SyncState.SYNCING -> Color(0xFF7B8ED0)  // slightly muted blue
            SyncState.ERROR -> Color(0xFF8A7BA0)    // purple-gray warm
            SyncState.OFFLINE -> Color(0xFF7E7E96)  // desaturated blue-gray
            SyncState.IDLE -> Color(0xFF667EEA)     // original
        },
        animationSpec = tween(800)
    )
    val gradientEnd by animateColorAsState(
        targetValue = when (syncStatus.state) {
            SyncState.SYNCED -> Color(0xFF764BA2)   // original rich purple
            SyncState.SYNCING -> Color(0xFF8B6DAF)  // slightly muted purple
            SyncState.ERROR -> Color(0xFF8E6E82)    // dusty rose-purple
            SyncState.OFFLINE -> Color(0xFF6E6E82)  // desaturated gray-purple
            SyncState.IDLE -> Color(0xFF764BA2)     // original
        },
        animationSpec = tween(800)
    )
    val cardGradient = Brush.linearGradient(listOf(gradientStart, gradientEnd))

    // Modern gradient hero header
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LifePlannerDesign.CornerRadius.large))
            .background(cardGradient)
            .padding(LifePlannerDesign.Padding.large)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with gradient ring
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f),
                                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (!user?.selectedSymbol.isNullOrEmpty()) {
                        Text(
                            user?.selectedSymbol ?: "",
                            fontSize = 36.sp
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        user?.displayName ?: "Guest User",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onEditName,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = "Edit display name",
                            modifier = Modifier.size(16.dp),
                            tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                user?.email?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                    )
                }

                userProgress?.let { progress ->
                    Spacer(Modifier.height(8.dp))
                    // Level badge with glass effect
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            "Level ${progress.currentLevel} • ${progress.title}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

            }

            // Sync status cloud icon column
            val isRetryable = syncStatus.state == SyncState.ERROR || syncStatus.state == SyncState.OFFLINE
            val syncIcon = when (syncStatus.state) {
                SyncState.SYNCING -> Icons.Rounded.CloudSync
                SyncState.SYNCED -> Icons.Rounded.CloudDone
                SyncState.OFFLINE -> Icons.Rounded.CloudOff
                SyncState.ERROR -> Icons.Rounded.ErrorOutline
                SyncState.IDLE -> if (syncStatus.pendingChanges > 0)
                    Icons.Rounded.CloudUpload else Icons.Rounded.Cloud
            }
            val syncDesc = when (syncStatus.state) {
                SyncState.SYNCING -> "Syncing with cloud"
                SyncState.SYNCED -> "All data synced"
                SyncState.OFFLINE -> "No internet connection"
                SyncState.ERROR -> "Sync failed — tap to retry"
                SyncState.IDLE -> if (syncStatus.pendingChanges > 0)
                    "${syncStatus.pendingChanges} changes waiting to sync" else "Connected"
            }
            val iconColor = when (syncStatus.state) {
                SyncState.SYNCING -> Color.White
                SyncState.SYNCED -> Color(0xFFA8E6CF)
                SyncState.OFFLINE -> Color(0xFF9A9AAE)
                SyncState.ERROR -> Color(0xFFFFB4A2)
                SyncState.IDLE -> if (syncStatus.pendingChanges > 0)
                    Color(0xFFB89BDB) else Color.White.copy(alpha = 0.4f)
            }
            // Pulsing alpha for SYNCING state
            val pulseTransition = rememberInfiniteTransition()
            val pulseAlpha by pulseTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800),
                    repeatMode = RepeatMode.Reverse
                )
            )
            val iconAlpha = if (syncStatus.state == SyncState.SYNCING) pulseAlpha else 1f

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(enabled = isRetryable) { onRetrySync() }
                    .padding(4.dp)
            ) {
                Icon(
                    syncIcon,
                    contentDescription = syncDesc,
                    modifier = Modifier.size(24.dp).alpha(iconAlpha),
                    tint = iconColor
                )
            }
        }
    }
}

@Composable
private fun ProfileStatsCard(progress: UserProgress) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = LifePlannerDesign.CornerRadius.large
    ) {
        Column {
            // Gradient accent bar at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(LifePlannerGradients.primary)
            )

            Column(
                modifier = Modifier.padding(LifePlannerDesign.Padding.standard)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ModernStatItem(
                        value = formatCompact(progress.totalXp),
                        label = "Total XP",
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    ModernStatItem(
                        value = "Lv.${progress.currentLevel}",
                        label = "Level",
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                    ModernStatItem(
                        value = "${progress.currentStreak}",
                        label = "Day Streak",
                        accentColor = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // XP Progress to next level with gradient
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Progress to Level ${progress.currentLevel + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${progress.xpRemainingForNextLevel} XP remaining",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    GradientProgressBar(
                        progress = progress.levelProgress,
                        gradient = LifePlannerGradients.primary,
                        modifier = Modifier.fillMaxWidth(),
                        height = 10.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernStatItem(
    value: String,
    label: String,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatItem(
    value: String,
    label: String,
    icon: ImageVector?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProfileSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.small)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LifePlannerDesign.Padding.standard),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(LifePlannerDesign.CornerRadius.small))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = LifePlannerDesign.Alpha.overlay)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            trailingContent?.invoke() ?: Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatCompact(value: Int): String = when {
    value >= 1_000_000 -> {
        val m = value / 1_000_000.0
        val rounded = (m * 10).toLong() / 10.0
        if (rounded == rounded.toLong().toDouble()) "${rounded.toLong()}M" else "${rounded}M"
    }
    value >= 1_000 -> {
        val k = value / 1_000.0
        if (k == k.toLong().toDouble()) "${k.toLong()}k" else {
            val rounded = (k * 10).toLong() / 10.0
            "${rounded}k"
        }
    }
    else -> "$value"
}

private fun formatLastSynced(instant: Instant?): String {
    if (instant == null) return "Synced"
    val now = Clock.System.now()
    val diff = now - instant
    val seconds = diff.inWholeSeconds
    return when {
        seconds < 60 -> "Synced just now"
        seconds < 3600 -> "Synced ${seconds / 60}m ago"
        seconds < 86400 -> "Synced ${seconds / 3600}h ago"
        else -> "Synced ${seconds / 86400}d ago"
    }
}

private fun friendlyErrorMessage(raw: String?): String {
    if (raw == null) return "Sync failed"
    val lower = raw.lowercase()
    return when {
        "partial sync" in lower -> "Some data didn't sync"
        "timeout" in lower -> "Server took too long"
        "unauthorized" in lower || "401" in lower -> "Session expired — sign in again"
        "forbidden" in lower || "403" in lower -> "Permission denied"
        "not found" in lower || "404" in lower -> "Server not reachable"
        "500" in lower || "internal server" in lower -> "Server error"
        "socket" in lower || "connect" in lower || "network" in lower || "resolve" in lower ->
            "Connection problem"
        else -> "Sync failed"
    }
}
