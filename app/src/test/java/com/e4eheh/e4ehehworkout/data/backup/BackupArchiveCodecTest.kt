package com.e4eheh.e4ehehworkout.data.backup

import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveCodecTest {
    @Test
    fun encodeAndDecode_validArchive_preservesAllEntitiesAndMarksPendingSync() {
        val archive = sampleArchive()

        val encoded = BackupArchiveCodec.encode(archive)
        val decoded = BackupArchiveCodec.decodeAndValidate(encoded)

        assertTrue(decoded is BackupDecodeResult.Success)
        val backup = (decoded as BackupDecodeResult.Success).backup

        assertEquals(1, backup.summary.schemaVersion)
        assertEquals(1, backup.summary.exerciseCount)
        assertEquals(1, backup.summary.routineCount)
        assertEquals(1, backup.summary.sessionCount)
        assertEquals(1, backup.summary.bodyweightEntryCount)

        val exercise = backup.exercises.single()
        assertEquals("ex-1", exercise.id)
        assertEquals("Weighted Pull-up", exercise.name)
        assertEquals(Discipline.CALISTHENICS.name, exercise.discipline)
        assertEquals(TrackingMode.REPS_AND_LOAD.name, exercise.trackingMode)
        assertEquals("Chest to bar", exercise.defaultNotes)
        assertEquals(1700000000000L, exercise.createdAt)
        assertEquals(1700000005000L, exercise.updatedAt)
        assertNull(exercise.deletedAt)
        assertEquals(SyncStatus.PENDING.name, exercise.syncStatus)

        val routineGraph = backup.routines.single()
        assertEquals("rt-1", routineGraph.routine.id)
        assertEquals(SyncStatus.PENDING.name, routineGraph.routine.syncStatus)
        assertEquals("re-1", routineGraph.exercises.single().id)
        assertEquals(4, routineGraph.exercises.single().defaultTargetSets)

        val sessionGraph = backup.sessions.single()
        assertEquals("ss-1", sessionGraph.session.id)
        assertEquals(SessionStatus.COMPLETED.name, sessionGraph.session.status)
        assertEquals(1800L, sessionGraph.session.durationSeconds)
        val sessionEx = sessionGraph.exercises.single()
        assertEquals("se-1", sessionEx.sessionExercise.id)
        assertEquals(2, sessionEx.sets.size)
        assertEquals(0.0, sessionEx.sets[0].loadKg!!, 1e-9)
        assertEquals(15.5, sessionEx.sets[1].loadKg!!, 1e-9)
        assertTrue(sessionEx.sets[0].isWarmup)

        val bodyweight = backup.bodyweightEntries.single()
        assertEquals("bw-1", bodyweight.id)
        assertEquals(74.25, bodyweight.weightKg, 1e-9)
        assertEquals(SyncStatus.PENDING.name, bodyweight.syncStatus)
    }

    @Test
    fun decode_blankOrMalformedJsonOrEmptyObject_returnsFailure() {
        assertTrue(BackupArchiveCodec.decodeAndValidate("   ") is BackupDecodeResult.Failure)
        assertTrue(BackupArchiveCodec.decodeAndValidate("{not valid json") is BackupDecodeResult.Failure)
        assertTrue(BackupArchiveCodec.decodeAndValidate("[]") is BackupDecodeResult.Failure)
        assertTrue(BackupArchiveCodec.decodeAndValidate("{}") is BackupDecodeResult.Failure)
    }

    @Test
    fun decode_unsupportedOrInvalidSchemaVersion_returnsClearError() {
        val unsupported = BackupArchiveCodec.encode(sampleArchive(schemaVersion = 99))
        val unsupportedResult = BackupArchiveCodec.decodeAndValidate(unsupported)
        assertTrue(unsupportedResult is BackupDecodeResult.Failure)
        assertTrue(
            (unsupportedResult as BackupDecodeResult.Failure).reason.contains("Unsupported backup schema version 99"),
        )

        val stringVersion = """{"schemaVersion":"1","exportedAt":"2026-11-23T08:00:00Z","exercises":[],"routines":[],"sessions":[],"bodyweightEntries":[]}"""
        val stringResult = BackupArchiveCodec.decodeAndValidate(stringVersion)
        assertTrue(stringResult is BackupDecodeResult.Failure)

        val floatVersion = """{"schemaVersion":1.5,"exportedAt":"2026-11-23T08:00:00Z","exercises":[],"routines":[],"sessions":[],"bodyweightEntries":[]}"""
        val floatResult = BackupArchiveCodec.decodeAndValidate(floatVersion)
        assertTrue(floatResult is BackupDecodeResult.Failure)
    }

    @Test
    fun decode_rejectsUnknownTopLevelField_duplicateIds_invalidEnum_andInvalidDomainValues() {
        val withUnknownField = BackupArchiveCodec.encode(sampleArchive())
            .replaceFirst("{", "{\"unexpectedKey\": true,")
        assertTrue(BackupArchiveCodec.decodeAndValidate(withUnknownField) is BackupDecodeResult.Failure)

        val duplicateExercise = BackupArchiveCodec.encode(
            sampleArchive(
                exercises = listOf(
                    sampleExercise("ex-1"),
                    sampleExercise("ex-1"),
                ),
            ),
        )
        val duplicateResult = BackupArchiveCodec.decodeAndValidate(duplicateExercise)
        assertTrue(duplicateResult is BackupDecodeResult.Failure)
        assertTrue((duplicateResult as BackupDecodeResult.Failure).reason.contains("Duplicate Exercise id"))

        val invalidDiscipline = BackupArchiveCodec.encode(
            sampleArchive(
                exercises = listOf(sampleExercise("ex-1", discipline = "INVALID_DISCIPLINE")),
            ),
        )
        assertTrue(BackupArchiveCodec.decodeAndValidate(invalidDiscipline) is BackupDecodeResult.Failure)

        val nonPositiveBodyweight = BackupArchiveCodec.encode(
            sampleArchive(
                bodyweightEntries = listOf(
                    BackupBodyweightEntry(
                        id = "bw-1",
                        recordedAt = "2026-11-22T07:00:00Z",
                        weightKg = 0.0,
                        createdAt = "2026-11-22T07:00:00Z",
                        updatedAt = "2026-11-22T07:00:00Z",
                    ),
                ),
            ),
        )
        assertTrue(BackupArchiveCodec.decodeAndValidate(nonPositiveBodyweight) is BackupDecodeResult.Failure)
    }

    @Test
    fun decode_allowsSessionOrRoutineReferencingDeletedExerciseOrRoutineId() {
        // An exported Session or Routine may legitimately reference an Exercise/Routine UUID that was later soft-deleted.
        val archive = sampleArchive(exercises = emptyList())
        val result = BackupArchiveCodec.decodeAndValidate(BackupArchiveCodec.encode(archive))
        assertTrue(result is BackupDecodeResult.Success)
    }

    private fun sampleExercise(
        id: String = "ex-1",
        discipline: String = Discipline.CALISTHENICS.name,
    ) = BackupExercise(
        id = id,
        name = "Weighted Pull-up",
        discipline = discipline,
        trackingMode = TrackingMode.REPS_AND_LOAD.name,
        defaultNotes = "Chest to bar",
        createdAt = "2023-11-14T22:13:20Z",
        updatedAt = "2023-11-14T22:13:25Z",
    )

    private fun sampleArchive(
        schemaVersion: Int = 1,
        exercises: List<BackupExercise> = listOf(sampleExercise()),
        bodyweightEntries: List<BackupBodyweightEntry> = listOf(
            BackupBodyweightEntry(
                id = "bw-1",
                recordedAt = "2026-11-22T07:00:00Z",
                weightKg = 74.25,
                createdAt = "2026-11-22T07:00:00Z",
                updatedAt = "2026-11-22T07:00:00Z",
            ),
        ),
    ) = WorkoutBackupArchive(
        schemaVersion = schemaVersion,
        exportedAt = "2026-11-23T08:00:00Z",
        exercises = exercises,
        routines = listOf(
            BackupRoutine(
                id = "rt-1",
                name = "Calisthenics Pull",
                discipline = Discipline.CALISTHENICS.name,
                exercises = listOf(
                    BackupRoutineExercise(
                        id = "re-1",
                        exerciseId = "ex-1",
                        sortOrder = 0,
                        defaultTargetSets = 4,
                        createdAt = "2026-11-20T08:00:00Z",
                        updatedAt = "2026-11-20T08:00:00Z",
                    ),
                ),
                createdAt = "2026-11-20T08:00:00Z",
                updatedAt = "2026-11-20T08:00:00Z",
            ),
        ),
        sessions = listOf(
            BackupSession(
                id = "ss-1",
                routineId = "rt-1",
                title = "Calisthenics Pull",
                discipline = Discipline.CALISTHENICS.name,
                status = SessionStatus.COMPLETED.name,
                startedAt = "2026-11-21T08:00:00Z",
                completedAt = "2026-11-21T08:30:00Z",
                durationSeconds = 1800L,
                notes = "Strong grip",
                exercises = listOf(
                    BackupSessionExercise(
                        id = "se-1",
                        exerciseId = "ex-1",
                        exerciseName = "Weighted Pull-up",
                        exerciseDiscipline = Discipline.CALISTHENICS.name,
                        trackingMode = TrackingMode.REPS_AND_LOAD.name,
                        sortOrder = 0,
                        notes = "Strict pause",
                        sets = listOf(
                            BackupSessionSet(
                                id = "set-1",
                                sortOrder = 0,
                                reps = 6,
                                loadKg = 0.0,
                                isCompleted = true,
                                isWarmup = true,
                                createdAt = "2026-11-21T08:05:00Z",
                                updatedAt = "2026-11-21T08:05:00Z",
                            ),
                            BackupSessionSet(
                                id = "set-2",
                                sortOrder = 1,
                                reps = 8,
                                loadKg = 15.5,
                                isCompleted = true,
                                isWarmup = false,
                                createdAt = "2026-11-21T08:10:00Z",
                                updatedAt = "2026-11-21T08:10:00Z",
                            ),
                        ),
                        createdAt = "2026-11-21T08:00:00Z",
                        updatedAt = "2026-11-21T08:25:00Z",
                    ),
                ),
                createdAt = "2026-11-21T08:00:00Z",
                updatedAt = "2026-11-21T08:30:00Z",
            ),
        ),
        bodyweightEntries = bodyweightEntries,
    )
}
