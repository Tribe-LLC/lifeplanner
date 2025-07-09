package az.tribe.lifeplanner.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.ui.theme.modernColors
import kotlinx.datetime.LocalDate


@Composable
fun CompleteGoalDialog(
    goalTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Complete Goal?",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.modernColors.textPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Are you ready to mark \"$goalTitle\" as completed?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.modernColors.textSecondary
                )
                Text(
                    text = "🎉 Congratulations on achieving your goal!",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF4CAF50)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("🎉 Complete Goal!", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Not Yet")
            }
        }
    )
}


@Composable
fun DeleteGoalDialog(
    goalTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Delete Goal?",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.modernColors.textPrimary
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"$goalTitle\"? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.modernColors.textSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Delete", color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun UpdateProgressDialog(
    currentProgress: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var progressText by remember { mutableStateOf(currentProgress.toString()) }
    var sliderValue by remember { mutableStateOf(currentProgress.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Update Progress",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.modernColors.textPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Set your progress percentage (0-100%)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.modernColors.textSecondary
                )

                // Slider
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${sliderValue.toInt()}%",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.modernColors.primary
                    )

                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            sliderValue = it
                            progressText = it.toInt().toString()
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Text Input
                OutlinedTextField(
                    value = progressText,
                    onValueChange = { newValue ->
                        progressText = newValue
                        newValue.toIntOrNull()?.let { intValue ->
                            if (intValue in 0..100) {
                                sliderValue = intValue.toFloat()
                            }
                        }
                    },
                    label = { Text("Progress %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    suffix = { Text("%") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val progress = progressText.toIntOrNull()?.coerceIn(0, 100) ?: currentProgress
                    onConfirm(progress)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.modernColors.primary
                ),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NotesDialog(
    currentNotes: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var notesText by remember { mutableStateOf(TextFieldValue(currentNotes)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Notes",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.modernColors.textPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Add notes to track your thoughts, plans, or important details about this goal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.modernColors.textSecondary
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    minLines = 5,
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(notesText.text) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.modernColors.primary
                ),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddMilestoneDialog(
    onAdd: (String, LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    var titleText by remember { mutableStateOf(TextFieldValue("")) }
    var dueDateText by remember { mutableStateOf("") }
    var hasDueDate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Milestone",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.modernColors.textPrimary
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Break down your goal into smaller, achievable milestones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.modernColors.textSecondary
                )

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Milestone title *") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = titleText.text.isBlank()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = hasDueDate,
                        onCheckedChange = { hasDueDate = it }
                    )
                    Text(
                        text = "Set due date",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.modernColors.textPrimary
                    )
                }

                if (hasDueDate) {
                    OutlinedTextField(
                        value = dueDateText,
                        onValueChange = { dueDateText = it },
                        label = { Text("Due date (yyyy-mm-dd)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("2025-12-31") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (titleText.text.isNotBlank()) {
                        val dueDate = if (hasDueDate && dueDateText.isNotBlank()) {
                            try {
                                LocalDate.parse(dueDateText)
                            } catch (e: Exception) {
                                null
                            }
                        } else null

                        onAdd(titleText.text, dueDate)
                    }
                },
                enabled = titleText.text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.modernColors.primary
                ),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Add Milestone")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Cancel")
            }
        }
    )
}