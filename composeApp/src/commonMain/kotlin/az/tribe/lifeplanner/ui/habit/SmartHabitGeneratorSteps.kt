package az.tribe.lifeplanner.ui.habit

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitType
import az.tribe.lifeplanner.ui.theme.modernColors
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import com.adamglin.phosphoricons.regular.ArrowRight
import com.adamglin.phosphoricons.regular.Check
import com.adamglin.phosphoricons.regular.PencilSimple
import com.adamglin.phosphoricons.regular.Plus
import com.adamglin.phosphoricons.regular.PlusCircle
import com.adamglin.phosphoricons.regular.Sparkle
import com.adamglin.phosphoricons.regular.WifiSlash
import kotlinx.coroutines.delay

// ── Top Bar ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HabitGeneratorTopBar(step: HabitGeneratorStep, onBackClick: () -> Unit) {
    val progress = when (step) {
        HabitGeneratorStep.SCENARIO_SELECT -> 0f
        HabitGeneratorStep.CUSTOM_INPUT -> 0.25f
        HabitGeneratorStep.GENERATING -> 0.7f
        HabitGeneratorStep.RESULTS -> 1f
    }
    Column {
        TopAppBar(
            title = {
                Text(
                    text = when (step) {
                        HabitGeneratorStep.SCENARIO_SELECT -> "AI Habit Builder"
                        HabitGeneratorStep.CUSTOM_INPUT -> "Describe Your Goal"
                        HabitGeneratorStep.GENERATING -> "Building Habits"
                        HabitGeneratorStep.RESULTS -> "Your Habits"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                if (step != HabitGeneratorStep.GENERATING) {
                    IconButton(onClick = onBackClick) {
                        Icon(PhosphorIcons.Regular.ArrowLeft, contentDescription = "Back")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )
        val animProgress by animateFloatAsState(progress, label = "progress")
        LinearProgressIndicator(
            progress = { animProgress },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

// ── Scenario Select ───────────────────────────────────────────────────────────

@Composable
internal fun HabitScenarioSelectStep(
    scenarios: List<HabitScenario>,
    isOffline: Boolean,
    onScenarioSelected: (HabitScenario) -> Unit,
    onCustomClick: () -> Unit,
    onManualClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(PhosphorIcons.Regular.Sparkle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text("Pick a focus, AI builds your habits", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Select a life area and we'll generate a personalised habit stack instantly",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.modernColors.textSecondary
                )
            }
        }

        if (isOffline) {
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(PhosphorIcons.Regular.WifiSlash, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text("You're offline. AI generation requires internet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        items(scenarios.chunked(2)) { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { scenario ->
                    HabitScenarioCard(
                        scenario = scenario,
                        enabled = !isOffline,
                        onClick = { onScenarioSelected(scenario) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }

        item {
            // Custom prompt option
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = onCustomClick
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                        Icon(PhosphorIcons.Regular.PencilSimple, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Something else", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Describe your goal in your own words", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.modernColors.textSecondary)
                    }
                    Icon(PhosphorIcons.Regular.ArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            // Manual fallback
            OutlinedButton(onClick = onManualClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Text("Create manually instead")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HabitScenarioCard(scenario: HabitScenario, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.height(150.dp), shape = RoundedCornerShape(16.dp), onClick = onClick, enabled = enabled) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(if (enabled) scenario.gradientColors else scenario.gradientColors.map { it.copy(alpha = 0.4f) }))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(scenario.icon, fontSize = 32.sp)
                Column {
                    Text(scenario.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(scenario.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

// ── Custom Input ──────────────────────────────────────────────────────────────

@Composable
internal fun HabitCustomInputStep(prompt: String, isOffline: Boolean, onPromptChange: (String) -> Unit, onGenerate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What's your goal?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Describe what you want to achieve and AI will build a personalised habit stack for you.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.modernColors.textSecondary)
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).weight(1f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            BasicTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.modernColors.textPrimary),
                decorationBox = { inner ->
                    Box {
                        if (prompt.isEmpty()) {
                            Text(
                                "Example: I want to sleep better, exercise consistently, and reduce screen time before bed...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.modernColors.textSecondary.copy(alpha = 0.6f)
                            )
                        }
                        inner()
                    }
                }
            )
        }

        if (isOffline) {
            Text("Habit generation requires internet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        }

        Button(
            onClick = onGenerate,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = prompt.length >= 10 && !isOffline
        ) {
            Icon(PhosphorIcons.Regular.Sparkle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Build My Habit Stack", fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Generating ────────────────────────────────────────────────────────────────

@Composable
internal fun HabitGeneratingStep() {
    var dotCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(500); dotCount = (dotCount + 1) % 4 } }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            modifier = Modifier.size(100.dp).scale(scale).clip(CircleShape)
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
            contentAlignment = Alignment.Center
        ) {
            Icon(PhosphorIcons.Regular.Sparkle, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("Building your habit stack${".".repeat(dotCount)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text("AI is crafting habits tailored to your goal and lifestyle", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.modernColors.textSecondary, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f).clip(RoundedCornerShape(4.dp)))
    }
}

// ── Results ───────────────────────────────────────────────────────────────────

@Composable
internal fun HabitResultsStep(
    habits: List<GeneratedHabit>,
    addedTitles: Set<String>,
    onAddHabit: (GeneratedHabit) -> Unit,
    onAddAll: () -> Unit,
    onComplete: () -> Unit
) {
    var showCelebration by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200); showCelebration = true }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            androidx.compose.animation.AnimatedVisibility(visible = showCelebration, enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Icon(PhosphorIcons.Regular.Sparkle, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("${habits.size} Habits Ready!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Pick which ones to add to your tracker", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.modernColors.textSecondary)
                }
            }
        }

        items(habits) { habit ->
            GeneratedHabitCard(habit = habit, isAdded = habit.title in addedTitles, onAdd = { onAddHabit(habit) })
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val unadded = habits.count { it.title !in addedTitles }
                if (unadded > 0) {
                    Button(
                        onClick = { onAddAll() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(PhosphorIcons.Regular.PlusCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add All $unadded Habits", fontWeight = FontWeight.SemiBold)
                    }
                }
                OutlinedButton(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(if (addedTitles.isNotEmpty()) "Done — View My Habits" else "Skip for Now", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GeneratedHabitCard(habit: GeneratedHabit, isAdded: Boolean, onAdd: () -> Unit) {
    val color = habit.category.toHabitColor()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isAdded) 0.dp else 2.dp,
        border = if (isAdded) BorderStroke(2.dp, Color(0xFF4CAF50)) else null
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                    Text(habit.emoji, fontSize = 22.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(habit.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.1f)) {
                            Text(habit.category.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                        }
                        val typeColor = if (habit.type == HabitType.BUILD) Color(0xFF4CAF50) else Color(0xFFF44336)
                        Surface(shape = RoundedCornerShape(50), color = typeColor.copy(alpha = 0.1f)) {
                            Text(if (habit.type == HabitType.BUILD) "Build" else "Break", style = MaterialTheme.typography.labelSmall, color = typeColor, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                        }
                    }
                }
                if (isAdded) {
                    Surface(shape = CircleShape, color = Color(0xFF4CAF50)) {
                        Icon(PhosphorIcons.Regular.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(4.dp).size(16.dp))
                    }
                }
            }
            Text(habit.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.modernColors.textSecondary)
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(habit.frequency.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
            if (!isAdded) {
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = color)
                ) {
                    Icon(PhosphorIcons.Regular.Plus, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add This Habit")
                }
            }
        }
    }
}

internal fun GoalCategory.toHabitColor(): Color = when (this) {
    GoalCategory.CAREER -> Color(0xFF667EEA)
    GoalCategory.MONEY -> Color(0xFFF7971E)
    GoalCategory.BODY -> Color(0xFFED213A)
    GoalCategory.PEOPLE -> Color(0xFF4776E6)
    GoalCategory.WELLBEING -> Color(0xFF11998E)
    GoalCategory.PURPOSE -> Color(0xFF8E54E9)
}
