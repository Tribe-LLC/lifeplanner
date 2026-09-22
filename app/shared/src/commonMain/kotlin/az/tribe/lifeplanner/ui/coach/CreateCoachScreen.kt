package az.tribe.lifeplanner.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.CaretDown
import com.adamglin.phosphoricons.regular.CaretUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.CoachCharacteristics
import az.tribe.lifeplanner.domain.model.CoachColors
import az.tribe.lifeplanner.domain.model.CoachIcons
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.CustomCoach
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.adamglin.phosphoricons.regular.Trash
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog

/**
 * Screen for creating or editing a custom coach
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateCoachScreen(
    coachToEdit: CustomCoach? = null,
    templateCoach: CoachPersona? = null,
    onNavigateBack: () -> Unit,
    /** Only supplied when editing, since there is nothing to delete while creating. */
    onDeleteCoach: (() -> Unit)? = null,
    onCoachSaved: (CustomCoach) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isEditing = coachToEdit != null

    // Form state
    var name by remember {
        mutableStateOf(coachToEdit?.name ?: templateCoach?.name ?: "")
    }
    var selectedIcon by remember {
        mutableStateOf(coachToEdit?.icon ?: templateCoach?.emoji ?: CoachIcons.PRESETS.first())
    }
    var selectedColor by remember {
        mutableStateOf(
            coachToEdit?.let { coach ->
                CoachColors.PRESETS.find { it.backgroundColor == coach.iconBackgroundColor }
            } ?: CoachColors.PRESETS.first()
        )
    }
    val selectedCharacteristics = remember {
        mutableStateListOf<String>().apply {
            coachToEdit?.characteristics?.let { addAll(it) }
        }
    }
    var systemPrompt by remember {
        mutableStateOf(coachToEdit?.systemPrompt ?: templateCoach?.let {
            "You are ${it.name}, a ${it.title}. ${it.personality}. Your specialties include: ${it.specialties.joinToString(", ")}."
        } ?: "")
    }
    var showAdvanced by remember { mutableStateOf(coachToEdit?.systemPrompt?.isNotEmpty() == true) }
    var showIconPicker by remember { mutableStateOf(false) }

    val isFormValid = name.isNotBlank() && selectedIcon.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEditing) "Edit Coach" else "Create Coach")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            PhosphorIcons.Regular.ArrowLeft,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isEditing && onDeleteCoach != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                PhosphorIcons.Regular.Trash,
                                contentDescription = "Delete coach",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Preview Card
            CoachPreviewCard(
                name = name.ifBlank { "Your Coach" },
                icon = selectedIcon,
                color = selectedColor,
                characteristics = selectedCharacteristics.toList()
            )

            // Name Input
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Coach Name") },
                placeholder = { Text("e.g., Max, Coach Sarah") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Icon Selection
            Column {
                Text(
                    text = "Choose Icon",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Current icon preview
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                color = parseColor(selectedColor.backgroundColor),
                                shape = CircleShape
                            )
                            .clickable { showIconPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = selectedIcon,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }

                    // Quick icon selection
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(CoachIcons.PRESETS.take(8)) { icon ->
                            IconChip(
                                icon = icon,
                                isSelected = icon == selectedIcon,
                                onClick = { selectedIcon = icon }
                            )
                        }
                        item {
                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { showIconPicker = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("...")
                                }
                            }
                        }
                    }
                }
            }

            // Color Selection
            Column {
                Text(
                    text = "Choose Color",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CoachColors.PRESETS) { colorPreset ->
                        ColorChip(
                            colorPreset = colorPreset,
                            isSelected = colorPreset == selectedColor,
                            onClick = { selectedColor = colorPreset }
                        )
                    }
                }
            }

            // Characteristics Selection
            Column {
                Text(
                    text = "Personality Traits",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Select traits that define your coach's personality",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CoachCharacteristics.PRESETS.forEach { characteristic ->
                        CharacteristicChip(
                            characteristic = characteristic,
                            isSelected = selectedCharacteristics.contains(characteristic.id),
                            onClick = {
                                if (selectedCharacteristics.contains(characteristic.id)) {
                                    selectedCharacteristics.remove(characteristic.id)
                                } else {
                                    selectedCharacteristics.add(characteristic.id)
                                }
                            }
                        )
                    }
                }
            }

            // Advanced Options (System Prompt)
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvanced = !showAdvanced }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Advanced Options",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        if (showAdvanced) PhosphorIcons.Regular.CaretUp else PhosphorIcons.Regular.CaretDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (showAdvanced) {
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        label = { Text("Custom Instructions") },
                        placeholder = { Text("Write specific instructions for how your coach should behave...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        shape = RoundedCornerShape(12.dp),
                        supportingText = {
                            Text("Optional: Write custom instructions for your coach's personality and expertise")
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    val coach = CustomCoach(
                        id = coachToEdit?.id ?: "",
                        name = name.trim(),
                        icon = selectedIcon,
                        iconBackgroundColor = selectedColor.backgroundColor,
                        iconAccentColor = selectedColor.accentColor,
                        systemPrompt = systemPrompt.trim(),
                        characteristics = selectedCharacteristics.toList(),
                        isFromTemplate = templateCoach != null,
                        templateId = templateCoach?.id,
                        createdAt = coachToEdit?.createdAt ?: now,
                        updatedAt = if (isEditing) now else null
                    )
                    onCoachSaved(coach)
                },
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isEditing) "Save Changes" else "Create Coach",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Icon Picker Bottom Sheet
    if (showIconPicker) {
        IconPickerBottomSheet(
            selectedIcon = selectedIcon,
            onIconSelected = {
                selectedIcon = it
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }

    if (showDeleteConfirm && onDeleteCoach != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this coach?") },
            text = { Text("This cannot be undone. Conversations you have already had with this coach are kept.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteCoach()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

}
