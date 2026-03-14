package az.tribe.lifeplanner.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import az.tribe.lifeplanner.data.sync.SyncState
import az.tribe.lifeplanner.data.sync.SyncStatus
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SyncStatusIndicator(
    syncStatus: StateFlow<SyncStatus>,
    onRetryClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val status by syncStatus.collectAsState()

    AnimatedContent(
        targetState = status.state,
        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
        modifier = modifier.padding(horizontal = 4.dp)
    ) { state ->
        when (state) {
            SyncState.SYNCING -> {
                val infiniteTransition = rememberInfiniteTransition()
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = "Syncing with cloud",
                    modifier = Modifier.size(20.dp).alpha(alpha),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            SyncState.SYNCED -> {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "All data synced",
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF4CAF50)
                )
            }

            SyncState.OFFLINE -> {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = "No internet connection",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            SyncState.ERROR -> {
                IconButton(onClick = onRetryClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "Sync failed — tap to retry",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            SyncState.IDLE -> {
                if (status.pendingChanges > 0) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "${status.pendingChanges} changes waiting to sync",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = "Connected",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
