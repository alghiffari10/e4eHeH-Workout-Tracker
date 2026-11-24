package com.e4eheh.e4ehehworkout.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressionMetric
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressExerciseProgressionTest {
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
    fun progressTab_emptyExerciseLibrary_showsExerciseProgressionEmptyState() {
        composeTestRule.onNodeWithTag("nav_tab_progress").performClick()

        composeTestRule.onNodeWithTag("progress_exercise_empty_library")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("NO EXERCISES IN LIBRARY").assertIsDisplayed()
    }

    @Test
    fun progressTab_selectExercisesToggleMetricsInspectChartAndVerifyPrExclusionAcrossAllModes() {
        lateinit var pullUp: Exercise
        lateinit var benchPress: Exercise
        lateinit var lSit: Exercise
        lateinit var fiveKRun: Exercise
        lateinit var emomCircuit: Exercise

        runBlocking {
            pullUp = container.exerciseRepository.create(
                ExerciseInput(
                    name = "Pull-up",
                    discipline = Discipline.CALISTHENICS,
                    trackingMode = TrackingMode.REPS_AND_LOAD,
                ),
            )
            benchPress = container.exerciseRepository.create(
                ExerciseInput(
                    name = "Bench Press",
                    discipline = Discipline.WEIGHT_LIFTING,
                    trackingMode = TrackingMode.REPS_AND_LOAD,
                ),
            )
            lSit = container.exerciseRepository.create(
                ExerciseInput(
                    name = "L-Sit Hold",
                    discipline = Discipline.CALISTHENICS,
                    trackingMode = TrackingMode.HOLD_DURATION,
                ),
            )
            fiveKRun = container.exerciseRepository.create(
                ExerciseInput(
                    name = "5K Run",
                    discipline = Discipline.CARDIO,
                    trackingMode = TrackingMode.DISTANCE_AND_TIME,
                ),
            )
            emomCircuit = container.exerciseRepository.create(
                ExerciseInput(
                    name = "EMOM Circuit",
                    discipline = Discipline.CONDITIONING,
                    trackingMode = TrackingMode.CONDITIONING_ROUNDS,
                ),
            )

            // Pull-up: 3 completed sessions (Nov 1, Nov 10, Nov 22) + huge Warm-up Set that must be ignored
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-01T08:00:00Z"),
                durationSeconds = 1_200L,
                input = singleExerciseDraft("Pull S1", pullUp, listOf(SessionSetDraftInput(reps = 8, loadKg = 0.0, isCompleted = true))),
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-10T08:00:00Z"),
                durationSeconds = 1_200L,
                input = singleExerciseDraft(
                    "Pull S2",
                    pullUp,
                    listOf(
                        SessionSetDraftInput(reps = 12, loadKg = 0.0, isCompleted = true),
                        SessionSetDraftInput(reps = 6, loadKg = 0.0, isCompleted = true),
                        SessionSetDraftInput(reps = 99, loadKg = 0.0, isCompleted = true, isWarmup = true),
                    ),
                ),
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-22T08:00:00Z"),
                durationSeconds = 1_200L,
                input = singleExerciseDraft("Pull S3", pullUp, listOf(SessionSetDraftInput(reps = 10, loadKg = 0.0, isCompleted = true))),
            )

            // Bench Press: 2 sessions (Nov 5, Nov 20)
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-05T08:00:00Z"),
                durationSeconds = 1_800L,
                input = singleExerciseDraft(
                    "Bench S1",
                    benchPress,
                    listOf(SessionSetDraftInput(reps = 5, loadKg = 80.0, isCompleted = true)),
                ),
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-20T08:00:00Z"),
                durationSeconds = 1_800L,
                input = singleExerciseDraft(
                    "Bench S2",
                    benchPress,
                    listOf(
                        SessionSetDraftInput(reps = 5, loadKg = 100.0, isCompleted = true),
                        SessionSetDraftInput(reps = 5, loadKg = 150.0, isCompleted = true, isWarmup = true),
                    ),
                ),
            )

            // L-Sit Hold
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-18T08:00:00Z"),
                durationSeconds = 900L,
                input = singleExerciseDraft(
                    "Skill Session",
                    lSit,
                    listOf(
                        SessionSetDraftInput(durationSeconds = 45, isCompleted = true),
                        SessionSetDraftInput(durationSeconds = 30, isCompleted = true),
                    ),
                ),
            )

            // 5K Run (2 runs: Nov 8 slow/long, Nov 19 fast 5k)
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-08T08:00:00Z"),
                durationSeconds = 2_880L,
                input = singleExerciseDraft(
                    "Long Run",
                    fiveKRun,
                    listOf(SessionSetDraftInput(distanceKm = 8.0, durationSeconds = 2_880, isCompleted = true)),
                ),
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-19T08:00:00Z"),
                durationSeconds = 1_500L,
                input = singleExerciseDraft(
                    "Tempo 5K",
                    fiveKRun,
                    listOf(SessionSetDraftInput(distanceKm = 5.0, durationSeconds = 1_500, isCompleted = true)),
                ),
            )

            // EMOM Circuit (most recent: Nov 23)
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-23T07:00:00Z"),
                durationSeconds = 900L,
                input = singleExerciseDraft(
                    "EMOM Morning",
                    emomCircuit,
                    listOf(SessionSetDraftInput(rounds = 8, durationSeconds = 900, isCompleted = true)),
                ),
            )

            // Excluded IN_PROGRESS draft with huge numbers
            val draft = container.sessionRepository.createLiveBlankSession(
                discipline = Discipline.CALISTHENICS,
                title = "Draft Pull",
            )
            container.sessionRepository.saveDraft(
                draft.id,
                singleExerciseDraft("Draft Pull", pullUp, listOf(SessionSetDraftInput(reps = 200, loadKg = 0.0, isCompleted = true))),
            )

            // Excluded soft-deleted completed Session with huge numbers
            val deleted = container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-23T06:00:00Z"),
                durationSeconds = 600L,
                input = singleExerciseDraft("Deleted Bench", benchPress, listOf(SessionSetDraftInput(reps = 10, loadKg = 300.0, isCompleted = true))),
            )
            container.sessionRepository.softDelete(deleted.id)
        }

        composeTestRule.onNodeWithTag("nav_tab_progress").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("progress_selected_exercise_name", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Default selected is most recent: EMOM Circuit (Conditioning / Rounds)
        composeTestRule.onNodeWithTag("progress_exercise_selector").performScrollTo()
        composeTestRule.onNodeWithTag("progress_selected_exercise_name", useUnmergedTree = true)
            .assertTextEquals("EMOM Circuit")
        composeTestRule.onNodeWithTag("progress_pr_badge_${ProgressionMetric.ROUNDS_COMPLETED.name}")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.ROUNDS_COMPLETED.name}")
            .performScrollTo()
            .assertTextEquals("8 rounds")
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.TOTAL_DURATION.name}")
            .performScrollTo()
            .assertTextEquals("15:00")
        composeTestRule.onNodeWithTag("progress_metric_toggle_${ProgressionMetric.TOTAL_DURATION.name}")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("progress_chart_metric_header")
            .performScrollTo()
            .assertTextEquals("TOTAL DURATION // MM:SS")

        // Select Bodyweight Pull-up, verify PRs (warmups & drafts excluded), toggle metrics, and touch-inspect center point
        selectExerciseInPicker(pullUp.id, Discipline.CALISTHENICS)
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.TOTAL_REPS.name}")
            .performScrollTo()
            .assertTextEquals("18 reps")
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.BEST_SET_REPS.name}")
            .performScrollTo()
            .assertTextEquals("12 reps")
        composeTestRule.onNodeWithTag("progress_chart_y_max").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("progress_chart_y_min").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("progress_chart_x_start").performScrollTo().assertTextEquals("Nov 1")
        composeTestRule.onNodeWithTag("progress_chart_x_end").performScrollTo().assertTextEquals("Nov 22")

        // Tap center of canvas -> inspects middle session (Pull S2 on Nov 10: 18 reps)
        composeTestRule.onNodeWithTag("progress_exercise_chart_canvas")
            .performScrollTo()
            .performTouchInput { click(center) }
        composeTestRule.onNodeWithTag("progress_chart_inspection_readout")
            .performScrollTo()
            .assertTextContains("INSPECT // Nov 10 · 18 reps · Pull S2", substring = true)

        // Horizontal drag left -> inspects first session (Pull S1 on Nov 1: 8 reps), then CLEAR resets readout
        composeTestRule.onNodeWithTag("progress_exercise_chart_canvas")
            .performScrollTo()
            .performTouchInput { swipeLeft() }
        composeTestRule.onNodeWithTag("progress_chart_inspection_readout")
            .performScrollTo()
            .assertTextContains("INSPECT // Nov 1 · 8 reps · Pull S1", substring = true)
        composeTestRule.onNodeWithTag("progress_chart_clear_inspect")
            .performClick()
        composeTestRule.onNodeWithTag("progress_chart_inspection_readout")
            .assertTextContains("LATEST // Nov 22 · 10 reps", substring = true)

        // Tap again then toggle to Best Set Reps -> inspection clears and chart updates
        composeTestRule.onNodeWithTag("progress_exercise_chart_canvas")
            .performScrollTo()
            .performTouchInput { click(center) }
        composeTestRule.onNodeWithTag("progress_metric_toggle_${ProgressionMetric.BEST_SET_REPS.name}")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("progress_chart_metric_header")
            .performScrollTo()
            .assertTextEquals("BEST SET REPS // REPS")
        composeTestRule.onNodeWithTag("progress_chart_inspection_readout")
            .assertTextContains("LATEST // Nov 22 · 10 reps", substring = true)

        // Switch time range to 1W -> Pull S1 & S2 filtered out, only Nov 22 remains, all-time PR remains 18 reps
        composeTestRule.onNodeWithTag("progress_range_1W").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("progress_chart_x_start").performScrollTo().assertTextEquals("Nov 22")
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.TOTAL_REPS.name}")
            .performScrollTo()
            .assertTextEquals("18 reps")
        composeTestRule.onNodeWithTag("progress_range_ALL").performScrollTo().performClick()

        // Select Weighted Bench Press -> verify Total Volume & Max Load PRs
        selectExerciseInPicker(benchPress.id, Discipline.WEIGHT_LIFTING)
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.TOTAL_VOLUME.name}")
            .performScrollTo()
            .assertTextEquals("500 kg")
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.MAX_LOAD.name}")
            .performScrollTo()
            .assertTextEquals("100 kg")

        // Select L-Sit Hold -> verify Longest Hold & Total Hold Time PRs
        selectExerciseInPicker(lSit.id, Discipline.CALISTHENICS)
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.LONGEST_HOLD.name}")
            .performScrollTo()
            .assertTextEquals("00:45")
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.TOTAL_HOLD_TIME.name}")
            .performScrollTo()
            .assertTextEquals("01:15")

        // Select 5K Run -> verify Distance & Fastest Pace PRs
        selectExerciseInPicker(fiveKRun.id, Discipline.CARDIO)
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.DISTANCE.name}")
            .performScrollTo()
            .assertTextEquals("8 km")
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.PACE.name}")
            .performScrollTo()
            .assertTextEquals("05:00 /km")

        // Switch to 1W -> 5K Run has 0 pts in 1W (Nov 8 and Nov 19 are >7 days before Nov 23? Wait: Nov 19 is inside 1W from Nov 23! Let's check Nov 16 cutoff: Nov 19 is inside 1W)
        // Advance clock 10 days -> Dec 3, then toggle range to force empty 1W chart while PRs remain
        clock.advanceBy(java.time.Duration.ofDays(10))
        composeTestRule.onNodeWithTag("progress_range_1W").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("progress_exercise_chart_empty")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.PACE.name}")
            .performScrollTo()
            .assertTextEquals("05:00 /km")
    }

    @Test
    fun progressTab_unitPreferenceSwitch_updatesWeightedAndDistancePrsAndChartHeaders() {
        lateinit var benchPress: Exercise
        lateinit var fiveKRun: Exercise

        runBlocking {
            benchPress = container.exerciseRepository.create(
                ExerciseInput(
                    name = "Bench Press",
                    discipline = Discipline.WEIGHT_LIFTING,
                    trackingMode = TrackingMode.REPS_AND_LOAD,
                ),
            )
            fiveKRun = container.exerciseRepository.create(
                ExerciseInput(
                    name = "5K Run",
                    discipline = Discipline.CARDIO,
                    trackingMode = TrackingMode.DISTANCE_AND_TIME,
                ),
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-20T08:00:00Z"),
                durationSeconds = 1_800L,
                input = singleExerciseDraft(
                    "Heavy Upper",
                    benchPress,
                    listOf(SessionSetDraftInput(reps = 5, loadKg = 100.0, isCompleted = true)),
                ),
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-22T08:00:00Z"),
                durationSeconds = 1_500L,
                input = singleExerciseDraft(
                    "Tempo Run",
                    fiveKRun,
                    listOf(SessionSetDraftInput(distanceKm = 5.0, durationSeconds = 1_500, isCompleted = true)),
                ),
            )
        }

        composeTestRule.onNodeWithTag("nav_tab_progress").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("progress_selected_exercise_name", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Default most recent is 5K Run (km)
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.DISTANCE.name}")
            .performScrollTo()
            .assertTextEquals("5 km")
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.PACE.name}")
            .performScrollTo()
            .assertTextEquals("05:00 /km")
        composeTestRule.onNodeWithTag("progress_chart_metric_header")
            .performScrollTo()
            .assertTextEquals("DISTANCE // KM")

        // Switch units on Profile tab to lb & mi
        composeTestRule.onNodeWithTag("nav_tab_profile").performClick()
        composeTestRule.onNodeWithTag("weight_unit_lb").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("distance_unit_mi").performScrollTo().performClick()

        // Return to Progress tab -> 5K Run now renders in mi & /mi
        composeTestRule.onNodeWithTag("nav_tab_progress").performClick()
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.DISTANCE.name}")
            .performScrollTo()
            .assertTextEquals("3.11 mi")
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.PACE.name}")
            .performScrollTo()
            .assertTextEquals("08:03 /mi")
        composeTestRule.onNodeWithTag("progress_chart_metric_header")
            .performScrollTo()
            .assertTextEquals("DISTANCE // MI")

        // Switch to Bench Press -> renders in lb
        selectExerciseInPicker(benchPress.id, Discipline.WEIGHT_LIFTING)
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.TOTAL_VOLUME.name}")
            .performScrollTo()
            .assertTextEquals("1102.31 lb")
        composeTestRule.onNodeWithTag("progress_pr_value_${ProgressionMetric.MAX_LOAD.name}")
            .performScrollTo()
            .assertTextEquals("220.46 lb")
        composeTestRule.onNodeWithTag("progress_chart_metric_header")
            .performScrollTo()
            .assertTextEquals("TOTAL VOLUME // LB")
    }

    private fun selectExerciseInPicker(exerciseId: String, disciplineFilter: Discipline? = null) {
        composeTestRule.onNodeWithTag("progress_exercise_selector")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("progress_exercise_picker_sheet")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val filterTag = if (disciplineFilter == null) {
            "progress_picker_filter_all"
        } else {
            "progress_picker_filter_${disciplineFilter.name}"
        }
        composeTestRule.onNodeWithTag(filterTag).performScrollTo().performClick()
        composeTestRule.onNodeWithTag("progress_exercise_option_$exerciseId")
            .performScrollTo()
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("progress_exercise_picker_sheet")
                .fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun singleExerciseDraft(
        title: String,
        exercise: Exercise,
        sets: List<SessionSetDraftInput>,
    ): SessionDraftInput = SessionDraftInput(
        title = title,
        discipline = exercise.discipline,
        exercises = listOf(
            SessionExerciseDraftInput(
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                exerciseDiscipline = exercise.discipline,
                trackingMode = exercise.trackingMode,
                sets = sets,
            ),
        ),
    )
}
