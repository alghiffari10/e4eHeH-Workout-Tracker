package com.e4eheh.e4ehehworkout.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressionMetric
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntryInput
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BodyweightEntriesAndTrendTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var clock: FakeClock
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        clock = FakeClock(initialInstant = Instant.parse("2026-11-23T08:00:00Z"))
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
    fun profileTab_logEditSoftDeleteBodyweightEntries_persistsCanonicalKgAndTombstones() {
        composeTestRule.onNodeWithTag("nav_tab_profile").performClick()

        composeTestRule.onNodeWithTag("profile_bodyweight_panel")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("profile_bodyweight_empty_state")
            .performScrollTo()
            .assertIsDisplayed()

        // Log first entry (2026-11-20, 76.5 kg)
        composeTestRule.onNodeWithTag("profile_log_bodyweight_button")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("bodyweight_date_input").performTextClearance()
        composeTestRule.onNodeWithTag("bodyweight_date_input").performTextInput("2026-11-20")
        composeTestRule.onNodeWithTag("bodyweight_weight_input").performTextInput("76.5")
        composeTestRule.onNodeWithTag("bodyweight_editor_save_button").performClick()

        // Log second entry (2026-11-23, 75 kg)
        composeTestRule.onNodeWithTag("profile_log_bodyweight_button")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("bodyweight_weight_input").performTextInput("75")
        composeTestRule.onNodeWithTag("bodyweight_editor_save_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { container.bodyweightRepository.observeActiveEntries().first().size == 2 }
        }
        val entries = runBlocking { container.bodyweightRepository.observeActiveEntries().first() }
        val newest = entries[0]
        val older = entries[1]
        assertEquals(75.0, newest.weightKg, 1e-6)
        assertEquals(76.5, older.weightKg, 1e-6)
        assertEquals(SyncStatus.PENDING, newest.syncStatus)

        composeTestRule.onNodeWithTag("bodyweight_entry_weight_${newest.id}")
            .performScrollTo()
            .assertTextEquals("75 kg")
        composeTestRule.onNodeWithTag("bodyweight_entry_weight_${older.id}")
            .performScrollTo()
            .assertTextEquals("76.5 kg")

        // Edit the older entry from 76.5 kg -> 76 kg
        composeTestRule.onNodeWithTag("bodyweight_entry_edit_${older.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("bodyweight_weight_input").performTextClearance()
        composeTestRule.onNodeWithTag("bodyweight_weight_input").performTextInput("76")
        composeTestRule.onNodeWithTag("bodyweight_editor_save_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("76 kg").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(76.0, runBlocking { container.bodyweightRepository.getById(older.id) }!!.weightKg, 1e-6)

        // Soft-delete the older entry -> disappears from UI, tombstone persisted
        composeTestRule.onNodeWithTag("bodyweight_entry_delete_${older.id}")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("bodyweight_delete_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("bodyweight_entry_weight_${older.id}")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        val tombstone = runBlocking {
            container.bodyweightRepository.getById(older.id, includeDeleted = true)
        }
        assertNotNull(tombstone?.deletedAt)
        assertEquals(SyncStatus.PENDING, tombstone?.syncStatus)
        assertEquals(1, runBlocking { container.bodyweightRepository.observeActiveEntries().first().size })
    }

    @Test
    fun progressBodyweightTrend_filtersTimeRanges_inspectsChart_switchesUnits_andStaysSeparateFromExercisePrs() {
        runBlocking {
            val pullUp = container.exerciseRepository.create(
                ExerciseInput(
                    name = "Pull-up",
                    discipline = Discipline.CALISTHENICS,
                    trackingMode = TrackingMode.REPS_AND_LOAD,
                ),
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-21T08:00:00Z"),
                durationSeconds = 1_200L,
                input = SessionDraftInput(
                    title = "Calisthenics Pull",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        SessionExerciseDraftInput(
                            exerciseId = pullUp.id,
                            exerciseName = pullUp.name,
                            exerciseDiscipline = pullUp.discipline,
                            trackingMode = pullUp.trackingMode,
                            sets = listOf(
                                SessionSetDraftInput(reps = 10, loadKg = 0.0, isCompleted = true),
                            ),
                        ),
                    ),
                ),
            )

            container.bodyweightRepository.create(
                BodyweightEntryInput(
                    recordedAt = Instant.parse("2026-10-10T08:00:00Z"),
                    weightKg = 78.0,
                ),
            )
            container.bodyweightRepository.create(
                BodyweightEntryInput(
                    recordedAt = Instant.parse("2026-11-10T08:00:00Z"),
                    weightKg = 76.0,
                ),
            )
            container.bodyweightRepository.create(
                BodyweightEntryInput(
                    recordedAt = Instant.parse("2026-11-20T08:00:00Z"),
                    weightKg = 75.0,
                ),
            )
        }

        composeTestRule.onNodeWithTag("nav_tab_progress").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("progress_bodyweight_chart_canvas")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Verify Summary Stats Bodyweight Trend chart in KG
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_header")
            .performScrollTo()
            .assertTextEquals("BODYWEIGHT TREND // KG")
        composeTestRule.onNodeWithTag("progress_bodyweight_range_delta")
            .performScrollTo()
            .assertTextEquals("DELTA -3 kg")
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_y_max")
            .performScrollTo()
            .assertTextEquals("78.6")
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_y_min")
            .performScrollTo()
            .assertTextEquals("74.4")
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_x_start")
            .performScrollTo()
            .assertTextEquals("Oct 10")
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_x_end")
            .performScrollTo()
            .assertTextEquals("Nov 20")

        // Tap center of Bodyweight chart to inspect middle point, then CLEAR
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_canvas")
            .performScrollTo()
            .performTouchInput { click(center) }
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_readout")
            .performScrollTo()
            .assertTextContains("INSPECT // Nov 10 · 76 kg", substring = true)
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_clear_inspect").performClick()
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_readout")
            .assertTextContains("LATEST // Nov 20 · 75 kg", substring = true)

        // Filter 1M -> Oct 10 drops out, start label becomes Nov 10
        composeTestRule.onNodeWithTag("progress_range_1M").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_x_start")
            .performScrollTo()
            .assertTextEquals("Nov 10")
        composeTestRule.onNodeWithTag("progress_bodyweight_range_delta")
            .performScrollTo()
            .assertTextEquals("DELTA -1 kg")

        // Filter 1W -> only Nov 20 remains
        composeTestRule.onNodeWithTag("progress_range_1W").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_x_start")
            .performScrollTo()
            .assertTextEquals("Nov 20")

        // Log a new Bodyweight Entry directly from Progress (2026-11-23, 74 kg)
        composeTestRule.onNodeWithTag("progress_log_bodyweight_button")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("bodyweight_weight_input").performTextInput("74")
        composeTestRule.onNodeWithTag("bodyweight_editor_save_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("LATEST // Nov 23 · 74 kg")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Switch WeightUnit to LB on Profile -> canonical weightKg stays unchanged while display switches to lb
        composeTestRule.onNodeWithTag("nav_tab_profile").performClick()
        composeTestRule.onNodeWithTag("weight_unit_lb").performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("163.14 lb").fetchSemanticsNodes().isNotEmpty()
        }
        val storedKgValues = runBlocking {
            container.bodyweightRepository.observeActiveEntries().first().map { it.weightKg }
        }
        assertEquals(listOf(74.0, 75.0, 76.0, 78.0), storedKgValues)

        // Return to Progress -> Bodyweight chart updates to LB (axes & readout), Pull-up PR remains untouched
        composeTestRule.onNodeWithTag("nav_tab_progress").performClick()
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_header")
            .performScrollTo()
            .assertTextEquals("BODYWEIGHT TREND // LB")
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_y_max")
            .performScrollTo()
            .assertTextEquals("165.8")
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_y_min")
            .performScrollTo()
            .assertTextEquals("162.6")
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_readout")
            .performScrollTo()
            .assertTextContains("LATEST // Nov 23 · 163.14 lb", substring = true)

        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.TOTAL_REPS.name}")
            .performScrollTo()
            .assertTextEquals("10 reps")

        // Advance clock past 1W -> narrow window shows empty Bodyweight chart state
        clock.advanceBy(Duration.ofDays(10))
        composeTestRule.onNodeWithTag("progress_range_ALL").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("progress_range_1W").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("progress_bodyweight_chart_empty")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
