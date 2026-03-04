package az.tribe.lifeplanner.ui

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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import az.tribe.lifeplanner.ui.components.SyncStatusIndicator
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.domain.enum.AiProvider
import az.tribe.lifeplanner.domain.model.AiUsageStats
import az.tribe.lifeplanner.domain.repository.AiUsageRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
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
    onNavigateToReviewChat: () -> Unit,
    onNavigateToLifeBalance: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToRetrospective: () -> Unit = {},
    onNavigateToAICoach: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToSignIn: () -> Unit
) {
    val syncManager: SyncManager = koinInject()
    val settings: Settings = koinInject()
    val authState by authViewModel.authState.collectAsState()
    val isLocalOnlyGuest by authViewModel.isLocalOnlyGuest.collectAsState()
    val userProgress by gamificationViewModel.userProgress.collectAsState()
    val badges by gamificationViewModel.badges.collectAsState()

    var showAiProviderDialog by remember { mutableStateOf(false) }
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        "Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    SyncStatusIndicator(
                        syncStatus = syncManager.syncStatus,
                        onRetryClick = { syncManager.requestSync() },
                        compact = false
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues = PaddingValues(top = padding.calculateTopPadding())),
            contentPadding = PaddingValues(
                start = LifePlannerDesign.Padding.screenHorizontal,
                end = LifePlannerDesign.Padding.screenHorizontal,
                bottom = padding.calculateBottomPadding()+96.dp,
                top = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.md)
        ) {
            // User Profile Header Card
            item {
                UserProfileHeaderCard(
                    user = currentUser,
                    userProgress = userProgress,
                    onEditProfile = onNavigateToOnboarding
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
                    icon = Icons.Rounded.Assessment,
                    title = "Reviews",
                    subtitle = "Weekly and monthly AI reviews",
                    onClick = onNavigateToReviewChat
                )
            }

            // Account Section
            item {
                ProfileSectionHeader("Account")
            }

            item {
                if (currentUser?.isGuest != false) {
                    ProfileMenuItem(
                        icon = Icons.AutoMirrored.Rounded.Login,
                        title = "Create Account",
                        subtitle = "Keep your data and sync across devices",
                        onClick = onNavigateToSignIn
                    )
                } else {
                    ProfileMenuItem(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = "Sign Out",
                        subtitle = currentUser.email ?: "",
                        onClick = { authViewModel.signOut() }
                    )
                }
            }

        }
    }

    if (showAiProviderDialog) {
        AiProviderDialog(
            currentProvider = selectedAiProvider,
            isGuest = currentUser?.isGuest != false,
            onProviderSelected = { provider ->
                selectedAiProvider = provider
                settings.putString("ai_provider", provider.name)
                showAiProviderDialog = false
            },
            onDismiss = { showAiProviderDialog = false }
        )
    }
}

@Composable
private fun AiProviderDialog(
    currentProvider: AiProvider,
    isGuest: Boolean,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProviderSelected(provider) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = provider == currentProvider,
                            onClick = { onProviderSelected(provider) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                provider.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                provider.modelInfo,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    tokens >= 1_000_000 -> String.format("%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format("%.1fK", tokens / 1_000.0)
    else -> "$tokens"
}

private fun formatCost(cost: Double): String = when {
    cost < 0.005 -> if (cost == 0.0) "$0.00" else "<$0.01"
    cost < 1.0 -> String.format("$%.2f", cost)
    else -> String.format("$%.2f", cost)
}

@Composable
private fun UserProfileHeaderCard(
    user: User?,
    userProgress: UserProgress?,
    onEditProfile: () -> Unit
) {
    // Modern gradient hero header
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LifePlannerDesign.CornerRadius.large))
            .background(LifePlannerGradients.primary)
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
                            user!!.selectedSymbol!!,
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
                Text(
                    user?.displayName ?: "Guest User",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White
                )

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

            IconButton(
                onClick = onEditProfile,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f))
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    "Edit Profile",
                    tint = androidx.compose.ui.graphics.Color.White
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
                        value = "${progress.totalXp}",
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
