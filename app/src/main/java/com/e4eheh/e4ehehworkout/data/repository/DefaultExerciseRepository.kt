package com.e4eheh.e4ehehworkout.data.repository

import com.e4eheh.e4ehehworkout.data.local.ExerciseDao
import com.e4eheh.e4ehehworkout.data.local.toDomain
import com.e4eheh.e4ehehworkout.data.local.toEntity
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.repository.ExerciseRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Room-backed ExerciseRepository enforcing sync metadata and soft-delete tombstones. */
class DefaultExerciseRepository(
    private val exerciseDao: ExerciseDao,
    private val clock: Clock,
) : ExerciseRepository {
    override fun observeActiveExercises(): Flow<List<Exercise>> =
        exerciseDao.observeActive().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: String, includeDeleted: Boolean): Exercise? {
        val entity = exerciseDao.getById(id) ?: return null
        if (!includeDeleted && entity.deletedAt != null) return null
        return entity.toDomain()
    }

    override suspend fun create(input: ExerciseInput): Exercise {
        val (trimmedName, normalizedNotes) = normalizeInput(input)
        val now = nowMillis()
        val exercise = Exercise(
            id = UUID.randomUUID().toString(),
            name = trimmedName,
            discipline = input.discipline,
            trackingMode = input.trackingMode,
            defaultNotes = normalizedNotes,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            syncStatus = SyncStatus.PENDING,
        )
        exerciseDao.upsert(exercise.toEntity())
        return exercise
    }

    override suspend fun update(id: String, input: ExerciseInput): Exercise {
        val (trimmedName, normalizedNotes) = normalizeInput(input)
        val existing = exerciseDao.getById(id)?.takeIf { it.deletedAt == null }?.toDomain()
            ?: throw NoSuchElementException("Active Exercise not found for id=$id")

        val updated = existing.copy(
            name = trimmedName,
            discipline = input.discipline,
            trackingMode = input.trackingMode,
            defaultNotes = normalizedNotes,
            updatedAt = nowMillis(),
            syncStatus = SyncStatus.PENDING,
        )
        exerciseDao.upsert(updated.toEntity())
        return updated
    }

    override suspend fun softDelete(id: String): Exercise {
        val existing = exerciseDao.getById(id)?.toDomain()
            ?: throw NoSuchElementException("Exercise not found for id=$id")
        if (existing.deletedAt != null) return existing

        val now = nowMillis()
        val deleted = existing.copy(
            updatedAt = now,
            deletedAt = now,
            syncStatus = SyncStatus.PENDING,
        )
        exerciseDao.upsert(deleted.toEntity())
        return deleted
    }

    private fun nowMillis(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)

    private fun normalizeInput(input: ExerciseInput): Pair<String, String?> {
        val trimmedName = input.name.trim()
        require(trimmedName.isNotEmpty()) { "Exercise name must not be blank." }
        val normalizedNotes = input.defaultNotes?.trim()?.takeIf { it.isNotEmpty() }
        return trimmedName to normalizedNotes
    }
}
