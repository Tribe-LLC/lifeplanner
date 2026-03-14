package az.tribe.lifeplanner.domain.model

import az.tribe.lifeplanner.domain.enum.ChallengeType
import az.tribe.lifeplanner.domain.enum.DependencyType
import az.tribe.lifeplanner.domain.enum.GoalCategory
import az.tribe.lifeplanner.domain.enum.GoalStatus
import az.tribe.lifeplanner.domain.enum.GoalTimeline
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.*

class ReviewModelTest {

    // ── ReviewType enum ──────────────────────────────────────────────

    @Test
    fun `ReviewType contains all four period types`() {
        val values = ReviewType.entries
        assertEquals(4, values.size)
        assertTrue(values.contains(ReviewType.WEEKLY))
        assertTrue(values.contains(ReviewType.MONTHLY))
        assertTrue(values.contains(ReviewType.QUARTERLY))
        assertTrue(values.contains(ReviewType.YEARLY))
    }

    // ── Challenge progressPercentage ─────────────────────────────────

    @Test
    fun `progressPercentage returns 0 when currentProgress is 0`() {
        val challenge = makeChallenge(currentProgress = 0, targetProgress = 10)
        assertEquals(0f, challenge.progressPercentage, 0.001f)
    }

    @Test
    fun `progressPercentage returns correct partial value`() {
        val challenge = makeChallenge(currentProgress = 3, targetProgress = 10)
        assertEquals(0.3f, challenge.progressPercentage, 0.001f)
    }

    @Test
    fun `progressPercentage returns 1 when fully completed`() {
        val challenge = makeChallenge(currentProgress = 10, targetProgress = 10)
        assertEquals(1f, challenge.progressPercentage, 0.001f)
    }

    @Test
    fun `progressPercentage is clamped to 1 when over 100 percent`() {
        val challenge = makeChallenge(currentProgress = 15, targetProgress = 10)
        assertEquals(1f, challenge.progressPercentage, 0.001f)
    }

    @Test
    fun `progressPercentage returns 0 when targetProgress is 0`() {
        val challenge = makeChallenge(currentProgress = 5, targetProgress = 0)
        assertEquals(0f, challenge.progressPercentage, 0.001f)
    }

    // ── ChallengeTargets.getTargetForType ────────────────────────────

    @Test
    fun `getTargetForType returns correct target for every ChallengeType`() {
        val expected = mapOf(
            ChallengeType.DAILY_CHECK_IN to 1,
            ChallengeType.DAILY_JOURNAL to 1,
            ChallengeType.DAILY_HABIT to 1,
            ChallengeType.WEEKLY_GOALS to 3,
            ChallengeType.WEEKLY_HABITS to 5,
            ChallengeType.WEEKLY_JOURNAL to 3,
            ChallengeType.WEEKLY_MILESTONE to 2,
            ChallengeType.MONTHLY_COMPLETION to 1,
            ChallengeType.MONTHLY_STREAK to 30,
            ChallengeType.MONTHLY_BALANCED to 8,
            ChallengeType.PERFECT_DAY to 1,
            ChallengeType.CATEGORY_FOCUS to 3,
            ChallengeType.EARLY_RISER to 7
        )
        for ((type, target) in expected) {
            assertEquals(target, ChallengeTargets.getTargetForType(type), "Target mismatch for $type")
        }
        // Verify we covered every enum entry
        assertEquals(ChallengeType.entries.size, expected.size, "Not all ChallengeType values tested")
    }

    // ── CoachPersona.getByCategory ───────────────────────────────────

    @Test
    fun `getByCategory returns correct coach for each mapped category`() {
        assertEquals("alex_career", CoachPersona.getByCategory(GoalCategory.CAREER).id)
        assertEquals("morgan_finance", CoachPersona.getByCategory(GoalCategory.FINANCIAL).id)
        assertEquals("kai_fitness", CoachPersona.getByCategory(GoalCategory.PHYSICAL).id)
        assertEquals("sam_social", CoachPersona.getByCategory(GoalCategory.SOCIAL).id)
        assertEquals("luna_general", CoachPersona.getByCategory(GoalCategory.EMOTIONAL).id)
        assertEquals("river_wellness", CoachPersona.getByCategory(GoalCategory.SPIRITUAL).id)
        assertEquals("jamie_family", CoachPersona.getByCategory(GoalCategory.FAMILY).id)
    }

