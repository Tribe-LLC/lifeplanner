package az.tribe.lifeplanner.infrastructure

import az.tribe.lifeplanner.database.LifePlannerDB
import az.tribe.lifeplanner.di.DatabaseDriverFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class SharedDatabase(
    private val driverProvider: DatabaseDriverFactory,
) {
    internal var database: LifePlannerDB? = null
    internal val dbMutex = Mutex()

    internal fun nowTimestamp(): String = Clock.System.now().toString()

    internal suspend fun initDatabase() {
        if (database == null) {
            dbMutex.withLock {
                if (database == null) {
                    database = LifePlannerDB.invoke(
                        driver = driverProvider.createDriver()
                    )
                }
            }
        }
    }

    suspend operator fun <R> invoke(block: suspend (LifePlannerDB) -> R): R {
        initDatabase()
        return block(database!!)
    }

    /**
     * Delete all local data from every table. Used on logout.
     */
    suspend fun clearAllLocalData() {
        this { db ->
            val q = db.lifePlannerDBQueries
            // Tier 3 (depends on Tier 2)
            q.deleteAllChatMessages()
            // Tier 2 (depends on Tier 1)
            q.deleteAllMilestones()
            q.deleteAllGoalHistory()
            q.deleteAllGoalDependencies()
            q.deleteAllHabitCheckIns()
            q.deleteAllJournalEntries()
            q.deleteAllChatSessions()
            q.deleteAllReminders()
            q.deleteAllFocusSessions()
            q.deleteAllChallenges()
            q.deleteAllCoachGroupMembers()
            q.deleteAllScheduledNotifications()
            q.deleteAllReviewReports()
            q.deleteAllUserProgress()
            q.deleteAllBeginnerObjectives()
            q.deleteAllDayRecaps()
            // Tier 1 (no deps)
            q.deleteAllAbilityHabitLinks()
            q.deleteAllAbilityGoalLinks()
            q.deleteAllAbilities()
            q.deleteAllLifeValues()
            q.deleteAllIdentityStatements()
            q.deleteAllDecisionProfiles()
            q.deleteAllDecisions()
            q.deleteAllGoals()
            q.deleteAllHabits()
            q.deleteAllBadges()
            q.deleteAllCustomCoaches()
            q.deleteAllCoachGroups()
            q.deleteAllUserSituations()
            q.deleteAllUsers()
            q.deleteAllCachedPersonas()
            q.deleteAllScreenTimeEvents()
            // Personal, and previously left behind: signing in as someone else inherited the
            // last account's read lessons, and would have inherited their wheel scores.
            q.deleteAllKnowledgeReads()
            q.deleteAllWheelScores()
            q.deleteAllWheelSnapshots()
            // v4 areas: spends, workouts, budgets and trips are as personal as it gets.
            q.deleteAllLifeLogs()
            q.deleteAllBudgets()
            q.deleteAllTrips()
            q.deleteAllTripItems()
            // KnowledgeLessonEntity / KnowledgeCollectionEntity are deliberately kept. They are
            // server-authored content, identical for every user, so there is nothing to leak and
            // keeping them means the Learn map is populated before the next fetch returns.
        }
    }
}
