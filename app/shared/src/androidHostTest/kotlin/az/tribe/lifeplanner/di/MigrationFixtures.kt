package az.tribe.lifeplanner.di

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import az.tribe.lifeplanner.database.LifePlannerDB
import org.robolectric.RuntimeEnvironment

/**
 * Shared by the two migration suites, which check the same journey by different routes:
 * [DatabaseMigrationsTest] for the hand-written chain Android runs, [SqmMigrationTest] for the
 * `.sqm` files iOS runs. Both compare against [freshInstallDatabase], so the comparison has to
 * mean the same thing in both.
 */

/** An open, table-less database to build a schema into. */
internal fun emptyDatabase(): SupportSQLiteDatabase {
    val callback = object : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) = Unit
        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    val configuration = SupportSQLiteOpenHelper.Configuration
        .builder(RuntimeEnvironment.getApplication())
        .name(null) // in-memory, so each test starts clean
        .callback(callback)
        .build()
    return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
}

/**
 * What a clean 3.0 install gets: SQLDelight's own `Schema.create`, which is the only definition of
 * the current schema that cannot drift from the `.sq` files.
 */
internal fun freshInstallDatabase(): SupportSQLiteDatabase {
    val db = emptyDatabase()
    LifePlannerDB.Schema.synchronous().create(AndroidSqliteDriver(db))
    return db
}

/** Every table and column, so a difference shows up as a diff rather than a guess. */
internal fun schemaSnapshot(db: SupportSQLiteDatabase): List<String> {
    val tables = buildList {
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' " +
                "AND name != 'android_metadata' ORDER BY name"
        ).use { while (it.moveToNext()) add(it.getString(0)) }
    }
    return tables.flatMap { table ->
        buildList {
            db.query("PRAGMA table_info($table)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) add("$table.${cursor.getString(nameIndex)}")
            }
        }.sorted()
    }
}

internal fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
    db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
        .use { it.moveToFirst() }

/** The columns a fresh install has and [migrated] does not, which is the only direction that crashes. */
internal fun columnsMissingFromFreshInstall(migrated: SupportSQLiteDatabase): List<String> {
    val actual = schemaSnapshot(migrated).toSet()
    return schemaSnapshot(freshInstallDatabase()).filterNot { it in actual }
}
