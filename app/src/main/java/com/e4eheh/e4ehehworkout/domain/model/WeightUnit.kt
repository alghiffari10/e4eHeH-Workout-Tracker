package com.e4eheh.e4ehehworkout.domain.model

/** Display unit for athlete bodyweight and Exercise load values. */
enum class WeightUnit(
    val symbol: String,
    val kgPerUnit: Double,
) {
    KG("kg", 1.0),
    LB("lb", 0.45359237),
}
