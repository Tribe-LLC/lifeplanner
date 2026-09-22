package az.tribe.lifeplanner.di

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import co.touchlab.kermit.Logger

/**
 * Runs the `.sqm` migration chain one version at a time, tolerating a step that has already been
 * applied.
 *
 * This is the whole of the iOS upgrade path: [DatabaseDriverFactory] hands it to
 * `NativeSqliteDriver`, and unlike Android, which runs a hand-written chain from `onOpen` and never
 * lets the `.sqm` files execute at all, iOS has nothing else. It lives in `commonMain` rather than
 * `iosMain` so that [az.tribe.lifeplanner.di.SqmMigrationTest] can run the real thing on the JVM
 * instead of a copy that could drift from it.
 *
 * SQLDelight guards each step with `oldVersion <= N && newVersion > N`, so a device sitting exactly
 * at version N re-runs `N.sqm`, which was already folded into the schema it installed with. That
 * replay is expected and is why duplicate-column failures are swallowed. Everything else rethrows:
 * a migration that genuinely cannot apply should surface rather than leave a half-migrated
 * database behind.
 */
internal class DefensiveSchema(
    private val delegate: SqlSchema<QueryResult.Value<Unit>>
) : SqlSchema<QueryResult.Value<Unit>> by delegate {

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion
    ): QueryResult.Value<Unit> {
        Logger.i("DefensiveSchema") { "Migrating from $oldVersion to $newVersion" }

        var currentVersion = oldVersion
        while (currentVersion < newVersion) {
            val nextVersion = currentVersion + 1
            try {
                Logger.i("DefensiveSchema") { "Running migration $currentVersion -> $nextVersion" }
                delegate.migrate(driver, currentVersion, nextVersion, *callbacks)
            } catch (e: Exception) {
                val message = e.message ?: ""
                if (message.contains("duplicate column name", ignoreCase = true)) {
                    Logger.w("DefensiveSchema") { "Ignoring duplicate column error: $message" }
                } else {
                    Logger.e("DefensiveSchema") { "Migration error: $message" }
                    throw e
                }
            }
            currentVersion = nextVersion
        }

        return QueryResult.Value(Unit)
    }
}
