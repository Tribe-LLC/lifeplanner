package az.tribe.lifeplanner.domain.enum

import kotlinx.serialization.Serializable

@Serializable
enum class Mood(val emoji: String, val displayName: String, val score: Int) {
    VERY_HAPPY("\uD83D\uDE01", "Very Happy", 5),
    HAPPY("\uD83D\uDE0A", "Happy", 4),
    NEUTRAL("\uD83D\uDE10", "Neutral", 3),
    SAD("\uD83D\uDE1E", "Sad", 2),
    VERY_SAD("\uD83D\uDE22", "Very Sad", 1);

    companion object {
        fun fromString(value: String): Mood {
            return entries.find { it.name == value } ?: NEUTRAL
        }

        fun fromScore(score: Int): Mood {
            return entries.find { it.score == score } ?: NEUTRAL
        }
    }
}
