package az.tribe.lifeplanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.domain.model.ChatSession
import az.tribe.lifeplanner.domain.model.CoachCharacteristics
import az.tribe.lifeplanner.domain.model.CoachGroup
import az.tribe.lifeplanner.domain.model.CoachPersona
import az.tribe.lifeplanner.domain.model.CustomCoach
import az.tribe.lifeplanner.domain.model.MessageRole
import kotlinx.datetime.LocalDateTime

/**
 * Displays the coach list with "The Council" group chat and individual coaches
 */
@Composable
fun CoachListContent(
    coaches: List<CoachPersona>,
    sessions: Map<String, ChatSession?>,
    onCoachClick: (CoachPersona) -> Unit,
    onCouncilClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CoachListContentExtended(
        builtinCoaches = coaches,
        customCoaches = emptyList(),
        coachGroups = emptyList(),
        sessions = sessions,
        onBuiltinCoachClick = onCoachClick,
        onCustomCoachClick = {},
        onGroupClick = {},
        onCouncilClick = onCouncilClick,
        onCreateCoach = {},
        onCreateGroup = {},
        modifier = modifier
    )
}

// Unified item type for sorting
private sealed class ChatListItem(val sortKey: LocalDateTime?) {
    data class BuiltinCoach(val coach: CoachPersona, val session: ChatSession?) : ChatListItem(session?.lastMessageAt)
    data class Custom(val coach: CustomCoach, val session: ChatSession?) : ChatListItem(session?.lastMessageAt)
    data class Group(val group: CoachGroup, val session: ChatSession?) : ChatListItem(session?.lastMessageAt)
    data class Council(val session: ChatSession?) : ChatListItem(session?.lastMessageAt)
}

/**
 * Extended coach list — chat-list style sorted by recent activity
 */
