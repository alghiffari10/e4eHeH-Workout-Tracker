package com.e4eheh.e4ehehworkout.data.repository

import com.e4eheh.e4ehehworkout.data.local.InMemoryExerciseDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryRoutineDao
import com.e4eheh.e4ehehworkout.data.local.InMemorySessionDao
import com.e4eheh.e4ehehworkout.data.local.SessionEntity
import com.e4eheh.e4ehehworkout.data.local.SessionExerciseEntity
import com.e4eheh.e4ehehworkout.data.local.SessionSetEntity
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {
    private val initialInstant = Instant.parse("2026-11-23T08:00:00Z")
    private val clock = FakeClock(initialInstant)
    private val dao = InMemorySessionDao()
    private val routineDao = InMemoryRoutineDao()
    private val exerciseDao = InMemoryExerciseDao()
    private val routineRepository = DefaultRoutineRepository(routineDao, clock)
    private val exerciseRepository = DefaultExerciseRepository(exerciseDao, clock)
    private val repository = DefaultSessionRepository(
        sessionDao = dao,
        clock = clock,
        routineDao = routineDao,
        exerciseDao = exerciseDao,
    )

    @Test
    fun createLiveBlankSession_generatesUuid_setsInProgressAndSyncMetadata() = runTest {
        clock.setInstant(Instant.parse("2026-11-23T08:00:00.987654321Z"))
        val expectedInstant = Instant.parse("2026-11-23T08:00:00.987Z")

        val created = repository.createLiveBlankSession(
            discipline = Discipline.CONDITIONING,
            title = "  Evening Circuit  ",
        )

        assertNotNull(UUID.fromString(created.id))
        assertNull(created.routineId)
        assertEquals("Evening Circuit", created.title)
        assertEquals(Discipline.CONDITIONING, created.discipline)
        assertEquals(SessionStatus.IN_PROGRESS, created.status)
        assertEquals(expectedInstant, created.startedAt)
        assertNull(created.completedAt)
        assertEquals(0L, created.durationSeconds)
        assertNull(created.notes)
        assertTrue(created.exercises.isEmpty())
        assertEquals(expectedInstant, created.createdAt)
        assertEquals(expectedInstant, created.updatedAt)
        assertNull(created.deletedAt)
        assertEquals(SyncStatus.PENDING, created.syncStatus)

        assertEquals(listOf(created), repository.observeActiveSessions().first())
        assertEquals(created, repository.observeById(created.id).first())
        assertEquals(created, repository.getById(created.id))
    }

    @Test
    fun saveDraft_persistsAllFourTrackingModes_warmupFlags_notes_andBodyweightDefault() = runTest {
        val created = repository.createLiveBlankSession()
        clock.advanceBy(Duration.ofMinutes(7))
        val draftInstant = clock.instant()

        val repsExerciseId = UUID.randomUUID().toString()
        val holdExerciseId = UUID.randomUUID().toString()
        val cardioExerciseId = UUID.randomUUID().toString()
        val condExerciseId = UUID.randomUUID().toString()

        val warmupSetId = UUID.randomUUID().toString()
        val weightedSetId = UUID.randomUUID().toString()
        val holdSetId = UUID.randomUUID().toString()
        val cardioSetId = UUID.randomUUID().toString()
        val condSetId = UUID.randomUUID().toString()

        val saved = repository.saveDraft(
            id = created.id,
            input = SessionDraftInput(
                title = "  Full Spectrum Training  ",
                discipline = Discipline.CALISTHENICS,
                notes = "  High output session  ",
                exercises = listOf(
                    SessionExerciseDraftInput(
                        id = repsExerciseId,
                        exerciseId = "ex-pullup",
                        exerciseName = "  Pull-up  ",
                        exerciseDiscipline = Discipline.CALISTHENICS,
                        trackingMode = TrackingMode.REPS_AND_LOAD,
                        notes = "  Strict chest to bar  ",
                        sets = listOf(
                            SessionSetDraftInput(
                                id = warmupSetId,
                                reps = 6,
                                loadKg = null, // defaults to 0.0 bodyweight
                                durationSeconds = 999, // ignored for REPS_AND_LOAD
                                isCompleted = true,
                                isWarmup = true,
                            ),
                            SessionSetDraftInput(
                                id = weightedSetId,
                                reps = 10,
                                loadKg = 15.5,
                                isCompleted = true,
                                isWarmup = false,
                            ),
                        ),
                    ),
                    SessionExerciseDraftInput(
                        id = holdExerciseId,
                        exerciseId = "ex-lsit",
                        exerciseName = "L-Sit",
                        exerciseDiscipline = Discipline.CALISTHENICS,
                        trackingMode = TrackingMode.HOLD_DURATION,
                        notes = "   ", // normalized to null
                        sets = listOf(
                            SessionSetDraftInput(
                                id = holdSetId,
                                reps = 5, // ignored for HOLD_DURATION
                                loadKg = 10.0, // ignored for HOLD_DURATION
                                durationSeconds = 45,
                                isCompleted = true,
                                isWarmup = false,
                            ),
                        ),
                    ),
                    SessionExerciseDraftInput(
                        id = cardioExerciseId,
                        exerciseId = "ex-run",
                        exerciseName = "5K Tempo",
                        exerciseDiscipline = Discipline.CARDIO,
                        trackingMode = TrackingMode.DISTANCE_AND_TIME,
                        sets = listOf(
                            SessionSetDraftInput(
                                id = cardioSetId,
                                distanceKm = 5.0,
                                durationSeconds = 1470,
                                isCompleted = false,
                                isWarmup = false,
                            ),
                        ),
                    ),
                    SessionExerciseDraftInput(
                        id = condExerciseId,
                        exerciseId = "ex-emom",
                        exerciseName = "EMOM Finisher",
                        exerciseDiscipline = Discipline.CONDITIONING,
                        trackingMode = TrackingMode.CONDITIONING_ROUNDS,
                        sets = listOf(
                            SessionSetDraftInput(
                                id = condSetId,
                                rounds = 10,
                                reps = 12,
                                durationSeconds = 720,
                                distanceKm = 3.0, // ignored for CONDITIONING_ROUNDS
                                isCompleted = true,
                                isWarmup = false,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("Full Spectrum Training", saved.title)
        assertEquals(Discipline.CALISTHENICS, saved.discipline)
        assertEquals(SessionStatus.IN_PROGRESS, saved.status)
        assertNull(saved.completedAt)
        assertEquals(0L, saved.durationSeconds)
        assertEquals("High output session", saved.notes)
        assertEquals(draftInstant, saved.updatedAt)
        assertEquals(SyncStatus.PENDING, saved.syncStatus)

        assertEquals(4, saved.exercises.size)
        val pullup = saved.exercises[0]
        assertEquals(repsExerciseId, pullup.id)
        assertEquals("Pull-up", pullup.exerciseName)
        assertEquals(0, pullup.sortOrder)
        assertEquals("Strict chest to bar", pullup.notes)
        assertEquals(2, pullup.sets.size)
        assertEquals(0.0, pullup.sets[0].loadKg!!, 1e-6)
        assertTrue(pullup.sets[0].isBodyweight)
        assertTrue(pullup.sets[0].isWarmup)
        assertTrue(pullup.sets[0].isCompleted)
        assertNull(pullup.sets[0].durationSeconds)
        assertEquals(15.5, pullup.sets[1].loadKg!!, 1e-6)
        assertFalse(pullup.sets[1].isBodyweight)
        assertFalse(pullup.sets[1].isWarmup)

        val lsit = saved.exercises[1]
        assertNull(lsit.notes)
        assertEquals(45, lsit.sets.single().durationSeconds)
        assertNull(lsit.sets.single().reps)
        assertNull(lsit.sets.single().loadKg)

        val cardio = saved.exercises[2]
        assertEquals(5.0, cardio.sets.single().distanceKm!!, 1e-6)
        assertEquals(1470, cardio.sets.single().durationSeconds)

        val cond = saved.exercises[3]
        assertEquals(10, cond.sets.single().rounds)
        assertEquals(12, cond.sets.single().reps)
        assertEquals(720, cond.sets.single().durationSeconds)
        assertNull(cond.sets.single().distanceKm)
    }

    @Test
    fun saveDraft_reconcilesChildren_preservesUntouchedSyncedRows_andCascadesRemovedExerciseSoftDelete() =
        runTest {
            val sessionId = "00000000-0000-4000-8000-000000000501"
            val exKeepId = "00000000-0000-4000-8000-000000000511"
            val exRemoveId = "00000000-0000-4000-8000-000000000512"
            val setKeepUnchangedId = "00000000-0000-4000-8000-000000000521"
            val setRemoveId = "00000000-0000-4000-8000-000000000522"
            val setCascadeId = "00000000-0000-4000-8000-000000000523"
            val t0 = initialInstant.toEpochMilli()

            dao.upsertSessionGraph(
                session = SessionEntity(
                    id = sessionId,
                    routineId = null,
                    title = "Live Draft",
                    discipline = Discipline.CALISTHENICS.name,
                    status = SessionStatus.IN_PROGRESS.name,
                    startedAt = t0,
                    completedAt = null,
                    durationSeconds = 0L,
                    notes = null,
                    createdAt = t0,
                    updatedAt = t0,
                    deletedAt = null,
                    syncStatus = SyncStatus.SYNCED.name,
                ),
                exercises = listOf(
                    SessionExerciseEntity(
                        id = exKeepId,
                        sessionId = sessionId,
                        exerciseId = "ex-1",
                        exerciseName = "Pull-up",
                        exerciseDiscipline = Discipline.CALISTHENICS.name,
                        trackingMode = TrackingMode.REPS_AND_LOAD.name,
                        sortOrder = 0,
                        notes = null,
                        createdAt = t0,
                        updatedAt = t0,
                        deletedAt = null,
                        syncStatus = SyncStatus.SYNCED.name,
                    ),
                    SessionExerciseEntity(
                        id = exRemoveId,
                        sessionId = sessionId,
                        exerciseId = "ex-2",
                        exerciseName = "Plank",
                        exerciseDiscipline = Discipline.CALISTHENICS.name,
                        trackingMode = TrackingMode.HOLD_DURATION.name,
                        sortOrder = 1,
                        notes = null,
                        createdAt = t0,
                        updatedAt = t0,
                        deletedAt = null,
                        syncStatus = SyncStatus.SYNCED.name,
                    ),
                ),
                sets = listOf(
                    SessionSetEntity(
                        id = setKeepUnchangedId,
                        sessionExerciseId = exKeepId,
                        sortOrder = 0,
                        reps = 8,
                        loadKg = 0.0,
                        durationSeconds = null,
                        distanceKm = null,
                        rounds = null,
                        isCompleted = true,
                        isWarmup = false,
                        createdAt = t0,
                        updatedAt = t0,
                        deletedAt = null,
                        syncStatus = SyncStatus.SYNCED.name,
                    ),
                    SessionSetEntity(
                        id = setRemoveId,
                        sessionExerciseId = exKeepId,
                        sortOrder = 1,
                        reps = 6,
                        loadKg = 0.0,
                        durationSeconds = null,
                        distanceKm = null,
                        rounds = null,
                        isCompleted = false,
                        isWarmup = false,
                        createdAt = t0,
                        updatedAt = t0,
                        deletedAt = null,
                        syncStatus = SyncStatus.SYNCED.name,
                    ),
                    SessionSetEntity(
                        id = setCascadeId,
                        sessionExerciseId = exRemoveId,
                        sortOrder = 0,
                        reps = null,
                        loadKg = null,
                        durationSeconds = 60,
                        distanceKm = null,
                        rounds = null,
                        isCompleted = true,
                        isWarmup = false,
                        createdAt = t0,
                        updatedAt = t0,
                        deletedAt = null,
                        syncStatus = SyncStatus.SYNCED.name,
                    ),
                ),
            )

            clock.advanceBy(Duration.ofMinutes(5))
            val t1 = clock.instant()
            val newSetId = UUID.randomUUID().toString()

            val updated = repository.saveDraft(
                id = sessionId,
                input = SessionDraftInput(
                    title = "Live Draft",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            id = exKeepId,
                            exerciseId = "ex-1",
                            exerciseName = "Pull-up",
                            exerciseDiscipline = Discipline.CALISTHENICS,
                            trackingMode = TrackingMode.REPS_AND_LOAD,
                            sets = listOf(
                                SessionSetDraftInput(
                                    id = setKeepUnchangedId,
                                    reps = 8,
                                    loadKg = 0.0,
                                    isCompleted = true,
                                    isWarmup = false,
                                ),
                                SessionSetDraftInput(
                                    id = newSetId,
                                    reps = 10,
                                    loadKg = 5.0,
                                    isCompleted = true,
                                    isWarmup = false,
                                ),
                            ),
                        ),
                    ),
                ),
            )

            assertEquals(1, updated.exercises.size)
            val keptEx = updated.exercises.single()
            assertEquals(initialInstant, keptEx.updatedAt)
            assertEquals(SyncStatus.SYNCED, keptEx.syncStatus)

            val keptSet = keptEx.sets[0]
            assertEquals(setKeepUnchangedId, keptSet.id)
            assertEquals(initialInstant, keptSet.updatedAt)
            assertEquals(SyncStatus.SYNCED, keptSet.syncStatus)

            val addedSet = keptEx.sets[1]
            assertEquals(newSetId, addedSet.id)
            assertEquals(t1, addedSet.createdAt)
            assertEquals(t1, addedSet.updatedAt)
            assertEquals(SyncStatus.PENDING, addedSet.syncStatus)

            val withDeleted = repository.getById(sessionId, includeDeleted = true)!!
            val removedSet = withDeleted.exercises.single { it.id == exKeepId }
                .sets.single { it.id == setRemoveId }
            assertEquals(t1, removedSet.deletedAt)
            assertEquals(SyncStatus.PENDING, removedSet.syncStatus)

            val removedExercise = withDeleted.exercises.single { it.id == exRemoveId }
            assertEquals(t1, removedExercise.deletedAt)
            assertEquals(SyncStatus.PENDING, removedExercise.syncStatus)
            val cascadedSet = removedExercise.sets.single { it.id == setCascadeId }
            assertEquals(t1, cascadedSet.deletedAt)
            assertEquals(SyncStatus.PENDING, cascadedSet.syncStatus)
        }

    @Test
    fun completeSession_recordsCompletedAtDurationAndSnapshot_andResolvesBlankTitle() = runTest {
        val created = repository.createLiveBlankSession(discipline = Discipline.WEIGHT_LIFTING)
        clock.advanceBy(Duration.ofMinutes(42).plusSeconds(15))
        val finishInstant = clock.instant()

        val completed = repository.completeSession(
            id = created.id,
            input = SessionDraftInput(
                title = "   ",
                discipline = Discipline.WEIGHT_LIFTING,
                notes = "PR feeling",
                exercises = listOf(
                    SessionExerciseDraftInput(
                        exerciseId = "ex-squat",
                        exerciseName = "Back Squat",
                        exerciseDiscipline = Discipline.WEIGHT_LIFTING,
                        trackingMode = TrackingMode.REPS_AND_LOAD,
                        sets = listOf(
                            SessionSetDraftInput(
                                reps = 5,
                                loadKg = 100.0,
                                isCompleted = true,
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals("Weight Lifting Session", completed.title)
        assertEquals(SessionStatus.COMPLETED, completed.status)
        assertEquals(created.startedAt, completed.startedAt)
        assertEquals(finishInstant, completed.completedAt)
        assertEquals(2535L, completed.durationSeconds)
        assertEquals(finishInstant, completed.updatedAt)
        assertEquals(SyncStatus.PENDING, completed.syncStatus)
        assertEquals("Back Squat", completed.exercises.single().exerciseName)
        assertEquals(100.0, completed.exercises.single().sets.single().loadKg!!, 1e-6)
    }

    @Test(expected = IllegalStateException::class)
    fun saveDraft_completedSession_throwsIllegalStateException() = runTest {
        val created = repository.createLiveBlankSession()
        repository.completeSession(
            id = created.id,
            input = SessionDraftInput(title = "Done", discipline = Discipline.CALISTHENICS),
        )
        repository.saveDraft(
            id = created.id,
            input = SessionDraftInput(title = "Mutated", discipline = Discipline.CALISTHENICS),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun completeSession_alreadyCompleted_throwsIllegalStateException() = runTest {
        val created = repository.createLiveBlankSession()
        repository.completeSession(
            id = created.id,
            input = SessionDraftInput(title = "Done", discipline = Discipline.CALISTHENICS),
        )
        repository.completeSession(
            id = created.id,
            input = SessionDraftInput(title = "Done Again", discipline = Discipline.CALISTHENICS),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveDraft_negativeLoad_throwsIllegalArgumentException() = runTest {
        val created = repository.createLiveBlankSession()
        repository.saveDraft(
            id = created.id,
            input = SessionDraftInput(
                title = "Bad Load",
                discipline = Discipline.WEIGHT_LIFTING,
                exercises = listOf(
                    SessionExerciseDraftInput(
                        exerciseId = "ex-1",
                        exerciseName = "Bench",
                        exerciseDiscipline = Discipline.WEIGHT_LIFTING,
                        trackingMode = TrackingMode.REPS_AND_LOAD,
                        sets = listOf(SessionSetDraftInput(reps = 5, loadKg = -2.5)),
                    ),
                ),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveDraft_duplicateExerciseId_throwsIllegalArgumentException() = runTest {
        val created = repository.createLiveBlankSession()
        val duplicateId = UUID.randomUUID().toString()
        repository.saveDraft(
            id = created.id,
            input = SessionDraftInput(
                title = "Dup",
                discipline = Discipline.CALISTHENICS,
                exercises = listOf(
                    SessionExerciseDraftInput(
                        id = duplicateId,
                        exerciseId = "ex-1",
                        exerciseName = "Dip",
                        exerciseDiscipline = Discipline.CALISTHENICS,
                        trackingMode = TrackingMode.REPS_AND_LOAD,
                    ),
                    SessionExerciseDraftInput(
                        id = duplicateId,
                        exerciseId = "ex-2",
                        exerciseName = "Pull-up",
                        exerciseDiscipline = Discipline.CALISTHENICS,
                        trackingMode = TrackingMode.REPS_AND_LOAD,
                    ),
                ),
            ),
        )
    }

    @Test(expected = NoSuchElementException::class)
    fun saveDraft_missingSession_throwsNoSuchElementException() = runTest {
        repository.saveDraft(
            id = "missing-session",
            input = SessionDraftInput(title = "Nope", discipline = Discipline.CARDIO),
        )
    }

    @Test
    fun softDelete_tombstonesSessionExercisesAndSets_preservesPriorTombstones_andIsIdempotent() =
        runTest {
            val created = repository.createLiveBlankSession()
            val exId = UUID.randomUUID().toString()
            val keepSetId = UUID.randomUUID().toString()
            val removedSetId = UUID.randomUUID().toString()

            repository.saveDraft(
                id = created.id,
                input = SessionDraftInput(
                    title = "Session To Tombstone",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            id = exId,
                            exerciseId = "ex-1",
                            exerciseName = "Dip",
                            exerciseDiscipline = Discipline.CALISTHENICS,
                            trackingMode = TrackingMode.REPS_AND_LOAD,
                            sets = listOf(
                                SessionSetDraftInput(id = keepSetId, reps = 10, loadKg = 0.0),
                                SessionSetDraftInput(id = removedSetId, reps = 8, loadKg = 0.0),
                            ),
                        ),
                    ),
                ),
            )

            clock.advanceBy(Duration.ofMinutes(4))
            val priorDelete = clock.instant()
            repository.saveDraft(
                id = created.id,
                input = SessionDraftInput(
                    title = "Session To Tombstone",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            id = exId,
                            exerciseId = "ex-1",
                            exerciseName = "Dip",
                            exerciseDiscipline = Discipline.CALISTHENICS,
                            trackingMode = TrackingMode.REPS_AND_LOAD,
                            sets = listOf(
                                SessionSetDraftInput(id = keepSetId, reps = 10, loadKg = 0.0),
                            ),
                        ),
                    ),
                ),
            )

            clock.advanceBy(Duration.ofMinutes(10))
            val deleteInstant = clock.instant()
            val deleted = repository.softDelete(created.id)

            assertEquals(deleteInstant, deleted.deletedAt)
            assertEquals(deleteInstant, deleted.updatedAt)
            assertEquals(SyncStatus.PENDING, deleted.syncStatus)

            val ex = deleted.exercises.single()
            assertEquals(deleteInstant, ex.deletedAt)
            assertEquals(SyncStatus.PENDING, ex.syncStatus)

            val activeDeletedSet = ex.sets.single { it.id == keepSetId }
            assertEquals(deleteInstant, activeDeletedSet.deletedAt)
            assertEquals(SyncStatus.PENDING, activeDeletedSet.syncStatus)

            val priorSet = ex.sets.single { it.id == removedSetId }
            assertEquals(priorDelete, priorSet.deletedAt)
            assertEquals(priorDelete, priorSet.updatedAt)

            assertNull(repository.getById(created.id))
            assertNull(repository.observeById(created.id).first())
            assertTrue(repository.observeActiveSessions().first().isEmpty())

            clock.advanceBy(Duration.ofMinutes(5))
            assertEquals(deleted, repository.softDelete(created.id))
        }

    @Test
    fun createLiveSessionFromRoutine_snapshotsRoutineAndTargetSets_andPreservesIndependenceAfterRoutineEditsAndDelete() =
        runTest {
            val pushup = exerciseRepository.create(
                ExerciseInput("Archer Push-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val lsit = exerciseRepository.create(
                ExerciseInput("L-Sit", Discipline.CALISTHENICS, TrackingMode.HOLD_DURATION)
            )
            val finisher = exerciseRepository.create(
                ExerciseInput("EMOM Finisher", Discipline.CONDITIONING, TrackingMode.CONDITIONING_ROUNDS)
            )
            val routine = routineRepository.create(
                RoutineInput(
                    name = "Calisthenics Push",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        RoutineExerciseInput(pushup.id, defaultTargetSets = 3),
                        RoutineExerciseInput(lsit.id, defaultTargetSets = null),
                        RoutineExerciseInput(finisher.id, defaultTargetSets = 2),
                    ),
                )
            )

            clock.advanceBy(Duration.ofMinutes(30))
            val launchInstant = clock.instant()
            val launched = repository.createLiveSessionFromRoutine(routine.id)

            assertNotNull(UUID.fromString(launched.id))
            assertEquals(routine.id, launched.routineId)
            assertEquals("Calisthenics Push", launched.title)
            assertEquals(Discipline.CALISTHENICS, launched.discipline)
            assertEquals(SessionStatus.IN_PROGRESS, launched.status)
            assertEquals(launchInstant, launched.startedAt)
            assertNull(launched.completedAt)
            assertEquals(SyncStatus.PENDING, launched.syncStatus)

            assertEquals(3, launched.exercises.size)
            val ex0 = launched.exercises[0]
            assertEquals(pushup.id, ex0.exerciseId)
            assertEquals("Archer Push-up", ex0.exerciseName)
            assertEquals(Discipline.CALISTHENICS, ex0.exerciseDiscipline)
            assertEquals(TrackingMode.REPS_AND_LOAD, ex0.trackingMode)
            assertEquals(0, ex0.sortOrder)
            assertEquals(3, ex0.sets.size)
            assertTrue(ex0.sets.all { !it.isCompleted && !it.isWarmup && it.syncStatus == SyncStatus.PENDING })
            assertTrue(ex0.sets.all { it.loadKg == 0.0 })
            assertEquals(listOf(0, 1, 2), ex0.sets.map { it.sortOrder })

            val ex1 = launched.exercises[1]
            assertEquals(lsit.id, ex1.exerciseId)
            assertEquals(1, ex1.sortOrder)
            assertTrue(ex1.sets.isEmpty())

            val ex2 = launched.exercises[2]
            assertEquals(finisher.id, ex2.exerciseId)
            assertEquals(Discipline.CONDITIONING, ex2.exerciseDiscipline)
            assertEquals(TrackingMode.CONDITIONING_ROUNDS, ex2.trackingMode)
            assertEquals(2, ex2.sortOrder)
            assertEquals(2, ex2.sets.size)
            assertTrue(ex2.sets.all { it.loadKg == null })

            // Mutate and soft-delete the originating Routine & Exercise: Session snapshot remains intact
            clock.advanceBy(Duration.ofMinutes(5))
            routineRepository.update(
                routine.id,
                RoutineInput(
                    name = "Mutated Routine",
                    discipline = Discipline.CARDIO,
                    exercises = listOf(RoutineExerciseInput(lsit.id, defaultTargetSets = 1)),
                ),
            )
            exerciseRepository.update(
                pushup.id,
                ExerciseInput("One-Arm Push-up", Discipline.WEIGHT_LIFTING, TrackingMode.HOLD_DURATION),
            )
            routineRepository.softDelete(routine.id)

            val reloadedInProgress = repository.getById(launched.id)!!
            assertEquals(launched, reloadedInProgress)
            assertEquals("Calisthenics Push", reloadedInProgress.title)
            assertEquals("Archer Push-up", reloadedInProgress.exercises[0].exerciseName)
            assertEquals(3, reloadedInProgress.exercises.size)
        }

    @Test(expected = NoSuchElementException::class)
    fun createLiveSessionFromRoutine_missingRoutine_throwsNoSuchElementException() = runTest {
        repository.createLiveSessionFromRoutine("missing-routine")
    }

    @Test(expected = NoSuchElementException::class)
    fun createLiveSessionFromRoutine_deletedRoutine_throwsNoSuchElementException() = runTest {
        val routine = routineRepository.create(
            RoutineInput(name = "Deleted", discipline = Discipline.CALISTHENICS)
        )
        routineRepository.softDelete(routine.id)
        repository.createLiveSessionFromRoutine(routine.id)
    }

    @Test(expected = NoSuchElementException::class)
    fun createLiveSessionFromRoutine_missingExercise_throwsNoSuchElementException() = runTest {
        val routine = routineRepository.create(
            RoutineInput(
                name = "Broken Routine",
                discipline = Discipline.CALISTHENICS,
                exercises = listOf(RoutineExerciseInput("missing-exercise", defaultTargetSets = 2)),
            )
        )
        repository.createLiveSessionFromRoutine(routine.id)
    }
}
