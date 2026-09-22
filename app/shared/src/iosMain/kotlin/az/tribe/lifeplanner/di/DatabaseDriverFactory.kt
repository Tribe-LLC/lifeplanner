@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package az.tribe.lifeplanner.di

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import az.tribe.lifeplanner.database.LifePlannerDB
import co.touchlab.kermit.Logger
import platform.Foundation.NSFileManager
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

private const val PREFS_KEY_DB_SCHEMA_VERSION = "db_schema_version"
private const val CURRENT_SCHEMA_VERSION = 2 // Increment this when you want to force a fresh DB for all users

actual class DatabaseDriverFactory {
    actual suspend fun createDriver(): SqlDriver {
        // Check if we need to reset the database for users upgrading from v1
        resetDatabaseIfNeeded()

        return NativeSqliteDriver(
            schema = DefensiveSchema(LifePlannerDB.Schema.synchronous()),
            name = DB_NAME
        )
    }

    /**
     * Reset database for users upgrading from older versions (v1).
     * This ensures a clean slate without migration errors.
     */
    private fun resetDatabaseIfNeeded() {
        val userDefaults = NSUserDefaults.standardUserDefaults
        val storedVersion = userDefaults.integerForKey(PREFS_KEY_DB_SCHEMA_VERSION).toInt()

        if (storedVersion < CURRENT_SCHEMA_VERSION) {
            Logger.i("DatabaseDriverFactory") { "Upgrading from schema version $storedVersion to $CURRENT_SCHEMA_VERSION" }

            // Get the database file path
            val fileManager = NSFileManager.defaultManager
            val libraryPaths = NSFileManager.defaultManager.URLsForDirectory(
                NSLibraryDirectory,
                NSUserDomainMask
            )
            val libraryPath = (libraryPaths.firstOrNull() as? platform.Foundation.NSURL)?.path

            if (libraryPath != null) {
                val dbPath = "$libraryPath/$DB_NAME"

                // Delete database files
                listOf(dbPath, "$dbPath-shm", "$dbPath-wal", "$dbPath-journal").forEach { path ->
                    if (fileManager.fileExistsAtPath(path)) {
                        try {
                            fileManager.removeItemAtPath(path, null)
                            Logger.i("DatabaseDriverFactory") { "Deleted: $path" }
                        } catch (e: Exception) {
                            Logger.w("DatabaseDriverFactory") { "Failed to delete $path: ${e.message}" }
                        }
                    }
                }
            }

            // Update stored version
            userDefaults.setInteger(CURRENT_SCHEMA_VERSION.toLong(), PREFS_KEY_DB_SCHEMA_VERSION)
            Logger.i("DatabaseDriverFactory") { "Database reset complete, now at schema version $CURRENT_SCHEMA_VERSION" }
        }
    }
}
