package com.e4eheh.e4ehehworkout.domain.model

/** Locally persisted athlete preferences for Batcomputer identity, units, and Rest Timer. */
data class UserPreferences(
    val displayName: String = DEFAULT_DISPLAY_NAME,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val defaultRestTimerSeconds: Int = DEFAULT_REST_TIMER_SECONDS,
) {
    companion object {
        const val DEFAULT_DISPLAY_NAME = "OPERATOR"
        const val DEFAULT_REST_TIMER_SECONDS = 90
        const val MIN_REST_TIMER_SECONDS = 15
        const val MAX_REST_TIMER_SECONDS = 600
        const val REST_TIMER_STEP_SECONDS = 15
    }
}
