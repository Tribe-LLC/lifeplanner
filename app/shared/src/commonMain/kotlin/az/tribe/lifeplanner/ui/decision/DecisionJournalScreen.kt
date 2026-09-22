package az.tribe.lifeplanner.ui.decision

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.ChoicePoint
import az.tribe.lifeplanner.domain.service.DecisionScorecard
import az.tribe.lifeplanner.domain.model.Decision
import az.tribe.lifeplanner.ui.components.InlineEmptyState
import az.tribe.lifeplanner.ui.theme.modernColors
import leanlifeplanner.app.shared.generated.resources.Res
import leanlifeplanner.app.shared.generated.resources.illus_empty_decisions
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import org.koin.compose.viewmodel.koinViewModel

/**
 * Which slice of the log the track record is pointing at. The stat row is the control: the numbers
 * are not decoration, each one is the entry point to the decisions behind it.
 */
internal enum class DecisionFilter { ALL, REVIEWED, AWAITING }

/**
 * Pillar 3, Decision Journal. Surfaces any pending [ChoicePoint]s at the top (tap to
 * re-choose inline on the card), then lists logged [Decision]s with reasoning
 * and outcome status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionJournalScreen(
    onBackClick: () -> Unit,
    onDecisionClick: (String) -> Unit,
    viewModel: DecisionViewModel = koinViewModel(),
    reviewViewModel: MetacognitiveReviewViewModel = koinViewModel(),
) {
    val decisions by viewModel.decisions.collectAsState()
    val scorecard by viewModel.scorecard.collectAsState()
    val choicePoints by viewModel.choicePoints.collectAsState()
    val pendingDecisions by viewModel.pendingDecisions.collectAsState()
    var activeChoicePoint by remember { mutableStateOf<ChoicePoint?>(null) }
    var activePendingDecision by remember { mutableStateOf<Decision?>(null) }

    // Reviewing your reasoning is part of keeping a decision journal, not a separate destination,
    // so it lives here as a tab instead of its own row on the You page.
    var currentTab by rememberSaveable { mutableStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(DecisionFilter.ALL) }
    val toReview = decisions.filter { it.outcomeQuality == null }

    Scaffold(
        containerColor = MaterialTheme.modernColors.background,
        topBar = {
            TopAppBar(
                title = { Text("Decisions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(PhosphorIcons.Regular.ArrowLeft, contentDescription = "Back", tint = MaterialTheme.modernColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.modernColors.background,
                    titleContentColor = MaterialTheme.modernColors.textPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 84.dp,
                start = 16.dp,
                end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DecisionTabRow(
                    selectedTab = currentTab,
                    reviewCount = toReview.size,
                    onTabSelected = { currentTab = it },
                )
            }

            // The track record belongs on the log, not on the review tab: the review tab is where
            // you answer for one call, this is where you see what all of them add up to.
            if (currentTab == 0) {
                item { ScorecardCard(scorecard, filter, onFilter = { filter = it }) }
            }

            if (currentTab == 1) {
                item {
                    Text(
                        "Grade your reasoning, not the outcome. A sound decision can turn out badly " +
                            "(and a flawed one can get lucky), what matters long-term is the process.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.modernColors.textSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                if (toReview.isEmpty()) {
                    item {
                        Text(
                            if (decisions.isEmpty())
                                "No decisions logged yet. Resolve a Choice Point and it'll appear here to review."
                            else
                                "Every decision is reviewed. Nothing waiting on you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.modernColors.textSecondary
                        )
                    }
                } else {
                    items(toReview, key = { it.id }) { d ->
                        DecisionGradeCard(d, onGrade = { q -> reviewViewModel.grade(d, q) })
                    }
                }
            } else {
                if (choicePoints.isNotEmpty()) {
                    item { SectionHeader("Needs a decision") }
                    items(choicePoints, key = { it.trigger.name + (it.relatedGoalId ?: it.relatedHabitId ?: it.title) }) { cp ->
                        ChoicePointCard(
                            cp = cp,
                            expanded = activeChoicePoint == cp,
                            onToggle = { activeChoicePoint = if (activeChoicePoint == cp) null else cp },
                            onResolve = { action, reasoning ->
                                viewModel.resolve(cp, action, reasoning)
                                activeChoicePoint = null
                            },
                        )
                    }
                }

                if (pendingDecisions.isNotEmpty()) {
                    item { SectionHeader("From your journal") }
                    items(pendingDecisions, key = { it.id }) { d ->
                        PendingDecisionCard(
                            d = d,
                            expanded = activePendingDecision == d,
                            onToggle = { activePendingDecision = if (activePendingDecision == d) null else d },
                            onConfirm = { chosen, reasoning ->
                                viewModel.confirm(d, chosen, reasoning)
                                activePendingDecision = null
                            },
                            onDismissDecision = {
                                viewModel.dismiss(d)
                                activePendingDecision = null
                            },
                        )
                    }
                }

                item { SectionHeader("Decision journal") }
                if (decisions.isEmpty()) {
                    item {
                        InlineEmptyState(
                            illustration = Res.drawable.illus_empty_decisions,
                            title = "No decisions yet",
                            subtitle = "Choices you make at choice points land here, what you picked and why.",
                        )
                    }
                } else {
                    val shown = when (filter) {
                        DecisionFilter.ALL -> decisions
                        DecisionFilter.REVIEWED -> decisions.filter { it.outcomeQuality != null }
                        DecisionFilter.AWAITING -> decisions.filter { it.outcomeQuality == null }
                    }
                    if (shown.isEmpty()) {
                        item {
                            Text(
                                when (filter) {
                                    DecisionFilter.REVIEWED -> "Nothing reviewed yet."
                                    DecisionFilter.AWAITING -> "Every call has been answered for."
                                    DecisionFilter.ALL -> ""
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.modernColors.textSecondary
                            )
                        }
                    } else {
                        items(shown, key = { it.id }) { d -> DecisionCard(d, onClick = { onDecisionClick(d.id) }) }
                    }
                }
            }
        }
    }

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingDecisionCard(
    d: Decision,
    expanded: Boolean,
    onToggle: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onDismissDecision: () -> Unit,
) {
    val c = MaterialTheme.modernColors
    var chosen by remember(d.id) { mutableStateOf(d.chosenOption) }
    var reasoning by remember(d.id) { mutableStateOf(d.reasoning) }

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        color = c.secondaryContainer,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Looks like a decision", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.onSecondaryContainer)
            Text(d.question, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.onSecondaryContainer, maxLines = 2)

            if (!expanded) {
                Text("Tap to log or dismiss", style = MaterialTheme.typography.bodySmall, color = c.onSecondaryContainer)
            } else {
                // Confirming happens on the card. This is the app guessing that something in your
                // journal was a decision, and a modal makes a guess feel like a demand.
                if (d.optionsConsidered.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        d.optionsConsidered.forEach { option ->
                            FilterChip(
                                selected = chosen == option,
                                onClick = { chosen = option },
                                label = { Text(option) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = chosen,
                    onValueChange = { chosen = it },
                    label = { Text("Chosen option") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = reasoning,
                    onValueChange = { reasoning = it },
                    label = { Text("Why? (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = { onConfirm(chosen.trim(), reasoning.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Log this decision") }
                TextButton(onClick = onDismissDecision, modifier = Modifier.fillMaxWidth()) {
                    Text("Not a decision", color = c.onSecondaryContainer)
                }
            }
        }
    }
}
@Composable
private fun DecisionTabRow(selectedTab: Int, reviewCount: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf("Journal", if (reviewCount > 0) "Review · $reviewCount" else "Review")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tabs.forEachIndexed { index, label ->
            val isSelected = selectedTab == index
            Surface(
                onClick = { onTabSelected(index) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * The track record, styled as an instrument rather than a card that shouts.
 *
 * A hairline over a flat surface, one figure that matters, and a row of raw counts underneath.
 * The point of this screen is to tell you something slightly uncomfortable about your own
 * judgement, and a bright filled panel reads as congratulation, which is the wrong register.
 *
 * Everything below the rule stays hidden until something has been reviewed, because a hit rate
 * over zero reviews is not a zero, it is an absence.
 */
