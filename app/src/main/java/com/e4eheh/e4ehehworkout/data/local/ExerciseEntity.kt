package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import java.time.Instant

/** Room entity storing an Exercise with sync-ready tombstone metadata. */
@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val discipline: String,
    val trackingMode: String,
    val defaultNotes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: String,
)

fun ExerciseEntity.toDomain(): Exercise = Exercise(
    id = id,
    name = name,
    discipline = Discipline.valueOf(discipline),
    trackingMode = TrackingMode.valueOf(trackingMode),
    defaultNotes = defaultNotes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    syncStatus = SyncStatus.valueOf(syncStatus),
)

fun Exercise.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id,
    name = name,
    discipline = discipline.name,
    trackingMode = trackingMode.name,
    defaultNotes = defaultNotes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
    syncStatus = syncStatus.name,
)
