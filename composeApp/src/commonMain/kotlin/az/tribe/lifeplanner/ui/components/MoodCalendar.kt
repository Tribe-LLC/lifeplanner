package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.JournalEntry
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@Composable
fun MoodCalendar(
    entries: List<JournalEntry>,
    selectedMonth: LocalDate,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onMonthChange: (LocalDate) -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    // Group entries by date
    val entriesByDate = remember(entries) {
        entries.groupBy { it.date }
    }

    // Count entries for current month
    val monthEntryCount = remember(entries, selectedMonth) {
        entries.count {
            it.date.year == selectedMonth.year &&
            it.date.monthNumber == selectedMonth.monthNumber
        }
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrow_rotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Collapsible Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Mood Calendar",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$monthEntryCount entries this month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotationAngle)
                )
            }

            // Compact week strip (visible when collapsed)
            AnimatedVisibility(
                visible = !isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    WeekStrip(
                        entriesByDate = entriesByDate,
                        today = today,
                        onDayClick = onDayClick
                    )
                }
            }

            // Full calendar (visible when expanded)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Month Navigation
                    CalendarHeader(
                        selectedMonth = selectedMonth,
                        onPreviousMonth = {
                            onMonthChange(selectedMonth.minus(1, DateTimeUnit.MONTH))
                        },
                        onNextMonth = {
                            onMonthChange(selectedMonth.plus(1, DateTimeUnit.MONTH))
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Week day labels
                    WeekDaysRow()

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar Grid
                    AnimatedContent(
                        targetState = selectedMonth,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                            } else {
                                slideInHorizontally { -it } togetherWith slideOutHorizontally { it }
                            }
                        },
                        label = "calendar_animation"
                    ) { month ->
                        CalendarGrid(
                            days = getDaysForMonth(month),
                            entriesByDate = entriesByDate,
                            today = today,
                            currentMonth = month,
                            onDayClick = onDayClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(
    entriesByDate: Map<LocalDate, List<JournalEntry>>,
    today: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    val weekDays = remember(today) {
        (6 downTo 0).map { today.minus(it, DateTimeUnit.DAY) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        weekDays.forEach { day ->
            val entriesForDay = entriesByDate[day] ?: emptyList()
            val dayLabel = when (day.dayOfWeek) {
                DayOfWeek.MONDAY -> "Mon"
                DayOfWeek.TUESDAY -> "Tue"
                DayOfWeek.WEDNESDAY -> "Wed"
                DayOfWeek.THURSDAY -> "Thu"
                DayOfWeek.FRIDAY -> "Fri"
                DayOfWeek.SATURDAY -> "Sat"
                DayOfWeek.SUNDAY -> "Sun"
                else -> ""
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                    color = if (day == today) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                DayCell(
                    date = day,
                    entries = entriesForDay,
                    isToday = day == today,
                    isCurrentMonth = true,
                    onClick = { onDayClick(day) }
                )
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    selectedMonth: LocalDate,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousMonth) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = "Previous month",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "${monthNames[selectedMonth.monthNumber - 1]} ${selectedMonth.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        IconButton(onClick = onNextMonth) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Next month",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeekDaysRow() {
    val weekDays = listOf("S", "M", "T", "W", "T", "F", "S")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        weekDays.forEach { day ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    days: List<LocalDate?>,
    entriesByDate: Map<LocalDate, List<JournalEntry>>,
    today: LocalDate,
    currentMonth: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val entriesForDay = entriesByDate[day] ?: emptyList()
                            val isToday = day == today
                            val isCurrentMonth = day.monthNumber == currentMonth.monthNumber

                            DayCell(
                                date = day,
                                entries = entriesForDay,
                                isToday = isToday,
                                isCurrentMonth = isCurrentMonth,
                                onClick = { onDayClick(day) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    entries: List<JournalEntry>,
    isToday: Boolean,
    isCurrentMonth: Boolean,
    onClick: () -> Unit
) {
    val hasEntries = entries.isNotEmpty()
    val dominantMood = if (hasEntries) {
        entries.groupBy { it.mood }
            .maxByOrNull { it.value.size }
            ?.key ?: entries.first().mood
    } else null

    val moodColor = dominantMood?.let { getMoodColor(it) }
    val backgroundColor = when {
        hasEntries && moodColor != null -> moodColor.copy(alpha = 0.2f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (isToday) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                } else Modifier
            )
            .clickable(enabled = isCurrentMonth && hasEntries) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (hasEntries && dominantMood != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = dominantMood.emoji,
                    fontSize = 14.sp
                )
                if (entries.size > 1) {
                    Text(
                        text = "+${entries.size - 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentMonth) {
                    if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun getDaysForMonth(date: LocalDate): List<LocalDate?> {
    val firstDayOfMonth = LocalDate(date.year, date.monthNumber, 1)
    val lastDayOfMonth = when (date.monthNumber) {
        1, 3, 5, 7, 8, 10, 12 -> LocalDate(date.year, date.monthNumber, 31)
        4, 6, 9, 11 -> LocalDate(date.year, date.monthNumber, 30)
        2 -> {
            val isLeapYear = (date.year % 4 == 0 && date.year % 100 != 0) || (date.year % 400 == 0)
            LocalDate(date.year, date.monthNumber, if (isLeapYear) 29 else 28)
        }
        else -> LocalDate(date.year, date.monthNumber, 28)
    }

    val days = mutableListOf<LocalDate?>()

    val firstDayOfWeek = when (firstDayOfMonth.dayOfWeek) {
        DayOfWeek.SUNDAY -> 0
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
        else -> 0
    }

    val previousMonth = date.minus(1, DateTimeUnit.MONTH)
    val daysInPreviousMonth = when (previousMonth.monthNumber) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> {
            val isLeapYear = (previousMonth.year % 4 == 0 && previousMonth.year % 100 != 0) || (previousMonth.year % 400 == 0)
            if (isLeapYear) 29 else 28
        }
        else -> 28
    }

    for (i in (daysInPreviousMonth - firstDayOfWeek + 1)..daysInPreviousMonth) {
        days.add(LocalDate(previousMonth.year, previousMonth.monthNumber, i))
    }

    for (day in 1..lastDayOfMonth.dayOfMonth) {
        days.add(LocalDate(date.year, date.monthNumber, day))
    }

    val nextMonth = date.plus(1, DateTimeUnit.MONTH)
    val remainingDays = (7 - (days.size % 7)) % 7
    for (day in 1..remainingDays) {
        days.add(LocalDate(nextMonth.year, nextMonth.monthNumber, day))
    }

    return days
}

private fun getMoodColor(mood: Mood): Color {
    return when (mood) {
        Mood.VERY_HAPPY -> Color(0xFF4CAF50)
        Mood.HAPPY -> Color(0xFF8BC34A)
        Mood.NEUTRAL -> Color(0xFFFFC107)
        Mood.SAD -> Color(0xFFFF9800)
        Mood.VERY_SAD -> Color(0xFFF44336)
    }
}