@Composable
internal fun ScorecardCard(
    card: DecisionScorecard,
    filter: DecisionFilter = DecisionFilter.ALL,
    onFilter: (DecisionFilter) -> Unit = {},
) {
    val c = MaterialTheme.modernColors
    // The gap is the one number nobody can read cold, so it explains itself on tap rather than in
    // a dialog. Everything else on this card is a door into the decisions behind it.
    var explainGap by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, c.outlineVariant, RoundedCornerShape(14.dp)),
        color = c.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "TRACK RECORD",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp),
                color = c.textTertiary
            )

            val hitRate = card.processHitRate
            if (hitRate == null) {
                Text(
                    if (card.logged == 0) "No calls logged yet." else "Review a call to start your record.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "$hitRate%",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        color = c.textPrimary
                    )
                    Text(
                        "of your reviewed calls were sound thinking",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            }

            HorizontalDivider(color = c.divider)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(card.logged.toString(), "LOGGED", filter == DecisionFilter.ALL) { onFilter(DecisionFilter.ALL) }
                Stat(card.reviewed.toString(), "REVIEWED", filter == DecisionFilter.REVIEWED) { onFilter(DecisionFilter.REVIEWED) }
                Stat(card.awaitingReview.toString(), "STILL OWED", filter == DecisionFilter.AWAITING) { onFilter(DecisionFilter.AWAITING) }
            }

            card.calibrationGap?.let { gap ->
                HorizontalDivider(color = c.divider)
                Column(
                    Modifier.fillMaxWidth().clickable { explainGap = !explainGap },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "CONFIDENCE GAP",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                            color = c.textTertiary
                        )
                        Text(
                            if (gap > 0) "+$gap" else "$gap",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = c.textPrimary
                        )
                    }
                    Text(
                        when {
                            gap > 10 -> "You said ${card.averageConfidence}%. You were right ${card.actualSuccessRate}%. Overconfident."
                            gap < -10 -> "You said ${card.averageConfidence}%. You were right ${card.actualSuccessRate}%. You undersell yourself."
                            else -> "You said ${card.averageConfidence}%. You were right ${card.actualSuccessRate}%. Well calibrated."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                    Text(
                        if (explainGap) {
                            "Every call records how sure you were. This compares that against how often " +
                                "things actually worked out. Zero means your confidence is honest. A big " +
                                "positive number means you back yourself harder than the results deserve. " +
                                "Tap to hide."
                        } else "What is this?",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textTertiary
                    )
                }
            }
        }
    }
}

