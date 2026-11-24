package com.e4eheh.e4ehehworkout.domain.model

import java.time.Instant

/** Domain representation of a dated Bodyweight Entry stored canonically in kg with sync metadata. */
data class BodyweightEntry(
    val id: String,
    val recordedAt: Instant,
    val weightKg: Double,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val syncStatus: SyncStatus,
)

/** Payload for creating or updating a Bodyweight Entry with canonical kg weight. */
data class BodyweightEntryInput(
    val recordedAt: Instant,
    val weightKg: Double,
)
