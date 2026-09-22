package az.tribe.lifeplanner.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.BuildKonfig
import az.tribe.lifeplanner.data.sync.SyncState
import az.tribe.lifeplanner.domain.enum.AiProvider
import az.tribe.lifeplanner.domain.model.sourceLabel
import az.tribe.lifeplanner.ui.calendar.CalendarPermissionState
import az.tribe.lifeplanner.ui.calendar.CalendarViewModel
import az.tribe.lifeplanner.ui.calendar.rememberCalendarPermission
import az.tribe.lifeplanner.ui.gamification.GamificationViewModel
import az.tribe.lifeplanner.ui.health.HealthPermissionState
import az.tribe.lifeplanner.ui.health.HealthViewModel
import az.tribe.lifeplanner.ui.profile.AiProviderDialog
import az.tribe.lifeplanner.ui.profile.CalendarIntegrationCard
import az.tribe.lifeplanner.ui.profile.HealthConnectionCard
import az.tribe.lifeplanner.ui.profile.ProfileMenuItem
import az.tribe.lifeplanner.ui.profile.ProfileSectionHeader
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.theme.ThemeController
import az.tribe.lifeplanner.ui.theme.ThemeMode
import az.tribe.lifeplanner.ui.viewmodel.AuthState
import az.tribe.lifeplanner.ui.viewmodel.AuthViewModel
import az.tribe.lifeplanner.ui.viewmodel.signOut
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.Bell
import com.adamglin.phosphoricons.regular.Brain
import com.adamglin.phosphoricons.regular.ChatCircleText
import com.adamglin.phosphoricons.regular.CloudArrowUp
import com.adamglin.phosphoricons.regular.SignOut
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Everything that configures the app rather than describes the user: providers, integrations,
 * appearance, notifications, data, and the account itself. Pulled off the You tab so that page can
 * be about you and only you, instead of a settings list wearing a profile's clothes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToFeedback: () -> Unit,
    onNavigateToHealth: () -> Unit,
    onNavigateToCalendarSettings: () -> Unit,
    onResetOnboarding: () -> Unit,
    authViewModel: AuthViewModel = koinInject(),
    gamificationViewModel: GamificationViewModel = koinViewModel(),
    healthViewModel: HealthViewModel = koinViewModel(),
    calendarViewModel: CalendarViewModel = koinViewModel(),
) {
    val settings: Settings = koinInject()
    val themeController: ThemeController = koinInject()
    val themeMode by themeController.mode.collectAsState()
    val authState by authViewModel.authState.collectAsState()
    val syncStatus by authViewModel.syncStatus.collectAsState()
    val userProgress by gamificationViewModel.userProgress.collectAsState()
    val healthPermissionState by healthViewModel.permissionState.collectAsState()
    val calendarPermission = rememberCalendarPermission()
    val deviceCalendars by calendarViewModel.calendars.collectAsState()

    var showAiProviderDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedAiProvider by remember {
        val saved = settings.getStringOrNull("ai_provider")
        mutableStateOf(
            saved?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() } ?: AiProvider.GEMINI
        )
    }

    val currentUser = when (authState) {
        is AuthState.Authenticated -> (authState as AuthState.Authenticated).user
        is AuthState.Guest -> (authState as AuthState.Guest).user
        else -> null
    }

    // Once connected, the Integrations row should say *what* is connected, not just "Connected".
    LaunchedEffect(calendarPermission.state) {
        if (calendarPermission.state == CalendarPermissionState.GRANTED) {
            calendarViewModel.loadCalendars()
        }
    }
    val calendarSummary = remember(deviceCalendars) {
        if (deviceCalendars.isEmpty()) null else {
            val enabled = deviceCalendars.count { it.enabled }
            val accounts = deviceCalendars.filter { it.enabled }.map { it.calendar.sourceLabel }.distinct()
            val account = when {
                accounts.size == 1 -> accounts.first()
                accounts.size > 1 -> "${accounts.size} accounts"
                else -> null
            }
            listOfNotNull("$enabled of ${deviceCalendars.size} calendars", account).joinToString(" • ")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(PhosphorIcons.Regular.ArrowLeft, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LifePlannerDesign.Padding.screenHorizontal,
                end = LifePlannerDesign.Padding.screenHorizontal,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 84.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.md),
        ) {
            item { ProfileSectionHeader("Appearance") }
            item { AppearanceToggle(current = themeMode, onSelect = { themeController.setMode(it) }) }

            item { ProfileSectionHeader("Integrations") }
            item {
                ProfileMenuItem(
                    icon = PhosphorIcons.Regular.Brain,
                    title = "AI Provider",
                    subtitle = selectedAiProvider.displayName,
                    onClick = { showAiProviderDialog = true },
                )
            }
            // This card used to hide itself once health was connected, on the reasoning that the
            // dashboard owned health from then on and the card would be a duplicate entry point.
            // It was not a duplicate, it was the only one: nothing on Home links to the dashboard,
            // so connecting health made the dashboard reachable only by searching for "health".
            // It stays, and when connected it leads there.
            item {
                HealthConnectionCard(
                    permissionState = healthPermissionState,
                    onConnect = onNavigateToHealth,
                    onSync = { healthViewModel.syncHealth() },
                    onOpenDashboard = onNavigateToHealth,
                )
            }
            item {
                CalendarIntegrationCard(
                    permissionState = calendarPermission.state,
                    calendarSummary = calendarSummary,
                    onConnect = calendarPermission.request,
                    onManage = onNavigateToCalendarSettings,
                )
            }

            item { ProfileSectionHeader("Notifications") }
            item {
                ProfileMenuItem(
                    icon = PhosphorIcons.Regular.Bell,
                    title = "Reminders",
                    subtitle = "Notification preferences",
                    onClick = onNavigateToReminders,
                )
            }

            item { ProfileSectionHeader("Data") }
            item {
                ProfileMenuItem(
                    icon = PhosphorIcons.Regular.CloudArrowUp,
                    title = "Backup & Sync",
                    subtitle = "Export and restore your data",
                    onClick = onNavigateToBackup,
                )
            }

            item { ProfileSectionHeader("About") }
            item {
                ProfileMenuItem(
                    icon = PhosphorIcons.Regular.ChatCircleText,
                    title = "Send Feedback",
                    subtitle = "Report bugs, request features",
                    onClick = onNavigateToFeedback,
                )
            }

            if (authState is AuthState.Authenticated && currentUser?.email != null) {
                item { ProfileSectionHeader("Account") }
                item {
                    ProfileMenuItem(
                        icon = PhosphorIcons.Regular.SignOut,
                        title = "Sign Out",
                        subtitle = currentUser?.email ?: "",
                        onClick = { showSignOutConfirm = true },
                    )
                }
            }

            item {
                @OptIn(ExperimentalFoundationApi::class)
                Text(
                    text = "v${BuildKonfig.APP_VERSION}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                onResetOnboarding()
                                scope.launch { snackbarHostState.showSnackbar("Onboarding reset") }
                            },
                        ),
                )
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
            onDismiss = { showAiProviderDialog = false },
        )
    }

    if (showSignOutConfirm) {
        val isOfflineOrError = syncStatus.state == SyncState.OFFLINE || syncStatus.state == SyncState.ERROR
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text(if (isOfflineOrError) "Unsynced Changes" else "Ready to leave?", fontWeight = FontWeight.Bold) },
            text = {
                if (isOfflineOrError) {
                    Column {
                        Text(
                            "Some of your recent changes haven't been saved to the cloud yet. Signing out now could result in losing them.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Try connecting to the internet and waiting for sync to finish first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text("Everything is synced. You can sign back in anytime to pick up where you left off.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSignOutConfirm = false; authViewModel.signOut() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(if (isOfflineOrError) "Leave Anyway" else "Yes, Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text(if (isOfflineOrError) "Wait for Sync" else "Stay")
                }
            },
        )
    }
}

@Composable
private fun AppearanceToggle(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.large),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(LifePlannerDesign.Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(LifePlannerDesign.Spacing.xs),
        ) {
            ThemeMode.entries.forEach { mode ->
                val selected = mode == current
                Surface(
                    modifier = Modifier.weight(1f).clickable { onSelect(mode) },
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(LifePlannerDesign.CornerRadius.small),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = LifePlannerDesign.Spacing.sm),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            mode.label(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}
