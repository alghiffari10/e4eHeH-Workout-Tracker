package com.e4eheh.e4ehehworkout.ui.screens.home

import com.e4eheh.e4ehehworkout.data.local.InMemoryExerciseDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryRoutineDao
import com.e4eheh.e4ehehworkout.data.local.InMemorySessionDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryUserPreferencesDao
import com.e4eheh.e4ehehworkout.data.repository.DefaultExerciseRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultRoutineRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultSessionRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultUserPreferencesRepository
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.repository.SessionRepository
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val clock = FakeClock()
    private val exerciseRepository = DefaultExerciseRepository(InMemoryExerciseDao(), clock)
    private val routineRepository = DefaultRoutineRepository(InMemoryRoutineDao(), clock)
    private val sessionRepository = DefaultSessionRepository(InMemorySessionDao(), clock)
    private val userPreferencesRepository =
        DefaultUserPreferencesRepository(InMemoryUserPreferencesDao(), clock)

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
        routineRepository = routineRepository,
        sessionRepository = sessions,
        userPreferencesRepository = userPreferencesRepository,
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

            assertEquals("OPERATOR", viewModel.uiState.value.displayName)
            assertEquals(1, viewModel.uiState.value.exerciseCount)
            assertNull(viewModel.uiState.value.activeSessionBanner)
            assertNull(viewModel.uiState.value.pendingDiscardSession)

            val history = viewModel.uiState.value.completedSessions
            assertEquals(1, history.size)
            assertEquals(completed.id, history.single().sessionId)
            assertEquals("Morning Push", history.single().displayTitle)
            assertEquals(Discipline.CALISTHENICS, history.single().discipline)
            assertEquals("2026-11-23 08:00", history.single().dateTimeLabel)
            assertEquals(0, history.single().exerciseCount)

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
    fun discardFailure_surfacesErrorAndAllowsRetryWithoutWedgingUi() =
        runTest(testDispatcher) {
            sessionRepository.createLiveBlankSession(title = "Failing Draft")
            var shouldFail = true
            val flakyRepo = object : SessionRepository by sessionRepository {
                override suspend fun softDelete(id: String): Session {
                    if (shouldFail) throw IllegalStateException("Disk I/O error")
                    return sessionRepository.softDelete(id)
                }
            }

            val viewModel = createViewModel(sessions = flakyRepo)
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            viewModel.onAction(HomeAction.RequestDiscardActiveSession)
            viewModel.onAction(HomeAction.ConfirmDiscardActiveSession)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isDiscarding)
            assertNull(viewModel.uiState.value.pendingDiscardSession)
            assertNotNull(viewModel.uiState.value.activeSessionBanner)
            assertNotNull(viewModel.uiState.value.discardError)

            // Retrying after recovery clears the error and removes the banner
            shouldFail = false
            viewModel.onAction(HomeAction.RequestDiscardActiveSession)
            assertNull(viewModel.uiState.value.discardError)
            viewModel.onAction(HomeAction.ConfirmDiscardActiveSession)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.discardError)
            assertNull(viewModel.uiState.value.activeSessionBanner)

            collector.cancel()
        }

    @Test
    fun personalizedGreeting_reflectsUpdatedDisplayNameFromPreferences() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals("OPERATOR", viewModel.uiState.value.displayName)

            userPreferencesRepository.updateDisplayName("Nightwing")
            advanceUntilIdle()

            assertEquals("Nightwing", viewModel.uiState.value.displayName)

            collector.cancel()
        }

    @Test
    fun startPicker_ordersSuggestedRoutineFirst_formatsLastVsEstimatedDuration_andTogglesVisibility() =
        runTest(testDispatcher) {
            val pushup = exerciseRepository.create(
                ExerciseInput("Push-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val pullup = exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val push = routineRepository.create(
                RoutineInput(
                    name = "Calisthenics Push",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(RoutineExerciseInput(pushup.id, defaultTargetSets = 4)),
                )
            )
            clock.advanceBy(Duration.ofMinutes(1))
            val pull = routineRepository.create(
                RoutineInput(
                    name = "Calisthenics Pull",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        RoutineExerciseInput(pullup.id, defaultTargetSets = 3),
                        RoutineExerciseInput(pushup.id, defaultTargetSets = 2),
                    ),
                )
            )

            // Completed Push -> Pull becomes suggested next, and Push gets LAST duration while Pull gets EST
            sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-23T07:00:00Z"),
                durationSeconds = 2_700L, // 45m
                input = SessionDraftInput(title = "Calisthenics Push", discipline = Discipline.CALISTHENICS),
                routineId = push.id,
            )

            val viewModel = createViewModel()
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isStartPickerVisible)
            viewModel.onAction(HomeAction.OpenStartPicker)
            assertTrue(viewModel.uiState.value.isStartPickerVisible)

            assertEquals(pull.id, viewModel.uiState.value.suggestedRoutine?.routineId)
            val pickerCards = viewModel.uiState.value.allRoutineCards
            assertEquals(listOf(pull.id, push.id), pickerCards.map { it.routineId })
            assertTrue(pickerCards[0].isSuggestedNext)
            assertEquals("EST ~15m", pickerCards[0].durationLabel) // 5 sets * 180s = 900s = 15m
            assertFalse(pickerCards[1].isSuggestedNext)
            assertEquals("LAST 45m", pickerCards[1].durationLabel)

            viewModel.onAction(HomeAction.DismissStartPicker)
            assertFalse(viewModel.uiState.value.isStartPickerVisible)

            collector.cancel()
        }

    @Test
    fun weeklyCalendar_selectsAndTogglesDays_andClearsSelectionWhenWeekRollsOver() =
        runTest(testDispatcher) {
            val monSession = sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-23T07:00:00Z"),
                durationSeconds = 1_800L,
                input = SessionDraftInput(title = "Monday Push", discipline = Discipline.CALISTHENICS),
            )
            // Tuesday draft should not mark Tuesday
            sessionRepository.createLiveBlankSession(title = "Tuesday Draft")

            val viewModel = createViewModel()
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            val calendar = viewModel.uiState.value.weeklyCalendar
            assertEquals(7, calendar.days.size)
            assertEquals(1, calendar.activeDayCount)
            assertEquals("NOV 23 - NOV 29", calendar.weekRangeLabel)
            assertTrue(calendar.days[0].hasCompletedSessions)
            assertFalse(calendar.days[1].hasCompletedSessions)

            val monday = LocalDate.of(2026, 11, 23)
            val tuesday = LocalDate.of(2026, 11, 24)

            viewModel.onAction(HomeAction.SelectCalendarDate(monday))
            assertEquals(monday, viewModel.uiState.value.selectedCalendarDate)
            assertEquals(listOf(monSession.id), viewModel.uiState.value.selectedDaySessions.map { it.sessionId })

            // Tapping the same day again collapses selection
            viewModel.onAction(HomeAction.SelectCalendarDate(monday))
            assertNull(viewModel.uiState.value.selectedCalendarDate)
            assertTrue(viewModel.uiState.value.selectedDaySessions.isEmpty())

            // Select Tuesday (empty)
            viewModel.onAction(HomeAction.SelectCalendarDate(tuesday))
            assertEquals(tuesday, viewModel.uiState.value.selectedCalendarDate)
            assertTrue(viewModel.uiState.value.selectedDaySessions.isEmpty())

            // Advancing clock into next week clears stale selection
            clock.advanceBy(Duration.ofDays(7))
            viewModel.onAction(HomeAction.RefreshElapsed)
            assertNull(viewModel.uiState.value.selectedCalendarDate)

            collector.cancel()
        }

    @Test
    fun disciplineFilter_filtersCarouselOnly_andRecentActivityCapsAtTenNewestCompletedSessions() =
        runTest(testDispatcher) {
            val ex = exerciseRepository.create(
                ExerciseInput("Kettlebell Swing", Discipline.CONDITIONING, TrackingMode.CONDITIONING_ROUNDS)
            )
            val calisthenicsRoutine = routineRepository.create(
                RoutineInput(
                    name = "Alpha Push",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(RoutineExerciseInput(ex.id, defaultTargetSets = 3)),
                )
            )
            val conditioningRoutine = routineRepository.create(
                RoutineInput(
                    name = "Beta EMOM",
                    discipline = Discipline.CONDITIONING,
                    exercises = listOf(RoutineExerciseInput(ex.id, defaultTargetSets = 2)),
                )
            )

            // Seed 12 completed sessions + 1 IN_PROGRESS + 1 soft-deleted
            val createdIds = mutableListOf<String>()
            repeat(12) { idx ->
                val created = sessionRepository.createCompletedSession(
                    startedAt = Instant.parse("2026-11-10T08:00:00Z").plus(Duration.ofHours(idx.toLong())),
                    durationSeconds = 1_200L,
                    input = SessionDraftInput(
                        title = "Session $idx",
                        discipline = Discipline.CALISTHENICS,
                    ),
                )
                createdIds += created.id
            }
            val deleted = sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-23T07:00:00Z"),
                durationSeconds = 900L,
                input = SessionDraftInput(title = "Deleted", discipline = Discipline.CARDIO),
            )
            sessionRepository.softDelete(deleted.id)
            sessionRepository.createLiveBlankSession(title = "Active Draft")

            val viewModel = createViewModel()
            val collector = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // Recent activity capped at 10 newest completed sessions
            val recent = viewModel.uiState.value.completedSessions
            assertEquals(10, recent.size)
            assertEquals(createdIds.last(), recent.first().sessionId)
            assertEquals(createdIds[2], recent.last().sessionId)

            // Filtering by Discipline affects carousel only, leaving allRoutineCards intact
            assertEquals(2, viewModel.uiState.value.filteredRoutineCards.size)
            viewModel.onAction(HomeAction.SelectDisciplineFilter(Discipline.CONDITIONING))
            assertEquals(Discipline.CONDITIONING, viewModel.uiState.value.selectedDisciplineFilter)
            assertEquals(
                listOf(conditioningRoutine.id),
                viewModel.uiState.value.filteredRoutineCards.map { it.routineId },
            )
            assertEquals(2, viewModel.uiState.value.allRoutineCards.size)

            viewModel.onAction(HomeAction.SelectDisciplineFilter(Discipline.CARDIO))
            assertTrue(viewModel.uiState.value.filteredRoutineCards.isEmpty())

            viewModel.onAction(HomeAction.SelectDisciplineFilter(null))
            assertEquals(
                setOf(calisthenicsRoutine.id, conditioningRoutine.id),
                viewModel.uiState.value.filteredRoutineCards.map { it.routineId }.toSet(),
            )

            collector.cancel()
        }
}
