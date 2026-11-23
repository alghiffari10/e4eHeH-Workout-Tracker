package com.e4eheh.e4ehehworkout.domain.model

import java.time.Instant
import java.util.UUID

/** Lifecycle state of a logged workout Session. */
enum class SessionStatus {
    IN_PROGRESS,
    COMPLETED,
}

/** Single recorded effort for an Exercise within a Session. */
data class SessionSet(
    val id: String,
    val sessionExerciseId: String,
    val sortOrder: Int,
    val reps: Int?,
    val loadKg: Double?,
    val durationSeconds: Int?,
    val distanceKm: Double?,
    val rounds: Int?,
    val isCompleted: Boolean,
    val isWarmup: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val syncStatus: SyncStatus,
) {
    val isBodyweight: Boolean
        get() = (loadKg ?: 0.0) == 0.0
}

/** Ordered Exercise snapshot within a Session preserving movement metadata and Sets. */
data class SessionExercise(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseDiscipline: Discipline,
    val trackingMode: TrackingMode,
    val sortOrder: Int,
    val notes: String?,
    val sets: List<SessionSet>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val syncStatus: SyncStatus,
)

/** Independent snapshot of a single logged or in-progress workout Session. */
data class Session(
    val id: String,
    val routineId: String? = null,
    val title: String,
    val discipline: Discipline,
    val status: SessionStatus,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationSeconds: Long,
    val notes: String?,
    val exercises: List<SessionExercise>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val syncStatus: SyncStatus,
)

/** Draft payload for reconciling a Set inside an in-progress or completing Session. */
data class SessionSetDraftInput(
    val id: String = UUID.randomUUID().toString(),
    val reps: Int? = null,
    val loadKg: Double? = null,
    val durationSeconds: Int? = null,
    val distanceKm: Double? = null,
    val rounds: Int? = null,
    val isCompleted: Boolean = false,
    val isWarmup: Boolean = false,
)

/** Draft payload for reconciling an Exercise slot inside a Session. */
data class SessionExerciseDraftInput(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: String,
    val exerciseName: String,
    val exerciseDiscipline: Discipline,
    val trackingMode: TrackingMode,
    val notes: String? = null,
    val sets: List<SessionSetDraftInput> = emptyList(),
)

/** Draft payload for saving or completing a live Session. */
data class SessionDraftInput(
    val title: String,
    val discipline: Discipline,
    val notes: String? = null,
    val exercises: List<SessionExerciseDraftInput> = emptyList(),
)
