package az.tribe.lifeplanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.repository.GoalTemplateProvider
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.GoalTemplate
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.domain.model.NewMilestone
import az.tribe.lifeplanner.ui.theme.backgroundColor
import az.tribe.lifeplanner.ui.theme.modernColors
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddGoalFromTemplateScreen(
    templateId: String,
    viewModel: GoalViewModel,
    onGoalSaved: () -> Unit,
    onBackClick: () -> Unit
) {
    val template = remember { GoalTemplateProvider.getTemplateById(templateId) }

    if (template == null) {
        // Template not found, go back
        LaunchedEffect(Unit) {
            onBackClick()
        }
        return
    }

    val focusManager = LocalFocusManager.current
    // Initialize form with template data
    var title by remember { mutableStateOf(TextFieldValue(template.title)) }
    var description by remember { mutableStateOf(TextFieldValue(template.description)) }
    var notes by remember { mutableStateOf(TextFieldValue("")) }
    var selectedCategory by remember { mutableStateOf(template.category) }
    var selectedTimeline by remember { mutableStateOf(template.suggestedTimeline) }

    // Calculate default due date based on timeline
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val defaultDueDate = remember(template.suggestedTimeline) {
        when (template.suggestedTimeline) {
            GoalTimeline.SHORT_TERM -> today.plus(3, DateTimeUnit.MONTH)
            GoalTimeline.MID_TERM -> today.plus(6, DateTimeUnit.MONTH)
            GoalTimeline.LONG_TERM -> today.plus(12, DateTimeUnit.MONTH)
        }
    }
    var dueDate by remember { mutableStateOf(defaultDueDate) }
    var dueDateText by remember { mutableStateOf(defaultDueDate.toString()) }

    // Initialize milestones from template
    var milestones by remember {
        mutableStateOf(
            template.suggestedMilestones.map { milestone ->
                NewMilestone(
                    title = TextFieldValue(milestone),
                    dueDate = TextFieldValue("")
                )
            }
        )
    }

    var isFormValid by remember { mutableStateOf(true) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = defaultDueDate.toEpochDays() * 24 * 60 * 60 * 1000L
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
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.modernColors.background,
                    titleContentColor = MaterialTheme.modernColors.textPrimary,
                ),
                title = {
                    Column {
                        Text(
                            "Customize Goal",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            "Based on: ${template.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = template.category.backgroundColor()
                        )
                    }
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
                                id = Uuid.random().toString(),
                                title = m.title.text.trim(),
                                dueDate = try {
                                    LocalDate.parse(m.dueDate.text)
                                } catch (_: Exception) {
                                    null
                                },
                                isCompleted = false
                            )
                        }.filter { it.title.isNotBlank() },
                        notes = notes.text.trim(),
                        createdAt = currentTime,
                        completionRate = 0f,
                        isArchived = false
                    )
                    viewModel.createGoal(goal)
                    onGoalSaved()
                }
            } else {
                {}
            }

            ExtendedFloatingActionButton(
                onClick = fabOnClick,
                text = { Text("Create Goal") },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save"
                    )
                },
                expanded = isFormValid,
                shape = RoundedCornerShape(16.dp),
                containerColor = if (isFormValid) template.category.backgroundColor() else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isFormValid) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
