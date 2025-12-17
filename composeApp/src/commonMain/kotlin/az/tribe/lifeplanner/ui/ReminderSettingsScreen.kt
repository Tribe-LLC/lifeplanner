package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.DayOfWeek
import az.tribe.lifeplanner.domain.model.Reminder
import az.tribe.lifeplanner.domain.model.ReminderFrequency
import az.tribe.lifeplanner.domain.model.ReminderSettings
import az.tribe.lifeplanner.domain.model.ReminderType
import az.tribe.lifeplanner.ui.reminder.ReminderViewModel
import az.tribe.lifeplanner.ui.theme.modernColors
import kotlinx.datetime.LocalTime
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReminderViewModel = koinInject()
) {
    val uiState by viewModel.uiState.collectAsState()

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
                            "${uiState.reminders.count { it.isEnabled }} active reminders",
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Global Toggle
            item {
                GlobalReminderToggle(
                    isEnabled = uiState.settings.isEnabled,
                    onToggle = { viewModel.toggleGlobalReminders(it) }
                )
            }

            // Smart Timing Info Card
            if (uiState.settings.smartTimingEnabled) {
                item {
                    SmartTimingCard()
                }
            }

            // Reminders Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Your Reminders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.modernColors.textPrimary
                    )
                    Text(
                        "${uiState.reminders.size} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.modernColors.textSecondary
                    )
                }
            }

            // Reminders List
            if (uiState.reminders.isEmpty()) {
                item {
                    EmptyRemindersCard(onAddClick = { viewModel.showAddDialog() })
                }
            } else {
                items(uiState.reminders, key = { it.id }) { reminder ->
                    ReminderCard(
                        reminder = reminder,
                        onToggle = { viewModel.toggleReminder(reminder) },
                        onClick = { viewModel.selectReminder(reminder) },
                        onDelete = { viewModel.deleteReminder(reminder.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // Add Reminder Dialog
    if (uiState.showAddDialog) {
        AddReminderDialog(
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
                onCheckedChange = onToggle
            )
        }
    }
}

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
            Text(
                "Tap to create your first reminder",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.modernColors.textSecondary
            )
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: Reminder,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val typeColor = getReminderTypeColor(reminder.type)

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
                .padding(16.dp),
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
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (reminder.isEnabled)
                        MaterialTheme.modernColors.textPrimary
                    else
                        MaterialTheme.modernColors.textSecondary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = reminder.scheduledTime.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.modernColors.textSecondary
                    )
                    Text(
                        text = "\u2022",
                        color = MaterialTheme.modernColors.textSecondary
                    )
                    Text(
                        text = reminder.frequency.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.modernColors.textSecondary
                    )
                    if (reminder.isSmartTiming) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Smart timing",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // Toggle
            Switch(
                checked = reminder.isEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, ReminderType, ReminderFrequency, LocalTime, List<DayOfWeek>, Boolean) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ReminderType.GOAL_CHECK_IN) }
    var selectedFrequency by remember { mutableStateOf(ReminderFrequency.DAILY) }
    var hour by remember { mutableStateOf(9) }
    var minute by remember { mutableStateOf(0) }
    var selectedDays by remember { mutableStateOf<List<DayOfWeek>>(emptyList()) }
    var smartTiming by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Reminder") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                // Time picker (simplified)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = hour.toString().padStart(2, '0'),
                        onValueChange = {
                            it.toIntOrNull()?.let { h ->
                                if (h in 0..23) hour = h
                            }
                        },
                        label = { Text("Hour") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = minute.toString().padStart(2, '0'),
                        onValueChange = {
                            it.toIntOrNull()?.let { m ->
                                if (m in 0..59) minute = m
                            }
                        },
                        label = { Text("Minute") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Smart timing toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Smart Timing")
                    Switch(
                        checked = smartTiming,
                        onCheckedChange = { smartTiming = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            title,
                            message,
                            selectedType,
                            selectedFrequency,
                            LocalTime(hour, minute),
                            selectedDays,
                            smartTiming
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

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
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Reminder Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Smart Timing
            SettingsRow(
                icon = Icons.Rounded.AutoAwesome,
                title = "Smart Timing",
                subtitle = "Optimize reminder times based on your activity",
                trailing = {
                    Switch(
                        checked = localSettings.smartTimingEnabled,
                        onCheckedChange = {
                            localSettings = localSettings.copy(smartTimingEnabled = it)
                        }
                    )
                }
            )

            HorizontalDivider()

            // Quiet Hours
            SettingsRow(
                icon = Icons.Rounded.NightsStay,
                title = "Quiet Hours",
                subtitle = "${localSettings.quietHoursStart} - ${localSettings.quietHoursEnd}",
                trailing = {}
            )

            // Max reminders per day
            SettingsRow(
                icon = Icons.Rounded.Repeat,
                title = "Max Reminders/Day",
                subtitle = "${localSettings.maxRemindersPerDay} reminders",
                trailing = {}
            )

            // Weekly Review
            SettingsRow(
                icon = Icons.Rounded.CalendarToday,
                title = "Weekly Review",
                subtitle = "${localSettings.weeklyReviewDay.name.lowercase().replaceFirstChar { it.uppercase() }} at ${localSettings.weeklyReviewTime}",
                trailing = {}
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onSave(localSettings) },
                modifier = Modifier.fillMaxWidth()
            ) {
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.modernColors.textSecondary
                )
            }
        }
        trailing()
    }
}

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
        ReminderType.MILESTONE_DUE -> Icons.Rounded.CalendarToday
        ReminderType.GOAL_DUE -> Icons.Rounded.Alarm
        ReminderType.DAILY_REFLECTION -> Icons.Rounded.NightsStay
        ReminderType.WEEKLY_REVIEW -> Icons.Rounded.CalendarToday
        ReminderType.MOTIVATION -> Icons.Rounded.AutoAwesome
        ReminderType.CUSTOM -> Icons.Default.Notifications
    }
}
