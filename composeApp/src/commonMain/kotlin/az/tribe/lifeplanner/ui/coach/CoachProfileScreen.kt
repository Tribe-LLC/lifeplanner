package az.tribe.lifeplanner.ui.coach

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.repository.CoachRepository
import az.tribe.lifeplanner.ui.components.SyncStatusIndicator
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachProfileScreen(
    coachId: String,
    onNavigateBack: () -> Unit,
    coachRepository: CoachRepository = koinInject(),
    syncManager: SyncManager = koinInject()
) {
    val coach = remember(coachId) { CoachPersona.getById(coachId) }
    val scope = rememberCoroutineScope()
    var personaText by remember { mutableStateOf("") }
    var hasExistingOverride by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    LaunchedEffect(coachId) {
        val existing = coachRepository.getPersonaOverride(coachId)
        if (existing != null) {
            personaText = existing
            hasExistingOverride = true
        }
    }

    val bgColor = try {
        Color(("FF" + coach.avatar.backgroundColor.removePrefix("#")).toLong(16))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.primary
    }
    val accentColor = try {
        Color(("FF" + coach.avatar.accentColor.removePrefix("#")).toLong(16))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.tertiary
    }

    val headerGradient = Brush.verticalGradient(
        colors = listOf(bgColor, accentColor, MaterialTheme.colorScheme.surface)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Gradient header with avatar, name, title, category, sync
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerGradient)
                        .statusBarsPadding()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top row: back button + sync indicator
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onNavigateBack,
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                            SyncStatusIndicator(
                                syncStatus = syncManager.syncStatus,
                                onRetryClick = { scope.launch { syncManager.performFullSync() } },
                                compact = true,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }

                        // Avatar circle
                        Surface(
                            modifier = Modifier.size(100.dp),
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            shadowElevation = 8.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = coach.emoji,
                                    style = MaterialTheme.typography.displayLarge
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = coach.name,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                        Text(
                            text = coach.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = coach.category.name,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // Bio
            item {
                Spacer(Modifier.height(8.dp))
                SectionCard(title = "About") {
                    Text(
                        text = coach.profile.bio,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Specialties
            item {
                SectionCard(title = "Specialties") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        coach.specialties.forEach { specialty ->
                            AssistChip(
                                onClick = {},
                                label = { Text(specialty) }
                            )
                        }
                    }
                }
            }

            // Personality
            item {
                SectionCard(title = "Personality") {
                    Text(
                        text = coach.personality,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Fun fact
            item {
                SectionCard(title = "Fun Fact") {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = coach.profile.funFact,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Divider
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            // Custom instructions
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Your Custom Instructions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tell ${coach.name} how you'd like them to behave. This shapes their responses.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = personaText,
                        onValueChange = {
                            personaText = it
                            isSaved = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g., Be more direct, skip the fluff") },
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = if (personaText.isNotEmpty()) {
                            {
                                IconButton(onClick = { personaText = ""; isSaved = false }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                }
                            }
                        } else null
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        if (hasExistingOverride) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        coachRepository.deletePersonaOverride(coachId)
                                        personaText = ""
                                        hasExistingOverride = false
                                        isSaved = false
                                    }
                                }
                            ) {
                                Text("Clear Instructions")
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    if (personaText.isNotBlank()) {
                                        coachRepository.savePersonaOverride(coachId, personaText.trim())
                                        hasExistingOverride = true
                                    } else {
                                        coachRepository.deletePersonaOverride(coachId)
                                        hasExistingOverride = false
                                    }
                                    isSaved = true
                                }
                            },
                            enabled = !isSaved
                        ) {
                            Text(if (isSaved) "Saved" else "Save")
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}
