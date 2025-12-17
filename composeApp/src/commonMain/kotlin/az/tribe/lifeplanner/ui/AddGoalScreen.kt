package az.tribe.lifeplanner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.NewMilestone
import az.tribe.lifeplanner.ui.theme.modernColors
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddGoalScreen(
    viewModel: GoalViewModel,
    onGoalSaved: () -> Unit,
    onBackClick: () -> Unit
) {
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var notes by remember { mutableStateOf(TextFieldValue("")) }
    var selectedCategory by remember { mutableStateOf(GoalCategory.FINANCIAL) }
    var selectedTimeline by remember { mutableStateOf(GoalTimeline.SHORT_TERM) }

    // Initialize with current date
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    var dueDate by remember { mutableStateOf(today) }
    var dueDateText by remember { mutableStateOf(dueDate.toString()) }

    var milestones by remember { mutableStateOf(listOf<NewMilestone>()) }
    var isFormValid by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = Clock.System.now().toEpochMilliseconds()
    )
    var showDatePicker by remember { mutableStateOf(false) }

    // Validate form
    LaunchedEffect(title, description, dueDateText) {
        isFormValid = title.text.isNotBlank() &&
                description.text.isNotBlank() &&
                dueDateText.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
    }

    Scaffold(
        containerColor = MaterialTheme.modernColors.background,
        topBar = {
            LargeTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.modernColors.background,
                    titleContentColor = MaterialTheme.modernColors.textPrimary,
                ),
                title = {
                    Text(
                        "Create New Goal",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.modernColors.textPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
                // Conditionally enable the onClick action
                val fabOnClick = if (isFormValid) {
                    {
                        val currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                        val goal = Goal(
                            id = Uuid.random().toString(),
                            category = selectedCategory,
                            title = title.text.trim(),
                            description = description.text.trim(),
                            status = GoalStatus.NOT_STARTED,
                            timeline = selectedTimeline,
                            dueDate = dueDate,
                            progress = 0,
                            milestones = milestones.mapIndexed { idx, m ->
                                Milestone(
                                    id = idx.toString(),
                                    title = m.title.text.trim(),
                                    dueDate = try {
                                        LocalDate.parse(m.dueDate.text)
                                    } catch (_: Exception) {
                                        null
                                    },
                                    isCompleted = false
                                )
                            },
                            notes = notes.text.trim(),
                            createdAt = currentTime,
                            completionRate = 0f,
                            isArchived = false
                        )
                        viewModel.createGoal(goal)
                        onGoalSaved()
                    }
                } else {
                    {} // No-op when form is invalid
                }

                ExtendedFloatingActionButton(
                    onClick = fabOnClick,
                    text = { Text("Save Goal") }, // Corrected: Pass as a composable lambda
                    icon = { // Corrected: Pass as a composable lambda
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save"
                        )
                    },
                    expanded = isFormValid, // This controls the visual expansion
                    shape = RoundedCornerShape(16.dp),
                    containerColor = if (isFormValid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, // Example: change color when disabled
                    contentColor = if (isFormValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, // Example: change color when disabled
                    // The enabled state is implicitly handled by the onClick behavior
                    // and visual cues (like color changes or the 'expanded' state).
                )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Title Section
            item {
                FormSectionHeader(title = "Goal Details", icon = null)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("Enter goal title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // Description Section
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Describe your goal") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // Notes Section
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    placeholder = { Text("Add any additional notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                )
            }

            // Category & Timeline Section
            item {
                FormSectionHeader(title = "Classification", icon = null)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Category",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.modernColors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ModernDropdownMenu(
                            options = GoalCategory.entries,
                            selected = selectedCategory,
                            onSelected = { selectedCategory = it }
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Timeline",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.modernColors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ModernDropdownMenu(
                            options = GoalTimeline.entries,
                            selected = selectedTimeline,
                            onSelected = { selectedTimeline = it }
                        )
                    }
                }
            }

            // Due Date Section
            item {
                Text(
                    "Due Date",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.modernColors.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                DateSelector(
                    date = dueDateText,
                    onDateClick = { showDatePicker = true }
                )
            }

            // Milestones Section
            item {
                FormSectionHeader(title = "Milestones", icon = Icons.Default.Add) {
                    milestones = milestones + NewMilestone(TextFieldValue(""), TextFieldValue(""))
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (milestones.isEmpty()) {
                    EmptyMilestoneCard {
                        milestones = milestones + NewMilestone(TextFieldValue(""), TextFieldValue(""))
                    }
                }
            }

            // Milestone Items
            items(milestones.size) { index ->
                val milestone = milestones[index]
                MilestoneItem(
                    milestone = milestone,
                    onMilestoneChange = { updatedMilestone ->
                        milestones = milestones.toMutableList().also {
                            it[index] = updatedMilestone
                        }
                    },
                    onRemove = {
                        milestones = milestones.toMutableList().also { it.removeAt(index) }
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val localDate = Instant.fromEpochMilliseconds(millis)
                                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
                                dueDate = localDate
                                dueDateText = localDate.toString()
                            }
                            showDatePicker = false
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
fun FormSectionHeader(
    title: String,
    icon: ImageVector?,
    onIconClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.modernColors.textPrimary
        )

        if (icon != null) {
            IconButton(
                onClick = onIconClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Add $title",
                    tint = MaterialTheme.modernColors.primary
                )
            }
        }
    }
}

@Composable
fun DateSelector(
    date: String,
    onDateClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        onClick = onDateClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = date,
                color = MaterialTheme.modernColors.textPrimary,
                style = MaterialTheme.typography.bodyLarge
            )

            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = "Select Date",
                tint = MaterialTheme.modernColors.primary
            )
        }
    }
}

@Composable
fun EmptyMilestoneCard(
    onAddClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        onClick = onAddClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Milestone",
                tint = MaterialTheme.modernColors.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Add Your First Milestone",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.modernColors.textSecondary
            )
        }
    }
}

@Composable
fun MilestoneItem(
    milestone: NewMilestone,
    onMilestoneChange: (NewMilestone) -> Unit,
    onRemove: () -> Unit
) {
    val alpha by animateFloatAsState(
        targetValue = if (milestone.title.text.isBlank()) 0.9f else 1f,
        label = "MilestoneAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(alpha),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Milestone",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.modernColors.textSecondary
                )

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove Milestone",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            OutlinedTextField(
                value = milestone.title,
                onValueChange = { onMilestoneChange(milestone.copy(title = it)) },
                placeholder = { Text("Enter milestone title") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            )

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ModernDropdownMenu(
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selected.toString()
                .lowercase()
                .replace('_', ' ')
                .split(' ')
                .joinToString(" ") { it.replaceFirstChar(Char::uppercase) },
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .clip(RoundedCornerShape(12.dp))
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.toString()
                                .lowercase()
                                .replace('_', ' ')
                                .split(' ')
                                .joinToString(" ") { word ->
                                    word.replaceFirstChar(Char::uppercase)
                                }
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.modernColors.textPrimary
                    )
                )
            }
        }
    }
}