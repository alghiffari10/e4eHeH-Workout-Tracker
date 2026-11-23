package com.e4eheh.e4ehehworkout.ui.screens.progress

import com.e4eheh.e4ehehworkout.data.local.InMemorySessionDao
import com.e4eheh.e4ehehworkout.data.repository.DefaultSessionRepository
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressTimeRange
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.testing.FakeClock
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val clock = FakeClock(initialInstant = Instant.parse("2026-11-23T08:00:00Z"))
    private val sessionRepository = DefaultSessionRepository(InMemorySessionDao(), clock)

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
        clock = clock,
    )

    @Test
    fun initialState_defaultsToAllWithEmptyAnalytics() = runTest(testDispatcher) {
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
}
