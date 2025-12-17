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
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.model.User
import az.tribe.lifeplanner.domain.model.UserProgress
import az.tribe.lifeplanner.ui.components.GlassCard
import az.tribe.lifeplanner.ui.components.GradientProgressBar
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
    onNavigateToReviews: () -> Unit,
    onNavigateToLifeBalance: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToAICoach: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToSignIn: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()
    val userProgress by gamificationViewModel.userProgress.collectAsState()
    val badges by gamificationViewModel.badges.collectAsState()

    val currentUser = when (authState) {
        is AuthState.Authenticated -> (authState as AuthState.Authenticated).user
        is AuthState.Guest -> (authState as AuthState.Guest).user
        else -> null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
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
                .padding(padding),
            contentPadding = PaddingValues(LifePlannerDesign.Padding.screenHorizontal),
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

            // Stats Overview (Level, XP, Streak)
            item {
                userProgress?.let { progress ->
                    ProfileStatsCard(progress)
                }
            }

            // Insights & Analytics Section
            item {
                ProfileSectionHeader("Insights & Analytics")
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Rounded.Assessment,
                    title = "Reviews & Insights",
                    subtitle = "Weekly, monthly, and quarterly reviews",
                    onClick = onNavigateToReviews
                )
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Rounded.Favorite,
                    title = "Life Balance",
                    subtitle = "Assess your life areas",
                    onClick = onNavigateToLifeBalance
                )
            }

            // Achievements Section
            item {
                ProfileSectionHeader("Achievements")
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Rounded.EmojiEvents,
                    title = "Badges & Achievements",
                    subtitle = "${badges.size} badges earned",
                    onClick = onNavigateToAchievements,
                    trailingContent = {
                        Row {
                            badges.take(3).forEach { badge ->
                                Text(
                                    badge.type.icon,
                                    modifier = Modifier.padding(2.dp),
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }
                )
            }

            // AI Features Section
            item {
                ProfileSectionHeader("AI Features")
            }

            item {
                ProfileMenuItem(
                    icon = Icons.Rounded.Psychology,
                    title = "AI Coach",
                    subtitle = "Get personalized guidance",
                    onClick = onNavigateToAICoach
                )
            }

            // Settings Section
            item {
                ProfileSectionHeader("Settings")
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

            // Account Section
            item {
                ProfileSectionHeader("Account")
            }

            item {
                if (currentUser?.isGuest == true) {
                    ProfileMenuItem(
                        icon = Icons.Rounded.Login,
                        title = "Sign In / Create Account",
                        subtitle = "Sync your data across devices",
                        onClick = onNavigateToSignIn
                    )
                } else {
                    ProfileMenuItem(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = "Sign Out",
                        subtitle = currentUser?.email ?: "",
                        onClick = { authViewModel.signOut() }
                    )
                }
            }

        }
    }
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
                            "${progress.xpForNextLevel} XP needed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val xpForCurrentLevel = progress.totalXp % 100
                    val progressPercent = xpForCurrentLevel / 100f
                    GradientProgressBar(
                        progress = progressPercent,
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
