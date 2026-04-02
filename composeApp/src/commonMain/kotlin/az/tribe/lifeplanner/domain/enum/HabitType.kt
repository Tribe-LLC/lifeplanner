package az.tribe.lifeplanner.domain.enum

enum class HabitType(val displayName: String, val actionLabel: String, val undoLabel: String) {
    BUILD("Build", "Check in", "Undo"),
    QUIT("Break", "Resisted", "Undo")
}
