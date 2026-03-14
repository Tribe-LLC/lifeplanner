package az.tribe.lifeplanner.ui.focus

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.enum.AmbientSound
import az.tribe.lifeplanner.domain.enum.FocusTheme
import az.tribe.lifeplanner.domain.enum.Mood
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.ui.components.AmbientSoundPlayer
import az.tribe.lifeplanner.ui.components.CelebrationOverlay
import az.tribe.lifeplanner.ui.components.CelebrationType
import az.tribe.lifeplanner.ui.components.GlassCard
import az.tribe.lifeplanner.ui.components.rememberAmbientSoundPlayer
import az.tribe.lifeplanner.ui.components.rememberCelebrationSoundPlayer
import az.tribe.lifeplanner.ui.components.rememberHapticManager
import az.tribe.lifeplanner.ui.theme.LifePlannerDesign
import az.tribe.lifeplanner.ui.theme.gradientColors
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    onNavigateBack: () -> Unit,
    goalId: String? = null,
    milestoneId: String? = null,
    focusViewModel: FocusViewModel = koinViewModel()
) {
    val timerState by focusViewModel.timerState.collectAsState()
    val selectedAmbientSound by focusViewModel.selectedAmbientSound.collectAsState()
    val selectedFocusTheme by focusViewModel.selectedFocusTheme.collectAsState()
    val selectedMood by focusViewModel.selectedMood.collectAsState()
    val soundPlayer = rememberCelebrationSoundPlayer()
    val hapticManager = rememberHapticManager()
    val ambientSoundPlayer = rememberAmbientSoundPlayer()

    // Pre-select if navigated from GoalDetail; default to free flow if no milestone
    LaunchedEffect(goalId, milestoneId) {
        if (goalId != null && milestoneId != null) {
            focusViewModel.preSelectGoalAndMilestone(goalId, milestoneId)
        } else if (goalId == null && milestoneId == null) {
            // No specific milestone — default to free flow for quick start
            focusViewModel.setTimerMode(true)
        }
    }

    // Handle celebration on completion
    LaunchedEffect(timerState) {
        if (timerState == TimerState.COMPLETED) {
            hapticManager.celebration()
            soundPlayer.play(CelebrationType.GOAL_COMPLETED)
        }
    }

    // Ambient sound lifecycle tied to timer state
    LaunchedEffect(timerState, selectedAmbientSound) {
        when (timerState) {
            TimerState.RUNNING -> ambientSoundPlayer.play(selectedAmbientSound)
            TimerState.PAUSED -> ambientSoundPlayer.stop()
            TimerState.COMPLETED, TimerState.CANCELLED -> ambientSoundPlayer.stop()
            TimerState.IDLE -> ambientSoundPlayer.stop()
        }
    }

    val isActive = timerState == TimerState.RUNNING || timerState == TimerState.PAUSED

    Scaffold(
        topBar = {
            if (timerState == TimerState.IDLE) {
                TopAppBar(
                    title = {
                        Text(
                            "Focus Timer",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        containerColor = if (isActive) Color.Transparent else MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Animated background during active session
            if (isActive) {
                FocusAnimatedBackground(
                    theme = selectedFocusTheme,
                    mood = selectedMood,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = timerState,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "focusContent"
                ) { state ->
                    when (state) {
                        TimerState.IDLE -> FocusSetupContent(
                            focusViewModel = focusViewModel,
                            onStartFocus = { focusViewModel.startTimer() }
                        )
                        TimerState.RUNNING, TimerState.PAUSED -> FocusActiveContent(
                            focusViewModel = focusViewModel
                        )
                        TimerState.COMPLETED -> FocusCompleteContent(
                            focusViewModel = focusViewModel,
                            onDone = onNavigateBack,
                            onStartAnother = { focusViewModel.resetToSetup() }
                        )
                        TimerState.CANCELLED -> FocusCompleteContent(
                            focusViewModel = focusViewModel,
                            onDone = onNavigateBack,
                            onStartAnother = { focusViewModel.resetToSetup() },
                            wasCancelled = true
                        )
                    }
                }

                // Celebration overlay for completion
                if (timerState == TimerState.COMPLETED) {
                    CelebrationOverlay(
                        type = CelebrationType.GOAL_COMPLETED,
                        message = "Focus Complete!",
                        isVisible = true,
                        onDismiss = { focusViewModel.resetToSetup() }
                    )
                }
            }
        }
    }
}

// ============================================
// SETUP CONTENT — Milestone-first selection
// ============================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusSetupContent(
    focusViewModel: FocusViewModel,
    onStartFocus: () -> Unit
) {
    val milestoneItems by focusViewModel.milestoneItems.collectAsState()
    val selectedMilestone by focusViewModel.selectedMilestone.collectAsState()
    val selectedGoal by focusViewModel.selectedGoal.collectAsState()
    val durationMinutes by focusViewModel.durationMinutes.collectAsState()
    val todaySessionCount by focusViewModel.todaySessionCount.collectAsState()
    val todaySeconds by focusViewModel.todaySeconds.collectAsState()
    val allTimeSessionCount by focusViewModel.allTimeSessionCount.collectAsState()
    val allTimeSeconds by focusViewModel.allTimeSeconds.collectAsState()
    val selectedMood by focusViewModel.selectedMood.collectAsState()
    val selectedAmbientSound by focusViewModel.selectedAmbientSound.collectAsState()
    val selectedFocusTheme by focusViewModel.selectedFocusTheme.collectAsState()
    val isFreeFlow by focusViewModel.isFreeFlow.collectAsState()
    val isCustomDuration by focusViewModel.isCustomDuration.collectAsState()
    val customDurationMinutes by focusViewModel.customDurationMinutes.collectAsState()

    // Free flow: milestone is optional; timed: milestone is required
    val canStart = if (isFreeFlow) true else selectedMilestone != null

    // Group milestones by goal for display
    val groupedMilestones = remember(milestoneItems) {
        milestoneItems.groupBy { it.goal }
    }

    // Expandable customize section for free flow
    var showCustomize by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = LifePlannerDesign.Padding.screenHorizontal,
            vertical = 8.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Timer mode toggle — Timed vs Free Flow
        item {
            TimerModeToggle(
                isFreeFlow = isFreeFlow,
                onModeChange = { focusViewModel.setTimerMode(it) }
            )
        }

        if (isFreeFlow) {
            // ── FREE FLOW: Quick-start hero ──

            // Big start button right away
            item {
                Button(
                    onClick = onStartFocus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B35)
                    )
                ) {
                    Icon(
                        Icons.Rounded.AllInclusive,
                        null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Start Focusing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Compact stats inline
            if (todaySeconds > 0 || allTimeSeconds > 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (todaySeconds > 0) {
                            Text(
                                "${formatDuration(todaySeconds)} today",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFFF6B35),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (todaySeconds > 0 && allTimeSeconds > 0) {
                            Text(
                                "  •  ",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (allTimeSeconds > 0) {
                            Text(
                                "${formatDuration(allTimeSeconds)} all time",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Expandable customize section
            item {
                Surface(
                    onClick = { showCustomize = !showCustomize },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                selectedMood?.emoji ?: "😊",
                                fontSize = 18.sp
                            )
                            Text(
                                selectedAmbientSound.icon,
                                fontSize = 18.sp
                            )
                            Text(
                                selectedFocusTheme.icon,
                                fontSize = 18.sp
                            )
                        }
                        Text(
                            if (showCustomize) "Hide options" else "Customize",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (showCustomize) {
                // Mood picker
                item {
                    Text(
                        "How are you feeling?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Mood.entries.forEach { mood ->
                            MoodChip(
                                mood = mood,
                                isSelected = selectedMood == mood,
                                onClick = { focusViewModel.setMood(mood) }
                            )
                        }
                    }
                }

                // Ambient sound
                item {
                    Text(
                        "Ambient Sound",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AmbientSound.entries.forEach { sound ->
                            PickerChip(
                                label = "${sound.icon} ${sound.displayName}",
                                isSelected = selectedAmbientSound == sound,
                                onClick = { focusViewModel.setAmbientSound(sound) }
                            )
                        }
                    }
                }

                // Visual theme
                item {
                    Text(
                        "Visual Theme",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FocusTheme.entries.forEach { theme ->
                            PickerChip(
                                label = "${theme.icon} ${theme.displayName}",
                                isSelected = selectedFocusTheme == theme,
                                onClick = { focusViewModel.setFocusTheme(theme) }
                            )
                        }
                    }
                }
            }

            // Optional milestone picker
            if (milestoneItems.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Focus on a milestone",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "optional",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                groupedMilestones.forEach { (goal, items) ->
                    item(key = "goal_header_${goal.id}") {
                        GoalSectionHeader(goal = goal)
                    }
                    items(items, key = { it.milestone.id }) { item ->
                        MilestonePickerItem(
                            milestoneItem = item,
                            isSelected = selectedMilestone?.id == item.milestone.id,
                            onClick = {
                                focusViewModel.selectMilestoneWithGoal(item.milestone, item.goal)
                            }
                        )
                    }
                }

                item { Spacer(Modifier.height(32.dp)) }
            }
        } else {
            // ── TIMED MODE: Full setup (unchanged) ──

            // Stats card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        if (todaySessionCount > 0 || todaySeconds > 0) {
                            Text(
                                "Today",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem("$todaySessionCount", "sessions")
                                StatItem(formatDuration(todaySeconds), "focused")
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        if (allTimeSessionCount > 0 || allTimeSeconds > 0) {
                            Text(
                                "All Time",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem("$allTimeSessionCount", "sessions")
                                StatItem(formatDuration(allTimeSeconds), "focused")
                            }
                        }
                    }
                }
            }

            // Mood picker
            item {
                Text(
                    "How are you feeling?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Mood.entries.forEach { mood ->
                        MoodChip(
                            mood = mood,
                            isSelected = selectedMood == mood,
                            onClick = { focusViewModel.setMood(mood) }
                        )
                    }
                }
            }

            // Ambient sound picker
            item {
                Text(
                    "Ambient Sound",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AmbientSound.entries.forEach { sound ->
                        PickerChip(
                            label = "${sound.icon} ${sound.displayName}",
                            isSelected = selectedAmbientSound == sound,
                            onClick = { focusViewModel.setAmbientSound(sound) }
                        )
                    }
                }
            }

            // Focus theme picker
            item {
                Text(
                    "Visual Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FocusTheme.entries.forEach { theme ->
                        PickerChip(
                            label = "${theme.icon} ${theme.displayName}",
                            isSelected = selectedFocusTheme == theme,
                            onClick = { focusViewModel.setFocusTheme(theme) }
                        )
                    }
                }
            }

            // Pick a Milestone header
            item {
                Text(
                    "What will you focus on?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (milestoneItems.isEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No milestones to focus on",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Create a goal with milestones to start a focus session",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Milestones grouped by goal
                groupedMilestones.forEach { (goal, items) ->
                    item(key = "goal_header_${goal.id}") {
                        GoalSectionHeader(goal = goal)
                    }
                    items(items, key = { it.milestone.id }) { item ->
                        MilestonePickerItem(
                            milestoneItem = item,
                            isSelected = selectedMilestone?.id == item.milestone.id,
                            onClick = {
                                focusViewModel.selectMilestoneWithGoal(item.milestone, item.goal)
                            }
                        )
                    }
                }
            }

            // Duration (timed only)
            if (milestoneItems.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Duration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    val presets = listOf(15, 25, 30, 45, 60)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            DurationChip(
                                minutes = preset,
                                isSelected = durationMinutes == preset && !isCustomDuration,
                                onClick = { focusViewModel.setDuration(preset) }
                            )
                        }
                        PickerChip(
                            label = "Custom",
                            isSelected = isCustomDuration,
                            onClick = { focusViewModel.toggleCustomDuration() }
                        )
                    }
                }

                if (isCustomDuration) {
                    item {
                        CustomDurationStepper(
                            minutes = customDurationMinutes,
                            onIncrement = { focusViewModel.incrementCustomDuration() },
                            onDecrement = { focusViewModel.decrementCustomDuration() }
                        )
                    }
                }
            }

            // Start Button (timed)
            if (milestoneItems.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onStartFocus,
                        enabled = canStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B35)
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Timer,
                            null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Start Focus",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6B35)
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MoodChip(
    mood: Mood,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) Color(0xFFFF6B35).copy(alpha = 0.15f) else Color.Transparent,
        modifier = if (isSelected) {
            Modifier.border(2.dp, Color(0xFFFF6B35), CircleShape)
        } else {
            Modifier
        }
    ) {
        Text(
            mood.emoji,
            modifier = Modifier.padding(12.dp),
            fontSize = 24.sp
        )
    }
}

@Composable
private fun PickerChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (isSelected) Color(0xFFFF6B35) else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun GoalSectionHeader(goal: Goal) {
    val categoryColors = goal.category.gradientColors()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(categoryColors))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            goal.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MilestonePickerItem(
    milestoneItem: MilestoneItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val categoryColors = milestoneItem.goal.category.gradientColors()
    val borderModifier = if (isSelected) {
        Modifier.border(
            width = 2.dp,
            brush = Brush.horizontalGradient(categoryColors),
            shape = RoundedCornerShape(16.dp)
        )
    } else Modifier

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(borderModifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    milestoneItem.milestone.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isSelected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Rounded.Check,
                    null,
                    tint = categoryColors.first(),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DurationChip(
    minutes: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (isSelected) Color(0xFFFF6B35) else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            "${minutes}m",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun TimerModeToggle(
    isFreeFlow: Boolean,
    onModeChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Timed segment
            Surface(
                onClick = { onModeChange(false) },
                shape = RoundedCornerShape(12.dp),
                color = if (!isFreeFlow) Color(0xFFFF6B35) else Color.Transparent,
                contentColor = if (!isFreeFlow) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Timed",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            // Free Flow segment
            Surface(
                onClick = { onModeChange(true) },
                shape = RoundedCornerShape(12.dp),
                color = if (isFreeFlow) Color(0xFFFF6B35) else Color.Transparent,
                contentColor = if (isFreeFlow) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AllInclusive, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Free Flow",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomDurationStepper(
    minutes: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = onDecrement,
            enabled = minutes > 5,
            shape = CircleShape,
            modifier = Modifier.size(44.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Rounded.Remove, "Decrease", modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(24.dp))
        Text(
            "$minutes min",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF6B35)
        )
        Spacer(Modifier.width(24.dp))
        FilledTonalButton(
            onClick = onIncrement,
            enabled = minutes < 120,
            shape = CircleShape,
            modifier = Modifier.size(44.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Icon(Icons.Rounded.Add, "Increase", modifier = Modifier.size(20.dp))
        }
    }
}

// ============================================
// ACTIVE CONTENT (Timer Running/Paused)
// ============================================

@Composable
private fun FocusActiveContent(
    focusViewModel: FocusViewModel
) {
    val timerState by focusViewModel.timerState.collectAsState()
    val remainingSeconds by focusViewModel.remainingSeconds.collectAsState()
    val progress by focusViewModel.progress.collectAsState()
    val selectedGoal by focusViewModel.selectedGoal.collectAsState()
    val selectedMilestone by focusViewModel.selectedMilestone.collectAsState()
    val elapsedSeconds by focusViewModel.elapsedSeconds.collectAsState()
    val isFreeFlow by focusViewModel.isFreeFlow.collectAsState()

    val isRunning = timerState == TimerState.RUNNING
    val gradientColors = selectedGoal?.category?.gradientColors()
        ?: listOf(Color(0xFFFF6B35), Color(0xFFFFA726))

    // Motivational messages that rotate
    val motivationalTexts = remember {
        listOf(
            "Stay focused, you're doing great!",
            "Deep work builds extraordinary results.",
            "One minute at a time.",
            "Your future self will thank you.",
            "Progress, not perfection.",
            "This is where growth happens.",
            "Stay in the zone.",
            "You've got this!"
        )
    }
    val messageIndex = (elapsedSeconds / 300) % motivationalTexts.size // Changes every 5 min

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LifePlannerDesign.Padding.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        // Goal + Milestone info (compact)
        selectedGoal?.let { goal ->
            Text(
                goal.title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        selectedMilestone?.let { milestone ->
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.12f)
            ) {
                Text(
                    milestone.title,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Progress Ring
        FocusProgressRing(
            progress = progress,
            remainingSeconds = remainingSeconds,
            isRunning = isRunning,
            elapsedSeconds = elapsedSeconds,
            isFreeFlow = isFreeFlow,
            gradientColors = gradientColors,
            size = 240.dp
        )

        Spacer(Modifier.height(24.dp))

        // Motivational text
        Text(
            motivationalTexts[messageIndex],
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.weight(1f))

        // Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel button (small)
            FilledTonalButton(
                onClick = { focusViewModel.cancelTimer() },
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Rounded.Close, "Cancel", modifier = Modifier.size(20.dp))
            }

            // Pause/Resume button (large)
            Button(
                onClick = {
                    if (isRunning) focusViewModel.pauseTimer()
                    else focusViewModel.resumeTimer()
                },
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = gradientColors.first()
                )
            ) {
                Icon(
                    if (isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (isRunning) "Pause" else "Resume",
                    modifier = Modifier.size(32.dp)
                )
            }

            // Third button: Done (free flow) or +5 (timed)
            if (isFreeFlow) {
                FilledTonalButton(
                    onClick = { focusViewModel.completeFreeFlowSession() },
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Rounded.Check, "Done", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            } else {
                FilledTonalButton(
                    onClick = { focusViewModel.addFiveMinutes() },
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("+5", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

// ============================================
// COMPLETE CONTENT
// ============================================

@Composable
private fun FocusCompleteContent(
    focusViewModel: FocusViewModel,
    onDone: () -> Unit,
    onStartAnother: () -> Unit,
    wasCancelled: Boolean = false
) {
    val lastXpEarned by focusViewModel.lastXpEarned.collectAsState()
    val selectedGoal by focusViewModel.selectedGoal.collectAsState()
    val selectedMilestone by focusViewModel.selectedMilestone.collectAsState()
    val elapsedSeconds by focusViewModel.elapsedSeconds.collectAsState()
    val selectedMood by focusViewModel.selectedMood.collectAsState()

    val elapsedDisplay = formatDuration(elapsedSeconds)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LifePlannerDesign.Padding.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Completion icon
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    brush = if (wasCancelled)
                        Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant))
                    else
                        Brush.horizontalGradient(listOf(Color(0xFF4CAF50), Color(0xFF81C784)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (wasCancelled) Icons.Rounded.Schedule else Icons.Rounded.Check,
                null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            if (wasCancelled) "Session Ended" else "Focus Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        selectedMilestone?.let { milestone ->
            Text(
                milestone.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(8.dp))

        // Mood + duration summary
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            selectedMood?.let { mood ->
                Text(
                    mood.emoji,
                    fontSize = 20.sp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "$elapsedDisplay focused",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(24.dp))

        // XP earned
        if (lastXpEarned > 0) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFFF6B35).copy(alpha = 0.12f)
            ) {
                Text(
                    "+$lastXpEarned XP",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B35)
                )
            }
        }

        // Milestone completion prompt
        val showMilestonePrompt by focusViewModel.showMilestonePrompt.collectAsState()
        val milestoneMarkedComplete by focusViewModel.milestoneMarkedComplete.collectAsState()
        val canCompleteMilestone by focusViewModel.canCompleteMilestone.collectAsState()

        if (showMilestonePrompt) selectedMilestone?.let { milestone ->
            Spacer(Modifier.height(24.dp))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Did you complete this milestone?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        milestone.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    if (!canCompleteMilestone) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Complete at least one focus session first",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { focusViewModel.dismissMilestonePrompt() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Need more time")
                        }
                        Button(
                            onClick = { focusViewModel.markMilestoneComplete() },
                            enabled = canCompleteMilestone,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Yes, done!")
                        }
                    }
                }
            }
        }

        // Confirmation badge
        if (milestoneMarkedComplete) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF4CAF50).copy(alpha = 0.12f)
            ) {
                Text(
                    "Milestone completed!",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
        }

        Spacer(Modifier.height(48.dp))

        // Buttons
        Button(
            onClick = onStartAnother,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B35))
        ) {
            Text("Start Another", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}m" else "${seconds}s"
}