/** One number in the stat row, and the door to the decisions behind it. */
@Composable
private fun Stat(value: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.modernColors
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (selected) c.primary else c.textPrimary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
            color = if (selected) c.primary else c.textTertiary
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.modernColors.textSecondary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ChoicePointCard(
    cp: ChoicePoint,
    expanded: Boolean,
    onToggle: () -> Unit,
    onResolve: (ChoicePointAction, String) -> Unit,
) {
    val c = MaterialTheme.modernColors
    var reasoning by remember(cp.title) { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)),
        color = c.warningContainer,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(cp.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = c.onWarningContainer)
            Text(cp.prompt, style = MaterialTheme.typography.bodySmall, color = c.onWarningContainer)

            // Resolving happens here rather than in a modal sheet. The list scrolls, so the
            // keyboard can never strand the actions the way the sheet did.
            if (expanded) {
                OutlinedTextField(
                    value = reasoning,
                    onValueChange = { reasoning = it },
                    label = { Text("Why? (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )
                Button(
                    onClick = { onResolve(ChoicePointAction.KEEP, reasoning.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Keep going") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onResolve(ChoicePointAction.RESCHEDULE, reasoning.trim()) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Reschedule", maxLines = 1) }
                    OutlinedButton(onClick = { onResolve(ChoicePointAction.SHRINK, reasoning.trim()) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Shrink", maxLines = 1) }
                }
                OutlinedButton(
                    onClick = { onResolve(ChoicePointAction.DROP, reasoning.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Drop") }
            }
        }
    }
}
@Composable
private fun DecisionCard(d: Decision, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        color = MaterialTheme.modernColors.cardBackground,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(d.question, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.modernColors.textPrimary, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.modernColors.primaryContainer) {
                    Text(
                        d.chosenOption,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.modernColors.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
                val status = if (d.outcomeQuality != null) "Reviewed" else "Awaiting outcome"
                Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.modernColors.textSecondary)
            }
            if (d.reasoning.isNotBlank()) {
                Text(d.reasoning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.modernColors.textSecondary, maxLines = 2)
            }
        }
    }
}
