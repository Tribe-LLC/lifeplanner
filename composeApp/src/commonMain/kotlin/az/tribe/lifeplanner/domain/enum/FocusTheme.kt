package az.tribe.lifeplanner.domain.enum

enum class FocusTheme(val displayName: String, val icon: String) {
    DEFAULT("Default", "🎯"),
    RAIN("Rain", "🌧️"),
    FOREST("Forest", "🌲"),
    FIREPLACE("Fireplace", "🔥"),
    OCEAN("Ocean", "🌊"),
    NIGHT_SKY("Night Sky", "🌙");

    companion object {
        fun fromString(value: String): FocusTheme {
            return entries.find { it.name == value } ?: DEFAULT
        }
    }
}
