package az.tribe.lifeplanner.domain.enum

enum class AiProvider(
    val displayName: String,
    val modelInfo: String,
    val inputPricePerMillion: Double,
    val outputPricePerMillion: Double
) {
    GEMINI("Gemini", "gemini-2.0-flash", 0.10, 0.40),
    OPENAI("ChatGPT", "gpt-4o-mini", 0.15, 0.60),
    GROK("Grok", "grok-4-1-fast", 0.30, 1.00);

    fun estimateCost(inputTokens: Long, outputTokens: Long): Double =
        (inputTokens * inputPricePerMillion + outputTokens * outputPricePerMillion) / 1_000_000.0

    companion object {
        fun fromProviderName(name: String): AiProvider? =
            entries.find { it.name.equals(name, ignoreCase = true) }
    }
}
