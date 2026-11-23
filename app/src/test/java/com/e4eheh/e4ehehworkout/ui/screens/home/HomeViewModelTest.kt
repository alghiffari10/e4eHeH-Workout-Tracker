package com.e4eheh.e4ehehworkout.ui.screens.home

import com.e4eheh.e4ehehworkout.data.local.InMemoryExerciseDao
import com.e4eheh.e4ehehworkout.data.local.InMemorySessionDao
import com.e4eheh.e4ehehworkout.data.repository.DefaultExerciseRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultSessionRepository
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.repository.SessionRepository
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import java.util.UUID
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val clock = FakeClock()
    private val exerciseRepository = DefaultExerciseRepository(InMemoryExerciseDao(), clock)
    private val sessionRepository = DefaultSessionRepository(InMemorySessionDao(), clock)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        sessions: SessionRepository = sessionRepository,
    ): HomeViewModel = HomeViewModel(
        exerciseRepository = exerciseRepository,
        sessionRepository = sessions,
        clock = clock,
    )

    @Test
    fun blankAndCompletedOnlySessions_hideActiveBanner_andCountActiveExercises() =
        runTest(testDispatcher) {
            exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val completed = sessionRepository.createLiveBlankSession(
                discipline = Discipline.CALISTHENICS,
                title = "Morning Push",
            )
            sessionRepository.completeSession(
                completed.id,
                SessionDraftInput(title = "Morning Push", discipline = Discipline.CALISTHENICS),
            )

            val viewModel = createViewModel()
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.exerciseCount)
            assertNull(viewModel.uiState.value.activeSessionBanner)
            assertNull(viewModel.uiState.value.pendingDiscardSession)

            collector.cancel()
        }

    @Test
    fun inProgressSession_exposesBannerWithFallbackTitleCountsElapsedAndNewestSelection() =
        runTest(testDispatcher) {
            val older = sessionRepository.createLiveBlankSession(
                discipline = Discipline.CARDIO,
                title = "Older Run",
            )
            clock.advanceBy(Duration.ofMinutes(5))
            val newer = sessionRepository.createLiveBlankSession(
                discipline = Discipline.CONDITIONING,
                title = "",
            )
            sessionRepository.saveDraft(
                id = newer.id,
                input = SessionDraftInput(
                    title = "   ",
                    discipline = Discipline.CONDITIONING,
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            id = UUID.randomUUID().toString(),
                            exerciseId = "ex-1",
                            exerciseName = "Kettlebell Swing",
                            exerciseDiscipline = Discipline.CONDITIONING,
                            trackingMode = TrackingMode.CONDITIONING_ROUNDS,
                            sets = listOf(
                                SessionSetDraftInput(rounds = 5, isCompleted = true),
                                SessionSetDraftInput(rounds = 5, isCompleted = false),
                            ),
                        ),
                    ),
                ),
            )
            clock.advanceBy(Duration.ofSeconds(42))

            val viewModel = createViewModel()
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val banner = viewModel.uiState.value.activeSessionBanner
            assertNotNull(banner)
            assertEquals(newer.id, banner!!.sessionId)
            assertEquals("Conditioning Session", banner.displayTitle)
            assertEquals(Discipline.CONDITIONING, banner.discipline)
            assertEquals(1, banner.exerciseCount)
            assertEquals(1, banner.completedSetCount)
            assertEquals(2, banner.totalSetCount)
            assertEquals(42L, banner.elapsedSeconds)

            // RefreshElapsed updates duration deterministically from Clock
            clock.advanceBy(Duration.ofSeconds(18))
            viewModel.onAction(HomeAction.RefreshElapsed)
            assertEquals(60L, viewModel.uiState.value.activeSessionBanner!!.elapsedSeconds)

            // Completing newer falls back to older IN_PROGRESS session
            sessionRepository.completeSession(
                newer.id,
                SessionDraftInput(title = "Done Conditioning", discipline = Discipline.CONDITIONING),
            )
            advanceUntilIdle()
            assertEquals(older.id, viewModel.uiState.value.activeSessionBanner!!.sessionId)

            collector.cancel()
        }

    @Test
    fun discardFlow_confirmSoftDeletesGraphWithSyncMetadata_andDismissKeepsDraft() =
        runTest(testDispatcher) {
            val session = sessionRepository.createLiveBlankSession(
                discipline = Discipline.CALISTHENICS,
                title = "Evening Pull",
            )
            val exId = UUID.randomUUID().toString()
            val setId = UUID.randomUUID().toString()
            sessionRepository.saveDraft(
                id = session.id,
                input = SessionDraftInput(
                    title = "Evening Pull",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            id = exId,
                            exerciseId = "ex-pull",
                            exerciseName = "Pull-up",
                            exerciseDiscipline = Discipline.CALISTHENICS,
                            trackingMode = TrackingMode.REPS_AND_LOAD,
                            sets = listOf(SessionSetDraftInput(id = setId, reps = 10, loadKg = 0.0)),
                        ),
                    ),
                ),
            )

            val viewModel = createViewModel()
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // Request -> Dismiss keeps draft intact
            viewModel.onAction(HomeAction.RequestDiscardActiveSession)
            assertEquals(session.id, viewModel.uiState.value.pendingDiscardSession?.sessionId)

            viewModel.onAction(HomeAction.DismissDiscardDialog)
            assertNull(viewModel.uiState.value.pendingDiscardSession)
            assertNotNull(viewModel.uiState.value.activeSessionBanner)
            assertNotNull(sessionRepository.getById(session.id))

            // Request -> Confirm soft-deletes Session + Exercise + Set
            clock.advanceBy(Duration.ofMinutes(12))
            val deleteInstant = clock.instant()
            viewModel.onAction(HomeAction.RequestDiscardActiveSession)
            viewModel.onAction(HomeAction.ConfirmDiscardActiveSession)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isDiscarding)
            assertNull(viewModel.uiState.value.pendingDiscardSession)
            assertNull(viewModel.uiState.value.activeSessionBanner)
            assertNull(sessionRepository.getById(session.id))

            val tombstone = sessionRepository.getById(session.id, includeDeleted = true)!!
            assertEquals(deleteInstant, tombstone.deletedAt)
            assertEquals(SyncStatus.PENDING, tombstone.syncStatus)
            assertEquals(deleteInstant, tombstone.exercises.single().deletedAt)
            assertEquals(deleteInstant, tombstone.exercises.single().sets.single().deletedAt)

            collector.cancel()
        }

    @Test
    fun discardFailure_resetsIsDiscardingAndPendingDialogWithoutWedgingUi() =
        runTest(testDispatcher) {
            val session = sessionRepository.createLiveBlankSession(title = "Failing Draft")
            val failingRepo = object : SessionRepository by sessionRepository {
                override suspend fun softDelete(id: String): Session {
                    throw IllegalStateException("Disk I/O error")
                }
            }

            val viewModel = createViewModel(sessions = failingRepo)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onAction(HomeAction.RequestDiscardActiveSession)
            viewModel.onAction(HomeAction.ConfirmDiscardActiveSession)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isDiscarding)
            assertNull(viewModel.uiState.value.pendingDiscardSession)
            assertNotNull(viewModel.uiState.value.activeSessionBanner)

            collector.cancel()
        }
}
