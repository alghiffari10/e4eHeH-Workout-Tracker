package com.e4eheh.e4ehehworkout.data.backup

import com.e4eheh.e4ehehworkout.data.local.BodyweightEntryDao
import com.e4eheh.e4ehehworkout.data.local.ExerciseDao
import com.e4eheh.e4ehehworkout.data.local.RoutineDao
import com.e4eheh.e4ehehworkout.data.local.SessionDao
import com.e4eheh.e4ehehworkout.domain.backup.BackupExportPayload
import com.e4eheh.e4ehehworkout.domain.backup.BackupFileNames
import com.e4eheh.e4ehehworkout.domain.backup.BackupImportResult
import com.e4eheh.e4ehehworkout.domain.backup.BackupRepository
import com.e4eheh.e4ehehworkout.domain.backup.BackupSummary
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Room-backed BackupRepository exporting and restoring non-deleted training records as versioned JSON. */
class DefaultBackupRepository(
    private val exerciseDao: ExerciseDao,
    private val routineDao: RoutineDao,
    private val sessionDao: SessionDao,
    private val bodyweightEntryDao: BodyweightEntryDao,
    private val clock: Clock,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
) : BackupRepository {
    private val backupMutex = Mutex()

    override suspend fun exportBackup(): BackupExportPayload = backupMutex.withLock {
        lateinit var payload: BackupExportPayload
        runInTransaction {
            val exportedAt = nowMillis().toString()
            val exercises = exerciseDao.getActive().map { entity ->
                BackupExercise(
                    id = entity.id,
                    name = entity.name,
                    discipline = entity.discipline,
                    trackingMode = entity.trackingMode,
                    defaultNotes = entity.defaultNotes,
                    createdAt = formatEpochMillis(entity.createdAt),
                    updatedAt = formatEpochMillis(entity.updatedAt),
                )
            }
            val routines = routineDao.getActiveWithExercises().map { record ->
                val activeExercises = record.exercises
                    .filter { it.deletedAt == null }
                    .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
                    .map { entry ->
                        BackupRoutineExercise(
                            id = entry.id,
                            exerciseId = entry.exerciseId,
                            sortOrder = entry.sortOrder,
                            defaultTargetSets = entry.defaultTargetSets,
                            createdAt = formatEpochMillis(entry.createdAt),
                            updatedAt = formatEpochMillis(entry.updatedAt),
                        )
                    }
                BackupRoutine(
                    id = record.routine.id,
                    name = record.routine.name,
                    discipline = record.routine.discipline,
                    exercises = activeExercises,
                    createdAt = formatEpochMillis(record.routine.createdAt),
                    updatedAt = formatEpochMillis(record.routine.updatedAt),
                )
            }
            val sessions = sessionDao.getActiveWithExercises().map { record ->
                val activeExercises = record.exercises
                    .filter { it.sessionExercise.deletedAt == null }
                    .sortedWith(compareBy({ it.sessionExercise.sortOrder }, { it.sessionExercise.createdAt }))
                    .map { exRecord ->
                        val activeSets = exRecord.sets
                            .filter { it.deletedAt == null }
                            .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
                            .map { set ->
                                BackupSessionSet(
                                    id = set.id,
                                    sortOrder = set.sortOrder,
                                    reps = set.reps,
                                    loadKg = set.loadKg,
                                    durationSeconds = set.durationSeconds,
                                    distanceKm = set.distanceKm,
                                    rounds = set.rounds,
                                    isCompleted = set.isCompleted,
                                    isWarmup = set.isWarmup,
                                    createdAt = formatEpochMillis(set.createdAt),
                                    updatedAt = formatEpochMillis(set.updatedAt),
                                )
                            }
                        val ex = exRecord.sessionExercise
                        BackupSessionExercise(
                            id = ex.id,
                            exerciseId = ex.exerciseId,
                            exerciseName = ex.exerciseName,
                            exerciseDiscipline = ex.exerciseDiscipline,
                            trackingMode = ex.trackingMode,
                            sortOrder = ex.sortOrder,
                            notes = ex.notes,
                            sets = activeSets,
                            createdAt = formatEpochMillis(ex.createdAt),
                            updatedAt = formatEpochMillis(ex.updatedAt),
                        )
                    }
                val session = record.session
                BackupSession(
                    id = session.id,
                    routineId = session.routineId,
                    title = session.title,
                    discipline = session.discipline,
                    status = session.status,
                    startedAt = formatEpochMillis(session.startedAt),
                    completedAt = session.completedAt?.let(::formatEpochMillis),
                    durationSeconds = session.durationSeconds,
                    notes = session.notes,
                    exercises = activeExercises,
                    createdAt = formatEpochMillis(session.createdAt),
                    updatedAt = formatEpochMillis(session.updatedAt),
                )
            }
            val bodyweightEntries = bodyweightEntryDao.getActive().map { entry ->
                BackupBodyweightEntry(
                    id = entry.id,
                    recordedAt = formatEpochMillis(entry.recordedAt),
                    weightKg = entry.weightKg,
                    createdAt = formatEpochMillis(entry.createdAt),
                    updatedAt = formatEpochMillis(entry.updatedAt),
                )
            }

            val archive = WorkoutBackupArchive(
                schemaVersion = BackupFileNames.CURRENT_SCHEMA_VERSION,
                exportedAt = exportedAt,
                exercises = exercises,
                routines = routines,
                sessions = sessions,
                bodyweightEntries = bodyweightEntries,
            )
            payload = BackupExportPayload(
                json = BackupArchiveCodec.encode(archive),
                suggestedFileName = BackupFileNames.suggestedFileName(clock),
                summary = BackupSummary(
                    schemaVersion = BackupFileNames.CURRENT_SCHEMA_VERSION,
                    exerciseCount = exercises.size,
                    routineCount = routines.size,
                    sessionCount = sessions.size,
                    bodyweightEntryCount = bodyweightEntries.size,
                ),
            )
        }
        payload
    }

    override suspend fun importBackup(rawJson: String): BackupImportResult {
        val decoded = when (val result = BackupArchiveCodec.decodeAndValidate(rawJson)) {
            is BackupDecodeResult.Failure -> return BackupImportResult.Failure(result.reason)
            is BackupDecodeResult.Success -> result.backup
        }

        return backupMutex.withLock {
            runInTransaction {
                val nowEpoch = nowMillis().toEpochMilli()
                val pendingSync = SyncStatus.PENDING.name

                decoded.exercises.forEach { exercise ->
                    exerciseDao.upsert(exercise)
                }

                decoded.bodyweightEntries.forEach { entry ->
                    bodyweightEntryDao.upsert(entry)
                }

                decoded.routines.forEach { routineGraph ->
                    val existing = routineDao.getWithExercisesById(routineGraph.routine.id)
                    val importedExerciseIds = routineGraph.exercises.map { it.id }.toSet()
                    val tombstonedRemovedExercises = existing?.exercises
                        .orEmpty()
                        .filter { it.deletedAt == null && it.id !in importedExerciseIds }
                        .map { removed ->
                            removed.copy(
                                updatedAt = nowEpoch,
                                deletedAt = nowEpoch,
                                syncStatus = pendingSync,
                            )
                        }
                    routineDao.upsertRoutineWithExercises(
                        routine = routineGraph.routine,
                        exercises = routineGraph.exercises + tombstonedRemovedExercises,
                    )
                }

                decoded.sessions.forEach { sessionGraph ->
                    val existing = sessionDao.getWithExercisesById(sessionGraph.session.id)
                    val importedExerciseIds = sessionGraph.exercises
                        .map { it.sessionExercise.id }
                        .toSet()
                    val importedSetIds = sessionGraph.exercises
                        .flatMap { ex -> ex.sets.map { it.id } }
                        .toSet()

                    val tombstonedExercises = mutableListOf<com.e4eheh.e4ehehworkout.data.local.SessionExerciseEntity>()
                    val tombstonedSets = mutableListOf<com.e4eheh.e4ehehworkout.data.local.SessionSetEntity>()

                    existing?.exercises.orEmpty().forEach { existingEx ->
                        val exEntity = existingEx.sessionExercise
                        if (exEntity.deletedAt == null && exEntity.id !in importedExerciseIds) {
                            tombstonedExercises += exEntity.copy(
                                updatedAt = nowEpoch,
                                deletedAt = nowEpoch,
                                syncStatus = pendingSync,
                            )
                            existingEx.sets
                                .filter { it.deletedAt == null }
                                .forEach { set ->
                                    tombstonedSets += set.copy(
                                        updatedAt = nowEpoch,
                                        deletedAt = nowEpoch,
                                        syncStatus = pendingSync,
                                    )
                                }
                        } else if (exEntity.deletedAt == null) {
                            existingEx.sets
                                .filter { it.deletedAt == null && it.id !in importedSetIds }
                                .forEach { removedSet ->
                                    tombstonedSets += removedSet.copy(
                                        updatedAt = nowEpoch,
                                        deletedAt = nowEpoch,
                                        syncStatus = pendingSync,
                                    )
                                }
                        }
                    }

                    val importedExercises = sessionGraph.exercises.map { it.sessionExercise }
                    val importedSets = sessionGraph.exercises.flatMap { it.sets }
                    sessionDao.upsertSessionGraph(
                        session = sessionGraph.session,
                        exercises = importedExercises + tombstonedExercises,
                        sets = importedSets + tombstonedSets,
                    )
                }
            }
            BackupImportResult.Success(decoded.summary)
        }
    }

    private fun nowMillis(): Instant = clock.instant().truncatedTo(ChronoUnit.MILLIS)

    private fun formatEpochMillis(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).toString()
}
