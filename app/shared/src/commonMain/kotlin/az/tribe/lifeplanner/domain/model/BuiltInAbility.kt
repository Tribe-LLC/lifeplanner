package az.tribe.lifeplanner.domain.model

/**
 * Abilities the app owns rather than the user. A user-created ability earns XP through the habits
 * linked to it; these two are fed directly by decisions, reviews and journal entries, which have no
 * link table.
 *
 * Fixed ids mean seeding is idempotent and needs no schema change: [seeds] inserts rows, and
 * re-running it simply finds them already there.
 */
object BuiltInAbility {
    const val JUDGMENT = "builtin_judgment"
    const val REFLECTION = "builtin_reflection"

    fun seeds(createdAt: String): List<Ability> = listOf(
        Ability(
            id = JUDGMENT,
            title = "Judgment",
            description = "Grows when you make a call deliberately and come back to score how you " +
                "thought, not how it happened to land.",
            iconEmoji = "🎯",
            createdAt = createdAt,
        ),
        Ability(
            id = REFLECTION,
            title = "Reflection",
            description = "Grows when you look back honestly. Depth counts, not word count.",
            iconEmoji = "🪞",
            createdAt = createdAt,
        ),
    )
}
