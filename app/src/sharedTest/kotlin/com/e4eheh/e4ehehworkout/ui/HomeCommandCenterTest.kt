package com.e4eheh.e4ehehworkout.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeCommandCenterTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var clock: FakeClock
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        clock = FakeClock(initialInstant = Instant.parse("2026-11-25T10:00:00Z")) // Wednesday
        container = AppContainer.createInMemory(context, clock)
        composeTestRule.setContent {
            E4eHeHWorkoutApp(appContainer = container)
        }
    }

    @After
    fun tearDown() {
        container.close()
    }

    @Test
    fun blankSlateStates_andPersonalizedGreetingReflectsProfileDisplayName() {
        composeTestRule.onNodeWithTag("home_greeting_text").assertIsDisplayed()
        composeTestRule.onNodeWithText("WELCOME BACK, OPERATOR").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_start_hero_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_start_button").assertIsDisplayed()

        // Monday 2026-11-23 .. Sunday 2026-11-29 calendar strip with no completed indicators
        val weekDays = (23..29).map { day -> LocalDate.of(2026, 11, day) }
        weekDays.forEach { date ->
            composeTestRule.onNodeWithTag("home_calendar_day_$date")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag("home_calendar_indicator_$date", useUnmergedTree = true)
                .assertDoesNotExist()
        }

        composeTestRule.onNodeWithTag("home_routines_empty_state")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_recent_activity_empty_state")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("NO SESSIONS LOGGED YET")
            .performScrollTo()
            .assertIsDisplayed()

        // Update display name in Profile -> reflects on Home greeting
        composeTestRule.onNodeWithTag("nav_tab_profile").performClick()
        composeTestRule.onNodeWithTag("profile_display_name_input")
            .performScrollTo()
            .performTextClearance()
        composeTestRule.onNodeWithTag("profile_display_name_input")
            .performTextInput("Nightwing")
        composeTestRule.onNodeWithTag("profile_save_name_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("CALLSIGN: Nightwing")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag("nav_tab_home").performClick()
        composeTestRule.onNodeWithText("WELCOME BACK, Nightwing")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun startHeroPicker_highlightsSuggestedNextRoutine_launchesRoutineAndBlankSession() {
        val (push, pull) = runBlocking {
            val ex = container.exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val pushRoutine = container.routineRepository.create(
                RoutineInput(
                    name = "Calisthenics Push",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(RoutineExerciseInput(ex.id, defaultTargetSets = 2)),
                )
            )
            val pullRoutine = container.routineRepository.create(
                RoutineInput(
                    name = "Calisthenics Pull",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(RoutineExerciseInput(ex.id, defaultTargetSets = 3)),
                )
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-24T09:00:00Z"),
                durationSeconds = 1_800L,
                input = SessionDraftInput(
                    title = "Calisthenics Push",
                    discipline = Discipline.CALISTHENICS,
                ),
                routineId = pushRoutine.id,
            )
            pushRoutine to pullRoutine
        }

        // Open START picker -> Pull is highlighted as SUGGESTED NEXT
        composeTestRule.onNodeWithTag("home_start_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("home_start_picker_sheet")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_start_picker_routine_${pull.id}")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(
            "home_start_picker_suggested_badge_${pull.id}",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag(
            "home_start_picker_suggested_badge_${push.id}",
            useUnmergedTree = true,
        ).assertDoesNotExist()

        // Tap suggested Routine -> launches ActiveSessionScreen for Calisthenics Pull
        composeTestRule.onNodeWithTag("home_start_picker_routine_${pull.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasTestTag("active_session_finish_button") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNode(
            hasTestTag("active_session_title_input") and hasText("Calisthenics Pull"),
        ).assertIsDisplayed()

        // Finish Session to return to Home
        composeTestRule.onNodeWithTag("active_session_finish_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("home_start_button")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Re-open START picker -> launch live blank Session
        composeTestRule.onNodeWithTag("home_start_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("home_start_picker_blank_session")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_start_picker_blank_session")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasTestTag("active_session_add_exercise_button") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("active_session_timer").assertIsDisplayed()
    }

    @Test
    fun weeklyCalendarStrip_marksCompletedDays_inspectsSelectedDay_andOpensSessionDetail() {
        val wedSession = runBlocking {
            // Previous week Sunday (excluded from current week strip)
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-22T18:00:00Z"),
                durationSeconds = 1_500L,
                input = SessionDraftInput(title = "Prev Week Run", discipline = Discipline.CARDIO),
            )
            // Monday completed
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-23T07:30:00Z"),
                durationSeconds = 1_800L,
                input = SessionDraftInput(title = "Monday Push", discipline = Discipline.CALISTHENICS),
            )
            // Tuesday IN_PROGRESS draft (must NOT light up Tuesday)
            container.sessionRepository.createLiveBlankSession(
                discipline = Discipline.CONDITIONING,
                title = "Tuesday Draft",
            )
            // Wednesday completed
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-25T08:15:00Z"),
                durationSeconds = 2_400L,
                input = SessionDraftInput(title = "Wednesday Conditioning", discipline = Discipline.CONDITIONING),
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag(
                "home_calendar_indicator_2026-11-23",
                useUnmergedTree = true,
            )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_calendar_day_2026-11-23").performScrollTo()
        composeTestRule.onNodeWithTag("home_calendar_indicator_2026-11-23", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_calendar_indicator_2026-11-25", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_calendar_indicator_2026-11-24", useUnmergedTree = true)
            .assertDoesNotExist()

        // Tap Tuesday -> empty day state
        composeTestRule.onNodeWithTag("home_calendar_day_2026-11-24")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("home_calendar_day_empty")
            .performScrollTo()
            .assertIsDisplayed()

        // Tap Wednesday -> shows Wednesday Conditioning, tap it -> opens Session Detail
        composeTestRule.onNodeWithTag("home_calendar_day_2026-11-25")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("home_calendar_session_${wedSession.id}")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("SESSION DETAIL")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNode(
            hasTestTag("session_detail_title_input") and hasText("Wednesday Conditioning"),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun disciplineFilterPills_routineCarouselDurationAndLaunch_andRecentActivityNavigation() {
        val (calisthenicsRoutine, conditioningRoutine, cardioRoutine, recentSession) = runBlocking {
            val pullup = container.exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val kettlebell = container.exerciseRepository.create(
                ExerciseInput("Kettlebell Swing", Discipline.CONDITIONING, TrackingMode.CONDITIONING_ROUNDS)
            )
            val run = container.exerciseRepository.create(
                ExerciseInput("5K Run", Discipline.CARDIO, TrackingMode.DISTANCE_AND_TIME)
            )
            val cali = container.routineRepository.create(
                RoutineInput(
                    name = "Cali Push",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(RoutineExerciseInput(pullup.id, defaultTargetSets = 4)),
                )
            )
            val cond = container.routineRepository.create(
                RoutineInput(
                    name = "EMOM Assault",
                    discipline = Discipline.CONDITIONING,
                    exercises = listOf(
                        RoutineExerciseInput(kettlebell.id, defaultTargetSets = 3),
                        RoutineExerciseInput(pullup.id, defaultTargetSets = 3),
                    ), // 6 sets * 180s = 18m -> EST ~18m
                )
            )
            val card = container.routineRepository.create(
                RoutineInput(
                    name = "Tempo Cardio",
                    discipline = Discipline.CARDIO,
                    exercises = listOf(RoutineExerciseInput(run.id, defaultTargetSets = 1)),
                )
            )
            val completed = container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-24T17:00:00Z"),
                durationSeconds = 2_700L, // 45m -> LAST 45m on Cali Push
                input = SessionDraftInput(title = "Cali Push Log", discipline = Discipline.CALISTHENICS),
                routineId = cali.id,
            )
            Quad(cali, cond, card, completed)
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("home_routine_carousel")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Verify duration labels (LAST 45m vs EST ~18m) in carousel
        composeTestRule.onNodeWithTag("home_routine_carousel")
            .performScrollTo()
            .performScrollToNode(hasTestTag("home_routine_card_${calisthenicsRoutine.id}"))
        composeTestRule.onNode(
            hasText("LAST 45m") and hasAnyAncestor(hasTestTag("home_routine_card_${calisthenicsRoutine.id}")),
            useUnmergedTree = true,
        ).assertIsDisplayed()

        composeTestRule.onNodeWithTag("home_routine_carousel")
            .performScrollToNode(hasTestTag("home_routine_card_${conditioningRoutine.id}"))
        composeTestRule.onNode(
            hasText("EST ~18m") and hasAnyAncestor(hasTestTag("home_routine_card_${conditioningRoutine.id}")),
            useUnmergedTree = true,
        ).assertIsDisplayed()

        // Filter by Conditioning -> only Conditioning Routine remains
        composeTestRule.onNodeWithTag("home_discipline_filter_CONDITIONING")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("home_routine_card_${conditioningRoutine.id}").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_routine_card_${calisthenicsRoutine.id}").assertDoesNotExist()
        composeTestRule.onNodeWithTag("home_routine_card_${cardioRoutine.id}").assertDoesNotExist()

        // Filter by Weight Lifting -> empty filter state, then clear filter
        composeTestRule.onNodeWithTag("home_discipline_filter_WEIGHT_LIFTING")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("home_routines_filter_empty_state")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_routines_clear_filter_button")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("home_routine_carousel")
            .performScrollToNode(hasTestTag("home_routine_card_${cardioRoutine.id}"))
        composeTestRule.onNodeWithTag("home_routine_card_${cardioRoutine.id}").assertIsDisplayed()

        // Tap Routine card -> launches ActiveSessionScreen, then finish to return Home
        composeTestRule.onNodeWithTag("home_routine_carousel")
            .performScrollToNode(hasTestTag("home_routine_card_${conditioningRoutine.id}"))
        composeTestRule.onNodeWithTag("home_routine_card_${conditioningRoutine.id}").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasTestTag("active_session_finish_button") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNode(
            hasTestTag("active_session_title_input") and hasText("EMOM Assault"),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_session_finish_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("home_completed_session_${recentSession.id}")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Tap completed Session in Recent Activity -> navigates to Session Detail
        composeTestRule.onNodeWithTag("home_completed_session_${recentSession.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("SESSION DETAIL")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNode(
            hasTestTag("session_detail_title_input") and hasText("Cali Push Log"),
        ).performScrollTo().assertIsDisplayed()
    }

    private data class Quad<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )
}
