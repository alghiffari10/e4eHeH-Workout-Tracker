package com.e4eheh.e4ehehworkout.data.backup

import com.e4eheh.e4ehehworkout.data.local.InMemoryBodyweightEntryDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryExerciseDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryRoutineDao
import com.e4eheh.e4ehehworkout.data.local.InMemorySessionDao
import com.e4eheh.e4ehehworkout.data.repository.DefaultBodyweightRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultExerciseRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultRoutineRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultSessionRepository
import com.e4eheh.e4ehehworkout.domain.backup.BackupImportResult
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntryInput
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultBackupRepositoryTest {
    private class LocalStack(initialInstant: Instant = Instant.parse("2026-11-23T08:00:00Z")) {
        val clock = FakeClock(initialInstant)
        val exerciseDao = InMemoryExerciseDao()
        val routineDao = InMemoryRoutineDao()
        val sessionDao = InMemorySessionDao()
        val bodyweightDao = InMemoryBodyweightEntryDao()
        val exerciseRepo = DefaultExerciseRepository(exerciseDao, clock)
        val routineRepo = DefaultRoutineRepository(routineDao, clock)
        val sessionRepo = DefaultSessionRepository(sessionDao, clock, routineDao, exerciseDao)
        val bodyweightRepo = DefaultBodyweightRepository(bodyweightDao, clock)
        val backupRepo = DefaultBackupRepository(
            exerciseDao = exerciseDao,
            routineDao = routineDao,
            sessionDao = sessionDao,
            bodyweightEntryDao = bodyweightDao,
            clock = clock,
        )
    }

    @Test
    fun exportAndImport_fullRoundTrip_preservesUuidsTimestampsCanonicalValuesAndAllTrackingModes() =
        runTest {
            val source = LocalStack()

            // Create active exercises covering all 4 TrackingModes + 1 soft-deleted exercise
            val pullUp = source.exerciseRepo.create(
                ExerciseInput("Weighted Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD, "Chest to bar"),
            )
            source.clock.advanceBy(Duration.ofSeconds(10))
            val lSit = source.exerciseRepo.create(
                ExerciseInput("L-Sit", Discipline.CALISTHENICS, TrackingMode.HOLD_DURATION, "Full lock"),
            )
            source.clock.advanceBy(Duration.ofSeconds(10))
            val run = source.exerciseRepo.create(
                ExerciseInput("Tempo Run", Discipline.CARDIO, TrackingMode.DISTANCE_AND_TIME),
            )
            source.clock.advanceBy(Duration.ofSeconds(10))
            val emom = source.exerciseRepo.create(
                ExerciseInput("EMOM Burpees", Discipline.CONDITIONING, TrackingMode.CONDITIONING_ROUNDS),
            )
            source.clock.advanceBy(Duration.ofSeconds(10))
            val deletedExercise = source.exerciseRepo.create(
                ExerciseInput("Deleted Dip", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD),
            )
            source.clock.advanceBy(Duration.ofSeconds(5))
            source.exerciseRepo.softDelete(deletedExercise.id)

            // Create 1 active Routine and 1 soft-deleted Routine
            source.clock.advanceBy(Duration.ofSeconds(10))
            val routine = source.routineRepo.create(
                RoutineInput(
                    name = "Hybrid Push/Pull",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        RoutineExerciseInput(pullUp.id, defaultTargetSets = 3),
                        RoutineExerciseInput(emom.id, defaultTargetSets = null),
                    ),
                ),
            )
            source.clock.advanceBy(Duration.ofSeconds(10))
            val deletedRoutine = source.routineRepo.create(
                RoutineInput(
                    name = "Retired Block",
                    discipline = Discipline.WEIGHT_LIFTING,
                    exercises = listOf(RoutineExerciseInput(pullUp.id, 2)),
                ),
            )
            source.clock.advanceBy(Duration.ofSeconds(5))
            source.routineRepo.softDelete(deletedRoutine.id)

            // Create 1 COMPLETED Session (all 4 modes, Working + Warm-up Sets) + 1 IN_PROGRESS + 1 soft-deleted
            source.clock.advanceBy(Duration.ofMinutes(1))
            val completedSession = source.sessionRepo.createCompletedSession(
                startedAt = Instant.parse("2026-11-21T07:00:00Z"),
                durationSeconds = 2400L,
                routineId = routine.id,
                input = SessionDraftInput(
                    title = "Hybrid Session",
                    discipline = Discipline.CALISTHENICS,
                    notes = "Crisp session",
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            exerciseId = pullUp.id,
                            exerciseName = pullUp.name,
                            exerciseDiscipline = pullUp.discipline,
                            trackingMode = pullUp.trackingMode,
                            notes = "Clean pause",
                            sets = listOf(
                                SessionSetDraftInput(reps = 6, loadKg = 0.0, isCompleted = true, isWarmup = true),
                                SessionSetDraftInput(reps = 8, loadKg = 20.5, isCompleted = true, isWarmup = false),
                            ),
                        ),
                        SessionExerciseDraftInput(
                            exerciseId = lSit.id,
                            exerciseName = lSit.name,
                            exerciseDiscipline = lSit.discipline,
                            trackingMode = lSit.trackingMode,
                            sets = listOf(SessionSetDraftInput(durationSeconds = 35, isCompleted = true)),
                        ),
                        SessionExerciseDraftInput(
                            exerciseId = run.id,
                            exerciseName = run.name,
                            exerciseDiscipline = run.discipline,
                            trackingMode = run.trackingMode,
                            sets = listOf(
                                SessionSetDraftInput(distanceKm = 5.25, durationSeconds = 1320, isCompleted = true),
                            ),
                        ),
                        SessionExerciseDraftInput(
                            exerciseId = emom.id,
                            exerciseName = emom.name,
                            exerciseDiscipline = emom.discipline,
                            trackingMode = emom.trackingMode,
                            sets = listOf(
                                SessionSetDraftInput(rounds = 10, reps = 120, durationSeconds = 600, isCompleted = true),
                            ),
                        ),
                    ),
                ),
            )

            source.clock.advanceBy(Duration.ofMinutes(1))
            val liveSession = source.sessionRepo.createLiveBlankSession(
                discipline = Discipline.CONDITIONING,
                title = "Live Draft",
            )
            source.clock.advanceBy(Duration.ofMinutes(1))
            val deletedSession = source.sessionRepo.createCompletedSession(
                startedAt = Instant.parse("2026-11-19T07:00:00Z"),
                durationSeconds = 900L,
                input = SessionDraftInput("Deleted Session", Discipline.CARDIO),
            )
            source.clock.advanceBy(Duration.ofSeconds(5))
            source.sessionRepo.softDelete(deletedSession.id)

            // Create 1 active Bodyweight Entry and 1 soft-deleted Bodyweight Entry
            val bwActive = source.bodyweightRepo.create(
                BodyweightEntryInput(
                    recordedAt = Instant.parse("2026-11-22T06:30:00Z"),
                    weightKg = 75.4,
                ),
            )
            val bwDeleted = source.bodyweightRepo.create(
                BodyweightEntryInput(
                    recordedAt = Instant.parse("2026-11-18T06:30:00Z"),
                    weightKg = 76.2,
                ),
            )
            source.clock.advanceBy(Duration.ofSeconds(5))
            source.bodyweightRepo.softDelete(bwDeleted.id)

            // Export
            val exportPayload = source.backupRepo.exportBackup()
            assertEquals(4, exportPayload.summary.exerciseCount)
            assertEquals(1, exportPayload.summary.routineCount)
            assertEquals(2, exportPayload.summary.sessionCount)
            assertEquals(1, exportPayload.summary.bodyweightEntryCount)
            assertFalse(exportPayload.json.contains(deletedExercise.id))
            assertFalse(exportPayload.json.contains(deletedRoutine.id))
            assertFalse(exportPayload.json.contains(deletedSession.id))
            assertFalse(exportPayload.json.contains(bwDeleted.id))

            // Restore into a completely fresh local stack at a later clock time
            val target = LocalStack(initialInstant = Instant.parse("2026-12-01T10:00:00Z"))
            val importResult = target.backupRepo.importBackup(exportPayload.json)
            assertTrue(importResult is BackupImportResult.Success)
            assertEquals(exportPayload.summary, (importResult as BackupImportResult.Success).summary)

            // Verify restored Exercises
            val restoredExercises = target.exerciseRepo.observeActiveExercises().first()
            assertEquals(4, restoredExercises.size)
            val restoredPullUp = target.exerciseRepo.getById(pullUp.id)!!
            assertEquals(pullUp.name, restoredPullUp.name)
            assertEquals(pullUp.createdAt, restoredPullUp.createdAt)
            assertEquals(pullUp.updatedAt, restoredPullUp.updatedAt)
            assertEquals(SyncStatus.PENDING, restoredPullUp.syncStatus)
            assertNull(target.exerciseRepo.getById(deletedExercise.id, includeDeleted = true))

            // Verify restored Routine + RoutineExercises
            val restoredRoutines = target.routineRepo.observeActiveRoutines().first()
            assertEquals(1, restoredRoutines.size)
            val restoredRoutine = restoredRoutines.single()
            assertEquals(routine.id, restoredRoutine.id)
            assertEquals(routine.createdAt, restoredRoutine.createdAt)
            assertEquals(routine.updatedAt, restoredRoutine.updatedAt)
            assertEquals(SyncStatus.PENDING, restoredRoutine.syncStatus)
            assertEquals(routine.exercises.map { it.id }, restoredRoutine.exercises.map { it.id })
            assertEquals(listOf(3, null), restoredRoutine.exercises.map { it.defaultTargetSets })

            // Verify restored Sessions (COMPLETED & IN_PROGRESS)
            val restoredSessions = target.sessionRepo.observeActiveSessions().first()
            assertEquals(2, restoredSessions.size)
            val restoredCompleted = target.sessionRepo.getById(completedSession.id)!!
            assertEquals(completedSession.routineId, restoredCompleted.routineId)
            assertEquals(completedSession.startedAt, restoredCompleted.startedAt)
            assertEquals(completedSession.completedAt, restoredCompleted.completedAt)
            assertEquals(completedSession.durationSeconds, restoredCompleted.durationSeconds)
            assertEquals(completedSession.createdAt, restoredCompleted.createdAt)
            assertEquals(completedSession.updatedAt, restoredCompleted.updatedAt)
            assertEquals(SyncStatus.PENDING, restoredCompleted.syncStatus)
            assertEquals(4, restoredCompleted.exercises.size)
            val pullUpSets = restoredCompleted.exercises[0].sets
            assertEquals(2, pullUpSets.size)
            assertTrue(pullUpSets[0].isWarmup)
            assertEquals(0.0, pullUpSets[0].loadKg!!, 1e-9)
            assertFalse(pullUpSets[1].isWarmup)
            assertEquals(20.5, pullUpSets[1].loadKg!!, 1e-9)
            assertEquals(35, restoredCompleted.exercises[1].sets.single().durationSeconds)
            assertEquals(5.25, restoredCompleted.exercises[2].sets.single().distanceKm!!, 1e-9)
            assertEquals(1320, restoredCompleted.exercises[2].sets.single().durationSeconds)
            assertEquals(10, restoredCompleted.exercises[3].sets.single().rounds)
            assertEquals(120, restoredCompleted.exercises[3].sets.single().reps)

            val restoredLive = target.sessionRepo.getById(liveSession.id)!!
            assertEquals(SessionStatus.IN_PROGRESS, restoredLive.status)
            assertNull(restoredLive.completedAt)

            // Verify restored Bodyweight Entry
            val restoredBw = target.bodyweightRepo.getById(bwActive.id)!!
            assertEquals(bwActive.recordedAt, restoredBw.recordedAt)
            assertEquals(75.4, restoredBw.weightKg, 1e-9)
            assertEquals(bwActive.createdAt, restoredBw.createdAt)
            assertEquals(bwActive.updatedAt, restoredBw.updatedAt)
            assertEquals(SyncStatus.PENDING, restoredBw.syncStatus)
        }

    @Test
    fun importBackup_evenWhenSourceRowsWereSynced_marksAllRestoredRecordsPendingAndIsIdempotent() =
        runTest {
            val stack = LocalStack()
            val exercise = stack.exerciseRepo.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD),
            )
            // Simulate previously SYNCED record in DB before export
            val syncedEntity = stack.exerciseDao.getById(exercise.id)!!
                .copy(syncStatus = SyncStatus.SYNCED.name)
            stack.exerciseDao.upsert(syncedEntity)

            val payload = stack.backupRepo.exportBackup()

            // Re-import twice into same DB -> no duplicate UUIDs, syncStatus set to PENDING
            assertTrue(stack.backupRepo.importBackup(payload.json) is BackupImportResult.Success)
            assertTrue(stack.backupRepo.importBackup(payload.json) is BackupImportResult.Success)

            val active = stack.exerciseRepo.observeActiveExercises().first()
            assertEquals(1, active.size)
            assertEquals(exercise.id, active.single().id)
            assertEquals(SyncStatus.PENDING, active.single().syncStatus)
        }

    @Test
    fun importBackup_reconcilesRemovedChildren_withSoftDeleteTombstones() = runTest {
        val stack = LocalStack()
        val ex1 = stack.exerciseRepo.create(
            ExerciseInput("Push-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD),
        )
        val ex2 = stack.exerciseRepo.create(
            ExerciseInput("Dip", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD),
        )
        val routine = stack.routineRepo.create(
            RoutineInput(
                name = "Push",
                discipline = Discipline.CALISTHENICS,
                exercises = listOf(
                    RoutineExerciseInput(ex1.id, 3),
                    RoutineExerciseInput(ex2.id, 3),
                ),
            ),
        )
        val session = stack.sessionRepo.createCompletedSession(
            startedAt = Instant.parse("2026-11-22T08:00:00Z"),
            durationSeconds = 1200L,
            input = SessionDraftInput(
                title = "Push Day",
                discipline = Discipline.CALISTHENICS,
                exercises = listOf(
                    SessionExerciseDraftInput(
                        exerciseId = ex1.id,
                        exerciseName = ex1.name,
                        exerciseDiscipline = ex1.discipline,
                        trackingMode = ex1.trackingMode,
                        sets = listOf(
                            SessionSetDraftInput(reps = 12, loadKg = 0.0, isCompleted = true),
                            SessionSetDraftInput(reps = 10, loadKg = 0.0, isCompleted = true),
                        ),
                    ),
                ),
            ),
        )

        // Export snapshot BEFORE adding an extra exercise & set locally
        val snapshotJson = stack.backupRepo.exportBackup().json

        // Locally add another RoutineExercise and another SessionSet
        stack.clock.advanceBy(Duration.ofMinutes(5))
        val updatedRoutine = stack.routineRepo.update(
            routine.id,
            RoutineInput(
                name = "Push",
                discipline = Discipline.CALISTHENICS,
                exercises = routine.exercises.map {
                    RoutineExerciseInput(it.exerciseId, it.defaultTargetSets, it.id)
                } + RoutineExerciseInput(ex1.id, 2),
            ),
        )
        val extraRoutineExerciseId = updatedRoutine.exercises.last().id

        val updatedSession = stack.sessionRepo.updateCompletedSession(
            id = session.id,
            startedAt = session.startedAt,
            durationSeconds = session.durationSeconds,
            input = SessionDraftInput(
                title = session.title,
                discipline = session.discipline,
                exercises = listOf(
                    SessionExerciseDraftInput(
                        id = session.exercises.single().id,
                        exerciseId = ex1.id,
                        exerciseName = ex1.name,
                        exerciseDiscipline = ex1.discipline,
                        trackingMode = ex1.trackingMode,
                        sets = session.exercises.single().sets.map {
                            SessionSetDraftInput(
                                id = it.id,
                                reps = it.reps,
                                loadKg = it.loadKg,
                                isCompleted = it.isCompleted,
                            )
                        } + SessionSetDraftInput(reps = 8, loadKg = 0.0, isCompleted = true),
                    ),
                ),
            ),
        )
        val extraSetId = updatedSession.exercises.single().sets.last().id

        // Import older snapshot -> extra children are soft-deleted, original children active
        val result = stack.backupRepo.importBackup(snapshotJson)
        assertTrue(result is BackupImportResult.Success)

        val restoredRoutine = stack.routineRepo.getById(routine.id)!!
        assertEquals(2, restoredRoutine.exercises.size)
        val routineWithDeleted = stack.routineRepo.getById(routine.id, includeDeleted = true)!!
        val tombstonedRoutineEx = routineWithDeleted.exercises.first { it.id == extraRoutineExerciseId }
        assertNotNull(tombstonedRoutineEx.deletedAt)
        assertEquals(SyncStatus.PENDING, tombstonedRoutineEx.syncStatus)

        val restoredSession = stack.sessionRepo.getById(session.id)!!
        assertEquals(2, restoredSession.exercises.single().sets.size)
        val sessionWithDeleted = stack.sessionRepo.getById(session.id, includeDeleted = true)!!
        val tombstonedSet = sessionWithDeleted.exercises.single().sets.first { it.id == extraSetId }
        assertNotNull(tombstonedSet.deletedAt)
        assertEquals(SyncStatus.PENDING, tombstonedSet.syncStatus)
    }

    @Test
    fun importBackup_invalidOrUnsupportedJson_failsGracefullyWithoutCorruptingExistingData() =
        runTest {
            val stack = LocalStack()
            val existing = stack.exerciseRepo.create(
                ExerciseInput("Plank", Discipline.CALISTHENICS, TrackingMode.HOLD_DURATION),
            )

            val malformed = stack.backupRepo.importBackup("{bad json")
            assertTrue(malformed is BackupImportResult.Failure)

            val unsupported = stack.backupRepo.importBackup(
                """{"schemaVersion":2,"exportedAt":"2026-11-23T08:00:00Z","exercises":[],"routines":[],"sessions":[],"bodyweightEntries":[]}""",
            )
            assertTrue(unsupported is BackupImportResult.Failure)
            assertTrue(
                (unsupported as BackupImportResult.Failure).reason.contains("Unsupported backup schema version 2"),
            )

            // Existing local database remains intact
            val activeExercises = stack.exerciseRepo.observeActiveExercises().first()
            assertEquals(1, activeExercises.size)
            assertEquals(existing.id, activeExercises.single().id)
            assertEquals("Plank", activeExercises.single().name)
        }
}
