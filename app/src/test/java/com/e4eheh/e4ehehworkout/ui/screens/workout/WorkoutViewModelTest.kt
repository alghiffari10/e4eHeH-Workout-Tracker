package com.e4eheh.e4ehehworkout.ui.screens.workout

import com.e4eheh.e4ehehworkout.data.local.InMemoryExerciseDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryRoutineDao
import com.e4eheh.e4ehehworkout.data.repository.DefaultExerciseRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultRoutineRepository
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val clock = FakeClock()
    private val exerciseRepository = DefaultExerciseRepository(InMemoryExerciseDao(), clock)
    private val routineRepository = DefaultRoutineRepository(InMemoryRoutineDao(), clock)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun createRoutine_withMixedDisciplineExercisesTargetSetsAndReordering_persistsOrderedRoutine() =
        runTest(testDispatcher) {
            val pushup = exerciseRepository.create(
                ExerciseInput("Archer Push-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val emom = exerciseRepository.create(
                ExerciseInput("EMOM Finisher", Discipline.CONDITIONING, TrackingMode.CONDITIONING_ROUNDS)
            )
            val run = exerciseRepository.create(
                ExerciseInput("Hill Sprint", Discipline.CARDIO, TrackingMode.DISTANCE_AND_TIME)
            )

            val viewModel = WorkoutViewModel(exerciseRepository, routineRepository)
            val collectorJob = backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.onAction(WorkoutAction.OpenCreateRoutine)
            viewModel.onAction(WorkoutAction.SaveRoutine)
            assertEquals("Routine name is required.", viewModel.uiState.value.routineBuilder?.nameError)

            viewModel.onAction(WorkoutAction.UpdateRoutineName("  Calisthenics Push  "))
            viewModel.onAction(WorkoutAction.UpdateRoutineDiscipline(Discipline.CALISTHENICS))
            viewModel.onAction(WorkoutAction.OpenExercisePicker)
            viewModel.onAction(WorkoutAction.AddExistingExerciseToRoutine(pushup.id))
            viewModel.onAction(WorkoutAction.OpenExercisePicker)
            viewModel.onAction(WorkoutAction.AddExistingExerciseToRoutine(emom.id))
            viewModel.onAction(WorkoutAction.OpenExercisePicker)
            viewModel.onAction(WorkoutAction.AddExistingExerciseToRoutine(run.id))

            // Set target sets on pushup (null -> 1 -> 2 -> 3) and emom (null -> 1 -> null)
            viewModel.onAction(WorkoutAction.IncrementRoutineExerciseTargetSets(0))
            viewModel.onAction(WorkoutAction.IncrementRoutineExerciseTargetSets(0))
            viewModel.onAction(WorkoutAction.IncrementRoutineExerciseTargetSets(0))
            viewModel.onAction(WorkoutAction.IncrementRoutineExerciseTargetSets(1))
            viewModel.onAction(WorkoutAction.DecrementRoutineExerciseTargetSets(1))

            // Reorder: move index 2 (run) up to index 1, then move index 0 (pushup) down to index 1 -> [run, pushup, emom]
            viewModel.onAction(WorkoutAction.MoveRoutineExerciseUp(2))
            viewModel.onAction(WorkoutAction.MoveRoutineExerciseDown(0))
            viewModel.onAction(WorkoutAction.SaveRoutine)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.routineBuilder)
            val routines = routineRepository.observeActiveRoutines().first()
            assertEquals(1, routines.size)
            val saved = routines.single()
            assertEquals("Calisthenics Push", saved.name)
            assertEquals(Discipline.CALISTHENICS, saved.discipline)
            assertEquals(listOf(run.id, pushup.id, emom.id), saved.exercises.map { it.exerciseId })
            assertEquals(listOf(0, 1, 2), saved.exercises.map { it.sortOrder })
            assertEquals(listOf(null, 3, null), saved.exercises.map { it.defaultTargetSets })
            assertEquals(SyncStatus.PENDING, saved.syncStatus)

            collectorJob.cancel()
        }

    @Test
    fun inlineExerciseCreation_validatesBlankName_persistsToLibrary_andAddsToRoutineWithoutLosingDraft() =
        runTest(testDispatcher) {
            val viewModel = WorkoutViewModel(exerciseRepository, routineRepository)
            val collectorJob = backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.onAction(WorkoutAction.OpenCreateRoutine)
            viewModel.onAction(WorkoutAction.UpdateRoutineName("Hybrid Push"))
            viewModel.onAction(WorkoutAction.UpdateRoutineDiscipline(Discipline.CALISTHENICS))
            viewModel.onAction(WorkoutAction.OpenExercisePicker)
            viewModel.onAction(WorkoutAction.OpenInlineExerciseCreator)

            // Defaults to Routine's primary Discipline
            assertEquals(
                Discipline.CALISTHENICS,
                viewModel.uiState.value.routineBuilder?.inlineExercise?.discipline,
            )

            // Blank inline Exercise name triggers error while preserving Routine draft
            viewModel.onAction(WorkoutAction.SaveInlineExercise)
            assertEquals(
                "Exercise name is required.",
                viewModel.uiState.value.routineBuilder?.inlineExercise?.nameError,
            )
            assertEquals("Hybrid Push", viewModel.uiState.value.routineBuilder?.name)

            viewModel.onAction(WorkoutAction.UpdateInlineExerciseName("Kettlebell Complex"))
            viewModel.onAction(WorkoutAction.UpdateInlineExerciseDiscipline(Discipline.CONDITIONING))
            viewModel.onAction(WorkoutAction.UpdateInlineExerciseTrackingMode(TrackingMode.CONDITIONING_ROUNDS))
            viewModel.onAction(WorkoutAction.SaveInlineExercise)
            advanceUntilIdle()

            val builder = viewModel.uiState.value.routineBuilder
            assertNotNull(builder)
            assertFalse(builder!!.showExercisePicker)
            assertNull(builder.inlineExercise)
            assertEquals("Hybrid Push", builder.name)
            assertEquals(1, builder.exercises.size)

            val createdExercises = exerciseRepository.observeActiveExercises().first()
            assertEquals(1, createdExercises.size)
            val createdExercise = createdExercises.single()
            assertEquals("Kettlebell Complex", createdExercise.name)
            assertEquals(Discipline.CONDITIONING, createdExercise.discipline)
            assertEquals(TrackingMode.CONDITIONING_ROUNDS, createdExercise.trackingMode)
            assertEquals(createdExercise.id, builder.exercises.single().exerciseId)
            assertEquals(createdExercise, viewModel.uiState.value.exercisesById[createdExercise.id])

            collectorJob.cancel()
        }

    @Test
    fun editRoutine_preservesRoutineExerciseIdsOnReorder_andSoftDeletesRemovedEntries() =
        runTest(testDispatcher) {
            val ex1 = exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val ex2 = exerciseRepository.create(
                ExerciseInput("L-Sit", Discipline.CALISTHENICS, TrackingMode.HOLD_DURATION)
            )
            val ex3 = exerciseRepository.create(
                ExerciseInput("Row", Discipline.WEIGHT_LIFTING, TrackingMode.REPS_AND_LOAD)
            )
            val initialRoutine = routineRepository.create(
                RoutineInput(
                    name = "Pull Day",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        RoutineExerciseInput(ex1.id, defaultTargetSets = 4),
                        RoutineExerciseInput(ex2.id, defaultTargetSets = 3),
                    ),
                )
            )
            val originalEntry1Id = initialRoutine.exercises[0].id
            val originalEntry2Id = initialRoutine.exercises[1].id

            clock.advanceBy(Duration.ofMinutes(15))
            val viewModel = WorkoutViewModel(exerciseRepository, routineRepository)
            val collectorJob = backgroundScope.launch { viewModel.uiState.collect {} }

            viewModel.onAction(WorkoutAction.OpenEditRoutine(initialRoutine))
            viewModel.onAction(WorkoutAction.UpdateRoutineName("Pull & Core"))
            viewModel.onAction(WorkoutAction.UpdateRoutineDiscipline(Discipline.CONDITIONING))
            viewModel.onAction(WorkoutAction.MoveRoutineExerciseUp(1)) // [ex2, ex1]
            viewModel.onAction(WorkoutAction.RemoveRoutineExercise(1)) // remove ex1, keep [ex2]
            viewModel.onAction(WorkoutAction.OpenExercisePicker)
            viewModel.onAction(WorkoutAction.AddExistingExerciseToRoutine(ex3.id)) // [ex2, ex3]
            viewModel.onAction(WorkoutAction.SaveRoutine)
            advanceUntilIdle()

            val withDeleted = routineRepository.getById(initialRoutine.id, includeDeleted = true)
            assertNotNull(withDeleted)
            assertEquals("Pull & Core", withDeleted!!.name)
            assertEquals(Discipline.CONDITIONING, withDeleted.discipline)

            val active = routineRepository.getById(initialRoutine.id)!!
            assertEquals(listOf(ex2.id, ex3.id), active.exercises.map { it.exerciseId })
            assertEquals(originalEntry2Id, active.exercises[0].id)
            assertEquals(0, active.exercises[0].sortOrder)

            val tombstonedEntry = withDeleted.exercises.single { it.id == originalEntry1Id }
            assertEquals(clock.instant(), tombstonedEntry.deletedAt)
            assertEquals(SyncStatus.PENDING, tombstonedEntry.syncStatus)

            collectorJob.cancel()
        }

    @Test
    fun deleteRoutineAndExercise_confirmationsSoftDeleteRecords() = runTest(testDispatcher) {
        val exercise = exerciseRepository.create(
            ExerciseInput("Dips", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
        )
        val routine = routineRepository.create(
            RoutineInput(
                name = "Dip Routine",
                discipline = Discipline.CALISTHENICS,
                exercises = listOf(RoutineExerciseInput(exercise.id, defaultTargetSets = 3)),
            )
        )

        val viewModel = WorkoutViewModel(exerciseRepository, routineRepository)
        val collectorJob = backgroundScope.launch { viewModel.uiState.collect {} }

        clock.advanceBy(Duration.ofMinutes(10))
        viewModel.onAction(WorkoutAction.RequestDeleteRoutine(routine))
        assertEquals(routine, viewModel.uiState.value.pendingDeleteRoutine)
        viewModel.onAction(WorkoutAction.ConfirmDeleteRoutine)
        advanceUntilIdle()

        assertNull(routineRepository.getById(routine.id))
        assertTrue(viewModel.uiState.value.routines.isEmpty())

        viewModel.onAction(WorkoutAction.RequestDeleteExercise(exercise))
        assertEquals(exercise, viewModel.uiState.value.pendingDeleteExercise)
        viewModel.onAction(WorkoutAction.ConfirmDeleteExercise)
        advanceUntilIdle()

        assertNull(exerciseRepository.getById(exercise.id))
        assertTrue(viewModel.uiState.value.exercises.isEmpty())

        collectorJob.cancel()
    }
}
