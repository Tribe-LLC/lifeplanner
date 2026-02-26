package az.tribe.lifeplanner.domain.model

import kotlinx.serialization.Serializable

/**
 * Preset characteristic that users can select when creating a custom coach
 */
@Serializable
data class CharacteristicPreset(
    val id: String,
    val name: String,
    val description: String,
    val promptSnippet: String
)

/**
 * Predefined coach characteristics/personality traits
 */
object CoachCharacteristics {
    val PRESETS = listOf(
        CharacteristicPreset(
            id = "motivating",
            name = "Motivating & Encouraging",
            description = "Always positive, celebrates wins, pushes through setbacks",
            promptSnippet = "You are highly motivating and encouraging. Always find the positive angle, celebrate every small win, and help push through setbacks with enthusiasm. Use uplifting language and remind the user of their progress."
        ),
        CharacteristicPreset(
            id = "strict",
            name = "Strict & Disciplined",
            description = "No excuses, holds you accountable, tough love",
            promptSnippet = "You are strict and disciplined. Accept no excuses, hold the user firmly accountable for their commitments. Apply tough love when needed - be direct about what needs to improve while maintaining respect."
        ),
        CharacteristicPreset(
            id = "friendly",
            name = "Friendly & Casual",
            description = "Like talking to a friend, relaxed tone, uses humor",
            promptSnippet = "You are friendly and casual, like a supportive friend. Use a relaxed conversational tone, incorporate light humor when appropriate, and make the user feel comfortable sharing their thoughts."
        ),
        CharacteristicPreset(
            id = "professional",
            name = "Professional & Formal",
            description = "Business-like, structured advice, data-driven",
            promptSnippet = "You are professional and formal. Provide structured, business-like advice. Focus on measurable outcomes, use data and metrics when relevant, and maintain a polished communication style."
        ),
        CharacteristicPreset(
            id = "empathetic",
            name = "Empathetic & Understanding",
            description = "Validates feelings, patient, focuses on wellbeing",
            promptSnippet = "You are deeply empathetic and understanding. Always validate the user's feelings first, be patient with their struggles, and prioritize their emotional wellbeing alongside their goals."
        ),
        CharacteristicPreset(
            id = "analytical",
            name = "Analytical & Strategic",
            description = "Breaks down problems, systematic approach, logical",
            promptSnippet = "You are analytical and strategic. Break down complex problems into manageable parts, take a systematic approach to challenges, and use logical reasoning to help the user make decisions."
        ),
        CharacteristicPreset(
            id = "creative",
            name = "Creative & Innovative",
            description = "Thinks outside the box, suggests unique approaches",
            promptSnippet = "You are creative and innovative. Think outside the box, suggest unconventional approaches to problems, and help the user explore unique solutions they might not have considered."
        ),
        CharacteristicPreset(
            id = "mindful",
            name = "Mindful & Reflective",
            description = "Encourages self-reflection, asks deep questions",
            promptSnippet = "You are mindful and reflective. Encourage deep self-reflection, ask thoughtful questions that help the user understand themselves better, and promote awareness of thoughts and feelings."
        )
    )

    fun getById(id: String): CharacteristicPreset? = PRESETS.find { it.id == id }

    fun getPromptForCharacteristics(characteristicIds: List<String>): String {
        return characteristicIds
            .mapNotNull { getById(it) }
            .joinToString("\n\n") { it.promptSnippet }
    }
}

/**
 * Preset color options for coach avatars
 */
object CoachColors {
    val PRESETS = listOf(
        ColorPreset("indigo", "#6366F1", "#818CF8"),
        ColorPreset("blue", "#3B82F6", "#60A5FA"),
        ColorPreset("green", "#10B981", "#34D399"),
        ColorPreset("red", "#EF4444", "#F87171"),
        ColorPreset("purple", "#8B5CF6", "#A78BFA"),
        ColorPreset("pink", "#EC4899", "#F472B6"),
        ColorPreset("orange", "#F97316", "#FB923C"),
        ColorPreset("teal", "#14B8A6", "#2DD4BF"),
        ColorPreset("amber", "#F59E0B", "#FBBF24"),
        ColorPreset("cyan", "#06B6D4", "#22D3EE")
    )
}

@Serializable
data class ColorPreset(
    val id: String,
    val backgroundColor: String,
    val accentColor: String
)

/**
 * Common emoji icons for coaches
 */
object CoachIcons {
    val PRESETS = listOf(
        // People
        "🧑‍🏫", "👨‍💼", "👩‍💻", "🧙", "🦸", "🧑‍🎓", "🧑‍🔬", "🧑‍🎨",
        // Objects
        "🎯", "💡", "🔥", "⭐", "💪", "🧠", "❤️", "🌟",
        // Animals
        "🦁", "🦊", "🐺", "🦅", "🐉", "🦉", "🐬", "🦋",
        // Nature
        "🌸", "🌿", "🌙", "☀️", "🌊", "⚡", "🌈", "🍀"
    )
}
