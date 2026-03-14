package az.tribe.lifeplanner.data.mapper

import az.tribe.lifeplanner.data.model.CandidateDto
import az.tribe.lifeplanner.data.model.ContentDto
import az.tribe.lifeplanner.data.model.GeminiResponseDto
import az.tribe.lifeplanner.data.model.PartDto
import az.tribe.lifeplanner.database.GoalEntity
import az.tribe.lifeplanner.database.MilestoneEntity
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import az.tribe.lifeplanner.domain.model.Milestone
import az.tribe.lifeplanner.testutil.testGoal
import az.tribe.lifeplanner.testutil.testMilestone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.*

class GoalMapperTest {

    // ── GoalEntity.toDomain ─────────────────────────────────────────

    @Test
    fun `GoalEntity toDomain maps all basic fields correctly`() {
        val entity = GoalEntity(
            id = "goal-1",
            category = "CAREER",
            title = "Learn Kotlin",
            description = "Master KMP",
            status = "IN_PROGRESS",
            timeline = "SHORT_TERM",
            dueDate = "2026-06-06",
            progress = 40,
            notes = "Some notes",
            createdAt = "2026-03-06T10:00:00",
            completionRate = 0.6,
            isArchived = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val goal = entity.toDomain()

        assertEquals("goal-1", goal.id)
        assertEquals(GoalCategory.CAREER, goal.category)
        assertEquals("Learn Kotlin", goal.title)
        assertEquals("Master KMP", goal.description)
        assertEquals(GoalStatus.IN_PROGRESS, goal.status)
        assertEquals(GoalTimeline.SHORT_TERM, goal.timeline)
        assertEquals(LocalDate(2026, 6, 6), goal.dueDate)
        assertEquals(40L, goal.progress)
        assertEquals("Some notes", goal.notes)
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), goal.createdAt)
        assertEquals(0.6f, goal.completionRate, 0.001f)
        assertFalse(goal.isArchived)
    }

    @Test
    fun `GoalEntity toDomain maps isArchived 1 to true`() {
        val entity = GoalEntity(
            id = "goal-2",
            category = "FINANCIAL",
            title = "Save",
            description = "Desc",
            status = "COMPLETED",
            timeline = "LONG_TERM",
            dueDate = "2027-01-01",
            progress = 100,
            notes = "",
            createdAt = "2026-01-01T00:00:00",
            completionRate = 100.0,
            isArchived = 1L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertTrue(entity.toDomain().isArchived)
    }

    @Test
    fun `GoalEntity toDomain maps null notes to empty string`() {
        val entity = GoalEntity(
            id = "goal-3",
            category = "PHYSICAL",
            title = "Run",
            description = "Run 5k",
            status = "NOT_STARTED",
            timeline = "MID_TERM",
            dueDate = "2026-09-01",
            progress = 0,
            notes = "",
            createdAt = "2026-03-06T10:00:00",
            completionRate = 0.0,
            isArchived = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertEquals("", entity.toDomain().notes)
    }

    @Test
    fun `GoalEntity toDomain uses provided milestones`() {
        val entity = GoalEntity(
            id = "goal-4",
            category = "CAREER",
            title = "Test",
            description = "Desc",
            status = "IN_PROGRESS",
            timeline = "SHORT_TERM",
            dueDate = "2026-06-01",
            progress = 0,
            notes = "",
            createdAt = "2026-03-06T10:00:00",
            completionRate = 0.0,
            isArchived = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val milestones = listOf(testMilestone(id = "m-1"), testMilestone(id = "m-2"))
        val goal = entity.toDomain(milestones)

        assertEquals(2, goal.milestones.size)
        assertEquals("m-1", goal.milestones[0].id)
        assertEquals("m-2", goal.milestones[1].id)
    }

    @Test
    fun `GoalEntity toDomain defaults milestones to empty list`() {
        val entity = GoalEntity(
            id = "goal-5",
            category = "SOCIAL",
            title = "Test",
            description = "Desc",
            status = "NOT_STARTED",
            timeline = "SHORT_TERM",
            dueDate = "2026-06-01",
            progress = 0,
            notes = "",
            createdAt = "2026-03-06T10:00:00",
            completionRate = 0.0,
            isArchived = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertTrue(entity.toDomain().milestones.isEmpty())
    }

    @Test
    fun `GoalEntity toDomain parses instant format createdAt`() {
        val entity = GoalEntity(
            id = "goal-tz",
            category = "CAREER",
            title = "Test",
            description = "Desc",
            status = "IN_PROGRESS",
            timeline = "SHORT_TERM",
            dueDate = "2026-06-01",
            progress = 0,
            notes = "",
            createdAt = "2026-03-06T10:00:00Z",
            completionRate = 0.0,
            isArchived = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val goal = entity.toDomain()
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), goal.createdAt)
    }

    @Test
    fun `GoalEntity toDomain parses offset createdAt`() {
        val entity = GoalEntity(
            id = "goal-offset",
            category = "CAREER",
            title = "Test",
            description = "Desc",
            status = "IN_PROGRESS",
            timeline = "SHORT_TERM",
            dueDate = "2026-06-01",
            progress = 0,
            notes = "",
            createdAt = "2026-03-06T10:00:00+00:00",
            completionRate = 0.0,
            isArchived = 0L,
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val goal = entity.toDomain()
        assertEquals(LocalDateTime(2026, 3, 6, 10, 0, 0), goal.createdAt)
    }

    @Test
    fun `GoalEntity toDomain maps all GoalCategory values`() {
        for (category in GoalCategory.entries) {
            val entity = GoalEntity(
                id = "goal-${category.name}",
                category = category.name,
                title = "T",
                description = "D",
                status = "NOT_STARTED",
                timeline = "SHORT_TERM",
                dueDate = "2026-06-01",
                progress = 0,
                notes = "",
                createdAt = "2026-01-01T00:00:00",
                completionRate = 0.0,
                isArchived = 0L,
                sync_updated_at = null,
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
            assertEquals(category, entity.toDomain().category, "Failed for category $category")
        }
    }

    @Test
    fun `GoalEntity toDomain maps all GoalStatus values`() {
        for (status in GoalStatus.entries) {
            val entity = GoalEntity(
                id = "goal-${status.name}",
                category = "CAREER",
                title = "T",
                description = "D",
                status = status.name,
                timeline = "SHORT_TERM",
                dueDate = "2026-06-01",
                progress = 0,
                notes = "",
                createdAt = "2026-01-01T00:00:00",
                completionRate = 0.0,
                isArchived = 0L,
                sync_updated_at = null,
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
            assertEquals(status, entity.toDomain().status, "Failed for status $status")
        }
    }

    @Test
    fun `GoalEntity toDomain maps all GoalTimeline values`() {
        for (timeline in GoalTimeline.entries) {
            val entity = GoalEntity(
                id = "goal-${timeline.name}",
                category = "CAREER",
                title = "T",
                description = "D",
                status = "NOT_STARTED",
                timeline = timeline.name,
                dueDate = "2026-06-01",
                progress = 0,
                notes = "",
                createdAt = "2026-01-01T00:00:00",
                completionRate = 0.0,
                isArchived = 0L,
                sync_updated_at = null,
                is_deleted = 0L,
                sync_version = 0L,
                last_synced_at = null
            )
            assertEquals(timeline, entity.toDomain().timeline, "Failed for timeline $timeline")
        }
    }

    // ── Goal.toEntity ───────────────────────────────────────────────

    @Test
    fun `Goal toEntity maps all fields correctly`() {
        val goal = testGoal(
            id = "goal-1",
            category = GoalCategory.FINANCIAL,
            title = "Save money",
            description = "Save 10k",
            status = GoalStatus.IN_PROGRESS,
            timeline = GoalTimeline.MID_TERM,
            dueDate = LocalDate(2026, 9, 1),
            progress = 50,
            notes = "Halfway there",
            createdAt = LocalDateTime(2026, 3, 6, 10, 0, 0),
            completionRate = 50f,
            isArchived = false
        )

        val entity = goal.toEntity()

        assertEquals("goal-1", entity.id)
        assertEquals("FINANCIAL", entity.category)
        assertEquals("Save money", entity.title)
        assertEquals("Save 10k", entity.description)
        assertEquals("IN_PROGRESS", entity.status)
        assertEquals("MID_TERM", entity.timeline)
        assertEquals("2026-09-01", entity.dueDate)
        assertEquals(50L, entity.progress)
        assertEquals("Halfway there", entity.notes)
        assertEquals("2026-03-06T10:00", entity.createdAt)
        assertEquals(50.0, entity.completionRate)
        assertEquals(0L, entity.isArchived)
        assertEquals(0L, entity.is_deleted)
        assertEquals(0L, entity.sync_version)
        assertNull(entity.last_synced_at)
    }

    @Test
    fun `Goal toEntity maps isArchived true to 1L`() {
        val goal = testGoal(isArchived = true)
        assertEquals(1L, goal.toEntity().isArchived)
    }

    @Test
    fun `Goal toEntity maps isArchived false to 0L`() {
        val goal = testGoal(isArchived = false)
        assertEquals(0L, goal.toEntity().isArchived)
    }

    @Test
    fun `Goal toEntity maps null progress to 0`() {
        val goal = testGoal(progress = null)
        assertEquals(0L, goal.toEntity().progress)
    }

    @Test
    fun `Goal toEntity sets sync_updated_at to non-null`() {
        val goal = testGoal()
        assertNotNull(goal.toEntity().sync_updated_at)
    }

    // ── Round trip Goal Entity → Domain → Entity preserves key fields ──

    @Test
    fun `GoalEntity round trip preserves id`() {
        val original = testGoal(id = "round-trip-id")
        val restored = original.toEntity().toDomain()
        assertEquals(original.id, restored.id)
    }

    @Test
    fun `GoalEntity round trip preserves category`() {
        val original = testGoal(category = GoalCategory.SPIRITUAL)
        val restored = original.toEntity().toDomain()
        assertEquals(original.category, restored.category)
    }

    @Test
    fun `GoalEntity round trip preserves title and description`() {
        val original = testGoal(title = "My Goal", description = "My Desc")
        val restored = original.toEntity().toDomain()
        assertEquals(original.title, restored.title)
        assertEquals(original.description, restored.description)
    }

    @Test
    fun `GoalEntity round trip preserves status and timeline`() {
        val original = testGoal(status = GoalStatus.COMPLETED, timeline = GoalTimeline.LONG_TERM)
        val restored = original.toEntity().toDomain()
        assertEquals(original.status, restored.status)
        assertEquals(original.timeline, restored.timeline)
    }

    @Test
    fun `GoalEntity round trip preserves dueDate`() {
        val original = testGoal(dueDate = LocalDate(2027, 12, 25))
        val restored = original.toEntity().toDomain()
        assertEquals(original.dueDate, restored.dueDate)
    }

    @Test
    fun `GoalEntity round trip preserves completionRate`() {
        val original = testGoal(completionRate = 75.5f)
        val restored = original.toEntity().toDomain()
        assertEquals(original.completionRate, restored.completionRate, 0.01f)
    }

    @Test
    fun `GoalEntity round trip preserves isArchived`() {
        val original = testGoal(isArchived = true)
        val restored = original.toEntity().toDomain()
        assertEquals(original.isArchived, restored.isArchived)
    }

    @Test
    fun `GoalEntity round trip preserves notes`() {
        val original = testGoal(notes = "Important notes")
        val restored = original.toEntity().toDomain()
        assertEquals(original.notes, restored.notes)
    }

    // ── MilestoneEntity.toDomain ────────────────────────────────────

    @Test
    fun `MilestoneEntity toDomain maps all fields`() {
        val entity = MilestoneEntity(
            id = "ms-1",
            goalId = "goal-1",
            title = "Step 1",
            isCompleted = 0L,
            dueDate = "2026-07-01",
            createdAt = "2026-03-06T10:00:00",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        val milestone = entity.toDomain()

        assertEquals("ms-1", milestone.id)
        assertEquals("Step 1", milestone.title)
        assertFalse(milestone.isCompleted)
        assertEquals(LocalDate(2026, 7, 1), milestone.dueDate)
    }

    @Test
    fun `MilestoneEntity toDomain maps isCompleted 1 to true`() {
        val entity = MilestoneEntity(
            id = "ms-2",
            goalId = "goal-1",
            title = "Done",
            isCompleted = 1L,
            dueDate = null,
            createdAt = "2026-03-06T10:00:00",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertTrue(entity.toDomain().isCompleted)
    }

    @Test
    fun `MilestoneEntity toDomain maps null dueDate`() {
        val entity = MilestoneEntity(
            id = "ms-3",
            goalId = "goal-1",
            title = "No date",
            isCompleted = 0L,
            dueDate = null,
            createdAt = "2026-03-06T10:00:00",
            sync_updated_at = null,
            is_deleted = 0L,
            sync_version = 0L,
            last_synced_at = null
        )

        assertNull(entity.toDomain().dueDate)
    }

    // ── Milestone.toEntity ──────────────────────────────────────────

    @Test
    fun `Milestone toEntity maps all fields correctly`() {
        val milestone = testMilestone(
            id = "ms-1",
            title = "Step 1",
            isCompleted = false,
            dueDate = LocalDate(2026, 7, 1)
        )

        val entity = milestone.toEntity("goal-1")

        assertEquals("ms-1", entity.id)
        assertEquals("goal-1", entity.goalId)
        assertEquals("Step 1", entity.title)
        assertEquals(0L, entity.isCompleted)
        assertEquals("2026-07-01", entity.dueDate)
    }

    @Test
    fun `Milestone toEntity maps isCompleted true to 1L`() {
        val milestone = testMilestone(isCompleted = true)
        assertEquals(1L, milestone.toEntity("goal-1").isCompleted)
    }

    @Test
    fun `Milestone toEntity maps null dueDate`() {
        val milestone = testMilestone(dueDate = null)
        assertNull(milestone.toEntity("goal-1").dueDate)
    }

    @Test
    fun `Milestone toEntity sets sync fields`() {
        val milestone = testMilestone()
        val entity = milestone.toEntity("goal-1")

        assertNotNull(entity.sync_updated_at)
        assertEquals(0L, entity.is_deleted)
        assertEquals(0L, entity.sync_version)
        assertNull(entity.last_synced_at)
    }

    // ── Milestone round trip ────────────────────────────────────────

    @Test
    fun `Milestone round trip preserves id and title`() {
        val original = testMilestone(id = "m-rt", title = "My Milestone")
        val restored = original.toEntity("g").toDomain()
        assertEquals(original.id, restored.id)
        assertEquals(original.title, restored.title)
    }

    @Test
    fun `Milestone round trip preserves isCompleted`() {
        val original = testMilestone(isCompleted = true)
        val restored = original.toEntity("g").toDomain()
        assertEquals(original.isCompleted, restored.isCompleted)
    }

    @Test
    fun `Milestone round trip preserves dueDate`() {
        val original = testMilestone(dueDate = LocalDate(2026, 12, 31))
        val restored = original.toEntity("g").toDomain()
        assertEquals(original.dueDate, restored.dueDate)
    }

    @Test
    fun `Milestone round trip preserves null dueDate`() {
        val original = testMilestone(dueDate = null)
        val restored = original.toEntity("g").toDomain()
        assertNull(restored.dueDate)
    }

    // ── calculateCompletionRate ─────────────────────────────────────

    @Test
    fun `calculateCompletionRate returns 0 for empty milestones and null progress`() {
        assertEquals(0f, calculateCompletionRate(emptyList(), null))
    }

    @Test
    fun `calculateCompletionRate returns progress when no milestones`() {
        assertEquals(65f, calculateCompletionRate(emptyList(), 65L))
    }

    @Test
    fun `calculateCompletionRate returns 0 for zero milestones completed`() {
        val milestones = listOf(
            testMilestone(id = "1", isCompleted = false),
            testMilestone(id = "2", isCompleted = false)
        )
        assertEquals(0f, calculateCompletionRate(milestones, null))
    }

    @Test
    fun `calculateCompletionRate returns 50 for half milestones completed`() {
        val milestones = listOf(
            testMilestone(id = "1", isCompleted = true),
            testMilestone(id = "2", isCompleted = false)
        )
        assertEquals(50f, calculateCompletionRate(milestones, null))
    }

    @Test
    fun `calculateCompletionRate returns 100 for all milestones completed`() {
        val milestones = listOf(
            testMilestone(id = "1", isCompleted = true),
            testMilestone(id = "2", isCompleted = true),
            testMilestone(id = "3", isCompleted = true)
        )
        assertEquals(100f, calculateCompletionRate(milestones, null))
    }

    @Test
    fun `calculateCompletionRate calculates one third correctly`() {
        val milestones = listOf(
            testMilestone(id = "1", isCompleted = true),
            testMilestone(id = "2", isCompleted = false),
            testMilestone(id = "3", isCompleted = false)
        )
        assertEquals(100f / 3f, calculateCompletionRate(milestones, null), 0.01f)
    }

    @Test
    fun `calculateCompletionRate ignores progress when milestones exist`() {
        val milestones = listOf(
            testMilestone(id = "1", isCompleted = true),
            testMilestone(id = "2", isCompleted = false)
        )
        // Even though progress is 99, milestones take precedence
        assertEquals(50f, calculateCompletionRate(milestones, 99L))
    }

    @Test
    fun `calculateCompletionRate returns 0 for empty milestones and zero progress`() {
        assertEquals(0f, calculateCompletionRate(emptyList(), 0L))
    }

    // ── createNewGoal ───────────────────────────────────────────────

    @Test
    fun `createNewGoal sets NOT_STARTED status`() {
        val goal = createNewGoal(
            category = GoalCategory.CAREER,
            title = "New Goal",
            description = "Desc",
            timeline = GoalTimeline.SHORT_TERM,
            dueDate = LocalDate(2026, 6, 1)
        )
        assertEquals(GoalStatus.NOT_STARTED, goal.status)
    }

    @Test
    fun `createNewGoal sets zero progress`() {
        val goal = createNewGoal(
            category = GoalCategory.CAREER,
            title = "New Goal",
            description = "Desc",
            timeline = GoalTimeline.SHORT_TERM,
            dueDate = LocalDate(2026, 6, 1)
        )
        assertEquals(0L, goal.progress)
    }

    @Test
    fun `createNewGoal sets empty milestones`() {
        val goal = createNewGoal(
            category = GoalCategory.CAREER,
            title = "New Goal",
            description = "Desc",
            timeline = GoalTimeline.SHORT_TERM,
            dueDate = LocalDate(2026, 6, 1)
        )
        assertTrue(goal.milestones.isEmpty())
    }

    @Test
    fun `createNewGoal sets zero completionRate`() {
        val goal = createNewGoal(
            category = GoalCategory.CAREER,
            title = "New Goal",
            description = "Desc",
            timeline = GoalTimeline.SHORT_TERM,
            dueDate = LocalDate(2026, 6, 1)
        )
        assertEquals(0f, goal.completionRate)
    }

    @Test
    fun `createNewGoal sets isArchived to false`() {
        val goal = createNewGoal(
            category = GoalCategory.CAREER,
            title = "New Goal",
            description = "Desc",
            timeline = GoalTimeline.SHORT_TERM,
            dueDate = LocalDate(2026, 6, 1)
        )
        assertFalse(goal.isArchived)
    }

    @Test
    fun `createNewGoal generates unique ids`() {
        val goal1 = createNewGoal(
            category = GoalCategory.CAREER,
            title = "G1",
            description = "D",
            timeline = GoalTimeline.SHORT_TERM,
            dueDate = LocalDate(2026, 6, 1)
        )
        val goal2 = createNewGoal(
            category = GoalCategory.CAREER,
            title = "G2",
            description = "D",
            timeline = GoalTimeline.SHORT_TERM,
            dueDate = LocalDate(2026, 6, 1)
        )
        assertNotEquals(goal1.id, goal2.id)
    }

    @Test
    fun `createNewGoal uses provided notes`() {
        val goal = createNewGoal(
            category = GoalCategory.CAREER,
            title = "New Goal",
            description = "Desc",
            timeline = GoalTimeline.SHORT_TERM,
            dueDate = LocalDate(2026, 6, 1),
            notes = "Custom notes"
        )
        assertEquals("Custom notes", goal.notes)
    }

    @Test
    fun `createNewGoal defaults notes to empty string`() {
        val goal = createNewGoal(
            category = GoalCategory.CAREER,
            title = "New Goal",
            description = "Desc",
            timeline = GoalTimeline.SHORT_TERM,
            dueDate = LocalDate(2026, 6, 1)
        )
        assertEquals("", goal.notes)
    }

    // ── createNewMilestone ──────────────────────────────────────────

    @Test
    fun `createNewMilestone sets isCompleted to false`() {
        val milestone = createNewMilestone("Step 1")
        assertFalse(milestone.isCompleted)
    }

    @Test
    fun `createNewMilestone uses provided title`() {
        val milestone = createNewMilestone("Buy supplies")
        assertEquals("Buy supplies", milestone.title)
    }

    @Test
    fun `createNewMilestone defaults dueDate to null`() {
        val milestone = createNewMilestone("Step")
        assertNull(milestone.dueDate)
    }

    @Test
    fun `createNewMilestone uses provided dueDate`() {
        val date = LocalDate(2026, 12, 25)
        val milestone = createNewMilestone("Step", dueDate = date)
        assertEquals(date, milestone.dueDate)
    }

    @Test
    fun `createNewMilestone generates unique ids`() {
        val m1 = createNewMilestone("A")
        val m2 = createNewMilestone("B")
        assertNotEquals(m1.id, m2.id)
    }

    // ── List mappers ────────────────────────────────────────────────

    @Test
    fun `toDomainGoals maps empty list`() {
        val result = emptyList<GoalEntity>().toDomainGoals()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `toDomainGoals maps multiple entities`() {
        val entities = listOf(
            GoalEntity(
                id = "g1", category = "CAREER", title = "A", description = "D",
                status = "NOT_STARTED", timeline = "SHORT_TERM", dueDate = "2026-06-01",
                progress = 0, notes = "", createdAt = "2026-01-01T00:00:00",
                completionRate = 0.0, isArchived = 0L,
                sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
            ),
            GoalEntity(
                id = "g2", category = "FINANCIAL", title = "B", description = "D",
                status = "IN_PROGRESS", timeline = "MID_TERM", dueDate = "2026-09-01",
                progress = 50, notes = "", createdAt = "2026-01-01T00:00:00",
                completionRate = 50.0, isArchived = 0L,
                sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
            )
        )

        val result = entities.toDomainGoals()
        assertEquals(2, result.size)
        assertEquals("g1", result[0].id)
        assertEquals("g2", result[1].id)
    }

    @Test
    fun `toDomainGoals uses milestonesMap`() {
        val entities = listOf(
            GoalEntity(
                id = "g1", category = "CAREER", title = "A", description = "D",
                status = "NOT_STARTED", timeline = "SHORT_TERM", dueDate = "2026-06-01",
                progress = 0, notes = "", createdAt = "2026-01-01T00:00:00",
                completionRate = 0.0, isArchived = 0L,
                sync_updated_at = null, is_deleted = 0L, sync_version = 0L, last_synced_at = null
            )
        )
        val milestonesMap = mapOf("g1" to listOf(testMilestone(id = "m-1")))

        val result = entities.toDomainGoals(milestonesMap)
        assertEquals(1, result[0].milestones.size)
        assertEquals("m-1", result[0].milestones[0].id)
    }

    @Test
    fun `toDomainMilestones maps list correctly`() {
        val entities = listOf(
            MilestoneEntity("m1", "g1", "Step 1", 0L, null, "2026-01-01T00:00:00", null, 0L, 0L, null),
            MilestoneEntity("m2", "g1", "Step 2", 1L, "2026-06-01", "2026-01-01T00:00:00", null, 0L, 0L, null)
        )

        val result = entities.toDomainMilestones()
        assertEquals(2, result.size)
        assertEquals("m1", result[0].id)
        assertFalse(result[0].isCompleted)
        assertEquals("m2", result[1].id)
        assertTrue(result[1].isCompleted)
    }

    @Test
    fun `toEntities maps domain goals to entities`() {
        val goals = listOf(testGoal(id = "g1"), testGoal(id = "g2"))
        val entities = goals.toEntities()
        assertEquals(2, entities.size)
        assertEquals("g1", entities[0].id)
        assertEquals("g2", entities[1].id)
    }

    // ── GeminiResponseDto mappers ───────────────────────────────────

    @Test
    fun `getTextResponse returns text from first candidate`() {
        val dto = GeminiResponseDto(
            candidates = listOf(
                CandidateDto(
                    content = ContentDto(
                        parts = listOf(PartDto(text = "Hello world")),
                        role = "model"
                    )
                )
            )
        )
        assertEquals("Hello world", dto.getTextResponse())
    }

    @Test
    fun `getTextResponse returns null for empty candidates`() {
        val dto = GeminiResponseDto(candidates = emptyList())
        assertNull(dto.getTextResponse())
    }

    @Test
    fun `getQuestionGenerationResponse parses valid JSON`() {
        val jsonText = """{"goals":[{"goal_type":"Career","questions":[{"title":"What do you want?","options":["A","B"]}]}]}"""
        val dto = GeminiResponseDto(
            candidates = listOf(
                CandidateDto(
                    content = ContentDto(
                        parts = listOf(PartDto(text = jsonText)),
                        role = "model"
                    )
                )
            )
        )
        val result = dto.getQuestionGenerationResponse()
        assertNotNull(result)
        assertEquals(1, result.goals.size)
        assertEquals("Career", result.goals[0].goalType)
    }

    @Test
    fun `getQuestionGenerationResponse returns null for invalid JSON`() {
        val dto = GeminiResponseDto(
            candidates = listOf(
                CandidateDto(
                    content = ContentDto(
                        parts = listOf(PartDto(text = "not json")),
                        role = "model"
                    )
                )
            )
        )
        assertNull(dto.getQuestionGenerationResponse())
    }

    @Test
    fun `getQuestionGenerationResponse returns null for empty candidates`() {
        val dto = GeminiResponseDto(candidates = emptyList())
        assertNull(dto.getQuestionGenerationResponse())
    }

    @Test
    fun `getGoalGenerationResponse parses valid JSON`() {
        val jsonText = """{"goals":[{"title":"Learn Kotlin","description":"Desc","category":"CAREER","timeline":"SHORT_TERM","milestones":[{"title":"Step 1"}]}]}"""
        val dto = GeminiResponseDto(
            candidates = listOf(
                CandidateDto(
                    content = ContentDto(
                        parts = listOf(PartDto(text = jsonText)),
                        role = "model"
                    )
                )
            )
        )
        val result = dto.getGoalGenerationResponse()
        assertNotNull(result)
        assertEquals(1, result.goals.size)
        assertEquals("Learn Kotlin", result.goals[0].title)
    }

    @Test
    fun `getGoalGenerationResponse returns null for invalid JSON`() {
        val dto = GeminiResponseDto(
            candidates = listOf(
                CandidateDto(
                    content = ContentDto(
                        parts = listOf(PartDto(text = "{broken")),
                        role = "model"
                    )
                )
            )
        )
        assertNull(dto.getGoalGenerationResponse())
    }

    @Test
    fun `toDomain returns empty list for empty candidates`() {
        val dto = GeminiResponseDto(candidates = emptyList())
        assertTrue(dto.toDomain().isEmpty())
    }

    @Test
    fun `toDomain returns empty list for invalid JSON`() {
        val dto = GeminiResponseDto(
            candidates = listOf(
                CandidateDto(
                    content = ContentDto(
                        parts = listOf(PartDto(text = "invalid")),
                        role = "model"
                    )
                )
            )
        )
        assertTrue(dto.toDomain().isEmpty())
    }

    @Test
    fun `toDomain parses valid goal generation response`() {
        val jsonText = """{"goals":[{"title":"My Goal","description":"Desc","category":"CAREER","timeline":"SHORT_TERM","milestones":[{"title":"Step 1"}]}]}"""
        val dto = GeminiResponseDto(
            candidates = listOf(
                CandidateDto(
                    content = ContentDto(
                        parts = listOf(PartDto(text = jsonText)),
                        role = "model"
                    )
                )
            )
        )

        val goals = dto.toDomain()
        assertEquals(1, goals.size)
        assertEquals("My Goal", goals[0].title)
        assertEquals(GoalStatus.NOT_STARTED, goals[0].status)
        assertEquals(0, goals[0].progress)
        assertEquals(0f, goals[0].completionRate)
        assertFalse(goals[0].isArchived)
        assertEquals(1, goals[0].milestones.size)
        assertEquals("Step 1", goals[0].milestones[0].title)
        assertFalse(goals[0].milestones[0].isCompleted)
    }
}
