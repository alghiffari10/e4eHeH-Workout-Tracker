package com.e4eheh.e4ehehworkout.domain.model

/** Display unit for cardio and conditioning Exercise distances. */
enum class DistanceUnit(
    val symbol: String,
    val kmPerUnit: Double,
) {
    KM("km", 1.0),
    MI("mi", 1.609344),
}
