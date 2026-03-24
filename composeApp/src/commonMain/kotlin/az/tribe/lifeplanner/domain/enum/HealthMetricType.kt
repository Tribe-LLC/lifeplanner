package az.tribe.lifeplanner.domain.enum

enum class HealthMetricType(val displayName: String, val unit: String) {
    STEPS("Steps", "count"),
    WEIGHT("Weight", "kg"),
    HEART_RATE("Heart Rate", "bpm"),
    SLEEP("Sleep", "hours")
}