    // ── CoachPersona.getById ─────────────────────────────────────────

    @Test
    fun `getById returns correct coach for valid id`() {
        val coach = CoachPersona.getById("kai_fitness")
        assertEquals("Kai", coach.name)
        assertEquals(GoalCategory.PHYSICAL, coach.category)
    }

    @Test
    fun `getById returns first coach for invalid id`() {
        val coach = CoachPersona.getById("nonexistent_coach")
        assertEquals("luna_general", coach.id)
    }

    // ── CoachPersona.getGeneral ──────────────────────────────────────

    @Test
    fun `getGeneral returns luna the general life coach`() {
        val general = CoachPersona.getGeneral()
        assertEquals("luna_general", general.id)
        assertEquals("Luna", general.name)
        assertEquals("Life Coach", general.title)
    }

    // ── GoalDependency.getInverseType ────────────────────────────────

    @Test
    fun `getInverseType returns correct inverse for all DependencyTypes`() {
        val now = LocalDateTime(2025, 1, 1, 0, 0)
        fun dep(type: DependencyType) = GoalDependency("d1", "s", "t", type, now)

        assertEquals(DependencyType.BLOCKED_BY, dep(DependencyType.BLOCKS).getInverseType())
        assertEquals(DependencyType.BLOCKS, dep(DependencyType.BLOCKED_BY).getInverseType())
        assertEquals(DependencyType.CHILD_OF, dep(DependencyType.PARENT_OF).getInverseType())
        assertEquals(DependencyType.PARENT_OF, dep(DependencyType.CHILD_OF).getInverseType())
        assertEquals(DependencyType.RELATED, dep(DependencyType.RELATED).getInverseType())
        assertEquals(DependencyType.SUPPORTS, dep(DependencyType.SUPPORTS).getInverseType())
    }

    // ── GoalNode computed properties ─────────────────────────────────

    @Test
    fun `hasBlockingDependencies returns true when node has BLOCKED_BY dependency`() {
        val node = makeGoalNode(
            dependencies = listOf(makeDep("d1", DependencyType.BLOCKED_BY))
        )
        assertTrue(node.hasBlockingDependencies)
    }

    @Test
    fun `hasBlockingDependencies returns false when node has no BLOCKED_BY dependency`() {
        val node = makeGoalNode(
            dependencies = listOf(makeDep("d1", DependencyType.RELATED))
        )
        assertFalse(node.hasBlockingDependencies)
    }

    @Test
    fun `isBlocking returns true when dependents contain BLOCKS type`() {
        val node = makeGoalNode(
            dependents = listOf(makeDep("d1", DependencyType.BLOCKS))
        )
        assertTrue(node.isBlocking)
    }

    @Test
    fun `isBlocking returns false when no dependents have BLOCKS type`() {
        val node = makeGoalNode(dependents = emptyList())
        assertFalse(node.isBlocking)
    }

    @Test
    fun `childGoals returns target IDs of PARENT_OF dependents`() {
        val node = makeGoalNode(
            dependents = listOf(
                makeDep("d1", DependencyType.PARENT_OF, targetGoalId = "child-1"),
                makeDep("d2", DependencyType.PARENT_OF, targetGoalId = "child-2"),
                makeDep("d3", DependencyType.BLOCKS, targetGoalId = "blocked-1")
            )
        )
        assertEquals(listOf("child-1", "child-2"), node.childGoals)
    }

    @Test
    fun `parentGoals returns target IDs of CHILD_OF dependencies`() {
        val node = makeGoalNode(
            dependencies = listOf(
                makeDep("d1", DependencyType.CHILD_OF, targetGoalId = "parent-1"),
                makeDep("d2", DependencyType.RELATED, targetGoalId = "related-1")
            )
        )
        assertEquals(listOf("parent-1"), node.parentGoals)
    }

