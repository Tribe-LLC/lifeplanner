package az.tribe.lifeplanner.ui.v4.coach

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.ui.chat.ChatViewModel
import az.tribe.lifeplanner.ui.chat.executeCoachSuggestion
import az.tribe.lifeplanner.ui.v4.components.V4TextButton
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.PaperPlaneRight
import org.koin.compose.viewmodel.koinViewModel

private const val DEFAULT_COACH = "luna_general"


/**
 * The Coach tab: one conversation with the everyday coach, in v4's look. Other coaches and
 * groups are still one tap away in the v3 list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun V4CoachScreen(
    initialPrompt: String?,
    onPromptConsumed: () -> Unit,
    onAllCoaches: () -> Unit,
    bottomInset: PaddingValues,
    viewModel: ChatViewModel = koinViewModel(),
    facts: az.tribe.lifeplanner.data.life.LifeFactsService = org.koin.compose.koinInject(),
) {
    val ui by viewModel.uiState.collectAsState()
    // The coach speaks first, from the user's own week. Worked out on the phone, no AI call.
    val opener by androidx.compose.runtime.produceState<az.tribe.lifeplanner.domain.service.Opener?>(null) {
        value = runCatching { facts.opener() }.getOrNull()
    }
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val c = V4.colors

    LaunchedEffect(Unit) { if (ui.currentSession == null) viewModel.selectCoachById(DEFAULT_COACH) }
    LaunchedEffect(ui.currentSession?.id, initialPrompt) {
        if (initialPrompt != null && ui.currentSession != null) {
            viewModel.sendMessage(initialPrompt)
            onPromptConsumed()
        }
    }

    val messages = ui.messages.filter { it.role != MessageRole.SYSTEM }
    val streaming = ui.streamingText?.takeIf { ui.isStreaming && it.isNotBlank() }
    val rows = messages.size + (if (streaming != null || ui.isSending) 1 else 0)
    LaunchedEffect(rows, streaming?.length) { if (rows > 0) listState.animateScrollToItem(rows) }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || ui.currentSession == null || ui.isSending) return
        viewModel.sendMessage(t)
        draft = ""
    }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Sees your last 7 days", style = V4.type.label.copy(fontSize = V4.type.body.fontSize * 0.93f), color = c.ink3)
                        Text("Coach", style = V4.type.display, color = c.ink, modifier = Modifier.semantics { heading() })
                    }
                }
            }
            if (messages.isEmpty() && !ui.isLoading) {
                item(key = "hello") {
                    Bubble(
                        opener?.text ?: "Hi. I see a short summary of your last 7 days in the areas you picked. Ask me to plan your week, rethink a goal, or make a bad day lighter.",
                        mine = false,
                    )
                }
            }
            items(messages, key = { it.id }) { m ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Bubble(m.content, mine = m.role == MessageRole.USER)
                    val sugs = m.metadata?.coachSuggestions.orEmpty()
                    if (m.role != MessageRole.USER && sugs.isNotEmpty()) {
                        Suggestions(
                            sugs,
                            done = ui.executedSuggestionIds + (m.metadata?.executedSuggestionIds ?: emptySet()),
                            busy = ui.executingAction,
                            onDo = { viewModel.executeCoachSuggestion(it) },
                            onAnswer = { send(it) },
                        )
                    }
                }
            }
            ui.actionFeedback?.let { fb ->
                item(key = "feedback") { Text(fb, style = V4.type.caption, color = c.accentInk, modifier = Modifier.padding(start = 8.dp)) }
            }
            if (streaming != null) {
                item(key = "streaming") { Bubble(streaming, mine = false) }
            } else if (ui.isSending) {
                item(key = "thinking") {
                    Box(Modifier.padding(start = 8.dp)) { CircularProgressIndicator(Modifier.size(22.dp), color = c.accent, strokeWidth = 2.5.dp) }
                }
            }
            if (messages.size <= 1 && !ui.isSending) {
                item(key = "starters") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val replies = opener?.replies ?: listOf("Plan my week" to "Plan my week", "Build a new habit" to "Help me build a new habit")
                        replies.forEach { (label, prompt) ->
                            Text(
                                label,
                                style = V4.type.label.copy(fontSize = V4.type.body.fontSize * 0.93f),
                                color = c.ink,
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .border(1.5.dp, c.trackOff, RoundedCornerShape(22.dp))
                                    .background(c.surface)
                                    .clickable(role = Role.Button) { send(prompt) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
            ui.error?.let { err ->
                item(key = "error") { Text("Could not reach your coach. $err", style = V4.type.caption, color = c.ink2) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomInset.calculateBottomPadding() + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(c.surface)
                    .border(1.5.dp, c.trackOff, RoundedCornerShape(26.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) Text("Message your coach", style = V4.type.body, color = c.ink3)
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    textStyle = V4.type.body.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(draft) }),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Message your coach" },
                )
            }
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (draft.isBlank()) c.trackOff else c.accent)
                    .clickable(role = Role.Button, enabled = draft.isNotBlank()) { send(draft) }
                    .semantics { contentDescription = "Send" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(PhosphorIcons.Fill.PaperPlaneRight, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    val c = V4.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        val shape = if (mine) RoundedCornerShape(20.dp, 6.dp, 20.dp, 20.dp) else RoundedCornerShape(6.dp, 20.dp, 20.dp, 20.dp)
        Text(
            text,
            style = V4.type.body,
            color = if (mine) c.onAccent else c.ink,
            modifier = Modifier
                .widthIn(max = 316.dp)
                .clip(shape)
                .background(if (mine) c.accent else c.surface)
                .let { if (mine) it else it.border(1.dp, c.line, shape) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}


/**
 * What the coach offers to do, as buttons that really do it: add the habit or plan, save the
 * journal entry, tick the habit. Questions show their answers as chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(
    list: List<az.tribe.lifeplanner.domain.model.CoachSuggestion>,
    done: Set<String>,
    busy: Boolean,
    onDo: (az.tribe.lifeplanner.domain.model.CoachSuggestion) -> Unit,
    onAnswer: (String) -> Unit,
) {
    val c = V4.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        list.forEach { s ->
            if (s is az.tribe.lifeplanner.domain.model.CoachSuggestion.AskQuestion) {
                Text(s.question, style = V4.type.bodyStrong, color = c.ink)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.options.forEach { o ->
                        Text(
                            o.label, style = V4.type.bodyStrong, color = c.ink,
                            modifier = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, c.line, RoundedCornerShape(22.dp))
                                .clickable(role = Role.Button) { onAnswer(o.label) }.padding(horizontal = 14.dp, vertical = 11.dp),
                        )
                    }
                }
                return@forEach
            }
            val (title, sub, verb) = describe(s)
            val isDone = s.id in done
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = V4.type.bodyStrong, color = c.ink)
                    Text(sub, style = V4.type.caption, color = c.ink3)
                }
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp))
                        .background(if (isDone) c.surface else c.accent)
                        .let { if (isDone) it.border(1.dp, c.accentSoft, RoundedCornerShape(22.dp)) else it }
                        .clickable(role = Role.Button, enabled = !isDone && !busy) { onDo(s) }
                        .semantics { contentDescription = if (isDone) "$title, done" else "$verb $title" }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (isDone) "Done" else verb, style = V4.type.bodyStrong, color = if (isDone) c.accentInk else c.onAccent) }
            }
        }
    }
}

private fun describe(s: az.tribe.lifeplanner.domain.model.CoachSuggestion): Triple<String, String, String> = when (s) {
    is az.tribe.lifeplanner.domain.model.CoachSuggestion.CreateHabit ->
        Triple(s.title, "New habit, ${if (s.frequency == "WEEKLY") "weekly" else "every day"}", "Add")
    is az.tribe.lifeplanner.domain.model.CoachSuggestion.CreateGoal ->
        Triple(s.title, if (s.milestones.isEmpty()) "New plan" else "New plan with ${s.milestones.size} steps", "Add")
    is az.tribe.lifeplanner.domain.model.CoachSuggestion.CreateJournalEntry -> Triple(s.title, "Save to your journal", "Save")
    is az.tribe.lifeplanner.domain.model.CoachSuggestion.CheckInHabit -> Triple(s.habitTitle, "Tick it for today", "Tick")
    is az.tribe.lifeplanner.domain.model.CoachSuggestion.AskQuestion -> Triple(s.question, "", "Answer")
}
