package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntry
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import java.time.Instant

/** Room entity storing a Bodyweight Entry canonically in kg with sync-ready tombstone metadata. */
@Entity(
    tableName = "bodyweight_entries",
    indices = [Index(value = ["recordedAt"])],
)
data class BodyweightEntryEntity(
    @PrimaryKey val id: String,
    val recordedAt: Long,
    val weightKg: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val syncStatus: String,
)

fun BodyweightEntryEntity.toDomain(): BodyweightEntry = BodyweightEntry(
    id = id,
    recordedAt = Instant.ofEpochMilli(recordedAt),
    weightKg = weightKg,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    syncStatus = SyncStatus.valueOf(syncStatus),
)

fun BodyweightEntry.toEntity(): BodyweightEntryEntity = BodyweightEntryEntity(
    id = id,
    recordedAt = recordedAt.toEpochMilli(),
    weightKg = weightKg,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
    syncStatus = syncStatus.name,
)
