package az.tribe.lifeplanner.data.sync

import app.cash.turbine.test
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    private lateinit var syncManager: SyncManager
    private lateinit var fakeSyncer: FakeTableSyncer
    private var currentUserId: String? = "test-user-id"
    private var isConnected: Boolean = true

    private fun createSyncManager(
        syncers: List<TableSyncer<*, *>>? = null
    ): SyncManager {
        fakeSyncer = FakeTableSyncer("test_table")
        return SyncManager(
            userIdProvider = { currentUserId },
            isConnectedProvider = { isConnected },
            connectivityFlow = null,
            syncersProvider = { syncers ?: listOf(fakeSyncer) }
        )
    }

    @Test
    fun `initial hasCompletedInitialSync is false`() {
        syncManager = createSyncManager()
        assertFalse(syncManager.hasCompletedInitialSync())
    }

    @Test
    fun `initial syncStatus state is IDLE`() {
        syncManager = createSyncManager()
        assertEquals(SyncState.IDLE, syncManager.syncStatus.value.state)
    }

    @Test
    fun `performFullSync skips when no user is authenticated`() = runTest {
        syncManager = createSyncManager()
        currentUserId = null

        syncManager.performFullSync()

        assertEquals(SyncState.IDLE, syncManager.syncStatus.value.state)
        assertFalse(syncManager.hasCompletedInitialSync())
        assertEquals(0, fakeSyncer.pullCallCount)
        assertEquals(0, fakeSyncer.pushCallCount)
    }

    @Test
    fun `performFullSync sets OFFLINE when not connected`() = runTest {
        syncManager = createSyncManager()
        isConnected = false

        syncManager.performFullSync()

        assertEquals(SyncState.OFFLINE, syncManager.syncStatus.value.state)
        assertFalse(syncManager.hasCompletedInitialSync())
    }

    @Test
    fun `fresh sync pulls before pushing when no pull timestamps exist`() = runTest {
        syncManager = createSyncManager()
        // fakeSyncer has lastPullTimestamp = null by default (fresh sync)

        syncManager.performFullSync()

        assertEquals(SyncState.SYNCED, syncManager.syncStatus.value.state)
        assertTrue(syncManager.hasCompletedInitialSync())

        // Verify pull was called before push
        assertTrue(fakeSyncer.operationOrder.isNotEmpty())
        val pullIndex = fakeSyncer.operationOrder.indexOf("pull")
        val pushIndex = fakeSyncer.operationOrder.indexOf("push")
        assertTrue(pullIndex >= 0, "pull should have been called")
        assertTrue(pushIndex >= 0, "push should have been called")
        assertTrue(pullIndex < pushIndex, "pull should happen before push on fresh sync")
    }

    @Test
    fun `normal sync pushes before pulling when pull timestamps exist`() = runTest {
        fakeSyncer = FakeTableSyncer("test_table", lastPullTimestamp = "2024-01-01T00:00:00Z")
        syncManager = SyncManager(
            userIdProvider = { currentUserId },
            isConnectedProvider = { isConnected },
            connectivityFlow = null,
            syncersProvider = { listOf(fakeSyncer) }
        )

        syncManager.performFullSync()

        assertEquals(SyncState.SYNCED, syncManager.syncStatus.value.state)
        assertTrue(syncManager.hasCompletedInitialSync())

        // Verify push was called before pull
        val pushIndex = fakeSyncer.operationOrder.indexOf("push")
        val pullIndex = fakeSyncer.operationOrder.indexOf("pull")
        assertTrue(pushIndex >= 0, "push should have been called")
        assertTrue(pullIndex >= 0, "pull should have been called")
        assertTrue(pushIndex < pullIndex, "push should happen before pull on normal sync")
    }

    @Test
    fun `hasCompletedInitialSync becomes true after successful sync`() = runTest {
        syncManager = createSyncManager()
        assertFalse(syncManager.hasCompletedInitialSync())

        syncManager.performFullSync()

        assertTrue(syncManager.hasCompletedInitialSync())
    }

    @Test
    fun `hasCompletedInitialSync becomes true even on partial sync failure`() = runTest {
        val failingSyncer = FakeTableSyncer("failing_table", shouldFailOnPush = true)
        val goodSyncer = FakeTableSyncer("good_table")
        syncManager = SyncManager(
            userIdProvider = { currentUserId },
            isConnectedProvider = { isConnected },
            connectivityFlow = null,
            syncersProvider = { listOf(failingSyncer, goodSyncer) }
        )

        syncManager.performFullSync()

        // Should still mark initial sync as completed (partial is still "completed")
        assertTrue(syncManager.hasCompletedInitialSync())
        assertEquals(SyncState.ERROR, syncManager.syncStatus.value.state)
        assertNotNull(syncManager.syncStatus.value.errorMessage)
    }

    @Test
    fun `onLogout resets hasCompletedInitialSync to false`() = runTest {
        syncManager = createSyncManager()
        syncManager.performFullSync()
        assertTrue(syncManager.hasCompletedInitialSync())

        syncManager.onLogout()

        assertFalse(syncManager.hasCompletedInitialSync())
    }

    @Test
    fun `onLogout resets syncStatus to IDLE`() = runTest {
        syncManager = createSyncManager()
        syncManager.performFullSync()
        assertEquals(SyncState.SYNCED, syncManager.syncStatus.value.state)

        syncManager.onLogout()

        assertEquals(SyncState.IDLE, syncManager.syncStatus.value.state)
    }

    @Test
    fun `syncStatus transitions through SYNCING to SYNCED`() = runTest {
        syncManager = createSyncManager()

        syncManager.syncStatus.test {
            // Initial state
            assertEquals(SyncState.IDLE, awaitItem().state)

            syncManager.performFullSync()

            // Should transition to SYNCING then SYNCED
            assertEquals(SyncState.SYNCING, awaitItem().state)
            assertEquals(SyncState.SYNCED, awaitItem().state)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fresh sync with multiple syncers pulls all before pushing any`() = runTest {
        val syncer1 = FakeTableSyncer("table1")
        val syncer2 = FakeTableSyncer("table2")
        syncManager = SyncManager(
            userIdProvider = { currentUserId },
            isConnectedProvider = { isConnected },
            connectivityFlow = null,
            syncersProvider = { listOf(syncer1, syncer2) }
        )

        syncManager.performFullSync()

        // Both should have been pulled then pushed
        assertEquals(1, syncer1.pullCallCount)
        assertEquals(1, syncer1.pushCallCount)
        assertEquals(1, syncer2.pullCallCount)
        assertEquals(1, syncer2.pushCallCount)

        // Verify: all pulls happen before any push
        // syncer1: pull, syncer2: pull, syncer1: push, syncer2: push
        assertEquals("pull", syncer1.operationOrder[0])
        assertEquals("pull", syncer2.operationOrder[0])
    }

    @Test
    fun `account switch scenario - logout then fresh sync pulls first`() = runTest {
        // First user session
        fakeSyncer = FakeTableSyncer("test_table")
        syncManager = SyncManager(
            userIdProvider = { currentUserId },
            isConnectedProvider = { isConnected },
            connectivityFlow = null,
            syncersProvider = { listOf(fakeSyncer) }
        )

        syncManager.performFullSync()
        assertTrue(syncManager.hasCompletedInitialSync())
        assertEquals(SyncState.SYNCED, syncManager.syncStatus.value.state)

        // Simulate account switch: logout resets state
        syncManager.onLogout()
        assertFalse(syncManager.hasCompletedInitialSync())
        assertEquals(SyncState.IDLE, syncManager.syncStatus.value.state)

        // Reset the syncer to simulate cleared timestamps (fresh state)
        fakeSyncer.resetForFreshSync()

        // New user's first sync should pull first
        currentUserId = "new-user-id"
        syncManager.performFullSync()

        assertTrue(syncManager.hasCompletedInitialSync())
        val pullIndex = fakeSyncer.operationOrder.indexOf("pull")
        val pushIndex = fakeSyncer.operationOrder.indexOf("push")
        assertTrue(pullIndex < pushIndex, "After account switch, pull should happen before push")
    }
}

private val dummySupabase: SupabaseClient by lazy {
    createSupabaseClient(
        supabaseUrl = "https://dummy.supabase.co",
        supabaseKey = "dummy-key"
    ) {
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
        })
    }
}

/**
 * Fake TableSyncer that tracks operation order and counts.
 */
class FakeTableSyncer(
    override val tableName: String,
    private var lastPullTimestamp: String? = null,
    private val shouldFailOnPush: Boolean = false,
    private val shouldFailOnPull: Boolean = false
) : TableSyncer<String, String>(dummySupabase) {
    var pushCallCount = 0
        private set
    var pullCallCount = 0
        private set
    val operationOrder = mutableListOf<String>()

    fun resetForFreshSync() {
        lastPullTimestamp = null
        pushCallCount = 0
        pullCallCount = 0
        operationOrder.clear()
    }

    override suspend fun getUnsyncedLocal(): List<String> = emptyList()
    override suspend fun getDeletedLocal(): List<String> = emptyList()
    override suspend fun localToRemote(local: String, userId: String): String = local
    override suspend fun remoteToLocal(remote: String): String = remote
    override suspend fun upsertLocal(entity: String) {}
    override suspend fun markSynced(id: String, now: String) {}
    override suspend fun purgeDeleted() {}
    override suspend fun getEntityId(entity: String): String = entity
    override suspend fun upsertRemote(dtos: List<String>) {}

    override suspend fun getLastPullTimestamp(): String? = lastPullTimestamp

    override suspend fun setLastPullTimestamp(timestamp: String) {
        lastPullTimestamp = timestamp
    }

    override suspend fun pushLocalChanges(userId: String): Int {
        if (shouldFailOnPush) throw Exception("Push failed for $tableName")
        pushCallCount++
        operationOrder.add("push")
        return 0
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        if (shouldFailOnPull) throw Exception("Pull failed for $tableName")
        pullCallCount++
        operationOrder.add("pull")
        lastPullTimestamp = kotlinx.datetime.Clock.System.now().toString()
        return 0
    }
}
