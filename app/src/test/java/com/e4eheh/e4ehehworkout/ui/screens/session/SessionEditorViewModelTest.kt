package com.e4eheh.e4ehehworkout.ui.screens.session

import com.e4eheh.e4ehehworkout.data.local.InMemoryExerciseDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryRoutineDao
import com.e4eheh.e4ehehworkout.data.local.InMemorySessionDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryUserPreferencesDao
import com.e4eheh.e4ehehworkout.data.repository.DefaultExerciseRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultRoutineRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultSessionRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultUserPreferencesRepository
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.unit.UnitConverter
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import java.time.Instant
import java.util.UUID
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
class SessionEditorViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val clock = FakeClock()
    private val exerciseDao = InMemoryExerciseDao()
    private val routineDao = InMemoryRoutineDao()
    private val exerciseRepository = DefaultExerciseRepository(exerciseDao, clock)
    private val routineRepository = DefaultRoutineRepository(routineDao, clock)
    private val sessionRepository = DefaultSessionRepository(
        sessionDao = InMemorySessionDao(),
        clock = clock,
        routineDao = routineDao,
        exerciseDao = exerciseDao,
    )
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

    private fun createViewModel(sessionId: String? = null): SessionEditorViewModel =
        SessionEditorViewModel(
            sessionId = sessionId,
            sessionRepository = sessionRepository,
            exerciseRepository = exerciseRepository,
            userPreferencesRepository = userPreferencesRepository,
            clock = clock,
            routineRepository = routineRepository,
        )

    @Test
    fun createBackdated_prefillsClockDateTime_appliesRoutine_logsAllFourTrackingModesAndInlineExercise_andSavesCompletedWithPendingSync() =
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
            val routine = routineRepository.create(
                RoutineInput(
                    name = "Full Mix",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        RoutineExerciseInput(pullup.id, defaultTargetSets = 2),
                        RoutineExerciseInput(lsit.id, defaultTargetSets = 1),
                        RoutineExerciseInput(run.id, defaultTargetSets = 1),
                    ),
                )
            )

            val viewModel = createViewModel(sessionId = null)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val initial = viewModel.uiState.value
            assertTrue(initial.isLoaded)
            assertFalse(initial.isDetail)
            assertEquals(SessionEditorMode.CREATE_BACKDATED, initial.mode)
            assertEquals("2026-11-23", initial.dateText)
            assertEquals("08:00", initial.timeText)
            assertEquals("", initial.durationMinText)
            assertEquals("", initial.durationSecText)

            // Apply Routine, toggle off by tapping again, then re-apply and verify prefill with completed target Sets
            viewModel.onAction(SessionEditorScreenAction.ApplyRoutine(routine.id))
            assertEquals(routine.id, viewModel.uiState.value.routineId)
            viewModel.onAction(SessionEditorScreenAction.ApplyRoutine(routine.id))
            assertNull(viewModel.uiState.value.routineId)
            assertEquals("", viewModel.uiState.value.draft.title)
            assertTrue(viewModel.uiState.value.draft.exercises.isEmpty())

            viewModel.onAction(SessionEditorScreenAction.ApplyRoutine(routine.id))
            val applied = viewModel.uiState.value
            assertEquals(routine.id, applied.routineId)
            assertEquals("Full Mix", applied.draft.title)
            assertEquals(Discipline.CALISTHENICS, applied.draft.discipline)
            assertEquals(3, applied.draft.exercises.size)
            assertEquals(2, applied.draft.exercises[0].sets.size)
            assertTrue(applied.draft.exercises[0].sets.all { it.isCompleted })
            assertEquals(1, applied.draft.exercises[1].sets.size)
            assertEquals(1, applied.draft.exercises[2].sets.size)

            // Add 4th Exercise inline (CONDITIONING_ROUNDS)
            viewModel.onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.OpenExercisePicker))
            viewModel.onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.OpenInlineExerciseCreator))
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateInlineExerciseName("EMOM Circuit"))
            )
            viewModel.onAction(
                SessionEditorScreenAction.Edit(
                    ActiveSessionAction.UpdateInlineExerciseDiscipline(Discipline.CONDITIONING)
                )
            )
            viewModel.onAction(
                SessionEditorScreenAction.Edit(
                    ActiveSessionAction.UpdateInlineExerciseTrackingMode(TrackingMode.CONDITIONING_ROUNDS)
                )
            )
            viewModel.onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.SaveInlineExercise))
            advanceUntilIdle()

            assertEquals(4, viewModel.uiState.value.draft.exercises.size)
            assertTrue(viewModel.uiState.value.draft.exercises[3].sets.single().isCompleted)

            // Set backdated date/time/duration and notes
            viewModel.onAction(SessionEditorScreenAction.UpdateDate("2026-11-18"))
            viewModel.onAction(SessionEditorScreenAction.UpdateTime("17:45"))
            viewModel.onAction(SessionEditorScreenAction.UpdateDurationMinutes("45"))
            viewModel.onAction(SessionEditorScreenAction.UpdateDurationSeconds("30"))
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateNotes("Migrated from notebook"))
            )

            // Exercise 0 (REPS_AND_LOAD): Set 0 Warm-up bodyweight, Set 1 Working 45 lb
            viewModel.onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.ToggleSetWarmup(0, 0)))
            viewModel.onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateSetReps(0, 0, "6")))
            viewModel.onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateSetReps(0, 1, "10")))
            viewModel.onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateSetLoad(0, 1, "45")))

            // Exercise 1 (HOLD_DURATION): 40s
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateSetHoldSeconds(1, 0, "40"))
            )

            // Exercise 2 (DISTANCE_AND_TIME): 3.11 mi in 24m 15s
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateSetDistance(2, 0, "3.11"))
            )
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateSetDurationMinutes(2, 0, "24"))
            )
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateSetDurationSeconds(2, 0, "15"))
            )

            // Exercise 3 (CONDITIONING_ROUNDS): 10 rounds, 15 reps, 12m 0s
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateSetRounds(3, 0, "10"))
            )
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateSetReps(3, 0, "15"))
            )
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateSetDurationMinutes(3, 0, "12"))
            )
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateSetDurationSeconds(3, 0, "0"))
            )

            viewModel.onAction(SessionEditorScreenAction.Save)
            advanceUntilIdle()

            val expectedStartedAt = Instant.parse("2026-11-18T17:45:00Z")
            val expectedDuration = 45L * 60L + 30L
            val persisted = sessionRepository.observeActiveSessions().first().single()
            assertEquals(SessionStatus.COMPLETED, persisted.status)
            assertEquals(SyncStatus.PENDING, persisted.syncStatus)
            assertEquals(routine.id, persisted.routineId)
            assertEquals("Full Mix", persisted.title)
            assertEquals("Migrated from notebook", persisted.notes)
            assertEquals(expectedStartedAt, persisted.startedAt)
            assertEquals(expectedStartedAt.plusSeconds(expectedDuration), persisted.completedAt)
            assertEquals(expectedDuration, persisted.durationSeconds)

            assertEquals(4, persisted.exercises.size)
            assertTrue(persisted.exercises[0].sets[0].isWarmup)
            assertEquals(6, persisted.exercises[0].sets[0].reps)
            assertEquals(0.0, persisted.exercises[0].sets[0].loadKg!!, 1e-6)
            assertEquals(
                UnitConverter.weightToCanonicalKg(45.0, WeightUnit.LB),
                persisted.exercises[0].sets[1].loadKg!!,
                1e-6,
            )
            assertEquals(40, persisted.exercises[1].sets.single().durationSeconds)
            assertEquals(
                UnitConverter.distanceToCanonicalKm(3.11, DistanceUnit.MI),
                persisted.exercises[2].sets.single().distanceKm!!,
                1e-6,
            )
            assertEquals(24 * 60 + 15, persisted.exercises[2].sets.single().durationSeconds)
            assertEquals(10, persisted.exercises[3].sets.single().rounds)
            assertEquals(15, persisted.exercises[3].sets.single().reps)
            assertEquals(12 * 60, persisted.exercises[3].sets.single().durationSeconds)

            val savedState = viewModel.uiState.value
            assertEquals(SessionEditorMode.DETAIL, savedState.mode)
            assertTrue(savedState.isDetail)
            assertEquals(persisted.id, savedState.sessionId)
            assertEquals(routine.id, savedState.routineId)

            collector.cancel()
        }

    @Test
    fun save_withInvalidOrFutureDateOrTime_surfacesValidationErrorWithoutPersisting_andClearsErrorOnEdit() =
        runTest(testDispatcher) {
            val viewModel = createViewModel(sessionId = null)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onAction(SessionEditorScreenAction.UpdateDate("2026-99-99"))
            viewModel.onAction(SessionEditorScreenAction.Save)
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.errorMessage)
            assertTrue(sessionRepository.observeActiveSessions().first().isEmpty())

            viewModel.onAction(SessionEditorScreenAction.UpdateDate("2026-11-20"))
            assertNull(viewModel.uiState.value.errorMessage)

            viewModel.onAction(SessionEditorScreenAction.UpdateTime("99:99"))
            viewModel.onAction(SessionEditorScreenAction.Save)
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.errorMessage)
            assertTrue(sessionRepository.observeActiveSessions().first().isEmpty())

            viewModel.onAction(SessionEditorScreenAction.UpdateDate("2026-11-24"))
            viewModel.onAction(SessionEditorScreenAction.UpdateTime("08:00"))
            viewModel.onAction(SessionEditorScreenAction.Save)
            advanceUntilIdle()

            assertEquals("Session date/time cannot be in the future.", viewModel.uiState.value.errorMessage)
            assertTrue(sessionRepository.observeActiveSessions().first().isEmpty())

            viewModel.onAction(SessionEditorScreenAction.UpdateTime("09:15"))
            assertNull(viewModel.uiState.value.errorMessage)

            collector.cancel()
        }

    @Test
    fun detailMode_hydratesExistingCompletedSession_editsTimingAndSets_reformatsOnUnitChange_andUpdatesWithTombstones() =
        runTest(testDispatcher) {
            val keepSetId = UUID.randomUUID().toString()
            val removedSetId = UUID.randomUUID().toString()
            val existing = sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-10T07:15:00Z"),
                durationSeconds = 1_830L,
                input = SessionDraftInput(
                    title = "Weighted Dip Session",
                    discipline = Discipline.CALISTHENICS,
                    notes = "Initial notes",
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            id = UUID.randomUUID().toString(),
                            exerciseId = "ex-dip",
                            exerciseName = "Dip",
                            exerciseDiscipline = Discipline.CALISTHENICS,
                            trackingMode = TrackingMode.REPS_AND_LOAD,
                            sets = listOf(
                                SessionSetDraftInput(
                                    id = keepSetId,
                                    reps = 8,
                                    loadKg = 20.0,
                                    isCompleted = true,
                                ),
                                SessionSetDraftInput(
                                    id = removedSetId,
                                    reps = 6,
                                    loadKg = 20.0,
                                    isCompleted = true,
                                ),
                            ),
                        ),
                    ),
                ),
            )

            val viewModel = createViewModel(sessionId = existing.id)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val loaded = viewModel.uiState.value
            assertTrue(loaded.isLoaded)
            assertTrue(loaded.isDetail)
            assertFalse(loaded.isMissing)
            assertEquals("2026-11-10", loaded.dateText)
            assertEquals("07:15", loaded.timeText)
            assertEquals("30", loaded.durationMinText)
            assertEquals("30", loaded.durationSecText)
            assertEquals("20", loaded.draft.exercises.single().sets[0].loadText)

            // Switch units mid-edit -> display reformats without corrupting canonical loadKg
            userPreferencesRepository.updateWeightUnit(WeightUnit.LB)
            advanceUntilIdle()
            assertEquals(
                UnitConverter.formatWeightValue(20.0, WeightUnit.LB),
                viewModel.uiState.value.draft.exercises.single().sets[0].loadText,
            )
            assertEquals(
                20.0,
                viewModel.uiState.value.draft.exercises.single().sets[0].loadKg!!,
                1e-6,
            )

            // Edit timing, metadata, remove set 1, and update set 0 reps
            clock.advanceBy(Duration.ofHours(2))
            viewModel.onAction(SessionEditorScreenAction.UpdateDate("2026-11-11"))
            viewModel.onAction(SessionEditorScreenAction.UpdateTime("08:05"))
            viewModel.onAction(SessionEditorScreenAction.UpdateDurationMinutes("35"))
            viewModel.onAction(SessionEditorScreenAction.UpdateDurationSeconds("0"))
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateTitle("Weighted Dip Revised"))
            )
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateSetReps(0, 0, "10"))
            )
            viewModel.onAction(
                SessionEditorScreenAction.Edit(ActiveSessionAction.RemoveSet(0, 1))
            )
            viewModel.onAction(SessionEditorScreenAction.Save)
            advanceUntilIdle()

            val updated = sessionRepository.getById(existing.id)!!
            assertEquals("Weighted Dip Revised", updated.title)
            assertEquals(Instant.parse("2026-11-11T08:05:00Z"), updated.startedAt)
            assertEquals(2_100L, updated.durationSeconds)
            assertEquals(SyncStatus.PENDING, updated.syncStatus)
            assertEquals(1, updated.exercises.single().sets.size)
            assertEquals(10, updated.exercises.single().sets.single().reps)
            assertEquals(20.0, updated.exercises.single().sets.single().loadKg!!, 1e-6)

            val withDeleted = sessionRepository.getById(existing.id, includeDeleted = true)!!
            val deletedSet = withDeleted.exercises.single().sets.single { it.id == removedSetId }
            assertNotNull(deletedSet.deletedAt)
            assertEquals(SyncStatus.PENDING, deletedSet.syncStatus)

            collector.cancel()
        }

    @Test
    fun detailMode_softDeleteConfirmationAndExecution_tombstonesSessionAndSetsIsDeleted() =
        runTest(testDispatcher) {
            val completed = sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-15T10:00:00Z"),
                durationSeconds = 900L,
                input = SessionDraftInput(title = "To Delete", discipline = Discipline.CONDITIONING),
            )

            val viewModel = createViewModel(sessionId = completed.id)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onAction(SessionEditorScreenAction.RequestDelete)
            assertTrue(viewModel.uiState.value.showDeleteConfirmation)

            viewModel.onAction(SessionEditorScreenAction.DismissDelete)
            assertFalse(viewModel.uiState.value.showDeleteConfirmation)
            assertNotNull(sessionRepository.getById(completed.id))

            clock.advanceBy(Duration.ofMinutes(5))
            viewModel.onAction(SessionEditorScreenAction.RequestDelete)
            viewModel.onAction(SessionEditorScreenAction.ConfirmDelete)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isDeleted)
            assertFalse(viewModel.uiState.value.isDeleting)
            assertNull(sessionRepository.getById(completed.id))
            val tombstone = sessionRepository.getById(completed.id, includeDeleted = true)!!
            assertEquals(clock.instant(), tombstone.deletedAt)
            assertEquals(SyncStatus.PENDING, tombstone.syncStatus)

            collector.cancel()
        }

    @Test
    fun detailMode_missingOrInProgressSession_marksStateAsMissingAndNonEditable() =
        runTest(testDispatcher) {
            val live = sessionRepository.createLiveBlankSession()

            val missingVm = createViewModel(sessionId = "does-not-exist")
            val missingCollector = backgroundScope.launch { missingVm.uiState.collect {} }
            advanceUntilIdle()
            assertTrue(missingVm.uiState.value.isLoaded)
            assertTrue(missingVm.uiState.value.isMissing)
            assertFalse(missingVm.uiState.value.isEditable)
            missingCollector.cancel()

            val inProgressVm = createViewModel(sessionId = live.id)
            val liveCollector = backgroundScope.launch { inProgressVm.uiState.collect {} }
            advanceUntilIdle()
            assertTrue(inProgressVm.uiState.value.isLoaded)
            assertTrue(inProgressVm.uiState.value.isMissing)
            assertFalse(inProgressVm.uiState.value.isEditable)
            liveCollector.cancel()
        }
}
