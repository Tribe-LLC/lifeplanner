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

    const val TODAY = "v4_today"
    const val LIFE = "v4_life"
    const val COACH = "v4_coach"

    const val YOU = "v4_you"

    const val AREA = "v4_area/{area}"
    fun area(area: PlanArea) = "v4_area/${area.key}"

    const val TRIP = "v4_trip/{tripId}"
    fun trip(tripId: String) = "v4_trip/$tripId"

    const val CONNECTED_APPS = "v4_connected_apps"

    /** Routes that show the bottom bar. */
    val TABS = listOf(TODAY, LIFE, COACH)

    /** Tabs that also show the "Add anything" bar. Coach has its own message box instead. */
    val ADD_BAR = setOf(TODAY, LIFE)

    /** First-run routes: no bottom bar, and the gate never bounces a user out of them. */
    val FIRST_RUN = setOf(WELCOME, AREAS, CONNECT, UPDATE)
}
