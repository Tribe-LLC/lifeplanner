package az.tribe.lifeplanner.data.sync.syncers

import az.tribe.lifeplanner.data.sync.TableSyncer
import az.tribe.lifeplanner.data.sync.dto.UserSyncDto
import az.tribe.lifeplanner.database.UserEntity
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import com.russhwolf.settings.Settings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock

class UserTableSyncer(
    supabase: SupabaseClient,
    private val db: SharedDatabase
) : TableSyncer<UserEntity, UserSyncDto>(supabase) {

    override val tableName = "users"
    private val settings = Settings()

    override suspend fun upsertRemote(dtos: List<UserSyncDto>) {
        supabase.postgrest[tableName].upsert(dtos)
    }

    override suspend fun getUnsyncedLocal(): List<UserEntity> {
        return db { it.lifePlannerDBQueries.getUnsyncedUsers().executeAsList().map { row ->
            UserEntity(
                id = row.id,
                firebaseUid = row.firebaseUid,
                email = row.email,
                displayName = row.displayName,
                isGuest = row.isGuest,
                selectedSymbol = row.selectedSymbol,
                priorities = row.priorities,
                ageRange = row.ageRange,
                profession = row.profession,
                relationshipStatus = row.relationshipStatus,
                mindset = row.mindset,
                hasCompletedOnboarding = row.hasCompletedOnboarding,
                createdAt = row.createdAt,
                lastSyncedAt = row.lastSyncedAt,
                sync_updated_at = row.sync_updated_at,
                is_deleted = row.is_deleted,
                sync_version = row.sync_version
            )
        }}
    }

    override suspend fun getDeletedLocal(): List<UserEntity> {
        return emptyList()
    }

    override suspend fun localToRemote(local: UserEntity, userId: String) = UserSyncDto(
        id = local.id,
        userId = userId,
        firebaseUid = local.firebaseUid,
        email = local.email,
        displayName = local.displayName,
        isGuest = local.isGuest != 0L,
        selectedSymbol = local.selectedSymbol,
        priorities = local.priorities,
        ageRange = local.ageRange,
        profession = local.profession,
        relationshipStatus = local.relationshipStatus,
        mindset = local.mindset,
        hasCompletedOnboarding = local.hasCompletedOnboarding != 0L,
        createdAt = local.createdAt,
        updatedAt = local.sync_updated_at ?: Clock.System.now().toString(),
        isDeleted = local.is_deleted != 0L,
        syncVersion = local.sync_version
    )

    override suspend fun remoteToLocal(remote: UserSyncDto): UserEntity {
        return UserEntity(
            id = remote.id,
            firebaseUid = remote.firebaseUid,
            email = remote.email,
            displayName = remote.displayName,
            isGuest = if (remote.isGuest) 1L else 0L,
            selectedSymbol = remote.selectedSymbol,
            priorities = remote.priorities,
            ageRange = remote.ageRange,
            profession = remote.profession,
            relationshipStatus = remote.relationshipStatus,
            mindset = remote.mindset,
            hasCompletedOnboarding = if (remote.hasCompletedOnboarding) 1L else 0L,
            createdAt = remote.createdAt,
            lastSyncedAt = Clock.System.now().toString(),
            sync_updated_at = remote.updatedAt,
            is_deleted = if (remote.isDeleted) 1L else 0L,
            sync_version = remote.syncVersion
        )
    }

    override suspend fun upsertLocal(entity: UserEntity) {
        db { it.lifePlannerDBQueries.upsertUserFromSync(
            id = entity.id,
            firebaseUid = entity.firebaseUid,
            email = entity.email,
            displayName = entity.displayName,
            isGuest = entity.isGuest,
            selectedSymbol = entity.selectedSymbol,
            priorities = entity.priorities,
            ageRange = entity.ageRange,
            profession = entity.profession,
            relationshipStatus = entity.relationshipStatus,
            mindset = entity.mindset,
            hasCompletedOnboarding = entity.hasCompletedOnboarding,
            createdAt = entity.createdAt,
            lastSyncedAt = entity.lastSyncedAt,
            sync_updated_at = entity.sync_updated_at,
            is_deleted = entity.is_deleted,
            sync_version = entity.sync_version
        )}
    }

    override suspend fun markSynced(id: String, now: String) {
        db { it.lifePlannerDBQueries.markUserSynced(now, id) }
    }

    override suspend fun purgeDeleted() {
        // No-op: we don't soft-delete user records
    }

    override suspend fun getEntityId(entity: UserEntity) = entity.id

    override suspend fun getLastPullTimestamp(): String? {
        return settings.getStringOrNull("sync_pull_users")
    }

    override suspend fun setLastPullTimestamp(timestamp: String) {
        settings.putString("sync_pull_users", timestamp)
    }

    override suspend fun pullRemoteChanges(userId: String): Int {
        val lastPull = getLastPullTimestamp()
        val now = Clock.System.now().toString()

        val remoteItems = if (lastPull != null) {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId); gt("updated_at", lastPull) } }
                .decodeList<UserSyncDto>()
        } else {
            supabase.postgrest[tableName]
                .select { filter { eq("user_id", userId) } }
                .decodeList<UserSyncDto>()
        }

        remoteItems.forEach { remote ->
            val local = remoteToLocal(remote)
            upsertLocal(local)
        }

        setLastPullTimestamp(now)
        if (remoteItems.isNotEmpty()) {
            Logger.d("SyncEngine") { "Pulled ${remoteItems.size} items from $tableName" }
        }
        return remoteItems.size
    }
}
