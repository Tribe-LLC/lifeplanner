package az.tribe.lifeplanner.ui.retrospective

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.Badge
import az.tribe.lifeplanner.domain.model.DaySnapshot
import az.tribe.lifeplanner.domain.model.FocusSession
import az.tribe.lifeplanner.domain.model.GoalChangeWithTitle
import az.tribe.lifeplanner.domain.model.HabitDayStatus
import az.tribe.lifeplanner.domain.model.HabitDaySummary
import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.ui.components.GlassCard
import az.tribe.lifeplanner.ui.components.getIcon
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.theme.backgroundColor
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetrospectiveScreen(
    onNavigateBack: () -> Unit,
    viewModel: RetrospectiveViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Day Retrospective",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = LifePlannerDesign.Padding.screenHorizontal,
                end = LifePlannerDesign.Padding.screenHorizontal,
                top = 8.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Date Navigator
            item {
                DateNavigator(
                    selectedDate = uiState.selectedDate,
                    onPrevious = viewModel::goToPreviousDay,
                    onNext = viewModel::goToNextDay,
                    onDateTap = { showDatePicker = true }
                )
            }

            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
            } else if (uiState.snapshot != null && uiState.snapshot?.hasAnyActivity == false) {
                // Empty state
                item { EmptyDayState() }
            } else {
                val snapshot = uiState.snapshot ?: return@LazyColumn

                // 2. Day Summary Card
                item { DaySummaryCard(snapshot) }

                // 3. Mood & Journal
                if (snapshot.journalEntries.isNotEmpty()) {
                    item { SectionHeader("Mood & Journal") }
                    item { JournalSection(snapshot.journalEntries) }
                }

                // 4. Habits
                if (snapshot.habitSummary.habits.isNotEmpty()) {
                    item { SectionHeader("Habits (${snapshot.habitSummary.completedHabits}/${snapshot.habitSummary.totalHabits})") }
                    item { HabitsSection(snapshot.habitSummary) }
                }

                // 5. Focus Sessions
                if (snapshot.focusSessions.isNotEmpty()) {
                    item { SectionHeader("Focus Sessions") }
                    item { FocusSection(snapshot.focusSessions) }
                }

                // 6. Goal Changes
                if (snapshot.goalChanges.isNotEmpty()) {
                    item { SectionHeader("Goal Changes") }
                    item { GoalChangesSection(snapshot.goalChanges) }
                }

                // 7. Badges Earned
                if (snapshot.badgesEarned.isNotEmpty()) {
                    item { SectionHeader("Badges Earned") }
                    item { BadgesSection(snapshot.badgesEarned) }
                }

                // 8. Compare Toggle
                item {
                    CompareToggle(
                        isEnabled = uiState.compareMode,
                        onToggle = viewModel::toggleCompareMode
                    )
                }

                // Compare section
                val todaySnap = uiState.todaySnapshot
                if (uiState.compareMode && todaySnap != null) {
                    item {
                        CompareSection(
                            thenSnapshot = snapshot,
                            nowSnapshot = todaySnap
                        )
                    }
                }
            }
        }
    }

    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate
                .atStartOfDayIn(TimeZone.UTC)
                .toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.fromEpochMilliseconds(millis)
                            .toLocalDateTime(TimeZone.UTC).date
                        viewModel.selectDate(date)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ============== DATE NAVIGATOR ==============

