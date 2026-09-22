package az.tribe.lifeplanner.ui.coach

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.collectAsState
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
import az.tribe.lifeplanner.domain.model.CoachGroup
import az.tribe.lifeplanner.domain.model.CoachGroupMember
import az.tribe.lifeplanner.data.repository.BuiltinCoachStore
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.CoachType
import az.tribe.lifeplanner.domain.model.CustomCoach
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.adamglin.phosphoricons.regular.Trash
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog

/**
 * Screen for creating or editing a coach group
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    groupToEdit: CoachGroup? = null,
    customCoaches: List<CustomCoach>,
    onNavigateBack: () -> Unit,
    /** Only supplied when editing, since there is nothing to delete while creating. */
    onDeleteGroup: (() -> Unit)? = null,
    onGroupSaved: (CoachGroup) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isEditing = groupToEdit != null
    val builtinCoaches by BuiltinCoachStore.coaches.collectAsState()
    val allBuiltinCoaches = builtinCoaches.ifEmpty { CoachPersona.ALL_COACHES }

    // Form state
    var name by remember { mutableStateOf(groupToEdit?.name ?: "") }
    var selectedIcon by remember { mutableStateOf(groupToEdit?.icon ?: "👥") }
    var description by remember { mutableStateOf(groupToEdit?.description ?: "") }

    // Selected members - using a sealed class approach to handle both types
    val selectedBuiltinCoaches = remember {
        mutableStateListOf<String>().apply {
            groupToEdit?.members?.filter { it.coachType == CoachType.BUILTIN }
                ?.forEach { add(it.coachId) }
        }
    }
    val selectedCustomCoaches = remember {
        mutableStateListOf<String>().apply {
            groupToEdit?.members?.filter { it.coachType == CoachType.CUSTOM }
                ?.forEach { add(it.coachId) }
        }
    }

    var showIconPicker by remember { mutableStateOf(false) }

    val totalSelectedCount = selectedBuiltinCoaches.size + selectedCustomCoaches.size
    val isFormValid = name.isNotBlank() && totalSelectedCount >= 2

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEditing) "Edit Group" else "Create Group")
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
                    if (isEditing && onDeleteGroup != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                PhosphorIcons.Regular.Trash,
                                contentDescription = "Delete group",
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
            GroupPreviewCard(
                name = name.ifBlank { "Your Group" },
                icon = selectedIcon,
                selectedBuiltinCoaches = selectedBuiltinCoaches.mapNotNull { id ->
                    allBuiltinCoaches.find { it.id == id }
                },
                selectedCustomCoaches = customCoaches.filter { it.id in selectedCustomCoaches }
            )

            // Name Input
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group Name") },
                placeholder = { Text("e.g., Career Council, Wellness Team") },
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
                                color = MaterialTheme.colorScheme.primaryContainer,
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
                    val groupIcons = listOf("👥", "🤝", "💬", "🎯", "⭐", "🔥", "💡", "🧠")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(groupIcons) { icon ->
                            val isSelected = icon == selectedIcon
                            val backgroundColor by animateColorAsState(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )

                            Surface(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { selectedIcon = icon },
                                shape = CircleShape,
                                color = backgroundColor
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = icon, style = MaterialTheme.typography.titleMedium)
                                }
                            }
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

            // Description (optional)
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                placeholder = { Text("What's this group about?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Coach Selection - Built-in Coaches
            Column {
                Text(
                    text = "Select Coaches ($totalSelectedCount selected)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Choose at least 2 coaches for your group",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Built-in coaches section
                if (allBuiltinCoaches.isNotEmpty()) {
                    Text(
                        text = "Built-in Coaches",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allBuiltinCoaches.forEach { coach ->
                            val isSelected = selectedBuiltinCoaches.contains(coach.id)
                            CoachSelectionItem(
                                name = coach.name,
                                subtitle = coach.title,
                                emoji = coach.emoji,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        selectedBuiltinCoaches.remove(coach.id)
                                    } else {
                                        selectedBuiltinCoaches.add(coach.id)
                                    }
                                }
                            )
                        }
                    }
                }

                // Custom coaches section
                if (customCoaches.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your Custom Coaches",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customCoaches.forEach { coach ->
                            val isSelected = selectedCustomCoaches.contains(coach.id)
                            CoachSelectionItem(
                                name = coach.name,
                                subtitle = coach.characteristics.firstOrNull()?.let { id ->
                                    az.tribe.lifeplanner.domain.model.CoachCharacteristics.getById(id)?.name
                                } ?: "Custom Coach",
                                emoji = coach.icon,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        selectedCustomCoaches.remove(coach.id)
                                    } else {
                                        selectedCustomCoaches.add(coach.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            Button(
                onClick = {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

                    // Build members list
                    val members = mutableListOf<CoachGroupMember>()
                    var order = 0

                    selectedBuiltinCoaches.forEach { coachId ->
                        members.add(
                            CoachGroupMember(
                                id = "",
                                coachType = CoachType.BUILTIN,
                                coachId = coachId,
                                displayOrder = order++
                            )
                        )
                    }

                    selectedCustomCoaches.forEach { coachId ->
                        members.add(
                            CoachGroupMember(
                                id = "",
                                coachType = CoachType.CUSTOM,
                                coachId = coachId,
                                displayOrder = order++
                            )
                        )
                    }

                    val group = CoachGroup(
                        id = groupToEdit?.id ?: "",
                        name = name.trim(),
                        icon = selectedIcon,
                        description = description.trim(),
                        members = members,
                        createdAt = groupToEdit?.createdAt ?: now,
                        updatedAt = if (isEditing) now else null
                    )
                    onGroupSaved(group)
                },
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (isEditing) "Save Changes" else "Create Group",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Icon Picker Bottom Sheet
    if (showIconPicker) {
        GroupIconPickerBottomSheet(
            selectedIcon = selectedIcon,
            onIconSelected = {
                selectedIcon = it
                showIconPicker = false
            },
            onDismiss = { showIconPicker = false }
        )
    }

    if (showDeleteConfirm && onDeleteGroup != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this group?") },
            text = { Text("This cannot be undone. The coaches in the group are not deleted, only the group itself.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteGroup()
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
