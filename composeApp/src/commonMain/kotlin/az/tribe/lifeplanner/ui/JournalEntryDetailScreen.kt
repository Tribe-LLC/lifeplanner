package az.tribe.lifeplanner.ui

import androidx.compose.animation.animateColorAsState
import co.touchlab.kermit.Logger
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.data.network.AiProxyService
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.JournalEntry
import az.tribe.lifeplanner.ui.journal.JournalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalEntryDetailScreen(
    entryId: String,
    viewModel: JournalViewModel = koinViewModel(),
    aiProxy: AiProxyService = koinInject(),
    onBackClick: () -> Unit,
    onNavigateToGoal: (String) -> Unit = {}
) {
    val entries by viewModel.entries.collectAsState()
    val entry = entries.find { it.id == entryId }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.loadEntries()
    }

    if (entry == null) {
        EntryNotFoundState(onBackClick = onBackClick)
        return
    }

    val moodColor = getMoodColor(entry.mood)
    val animatedMoodColor by animateColorAsState(
        targetValue = moodColor,
        label = "mood_color"
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text("Journal Entry") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options"
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Edit, contentDescription = null)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showEditSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
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
                .padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Header with Mood
            item {
                JournalEntryHeroHeader(
                    entry = entry,
                    moodColor = animatedMoodColor
                )
            }

            // Content Card
            item {
                JournalContentCard(content = entry.content)
            }

            // Tags Section
            if (entry.tags.isNotEmpty()) {
                item {
                    JournalTagsSection(tags = entry.tags)
                }
            }

            // Prompt Used Section
            if (!entry.promptUsed.isNullOrBlank()) {
                item {
                    PromptUsedCard(prompt = entry.promptUsed)
                }
            }

            // Linked Goal Section
            if (!entry.linkedGoalId.isNullOrBlank()) {
                item {
                    LinkedGoalCard(
                        goalId = entry.linkedGoalId,
                        onClick = { onNavigateToGoal(entry.linkedGoalId) }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        DeleteEntryDialog(
            onConfirm = {
                viewModel.deleteEntry(entryId)
                showDeleteDialog = false
                onBackClick()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    // Edit Bottom Sheet
    if (showEditSheet) {
        EditJournalEntryBottomSheet(
            entry = entry,
            aiProxy = aiProxy,
            onDismiss = { showEditSheet = false },
            onSave = { title, content, mood, tags ->
                viewModel.updateEntry(
                    id = entryId,
                    title = title,
                    content = content,
                    mood = mood,
                    tags = tags
                )
                showEditSheet = false
            }
        )
    }
}

@Composable
private fun JournalEntryHeroHeader(
    entry: JournalEntry,
    moodColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        moodColor.copy(alpha = 0.2f),
                        Color.Transparent
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Large Mood Emoji
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(moodColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.mood.emoji,
                    fontSize = 48.sp
                )
            }

            // Title
            Text(
                text = entry.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Date
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDetailDate(entry.date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Mood Label
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = moodColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = entry.mood.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = moodColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun JournalContentCard(content: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 28.sp
            )
        }
    }
}

@Composable
private fun JournalTagsSection(tags: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Tag,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tags) { tag ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "#$tag",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptUsedCard(prompt: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Prompt Used",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LinkedGoalCard(
    goalId: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Linked Goal",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "View related goal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EntryNotFoundState(onBackClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Entry not found",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "This journal entry may have been deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onBackClick) {
                Text("Go Back")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteEntryDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Entry?") },
        text = { Text("This action cannot be undone. Are you sure you want to delete this journal entry?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditJournalEntryBottomSheet(
    entry: JournalEntry,
    aiProxy: AiProxyService,
    onDismiss: () -> Unit,
    onSave: (String, String, Mood, List<String>) -> Unit
) {
    var title by remember { mutableStateOf(entry.title) }
    var content by remember { mutableStateOf(entry.content) }
    var selectedMood by remember { mutableStateOf(entry.mood) }
    var tagsText by remember { mutableStateOf(entry.tags.joinToString(", ")) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Check if entry has linked goal or habit
    val hasLinkedItem = !entry.linkedGoalId.isNullOrBlank() || !entry.linkedHabitId.isNullOrBlank()
    val canUseAi = title.isNotBlank() && hasLinkedItem

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Edit Entry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close"
                    )
                }
            }

            // Content
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Mood Picker
                item {
                    Text(
                        "How are you feeling?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    MoodPicker(
                        selectedMood = selectedMood,
                        onMoodSelected = { selectedMood = it }
                    )
                }

                // Title field
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // AI Generate Button (only if linked to goal/habit)
                if (hasLinkedItem) {
                    item {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    isGeneratingAi = true
                                    try {
                                        val result = generateAiContentForEdit(
                                            aiProxy = aiProxy,
                                            mood = selectedMood,
                                            userTitle = title,
                                            prompt = entry.promptUsed ?: ""
                                        )
                                        result?.let { (generatedContent, generatedTags) ->
                                            content = generatedContent
                                            if (generatedTags.isNotEmpty()) {
                                                tagsText = generatedTags.joinToString(", ")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Logger.e("JournalEntryDetail") { "AI content regeneration failed: ${e.message}" }
                                    } finally {
                                        isGeneratingAi = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canUseAi && !isGeneratingAi,
                            shape = RoundedCornerShape(12.dp),
                            border = ButtonDefaults.outlinedButtonBorder(enabled = canUseAi)
                        ) {
                            if (isGeneratingAi) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generating...")
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Write with AI")
                            }
                        }
                    }
                }

                // Content field
                item {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Your thoughts") },
                        minLines = 5,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Tags field
                item {
                    OutlinedTextField(
                        value = tagsText,
                        onValueChange = { tagsText = it },
                        label = { Text("Tags (comma separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Save button
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && content.isNotBlank()) {
                                val tags = tagsText.split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotBlank() }
                                onSave(title, content, selectedMood, tags)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        enabled = title.isNotBlank() && content.isNotBlank(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Save Changes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * Generates content and tags for editing an entry using Gemini structured output
 */
private suspend fun generateAiContentForEdit(
    aiProxy: AiProxyService,
    mood: Mood,
    userTitle: String,
    prompt: String
): Pair<String, List<String>>? = withContext(Dispatchers.IO) {
    try {
        val aiPrompt = """
You are a personal journaling assistant helping someone write a journal entry.

User's current mood: ${mood.displayName} (${mood.emoji})
Entry title: "$userTitle"
${if (prompt.isNotBlank()) "Original prompt: \"$prompt\"" else ""}

Generate a personal, first-person journal entry (2-3 paragraphs) that:
- Matches the given title
- Reflects the user's current mood authentically
- Is warm, honest, and introspective
- Feels personal and genuine, not generic

Also suggest 2-4 relevant tags (single words, no hashtags).
""".trimIndent()

        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("content") { put("type", "string") }
                putJsonObject("tags") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("content"))
                add(JsonPrimitive("tags"))
            }
        }

        val responseText = aiProxy.generateStructuredJson(aiPrompt, schema)

        val json = Json { ignoreUnknownKeys = true }
        val entryJson = json.parseToJsonElement(responseText).jsonObject
        val generatedContent = entryJson["content"]?.jsonPrimitive?.contentOrNull
        val generatedTags = entryJson["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
        if (generatedContent != null) {
            Pair(generatedContent, generatedTags)
        } else null
    } catch (e: Exception) {
        Logger.e("JournalEntryDetail") { "AI journal regeneration failed: ${e.message}" }
        null
    }
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

private fun formatDetailDate(date: kotlinx.datetime.LocalDate): String {
    val dayOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )
    val dayIndex = (date.dayOfWeek.ordinal) % 7
    return "${dayOfWeek[dayIndex]}, ${months[date.monthNumber - 1]} ${date.dayOfMonth}, ${date.year}"
}
