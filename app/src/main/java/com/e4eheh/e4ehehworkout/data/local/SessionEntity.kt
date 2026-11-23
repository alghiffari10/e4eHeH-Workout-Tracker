package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionExercise
import com.e4eheh.e4ehehworkout.domain.model.SessionSet
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import java.time.Instant

/** Room entity storing a workout Session with sync-ready tombstone metadata. */
@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["startedAt"]),
        Index(value = ["routineId"]),
    ],
)
data class SessionEntity(
    @PrimaryKey val id: String,
    val routineId: String?,
    val title: String,
    val discipline: String,
    val status: String,
    val startedAt: Long,
    val completedAt: Long?,
    val durationSeconds: Long,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: String,
)

/** Room entity storing an Exercise snapshot within a Session. */
@Entity(
    tableName = "session_exercises",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["exerciseId"]),
    ],
)
data class SessionExerciseEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseDiscipline: String,
    val trackingMode: String,
    val sortOrder: Int,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: String,
)

/** Room entity storing a recorded Set within a SessionExercise. */
@Entity(
    tableName = "session_sets",
    indices = [
        Index(value = ["sessionExerciseId"]),
    ],
)
data class SessionSetEntity(
    @PrimaryKey val id: String,
    val sessionExerciseId: String,
    val sortOrder: Int,
    val reps: Int?,
    val loadKg: Double?,
    val durationSeconds: Int?,
    val distanceKm: Double?,
    val rounds: Int?,
    val isCompleted: Boolean,
    val isWarmup: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: String,
)

/** Room relation joining a SessionExercise with its SessionSet rows. */
data class SessionExerciseWithSets(
    @Embedded val sessionExercise: SessionExerciseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionExerciseId",
    )
    val sets: List<SessionSetEntity>,
)

/** Room relation joining a Session with its SessionExercise and nested SessionSet rows. */
data class SessionWithExercises(
    @Embedded val session: SessionEntity,
    @Relation(
        entity = SessionExerciseEntity::class,
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val exercises: List<SessionExerciseWithSets>,
)

fun SessionEntity.toDomain(exercises: List<SessionExercise>): Session = Session(
    id = id,
    routineId = routineId,
    title = title,
    discipline = Discipline.valueOf(discipline),
    status = SessionStatus.valueOf(status),
    startedAt = Instant.ofEpochMilli(startedAt),
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    durationSeconds = durationSeconds,
    notes = notes,
    exercises = exercises,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    syncStatus = SyncStatus.valueOf(syncStatus),
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    routineId = routineId,
    title = title,
    discipline = discipline.name,
    status = status.name,
    startedAt = startedAt.toEpochMilli(),
    completedAt = completedAt?.toEpochMilli(),
    durationSeconds = durationSeconds,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
    syncStatus = syncStatus.name,
)

fun SessionExerciseEntity.toDomain(sets: List<SessionSet>): SessionExercise = SessionExercise(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    exerciseDiscipline = Discipline.valueOf(exerciseDiscipline),
    trackingMode = TrackingMode.valueOf(trackingMode),
    sortOrder = sortOrder,
    notes = notes,
    sets = sets,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    syncStatus = SyncStatus.valueOf(syncStatus),
)

fun SessionExercise.toEntity(): SessionExerciseEntity = SessionExerciseEntity(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    exerciseDiscipline = exerciseDiscipline.name,
    trackingMode = trackingMode.name,
    sortOrder = sortOrder,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
    syncStatus = syncStatus.name,
)

fun SessionSetEntity.toDomain(): SessionSet = SessionSet(
    id = id,
    sessionExerciseId = sessionExerciseId,
    sortOrder = sortOrder,
    reps = reps,
    loadKg = loadKg,
    durationSeconds = durationSeconds,
    distanceKm = distanceKm,
    rounds = rounds,
    isCompleted = isCompleted,
    isWarmup = isWarmup,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    syncStatus = SyncStatus.valueOf(syncStatus),
)

fun SessionSet.toEntity(): SessionSetEntity = SessionSetEntity(
    id = id,
    sessionExerciseId = sessionExerciseId,
    sortOrder = sortOrder,
    reps = reps,
    loadKg = loadKg,
    durationSeconds = durationSeconds,
    distanceKm = distanceKm,
    rounds = rounds,
    isCompleted = isCompleted,
    isWarmup = isWarmup,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
    syncStatus = syncStatus.name,
)
