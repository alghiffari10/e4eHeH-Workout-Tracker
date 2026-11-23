package com.e4eheh.e4ehehworkout.data.repository

import com.e4eheh.e4ehehworkout.data.local.SessionDao
import com.e4eheh.e4ehehworkout.data.local.SessionEntity
import com.e4eheh.e4ehehworkout.data.local.SessionExerciseEntity
import com.e4eheh.e4ehehworkout.data.local.SessionExerciseWithSets
import com.e4eheh.e4ehehworkout.data.local.SessionSetEntity
import com.e4eheh.e4ehehworkout.data.local.SessionWithExercises
import com.e4eheh.e4ehehworkout.data.local.toDomain
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExercise
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.repository.SessionRepository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Room-backed SessionRepository enforcing draft auto-save, snapshots, and sync metadata. */
class DefaultSessionRepository(
    private val sessionDao: SessionDao,
    private val clock: Clock,
) : SessionRepository {
    private val writeMutex = Mutex()

    override fun observeActiveSessions(): Flow<List<Session>> =
        sessionDao.observeActiveWithExercises().map { list ->
            list.map { it.toDomain(includeDeletedChildren = false) }
        }

    override fun observeById(id: String): Flow<Session?> =
        sessionDao.observeWithExercisesById(id).map { record ->
            record?.toDomain(includeDeletedChildren = false)
        }

    override suspend fun getById(id: String, includeDeleted: Boolean): Session? {
        val record = sessionDao.getWithExercisesById(id) ?: return null
        if (!includeDeleted && record.session.deletedAt != null) return null
        return record.toDomain(includeDeletedChildren = includeDeleted)
    }

    override suspend fun createLiveBlankSession(
        discipline: Discipline,
        title: String,
    ): Session = writeMutex.withLock {
        val nowEpoch = nowMillis().toEpochMilli()
        val sessionEntity = SessionEntity(
            id = UUID.randomUUID().toString(),
            routineId = null,
            title = title.trim(),
            discipline = discipline.name,
            status = SessionStatus.IN_PROGRESS.name,
            startedAt = nowEpoch,
            completedAt = null,
            durationSeconds = 0L,
            notes = null,
            createdAt = nowEpoch,
            updatedAt = nowEpoch,
            deletedAt = null,
            syncStatus = SyncStatus.PENDING.name,
        )
        sessionDao.upsertSession(sessionEntity)
        sessionEntity.toDomain(emptyList())
    }

    override suspend fun saveDraft(id: String, input: SessionDraftInput): Session =
        persistSessionUpdate(id = id, input = input, complete = false)

    override suspend fun completeSession(id: String, input: SessionDraftInput): Session =
        persistSessionUpdate(id = id, input = input, complete = true)

    override suspend fun softDelete(id: String): Session = writeMutex.withLock {
        val existing = sessionDao.getWithExercisesById(id)
            ?: throw NoSuchElementException("Session not found for id=$id")
        if (existing.session.deletedAt != null) {
            return@withLock existing.toDomain(includeDeletedChildren = true)
        }

        val nowEpoch = nowMillis().toEpochMilli()
        val deletedSession = existing.session.copy(
            updatedAt = nowEpoch,
            deletedAt = nowEpoch,
            syncStatus = SyncStatus.PENDING.name,
        )
        val newlyTombstonedExercises = existing.exercises
            .map { it.sessionExercise }
            .filter { it.deletedAt == null }
            .map { exercise ->
                exercise.copy(
                    updatedAt = nowEpoch,
                    deletedAt = nowEpoch,
                    syncStatus = SyncStatus.PENDING.name,
                )
            }
        val newlyTombstonedSets = existing.exercises
            .flatMap { it.sets }
            .filter { it.deletedAt == null }
            .map { set ->
                set.copy(
                    updatedAt = nowEpoch,
                    deletedAt = nowEpoch,
                    syncStatus = SyncStatus.PENDING.name,
                )
            }

        sessionDao.upsertSessionGraph(
            session = deletedSession,
            exercises = newlyTombstonedExercises,
            sets = newlyTombstonedSets,
        )

        val tombstonedExById = newlyTombstonedExercises.associateBy { it.id }
        val tombstonedSetById = newlyTombstonedSets.associateBy { it.id }
        val allExercises = existing.exercises
            .map { entry ->
                val resolvedExercise = tombstonedExById[entry.sessionExercise.id] ?: entry.sessionExercise
                val resolvedSets = entry.sets
                    .map { set -> tombstonedSetById[set.id] ?: set }
                    .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
                    .map { it.toDomain() }
                resolvedExercise.toDomain(resolvedSets)
            }
            .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))

        deletedSession.toDomain(allExercises)
    }

    private suspend fun persistSessionUpdate(
        id: String,
        input: SessionDraftInput,
        complete: Boolean,
    ): Session {
        val normalized = normalizeAndValidateDraft(input)
        val incomingExerciseIds = normalized.exercises.map { it.id }
        val incomingSetIds = normalized.exercises.flatMap { ex -> ex.sets.map { it.id } }

        return writeMutex.withLock {
            val existing = sessionDao.getWithExercisesById(id)
                ?.takeIf { it.session.deletedAt == null }
                ?: throw NoSuchElementException("Active Session not found for id=$id")
            check(existing.session.status == SessionStatus.IN_PROGRESS.name) {
                "Cannot modify completed Session id=$id"
            }

            val existingExercisesById = existing.exercises.associateBy { it.sessionExercise.id }
            val existingSetsById = existing.exercises
                .flatMap { it.sets }
                .associateBy { it.id }

            incomingExerciseIds.forEach { exerciseEntryId ->
                val existingEntry = existingExercisesById[exerciseEntryId]
                require(existingEntry == null || existingEntry.sessionExercise.deletedAt == null) {
                    "Cannot reuse tombstoned SessionExercise id=$exerciseEntryId"
                }
            }
            normalized.exercises.forEach { exerciseDraft ->
                exerciseDraft.sets.forEach { setDraft ->
                    val existingSet = existingSetsById[setDraft.id]
                    require(
                        existingSet == null ||
                            (existingSet.deletedAt == null &&
                                existingSet.sessionExerciseId == exerciseDraft.id),
                    ) {
                        "Invalid or tombstoned SessionSet id=${setDraft.id} for SessionExercise ${exerciseDraft.id}"
                    }
                }
            }

            val now = nowMillis()
            val nowEpoch = now.toEpochMilli()
            val resolvedTitle = if (complete && normalized.title.isEmpty()) {
                "${normalized.discipline.displayName} Session"
            } else {
                normalized.title
            }
            val startedAtInstant = Instant.ofEpochMilli(existing.session.startedAt)
            val resolvedDurationSeconds = if (complete) {
                Duration.between(startedAtInstant, now).seconds.coerceAtLeast(0L)
            } else {
                existing.session.durationSeconds
            }

            val updatedSession = existing.session.copy(
                title = resolvedTitle,
                discipline = normalized.discipline.name,
                status = if (complete) SessionStatus.COMPLETED.name else SessionStatus.IN_PROGRESS.name,
                completedAt = if (complete) nowEpoch else null,
                durationSeconds = resolvedDurationSeconds,
                notes = normalized.notes,
                updatedAt = nowEpoch,
                syncStatus = SyncStatus.PENDING.name,
            )

            val exercisesToUpsert = mutableListOf<SessionExerciseEntity>()
            val setsToUpsert = mutableListOf<SessionSetEntity>()
            val retainedExerciseSet = incomingExerciseIds.toSet()
            val retainedSetIdSet = incomingSetIds.toSet()

            val activeExercises = normalized.exercises.mapIndexed { exIndex, exDraft ->
                val existingEx = existingExercisesById[exDraft.id]?.sessionExercise
                val resolvedExEntity = reconcileExerciseEntity(
                    sessionId = existing.session.id,
                    existingEx = existingEx,
                    draft = exDraft,
                    sortOrder = exIndex,
                    nowEpoch = nowEpoch,
                )
                if (resolvedExEntity !== existingEx) {
                    exercisesToUpsert += resolvedExEntity
                }

                val activeSets = exDraft.sets.mapIndexed { setIndex, setDraft ->
                    val existingSet = existingSetsById[setDraft.id]
                    val resolvedSetEntity = reconcileSetEntity(
                        sessionExerciseId = exDraft.id,
                        trackingMode = exDraft.trackingMode,
                        existingSet = existingSet,
                        draft = setDraft,
                        sortOrder = setIndex,
                        nowEpoch = nowEpoch,
                    )
                    if (resolvedSetEntity !== existingSet) {
                        setsToUpsert += resolvedSetEntity
                    }
                    resolvedSetEntity.toDomain()
                }

                resolvedExEntity.toDomain(activeSets)
            }

            existing.exercises.forEach { existingEntry ->
                val exEntity = existingEntry.sessionExercise
                if (exEntity.deletedAt == null && exEntity.id !in retainedExerciseSet) {
                    exercisesToUpsert += exEntity.copy(
                        updatedAt = nowEpoch,
                        deletedAt = nowEpoch,
                        syncStatus = SyncStatus.PENDING.name,
                    )
                    existingEntry.sets
                        .filter { it.deletedAt == null }
                        .forEach { set ->
                            setsToUpsert += set.copy(
                                updatedAt = nowEpoch,
                                deletedAt = nowEpoch,
                                syncStatus = SyncStatus.PENDING.name,
                            )
                        }
                } else if (exEntity.deletedAt == null) {
                    existingEntry.sets
                        .filter { it.deletedAt == null && it.id !in retainedSetIdSet }
                        .forEach { removedSet ->
                            setsToUpsert += removedSet.copy(
                                updatedAt = nowEpoch,
                                deletedAt = nowEpoch,
                                syncStatus = SyncStatus.PENDING.name,
                            )
                        }
                }
            }

            sessionDao.upsertSessionGraph(
                session = updatedSession,
                exercises = exercisesToUpsert,
                sets = setsToUpsert,
            )
            updatedSession.toDomain(activeExercises)
        }
    }

    private fun reconcileExerciseEntity(
        sessionId: String,
        existingEx: SessionExerciseEntity?,
        draft: SessionExerciseDraftInput,
        sortOrder: Int,
        nowEpoch: Long,
    ): SessionExerciseEntity {
        if (existingEx == null) {
            return SessionExerciseEntity(
                id = draft.id,
                sessionId = sessionId,
                exerciseId = draft.exerciseId,
                exerciseName = draft.exerciseName,
                exerciseDiscipline = draft.exerciseDiscipline.name,
                trackingMode = draft.trackingMode.name,
                sortOrder = sortOrder,
                notes = draft.notes,
                createdAt = nowEpoch,
                updatedAt = nowEpoch,
                deletedAt = null,
                syncStatus = SyncStatus.PENDING.name,
            )
        }
        val hasChanges = existingEx.exerciseId != draft.exerciseId ||
            existingEx.exerciseName != draft.exerciseName ||
            existingEx.exerciseDiscipline != draft.exerciseDiscipline.name ||
            existingEx.trackingMode != draft.trackingMode.name ||
            existingEx.sortOrder != sortOrder ||
            existingEx.notes != draft.notes
        return if (hasChanges) {
            existingEx.copy(
                exerciseId = draft.exerciseId,
                exerciseName = draft.exerciseName,
                exerciseDiscipline = draft.exerciseDiscipline.name,
                trackingMode = draft.trackingMode.name,
                sortOrder = sortOrder,
                notes = draft.notes,
                updatedAt = nowEpoch,
                syncStatus = SyncStatus.PENDING.name,
            )
        } else {
            existingEx
        }
    }

    private fun reconcileSetEntity(
        sessionExerciseId: String,
        trackingMode: TrackingMode,
        existingSet: SessionSetEntity?,
        draft: SessionSetDraftInput,
        sortOrder: Int,
        nowEpoch: Long,
    ): SessionSetEntity {
        val normalized = normalizeSetMeasurements(trackingMode, draft)
        if (existingSet == null) {
            return SessionSetEntity(
                id = draft.id,
                sessionExerciseId = sessionExerciseId,
                sortOrder = sortOrder,
                reps = normalized.reps,
                loadKg = normalized.loadKg,
                durationSeconds = normalized.durationSeconds,
                distanceKm = normalized.distanceKm,
                rounds = normalized.rounds,
                isCompleted = draft.isCompleted,
                isWarmup = draft.isWarmup,
                createdAt = nowEpoch,
                updatedAt = nowEpoch,
                deletedAt = null,
                syncStatus = SyncStatus.PENDING.name,
            )
        }
        val hasChanges = existingSet.sortOrder != sortOrder ||
            existingSet.reps != normalized.reps ||
            existingSet.loadKg != normalized.loadKg ||
            existingSet.durationSeconds != normalized.durationSeconds ||
            existingSet.distanceKm != normalized.distanceKm ||
            existingSet.rounds != normalized.rounds ||
            existingSet.isCompleted != draft.isCompleted ||
            existingSet.isWarmup != draft.isWarmup
        return if (hasChanges) {
            existingSet.copy(
                sortOrder = sortOrder,
                reps = normalized.reps,
                loadKg = normalized.loadKg,
                durationSeconds = normalized.durationSeconds,
                distanceKm = normalized.distanceKm,
                rounds = normalized.rounds,
                isCompleted = draft.isCompleted,
                isWarmup = draft.isWarmup,
                updatedAt = nowEpoch,
                syncStatus = SyncStatus.PENDING.name,
            )
        } else {
            existingSet
        }
    }

    private data class NormalizedSetMeasurements(
        val reps: Int?,
        val loadKg: Double?,
        val durationSeconds: Int?,
        val distanceKm: Double?,
        val rounds: Int?,
    )

    private fun normalizeSetMeasurements(
        trackingMode: TrackingMode,
        draft: SessionSetDraftInput,
    ): NormalizedSetMeasurements = when (trackingMode) {
        TrackingMode.REPS_AND_LOAD -> NormalizedSetMeasurements(
            reps = draft.reps,
            loadKg = normalizeZeroDouble(draft.loadKg ?: 0.0),
            durationSeconds = null,
            distanceKm = null,
            rounds = null,
        )
        TrackingMode.HOLD_DURATION -> NormalizedSetMeasurements(
            reps = null,
            loadKg = null,
            durationSeconds = draft.durationSeconds,
            distanceKm = null,
            rounds = null,
        )
        TrackingMode.DISTANCE_AND_TIME -> NormalizedSetMeasurements(
            reps = null,
            loadKg = null,
            durationSeconds = draft.durationSeconds,
            distanceKm = draft.distanceKm?.let(::normalizeZeroDouble),
            rounds = null,
        )
        TrackingMode.CONDITIONING_ROUNDS -> NormalizedSetMeasurements(
            reps = draft.reps,
            loadKg = null,
            durationSeconds = draft.durationSeconds,
            distanceKm = null,
            rounds = draft.rounds,
        )
    }

    private fun normalizeAndValidateDraft(input: SessionDraftInput): SessionDraftInput {
        val seenExerciseIds = HashSet<String>()
        val seenSetIds = HashSet<String>()

        val normalizedExercises = input.exercises.map { ex ->
            val trimmedId = ex.id.trim()
            val trimmedExerciseId = ex.exerciseId.trim()
            val trimmedExerciseName = ex.exerciseName.trim()
            require(trimmedId.isNotEmpty()) { "SessionExercise id must not be blank." }
            require(trimmedExerciseId.isNotEmpty()) { "Exercise id must not be blank." }
            require(trimmedExerciseName.isNotEmpty()) { "Exercise name must not be blank." }
            require(seenExerciseIds.add(trimmedId)) {
                "SessionExercise ids must be unique, duplicate id=$trimmedId"
            }

            val normalizedSets = ex.sets.map { set ->
                val trimmedSetId = set.id.trim()
                require(trimmedSetId.isNotEmpty()) { "SessionSet id must not be blank." }
                require(seenSetIds.add(trimmedSetId)) {
                    "SessionSet ids must be unique, duplicate id=$trimmedSetId"
                }
                set.reps?.let { require(it >= 0) { "Reps must be non-negative, got $it" } }
                set.durationSeconds?.let {
                    require(it >= 0) { "Duration must be non-negative, got $it" }
                }
                set.rounds?.let { require(it >= 0) { "Rounds must be non-negative, got $it" } }
                set.loadKg?.let {
                    require(it.isFinite() && it >= 0.0) {
                        "Load must be finite and non-negative, got $it"
                    }
                }
                set.distanceKm?.let {
                    require(it.isFinite() && it >= 0.0) {
                        "Distance must be finite and non-negative, got $it"
                    }
                }
                set.copy(
                    id = trimmedSetId,
                    loadKg = set.loadKg?.let(::normalizeZeroDouble),
                    distanceKm = set.distanceKm?.let(::normalizeZeroDouble),
                )
            }

            ex.copy(
                id = trimmedId,
                exerciseId = trimmedExerciseId,
                exerciseName = trimmedExerciseName,
                notes = ex.notes?.trim()?.takeIf { it.isNotEmpty() },
                sets = normalizedSets,
            )
        }

        return SessionDraftInput(
            title = input.title.trim(),
            discipline = input.discipline,
            notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
            exercises = normalizedExercises,
        )
    }

    private fun SessionWithExercises.toDomain(includeDeletedChildren: Boolean): Session {
        val domainExercises = exercises
            .let { list ->
                if (includeDeletedChildren) {
                    list
                } else {
                    list.filter { it.sessionExercise.deletedAt == null }
                }
            }
            .sortedWith(compareBy({ it.sessionExercise.sortOrder }, { it.sessionExercise.createdAt }))
            .map { it.toDomain(includeDeletedChildren) }
        return session.toDomain(domainExercises)
    }

    private fun SessionExerciseWithSets.toDomain(includeDeletedChildren: Boolean): SessionExercise {
        val domainSets = sets
            .let { list ->
                if (includeDeletedChildren) list else list.filter { it.deletedAt == null }
            }
            .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
            .map { it.toDomain() }
        return sessionExercise.toDomain(domainSets)
    }

    private fun normalizeZeroDouble(value: Double): Double = if (value == 0.0) 0.0 else value

    private fun nowMillis(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)
}