@Composable
fun CoachListContentExtended(
    builtinCoaches: List<CoachPersona>,
    customCoaches: List<CustomCoach>,
    coachGroups: List<CoachGroup>,
    sessions: Map<String, ChatSession?>,
    onBuiltinCoachClick: (CoachPersona) -> Unit,
    onCustomCoachClick: (CustomCoach) -> Unit,
    onGroupClick: (CoachGroup) -> Unit,
    onCouncilClick: () -> Unit,
    onCreateCoach: () -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Build unified list and sort by recent activity
    val items = buildList {
        add(ChatListItem.Council(sessions[CoachPersona.COUNCIL_ID]))
        builtinCoaches.forEach { coach ->
            add(ChatListItem.BuiltinCoach(coach, sessions[coach.id]))
        }
        customCoaches.forEach { coach ->
            add(ChatListItem.Custom(coach, sessions[coach.id]))
        }
        coachGroups.forEach { group ->
            add(ChatListItem.Group(group, sessions[group.id]))
        }
    }.sortedWith(compareByDescending<ChatListItem> { it.sortKey != null }.thenByDescending { it.sortKey })

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(items, key = { item ->
            when (item) {
                is ChatListItem.BuiltinCoach -> "builtin_${item.coach.id}"
                is ChatListItem.Custom -> "custom_${item.coach.id}"
                is ChatListItem.Group -> "group_${item.group.id}"
                is ChatListItem.Council -> "council"
            }
        }) { item ->
            when (item) {
                is ChatListItem.Council -> {
                    val lastMsg = item.session?.messages?.lastOrNull()
                    ChatListRow(
                        emoji = null,
                        gradient = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.secondary
                            )
                        ),
                        icon = {
                            Icon(
                                Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        name = "The Council",
                        lastMessage = lastMsg?.content,
                        isYourTurn = lastMsg?.role == MessageRole.ASSISTANT,
                        defaultSubtitle = "All coaches united",
                        timestamp = item.session?.lastMessageAt,
                        onClick = onCouncilClick
                    )
                }
                is ChatListItem.BuiltinCoach -> {
                    val lastMsg = item.session?.messages?.lastOrNull()
                    ChatListRow(
                        emoji = item.coach.emoji,
                        gradient = getCoachGradient(item.coach),
                        name = item.coach.name,
                        lastMessage = lastMsg?.content,
                        isYourTurn = lastMsg?.role == MessageRole.ASSISTANT,
                        defaultSubtitle = item.coach.greeting,
                        timestamp = item.session?.lastMessageAt,
                        onClick = { onBuiltinCoachClick(item.coach) }
                    )
                }
                is ChatListItem.Custom -> {
                    val lastMsg = item.session?.messages?.lastOrNull()
                    val bg = parseHexColor(item.coach.iconBackgroundColor)
                    val accent = parseHexColor(item.coach.iconAccentColor)
                    ChatListRow(
                        emoji = item.coach.icon,
                        gradient = Brush.linearGradient(listOf(bg, accent)),
                        name = item.coach.name,
                        lastMessage = lastMsg?.content,
                        isYourTurn = lastMsg?.role == MessageRole.ASSISTANT,
                        defaultSubtitle = item.coach.characteristics.firstOrNull()?.let { id ->
                            CoachCharacteristics.getById(id)?.name
                        } ?: "Your personal coach",
                        timestamp = item.session?.lastMessageAt,
                        onClick = { onCustomCoachClick(item.coach) }
                    )
                }
                is ChatListItem.Group -> {
                    val lastMsg = item.session?.messages?.lastOrNull()
                    ChatListRow(
                        emoji = item.group.icon,
                        gradient = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        ),
                        name = item.group.name,
                        lastMessage = lastMsg?.content,
                        isYourTurn = lastMsg?.role == MessageRole.ASSISTANT,
                        defaultSubtitle = "${item.group.members.size} coaches",
                        timestamp = item.session?.lastMessageAt,
                        onClick = { onGroupClick(item.group) }
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }

        // Create coach/group buttons at the bottom
        item {
            Spacer(modifier = Modifier.height(16.dp))
            CreateActionsRow(
                onCreateCoach = onCreateCoach,
                onCreateGroup = onCreateGroup
            )
        }
    }
}

/**
 * A single chat list row — WhatsApp/Telegram style
 */
@Composable
private fun ChatListRow(
    name: String,
    gradient: Brush,
    lastMessage: String?,
    isYourTurn: Boolean,
    defaultSubtitle: String,
    timestamp: LocalDateTime?,
    onClick: () -> Unit,
    emoji: String? = null,
    icon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(gradient, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                icon()
            } else if (emoji != null) {
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // Name + message preview
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (timestamp != null) {
                    Text(
                        text = formatChatTimestamp(timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isYourTurn) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (isYourTurn && lastMessage != null) {
                Text(
                    text = "Your turn - tap to reply",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = lastMessage ?: defaultSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Format timestamp for chat list — shows time for today, day name for this week, date otherwise
 */
private fun formatChatTimestamp(dateTime: LocalDateTime): String {
    val hour = dateTime.hour.toString().padStart(2, '0')
    val minute = dateTime.minute.toString().padStart(2, '0')
    return "$hour:$minute"
}

/**
 * Bottom row with create coach and create group buttons
 */
@Composable
private fun CreateActionsRow(
    onCreateCoach: () -> Unit,
    onCreateGroup: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onCreateCoach() },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Coach",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onCreateGroup() },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Rounded.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "New Group",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Returns a gradient brush for a coach based on their category
 */
@Composable
private fun getCoachGradient(coach: CoachPersona): Brush {
    val colors = when (coach.id) {
        "luna_general" -> listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
        "alex_career" -> listOf(
            Color(0xFF1976D2),
            Color(0xFF42A5F5)
        )
        "morgan_finance" -> listOf(
            Color(0xFF388E3C),
            Color(0xFF66BB6A)
        )
        "kai_fitness" -> listOf(
            Color(0xFFE53935),
            Color(0xFFEF5350)
        )
        "sam_social" -> listOf(
            Color(0xFF7B1FA2),
            Color(0xFFAB47BC)
        )
        "river_wellness" -> listOf(
            Color(0xFF00796B),
            Color(0xFF26A69A)
        )
        "jamie_family" -> listOf(
            Color(0xFFFF8F00),
            Color(0xFFFFB300)
        )
        else -> listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    }
    return Brush.linearGradient(colors)
}

/**
 * Helper function to parse hex color string to Compose Color
 */
private fun parseHexColor(hexColor: String): Color {
    return try {
        val hex = hexColor.removePrefix("#")
        Color(
            red = hex.substring(0, 2).toInt(16) / 255f,
            green = hex.substring(2, 4).toInt(16) / 255f,
            blue = hex.substring(4, 6).toInt(16) / 255f
        )
    } catch (e: Exception) {
        Color(0xFF6366F1) // Default indigo
    }
}
