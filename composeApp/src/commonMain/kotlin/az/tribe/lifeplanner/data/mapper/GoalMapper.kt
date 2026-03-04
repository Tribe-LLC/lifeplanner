package az.tribe.lifeplanner.data.mapper


import az.tribe.lifeplanner.data.model.GeminiResponseDto
import az.tribe.lifeplanner.data.model.GoalGenerationResponse
import az.tribe.lifeplanner.data.model.QuestionGenerationResponse
import co.touchlab.kermit.Logger
import az.tribe.lifeplanner.database.GoalEntity
import az.tribe.lifeplanner.database.MilestoneEntity
import az.tribe.lifeplanner.domain.model.Goal
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.Milestone
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Updated mapper to handle structured JSON responses from Gemini
 * Now parses actual goal data instead of returning hardcoded values
 */
fun GeminiResponseDto.getQuestionGenerationResponse(): QuestionGenerationResponse? {
    return try {
        val jsonText = candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
        jsonText?.let {
            Json.decodeFromString<QuestionGenerationResponse>(it)
        }
    } catch (e: Exception) {
        Logger.e("GoalMapper", e) { "Failed to parse question generation response: ${e.message}" }
        null
    }
}

/**
 * Extension to get structured goal data from Gemini response
 */
fun GeminiResponseDto.getGoalGenerationResponse(): GoalGenerationResponse? {
    return try {
        val jsonText = candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
        jsonText?.let {
            Json.decodeFromString<GoalGenerationResponse>(it)
        }
    } catch (e: Exception) {
        Logger.e("GoalMapper", e) { "Failed to parse goal generation response: ${e.message}" }
        null
    }
}

/**
 * Extension to get plain text (for backward compatibility)
 */
fun GeminiResponseDto.getTextResponse(): String? {
    return candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
}


@OptIn(ExperimentalUuidApi::class)
fun GeminiResponseDto.toDomain(): List<Goal> {
    return try {
        // Get the JSON text from the response
        val jsonText = candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text

        if (jsonText != null) {
            // Parse the structured JSON response
            val goalResponse = Json.decodeFromString<GoalGenerationResponse>(jsonText)

            // Convert each goal DTO to domain model
            goalResponse.goals.map { goalDto ->
                Goal(
                    id = Uuid.random().toString(),
                    category = goalDto.category,
                    title = goalDto.title,
                    description = goalDto.description,
                    status = GoalStatus.NOT_STARTED, // New goals start as not started
                    timeline = goalDto.timeline,
                    dueDate = goalDto.dueDate?.let { parseDueDate(it) } ?: getDefaultDueDate(),
                    progress = 0,
                    milestones = goalDto.milestones.map { milestoneDto ->
                        Milestone(
                            id = Uuid.random().toString(),
                            title = milestoneDto.title,
                            isCompleted = false,
                            dueDate = null // API doesn't provide milestone due dates yet
                        )
                    },
                    notes = goalDto.notes,
                    createdAt = Clock.System.now()
                        .toLocalDateTime(TimeZone.currentSystemDefault()),
                    completionRate = 0f,
                    isArchived = false
                )
            }
        } else {
            // Fallback to empty list if no content
            emptyList()
        }
    } catch (e: Exception) {
        Logger.e("GoalMapper", e) { "Error parsing Gemini response to domain: ${e.message}" }
        // Return empty list instead of crashing
        emptyList()
    }
}

/**
 * Helper function to parse due date strings from AI response
 * Handles various formats the AI might return
 */
private fun parseDueDate(dateString: String): LocalDate {
    return try {
        // Try parsing ISO format first (YYYY-MM-DD)
        LocalDate.parse(dateString)
    } catch (e: Exception) {
        try {
            // Handle common date formats
            when {
                dateString.contains("/") -> {
                    val parts = dateString.split("/")
                    if (parts.size == 3) {
                        // Assume MM/DD/YYYY format
                        LocalDate(parts[2].toInt(), parts[0].toInt(), parts[1].toInt())
                    } else {
                        getDefaultDueDate()
                    }
                }
                dateString.contains("-") && !dateString.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) -> {
                    // Handle other dash formats like DD-MM-YYYY
                    val parts = dateString.split("-")
                    if (parts.size == 3 && parts[0].length <= 2) {
                        LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                    } else {
                        getDefaultDueDate()
                    }
                }
                else -> getDefaultDueDate()
            }
        } catch (e: Exception) {
            Logger.w("GoalMapper") { "Failed to parse date: $dateString, using default" }
            getDefaultDueDate()
        }
    }
}

