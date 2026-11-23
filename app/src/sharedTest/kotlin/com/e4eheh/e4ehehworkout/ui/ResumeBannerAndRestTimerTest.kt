package com.e4eheh.e4ehehworkout.ui

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResumeBannerAndRestTimerTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var clock: FakeClock
    private lateinit var container: AppContainer
    private var launchEpoch by mutableIntStateOf(0)
    private val hapticEvents = mutableListOf<HapticFeedbackType>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        clock = FakeClock()
        container = AppContainer.createInMemory(context, clock)
        hapticEvents.clear()
        val recordingHaptic = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                hapticEvents += hapticFeedbackType
            }
        }
        composeTestRule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recordingHaptic) {
                key(launchEpoch) {
                    E4eHeHWorkoutApp(appContainer = container)
                }
            }
        }
    }

    @After
    fun tearDown() {
        container.close()
    }

    @Test
    fun home_inProgressSession_resumesWithAllAutoSavedDataAcrossRelaunch_andHidesAfterFinish() {
        val pullup = runBlocking {
            container.exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
        }

        // Start live Session and log metadata, Exercise notes, Warm-up Set, and Working Set
        composeTestRule.onNodeWithTag("nav_fab_add").performClick()
        composeTestRule.onNodeWithTag("quick_action_start_live_session").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodes(hasTestTag("active_session_add_exercise_button") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag("active_session_title_input")
            .performScrollTo()
            .performTextInput("Evening Pull")
        composeTestRule.onNodeWithTag("active_session_discipline_CONDITIONING")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("active_session_notes_input")
            .performScrollTo()
            .performTextInput("Grip solid")

        composeTestRule.onNodeWithTag("active_session_add_exercise_button").performClick()
        composeTestRule.onNodeWithTag("picker_exercise_list")
            .performScrollToNode(hasTestTag("picker_exercise_${pullup.id}"))
        composeTestRule.onNodeWithTag("picker_exercise_${pullup.id}").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_0").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("session_exercise_notes_0")
            .performScrollTo()
            .performTextInput("Full lockout")
        composeTestRule.onNodeWithTag("session_set_type_0_0").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("session_set_reps_0_0").performScrollTo().performTextInput("6")
        composeTestRule.onNodeWithTag("session_set_check_0_0").performScrollTo().performClick()

        composeTestRule.onNodeWithTag("session_exercise_add_set_0").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("session_set_reps_0_1").performScrollTo().performTextInput("10")
        composeTestRule.onNodeWithTag("session_set_load_0_1").performScrollTo().performTextInput("15")
        composeTestRule.onNodeWithTag("session_set_check_0_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("session_set_check_0_1").assertIsOn()

        val draftId = runBlocking { container.sessionRepository.observeActiveSessions().first() }.single().id
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking {
                val active = container.sessionRepository.getById(draftId)
                active?.title == "Evening Pull" &&
                    active.notes == "Grip solid" &&
                    active.exercises.singleOrNull()?.sets?.size == 2 &&
                    active.exercises.single().sets[1].isCompleted
            }
        }

        // Simulate app relaunch onto Home after 12 minutes
        clock.advanceBy(Duration.ofMinutes(12))
        composeTestRule.runOnIdle { launchEpoch++ }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("home_resume_session_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("RESUME ACTIVE SESSION").assertIsDisplayed()
        composeTestRule.onNodeWithText("Evening Pull").assertIsDisplayed()
        composeTestRule.onNodeWithText("12:00").assertIsDisplayed()

        // Tap Resume and verify every saved detail is restored
        composeTestRule.onNodeWithTag("home_resume_session_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_0").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Evening Pull").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Grip solid").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Full lockout").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("WARM-UP").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("session_set_check_0_0").performScrollTo().assertIsOn()
        composeTestRule.onNodeWithTag("session_set_check_0_1").performScrollTo().assertIsOn()
        composeTestRule.onNodeWithText("15").performScrollTo().assertIsDisplayed()

        // Finish Session and confirm banner disappears from Home
        composeTestRule.onNodeWithTag("active_session_finish_button").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("nav_tab_home").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("home_resume_session_banner").assertDoesNotExist()
        val completed = runBlocking { container.sessionRepository.getById(draftId) }!!
        assertEquals(SessionStatus.COMPLETED, completed.status)
    }

    @Test
    fun home_inProgressSession_discardSoftDeletesDraftAndHidesBanner() {
        val draftId = runBlocking {
            val session = container.sessionRepository.createLiveBlankSession(
                discipline = Discipline.CALISTHENICS,
                title = "Abandoned Draft",
            )
            container.sessionRepository.saveDraft(
                id = session.id,
                input = SessionDraftInput(
                    title = "Abandoned Draft",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            id = UUID.randomUUID().toString(),
                            exerciseId = "ex-dip",
                            exerciseName = "Dip",
                            exerciseDiscipline = Discipline.CALISTHENICS,
                            trackingMode = TrackingMode.REPS_AND_LOAD,
                            sets = listOf(SessionSetDraftInput(reps = 10, loadKg = 0.0)),
                        ),
                    ),
                ),
            )
            session.id
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("home_resume_session_banner").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("RESUME ACTIVE SESSION").assertIsDisplayed()
        composeTestRule.onNodeWithText("Abandoned Draft").assertIsDisplayed()

        // Cancelling discard keeps the draft and banner intact
        composeTestRule.onNodeWithTag("home_discard_session_button").performClick()
        composeTestRule.onNodeWithText("DISCARD ACTIVE SESSION?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_discard_cancel_button").performClick()
        composeTestRule.onNodeWithTag("home_resume_session_banner").assertIsDisplayed()

        // Confirming discard removes the banner and soft-deletes Session + Exercises + Sets
        clock.advanceBy(Duration.ofMinutes(5))
        val discardInstant = clock.instant()
        composeTestRule.onNodeWithTag("home_discard_session_button").performClick()
        composeTestRule.onNodeWithTag("home_discard_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("home_resume_session_banner").fetchSemanticsNodes().isEmpty()
        }
        assertNull(runBlocking { container.sessionRepository.getById(draftId) })
        val tombstone = runBlocking {
            container.sessionRepository.getById(draftId, includeDeleted = true)
        }!!
        assertEquals(discardInstant, tombstone.deletedAt)
        assertEquals(SyncStatus.PENDING, tombstone.syncStatus)
        assertEquals(discardInstant, tombstone.exercises.single().deletedAt)
        assertEquals(discardInstant, tombstone.exercises.single().sets.single().deletedAt)
    }

    @Test
    fun activeSession_checkOffSet_autoStartsConfiguredRestTimer_controlsAndHapticCompletion() {
        val dip = runBlocking {
            container.exerciseRepository.create(
                ExerciseInput("Ring Dip", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
        }

        // Configure Rest Timer to 60s via Profile
        composeTestRule.onNodeWithTag("nav_tab_profile").performClick()
        composeTestRule.onNodeWithTag("rest_timer_preset_60s").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodes(hasTestTag("profile_rest_timer_value") and hasText("60s // 01:00"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Start a live Session and add an Exercise with 2 Sets
        composeTestRule.onNodeWithTag("nav_fab_add").performClick()
        composeTestRule.onNodeWithTag("quick_action_start_live_session").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodes(hasTestTag("active_session_add_exercise_button") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag("active_session_add_exercise_button").performClick()
        composeTestRule.onNodeWithTag("picker_exercise_list")
            .performScrollToNode(hasTestTag("picker_exercise_${dip.id}"))
        composeTestRule.onNodeWithTag("picker_exercise_${dip.id}").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_0").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("session_exercise_add_set_0").performScrollTo().performClick()

        // Rest Timer stays hidden until a Set is checked off
        composeTestRule.onNodeWithTag("rest_timer_panel").assertDoesNotExist()

        // Check off Set 0 -> auto-starts at 01:00
        composeTestRule.onNodeWithTag("session_set_check_0_0").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("rest_timer_panel").assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("rest_timer_remaining") and hasText("01:00"))
            .assertIsDisplayed()

        // +15s -> 01:15, -15s -> 01:00, Skip -> hides with 0 haptic buzzes
        composeTestRule.onNodeWithTag("rest_timer_increment").performClick()
        composeTestRule.onNode(hasTestTag("rest_timer_remaining") and hasText("01:15"))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("rest_timer_decrement").performClick()
        composeTestRule.onNode(hasTestTag("rest_timer_remaining") and hasText("01:00"))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("rest_timer_skip").performClick()
        composeTestRule.onNodeWithTag("rest_timer_panel").assertDoesNotExist()
        assertTrue(hapticEvents.isEmpty())

        // Unchecking Set 0 keeps Rest Timer hidden; checking Set 1 starts 01:00
        composeTestRule.onNodeWithTag("session_set_check_0_0").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("rest_timer_panel").assertDoesNotExist()

        composeTestRule.onNodeWithTag("session_set_check_0_1").performScrollTo().performClick()
        composeTestRule.onNode(hasTestTag("rest_timer_remaining") and hasText("01:00"))
            .assertIsDisplayed()

        // Advance FakeClock 50s -> 00:10, then -15s clamps at 00:00 and triggers 1st haptic buzz
        clock.advanceBy(Duration.ofSeconds(50))
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodes(hasTestTag("rest_timer_remaining") and hasText("00:10"))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag("rest_timer_decrement").performClick()
        composeTestRule.onNodeWithText("REST COMPLETE").assertIsDisplayed()
        composeTestRule.onNode(hasTestTag("rest_timer_remaining") and hasText("00:00"))
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("rest_timer_decrement").assertIsNotEnabled()
        composeTestRule.waitUntil(timeoutMillis = 5_000) { hapticEvents.size == 1 }
        assertEquals(HapticFeedbackType.LongPress, hapticEvents.single())

        // +15s resumes from 00:00 -> 00:15, then advancing 15s triggers 2nd haptic buzz
        composeTestRule.onNodeWithTag("rest_timer_increment").performClick()
        composeTestRule.onNode(hasTestTag("rest_timer_remaining") and hasText("00:15"))
            .assertIsDisplayed()
        clock.advanceBy(Duration.ofSeconds(15))
        composeTestRule.waitUntil(timeoutMillis = 5_000) { hapticEvents.size == 2 }
        composeTestRule.onNodeWithText("REST COMPLETE").assertIsDisplayed()
    }
}
