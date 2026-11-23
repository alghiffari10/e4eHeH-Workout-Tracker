package com.e4eheh.e4ehehworkout.data.repository

import com.e4eheh.e4ehehworkout.data.local.RoutineDao
import com.e4eheh.e4ehehworkout.data.local.RoutineEntity
import com.e4eheh.e4ehehworkout.data.local.RoutineExerciseEntity
import com.e4eheh.e4ehehworkout.data.local.RoutineWithExercises
import com.e4eheh.e4ehehworkout.data.local.toDomain
import com.e4eheh.e4ehehworkout.domain.model.Routine
import com.e4eheh.e4ehehworkout.domain.model.RoutineExercise
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.repository.RoutineRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Room-backed RoutineRepository enforcing sync metadata, ordering, and soft-delete tombstones. */
class DefaultRoutineRepository(
    private val routineDao: RoutineDao,
    private val clock: Clock,
) : RoutineRepository {
    private val writeMutex = Mutex()

    override fun observeActiveRoutines(): Flow<List<Routine>> =
        routineDao.observeActiveWithExercises().map { list ->
            list.map { it.toDomain(includeDeletedExercises = false) }
        }

    override suspend fun getById(id: String, includeDeleted: Boolean): Routine? {
        val record = routineDao.getWithExercisesById(id) ?: return null
        if (!includeDeleted && record.routine.deletedAt != null) return null
        return record.toDomain(includeDeletedExercises = includeDeleted)
    }

    override suspend fun create(input: RoutineInput): Routine {
        val normalized = normalizeInput(input)
        return writeMutex.withLock {
            val now = nowMillis()
            val nowEpoch = now.toEpochMilli()
            val routineId = UUID.randomUUID().toString()
            val routineEntity = RoutineEntity(
                id = routineId,
                name = normalized.name,
                discipline = normalized.discipline.name,
                createdAt = nowEpoch,
                updatedAt = nowEpoch,
                deletedAt = null,
                syncStatus = SyncStatus.PENDING.name,
            )
            val exerciseEntities = normalized.exercises.mapIndexed { index, item ->
                RoutineExerciseEntity(
                    id = UUID.randomUUID().toString(),
                    routineId = routineId,
                    exerciseId = item.exerciseId,
                    sortOrder = index,
                    defaultTargetSets = item.defaultTargetSets,
                    createdAt = nowEpoch,
                    updatedAt = nowEpoch,
                    deletedAt = null,
                    syncStatus = SyncStatus.PENDING.name,
                )
            }
            routineDao.upsertRoutineWithExercises(routineEntity, exerciseEntities)
            routineEntity.toDomain(exerciseEntities.map { it.toDomain() })
        }
    }

    override suspend fun update(id: String, input: RoutineInput): Routine {
        val normalized = normalizeInput(input)
        val retainedIds = normalized.exercises.mapNotNull { it.id }
        require(retainedIds.size == retainedIds.toSet().size) {
            "Routine exercise entry ids must be unique."
        }

        return writeMutex.withLock {
            val existing = routineDao.getWithExercisesById(id)
                ?.takeIf { it.routine.deletedAt == null }
                ?: throw NoSuchElementException("Active Routine not found for id=$id")

            val existingActiveById = existing.exercises
                .filter { it.deletedAt == null }
                .associateBy { it.id }

            retainedIds.forEach { entryId ->
                require(entryId in existingActiveById) {
                    "Active RoutineExercise not found for id=$entryId in Routine $id"
                }
            }

            val nowEpoch = nowMillis().toEpochMilli()
            val updatedRoutine = existing.routine.copy(
                name = normalized.name,
                discipline = normalized.discipline.name,
                updatedAt = nowEpoch,
                syncStatus = SyncStatus.PENDING.name,
            )

            val toUpsert = mutableListOf<RoutineExerciseEntity>()
            val resultingActive = normalized.exercises.mapIndexed { index, item ->
                val existingEntry = item.id?.let(existingActiveById::get)
                if (existingEntry != null) {
                    val hasChanges = existingEntry.exerciseId != item.exerciseId ||
                        existingEntry.sortOrder != index ||
                        existingEntry.defaultTargetSets != item.defaultTargetSets
                    if (hasChanges) {
                        val updatedEntry = existingEntry.copy(
                            exerciseId = item.exerciseId,
                            sortOrder = index,
                            defaultTargetSets = item.defaultTargetSets,
                            updatedAt = nowEpoch,
                            syncStatus = SyncStatus.PENDING.name,
                        )
                        toUpsert += updatedEntry
                        updatedEntry
                    } else {
                        existingEntry
                    }
                } else {
                    val createdEntry = RoutineExerciseEntity(
                        id = UUID.randomUUID().toString(),
                        routineId = existing.routine.id,
                        exerciseId = item.exerciseId,
                        sortOrder = index,
                        defaultTargetSets = item.defaultTargetSets,
                        createdAt = nowEpoch,
                        updatedAt = nowEpoch,
                        deletedAt = null,
                        syncStatus = SyncStatus.PENDING.name,
                    )
                    toUpsert += createdEntry
                    createdEntry
                }
            }

            val retainedIdSet = retainedIds.toSet()
            existingActiveById.values
                .filter { it.id !in retainedIdSet }
                .forEach { removed ->
                    toUpsert += removed.copy(
                        updatedAt = nowEpoch,
                        deletedAt = nowEpoch,
                        syncStatus = SyncStatus.PENDING.name,
                    )
                }

            routineDao.upsertRoutineWithExercises(updatedRoutine, toUpsert)
            updatedRoutine.toDomain(resultingActive.map { it.toDomain() })
        }
    }

    override suspend fun softDelete(id: String): Routine = writeMutex.withLock {
        val existing = routineDao.getWithExercisesById(id)
            ?: throw NoSuchElementException("Routine not found for id=$id")
        if (existing.routine.deletedAt != null) {
            return@withLock existing.toDomain(includeDeletedExercises = true)
        }

        val nowEpoch = nowMillis().toEpochMilli()
        val deletedRoutine = existing.routine.copy(
            updatedAt = nowEpoch,
            deletedAt = nowEpoch,
            syncStatus = SyncStatus.PENDING.name,
        )
        val newlyTombstonedExercises = existing.exercises
            .filter { it.deletedAt == null }
            .map { entry ->
                entry.copy(
                    updatedAt = nowEpoch,
                    deletedAt = nowEpoch,
                    syncStatus = SyncStatus.PENDING.name,
                )
            }
        val tombstonedById = newlyTombstonedExercises.associateBy { it.id }
        val allExercises = existing.exercises
            .map { entry -> tombstonedById[entry.id] ?: entry }
            .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))

        routineDao.upsertRoutineWithExercises(deletedRoutine, newlyTombstonedExercises)
        deletedRoutine.toDomain(allExercises.map { it.toDomain() })
    }

    private fun RoutineWithExercises.toDomain(includeDeletedExercises: Boolean): Routine {
        val domainExercises = exercises
            .let { list ->
                if (includeDeletedExercises) list else list.filter { it.deletedAt == null }
            }
            .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
            .map { it.toDomain() }
        return routine.toDomain(domainExercises)
    }

    private fun nowMillis(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)

    private fun normalizeInput(input: RoutineInput): RoutineInput {
        val trimmedName = input.name.trim()
        require(trimmedName.isNotEmpty()) { "Routine name must not be blank." }
        val normalizedExercises = input.exercises.map { item ->
            val trimmedExerciseId = item.exerciseId.trim()
            require(trimmedExerciseId.isNotEmpty()) { "Routine Exercise id must not be blank." }
            item.defaultTargetSets?.let { targetSets ->
                require(
                    targetSets in
                        RoutineExercise.MIN_DEFAULT_TARGET_SETS..RoutineExercise.MAX_DEFAULT_TARGET_SETS
                ) {
                    "Default target sets must be between ${RoutineExercise.MIN_DEFAULT_TARGET_SETS} " +
                        "and ${RoutineExercise.MAX_DEFAULT_TARGET_SETS}."
                }
            }
            RoutineExerciseInput(
                exerciseId = trimmedExerciseId,
                defaultTargetSets = item.defaultTargetSets,
                id = item.id,
            )
        }
        return RoutineInput(
            name = trimmedName,
            discipline = input.discipline,
            exercises = normalizedExercises,
        )
    }
}
