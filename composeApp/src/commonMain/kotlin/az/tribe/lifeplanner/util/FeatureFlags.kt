package az.tribe.lifeplanner.util

/**
 * Central feature flags. Set to true to enable work-in-progress features.
 * All flags default to false until the feature is production-ready.
 */
object FeatureFlags {
    /**
     * Screen hint banners shown at the top of each screen.
     * TODO: Replace with a first-launch bottom sheet that explains the screen,
     *  shows example use-cases, and educates the user before they start using it.
     */
    const val HINTS_ENABLED = false
}
