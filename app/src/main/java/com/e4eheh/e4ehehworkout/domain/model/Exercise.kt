package com.e4eheh.e4ehehworkout.domain.model

import java.time.Instant

/** Domain representation of a user-cataloged Exercise with sync-ready metadata. */
data class Exercise(
    val id: String,
    val name: String,
    val discipline: Discipline,
    val trackingMode: TrackingMode,
    val defaultNotes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val syncStatus: SyncStatus,
)

/** Payload for creating or updating an Exercise in the library. */
data class ExerciseInput(
    val name: String,
    val discipline: Discipline,
    val trackingMode: TrackingMode,
    val defaultNotes: String? = null,
)
