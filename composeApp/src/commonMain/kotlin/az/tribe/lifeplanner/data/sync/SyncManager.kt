package az.tribe.lifeplanner.data.sync

import az.tribe.lifeplanner.infrastructure.SharedDatabase
import az.tribe.lifeplanner.util.NetworkConnectivityObserver
import co.touchlab.kermit.Logger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.datetime.Clock
import kotlin.math.min

class SyncManager private constructor(
    private val supabase: SupabaseClient?,
    private val userIdProvider: (() -> String?)?,
    private val isConnectedProvider: () -> Boolean,
    private val connectivityFlow: kotlinx.coroutines.flow.Flow<Boolean>?,
    syncersProvider: (() -> List<TableSyncer<*, *>>)?,
    sharedDatabase: SharedDatabase?
) {
    /**
     * Primary constructor for production use.
     */
    constructor(
        supabase: SupabaseClient,
        sharedDatabase: SharedDatabase,
        connectivityObserver: NetworkConnectivityObserver
    ) : this(
        supabase = supabase,
        userIdProvider = null,
        isConnectedProvider = { connectivityObserver.isConnected.value },
        connectivityFlow = connectivityObserver.observe(),
        syncersProvider = null,
        sharedDatabase = sharedDatabase
    )

    /**
     * Test constructor — allows injecting fakes for all dependencies.
     */
    constructor(
        userIdProvider: () -> String?,
        isConnectedProvider: () -> Boolean,
        connectivityFlow: kotlinx.coroutines.flow.Flow<Boolean>?,
        syncersProvider: () -> List<TableSyncer<*, *>>
    ) : this(
        supabase = null,
        userIdProvider = userIdProvider,
        isConnectedProvider = isConnectedProvider,
        connectivityFlow = connectivityFlow,
        syncersProvider = syncersProvider,
        sharedDatabase = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncStatus = MutableStateFlow(SyncStatus())
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    // Cooldown: skip sync if one just completed successfully within this window
    private var lastSuccessfulSyncAt: Long = 0L
    private val syncCooldownMs = 30_000L

    private val _hasCompletedInitialSync = MutableStateFlow(false)

    fun hasCompletedInitialSync(): Boolean = _hasCompletedInitialSync.value

    private val syncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Auto-retry state
    private var retryJob: Job? = null
    private var retryCount = 0
    private val retryDelays = longArrayOf(5_000L, 15_000L, 30_000L, 60_000L)
    private val maxRetries = retryDelays.size

    // Table syncers ordered by FK dependency tiers
    private val syncers: List<TableSyncer<*, *>> by lazy {
        syncersProvider?.invoke() ?: createSyncers(supabase!!, sharedDatabase!!)
    }

    init {
        startListeners()
    }

    /**
     * Start (or restart) the debounce and connectivity listeners.
     */
    private fun startListeners() {
        // Debounce sync requests to avoid rapid-fire syncing
        scope.launch {
            syncRequests
                .debounce(2000L)
                .collect {
                    performFullSync()
                }
        }

        // Auto-sync on network reconnect
        connectivityFlow?.let { flow ->
            scope.launch {
                flow
                    .distinctUntilChanged()
                    .filter { it } // only when connected
                    .collect {
                        Logger.d("SyncManager") { "Network reconnected, triggering sync" }
                        performFullSync()
                    }
            }
        }
    }

    /**
     * Request a debounced sync. Safe to call frequently after mutations.
     */
    fun requestSync() {
        syncRequests.tryEmit(Unit)
    }

    /**
     * Launch a full sync on the SyncManager's own scope (fire-and-forget).
     * Use this when the caller's coroutine may be cancelled (e.g. after login navigation).
     */
    fun launchFullSync(resetRetry: Boolean = false) {
        scope.launch { performFullSync(resetRetry) }
    }

    private fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }

    private fun scheduleRetry() {
        if (retryCount >= maxRetries) {
            Logger.w("SyncManager") { "Max retries ($maxRetries) reached, giving up auto-retry" }
            return
        }
        val delayMs = retryDelays[min(retryCount, retryDelays.size - 1)]
        retryCount++
        Logger.d("SyncManager") { "Scheduling retry #$retryCount in ${delayMs / 1000}s" }
        cancelRetry()
        retryJob = scope.launch {
            delay(delayMs)
            performFullSync()
        }
    }

    /**
     * Perform a full push-then-pull sync for all tables.
     * @param resetRetry true when triggered manually (user tap) to reset the backoff counter.
     */
    suspend fun performFullSync(resetRetry: Boolean = false) {
        cancelRetry()
        if (resetRetry) retryCount = 0

        // Cooldown: skip if a successful sync just completed (unless manual retry)
        if (!resetRetry) {
            val elapsed = Clock.System.now().toEpochMilliseconds() - lastSuccessfulSyncAt
            if (elapsed < syncCooldownMs) {
                Logger.d("SyncManager") { "Sync cooldown active (${elapsed / 1000}s ago), skipping" }
                return
            }
        }

        val userId = getCurrentUserId() ?: run {
            Logger.d("SyncManager") { "No authenticated user, skipping sync" }
            _syncStatus.value = _syncStatus.value.copy(state = SyncState.IDLE)
            return
        }
        Logger.d("SyncManager") { "Starting sync for userId=$userId" }

        if (!isConnectedProvider()) {
            _syncStatus.value = _syncStatus.value.copy(state = SyncState.OFFLINE)
            return
        }

        if (_syncStatus.value.state == SyncState.SYNCING) {
            Logger.d("SyncManager") { "Sync already in progress, skipping" }
            return
        }

        // Ensure the JWT is fresh before starting sync
        if (!refreshSessionIfNeeded()) return

        _syncStatus.value = _syncStatus.value.copy(
            state = SyncState.SYNCING,
            errorMessage = null
        )

        try {
            var totalPushed = 0
            var totalPulled = 0
            val failedTables = mutableListOf<String>()
            var networkDown = false

            // Detect fresh sync (no pull timestamps = first sync after login/account switch)
            val isFreshSync = syncers.any { it.getLastPullTimestamp() == null }

            if (isFreshSync) {
                // Pull first to avoid overwriting remote data with empty defaults
                Logger.d("SyncManager") { "Fresh sync detected — pulling before pushing" }
                for (syncer in syncers) {
                    if (networkDown) { failedTables.add(syncer.tableName); continue }
                    try {
                        totalPulled += syncer.pullRemoteChanges(userId)
                    } catch (e: Exception) {
                        Logger.e("SyncManager", e) { "Pull failed for ${syncer.tableName}" }
                        failedTables.add(syncer.tableName)
                        if (isNetworkException(e)) networkDown = true
                    }
                }
                for (syncer in syncers) {
                    if (networkDown) { if (syncer.tableName !in failedTables) failedTables.add(syncer.tableName); continue }
                    try {
                        totalPushed += syncer.pushLocalChanges(userId)
                    } catch (e: Exception) {
                        Logger.e("SyncManager", e) { "Push failed for ${syncer.tableName}" }
                        if (syncer.tableName !in failedTables) failedTables.add(syncer.tableName)
                        if (isNetworkException(e)) networkDown = true
                    }
                }
            } else {
                // Normal sync: push then pull
                for (syncer in syncers) {
                    if (networkDown) { failedTables.add(syncer.tableName); continue }
                    try {
                        totalPushed += syncer.pushLocalChanges(userId)
                    } catch (e: Exception) {
                        Logger.e("SyncManager", e) { "Push failed for ${syncer.tableName}" }
                        failedTables.add(syncer.tableName)
                        if (isNetworkException(e)) networkDown = true
                    }
                }
                for (syncer in syncers) {
                    if (networkDown) { if (syncer.tableName !in failedTables) failedTables.add(syncer.tableName); continue }
                    try {
                        totalPulled += syncer.pullRemoteChanges(userId)
                    } catch (e: Exception) {
                        Logger.e("SyncManager", e) { "Pull failed for ${syncer.tableName}" }
                        if (syncer.tableName !in failedTables) failedTables.add(syncer.tableName)
                        if (isNetworkException(e)) networkDown = true
                    }
                }
            }

            val now = Clock.System.now()
            if (failedTables.isNotEmpty()) {
                if (networkDown) {
                    Logger.w("SyncManager") { "Network down during sync, setting OFFLINE" }
                    _syncStatus.value = SyncStatus(
                        state = SyncState.OFFLINE,
                        lastSyncedAt = _syncStatus.value.lastSyncedAt,
                        pendingChanges = 0,
                        errorMessage = null
                    )
                } else {
                    _syncStatus.value = SyncStatus(
                        state = SyncState.ERROR,
                        lastSyncedAt = now,
                        pendingChanges = 0,
                        errorMessage = "Partial sync: ${failedTables.size} tables failed (${failedTables.joinToString()})"
                    )
                    Logger.w("SyncManager") { "Partial sync: pushed=$totalPushed, pulled=$totalPulled, failed=${failedTables}" }
                }
                _hasCompletedInitialSync.value = true
                scheduleRetry()
            } else {
                retryCount = 0
                lastSuccessfulSyncAt = Clock.System.now().toEpochMilliseconds()
                _syncStatus.value = SyncStatus(
                    state = SyncState.SYNCED,
                    lastSyncedAt = now,
                    pendingChanges = 0
                )
                Logger.d("SyncManager") { "Sync complete: pushed=$totalPushed, pulled=$totalPulled" }
                _hasCompletedInitialSync.value = true
            }

        } catch (e: Exception) {
            if (isNetworkException(e)) {
                Logger.w("SyncManager") { "Sync failed due to network: ${e.message}" }
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.OFFLINE,
                    errorMessage = null
                )
            } else {
                Logger.e("SyncManager", e) { "Sync failed" }
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.ERROR,
                    errorMessage = e.message
                )
            }
            scheduleRetry()
        }
    }

    /**
     * Get count of pending (unsynced) local changes across all tables.
     */
    suspend fun countPendingChanges(): Int {
        return syncers.sumOf { it.countPending() }
    }

    /**
     * Cancel in-flight syncs and reset state. Called on logout.
     */
    fun onLogout() {
        cancelRetry()
        retryCount = 0
        lastSuccessfulSyncAt = 0L
        scope.coroutineContext[Job]?.cancelChildren()
        _hasCompletedInitialSync.value = false
        clearSyncTimestamps()
        // Restart listeners since cancelChildren() killed them
        startListeners()
    }

    /**
     * Clear all sync pull timestamps. Called on logout so the next user gets a full pull.
     */
    fun clearSyncTimestamps() {
        try {
            val settings = com.russhwolf.settings.Settings()
            listOf(
                "sync_pull_users", "sync_pull_goals", "sync_pull_habits",
                "sync_pull_milestones", "sync_pull_goal_history", "sync_pull_user_progress",
                "sync_pull_habit_check_ins", "sync_pull_journal_entries", "sync_pull_badges",
                "sync_pull_challenges", "sync_pull_goal_dependencies", "sync_pull_chat_sessions",
                "sync_pull_chat_messages", "sync_pull_review_reports", "sync_pull_reminders",
                "sync_pull_custom_coaches", "sync_pull_coach_groups", "sync_pull_coach_group_members",
                "sync_pull_focus_sessions", "sync_pull_reviews", "sync_pull_coach_persona_overrides",
                "sync_pull_beginner_objectives"
            ).forEach { settings.remove(it) }
        } catch (e: Exception) {
            Logger.w("SyncManager") { "Failed to clear sync timestamps: ${e.message}" }
        }
        _syncStatus.value = SyncStatus()
    }

    /**
     * Refresh the Supabase session before sync to guarantee a fresh JWT.
     * Returns true if session is valid, false if refresh failed (sets ERROR state).
     */
    private suspend fun refreshSessionIfNeeded(): Boolean {
        if (supabase == null) return true // test constructor
        return try {
            val session = supabase.auth.currentSessionOrNull()
            if (session == null) {
                Logger.w("SyncManager") { "No session found, cannot sync" }
                _syncStatus.value = _syncStatus.value.copy(
                    state = SyncState.ERROR,
                    errorMessage = "Not authenticated"
                )
                false
            } else {
                supabase.auth.refreshCurrentSession()
                Logger.d("SyncManager") { "Session refreshed before sync" }
                true
            }
        } catch (e: Exception) {
            Logger.e("SyncManager") { "Session refresh failed: ${e.message}" }
            _syncStatus.value = _syncStatus.value.copy(
                state = SyncState.ERROR,
                errorMessage = "Session expired"
            )
            scheduleRetry()
            false
        }
    }

    private fun getCurrentUserId(): String? {
        return try {
            if (userIdProvider != null) {
                userIdProvider.invoke()
            } else {
                supabase?.auth?.currentUserOrNull()?.id
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Create all table syncers in FK-dependency order.
     * Tier 1 (no deps): users, goals, habits, badges, custom_coaches, coach_groups, review_reports
     * Tier 2 (→Tier 1): milestones, goal_history, goal_dependencies, habit_check_ins,
     *                     journal_entries, chat_sessions, reminders, focus_sessions, challenges,
     *                     coach_group_members, user_progress
     * Tier 3 (→Tier 2): chat_messages
     */
    private fun createSyncers(supabase: SupabaseClient, sharedDatabase: SharedDatabase): List<TableSyncer<*, *>> {
        return az.tribe.lifeplanner.data.sync.syncers.createAllSyncers(supabase, sharedDatabase)
    }

    private fun isNetworkException(e: Exception): Boolean {
        // Check exception type first (reliable, locale-independent)
        if (e is HttpRequestException) return true
        // Check causes recursively for platform-specific network exceptions
        var cause: Throwable? = e
        while (cause != null) {
            val name = cause::class.simpleName ?: ""
            if (name in networkExceptionNames) return true
            cause = cause.cause
        }
        // Fallback: check message for keywords (covers edge cases)
        val message = e.message ?: ""
        return networkKeywords.any { message.contains(it, ignoreCase = true) }
    }

    companion object {
        private val networkExceptionNames = setOf(
            "UnknownHostException", "ConnectException", "SocketException",
            "SocketTimeoutException", "NoRouteToHostException",
            "HttpRequestTimeoutException", "HttpRequestException",
            "ConnectTimeoutException", "NSURLErrorDomain"
        )
        private val networkKeywords = listOf(
            "Unable to resolve host", "Network is unreachable",
            "Connection refused", "Connection reset", "timeout"
        )
    }
}
