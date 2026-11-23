package com.e4eheh.e4ehehworkout.domain.model

/** Measurement model an Exercise uses to record Set performance. */
enum class TrackingMode(val displayName: String) {
    REPS_AND_LOAD("Reps & Load"),
    HOLD_DURATION("Hold / Duration"),
    DISTANCE_AND_TIME("Distance & Time"),
    CONDITIONING_ROUNDS("Conditioning / Rounds"),
}