@Composable
private fun DateNavigator(
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDateTap: () -> Unit
) {
    val today = remember {
        kotlinx.datetime.Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
    }
    val isToday = selectedDate == today
    val dayLabel = when {
        isToday -> "Today"
        selectedDate == today.minus(kotlinx.datetime.DatePeriod(days = 1)) -> "Yesterday"
        else -> {
            val month = selectedDate.month.name.take(3).lowercase()
                .replaceFirstChar { it.uppercase() }
            "$month ${selectedDate.dayOfMonth}, ${selectedDate.year}"
        }
    }
    val dayOfWeek = selectedDate.dayOfWeek.name.take(3).lowercase()
        .replaceFirstChar { it.uppercase() }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous day")
            }

            Surface(
                onClick = onDateTap,
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            dayLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            dayOfWeek,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            IconButton(
                onClick = onNext,
                enabled = !isToday
            ) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = "Next day",
                    tint = if (!isToday)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// ============== DAY SUMMARY CARD ==============

@Composable
private fun DaySummaryCard(snapshot: DaySnapshot) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        // Gradient accent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Mood
            SummaryItem(
                emoji = snapshot.dominantMood?.emoji ?: "-",
                label = snapshot.dominantMood?.displayName ?: "No mood"
            )
            // Habits
            SummaryItem(
                emoji = if (snapshot.habitSummary.completedHabits == snapshot.habitSummary.totalHabits && snapshot.habitSummary.totalHabits > 0) "\u2705" else "\uD83D\uDCCB",
                label = "${snapshot.habitSummary.completedHabits}/${snapshot.habitSummary.totalHabits} habits"
            )
            // Focus
            SummaryItem(
                emoji = "\u23F1\uFE0F",
                label = "${snapshot.totalFocusMinutes}m focus"
            )
            // Changes
            SummaryItem(
                emoji = "\uD83D\uDD04",
                label = "${snapshot.goalChanges.size} changes"
            )
        }
    }
}

