package az.tribe.lifeplanner.domain.enum

enum class AiProvider(
    val displayName: String,
    val modelInfo: String,
    val inputPricePerMillion: Double,
    val outputPricePerMillion: Double,
    /** Minimum user level required to use this provider. */
    val requiredLevel: Int
) {
    GEMINI("Gemini", "gemini-2.0-flash", 0.10, 0.40, requiredLevel = 1),
    OPENAI("ChatGPT", "gpt-4o-mini", 0.15, 0.60, requiredLevel = 5),
    GROK("Grok", "grok-4-1-fast", 0.30, 1.00, requiredLevel = 10);

    fun estimateCost(inputTokens: Long, outputTokens: Long): Double =
        (inputTokens * inputPricePerMillion + outputTokens * outputPricePerMillion) / 1_000_000.0

    /** Check if the user's level is high enough to use this provider. */
    fun isUnlocked(userLevel: Int): Boolean = userLevel >= requiredLevel

    companion object {
        fun fromProviderName(name: String): AiProvider? =
            entries.find { it.name.equals(name, ignoreCase = true) }

        /** Returns only the providers the user has unlocked. */
        fun unlockedProviders(userLevel: Int): List<AiProvider> =
            entries.filter { it.isUnlocked(userLevel) }
    }
}
