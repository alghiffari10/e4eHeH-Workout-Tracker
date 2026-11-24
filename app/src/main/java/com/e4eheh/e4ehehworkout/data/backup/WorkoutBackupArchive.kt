package com.e4eheh.e4ehehworkout.data.backup

import kotlinx.serialization.Serializable

/** Versioned JSON archive capturing all non-deleted training database records. */
@Serializable
data class WorkoutBackupArchive(
    val schemaVersion: Int,
    val exportedAt: String,
    val exercises: List<BackupExercise>,
    val routines: List<BackupRoutine>,
    val sessions: List<BackupSession>,
    val bodyweightEntries: List<BackupBodyweightEntry>,
)

@Serializable
data class BackupExercise(
    val id: String,
    val name: String,
    val discipline: String,
    val trackingMode: String,
    val defaultNotes: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupRoutine(
    val id: String,
    val name: String,
    val discipline: String,
    val exercises: List<BackupRoutineExercise>,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupRoutineExercise(
    val id: String,
    val exerciseId: String,
    val sortOrder: Int,
    val defaultTargetSets: Int? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupSession(
    val id: String,
    val routineId: String? = null,
    val title: String,
    val discipline: String,
    val status: String,
    val startedAt: String,
    val completedAt: String? = null,
    val durationSeconds: Long,
    val notes: String? = null,
    val exercises: List<BackupSessionExercise>,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupSessionExercise(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseDiscipline: String,
    val trackingMode: String,
    val sortOrder: Int,
    val notes: String? = null,
    val sets: List<BackupSessionSet>,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupSessionSet(
    val id: String,
    val sortOrder: Int,
    val reps: Int? = null,
    val loadKg: Double? = null,
    val durationSeconds: Int? = null,
    val distanceKm: Double? = null,
    val rounds: Int? = null,
    val isCompleted: Boolean,
    val isWarmup: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class BackupBodyweightEntry(
    val id: String,
    val recordedAt: String,
    val weightKg: Double,
    val createdAt: String,
    val updatedAt: String,
)
