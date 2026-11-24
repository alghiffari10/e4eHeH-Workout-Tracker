package com.e4eheh.e4ehehworkout.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.domain.backup.BackupImportResult
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntryInput
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryRoomTest {
    @Test
    fun roomInMemoryContainer_exportAndImport_executesAtomicallyAndRestoresAllTables() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = FakeClock(Instant.parse("2026-11-23T08:00:00Z"))
        val source = AppContainer.createInMemory(context, clock)
        val target = AppContainer.createInMemory(context, clock)
        try {
            val exercise = source.exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD),
            )
            val routine = source.routineRepository.create(
                RoutineInput(
                    name = "Pull Block",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(RoutineExerciseInput(exercise.id, 4)),
                ),
            )
            val session = source.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-22T08:00:00Z"),
                durationSeconds = 1500L,
                routineId = routine.id,
                input = SessionDraftInput(
                    title = "Pull Block",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            exerciseId = exercise.id,
                            exerciseName = exercise.name,
                            exerciseDiscipline = exercise.discipline,
                            trackingMode = exercise.trackingMode,
                            sets = listOf(
                                SessionSetDraftInput(reps = 10, loadKg = 12.5, isCompleted = true),
                            ),
                        ),
                    ),
                ),
            )
            val bodyweight = source.bodyweightRepository.create(
                BodyweightEntryInput(
                    recordedAt = Instant.parse("2026-11-23T07:00:00Z"),
                    weightKg = 74.0,
                ),
            )

            val exported = source.backupRepository.exportBackup()
            val importResult = target.backupRepository.importBackup(exported.json)
            assertTrue(importResult is BackupImportResult.Success)
            assertEquals(exported.summary, (importResult as BackupImportResult.Success).summary)

            assertEquals(listOf(exercise.id), target.exerciseRepository.observeActiveExercises().first().map { it.id })
            assertEquals(listOf(routine.id), target.routineRepository.observeActiveRoutines().first().map { it.id })
            val restoredSession = target.sessionRepository.getById(session.id)!!
            assertEquals(12.5, restoredSession.exercises.single().sets.single().loadKg!!, 1e-9)
            assertEquals(SyncStatus.PENDING, restoredSession.syncStatus)
            val restoredBw = target.bodyweightRepository.getById(bodyweight.id)!!
            assertEquals(74.0, restoredBw.weightKg, 1e-9)
            assertEquals(SyncStatus.PENDING, restoredBw.syncStatus)
        } finally {
            source.close()
            target.close()
        }
    }
}
