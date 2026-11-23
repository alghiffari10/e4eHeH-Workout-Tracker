package com.e4eheh.e4ehehworkout.ui.navigation

/** Top-level navigation destinations exposed in the 4-tab Batcomputer bottom bar. */
enum class TopLevelTab(
    val route: String,
    val label: String,
    val testTag: String,
) {
    HOME("home", "Home", "nav_tab_home"),
    WORKOUT("workout", "Workout", "nav_tab_workout"),
    PROGRESS("progress", "Progress", "nav_tab_progress"),
    PROFILE("profile", "Profile", "nav_tab_profile"),
}

/** Navigation route for the full-screen active live Session logger. */
object ActiveSessionRoute {
    const val ARG_SESSION_ID = "sessionId"
    const val PATTERN = "active_session/{$ARG_SESSION_ID}"

    fun createRoute(sessionId: String): String = "active_session/$sessionId"
}
