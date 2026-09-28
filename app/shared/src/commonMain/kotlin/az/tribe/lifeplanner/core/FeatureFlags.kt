package az.tribe.lifeplanner.core

/**
 * Compile-time feature flags.
 * Set a flag to `true` to enable the feature, `false` to hide it completely.
 *
 * Dead code elimination will strip all guarded branches when a flag is `false`,
 * so disabled features add zero runtime overhead.
 */
object FeatureFlags {
    /** Abilities, habit-XP → skill leveling system. Hidden until ready for release. */
    const val ABILITIES_ENABLED = false

    // ── v3 rollout ── flip on per phase ─────────────────────────────
    /**
     * Phase 1 interim: use the lightweight legacy Home as the Home tab instead of
     * the data-coupled For You feed. Default false = current behavior (For You home).
     * Flip to true for the Phase 1 release; back to false once Phase 2 lands For You.
     */

    /** Phase 2: For You feed as Home (needs PossibilityEngine + feed tables). */
    const val REDESIGN_HOME_FORYOU = false

    // Pillar kill switches. Each one guards every entry point AND the nav registration
    // for its feature, so flipping it to `false` both hides the entry point and makes the
    // route unreachable by a stale deep link.
    //
    // 2026-07-21: product owner reverted to a v2 feature set. All pillars OFF. The For You feed
    // stays home but shows only its v2-safe cards, since every
    // pillar feed card is gated on these flags. Flip one back to `true` to restore that pillar.

    /** Your Wiring (DecisionProfile / TuningInferenceEngine). */
    const val PILLAR_WIRING = false

    /** Causal Insights (CausalInsightEngine). */
    const val PILLAR_CAUSAL = false

    /** Becoming (IdentityStatement). */
    const val PILLAR_BECOMING = false

    /** Possibility Mode. Back on since 2026-07-23: TRI-20 ships with an intro gate and a local no-AI fallback. */
    const val PILLAR_POSSIBILITY = true

    /**
     * Community Journals (v0): opt-in public sharing of a journal entry into a moderated community
     * learning feed. Off until the backend (shared_journals table + share-journal/report-content
     * edge functions) is deployed and internal review is in place. See docs/SPEC-community-journals.md.
     */
    const val COMMUNITY_JOURNALS = false

    /**
     * LifePlanner 4: the life-areas planner (Today / Life / Coach, pick your areas, one box to add
     * anything, two-way Health and Calendar). Replaces the v3 shell when on; every v3 screen that v4
     * has not redesigned yet stays reachable inside it. Design: the "LifePlanner 4 redesign" canvas.
     */
    const val V4_SHELL = true
}
