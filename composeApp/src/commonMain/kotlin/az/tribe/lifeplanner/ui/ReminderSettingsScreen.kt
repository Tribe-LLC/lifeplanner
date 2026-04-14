package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.DayOfWeek
import az.tribe.lifeplanner.domain.model.Reminder
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import az.tribe.lifeplanner.domain.model.ReminderSettings
import az.tribe.lifeplanner.domain.model.ReminderType
import az.tribe.lifeplanner.ui.reminder.ReminderViewModel
import az.tribe.lifeplanner.ui.theme.modernColors
import com.mmk.kmpnotifier.notification.NotifierManager
import kotlin.time.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

// ── Grouped Reminder Section ────────────────────────────────────────
private data class ReminderSection(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val reminders: List<Reminder>
)

private fun groupReminders(reminders: List<Reminder>): List<ReminderSection> {
    val goalReminders = reminders.filter {
        it.type == ReminderType.GOAL_DUE || it.type == ReminderType.GOAL_CHECK_IN || it.type == ReminderType.MILESTONE_DUE
    }
    val habitReminders = reminders.filter { it.type == ReminderType.HABIT_REMINDER }
    val wellnessReminders = reminders.filter {
        it.type == ReminderType.DAILY_REFLECTION || it.type == ReminderType.WEEKLY_REVIEW || it.type == ReminderType.MOTIVATION
    }
    val customReminders = reminders.filter { it.type == ReminderType.CUSTOM }

    return listOfNotNull(
        if (goalReminders.isNotEmpty()) ReminderSection(
            "Goals & Milestones", Icons.Rounded.Flag, Color(0xFF4A6FFF), goalReminders
        ) else null,
        if (habitReminders.isNotEmpty()) ReminderSection(
            "Habits", Icons.Rounded.FitnessCenter, Color(0xFF28C76F), habitReminders
        ) else null,
        if (wellnessReminders.isNotEmpty()) ReminderSection(
            "Daily Wellness", Icons.Rounded.SelfImprovement, Color(0xFF7A5AF8), wellnessReminders
        ) else null,
        if (customReminders.isNotEmpty()) ReminderSection(
            "Custom", Icons.Default.Notifications, Color(0xFF9E9FA3), customReminders
        ) else null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReminderViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Mark SET_REMINDER objective as completed on visit
    val objectiveViewModel: az.tribe.lifeplanner.ui.objectives.BeginnerObjectiveViewModel = koinViewModel()
    LaunchedEffect(Unit) {
        objectiveViewModel.markObjectiveCompleted(az.tribe.lifeplanner.domain.model.ObjectiveType.SET_REMINDER)
    }

    LaunchedEffect(Unit) {
        NotifierManager.getPermissionUtil().askNotificationPermission()
    }

    val sections = remember(uiState.reminders) { groupReminders(uiState.reminders) }
    val activeCount = uiState.reminders.count { it.isEnabled }
    val autoCount = uiState.reminders.count { it.id.startsWith("auto-") }

    Scaffold(
        containerColor = MaterialTheme.modernColors.background,
        topBar = {
            LargeTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.modernColors.background,
                    titleContentColor = MaterialTheme.modernColors.textPrimary
                ),
                title = {
                    Column {
                        Text(
                            "Smart Reminders",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            "$activeCount active \u2022 $autoCount auto-managed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.modernColors.textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.modernColors.textPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showSettingsSheet() }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.modernColors.textPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                text = { Text("Add Reminder") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Global Toggle
            item {
                GlobalReminderToggle(
                    isEnabled = uiState.settings.isEnabled,
                    onToggle = { viewModel.toggleGlobalReminders(it) }
                )
            }

            // Summary Card
            if (uiState.reminders.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    SummaryCard(reminders = uiState.reminders)
                }
            }

            // Test Notification
            item {
                TestNotificationCard()
            }

            // Smart Timing
            if (uiState.settings.smartTimingEnabled) {
                item { SmartTimingCard() }
            }

            // Grouped Sections
            if (uiState.reminders.isEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    EmptyRemindersCard(onAddClick = { viewModel.showAddDialog() })
                }
            } else {
                sections.forEach { section ->
                    item {
                        Spacer(Modifier.height(8.dp))
                        SectionHeader(
                            title = section.title,
                            icon = section.icon,
                            color = section.color,
                            count = section.reminders.size
                        )
                    }
                    items(section.reminders, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            onToggle = { viewModel.toggleReminder(reminder) },
                            onClick = { viewModel.selectReminder(reminder) },
                            onDelete = { viewModel.deleteReminder(reminder.id) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // Add Reminder Dialog
    if (uiState.showAddDialog) {
        AddReminderSheet(
            onDismiss = { viewModel.hideAddDialog() },
            onConfirm = { title, message, type, frequency, time, days, smartTiming ->
                viewModel.createReminder(
                    title = title,
                    message = message,
                    type = type,
                    frequency = frequency,
                    scheduledTime = time,
                    scheduledDays = days,
                    isSmartTiming = smartTiming
                )
            }
        )
    }

    // Edit Reminder Sheet
    if (uiState.showEditDialog && uiState.selectedReminder != null) {
        EditReminderSheet(
            reminder = uiState.selectedReminder!!,
            onDismiss = { viewModel.hideEditDialog() },
            onSave = { updated -> viewModel.updateReminder(updated) },
            onDelete = {
                viewModel.deleteReminder(uiState.selectedReminder!!.id)
                viewModel.hideEditDialog()
            }
        )
    }

    // Settings Bottom Sheet
    if (uiState.showSettingsSheet) {
        ReminderSettingsSheet(
            settings = uiState.settings,
            onDismiss = { viewModel.hideSettingsSheet() },
            onSave = { settings ->
                viewModel.updateSettings(settings)
                viewModel.hideSettingsSheet()
            }
        )
    }
}

// ── Summary Card ────────────────────────────────────────────────────

@Composable
private fun SummaryCard(reminders: List<Reminder>) {
    val goalCount = reminders.count {
        it.type == ReminderType.GOAL_DUE || it.type == ReminderType.MILESTONE_DUE || it.type == ReminderType.GOAL_CHECK_IN
    }
    val habitCount = reminders.count { it.type == ReminderType.HABIT_REMINDER }
    val wellnessCount = reminders.count {
        it.type == ReminderType.DAILY_REFLECTION || it.type == ReminderType.WEEKLY_REVIEW || it.type == ReminderType.MOTIVATION
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SummaryChip(
                count = goalCount,
                label = "Goals",
                color = Color(0xFF4A6FFF)
            )
            SummaryChip(
                count = habitCount,
                label = "Habits",
                color = Color(0xFF28C76F)
            )
            SummaryChip(
                count = wellnessCount,
                label = "Wellness",
                color = Color(0xFF7A5AF8)
            )
        }
    }
}

@Composable
private fun SummaryChip(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.modernColors.textSecondary
        )
    }
}

// ── Section Header ──────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, icon: ImageVector, color: Color, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.modernColors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.modernColors.textSecondary
        )
    }
}

// ── Global Toggle ───────────────────────────────────────────────────

@Composable
private fun GlobalReminderToggle(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = if (isEnabled)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    Text(
                        "Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (isEnabled) "All reminders are active" else "All reminders are paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isEnabled)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) NotifierManager.getPermissionUtil().askNotificationPermission()
                    onToggle(enabled)
                }
            )
        }
    }
}

