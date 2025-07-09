package az.tribe.lifeplanner.domain.enum

enum class GoalCategory(val order: Int) {
    CAREER(1),
    FINANCIAL(2),
    PHYSICAL(3),
    SOCIAL(4),
    EMOTIONAL(5),
    SPIRITUAL(6),
    FAMILY(7);

    companion object {
        fun getAllSorted(): List<GoalCategory> {
            return entries.sortedBy { it.order }
        }
    }
}

