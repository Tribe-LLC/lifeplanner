package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.database.HabitCheckInEntity
import az.tribe.lifeplanner.database.HabitEntity
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.HabitFrequency
import az.tribe.lifeplanner.domain.model.Habit
import az.tribe.lifeplanner.domain.model.HabitCheckIn
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun HabitEntity.toDomain(): Habit {
    return Habit(
        id = id,
        title = title,
        description = description,
        category = GoalCategory.valueOf(category),
        frequency = HabitFrequency.fromString(frequency),
        targetCount = targetCount.toInt(),
        currentStreak = currentStreak.toInt(),
        longestStreak = longestStreak.toInt(),
        totalCompletions = totalCompletions.toInt(),
        lastCompletedDate = lastCompletedDate?.let { LocalDate.parse(it) },
        linkedGoalId = linkedGoalId,
        correlationScore = correlationScore.toFloat(),
        isActive = isActive == 1L,
        createdAt = LocalDateTime.parse(createdAt),
        reminderTime = reminderTime
    )
}

fun Habit.toEntity(): HabitEntity {
    return HabitEntity(
        id = id,
        title = title,
        description = description,
        category = category.name,
        frequency = frequency.name,
        targetCount = targetCount.toLong(),
        currentStreak = currentStreak.toLong(),
        longestStreak = longestStreak.toLong(),
        totalCompletions = totalCompletions.toLong(),
        lastCompletedDate = lastCompletedDate?.toString(),
        linkedGoalId = linkedGoalId,
        correlationScore = correlationScore.toDouble(),
        isActive = if (isActive) 1L else 0L,
        createdAt = createdAt.toString(),
        reminderTime = reminderTime
    )
}

fun HabitCheckInEntity.toDomain(): HabitCheckIn {
    return HabitCheckIn(
        id = id,
        habitId = habitId,
        date = LocalDate.parse(date),
        completed = completed == 1L,
        notes = notes
    )
}

fun HabitCheckIn.toEntity(): HabitCheckInEntity {
    return HabitCheckInEntity(
        id = id,
        habitId = habitId,
        date = date.toString(),
        completed = if (completed) 1L else 0L,
        notes = notes
    )
}

fun List<HabitEntity>.toDomainHabits(): List<Habit> {
    return map { it.toDomain() }
}

fun List<HabitCheckInEntity>.toDomainCheckIns(): List<HabitCheckIn> {
    return map { it.toDomain() }
}

@OptIn(ExperimentalUuidApi::class)
fun createNewHabit(
    title: String,
    description: String = "",
    category: GoalCategory,
    frequency: HabitFrequency,
    targetCount: Int = 1,
    linkedGoalId: String? = null,
    reminderTime: String? = null
): Habit {
    return Habit(
        id = Uuid.random().toString(),
        title = title,
        description = description,
        category = category,
        frequency = frequency,
        targetCount = targetCount,
        currentStreak = 0,
        longestStreak = 0,
        totalCompletions = 0,
        lastCompletedDate = null,
        linkedGoalId = linkedGoalId,
        correlationScore = 0f,
        isActive = true,
        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
        reminderTime = reminderTime
    )
}

@OptIn(ExperimentalUuidApi::class)
fun createNewCheckIn(
    habitId: String,
    date: LocalDate,
    completed: Boolean = true,
    notes: String = ""
): HabitCheckIn {
    return HabitCheckIn(
        id = Uuid.random().toString(),
        habitId = habitId,
        date = date,
        completed = completed,
        notes = notes
    )
}