// ── Test Notification ───────────────────────────────────────────────

@Composable
private fun TestNotificationCard() {
    OutlinedCard(
        onClick = {
            NotifierManager.getLocalNotifier().notify {
                id = 999
                title = "Life Planner"
                body = "Notifications are working!"
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Send Test Notification",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tap to verify notifications work on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Smart Timing Card ───────────────────────────────────────────────

@Composable
private fun SmartTimingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    "Smart Timing Active",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    "Reminders are optimized based on your activity patterns",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// ── Empty State ─────────────────────────────────────────────────────

@Composable
private fun EmptyRemindersCard(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onAddClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Alarm,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.modernColors.textSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No Reminders Yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.modernColors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Create a goal or habit and we'll set up\nsmart reminders automatically",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.modernColors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ── Reminder Card ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderCard(
    reminder: Reminder,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            when (dismissValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    showDeleteDialog = true
                    false
                }
                else -> false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.25f }
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    else -> Color.Transparent
                },
                animationSpec = tween(300),
                label = "swipeBg"
            )
            val scale by animateFloatAsState(
                targetValue = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) 1f else 0.8f,
                animationSpec = tween(300),
                label = "iconScale"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp).scale(scale)
                    )
                }
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true
    ) {
        ReminderCardContent(
            reminder = reminder,
            onToggle = onToggle,
            onClick = onClick
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Delete Reminder?") },
            text = { Text("Are you sure you want to delete \"${reminder.title}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ReminderCardContent(
    reminder: Reminder,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val typeColor = getReminderTypeColor(reminder.type)
    val isAuto = reminder.id.startsWith("auto-")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type indicator
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(typeColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getReminderTypeIcon(reminder.type),
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (reminder.isEnabled)
                            MaterialTheme.modernColors.textPrimary
                        else
                            MaterialTheme.modernColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isAuto) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "AUTO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified
                            )
                        }
                    }
                    if (reminder.isSmartTiming) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Smart timing",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))

                // Message preview
                if (reminder.message.isNotBlank()) {
                    Text(
                        text = reminder.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.modernColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Time & frequency row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Rounded.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.modernColors.textSecondary
                    )
                    Text(
                        text = formatTime12h(reminder.scheduledTime),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.modernColors.textSecondary
                    )
                    Text(
                        text = "\u2022",
                        color = MaterialTheme.modernColors.textSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = formatFrequency(reminder),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.modernColors.textSecondary
                    )
                }
            }

            // Toggle
            Switch(
                checked = reminder.isEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private fun formatFrequency(reminder: Reminder): String {
    return when (reminder.frequency) {
        ReminderFrequency.ONCE -> "Once"
        ReminderFrequency.DAILY -> "Every day"
        ReminderFrequency.WEEKDAYS -> "Weekdays"
        ReminderFrequency.WEEKENDS -> "Weekends"
        ReminderFrequency.WEEKLY -> {
            val days = reminder.scheduledDays.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
            if (days.isNotBlank()) days else "Weekly"
        }
        ReminderFrequency.MONTHLY -> "Monthly"
        ReminderFrequency.SMART -> "Smart"
    }
}

private fun formatTime12h(time: LocalTime): String {
    val hour = time.hour
    val minute = time.minute
    val amPm = if (hour < 12) "AM" else "PM"
    val h12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "${h12.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')} $amPm"
}

// ── Add Reminder Sheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderSheet(
    onDismiss: () -> Unit,
    onConfirm: (String, String, ReminderType, ReminderFrequency, LocalTime, List<DayOfWeek>, Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ReminderType.CUSTOM) }
    var selectedFrequency by remember { mutableStateOf(ReminderFrequency.DAILY) }
    var selectedDays by remember { mutableStateOf<List<DayOfWeek>>(emptyList()) }
    var smartTiming by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = false)

    if (showTimePicker) {
        TimePickerDialog(
            state = timePickerState,
            onDismiss = { showTimePicker = false },
            onConfirm = { showTimePicker = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "New Reminder",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Type selector
            Text(
                "Type",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val types = listOf(
                    ReminderType.CUSTOM to "Custom",
                    ReminderType.GOAL_CHECK_IN to "Goal",
                    ReminderType.DAILY_REFLECTION to "Reflection",
                    ReminderType.MOTIVATION to "Motivation"
                )
                types.forEach { (type, label) ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            // Time picker
            val displayHour = timePickerState.hour
            val displayMinute = timePickerState.minute
            val timeText = formatTime12h(LocalTime(displayHour, displayMinute))

            OutlinedCard(
                onClick = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(timeText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(Icons.Rounded.AccessTime, contentDescription = "Pick time", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Frequency
            Text("Repeat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val frequencies = listOf(
                    ReminderFrequency.ONCE to "Once",
                    ReminderFrequency.DAILY to "Daily",
                    ReminderFrequency.WEEKDAYS to "Weekdays",
                    ReminderFrequency.WEEKLY to "Weekly"
                )
                frequencies.forEachIndexed { index, (freq, label) ->
                    SegmentedButton(
                        selected = selectedFrequency == freq,
                        onClick = { selectedFrequency = freq },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = frequencies.size)
                    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                }
            }

            // Day selector
            AnimatedVisibility(visible = selectedFrequency == ReminderFrequency.WEEKLY) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in selectedDays,
                                onClick = { selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day },
                                label = { Text(day.name.take(2), style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Smart timing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Smart Timing", style = MaterialTheme.typography.bodyMedium)
                    Text("Auto-optimize based on activity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = smartTiming, onCheckedChange = { smartTiming = it })
            }

            // Preview card
            if (title.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                "Preview: \"$title\" at $timeText",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                formatFrequency(
                                    Reminder(
                                        id = "", title = "", message = "",
                                        type = selectedType, frequency = selectedFrequency,
                                        scheduledTime = LocalTime(0, 0), scheduledDays = selectedDays,
                                        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                    )
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedCard(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text("Cancel", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onConfirm(
                                title, message, selectedType, selectedFrequency,
                                LocalTime(timePickerState.hour, timePickerState.minute),
                                selectedDays, smartTiming
                            )
                        }
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Text("Create Reminder", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

// ── Edit Reminder Sheet ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditReminderSheet(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var title by remember { mutableStateOf(reminder.title) }
    var message by remember { mutableStateOf(reminder.message) }
    var selectedFrequency by remember { mutableStateOf(reminder.frequency) }
    var selectedDays by remember { mutableStateOf(reminder.scheduledDays) }
    var smartTiming by remember { mutableStateOf(reminder.isSmartTiming) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = reminder.scheduledTime.hour,
        initialMinute = reminder.scheduledTime.minute,
        is24Hour = false
    )
    val isAuto = reminder.id.startsWith("auto-")

    if (showTimePicker) {
        TimePickerDialog(
            state = timePickerState,
            onDismiss = { showTimePicker = false },
            onConfirm = { showTimePicker = false }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Reminder") },
            text = { Text("Are you sure you want to delete \"${reminder.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit Reminder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (isAuto) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("AUTO-MANAGED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            if (isAuto) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
                ) {
                    Text(
                        "This reminder was auto-created from your goals or habits. You can customize the time and frequency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            // Time picker
            val timeText = formatTime12h(LocalTime(timePickerState.hour, timePickerState.minute))
            OutlinedCard(
                onClick = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(timeText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(Icons.Rounded.AccessTime, contentDescription = "Pick time", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Frequency
            Text("Repeat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val frequencies = listOf(
                    ReminderFrequency.ONCE to "Once",
                    ReminderFrequency.DAILY to "Daily",
                    ReminderFrequency.WEEKDAYS to "Weekdays",
                    ReminderFrequency.WEEKLY to "Weekly"
                )
                frequencies.forEachIndexed { index, (freq, label) ->
                    SegmentedButton(
                        selected = selectedFrequency == freq,
                        onClick = { selectedFrequency = freq },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = frequencies.size)
                    ) { Text(label, style = MaterialTheme.typography.labelSmall) }
                }
            }

            // Day selector
            AnimatedVisibility(visible = selectedFrequency == ReminderFrequency.WEEKLY) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Days", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(
                                selected = day in selectedDays,
                                onClick = { selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day },
                                label = { Text(day.name.take(2), style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Smart timing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Smart Timing", style = MaterialTheme.typography.bodyMedium)
                    Text("Auto-optimize based on activity", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = smartTiming, onCheckedChange = { smartTiming = it })
            }

            // Actions
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Delete")
                }
                Spacer(Modifier.weight(1f))
                OutlinedCard(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text("Cancel", style = MaterialTheme.typography.titleSmall)
                    }
                }
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onSave(
                                reminder.copy(
                                    title = title,
                                    message = message,
                                    frequency = selectedFrequency,
                                    scheduledTime = LocalTime(timePickerState.hour, timePickerState.minute),
                                    scheduledDays = selectedDays,
                                    isSmartTiming = smartTiming
                                )
                            )
                        }
                    },
                    enabled = title.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) { Text("Save", style = MaterialTheme.typography.titleSmall) }
            }
        }
    }
}

// ── Time Picker Dialog ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    state: TimePickerState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time", style = MaterialTheme.typography.titleMedium) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ── Settings Sheet ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderSettingsSheet(
    settings: ReminderSettings,
    onDismiss: () -> Unit,
    onSave: (ReminderSettings) -> Unit
) {
    var localSettings by remember { mutableStateOf(settings) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Reminder Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            SettingsRow(
                icon = Icons.Rounded.AutoAwesome,
                title = "Smart Timing",
                subtitle = "Optimize reminder times based on your activity",
                trailing = {
                    Switch(
                        checked = localSettings.smartTimingEnabled,
                        onCheckedChange = { localSettings = localSettings.copy(smartTimingEnabled = it) }
                    )
                }
            )

            HorizontalDivider()

            SettingsRow(
                icon = Icons.Rounded.NightsStay,
                title = "Quiet Hours",
                subtitle = "${localSettings.quietHoursStart} - ${localSettings.quietHoursEnd}",
                trailing = {}
            )

            SettingsRow(
                icon = Icons.Rounded.Repeat,
                title = "Max Reminders/Day",
                subtitle = "${localSettings.maxRemindersPerDay} reminders",
                trailing = {}
            )

            SettingsRow(
                icon = Icons.Rounded.CalendarToday,
                title = "Weekly Review",
                subtitle = "${localSettings.weeklyReviewDay.name.lowercase().replaceFirstChar { it.uppercase() }} at ${localSettings.weeklyReviewTime}",
                trailing = {}
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = { onSave(localSettings) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save Settings")
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.modernColors.textSecondary)
            }
        }
        trailing()
    }
}

// ── Color & Icon Helpers ────────────────────────────────────────────

private fun getReminderTypeColor(type: ReminderType): Color {
    return when (type) {
        ReminderType.GOAL_CHECK_IN -> Color(0xFF4A6FFF)
        ReminderType.HABIT_REMINDER -> Color(0xFF28C76F)
        ReminderType.MILESTONE_DUE -> Color(0xFFFF9F43)
        ReminderType.GOAL_DUE -> Color(0xFFEA5455)
        ReminderType.DAILY_REFLECTION -> Color(0xFF7A5AF8)
        ReminderType.WEEKLY_REVIEW -> Color(0xFF00CFE8)
        ReminderType.MOTIVATION -> Color(0xFF6236FF)
        ReminderType.CUSTOM -> Color(0xFF9E9FA3)
    }
}

private fun getReminderTypeIcon(type: ReminderType): ImageVector {
    return when (type) {
        ReminderType.GOAL_CHECK_IN -> Icons.Rounded.AccessTime
        ReminderType.HABIT_REMINDER -> Icons.Rounded.Repeat
        ReminderType.MILESTONE_DUE -> Icons.Rounded.Star
        ReminderType.GOAL_DUE -> Icons.Rounded.Alarm
        ReminderType.DAILY_REFLECTION -> Icons.Rounded.SelfImprovement
        ReminderType.WEEKLY_REVIEW -> Icons.Rounded.ViewWeek
        ReminderType.MOTIVATION -> Icons.Rounded.LightMode
        ReminderType.CUSTOM -> Icons.Default.Notifications
    }
}
