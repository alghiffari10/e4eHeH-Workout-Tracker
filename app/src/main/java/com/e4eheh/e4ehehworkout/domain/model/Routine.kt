package com.e4eheh.e4ehehworkout.domain.model

import java.time.Instant

/** Ordered Exercise entry belonging to a Routine with optional default target Set count. */
data class RoutineExercise(
    val id: String,
    val routineId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val defaultTargetSets: Int?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val syncStatus: SyncStatus,
) {
    companion object {
        const val MIN_DEFAULT_TARGET_SETS = 1
        const val MAX_DEFAULT_TARGET_SETS = 99
    }
}

/** Domain representation of a reusable Routine with ordered Exercises and sync metadata. */
data class Routine(
    val id: String,
    val name: String,
    val discipline: Discipline,
    val exercises: List<RoutineExercise>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val syncStatus: SyncStatus,
)

/** Input entry for creating or updating an Exercise slot within a Routine. */
data class RoutineExerciseInput(
    val exerciseId: String,
    val defaultTargetSets: Int? = null,
    val id: String? = null,
)

/** Payload for creating or updating a Routine and its ordered Exercises. */
data class RoutineInput(
    val name: String,
    val discipline: Discipline,
    val exercises: List<RoutineExerciseInput> = emptyList(),
)
