package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Routine
import com.e4eheh.e4ehehworkout.domain.model.RoutineExercise
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import java.time.Instant

/** Room entity storing a Routine with sync-ready tombstone metadata. */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val discipline: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: String,
)

/** Room entity storing an ordered Exercise reference within a Routine. */
@Entity(
    tableName = "routine_exercises",
    indices = [
        Index(value = ["routineId"]),
        Index(value = ["exerciseId"]),
    ],
)
data class RoutineExerciseEntity(
    @PrimaryKey val id: String,
    val routineId: String,
    val exerciseId: String,
    val sortOrder: Int,
    val defaultTargetSets: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: String,
)

/** Room relation joining a Routine with its RoutineExercise rows. */
data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "routineId",
    )
    val exercises: List<RoutineExerciseEntity>,
)

fun RoutineEntity.toDomain(exercises: List<RoutineExercise>): Routine = Routine(
    id = id,
    name = name,
    discipline = Discipline.valueOf(discipline),
    exercises = exercises,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    syncStatus = SyncStatus.valueOf(syncStatus),
)

fun Routine.toEntity(): RoutineEntity = RoutineEntity(
    id = id,
    name = name,
    discipline = discipline.name,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
    syncStatus = syncStatus.name,
)

fun RoutineExerciseEntity.toDomain(): RoutineExercise = RoutineExercise(
    id = id,
    routineId = routineId,
    exerciseId = exerciseId,
    sortOrder = sortOrder,
    defaultTargetSets = defaultTargetSets,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    syncStatus = SyncStatus.valueOf(syncStatus),
)

fun RoutineExercise.toEntity(): RoutineExerciseEntity = RoutineExerciseEntity(
    id = id,
    routineId = routineId,
    exerciseId = exerciseId,
    sortOrder = sortOrder,
    defaultTargetSets = defaultTargetSets,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
    syncStatus = syncStatus.name,
)