@Composable
private fun SummaryItem(emoji: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            emoji,
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ============== SECTION HEADER ==============

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// ============== JOURNAL SECTION ==============

@Composable
private fun JournalSection(entries: List<JournalEntry>) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        entry.mood.emoji,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            entry.content.take(80) + if (entry.content.length > 80) "..." else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (index < entries.size - 1) {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// ============== HABITS SECTION ==============

@Composable
private fun HabitsSection(summary: HabitDaySummary) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            summary.habits.forEachIndexed { index, habit ->
                HabitDayRow(habit)
                if (index < summary.habits.size - 1) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitDayRow(habit: HabitDayStatus) {
    val categoryColor = habit.category.backgroundColor()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Category icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(categoryColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = habit.category.getIcon(),
                contentDescription = null,
                tint = categoryColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title
        Text(
            text = habit.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Status indicator
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (habit.wasCompleted) Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (habit.wasCompleted) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = if (habit.wasCompleted) "Completed" else "Missed",
                tint = if (habit.wasCompleted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ============== FOCUS SECTION ==============

@Composable
private fun FocusSection(sessions: List<FocusSession>) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            sessions.forEachIndexed { index, session ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFF6B35).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = Color(0xFFFF6B35),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${session.actualMinutes}m" + if (session.wasCompleted) " (completed)" else " (partial)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${session.plannedDurationMinutes}m planned",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (session.xpEarned > 0) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color(0xFFFFA726).copy(alpha = 0.12f)
                        ) {
                            Text(
                                "+${session.xpEarned} XP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFFFA726),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                if (index < sessions.size - 1) {
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

// ============== GOAL CHANGES SECTION ==============

@Composable
private fun GoalChangesSection(changes: List<GoalChangeWithTitle>) {
    // Sort by time and group changes by the same goal
    val sortedChanges = changes.sortedBy { it.changedAt }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            sortedChanges.forEachIndexed { index, change ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Timeline with time label
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(52.dp).padding(end = 8.dp)
                    ) {
                        Text(
                            formatTime(change.changedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    when (change.field.lowercase()) {
                                        "status" -> MaterialTheme.colorScheme.primary
                                        "progress" -> Color(0xFF4CAF50)
                                        else -> MaterialTheme.colorScheme.tertiary
                                    }
                                )
                        )
                        if (index < sortedChanges.size - 1) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(28.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            change.goalTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            buildChangeDescription(change),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (index < sortedChanges.size - 1) {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

private fun buildChangeDescription(change: GoalChangeWithTitle): String {
    return when (change.field.lowercase()) {
        "progress" -> {
            val old = change.oldValue?.toIntOrNull() ?: 0
            val new = change.newValue?.toIntOrNull() ?: 0
            if (new > old) "Progress increased from $old% to $new%"
            else if (new < old) "Progress adjusted from $old% to $new%"
            else "Progress is at $new%"
        }
        "status" -> {
            val old = formatStatus(change.oldValue)
            val new = formatStatus(change.newValue)
            "Moved from $old to $new"
        }
        "notes" -> "Notes were updated"
        "title" -> "Renamed to \"${change.newValue ?: "?"}\""
        "description" -> "Description was updated"
        "category" -> "Category changed to ${formatCategory(change.newValue)}"
        "timeline" -> "Timeline changed to ${formatTimeline(change.newValue)}"
        "duedate", "due_date" -> "Due date updated to ${change.newValue ?: "?"}"
        else -> "${formatFieldName(change.field)} was updated"
    }
}

private fun formatStatus(status: String?): String {
    return when (status?.uppercase()) {
        "NOT_STARTED" -> "Not Started"
        "IN_PROGRESS" -> "In Progress"
        "COMPLETED" -> "Completed"
        "ON_HOLD" -> "On Hold"
        "CANCELLED" -> "Cancelled"
        else -> status?.replace("_", " ")?.lowercase()
            ?.replaceFirstChar { it.uppercase() } ?: "Unknown"
    }
}

private fun formatCategory(category: String?): String {
    return category?.replace("_", " ")?.lowercase()
        ?.replaceFirstChar { it.uppercase() } ?: "Unknown"
}

private fun formatTimeline(timeline: String?): String {
    return when (timeline?.uppercase()) {
        "SHORT_TERM" -> "Short Term"
        "MID_TERM" -> "Mid Term"
        "LONG_TERM" -> "Long Term"
        else -> timeline?.replace("_", " ")?.lowercase()
            ?.replaceFirstChar { it.uppercase() } ?: "Unknown"
    }
}

private fun formatFieldName(field: String): String {
    return field.replace("_", " ").lowercase()
        .replaceFirstChar { it.uppercase() }
}

private fun formatTime(dateTime: kotlinx.datetime.LocalDateTime): String {
    val hour = dateTime.hour
    val minute = dateTime.minute.toString().padStart(2, '0')
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    return "$displayHour:$minute $amPm"
}

// ============== BADGES SECTION ==============

@Composable
private fun BadgesSection(badges: List<Badge>) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            badges.forEachIndexed { index, badge ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge icon in colored circle
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(badge.type.color).copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            badge.type.icon,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            badge.type.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Earned on this day",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = Color(badge.type.color),
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (index < badges.size - 1) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ============== COMPARE TOGGLE ==============

@Composable
private fun CompareToggle(isEnabled: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = if (isEnabled)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.CompareArrows,
                contentDescription = null,
                tint = if (isEnabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Compare with Today",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (isEnabled) "Showing then vs now" else "Tap to compare stats",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============== COMPARE SECTION ==============

@Composable
private fun CompareSection(thenSnapshot: DaySnapshot, nowSnapshot: DaySnapshot) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row with labels
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "How you're doing",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "That day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp)
                )
                Spacer(Modifier.width(24.dp))
                Text(
                    "Today",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp)
                )
            }

            CompareRow(
                label = "Habits completed",
                thenValue = "${thenSnapshot.habitSummary.completedHabits}/${thenSnapshot.habitSummary.totalHabits}",
                nowValue = "${nowSnapshot.habitSummary.completedHabits}/${nowSnapshot.habitSummary.totalHabits}"
            )
            CompareRow(
                label = "Focus time",
                thenValue = "${thenSnapshot.totalFocusMinutes}m",
                nowValue = "${nowSnapshot.totalFocusMinutes}m"
            )
            CompareRow(
                label = "Journal entries",
                thenValue = "${thenSnapshot.journalEntries.size}",
                nowValue = "${nowSnapshot.journalEntries.size}"
            )
            CompareRow(
                label = "XP earned",
                thenValue = "${thenSnapshot.xpEarnedOnDay}",
                nowValue = "${nowSnapshot.xpEarnedOnDay}"
            )
        }
    }
}

@Composable
private fun CompareRow(label: String, thenValue: String, nowValue: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            thenValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp)
        )
        Text(
            "\u2192",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outlineVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp)
        )
        Text(
            nowValue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp)
        )
    }
}

// ============== EMPTY STATE ==============

@Composable
private fun EmptyDayState() {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.Inbox,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No activity recorded",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Nothing was tracked on this date",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
