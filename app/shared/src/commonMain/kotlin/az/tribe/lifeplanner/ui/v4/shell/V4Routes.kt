package az.tribe.lifeplanner.ui.v4.shell

import az.tribe.lifeplanner.domain.model.PlanArea

/**
 * v4 routes. String-keyed like the rest of the app. v3 routes (goal_detail/{goalId},
 * ai_chat, journal_wizard, sign_in, settings...) stay registered inside the v4 host, so any
 * screen v4 has not redesigned yet is still one navigate() away.
 */
object V4Routes {
    /**
     * Where every "go home" lands, including the legacy screens' own (sign-in, onboarding). It
     * shows nothing: it decides between first run and Today and replaces itself.
     */
    const val HOME = "v4_home"

    const val WELCOME = "v4_welcome"
    const val AREAS = "v4_areas?mode={mode}"
    fun areas(edit: Boolean = false) = "v4_areas?mode=${if (edit) "edit" else "first"}"
    const val CONNECT = "v4_connect"
    const val UPDATE = "v4_update"
    /** Starter habits by swipe, then the first tick. [path] is "new" or "update", for analytics. */
    const val STARTERS = "v4_starters?path={path}"
    fun starters(path: String) = "v4_starters?path=$path"

    const val TODAY = "v4_today"
    const val LIFE = "v4_life"
    const val COACH = "v4_coach"

    const val YOU = "v4_you"

    /** LifePlanner Plus: the RevenueCat paywall, and Customer Center to manage a subscription. */
    const val PLUS = "v4_plus"
    const val SUBSCRIPTION = "v4_subscription"

    /** The swipe decks: check in on what is left today, and review habits that slipped. */
    const val CHECK_IN = "v4_checkin"
    const val REVIEW = "v4_review"

    const val AREA = "v4_area/{area}"
    fun area(area: PlanArea) = "v4_area/${area.key}"

    /** A plan's own page. v3's goal_detail stays for the v3 shell only. */
    const val PLAN = "v4_plan/{goalId}"
    fun plan(goalId: String) = "v4_plan/$goalId"

    const val TRIP = "v4_trip/{tripId}"
    fun trip(tripId: String) = "v4_trip/$tripId"

    const val CONNECTED_APPS = "v4_connected_apps"

    /** Routes that show the bottom bar. */
    val TABS = listOf(TODAY, LIFE, COACH)

    /** Tabs that also show the "Add anything" bar. Coach has its own message box instead. */
    val ADD_BAR = setOf(TODAY, LIFE)

    /** First-run routes: no bottom bar, and the gate never bounces a user out of them. */
    val FIRST_RUN = setOf(WELCOME, AREAS, CONNECT, UPDATE, STARTERS)
}
