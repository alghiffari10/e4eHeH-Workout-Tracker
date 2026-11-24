package com.e4eheh.e4ehehworkout.ui.screens.progress

import com.e4eheh.e4ehehworkout.data.local.InMemoryBodyweightEntryDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryExerciseDao
import com.e4eheh.e4ehehworkout.data.local.InMemorySessionDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryUserPreferencesDao
import com.e4eheh.e4ehehworkout.data.repository.DefaultBodyweightRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultExerciseRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultSessionRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultUserPreferencesRepository
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressTimeRange
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressionMetric
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntryInput
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.testing.FakeClock
import com.e4eheh.e4ehehworkout.ui.components.BodyweightCrudAction
import java.time.Instant
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val clock = FakeClock(initialInstant = Instant.parse("2026-11-23T08:00:00Z"))
    private val sessionRepository = DefaultSessionRepository(InMemorySessionDao(), clock)
    private val exerciseRepository = DefaultExerciseRepository(InMemoryExerciseDao(), clock)
    private val userPreferencesRepository =
        DefaultUserPreferencesRepository(InMemoryUserPreferencesDao(), clock)
    private val bodyweightRepository =
        DefaultBodyweightRepository(InMemoryBodyweightEntryDao(), clock)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProgressViewModel = ProgressViewModel(
        sessionRepository = sessionRepository,
        exerciseRepository = exerciseRepository,
        userPreferencesRepository = userPreferencesRepository,
        bodyweightRepository = bodyweightRepository,
        clock = clock,
    )

    @Test
    fun initialState_defaultsToAllWithEmptyAnalyticsAndNoExerciseSelected() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val collector = backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProgressTimeRange.ALL, state.selectedTimeRange)
        assertEquals(0, state.analytics.totalCompletedSessions)
        assertEquals(0, state.analytics.activeWeeklyStreak)
        assertEquals(0L, state.analytics.totalDurationSeconds)
        assertEquals(Discipline.entries.size, state.analytics.disciplineBreakdown.size)
        assertEquals(0, state.analytics.disciplineBreakdown.sumOf { it.sessionCount })
        assertTrue(state.exercises.isEmpty())
        assertNull(state.selectedExerciseId)
        assertNull(state.exerciseProgression)
        assertFalse(state.isExercisePickerOpen)

        collector.cancel()
    }

    @Test
    fun repositoryUpdatesAndTimeRangeSwitching_recomputeAnalyticsExcludingDraftsAndTombstones() =
        runTest(testDispatcher) {
            // Seed sessions across time ranges + an IN_PROGRESS draft + a soft-deleted session
            sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-23T07:00:00Z"),
                durationSeconds = 1_800L,
                input = SessionDraftInput(title = "Push", discipline = Discipline.CALISTHENICS),
            )
            sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-20T18:00:00Z"),
                durationSeconds = 2_700L,
                input = SessionDraftInput(title = "Conditioning", discipline = Discipline.CONDITIONING),
            )
            sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-11T18:00:00Z"),
                durationSeconds = 3_600L,
                input = SessionDraftInput(title = "Pull", discipline = Discipline.CALISTHENICS),
            )
            sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-10-09T18:00:00Z"),
                durationSeconds = 1_800L,
                input = SessionDraftInput(title = "Run", discipline = Discipline.CARDIO),
            )
            sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-05-07T18:00:00Z"),
                durationSeconds = 2_700L,
                input = SessionDraftInput(title = "Deadlift", discipline = Discipline.WEIGHT_LIFTING),
            )
            sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2025-07-11T18:00:00Z"),
                durationSeconds = 1_800L,
                input = SessionDraftInput(title = "Old Push", discipline = Discipline.CALISTHENICS),
            )
            sessionRepository.createLiveBlankSession(
                discipline = Discipline.WEIGHT_LIFTING,
                title = "Live Draft",
            )
            val toDelete = sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-22T12:00:00Z"),
                durationSeconds = 5_400L,
                input = SessionDraftInput(title = "Deleted Run", discipline = Discipline.CARDIO),
            )
            sessionRepository.softDelete(toDelete.id)

            val viewModel = createViewModel()
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(ProgressTimeRange.ALL, viewModel.uiState.value.selectedTimeRange)
            assertEquals(6, viewModel.uiState.value.analytics.totalCompletedSessions)
            assertEquals(3, viewModel.uiState.value.analytics.activeWeeklyStreak)
            assertEquals(14_400L, viewModel.uiState.value.analytics.totalDurationSeconds)

            viewModel.onAction(ProgressAction.SelectTimeRange(ProgressTimeRange.ONE_YEAR))
            assertEquals(ProgressTimeRange.ONE_YEAR, viewModel.uiState.value.selectedTimeRange)
            assertEquals(5, viewModel.uiState.value.analytics.totalCompletedSessions)
            assertEquals(12_600L, viewModel.uiState.value.analytics.totalDurationSeconds)

            viewModel.onAction(ProgressAction.SelectTimeRange(ProgressTimeRange.THREE_MONTHS))
            assertEquals(4, viewModel.uiState.value.analytics.totalCompletedSessions)
            assertEquals(9_900L, viewModel.uiState.value.analytics.totalDurationSeconds)

            viewModel.onAction(ProgressAction.SelectTimeRange(ProgressTimeRange.ONE_MONTH))
            assertEquals(3, viewModel.uiState.value.analytics.totalCompletedSessions)
            assertEquals(8_100L, viewModel.uiState.value.analytics.totalDurationSeconds)

            viewModel.onAction(ProgressAction.SelectTimeRange(ProgressTimeRange.ONE_WEEK))
            assertEquals(2, viewModel.uiState.value.analytics.totalCompletedSessions)
            assertEquals(2, viewModel.uiState.value.analytics.activeWeeklyStreak)
            assertEquals(4_500L, viewModel.uiState.value.analytics.totalDurationSeconds)

            // Reactive update when a new completed Session is added
            sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-23T07:45:00Z"),
                durationSeconds = 900L,
                input = SessionDraftInput(title = "Finisher", discipline = Discipline.CONDITIONING),
            )
            advanceUntilIdle()
            assertEquals(3, viewModel.uiState.value.analytics.totalCompletedSessions)
            assertEquals(5_400L, viewModel.uiState.value.analytics.totalDurationSeconds)

            collector.cancel()
        }

    @Test
    fun exerciseProgression_autoSelectsRecentExercise_switchesMetrics_andReactsToUnitAndRangeChanges() =
        runTest(testDispatcher) {
            val bench = exerciseRepository.create(
                ExerciseInput(
                    name = "Bench Press",
                    discipline = Discipline.WEIGHT_LIFTING,
                    trackingMode = TrackingMode.REPS_AND_LOAD,
                ),
            )
            val run = exerciseRepository.create(
                ExerciseInput(
                    name = "5K Run",
                    discipline = Discipline.CARDIO,
                    trackingMode = TrackingMode.DISTANCE_AND_TIME,
                ),
            )

            // Older run (outside 1W)
            sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-01T07:00:00Z"),
                durationSeconds = 1_500L,
                input = SessionDraftInput(
                    title = "Morning Run",
                    discipline = Discipline.CARDIO,
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            exerciseId = run.id,
                            exerciseName = run.name,
                            exerciseDiscipline = run.discipline,
                            trackingMode = run.trackingMode,
                            sets = listOf(
                                SessionSetDraftInput(
                                    distanceKm = 5.0,
                                    durationSeconds = 1_500,
                                    isCompleted = true,
                                ),
                            ),
                        ),
                    ),
                ),
            )
            // Newer bench (inside 1W)
            sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-22T07:00:00Z"),
                durationSeconds = 2_400L,
                input = SessionDraftInput(
                    title = "Heavy Upper",
                    discipline = Discipline.WEIGHT_LIFTING,
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            exerciseId = bench.id,
                            exerciseName = bench.name,
                            exerciseDiscipline = bench.discipline,
                            trackingMode = bench.trackingMode,
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

            val viewModel = createViewModel()
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // Defaults to most recently logged Exercise (Bench Press)
            assertEquals(bench.id, viewModel.uiState.value.selectedExerciseId)
            assertEquals(ProgressionMetric.TOTAL_VOLUME, viewModel.uiState.value.exerciseProgression?.selectedMetric)
            assertEquals(
                "500 kg",
                viewModel.uiState.value.exerciseProgression?.personalRecords
                    ?.first { it.metric == ProgressionMetric.TOTAL_VOLUME }
                    ?.formattedValue,
            )

            // Toggle metric to MAX_LOAD
            viewModel.onAction(ProgressAction.SelectProgressionMetric(ProgressionMetric.MAX_LOAD))
            assertEquals(ProgressionMetric.MAX_LOAD, viewModel.uiState.value.exerciseProgression?.selectedMetric)
            assertEquals("100 kg", viewModel.uiState.value.exerciseProgression?.chartPoints?.single()?.formattedValue)

            // Switch WeightUnit to LB -> updates PRs & chart points immediately
            userPreferencesRepository.updateWeightUnit(WeightUnit.LB)
            advanceUntilIdle()
            assertEquals("220.46 lb", viewModel.uiState.value.exerciseProgression?.chartPoints?.single()?.formattedValue)

            // Open picker, filter by Cardio, select 5K Run -> resets metric to DISTANCE and closes picker
            viewModel.onAction(ProgressAction.OpenExercisePicker)
            assertTrue(viewModel.uiState.value.isExercisePickerOpen)
            viewModel.onAction(ProgressAction.SelectPickerDisciplineFilter(Discipline.CARDIO))
            assertEquals(Discipline.CARDIO, viewModel.uiState.value.pickerDisciplineFilter)
            viewModel.onAction(ProgressAction.SelectExercise(run.id))
            assertFalse(viewModel.uiState.value.isExercisePickerOpen)
            assertEquals(run.id, viewModel.uiState.value.selectedExerciseId)
            assertEquals(ProgressionMetric.DISTANCE, viewModel.uiState.value.exerciseProgression?.selectedMetric)

            // Toggle to PACE and switch DistanceUnit to MI
            viewModel.onAction(ProgressAction.SelectProgressionMetric(ProgressionMetric.PACE))
            userPreferencesRepository.updateDistanceUnit(DistanceUnit.MI)
            advanceUntilIdle()
            assertEquals("08:03 /mi", viewModel.uiState.value.exerciseProgression?.chartPoints?.single()?.formattedValue)

            // Switch to 1W -> chart empty for Nov 1 run, but all-time PR remains
            viewModel.onAction(ProgressAction.SelectTimeRange(ProgressTimeRange.ONE_WEEK))
            val runOneWeek = viewModel.uiState.value.exerciseProgression!!
            assertTrue(runOneWeek.chartPoints.isEmpty())
            assertTrue(runOneWeek.hasAnyAllTimeHistory)
            assertEquals("08:03 /mi", runOneWeek.personalRecords.first { it.metric == ProgressionMetric.PACE }.formattedValue)

            collector.cancel()
        }

    @Test
    fun bodyweightTrend_filtersTimeRanges_updatesUnits_andSupportsCrudFromProgress() =
        runTest(testDispatcher) {
            bodyweightRepository.create(
                BodyweightEntryInput(
                    recordedAt = Instant.parse("2026-10-15T08:00:00Z"),
                    weightKg = 76.0,
                ),
            )
            bodyweightRepository.create(
                BodyweightEntryInput(
                    recordedAt = Instant.parse("2026-11-20T08:00:00Z"),
                    weightKg = 74.0,
                ),
            )

            val viewModel = createViewModel()
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.bodyweightTrend.chartPoints.size)
            assertEquals("KG", viewModel.uiState.value.bodyweightTrend.unitSuffix)
            assertEquals("-2 kg", viewModel.uiState.value.bodyweightTrend.formattedRangeDelta)

            viewModel.onAction(ProgressAction.SelectTimeRange(ProgressTimeRange.ONE_WEEK))
            assertEquals(1, viewModel.uiState.value.bodyweightTrend.chartPoints.size)
            assertEquals("74 kg", viewModel.uiState.value.bodyweightTrend.latestPoint?.formattedWeight)

            // Switch unit to LB
            userPreferencesRepository.updateWeightUnit(WeightUnit.LB)
            advanceUntilIdle()
            assertEquals("LB", viewModel.uiState.value.bodyweightTrend.unitSuffix)
            assertEquals("163.14 lb", viewModel.uiState.value.bodyweightTrend.latestPoint?.formattedWeight)

            // Log new entry directly from Progress
            viewModel.onAction(ProgressAction.SelectTimeRange(ProgressTimeRange.ALL))
            viewModel.onAction(ProgressAction.Bodyweight(BodyweightCrudAction.OpenCreate))
            viewModel.onAction(ProgressAction.Bodyweight(BodyweightCrudAction.UpdateDate("2026-11-23")))
            viewModel.onAction(ProgressAction.Bodyweight(BodyweightCrudAction.UpdateWeight("162")))
            viewModel.onAction(ProgressAction.Bodyweight(BodyweightCrudAction.Save))
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.bodyweightTrend.chartPoints.size)
            assertEquals("162 lb", viewModel.uiState.value.bodyweightTrend.latestPoint?.formattedWeight)

            // Toggle manage entries and soft-delete the newest entry
            viewModel.onAction(ProgressAction.ToggleManageBodyweight)
            assertTrue(viewModel.uiState.value.isManageBodyweightOpen)
            val newest = viewModel.uiState.value.bodyweight.entries.first().entry
            viewModel.onAction(ProgressAction.Bodyweight(BodyweightCrudAction.RequestDelete(newest)))
            viewModel.onAction(ProgressAction.Bodyweight(BodyweightCrudAction.ConfirmDelete))
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.bodyweightTrend.chartPoints.size)
            collector.cancel()
        }
}
