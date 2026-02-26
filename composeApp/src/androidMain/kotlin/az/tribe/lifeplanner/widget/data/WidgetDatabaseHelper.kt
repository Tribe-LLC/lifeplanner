package az.tribe.lifeplanner.widget.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import az.tribe.lifeplanner.di.DB_NAME
import az.tribe.lifeplanner.widget.WidgetDashboardData
import az.tribe.lifeplanner.widget.WidgetHabitData
import co.touchlab.kermit.Logger
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Lightweight read-only SQLite helper for widget data queries.
 * Reads directly from the same DB file used by the main app.
 */
object WidgetDatabaseHelper {

    private fun openDatabase(context: Context): SQLiteDatabase? {
        return try {
            val dbPath = context.getDatabasePath(DB_NAME)
            if (!dbPath.exists()) return null
            SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: Exception) {
            Logger.w("WidgetDatabaseHelper") { "Failed to open database: ${e.message}" }
            null
        }
    }

    fun getDashboardData(context: Context): WidgetDashboardData {
        val db = openDatabase(context) ?: return WidgetDashboardData()
        return try {
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

            // Get user progress
            var currentStreak = 0
            var totalXp = 0
            var currentLevel = 1
            db.rawQuery("SELECT currentStreak, totalXp, currentLevel FROM UserProgressEntity LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    currentStreak = cursor.getInt(0)
                    totalXp = cursor.getInt(1)
                    currentLevel = cursor.getInt(2)
                }
            }

            // Get active goals count
            var activeGoals = 0
            db.rawQuery(
                "SELECT COUNT(*) FROM GoalEntity WHERE status != 'COMPLETED' AND (isArchived = 0 OR isArchived IS NULL)",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) activeGoals = cursor.getInt(0)
            }

            // Get total active habits and done today
            var habitsTotal = 0
            db.rawQuery("SELECT COUNT(*) FROM HabitEntity WHERE isActive = 1", null).use { cursor ->
                if (cursor.moveToFirst()) habitsTotal = cursor.getInt(0)
            }

            var habitsDoneToday = 0
            db.rawQuery(
                "SELECT COUNT(*) FROM HabitCheckInEntity WHERE date = ? AND completed = 1",
                arrayOf(today)
            ).use { cursor ->
                if (cursor.moveToFirst()) habitsDoneToday = cursor.getInt(0)
            }

            WidgetDashboardData(
                currentStreak = currentStreak,
                totalXp = totalXp,
                currentLevel = currentLevel,
                activeGoals = activeGoals,
                habitsTotal = habitsTotal,
                habitsDoneToday = habitsDoneToday,
                lastUpdated = today
            )
        } catch (e: Exception) {
            Logger.w("WidgetDatabaseHelper") { "Error reading dashboard data: ${e.message}" }
            WidgetDashboardData()
        } finally {
            db.close()
        }
    }

    fun getHabitsForWidget(context: Context, limit: Int = 6): List<WidgetHabitData> {
        val db = openDatabase(context) ?: return emptyList()
        return try {
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

            val habits = mutableListOf<WidgetHabitData>()
            db.rawQuery(
                """
                SELECT h.id, h.title, h.currentStreak, h.category,
                    CASE WHEN c.id IS NOT NULL THEN 1 ELSE 0 END AS isCompleted
                FROM HabitEntity h
                LEFT JOIN HabitCheckInEntity c ON h.id = c.habitId AND c.date = ? AND c.completed = 1
                WHERE h.isActive = 1
                ORDER BY isCompleted ASC, h.currentStreak DESC
                LIMIT ?
                """.trimIndent(),
                arrayOf(today, limit.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    habits.add(
                        WidgetHabitData(
                            id = cursor.getString(0),
                            title = cursor.getString(1),
                            currentStreak = cursor.getInt(2),
                            category = cursor.getString(3),
                            isCompletedToday = cursor.getInt(4) == 1
                        )
                    )
                }
            }
            habits
        } catch (e: Exception) {
            Logger.w("WidgetDatabaseHelper") { "Error reading habits: ${e.message}" }
            emptyList()
        } finally {
            db.close()
        }
    }

    /**
     * Perform a habit check-in directly from the widget.
     * Returns true if the check-in was successful.
     */
    fun performHabitCheckIn(context: Context, habitId: String): Boolean {
        val dbPath = context.getDatabasePath(DB_NAME)
        if (!dbPath.exists()) return false

        val db = try {
            SQLiteDatabase.openDatabase(dbPath.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (e: Exception) {
            Logger.w("WidgetDatabaseHelper") { "Failed to open DB for write: ${e.message}" }
            return false
        }

        return try {
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

            // Check if already checked in
            var alreadyCheckedIn = false
            db.rawQuery(
                "SELECT COUNT(*) FROM HabitCheckInEntity WHERE habitId = ? AND date = ? AND completed = 1",
                arrayOf(habitId, today)
            ).use { cursor ->
                if (cursor.moveToFirst()) alreadyCheckedIn = cursor.getInt(0) > 0
            }

            if (alreadyCheckedIn) return true

            val id = java.util.UUID.randomUUID().toString()

            db.beginTransaction()
            try {
                // Insert check-in
                db.execSQL(
                    "INSERT INTO HabitCheckInEntity (id, habitId, date, completed, notes) VALUES (?, ?, ?, 1, '')",
                    arrayOf(id, habitId, today)
                )

                // Update streak
                db.execSQL(
                    "UPDATE HabitEntity SET currentStreak = currentStreak + 1, totalCompletions = totalCompletions + 1, lastCompletedDate = ? WHERE id = ?",
                    arrayOf(today, habitId)
                )

                // Update longest streak if needed
                db.execSQL(
                    "UPDATE HabitEntity SET longestStreak = currentStreak WHERE id = ? AND currentStreak > longestStreak",
                    arrayOf(habitId)
                )

                // Add XP (5 for habit check-in)
                db.execSQL(
                    "UPDATE UserProgressEntity SET totalXp = totalXp + 5, habitsCompleted = habitsCompleted + 1"
                )

                db.setTransactionSuccessful()
                true
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Logger.w("WidgetDatabaseHelper") { "Error performing check-in: ${e.message}" }
            false
        } finally {
            db.close()
        }
    }
}
