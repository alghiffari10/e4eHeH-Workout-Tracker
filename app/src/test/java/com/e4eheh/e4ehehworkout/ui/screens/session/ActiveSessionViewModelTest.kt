package com.e4eheh.e4ehehworkout.ui.screens.session

import com.e4eheh.e4ehehworkout.data.local.InMemoryExerciseDao
import com.e4eheh.e4ehehworkout.data.local.InMemorySessionDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryUserPreferencesDao
import com.e4eheh.e4ehehworkout.data.repository.DefaultExerciseRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultSessionRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultUserPreferencesRepository
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.repository.ExerciseRepository
import com.e4eheh.e4ehehworkout.domain.timer.RestTimerStatus
import com.e4eheh.e4ehehworkout.domain.unit.UnitConverter
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class ActiveSessionViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val clock = FakeClock()
    private val sessionRepository = DefaultSessionRepository(InMemorySessionDao(), clock)
    private val exerciseRepository = DefaultExerciseRepository(InMemoryExerciseDao(), clock)
    private val userPreferencesRepository = DefaultUserPreferencesRepository(
        InMemoryUserPreferencesDao(),
        clock,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(sessionId: String): ActiveSessionViewModel = ActiveSessionViewModel(
        sessionId = sessionId,
        sessionRepository = sessionRepository,
        exerciseRepository = exerciseRepository,
        userPreferencesRepository = userPreferencesRepository,
        clock = clock,
    )

    @Test
    fun init_loadsInProgressSessionWithPreferencesAndTimer_andAutoSavesMetadataChanges() =
        runTest(testDispatcher) {
            userPreferencesRepository.updateWeightUnit(WeightUnit.LB)
            userPreferencesRepository.updateDistanceUnit(DistanceUnit.MI)
            val created = sessionRepository.createLiveBlankSession()
            clock.advanceBy(Duration.ofSeconds(75))

            val viewModel = createViewModel(created.id)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val loaded = viewModel.uiState.value
            assertTrue(loaded.isLoaded)
            assertFalse(loaded.isCompleted)
            assertEquals(75L, loaded.elapsedSeconds)
            assertEquals(WeightUnit.LB, loaded.weightUnit)
            assertEquals(DistanceUnit.MI, loaded.distanceUnit)

            clock.advanceBy(Duration.ofSeconds(15))
            viewModel.onAction(ActiveSessionAction.UpdateTitle("Evening Calisthenics"))
            viewModel.onAction(ActiveSessionAction.UpdateDiscipline(Discipline.CONDITIONING))
            viewModel.onAction(ActiveSessionAction.UpdateNotes("High focus"))
            advanceUntilIdle()

            val persisted = sessionRepository.getById(created.id)!!
            assertEquals(SessionStatus.IN_PROGRESS, persisted.status)
            assertEquals("Evening Calisthenics", persisted.title)
            assertEquals(Discipline.CONDITIONING, persisted.discipline)
            assertEquals("High focus", persisted.notes)
            assertEquals(SyncStatus.PENDING, persisted.syncStatus)
            assertEquals(90L, viewModel.uiState.value.elapsedSeconds)

            collector.cancel()
        }

    @Test
    fun loggingSetsAcrossAllFourTrackingModes_canonicalImperialStorage_warmupAndCheckoff_autoSavesAndRehydrates() =
        runTest(testDispatcher) {
            userPreferencesRepository.updateWeightUnit(WeightUnit.LB)
            userPreferencesRepository.updateDistanceUnit(DistanceUnit.MI)

            val pullup = exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val lsit = exerciseRepository.create(
                ExerciseInput("L-Sit", Discipline.CALISTHENICS, TrackingMode.HOLD_DURATION)
            )
            val run = exerciseRepository.create(
                ExerciseInput("Tempo Run", Discipline.CARDIO, TrackingMode.DISTANCE_AND_TIME)
            )
            val emom = exerciseRepository.create(
                ExerciseInput("EMOM Circuit", Discipline.CONDITIONING, TrackingMode.CONDITIONING_ROUNDS)
            )

            val session = sessionRepository.createLiveBlankSession()
            val viewModel = createViewModel(session.id)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // Add all 4 exercises (each starts with 1 blank Working Set)
            viewModel.onAction(ActiveSessionAction.OpenExercisePicker)
            viewModel.onAction(ActiveSessionAction.AddExistingExercise(pullup.id))
            viewModel.onAction(ActiveSessionAction.OpenExercisePicker)
            viewModel.onAction(ActiveSessionAction.AddExistingExercise(lsit.id))
            viewModel.onAction(ActiveSessionAction.OpenExercisePicker)
            viewModel.onAction(ActiveSessionAction.AddExistingExercise(run.id))
            viewModel.onAction(ActiveSessionAction.OpenExercisePicker)
            viewModel.onAction(ActiveSessionAction.AddExistingExercise(emom.id))

            // Exercise 0 (REPS_AND_LOAD): Set 0 Warm-up bodyweight (blank load), Set 1 Working 45 lb, Set 2 added & deleted
            viewModel.onAction(ActiveSessionAction.UpdateExerciseNotes(0, "Strict form"))
            viewModel.onAction(ActiveSessionAction.ToggleSetWarmup(0, 0))
            viewModel.onAction(ActiveSessionAction.UpdateSetReps(0, 0, "8"))
            viewModel.onAction(ActiveSessionAction.ToggleSetCompleted(0, 0))

            viewModel.onAction(ActiveSessionAction.AddSet(0))
            viewModel.onAction(ActiveSessionAction.UpdateSetReps(0, 1, "10"))
            viewModel.onAction(ActiveSessionAction.UpdateSetLoad(0, 1, "45"))
            viewModel.onAction(ActiveSessionAction.ToggleSetCompleted(0, 1))

            viewModel.onAction(ActiveSessionAction.AddSet(0))
            viewModel.onAction(ActiveSessionAction.RemoveSet(0, 2))

            // Exercise 1 (HOLD_DURATION): 45s hold
            viewModel.onAction(ActiveSessionAction.UpdateSetHoldSeconds(1, 0, "45"))
            viewModel.onAction(ActiveSessionAction.ToggleSetCompleted(1, 0))

            // Exercise 2 (DISTANCE_AND_TIME): 3.11 mi in 24m 30s
            viewModel.onAction(ActiveSessionAction.UpdateSetDistance(2, 0, "3.11"))
            viewModel.onAction(ActiveSessionAction.UpdateSetDurationMinutes(2, 0, "24"))
            viewModel.onAction(ActiveSessionAction.UpdateSetDurationSeconds(2, 0, "30"))
            viewModel.onAction(ActiveSessionAction.ToggleSetCompleted(2, 0))

            // Exercise 3 (CONDITIONING_ROUNDS): 8 rounds, 12 reps, 15m 30s
            viewModel.onAction(ActiveSessionAction.UpdateSetRounds(3, 0, "8"))
            viewModel.onAction(ActiveSessionAction.UpdateSetReps(3, 0, "12"))
            viewModel.onAction(ActiveSessionAction.UpdateSetDurationMinutes(3, 0, "15"))
            viewModel.onAction(ActiveSessionAction.UpdateSetDurationSeconds(3, 0, "30"))
            viewModel.onAction(ActiveSessionAction.ToggleSetCompleted(3, 0))

            advanceUntilIdle()

            // Verify mid-session auto-saved canonical storage
            val midSession = sessionRepository.getById(session.id)!!
            assertEquals(SessionStatus.IN_PROGRESS, midSession.status)
            assertEquals(4, midSession.exercises.size)

            val savedPullup = midSession.exercises[0]
            assertEquals("Strict form", savedPullup.notes)
            assertEquals(2, savedPullup.sets.size)
            assertTrue(savedPullup.sets[0].isWarmup)
            assertTrue(savedPullup.sets[0].isCompleted)
            assertEquals(8, savedPullup.sets[0].reps)
            assertEquals(0.0, savedPullup.sets[0].loadKg!!, 1e-6)
            assertTrue(savedPullup.sets[0].isBodyweight)

            assertFalse(savedPullup.sets[1].isWarmup)
            assertTrue(savedPullup.sets[1].isCompleted)
            assertEquals(10, savedPullup.sets[1].reps)
            assertEquals(
                UnitConverter.weightToCanonicalKg(45.0, WeightUnit.LB),
                savedPullup.sets[1].loadKg!!,
                1e-6,
            )

            val savedLsit = midSession.exercises[1]
            assertEquals(45, savedLsit.sets.single().durationSeconds)

            val savedRun = midSession.exercises[2]
            assertEquals(
                UnitConverter.distanceToCanonicalKm(3.11, DistanceUnit.MI),
                savedRun.sets.single().distanceKm!!,
                1e-6,
            )
            assertEquals(1470, savedRun.sets.single().durationSeconds)

            val savedEmom = midSession.exercises[3]
            assertEquals(8, savedEmom.sets.single().rounds)
            assertEquals(12, savedEmom.sets.single().reps)
            assertEquals(930, savedEmom.sets.single().durationSeconds)

            collector.cancel()

            // Rehydrate a brand-new ViewModel (simulating app kill/resume) and check imperial formatting
            val rehydrated = createViewModel(session.id)
            val rehydratedCollector = backgroundScope.launch { rehydrated.uiState.collect {} }
            advanceUntilIdle()

            val draftExercises = rehydrated.uiState.value.draft.exercises
            assertEquals(4, draftExercises.size)
            assertEquals("Strict form", draftExercises[0].notes)
            assertEquals("8", draftExercises[0].sets[0].repsText)
            assertEquals("", draftExercises[0].sets[0].loadText) // 0.0 bodyweight restores as empty
            assertTrue(draftExercises[0].sets[0].isWarmup)
            assertTrue(draftExercises[0].sets[0].isCompleted)
            assertEquals("10", draftExercises[0].sets[1].repsText)
            assertEquals("45", draftExercises[0].sets[1].loadText)
            assertEquals("45", draftExercises[1].sets.single().holdSecondsText)
            assertEquals("3.11", draftExercises[2].sets.single().distanceText)
            assertEquals("24", draftExercises[2].sets.single().durationMinText)
            assertEquals("30", draftExercises[2].sets.single().durationSecText)
            assertEquals("8", draftExercises[3].sets.single().roundsText)
            assertEquals("12", draftExercises[3].sets.single().repsText)
            assertEquals("15", draftExercises[3].sets.single().durationMinText)
            assertEquals("30", draftExercises[3].sets.single().durationSecText)

            rehydratedCollector.cancel()
        }

    @Test
    fun inlineExerciseCreation_validatesBlankName_persistsAndAppendsWithBlankSet_andPreservesDraft() =
        runTest(testDispatcher) {
            val session = sessionRepository.createLiveBlankSession()
            val viewModel = createViewModel(session.id)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onAction(ActiveSessionAction.UpdateTitle("Mid-Session Inline"))
            viewModel.onAction(ActiveSessionAction.UpdateDiscipline(Discipline.CALISTHENICS))
            viewModel.onAction(ActiveSessionAction.OpenExercisePicker)
            viewModel.onAction(ActiveSessionAction.OpenInlineExerciseCreator)

            assertEquals(
                Discipline.CALISTHENICS,
                viewModel.uiState.value.inlineExercise?.discipline,
            )

            viewModel.onAction(ActiveSessionAction.SaveInlineExercise)
            assertEquals(
                "Exercise name is required.",
                viewModel.uiState.value.inlineExercise?.nameError,
            )
            assertEquals("Mid-Session Inline", viewModel.uiState.value.draft.title)

            viewModel.onAction(ActiveSessionAction.UpdateInlineExerciseName("Ring Dip"))
            viewModel.onAction(ActiveSessionAction.UpdateInlineExerciseDiscipline(Discipline.CALISTHENICS))
            viewModel.onAction(ActiveSessionAction.UpdateInlineExerciseTrackingMode(TrackingMode.REPS_AND_LOAD))
            viewModel.onAction(ActiveSessionAction.SaveInlineExercise)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.showExercisePicker)
            assertNull(state.inlineExercise)
            assertEquals(1, state.draft.exercises.size)
            assertEquals("Ring Dip", state.draft.exercises.single().exerciseName)
            assertEquals(1, state.draft.exercises.single().sets.size)
            assertFalse(state.draft.exercises.single().sets.single().isWarmup)

            val savedSession = sessionRepository.getById(session.id)!!
            assertEquals(1, savedSession.exercises.size)
            assertEquals("Ring Dip", savedSession.exercises.single().exerciseName)

            val catalogedExercises = exerciseRepository.getById(savedSession.exercises.single().exerciseId)
            assertNotNull(catalogedExercises)
            assertEquals("Ring Dip", catalogedExercises!!.name)

            collector.cancel()
        }

    @Test
    fun removeExercise_refreshTimer_andFinishSession_completesSnapshotIdempotently() =
        runTest(testDispatcher) {
            val dip = exerciseRepository.create(
                ExerciseInput("Dip", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val session = sessionRepository.createLiveBlankSession()
            val viewModel = createViewModel(session.id)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onAction(ActiveSessionAction.OpenExercisePicker)
            viewModel.onAction(ActiveSessionAction.AddExistingExercise(dip.id))
            viewModel.onAction(ActiveSessionAction.RemoveExercise(0))
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.draft.exercises.isEmpty())
            assertTrue(sessionRepository.getById(session.id)!!.exercises.isEmpty())

            clock.advanceBy(Duration.ofMinutes(18).plusSeconds(42))
            viewModel.onAction(ActiveSessionAction.RefreshTimer)
            assertEquals(1122L, viewModel.uiState.value.elapsedSeconds)

            viewModel.onAction(ActiveSessionAction.FinishSession)
            viewModel.onAction(ActiveSessionAction.FinishSession) // duplicate finish ignored
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isCompleted)
            val completed = sessionRepository.getById(session.id)!!
            assertEquals(SessionStatus.COMPLETED, completed.status)
            assertEquals(clock.instant(), completed.completedAt)
            assertEquals(1122L, completed.durationSeconds)
            assertEquals(SyncStatus.PENDING, completed.syncStatus)

            collector.cancel()
        }

    @Test
    fun inlineExerciseCreation_recoversFromFailure_allowsCloseAndRetry() =
        runTest(testDispatcher) {
            var shouldThrow = true
            val flakyExerciseRepository = object : ExerciseRepository by exerciseRepository {
                override suspend fun create(input: ExerciseInput): Exercise {
                    if (shouldThrow) throw IllegalStateException("Create failed")
                    return exerciseRepository.create(input)
                }
            }
            val session = sessionRepository.createLiveBlankSession()
            val viewModel = ActiveSessionViewModel(
                sessionId = session.id,
                sessionRepository = sessionRepository,
                exerciseRepository = flakyExerciseRepository,
                userPreferencesRepository = userPreferencesRepository,
                clock = clock,
            )
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onAction(ActiveSessionAction.OpenExercisePicker)
            viewModel.onAction(ActiveSessionAction.OpenInlineExerciseCreator)
            viewModel.onAction(ActiveSessionAction.UpdateInlineExerciseName("Archer Push-up"))
            viewModel.onAction(ActiveSessionAction.SaveInlineExercise)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.inlineExercise?.isSaving ?: true)
            assertTrue(viewModel.uiState.value.showExercisePicker)
            assertTrue(viewModel.uiState.value.draft.exercises.isEmpty())

            // Retry succeeds once repository stops throwing
            shouldThrow = false
            viewModel.onAction(ActiveSessionAction.SaveInlineExercise)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.showExercisePicker)
            assertNull(viewModel.uiState.value.inlineExercise)
            assertEquals("Archer Push-up", viewModel.uiState.value.draft.exercises.single().exerciseName)

            // Verify CloseInlineExerciseCreator / CloseExercisePicker are not wedged after error either
            shouldThrow = true
            viewModel.onAction(ActiveSessionAction.OpenExercisePicker)
            viewModel.onAction(ActiveSessionAction.OpenInlineExerciseCreator)
            viewModel.onAction(ActiveSessionAction.UpdateInlineExerciseName("Muscle-up"))
            viewModel.onAction(ActiveSessionAction.SaveInlineExercise)
            advanceUntilIdle()

            viewModel.onAction(ActiveSessionAction.CloseInlineExerciseCreator)
            assertNull(viewModel.uiState.value.inlineExercise)
            viewModel.onAction(ActiveSessionAction.CloseExercisePicker)
            assertFalse(viewModel.uiState.value.showExercisePicker)

            collector.cancel()
        }

    @Test
    fun midSessionUnitPreferenceChange_reformatsDisplayWithoutCorruptingCanonicalMetrics() =
        runTest(testDispatcher) {
            val dip = exerciseRepository.create(
                ExerciseInput("Weighted Dip", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val run = exerciseRepository.create(
                ExerciseInput("Road Run", Discipline.CARDIO, TrackingMode.DISTANCE_AND_TIME)
            )
            val session = sessionRepository.createLiveBlankSession()
            val viewModel = createViewModel(session.id)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onAction(ActiveSessionAction.OpenExercisePicker)
            viewModel.onAction(ActiveSessionAction.AddExistingExercise(dip.id))
            viewModel.onAction(ActiveSessionAction.OpenExercisePicker)
            viewModel.onAction(ActiveSessionAction.AddExistingExercise(run.id))
            viewModel.onAction(ActiveSessionAction.UpdateSetLoad(0, 0, "20"))
            viewModel.onAction(ActiveSessionAction.UpdateSetDistance(1, 0, "10"))
            advanceUntilIdle()

            userPreferencesRepository.updateWeightUnit(WeightUnit.LB)
            userPreferencesRepository.updateDistanceUnit(DistanceUnit.MI)
            advanceUntilIdle()

            val imperialState = viewModel.uiState.value
            assertEquals(WeightUnit.LB, imperialState.weightUnit)
            assertEquals(DistanceUnit.MI, imperialState.distanceUnit)
            assertEquals(
                UnitConverter.formatWeightValue(20.0, WeightUnit.LB),
                imperialState.draft.exercises[0].sets.single().loadText,
            )
            assertEquals(
                UnitConverter.formatDistanceValue(10.0, DistanceUnit.MI),
                imperialState.draft.exercises[1].sets.single().distanceText,
            )
            assertEquals(20.0, imperialState.draft.exercises[0].sets.single().loadKg!!, 1e-6)
            assertEquals(10.0, imperialState.draft.exercises[1].sets.single().distanceKm!!, 1e-6)

            userPreferencesRepository.updateWeightUnit(WeightUnit.KG)
            userPreferencesRepository.updateDistanceUnit(DistanceUnit.KM)
            advanceUntilIdle()

            val metricState = viewModel.uiState.value
            assertEquals("20", metricState.draft.exercises[0].sets.single().loadText)
            assertEquals("10", metricState.draft.exercises[1].sets.single().distanceText)
            assertEquals(20.0, metricState.draft.exercises[0].sets.single().loadKg!!, 1e-6)
            assertEquals(10.0, metricState.draft.exercises[1].sets.single().distanceKm!!, 1e-6)

            collector.cancel()
        }

    @Test
    fun checkOffSet_autoStartsConfiguredRestTimer_uncheckDoesNot_andTicksAndCompletesDeterministically() =
        runTest(testDispatcher) {
            userPreferencesRepository.updateDefaultRestTimerSeconds(60)
            val pullup = exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val session = sessionRepository.createLiveBlankSession()
            val viewModel = createViewModel(session.id)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onAction(ActiveSessionAction.OpenExercisePicker)
            viewModel.onAction(ActiveSessionAction.AddExistingExercise(pullup.id))
            viewModel.onAction(ActiveSessionAction.AddSet(0))

            assertEquals(RestTimerStatus.IDLE, viewModel.uiState.value.restTimer.status)
            assertEquals(60, viewModel.uiState.value.defaultRestTimerSeconds)

            // Checking off Set 0 starts the Rest Timer at 60s
            viewModel.onAction(ActiveSessionAction.ToggleSetCompleted(0, 0))
            assertEquals(RestTimerStatus.RUNNING, viewModel.uiState.value.restTimer.status)
            assertEquals(60, viewModel.uiState.value.restTimer.remainingSeconds)
            assertEquals(0L, viewModel.uiState.value.restTimer.completionEventId)

            // Unchecking Set 0 does not restart or clear the running Rest Timer
            clock.advanceBy(Duration.ofSeconds(15))
            viewModel.onAction(ActiveSessionAction.RefreshTimer)
            assertEquals(45, viewModel.uiState.value.restTimer.remainingSeconds)

            viewModel.onAction(ActiveSessionAction.ToggleSetCompleted(0, 0))
            assertEquals(RestTimerStatus.RUNNING, viewModel.uiState.value.restTimer.status)
            assertEquals(45, viewModel.uiState.value.restTimer.remainingSeconds)

            // Checking off Set 1 restarts the Rest Timer from now at 60s
            viewModel.onAction(ActiveSessionAction.ToggleSetCompleted(0, 1))
            assertEquals(RestTimerStatus.RUNNING, viewModel.uiState.value.restTimer.status)
            assertEquals(60, viewModel.uiState.value.restTimer.remainingSeconds)

            // Advancing clock to expiry and refreshing completes once
            clock.advanceBy(Duration.ofSeconds(60))
            viewModel.onAction(ActiveSessionAction.RefreshTimer)
            assertEquals(RestTimerStatus.COMPLETED, viewModel.uiState.value.restTimer.status)
            assertEquals(0, viewModel.uiState.value.restTimer.remainingSeconds)
            assertEquals(1L, viewModel.uiState.value.restTimer.completionEventId)

            collector.cancel()
        }

    @Test
    fun restTimerControls_adjustMinus15ClampPlus15SkipAndFinishSession_workWithoutCorruptingDraft() =
        runTest(testDispatcher) {
            userPreferencesRepository.updateDefaultRestTimerSeconds(30)
            val dip = exerciseRepository.create(
                ExerciseInput("Dip", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val session = sessionRepository.createLiveBlankSession()
            val viewModel = createViewModel(session.id)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onAction(ActiveSessionAction.OpenExercisePicker)
            viewModel.onAction(ActiveSessionAction.AddExistingExercise(dip.id))
            viewModel.onAction(ActiveSessionAction.ToggleSetCompleted(0, 0))
            assertEquals(30, viewModel.uiState.value.restTimer.remainingSeconds)

            viewModel.onAction(ActiveSessionAction.AdjustRestTimer(15))
            assertEquals(45, viewModel.uiState.value.restTimer.remainingSeconds)

            viewModel.onAction(ActiveSessionAction.AdjustRestTimer(-15))
            assertEquals(30, viewModel.uiState.value.restTimer.remainingSeconds)

            // Skip hides the timer without emitting completion
            viewModel.onAction(ActiveSessionAction.SkipRestTimer)
            assertEquals(RestTimerStatus.IDLE, viewModel.uiState.value.restTimer.status)
            assertEquals(0L, viewModel.uiState.value.restTimer.completionEventId)

            // Restart by unchecking then re-checking
            viewModel.onAction(ActiveSessionAction.ToggleSetCompleted(0, 0))
            viewModel.onAction(ActiveSessionAction.ToggleSetCompleted(0, 0))
            assertEquals(30, viewModel.uiState.value.restTimer.remainingSeconds)

            // Advance 20s -> 10s remaining, then -15s clamps to 00:00 and triggers completion
            clock.advanceBy(Duration.ofSeconds(20))
            viewModel.onAction(ActiveSessionAction.RefreshTimer)
            assertEquals(10, viewModel.uiState.value.restTimer.remainingSeconds)

            viewModel.onAction(ActiveSessionAction.AdjustRestTimer(-15))
            assertEquals(RestTimerStatus.COMPLETED, viewModel.uiState.value.restTimer.status)
            assertEquals(0, viewModel.uiState.value.restTimer.remainingSeconds)
            assertEquals(1L, viewModel.uiState.value.restTimer.completionEventId)

            // +15s from COMPLETED resumes for 15s, then FinishSession clears timer to IDLE
            viewModel.onAction(ActiveSessionAction.AdjustRestTimer(15))
            assertEquals(RestTimerStatus.RUNNING, viewModel.uiState.value.restTimer.status)
            assertEquals(15, viewModel.uiState.value.restTimer.remainingSeconds)

            viewModel.onAction(ActiveSessionAction.FinishSession)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isCompleted)
            assertEquals(RestTimerStatus.IDLE, viewModel.uiState.value.restTimer.status)

            collector.cancel()
        }
}
