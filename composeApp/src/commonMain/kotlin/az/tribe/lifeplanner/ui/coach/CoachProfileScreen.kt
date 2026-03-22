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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.data.analytics.Analytics
import az.tribe.lifeplanner.data.sync.SyncManager
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.CoachPost
import az.tribe.lifeplanner.domain.repository.CoachPostRepository
import az.tribe.lifeplanner.domain.repository.CoachRepository
import az.tribe.lifeplanner.ui.components.GlassCard
import az.tribe.lifeplanner.ui.components.SyncStatusIndicator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachProfileScreen(
    coachId: String,
    onNavigateBack: () -> Unit,
    coachRepository: CoachRepository = koinInject(),
    coachPostRepository: CoachPostRepository = koinInject(),
    syncManager: SyncManager = koinInject()
) {
    val coach = remember(coachId) { CoachPersona.getById(coachId) }
    val scope = rememberCoroutineScope()
    var personaText by remember { mutableStateOf("") }
    var hasExistingOverride by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }
    val postsFlow = remember { MutableStateFlow<List<CoachPost>>(emptyList()) }
    val posts by postsFlow.collectAsState()
    var selectedPost by remember { mutableStateOf<CoachPost?>(null) }

    LaunchedEffect(coachId) {
        Analytics.coachProfileViewed(coachId)
        val existing = coachRepository.getPersonaOverride(coachId)
        if (existing != null) {
            personaText = existing
            hasExistingOverride = true
        }
        postsFlow.value = coachPostRepository.getPostsForCoach(coachId)
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

            // Stories — horizontal manga-style cards
            if (posts.isNotEmpty()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                item {
                    Text(
                        text = "${coach.name}'s Stories",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Spacer(Modifier.width(4.dp))
                        posts.forEach { post ->
                            StoryCard(
                                post = post,
                                coachEmoji = coach.emoji,
                                bgColor = bgColor,
                                accentColor = accentColor,
                                onClick = { selectedPost = post }
                            )
                        }
                        Spacer(Modifier.width(4.dp))
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

        // Full-screen story reader overlay
        selectedPost?.let { post ->
            StoryReader(
                post = post,
                coachEmoji = coach.emoji,
                coachName = coach.name,
                bgColor = bgColor,
                accentColor = accentColor,
                onDismiss = { selectedPost = null }
            )
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

// ─── Horizontal story cover card ─────────────────────────────────────

@Composable
private fun StoryCard(
    post: CoachPost,
    coachEmoji: String,
    bgColor: Color,
    accentColor: Color,
    onClick: () -> Unit
) {
    val categoryLabel = when (post.category) {
        "story" -> "Story"
        "tip" -> "Quick Tip"
        "reflection" -> "Reflection"
        "motivation" -> "Motivation"
        else -> "Post"
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier
            .width(160.dp)
            .height(200.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(bgColor, accentColor.copy(alpha = 0.7f))
                    ),
                    RoundedCornerShape(16.dp)
                )
        ) {
            // Decorative emoji watermark
            Text(
                text = post.emoji,
                fontSize = 64.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .alpha(0.15f)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Category badge
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = categoryLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Column {
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(coachEmoji, fontSize = 12.sp)
                        Text(
                            text = "${post.readTimeMinutes} min",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

// ─── Full-screen manga-style story reader ────────────────────────────

@Composable
private fun StoryReader(
    post: CoachPost,
    coachEmoji: String,
    coachName: String,
    bgColor: Color,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    // Split content into paragraphs (panels)
    val panels = remember(post) {
        post.content.split("\n\n").filter { it.isNotBlank() }
    }
    var currentPanel by remember { mutableStateOf(0) }
    val totalPanels = panels.size

    val panelAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(300),
        label = "panelFade"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -40 && currentPanel < totalPanels - 1) {
                        currentPanel++
                    } else if (dragAmount > 40 && currentPanel > 0) {
                        currentPanel--
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Tap right half → next, left half → previous
                    if (offset.x > size.width / 2) {
                        if (currentPanel < totalPanels - 1) currentPanel++
                        else onDismiss()
                    } else {
                        if (currentPanel > 0) currentPanel--
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar: close button + progress
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Progress segments
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(totalPanels) { index ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (index <= currentPanel) accentColor
                                    else Color.White.copy(alpha = 0.2f)
                                )
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                ) {
                    Icon(Icons.Rounded.Close, "Close", modifier = Modifier.size(20.dp))
                }
            }

            // Title card (first panel gets the title)
            if (currentPanel == 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(post.emoji, fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = post.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "by $coachName",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Speech bubble panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .alpha(panelAlpha)
            ) {
                // Speech bubble
                val bubbleColor = accentColor.copy(alpha = 0.12f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            // Rounded bubble background
                            drawRoundRect(
                                color = bubbleColor,
                                cornerRadius = CornerRadius(20.dp.toPx()),
                                size = size
                            )
                            // Small triangle pointer at bottom-left
                            val trianglePath = Path().apply {
                                moveTo(40.dp.toPx(), size.height)
                                lineTo(28.dp.toPx(), size.height + 12.dp.toPx())
                                lineTo(56.dp.toPx(), size.height)
                                close()
                            }
                            drawPath(trianglePath, color = bubbleColor)
                        }
                        .padding(20.dp)
                ) {
                    Text(
                        text = panels[currentPanel],
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 26.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Coach avatar + panel indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Coach avatar
                Surface(
                    shape = CircleShape,
                    color = bgColor.copy(alpha = 0.3f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(coachEmoji, fontSize = 22.sp)
                    }
                }
                Column {
                    Text(
                        text = coachName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${currentPanel + 1} of $totalPanels",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
                Spacer(Modifier.weight(1f))
                // Tap hint
                Text(
                    text = if (currentPanel < totalPanels - 1) "Tap to continue" else "Tap to close",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
