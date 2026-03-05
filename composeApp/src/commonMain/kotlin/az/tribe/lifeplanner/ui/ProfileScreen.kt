package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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

    var showAccountSheet by remember { mutableStateOf(false) }
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
                val syncStatus by syncManager.syncStatus.collectAsState()
                UserProfileHeaderCard(
                    user = currentUser,
                    userProgress = userProgress,
                    syncStatus = syncStatus,
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

            // Secure Account CTA — prominent banner for guests
            if (currentUser?.isGuest != false) {
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
                        title = "Sign In or Create Account",
                        subtitle = "Use email & password instead",
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

    if (showAccountSheet) {
        AccountCreationBottomSheet(
            authViewModel = authViewModel,
            onDismiss = { showAccountSheet = false },
            onNavigateToSignIn = {
                showAccountSheet = false
                onNavigateToSignIn()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountCreationBottomSheet(
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit,
    onNavigateToSignIn: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val authState by authViewModel.authState.collectAsState()
    val magicLinkSent by authViewModel.magicLinkSent.collectAsState()

    var email by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var showOtpInput by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = {
            authViewModel.clearMagicLinkState()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF6366F1), Color(0xFFA855F7))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (magicLinkSent) Icons.Rounded.MarkEmailRead else Icons.Rounded.Email,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                if (magicLinkSent) "Check Your Email" else "Sign in Instantly",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(4.dp))

            Text(
                if (magicLinkSent) "We sent a sign-in link to your email"
                else "No password needed — we'll send you a magic link",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            if (!magicLinkSent) {
                // Email input
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email address") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Email, contentDescription = null)
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        focusedLabelColor = Color(0xFF6366F1),
                        focusedLeadingIconColor = Color(0xFF6366F1),
                        cursorColor = Color(0xFF6366F1)
                    )
                )

                Spacer(Modifier.height(16.dp))

                // Send Magic Link — gradient-style button
                Button(
                    onClick = {
                        if (email.isNotBlank()) authViewModel.sendMagicLink(email)
                    },
                    enabled = email.isNotBlank() && authState !is AuthState.Loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6366F1)
                    )
                ) {
                    if (authState is AuthState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Send Magic Link",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                // Magic link sent state
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF6366F1).copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            email,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF6366F1)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Open the link in the email to sign in automatically, or enter the 6-digit code below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // OTP input
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically { it / 2 }
                ) {
                    Column {
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { if (it.length <= 6) otpCode = it },
                            label = { Text("6-digit code") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6366F1),
                                focusedLabelColor = Color(0xFF6366F1),
                                cursorColor = Color(0xFF6366F1)
                            )
                        )

                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (email.isNotBlank() && otpCode.length == 6) {
                                    authViewModel.verifyOtp(email, otpCode)
                                }
                            },
                            enabled = otpCode.length == 6 && authState !is AuthState.Loading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6366F1)
                            )
                        ) {
                            if (authState is AuthState.Loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    "Verify & Sign In",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                authViewModel.clearMagicLinkState()
                                otpCode = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Resend magic link",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Divider + password option
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    "or",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNavigateToSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    "Sign in with password",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            // Error message
            if (authState is AuthState.Error) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        (authState as AuthState.Error).message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
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
    onEditProfile: () -> Unit
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

                // Sync status pill — colors blend gradient tones with gray
                Spacer(Modifier.height(6.dp))
                val syncColor by animateColorAsState(
                    targetValue = when (syncStatus.state) {
                        SyncState.SYNCED -> Color(0xFF9BB0F0)   // soft blue-white (gradient blue + white)
                        SyncState.SYNCING -> Color(0xFFB89BDB)  // muted purple (gradient purple + gray)
                        SyncState.ERROR -> Color(0xFFD4A0B0)    // dusty rose (purple-gray + warm)
                        SyncState.OFFLINE -> Color(0xFF9A9AAE)  // blue-gray muted
                        SyncState.IDLE -> Color(0xFFB0B5D0)     // light blue-gray
                    },
                    animationSpec = tween(600)
                )
                val syncText = when (syncStatus.state) {
                    SyncState.SYNCED -> formatLastSynced(syncStatus.lastSyncedAt)
                    SyncState.SYNCING -> "Syncing..."
                    SyncState.ERROR -> "Sync failed"
                    SyncState.OFFLINE -> "Offline"
                    SyncState.IDLE -> if (syncStatus.pendingChanges > 0)
                        "${syncStatus.pendingChanges} pending" else "Connected"
                }
                Text(
                    syncText,
                    style = MaterialTheme.typography.labelSmall,
                    color = syncColor
                )
            }

            // Edit profile button disabled — onboarding no longer collects profile data
            IconButton(
                onClick = { /* no-op: onboarding redesigned */ },
                enabled = false,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f))
            ) {
                Icon(
                    Icons.Rounded.Edit,
                    "Edit Profile",
                    tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f)
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
