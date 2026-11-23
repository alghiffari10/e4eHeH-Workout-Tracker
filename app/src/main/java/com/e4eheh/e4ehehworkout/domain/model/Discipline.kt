package com.e4eheh.e4ehehworkout.domain.model

/** Top-level training discipline classifying Exercises, Routines, and Sessions. */
enum class Discipline(val displayName: String) {
    CALISTHENICS("Calisthenics"),
    CONDITIONING("Conditioning"),
    CARDIO("Cardio"),
    WEIGHT_LIFTING("Weight Lifting"),
}