    // ── DependencyGraph ──────────────────────────────────────────────

    @Test
    fun `isEmpty returns true for empty graph`() {
        val graph = DependencyGraph(nodes = emptyList(), edges = emptyList())
        assertTrue(graph.isEmpty)
    }

    @Test
    fun `isEmpty returns false when graph has nodes`() {
        val graph = DependencyGraph(
            nodes = listOf(makeGoalNode()),
            edges = emptyList()
        )
        assertFalse(graph.isEmpty)
    }

    @Test
    fun `getNodeByGoalId returns correct node`() {
        val node1 = makeGoalNode(goalId = "goal-1")
        val node2 = makeGoalNode(goalId = "goal-2")
        val graph = DependencyGraph(nodes = listOf(node1, node2), edges = emptyList())

        val found = graph.getNodeByGoalId("goal-2")
        assertNotNull(found)
        assertEquals("goal-2", found.goal.id)
    }

    @Test
    fun `getNodeByGoalId returns null for missing goal`() {
        val graph = DependencyGraph(
            nodes = listOf(makeGoalNode(goalId = "goal-1")),
            edges = emptyList()
        )
        assertNull(graph.getNodeByGoalId("nonexistent"))
    }

    @Test
    fun `getRootNodes returns nodes with no dependencies`() {
        val root = makeGoalNode(goalId = "root", dependencies = emptyList())
        val child = makeGoalNode(
            goalId = "child",
            dependencies = listOf(makeDep("d1", DependencyType.CHILD_OF))
        )
        val graph = DependencyGraph(nodes = listOf(root, child), edges = emptyList())

        val roots = graph.getRootNodes()
        assertEquals(1, roots.size)
        assertEquals("root", roots.first().goal.id)
    }

    @Test
    fun `getLeafNodes returns nodes with no dependents`() {
        val parent = makeGoalNode(
            goalId = "parent",
            dependents = listOf(makeDep("d1", DependencyType.PARENT_OF))
        )
        val leaf = makeGoalNode(goalId = "leaf", dependents = emptyList())
        val graph = DependencyGraph(nodes = listOf(parent, leaf), edges = emptyList())

        val leaves = graph.getLeafNodes()
        assertEquals(1, leaves.size)
        assertEquals("leaf", leaves.first().goal.id)
    }

    // ── Helper functions ─────────────────────────────────────────────

    private fun makeChallenge(
        currentProgress: Int = 0,
        targetProgress: Int = 10
    ) = Challenge(
        id = "ch-1",
        type = ChallengeType.WEEKLY_GOALS,
        startDate = LocalDate(2025, 1, 1),
        endDate = LocalDate(2025, 12, 31),
        currentProgress = currentProgress,
        targetProgress = targetProgress
    )

    private fun makeGoal(id: String = "goal-1") = Goal(
        id = id,
        category = GoalCategory.CAREER,
        title = "Test Goal",
        description = "A test goal",
        status = GoalStatus.IN_PROGRESS,
        timeline = GoalTimeline.SHORT_TERM,
        dueDate = LocalDate(2025, 12, 31),
        createdAt = LocalDateTime(2025, 1, 1, 0, 0)
    )

    private fun makeDep(
        id: String = "dep-1",
        type: DependencyType = DependencyType.BLOCKS,
        sourceGoalId: String = "source",
        targetGoalId: String = "target"
    ) = GoalDependency(
        id = id,
        sourceGoalId = sourceGoalId,
        targetGoalId = targetGoalId,
        dependencyType = type,
        createdAt = LocalDateTime(2025, 1, 1, 0, 0)
    )

    private fun makeGoalNode(
        goalId: String = "goal-1",
        dependencies: List<GoalDependency> = emptyList(),
        dependents: List<GoalDependency> = emptyList()
    ) = GoalNode(
        goal = makeGoal(goalId),
        dependencies = dependencies,
        dependents = dependents
    )
}
