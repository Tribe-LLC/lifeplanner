package az.tribe.lifeplanner.ui.v4.coach

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import az.tribe.lifeplanner.ui.chat.parseMarkdownToAnnotatedString
import com.adamglin.phosphoricons.Bold
import com.adamglin.phosphoricons.bold.ArrowDown
import com.adamglin.phosphoricons.bold.ArrowUp
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.MessageRole
import az.tribe.lifeplanner.ui.chat.ChatViewModel
import az.tribe.lifeplanner.ui.chat.executeCoachSuggestion
import az.tribe.lifeplanner.ui.v4.theme.V4
import com.adamglin.PhosphorIcons
import org.koin.compose.viewmodel.koinViewModel

private const val DEFAULT_COACH = "luna_general"


/**
 * The Coach tab, laid out the way the big chat apps do it: a slim title at the top, the
 * conversation anchored to the bottom (so the newest line always sits just above the composer,
 * keyboard or not), coach replies as plain readable text, your messages in bubbles, and one
 * rounded composer that grows as you type with the send button inside it. Scrolling the
 * conversation puts the keyboard away; scrolling up to read stops the auto-follow, and a
 * round arrow brings you back to the latest.
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
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val typing = az.tribe.lifeplanner.ui.v4.components.keyboardUp()
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
    val waiting = streaming == null && ui.isSending

    // The list is reversed: item 0 is the newest, drawn at the bottom. "At the latest" means the
    // bottom edge is in view, give or take a line.
    val atLatest by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 120 } }
    var follow by remember { mutableStateOf(true) }
    var dragged by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { i ->
            if (i is DragInteraction.Start) {
                dragged = true
                keyboard?.hide()
                focus.clearFocus()
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { moving ->
            if (!moving && dragged) { follow = atLatest; dragged = false }
        }
    }
    // A new message, the coach starting to answer, or the keyboard opening: stay on the latest
    // unless the user has scrolled up to read.
    LaunchedEffect(messages.size, waiting, streaming != null, typing) {
        if (follow && listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(0)
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || ui.currentSession == null || ui.isSending) return
        viewModel.sendMessage(t)
        draft = ""
        follow = true
        scope.launch { if (listState.layoutInfo.totalItemsCount > 0) listState.animateScrollToItem(0) }
    }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        // A slim title that stays put, so the conversation has the whole screen.
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Coach", style = V4.type.title, color = c.ink, modifier = Modifier.semantics { heading() })
                Text("Sees your last 7 days", style = V4.type.caption, color = c.ink3)
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures { keyboard?.hide(); focus.clearFocus() }
                },
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
                // Short conversations start at the top, like a page; long ones stay pinned to the latest.
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Top),
            ) {
                ui.error?.let { err ->
                    item(key = "error") { Text("Could not reach your coach. $err", style = V4.type.caption, color = c.ink2) }
                }
                if (streaming != null) {
                    item(key = "streaming") { CoachText(streaming) }
                } else if (waiting) {
                    item(key = "thinking") { Thinking() }
                }
                ui.actionFeedback?.let { fb ->
                    item(key = "feedback") { Text(fb, style = V4.type.caption, color = c.accentInk) }
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
                items(messages.asReversed(), key = { it.id }) { m ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (m.role == MessageRole.USER) UserBubble(m.content) else CoachText(m.content)
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
                if (messages.isEmpty() && !ui.isLoading) {
                    item(key = "hello") {
                        CoachText(
                            opener?.text ?: "Hi. I see a short summary of your last 7 days in the areas you picked. Ask me to plan your week, rethink a goal, or make a bad day lighter.",
                        )
                    }
                }
            }

            // Back to the latest, once the user has scrolled up to read.
            androidx.compose.animation.AnimatedVisibility(
                visible = !atLatest && listState.layoutInfo.totalItemsCount > 0,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(c.surface)
                        .border(1.dp, c.line, CircleShape)
                        .clickable(role = Role.Button) {
                            follow = true
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                        .semantics { contentDescription = "Jump to the latest message" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PhosphorIcons.Bold.ArrowDown, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                }
            }
        }

        // With the keyboard up the tab bar steps aside, so the composer sits right on the keyboard.
        Composer(
            draft = draft,
            onDraft = { draft = it },
            canSend = draft.isNotBlank() && ui.currentSession != null && !ui.isSending,
            onSend = { send(draft) },
            modifier = Modifier.padding(
                start = 12.dp, end = 12.dp, top = 6.dp,
                bottom = if (typing) 8.dp else bottomInset.calculateBottomPadding() + 8.dp,
            ),
        )
    }
}

/** One rounded box that grows up to six lines, with the send button inside it. */
@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, canSend: Boolean, onSend: () -> Unit, modifier: Modifier = Modifier) {
    val c = V4.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(c.surface)
            .border(1.5.dp, c.trackOff, RoundedCornerShape(26.dp))
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f).heightIn(min = 40.dp).padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
            if (draft.isEmpty()) Text("Message your coach", style = V4.type.body, color = c.ink3)
            BasicTextField(
                value = draft,
                onValueChange = onDraft,
                textStyle = V4.type.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                // Return is a new line, as in the big chat apps; the arrow sends.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Message your coach" },
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (canSend) c.accent else c.trackOff)
                .clickable(role = Role.Button, enabled = canSend) { onSend() }
                .semantics { contentDescription = "Send" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(PhosphorIcons.Bold.ArrowUp, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(20.dp))
        }
    }
}

/** What you wrote, in a bubble on the right. */
@Composable
private fun UserBubble(text: String) {
    val c = V4.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text,
            style = V4.type.body,
            color = c.ink,
            modifier = Modifier
                .padding(start = 48.dp)
                .clip(RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp))
                .background(c.accentSoft)
                .padding(horizontal = 16.dp, vertical = 11.dp),
        )
    }
}

/** The coach's words as plain text across the page, with bold and lists kept. */
@Composable
private fun CoachText(text: String) {
    val c = V4.colors
    Text(
        parseMarkdownToAnnotatedString(text, c.ink),
        style = V4.type.body.copy(lineHeight = V4.type.body.fontSize * 1.5f),
        color = c.ink,
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
    )
}

/** Three soft dots while the coach thinks. */
@Composable
private fun Thinking() {
    val c = V4.colors
    val t = rememberInfiniteTransition(label = "thinking")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "phase")
    Row(
        Modifier.padding(vertical = 8.dp).semantics { contentDescription = "Your coach is thinking" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { i ->
            val on = phase.toInt() == i
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) c.ink2 else c.trackOff))
        }
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
