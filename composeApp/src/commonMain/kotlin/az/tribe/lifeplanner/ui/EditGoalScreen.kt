package az.tribe.lifeplanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.enum.GoalCategory
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
fun EditGoalScreen(
    goalId: String,
    viewModel: GoalViewModel,
    onGoalSaved: () -> Unit,
    onBackClick: () -> Unit
) {
    val goals by viewModel.goals.collectAsState()
    val existingGoal = goals.find { it.id == goalId }

    // If goal not found, show error state
    if (existingGoal == null) {
        LaunchedEffect(Unit) {
            viewModel.loadAllGoals()
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // Initialize state with existing goal data
    var title by remember(existingGoal) { mutableStateOf(TextFieldValue(existingGoal.title)) }
    var description by remember(existingGoal) { mutableStateOf(TextFieldValue(existingGoal.description)) }
    var notes by remember(existingGoal) { mutableStateOf(TextFieldValue(existingGoal.notes)) }
    var selectedCategory by remember(existingGoal) { mutableStateOf(existingGoal.category) }
    var selectedTimeline by remember(existingGoal) { mutableStateOf(existingGoal.timeline) }
    var dueDate by remember(existingGoal) { mutableStateOf(existingGoal.dueDate) }
    var dueDateText by remember(existingGoal) { mutableStateOf(existingGoal.dueDate.toString()) }

    // Convert existing milestones to editable format
    var milestones by remember(existingGoal) {
        mutableStateOf(
            existingGoal.milestones.map { milestone ->
                EditableMilestone(
                    id = milestone.id,
                    title = TextFieldValue(milestone.title),
                    dueDate = TextFieldValue(milestone.dueDate?.toString() ?: ""),
                    isCompleted = milestone.isCompleted
                )
            }
        )
    }

    var isFormValid by remember { mutableStateOf(true) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = existingGoal.dueDate.toEpochDays() * 24 * 60 * 60 * 1000L
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
                        "Edit Goal",
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
            val fabOnClick = if (isFormValid) {
                {
                    val updatedGoal = existingGoal.copy(
                        title = title.text.trim(),
                        description = description.text.trim(),
                        category = selectedCategory,
                        timeline = selectedTimeline,
                        dueDate = dueDate,
                        notes = notes.text.trim(),
                        milestones = milestones.map { m ->
                            Milestone(
                                id = m.id,
                                title = m.title.text.trim(),
                                dueDate = try {
                                    if (m.dueDate.text.isNotBlank()) LocalDate.parse(m.dueDate.text) else null
                                } catch (_: Exception) {
                                    null
                                },
                                isCompleted = m.isCompleted
                            )
                        }
                    )
                    viewModel.updateGoal(updatedGoal)
                    onGoalSaved()
                }
            } else {
                {}
            }

            ExtendedFloatingActionButton(
                onClick = fabOnClick,
                text = { Text("Save Changes") },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save"
                    )
                },
                expanded = isFormValid,
                shape = RoundedCornerShape(16.dp),
                containerColor = if (isFormValid) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isFormValid) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    milestones = milestones + EditableMilestone(
                        id = Uuid.random().toString(),
                        title = TextFieldValue(""),
                        dueDate = TextFieldValue(""),
                        isCompleted = false
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (milestones.isEmpty()) {
                    EmptyMilestoneCard {
                        milestones = milestones + EditableMilestone(
                            id = Uuid.random().toString(),
                            title = TextFieldValue(""),
                            dueDate = TextFieldValue(""),
                            isCompleted = false
                        )
                    }
                }
            }

            // Milestone Items
            items(milestones.size) { index ->
                val milestone = milestones[index]
                EditMilestoneItem(
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

data class EditableMilestone(
    val id: String,
    val title: TextFieldValue,
    val dueDate: TextFieldValue,
    val isCompleted: Boolean
)

@Composable
fun EditMilestoneItem(
    milestone: EditableMilestone,
    onMilestoneChange: (EditableMilestone) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Milestone",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.modernColors.textSecondary
                    )
                    if (milestone.isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "Completed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

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