/**
 * Returns a sensible default due date based on current date
 */
private fun getDefaultDueDate(): LocalDate {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return today.plus(DatePeriod(months = 6)) // Default to 6 months from now
}


// Goal Entity Mappers
fun GoalEntity.toDomain(milestones: List<Milestone> = emptyList()): Goal {
    return Goal(
        id = id,
        category = GoalCategory.valueOf(category),
        title = title,
        description = description,
        status = GoalStatus.valueOf(status),
        timeline = GoalTimeline.valueOf(timeline),
        dueDate = LocalDate.parse(dueDate),
        progress = progress,
        milestones = milestones,
        notes = notes ?: "",
        createdAt = parseLocalDateTime(createdAt),
        completionRate = completionRate.toFloat() ?: 0f,
        isArchived = isArchived == 1L
    )
}

fun Goal.toEntity(): GoalEntity {
    return GoalEntity(
        id = id,
        category = category.name,
        title = title,
        description = description,
        status = status.name,
        timeline = timeline.name,
        dueDate = dueDate.toString(),
        progress = progress ?: 0,
        notes = notes,
        createdAt = createdAt.toString(),
        completionRate = completionRate.toDouble(),
        isArchived = if (isArchived) 1L else 0L,
        sync_updated_at = Clock.System.now().toString(),
        is_deleted = 0L,
        sync_version = 0L,
        last_synced_at = null
    )
}

// Milestone Entity Mappers
fun MilestoneEntity.toDomain(): Milestone {
    return Milestone(
        id = id,
        title = title,
        isCompleted = isCompleted == 1L,
        dueDate = dueDate?.let { LocalDate.parse(it) }
    )
}

fun Milestone.toEntity(goalId: String): MilestoneEntity {
    return MilestoneEntity(
        id = id,
        goalId = goalId,
        title = title,
        isCompleted = if (isCompleted) 1L else 0L,
        dueDate = dueDate?.toString(),
        createdAt = Clock.System.now().toString(),
        sync_updated_at = Clock.System.now().toString(),
        is_deleted = 0L,
        sync_version = 0L,
        last_synced_at = null
    )
}

// Collection Extension Functions
fun List<GoalEntity>.toDomainGoals(milestonesMap: Map<String, List<Milestone>> = emptyMap()): List<Goal> {
    return map { goalEntity ->
        val milestones = milestonesMap[goalEntity.id] ?: emptyList()
        goalEntity.toDomain(milestones)
    }
}


fun List<MilestoneEntity>.toDomainMilestones(): List<Milestone> {
    return map { it.toDomain() }
}

fun List<Goal>.toEntities(): List<GoalEntity> {
    return map { it.toEntity() }
}

// Helper Functions
fun calculateCompletionRate(milestones: List<Milestone>, currentProgress: Long?): Float {
    return when {
        milestones.isNotEmpty() -> {
            val completedMilestones = milestones.count { it.isCompleted }
            (completedMilestones.toFloat() / milestones.size) * 100f
        }
        currentProgress != null -> currentProgress.toFloat()
        else -> 0f
    }
}

@OptIn(ExperimentalUuidApi::class)
fun createNewGoal(
    category: GoalCategory,
    title: String,
    description: String,
    timeline: GoalTimeline,
    dueDate: LocalDate,
    notes: String = ""
): Goal {
    return Goal(
        id = Uuid.random().toString(),
        category = category,
        title = title,
        description = description,
        status = GoalStatus.NOT_STARTED,
        timeline = timeline,
        dueDate = dueDate,
        progress = 0,
        milestones = emptyList(),
        notes = notes,
        createdAt = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()),
        completionRate = 0f,
        isArchived = false
    )
}

@OptIn(ExperimentalUuidApi::class)
fun createNewMilestone(
    title: String,
    dueDate: LocalDate? = null
): Milestone {
    return Milestone(
        id = Uuid.random().toString(),
        title = title,
        isCompleted = false,
        dueDate = dueDate
    )
}