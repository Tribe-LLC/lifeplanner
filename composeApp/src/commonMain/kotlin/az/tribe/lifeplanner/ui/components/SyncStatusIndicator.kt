package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.sync.SyncState
import az.tribe.lifeplanner.data.sync.SyncStatus
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.roundToInt

@Composable
fun SyncStatusIndicator(
    syncStatus: StateFlow<SyncStatus>,
    onRetryClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val status by syncStatus.collectAsState()

    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        when (status.state) {
            SyncState.SYNCING -> {
                val infiniteTransition = rememberInfiniteTransition()
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Restart
                    )
                )
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Syncing",
                    modifier = Modifier.size(18.dp).rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
                if (!compact) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Syncing...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            SyncState.SYNCED -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Synced",
                    modifier = Modifier.size(18.dp),
                    tint = Color(0xFF4CAF50)
                )
                if (!compact) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        formatLastSynced(status.lastSyncedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            SyncState.OFFLINE -> {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "Offline",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                if (!compact) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            SyncState.ERROR -> {
                IconButton(onClick = onRetryClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Sync error - tap to retry",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                if (!compact) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Sync error",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            SyncState.IDLE -> {
                // Show nothing or a subtle cloud icon
                if (status.pendingChanges > 0) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "${status.pendingChanges} pending changes",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

private fun formatLastSynced(instant: Instant?): String {
    if (instant == null) return "Synced"
    val now = Clock.System.now()
    val diff = now - instant
    val seconds = diff.inWholeSeconds
    return when {
        seconds < 60 -> "Just synced"
        seconds < 3600 -> "${(seconds / 60)} min ago"
        seconds < 86400 -> "${(seconds / 3600)} hr ago"
        else -> "${(seconds / 86400)} days ago"
    }
}
