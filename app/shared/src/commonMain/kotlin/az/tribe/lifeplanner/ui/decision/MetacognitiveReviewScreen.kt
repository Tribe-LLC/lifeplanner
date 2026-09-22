package az.tribe.lifeplanner.ui.decision

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.Decision
import az.tribe.lifeplanner.domain.model.OutcomeQuality
import az.tribe.lifeplanner.domain.service.DecisionScorecard
import az.tribe.lifeplanner.ui.theme.modernColors
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import org.koin.compose.viewmodel.koinViewModel

/**
 * Pillar 5 (P5.4), metacognitive review. Surfaces logged Decisions and asks the user to grade
 * their *reasoning* (process) separately from the *result*, the "good decision vs. good luck"
 * distinction. Grading sets [Decision.outcomeQuality].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetacognitiveReviewScreen(
    onBackClick: () -> Unit,
    viewModel: MetacognitiveReviewViewModel = koinViewModel(),
) {
    val decisions by viewModel.decisions.collectAsState()
    val justReviewed by viewModel.justReviewed.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.modernColors.background,
        topBar = {
            TopAppBar(
                title = { Text("Review Decisions", fontWeight = FontWeight.Bold) },
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
                start = 16.dp, end = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            justReviewed?.let { card ->
                item { ReviewedBanner(card, onDismiss = viewModel::clearReviewed) }
            }
            item {
                Text(
                    "Grade your reasoning, not the outcome. A sound decision can turn out badly " +
                        "(and a flawed one can get lucky), what matters long-term is the process.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.modernColors.textSecondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            if (decisions.isEmpty()) {
                item {
                    Text(
                        "No decisions logged yet. Resolve a Choice Point and it'll appear here to review.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.modernColors.textSecondary
                    )
                }
            } else {
                items(decisions, key = { it.id }) { d ->
                    DecisionGradeCard(d, onGrade = { q -> viewModel.grade(d, q) })
                }
            }
        }
    }
}

/**
 * One call, reviewed as two plain questions rather than a four-way grid.
 *
 * [OutcomeQuality] is a 2x2 of process against result, and the old card flattened it into four
 * chips ("Sound - Worked", "Flawed - Lucky") that made you decode a matrix before you could answer.
 * Asking result first and process second resolves to the same four quadrants while never showing
 * more than two buttons, and the order does the teaching: you commit to what happened before you
 * are asked whether the thinking was any good, which is exactly the separation the feature exists
 * to make.
 *
 * The stated confidence is shown throughout, because a review with the prediction hidden is not a
 * reveal, it is a quiz.
 */
@Composable
internal fun DecisionGradeCard(d: Decision, onGrade: (OutcomeQuality) -> Unit) {
    val c = MaterialTheme.modernColors
    // Null until the first question is answered. Held here rather than in the ViewModel because a
    // half-finished review is a UI state, not something worth persisting.
    var workedOut by remember(d.id) { mutableStateOf<Boolean?>(null) }

    Surface(modifier = Modifier.fillMaxWidth(), color = c.cardBackground, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                d.question,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = c.textPrimary,
                maxLines = 2
            )
            Text(
                "Chose ${d.chosenOption} \u00b7 ${d.confidence}% sure at the time",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )

            val graded = d.outcomeQuality
            when {
                graded != null -> Text(
                    verdictLine(graded),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textPrimary
                )

                workedOut == null -> GradeStep(
                    prompt = "How did it turn out?",
                    affirmative = "It worked out",
                    negative = "It didn't",
                    onAnswer = { workedOut = it }
                )

                else -> GradeStep(
                    prompt = "Knowing only what you knew then, was it the right call?",
                    affirmative = "Right call",
                    negative = "Flawed call",
                    onAnswer = { soundProcess ->
                        onGrade(quadrant(soundProcess = soundProcess, goodResult = workedOut == true))
                    }
                )
            }
        }
    }
}

/**
 * What the answer changed, shown as the record rather than a reward.
 *
 * There is no XP figure here on purpose. XP feeds the Judgment ability, and Abilities is switched
 * off, so a number pointing at a hidden screen is noise. What you get back instead is the thing
 * you were actually building: one more call answered for, and where that leaves your hit rate.
 */
@Composable
private fun ReviewedBanner(card: DecisionScorecard, onDismiss: () -> Unit) {
    val c = MaterialTheme.modernColors
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onDismiss),
        color = c.surface,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "Answered.",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = c.textPrimary
            )
            Text(
                buildString {
                    append("${card.reviewed} reviewed")
                    card.processHitRate?.let { append(" \u00b7 $it% sound") }
                    if (card.awaitingReview > 0) append(" \u00b7 ${card.awaitingReview} still owed")
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )
        }
    }
}

@Composable
internal fun GradeStep(
    prompt: String,
    affirmative: String,
    negative: String,
    onAnswer: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(prompt, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.modernColors.textSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onAnswer(true) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) { Text(affirmative, maxLines = 1) }
            OutlinedButton(
                onClick = { onAnswer(false) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) { Text(negative, maxLines = 1) }
        }
    }
}

private fun quadrant(soundProcess: Boolean, goodResult: Boolean): OutcomeQuality = when {
    soundProcess && goodResult -> OutcomeQuality.GOOD_PROCESS_GOOD_RESULT
    soundProcess && !goodResult -> OutcomeQuality.GOOD_PROCESS_BAD_RESULT
    !soundProcess && goodResult -> OutcomeQuality.BAD_PROCESS_GOOD_RESULT
    else -> OutcomeQuality.BAD_PROCESS_BAD_RESULT
}

/** Plain-language verdict, so a graded card still reads without the matrix in your head. */
internal fun verdictLine(q: OutcomeQuality): String = when (q) {
    OutcomeQuality.GOOD_PROCESS_GOOD_RESULT -> "Right call, and it worked"
    OutcomeQuality.GOOD_PROCESS_BAD_RESULT -> "Right call, unlucky result"
    OutcomeQuality.BAD_PROCESS_GOOD_RESULT -> "Flawed call, got away with it"
    OutcomeQuality.BAD_PROCESS_BAD_RESULT -> "Flawed call, and it cost you"
}
