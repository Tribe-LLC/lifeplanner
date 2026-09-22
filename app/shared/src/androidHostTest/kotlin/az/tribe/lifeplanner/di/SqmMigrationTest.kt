package az.tribe.lifeplanner.di

import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import az.tribe.lifeplanner.database.LifePlannerDB
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the `.sqm` chain, which is the entire iOS upgrade path and had no test of any kind.
 *
 * The two platforms migrate by completely different routes. Android overrides `onUpgrade` without
 * calling `super`, so the `.sqm` files never execute there and a hand-written chain in `onOpen`
 * does the work ([DatabaseMigrationsTest] covers that one). iOS runs the `.sqm` files through
 * [DefensiveSchema] and has nothing else. A schema change that reaches one route and not the other
 * breaks exactly one platform, and until now only one route was checked.
 *
 * Nothing verifies the `.sqm` files at build time either: `verifyMigrations` is off and no schema
 * snapshots are committed, so a broken step compiles, ships, and crashes on open.
 */
@RunWith(RobolectricTestRunner::class)
// The library targets SDK 37, past what Robolectric 4.16 emulates, so pin it as
// PreviewScreenshots does. SQLite behaviour is not what varies across these levels.
@Config(sdk = [35])
class SqmMigrationTest {

    @Test
    fun `a 2_3 install reaches the current schema through the sqm chain`() {
        val db = legacy23Database()

        migrateLikeIos(db)

        val missing = columnsMissingFromFreshInstall(db)
        assertTrue(
            missing.isEmpty(),
            "an iOS 2.3 upgrader never receives ${missing.size} columns a fresh install has, so " +
                "every query that reads one crashes on their device: " + missing.joinToString()
        )
    }

    @Test
    fun `the sqm chain and the Android chain land on the same schema`() {
        val viaSqm = legacy23Database().also { migrateLikeIos(it) }
        val viaAndroidChain = legacy23Database().also { runAndroidMigrations(it) }

        // The same 2.3 database taken forward by each platform's own route. These are separate
        // implementations of one intention, so they are free to drift, and a column that only one
        // of them adds is a crash on the other platform and nowhere else.
        val sqmOnly = schemaSnapshot(viaSqm) - schemaSnapshot(viaAndroidChain).toSet()
        val androidOnly = schemaSnapshot(viaAndroidChain) - schemaSnapshot(viaSqm).toSet()
        assertEquals(
            emptyList<String>() to emptyList<String>(),
            sqmOnly to androidOnly,
            "the two migration routes disagree. Only iOS gets: $sqmOnly. Only Android gets: $androidOnly"
        )
    }

    @Test
    fun `the sqm chain keeps the rows it migrates`() {
        val db = legacy23Database()
        db.execSQL(
            "INSERT INTO GoalEntity (id, category, title, description, status, timeline, dueDate) " +
                "VALUES ('g1', 'CAREER', 'Get promoted', 'd', 'NOT_STARTED', 'MEDIUM_TERM', '2026-12-01')"
        )
        db.execSQL(
            "INSERT INTO HabitEntity (id, title, category, createdAt) VALUES " +
                "('h1', 'Morning walk', 'BODY', '2026-01-01T08:00')"
        )
        db.execSQL(
            "INSERT INTO HealthMetricEntity (id, metricType, value_, unit, date, recordedAt, createdAt) " +
                "VALUES ('m1', 'STEPS', 8000.0, 'count', '2026-01-01', '2026-01-01T20:00', '2026-01-01T20:00')"
        )

        migrateLikeIos(db)

        // The question this whole suite exists to answer: the schema moves, the rows do not.
        db.query("SELECT title FROM GoalEntity WHERE id = 'g1'").use {
            assertTrue(it.moveToFirst(), "the goal did not survive the migration")
            assertEquals("Get promoted", it.getString(0))
        }
        db.query("SELECT title, completionSource FROM HabitEntity WHERE id = 'h1'").use {
            assertTrue(it.moveToFirst(), "the habit did not survive the migration")
            assertEquals("Morning walk", it.getString(0))
            assertTrue(it.isNull(1), "completionSource should start null for an old habit")
        }
        // Health predates 2.3, so these rows are the oldest thing a health user owns.
        db.query("SELECT metricType, value_ FROM HealthMetricEntity WHERE id = 'm1'").use {
            assertTrue(it.moveToFirst(), "the health metric did not survive the migration")
            assertEquals("STEPS", it.getString(0))
            assertEquals(8000.0, it.getDouble(1))
        }
    }

    // -- fixture ---------------------------------------------------------------------------

    /** Exactly what `NativeSqliteDriver` does on an iOS device whose database is still at 2.3. */
    private fun migrateLikeIos(db: SupportSQLiteDatabase) {
        DefensiveSchema(LifePlannerDB.Schema.synchronous())
            .migrate(
                driver = AndroidSqliteDriver(db),
                oldVersion = SCHEMA_VERSION_2_3,
                newVersion = LifePlannerDB.Schema.synchronous().version
            )
    }

    /**
     * The database a 2.3 install holds, read from `schema-2.3.sql` rather than written here, so it
     * is a record of what shipped instead of whatever makes today's test pass.
     */
    private fun legacy23Database(): SupportSQLiteDatabase {
        val sql = checkNotNull(
            javaClass.classLoader?.getResourceAsStream(SCHEMA_RESOURCE)
        ) { "$SCHEMA_RESOURCE is not on the test classpath" }
            .bufferedReader()
            .readText()

        val db = emptyDatabase()
        sql.lineSequence()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { db.execSQL(it) }
        return db
    }

    private companion object {
        const val SCHEMA_RESOURCE = "schema-2.3.sql"

        /**
         * 2.3 shipped from 003b892, where the highest migration file was 17.sqm. SQLDelight derives
         * the schema version as the highest migration plus one, so a 2.3 device sits at 18.
         *
         * Starting here rather than at a later version is deliberate: SQLDelight guards each step
         * with `oldVersion <= N`, so the lower the start, the more steps run. A device that turns
         * out to be at 21 or 23 runs a subset of what this test covers.
         */
        const val SCHEMA_VERSION_2_3 = 18L
    }
}
