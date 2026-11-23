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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackdatedSessionAndDetailTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var clock: FakeClock
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        clock = FakeClock()
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
    fun backdatedSession_logsPastDateTime_andPersistsCompletedHistoryWithoutLiveTimer() {
        val exercise = runBlocking {
            container.exerciseRepository.create(
                ExerciseInput("Archer Push-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
        }

        // Quick action sheet offers the backdated logger next to the live entry point
        composeTestRule.onNodeWithTag("nav_fab_add").performClick()
        composeTestRule.onNodeWithText("Log Backdated Session").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quick_action_log_backdated_session").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodes(hasTestTag("session_detail_save_button") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // No live timer or Rest Timer is ever presented for a completed-Session log
        composeTestRule.onNodeWithTag("active_session_timer").assertDoesNotExist()
        composeTestRule.onNodeWithTag("rest_timer_panel").assertDoesNotExist()
        composeTestRule.onNodeWithText("BACKDATED SESSION").assertIsDisplayed()

        // Past date/time and explicit duration
        composeTestRule.onNodeWithTag("session_detail_date_input")
            .performScrollTo()
            .performTextReplacement("2026-11-20")
        composeTestRule.onNodeWithTag("session_detail_time_input")
            .performScrollTo()
            .performTextReplacement("18:30")
        composeTestRule.onNodeWithTag("session_detail_duration_min_input")
            .performScrollTo()
            .performTextReplacement("45")
        composeTestRule.onNodeWithTag("session_detail_duration_sec_input")
            .performScrollTo()
            .performTextReplacement("0")

        composeTestRule.onNodeWithTag("session_detail_title_input")
            .performScrollTo()
            .performTextInput("Evening Push")
        composeTestRule.onNodeWithTag("session_detail_notes_input")
            .performScrollTo()
            .performTextInput("Backfilled from paper log")

        // Logged Exercises and Sets are recorded directly as completed history
        composeTestRule.onNodeWithTag("session_detail_add_exercise_button").performClick()
        composeTestRule.onNodeWithTag("picker_exercise_list")
            .performScrollToNode(hasTestTag("picker_exercise_${exercise.id}"))
        composeTestRule.onNodeWithTag("picker_exercise_${exercise.id}").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_0")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("session_set_reps_0_0").performScrollTo().performTextInput("12")
        composeTestRule.onNodeWithTag("session_set_load_0_0").performScrollTo().performTextInput("10")

        composeTestRule.onNodeWithTag("session_detail_save_button").performClick()

        // Saving turns the same editor into Session Detail for the newly recorded Session
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("SESSION DETAIL").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(
            hasTestTag("session_detail_date_input") and hasText("2026-11-20"),
        ).performScrollTo().assertIsDisplayed()

        val saved = runBlocking { container.sessionRepository.observeActiveSessions().first() }
            .single()
        assertEquals(SessionStatus.COMPLETED, saved.status)
        assertEquals("Evening Push", saved.title)
        assertEquals(Discipline.CALISTHENICS, saved.discipline)
        assertEquals(Instant.parse("2026-11-20T18:30:00Z"), saved.startedAt)
        assertEquals(Instant.parse("2026-11-20T19:15:00Z"), saved.completedAt)
        assertEquals(2_700L, saved.durationSeconds)
        assertEquals(SyncStatus.PENDING, saved.syncStatus)
        assertEquals("Backfilled from paper log", saved.notes)
        val savedExercise = saved.exercises.single()
        assertEquals(exercise.id, savedExercise.exerciseId)
        assertEquals("Archer Push-up", savedExercise.exerciseName)
        val savedSet = savedExercise.sets.single()
        assertEquals(12, savedSet.reps)
        assertEquals(10.0, savedSet.loadKg!!, 0.001)
        assertTrue(savedSet.isCompleted)

        // Returning Home surfaces the backdated Session in history and reopens its detail
        composeTestRule.onNodeWithTag("session_editor_back_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("home_completed_session_${saved.id}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_completed_session_${saved.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("SESSION DETAIL").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNode(
            hasText("Archer Push-up") and hasAnyAncestor(hasTestTag("session_exercise_card_0")),
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sessionDetail_editsEveryField_andSoftDeletesWithPendingTombstone() {
        val exercise = runBlocking {
            container.exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
        }
        val completed = runBlocking {
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-18T07:00:00Z"),
                durationSeconds = 1_800L,
                input = SessionDraftInput(
                    title = "Morning Pull",
                    discipline = Discipline.CALISTHENICS,
                    notes = "Old notes",
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            id = UUID.randomUUID().toString(),
                            exerciseId = exercise.id,
                            exerciseName = exercise.name,
                            exerciseDiscipline = Discipline.CALISTHENICS,
                            trackingMode = TrackingMode.REPS_AND_LOAD,
                            sets = listOf(SessionSetDraftInput(reps = 8, loadKg = 0.0)),
                        ),
                    ),
                ),
            )
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("home_completed_session_${completed.id}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_completed_session_${completed.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodes(hasTestTag("session_detail_save_button") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Detail prefills the persisted timing and content
        composeTestRule.onNode(hasTestTag("session_detail_title_input") and hasText("Morning Pull"))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("session_detail_date_input") and hasText("2026-11-18"))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("session_detail_time_input") and hasText("07:00"))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("session_detail_duration_min_input") and hasText("30"))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("session_set_reps_0_0").performScrollTo().assertIsDisplayed()

        // Edit timing, metadata, and Sets
        composeTestRule.onNodeWithTag("session_detail_title_input")
            .performScrollTo()
            .performTextReplacement("Morning Pull Revised")
        composeTestRule.onNodeWithTag("session_detail_date_input")
            .performScrollTo()
            .performTextReplacement("2026-11-19")
        composeTestRule.onNodeWithTag("session_detail_time_input")
            .performScrollTo()
            .performTextReplacement("06:45")
        composeTestRule.onNodeWithTag("session_detail_duration_min_input")
            .performScrollTo()
            .performTextReplacement("40")
        composeTestRule.onNodeWithTag("session_detail_notes_input")
            .performScrollTo()
            .performTextReplacement("Revised notes")
        composeTestRule.onNodeWithTag("session_detail_discipline_CONDITIONING")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("session_exercise_add_set_0").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("session_set_reps_0_1").performScrollTo().performTextInput("6")

        composeTestRule.onNodeWithTag("session_detail_save_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodes(hasTestTag("session_detail_save_button") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val updated = runBlocking { container.sessionRepository.getById(completed.id) }!!
        assertEquals("Morning Pull Revised", updated.title)
        assertEquals(Discipline.CONDITIONING, updated.discipline)
        assertEquals(Instant.parse("2026-11-19T06:45:00Z"), updated.startedAt)
        assertEquals(Instant.parse("2026-11-19T07:25:00Z"), updated.completedAt)
        assertEquals(2_400L, updated.durationSeconds)
        assertEquals("Revised notes", updated.notes)
        assertEquals(SyncStatus.PENDING, updated.syncStatus)
        assertEquals(SessionStatus.COMPLETED, updated.status)
        assertEquals(2, updated.exercises.single().sets.size)

        // Soft-delete from detail returns Home and removes the history entry
        composeTestRule.onNodeWithTag("session_detail_delete_button").performClick()
        composeTestRule.onNodeWithText("DELETE SESSION?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("session_detail_delete_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("nav_tab_home").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_completed_session_${completed.id}").assertDoesNotExist()
        composeTestRule.onNodeWithText("NO SESSIONS LOGGED YET").assertIsDisplayed()

        assertNull(runBlocking { container.sessionRepository.getById(completed.id) })
        val tombstone = runBlocking {
            container.sessionRepository.getById(completed.id, includeDeleted = true)
        }!!
        assertNotNull(tombstone.deletedAt)
        assertEquals(SyncStatus.PENDING, tombstone.syncStatus)
        assertTrue(tombstone.exercises.single().deletedAt != null)
        assertTrue(tombstone.exercises.single().sets.all { it.deletedAt != null })
    }
}
