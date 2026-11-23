package com.e4eheh.e4ehehworkout.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActiveSessionTest {
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
    fun liveBlankSession_logsAllFourTrackingModes_warmupAndWorkingSets_notes_inlineCreation_autoSave_andIndependentCompletionSnapshot() {
        val cardioRun = runBlocking {
            container.exerciseRepository.create(
                ExerciseInput("5K Run", Discipline.CARDIO, TrackingMode.DISTANCE_AND_TIME)
            )
        }

        // Open Quick Action sheet from center + FAB and start live blank Session
        composeTestRule.onNodeWithTag("nav_fab_add").performClick()
        composeTestRule.onNodeWithTag("quick_action_start_live_session").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodes(hasTestTag("active_session_add_exercise_button") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_tab_home").assertDoesNotExist()
        composeTestRule.onNodeWithTag("active_session_timer").assertIsDisplayed()
        composeTestRule.onNodeWithText("00:00").assertIsDisplayed()

        // Session-level metadata
        composeTestRule.onNodeWithTag("active_session_title_input")
            .performScrollTo()
            .performTextInput("Full Hybrid Session")
        composeTestRule.onNodeWithTag("active_session_discipline_CONDITIONING")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("active_session_notes_input")
            .performScrollTo()
            .performTextInput("Strong energy")

        // Exercise 0: Existing CARDIO Exercise (5K Run) -> 5 km, 24m 30s, checked off
        composeTestRule.onNodeWithTag("active_session_add_exercise_button").performClick()
        composeTestRule.onNodeWithTag("picker_exercise_list")
            .performScrollToNode(hasTestTag("picker_exercise_${cardioRun.id}"))
        composeTestRule.onNodeWithTag("picker_exercise_${cardioRun.id}").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_0").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("session_set_distance_0_0").performScrollTo().performTextInput("5")
        composeTestRule.onNodeWithTag("session_set_duration_min_0_0").performScrollTo().performTextInput("24")
        composeTestRule.onNodeWithTag("session_set_duration_sec_0_0").performScrollTo().performTextInput("30")
        composeTestRule.onNodeWithTag("session_set_check_0_0").performScrollTo().performClick()

        // Exercise 1: Inline REPS_AND_LOAD Exercise (Pull-up) -> Warm-up bodyweight + Working 15kg + deleted Set 3 + notes
        composeTestRule.onNodeWithTag("active_session_add_exercise_button").performClick()
        composeTestRule.onNodeWithTag("picker_inline_create_button").performClick()
        composeTestRule.onNodeWithTag("inline_exercise_name_input")
            .performScrollTo()
            .performTextInput("Pull-up")
        composeTestRule.onNodeWithTag("inline_exercise_discipline_CALISTHENICS")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("inline_exercise_tracking_mode_REPS_AND_LOAD")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("inline_exercise_save_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_1").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("session_exercise_notes_1")
            .performScrollTo()
            .performTextInput("Strict chest to bar")
        composeTestRule.onNodeWithTag("session_set_type_1_0").performScrollTo().performClick()
        composeTestRule.onNode(
            hasText("WARM-UP") and hasAnyAncestor(hasTestTag("session_set_1_0")),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("session_set_reps_1_0").performScrollTo().performTextInput("8")
        composeTestRule.onNodeWithTag("session_set_check_1_0").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("session_exercise_add_set_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("session_set_reps_1_1").performScrollTo().performTextInput("10")
        composeTestRule.onNodeWithTag("session_set_load_1_1").performScrollTo().performTextInput("15")
        composeTestRule.onNodeWithTag("session_set_check_1_1").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("session_exercise_add_set_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("session_set_delete_1_2").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("session_set_1_2").assertDoesNotExist()

        // Exercise 2: Inline HOLD_DURATION Exercise (L-Sit) -> 45s hold
        composeTestRule.onNodeWithTag("active_session_add_exercise_button").performClick()
        composeTestRule.onNodeWithTag("picker_inline_create_button").performClick()
        composeTestRule.onNodeWithTag("inline_exercise_name_input")
            .performScrollTo()
            .performTextInput("L-Sit")
        composeTestRule.onNodeWithTag("inline_exercise_discipline_CALISTHENICS")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("inline_exercise_tracking_mode_HOLD_DURATION")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("inline_exercise_save_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_2").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("session_set_hold_2_0").performScrollTo().performTextInput("45")
        composeTestRule.onNodeWithTag("session_set_check_2_0").performScrollTo().performClick()

        // Exercise 3: Inline CONDITIONING_ROUNDS Exercise (EMOM Circuit) -> 10 rounds, 15 reps, 12m 0s
        composeTestRule.onNodeWithTag("active_session_add_exercise_button").performClick()
        composeTestRule.onNodeWithTag("picker_inline_create_button").performClick()
        composeTestRule.onNodeWithTag("inline_exercise_name_input")
            .performScrollTo()
            .performTextInput("EMOM Circuit")
        composeTestRule.onNodeWithTag("inline_exercise_discipline_CONDITIONING")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("inline_exercise_tracking_mode_CONDITIONING_ROUNDS")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("inline_exercise_save_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_3").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("session_set_rounds_3_0").performScrollTo().performTextInput("10")
        composeTestRule.onNodeWithTag("session_set_reps_3_0").performScrollTo().performTextInput("15")
        composeTestRule.onNodeWithTag("session_set_duration_min_3_0").performScrollTo().performTextInput("12")
        composeTestRule.onNodeWithTag("session_set_duration_sec_3_0").performScrollTo().performTextInput("0")
        composeTestRule.onNodeWithTag("session_set_check_3_0").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("session_set_check_3_0").assertIsOn()

        // Wait for mid-Session auto-save to settle in Room
        val draftId = runBlocking { container.sessionRepository.observeActiveSessions().first() }.single().id
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                val active = container.sessionRepository.getById(draftId)
                active?.exercises?.size == 4 &&
                    active.exercises[3].sets.singleOrNull()?.isCompleted == true
            }
        }

        val midDraft = runBlocking { container.sessionRepository.observeActiveSessions().first() }.single()
        assertEquals(SessionStatus.IN_PROGRESS, midDraft.status)
        assertNull(midDraft.completedAt)
        assertEquals(SyncStatus.PENDING, midDraft.syncStatus)
        assertEquals("Full Hybrid Session", midDraft.title)
        assertEquals(Discipline.CONDITIONING, midDraft.discipline)
        assertEquals("Strong energy", midDraft.notes)
        assertEquals(5.0, midDraft.exercises[0].sets.single().distanceKm!!, 1e-6)
        assertEquals(1470, midDraft.exercises[0].sets.single().durationSeconds)
        assertEquals("Strict chest to bar", midDraft.exercises[1].notes)
        assertTrue(midDraft.exercises[1].sets[0].isWarmup)
        assertEquals(0.0, midDraft.exercises[1].sets[0].loadKg!!, 1e-6)
        assertTrue(midDraft.exercises[1].sets[0].isBodyweight)
        assertFalse(midDraft.exercises[1].sets[1].isWarmup)
        assertEquals(15.0, midDraft.exercises[1].sets[1].loadKg!!, 1e-6)
        assertEquals(45, midDraft.exercises[2].sets.single().durationSeconds)
        assertEquals(10, midDraft.exercises[3].sets.single().rounds)
        assertEquals(15, midDraft.exercises[3].sets.single().reps)
        assertEquals(720, midDraft.exercises[3].sets.single().durationSeconds)

        val midWithDeleted = runBlocking {
            container.sessionRepository.getById(midDraft.id, includeDeleted = true)!!
        }
        assertEquals(1, midWithDeleted.exercises[1].sets.count { it.deletedAt != null })

        // Advance workout clock and finish Session
        clock.advanceBy(Duration.ofMinutes(35).plusSeconds(20))
        composeTestRule.onNodeWithTag("active_session_finish_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("nav_tab_home").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()

        val completed = runBlocking { container.sessionRepository.getById(midDraft.id) }!!
        assertEquals(SessionStatus.COMPLETED, completed.status)
        assertEquals(clock.instant(), completed.completedAt)
        assertEquals(2120L, completed.durationSeconds)
        assertEquals(SyncStatus.PENDING, completed.syncStatus)

        val libraryExercises = runBlocking { container.exerciseRepository.observeActiveExercises().first() }
        assertEquals(4, libraryExercises.size)
        val pullupExercise = libraryExercises.single { it.name == "Pull-up" }

        // Soft-deleting the library Exercise never mutates the completed Session snapshot
        runBlocking { container.exerciseRepository.softDelete(pullupExercise.id) }
        val snapshotAfterDelete = runBlocking { container.sessionRepository.getById(midDraft.id) }!!
        assertEquals("Pull-up", snapshotAfterDelete.exercises[1].exerciseName)
        assertEquals(Discipline.CALISTHENICS, snapshotAfterDelete.exercises[1].exerciseDiscipline)
        assertEquals(TrackingMode.REPS_AND_LOAD, snapshotAfterDelete.exercises[1].trackingMode)
    }

    @Test
    fun liveBlankSession_imperialUnits_displaysPreferredLabelsAndPersistsCanonicalMetricValues() {
        val (weightedDip, tempoRun) = runBlocking {
            container.userPreferencesRepository.updateWeightUnit(WeightUnit.LB)
            container.userPreferencesRepository.updateDistanceUnit(DistanceUnit.MI)
            val dip = container.exerciseRepository.create(
                ExerciseInput("Weighted Dip", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val run = container.exerciseRepository.create(
                ExerciseInput("Track Tempo", Discipline.CARDIO, TrackingMode.DISTANCE_AND_TIME)
            )
            dip to run
        }

        composeTestRule.onNodeWithTag("nav_fab_add").performClick()
        composeTestRule.onNodeWithTag("quick_action_start_live_session").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodes(hasTestTag("active_session_add_exercise_button") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag("active_session_add_exercise_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("picker_exercise_${weightedDip.id}").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("picker_exercise_${weightedDip.id}").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_0").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("active_session_add_exercise_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("picker_exercise_${tempoRun.id}").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("picker_exercise_${tempoRun.id}").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_1").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Load (lb)").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Dist (mi)").performScrollTo().assertIsDisplayed()

        // Set 0: 45 lb; Set 1: 0 (bodyweight)
        composeTestRule.onNodeWithTag("session_set_reps_0_0").performScrollTo().performTextInput("8")
        composeTestRule.onNodeWithTag("session_set_load_0_0").performScrollTo().performTextInput("45")
        composeTestRule.onNodeWithTag("session_exercise_add_set_0").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("session_set_reps_0_1").performScrollTo().performTextInput("12")
        composeTestRule.onNodeWithTag("session_set_load_0_1").performScrollTo().performTextInput("0")

        // Cardio: 3.11 mi in 25m 0s
        composeTestRule.onNodeWithTag("session_set_distance_1_0").performScrollTo().performTextInput("3.11")
        composeTestRule.onNodeWithTag("session_set_duration_min_1_0").performScrollTo().performTextInput("25")
        composeTestRule.onNodeWithTag("session_set_duration_sec_1_0").performScrollTo().performTextInput("0")

        clock.advanceBy(Duration.ofMinutes(25))
        composeTestRule.onNodeWithTag("active_session_finish_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("nav_tab_home").fetchSemanticsNodes().isNotEmpty()
        }

        val completed = runBlocking { container.sessionRepository.observeActiveSessions().first() }.single()
        assertNotNull(completed.completedAt)
        assertEquals(SessionStatus.COMPLETED, completed.status)

        val dipSets = completed.exercises[0].sets
        assertEquals(20.41165665, dipSets[0].loadKg!!, 1e-4)
        assertEquals(0.0, dipSets[1].loadKg!!, 1e-6)
        assertTrue(dipSets[1].isBodyweight)

        val runSet = completed.exercises[1].sets.single()
        assertEquals(5.00505984, runSet.distanceKm!!, 1e-4)
        assertEquals(1500, runSet.durationSeconds)
    }
}
