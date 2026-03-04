package az.tribe.lifeplanner.domain.enum

enum class AmbientSound(val displayName: String, val icon: String) {
    NONE("None", "🔇"),
    RAIN("Rain", "🌧️"),
    FOREST("Forest", "🌲"),
    LOFI("Lo-Fi", "🎵"),
    WHITE_NOISE("White Noise", "〰️");

    companion object {
        fun fromString(value: String): AmbientSound {
            return entries.find { it.name == value } ?: NONE
        }
    }
}
