package az.tribe.lifeplanner.domain.enum

/**
 * Defines the type of relationship between goals
 */
enum class DependencyType(val displayName: String, val description: String) {
    /**
     * This goal must be completed before the dependent goal can start
     */
    BLOCKS("Blocks", "Must be completed first"),

    /**
     * This goal is blocked by another goal (inverse of BLOCKS)
     */
    BLOCKED_BY("Blocked By", "Waiting on another goal"),

    /**
     * Goals that are related but not dependent
     */
    RELATED("Related", "Connected goals"),

    /**
     * This goal is a sub-goal/child of another goal
     */
    PARENT_OF("Parent Of", "Has sub-goals"),

    /**
     * This goal is a child/sub-goal of another goal
     */
    CHILD_OF("Child Of", "Part of a larger goal"),

    /**
     * Goals that support each other (completing one helps the other)
     */
    SUPPORTS("Supports", "Helps achieve another goal")
}
