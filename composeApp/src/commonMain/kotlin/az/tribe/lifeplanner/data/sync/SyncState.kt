package az.tribe.lifeplanner.data.sync

import kotlin.time.Instant

enum class SyncState {
    IDLE,
    SYNCING,
    SYNCED,
    OFFLINE,
    ERROR
}

data class SyncStatus(
    val state: SyncState = SyncState.IDLE,
    val lastSyncedAt: Instant? = null,
    val errorMessage: String? = null,
    val pendingChanges: Int = 0
)
