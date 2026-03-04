package az.tribe.lifeplanner.data.repository

import az.tribe.lifeplanner.data.mapper.toDomain
import az.tribe.lifeplanner.data.mapper.parseLocalDateTime
import az.tribe.lifeplanner.domain.enum.BadgeType
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.model.Badge
import az.tribe.lifeplanner.domain.model.DaySnapshot
import az.tribe.lifeplanner.domain.model.GoalChangeWithTitle
import az.tribe.lifeplanner.domain.model.HabitDayStatus
import az.tribe.lifeplanner.domain.model.HabitDaySummary
import az.tribe.lifeplanner.domain.repository.RetrospectiveRepository
import az.tribe.lifeplanner.infrastructure.SharedDatabase
import kotlinx.datetime.LocalDate

class RetrospectiveRepositoryImpl(
    private val database: SharedDatabase
) : RetrospectiveRepository {

    override suspend fun getDaySnapshot(date: LocalDate): DaySnapshot {
        val dateStr = date.toString()

        // 1. Habits with check-in status for this date
        val habitsRaw = database.getHabitCheckInsWithHabitForDate(dateStr)
        val habitStatuses = habitsRaw.map { row ->
            HabitDayStatus(
                habitId = row.id,
                title = row.title,
                category = try { GoalCategory.valueOf(row.category) } catch (_: Exception) { GoalCategory.CAREER },
                wasCompleted = row.completed == 1L,
                notes = row.checkInNotes ?: ""
            )
        }

        val habitSummary = HabitDaySummary(
            totalHabits = habitStatuses.size,
            completedHabits = habitStatuses.count { it.wasCompleted },
            habits = habitStatuses
        )

        // 2. Journal entries for this date (reuse existing query)
        val journalEntries = database.getJournalEntriesByDate(dateStr).map { it.toDomain() }

        // 3. Focus sessions for this date
        val focusSessions = database.getFocusSessionsByDate(dateStr).map { it.toDomain() }

        // 4. Goal changes on this date
        val goalChangesRaw = database.getGoalChangesOnDate(dateStr)
        val goalChanges = goalChangesRaw.map { row ->
            GoalChangeWithTitle(
                id = row.id,
                goalId = row.goalId,
                goalTitle = row.goalTitle ?: "Deleted Goal",
                field = row.fieldName,
                oldValue = row.oldValue,
                newValue = row.newValue,
                changedAt = parseLocalDateTime(row.changedAt)
            )
        }

        // 5. Badges earned on this date
        val badges = database.getBadgesEarnedOnDate(dateStr).map { entity ->
            Badge(
                id = entity.id,
                type = try { BadgeType.valueOf(entity.badgeType) } catch (_: Exception) { BadgeType.FIRST_STEP },
                earnedAt = parseLocalDateTime(entity.earnedAt),
                isNew = entity.isNew == 1L
            )
        }

        return DaySnapshot(
            date = date,
            habitSummary = habitSummary,
            journalEntries = journalEntries,
            focusSessions = focusSessions,
            goalChanges = goalChanges,
            badgesEarned = badges
        )
    }

    override suspend fun getDatesWithActivity(start: LocalDate, end: LocalDate): Set<LocalDate> {
        val startStr = start.toString()
        val endStr = end.toString()

        return database.getDatesWithActivity(
            checkInStart = startStr, checkInEnd = endStr,
            journalStart = startStr, journalEnd = endStr,
            focusStart = startStr, focusEnd = endStr,
            historyStart = startStr, historyEnd = endStr,
            badgeStart = startStr, badgeEnd = endStr
        ).mapNotNull { dateStr ->
            try { LocalDate.parse(dateStr) } catch (_: Exception) { null }
        }.toSet()
    }
}
