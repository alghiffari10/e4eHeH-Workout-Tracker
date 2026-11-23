package com.e4eheh.e4ehehworkout.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressTabTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var clock: FakeClock
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        clock = FakeClock(initialInstant = Instant.parse("2026-11-23T08:00:00Z")) // Monday
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
    fun progressTab_blankSlate_showsEmptyDisciplineStateAndZeroSummaryStats() {
        composeTestRule.onNodeWithTag("nav_tab_progress").performClick()

        composeTestRule.onNodeWithText("// PROGRESS TELEMETRY").assertIsDisplayed()
        listOf("1W", "1M", "3M", "1Y", "ALL").forEach { label ->
            composeTestRule.onNodeWithTag("progress_range_$label").assertIsDisplayed()
        }

        composeTestRule.onNodeWithTag("progress_consistency_total_sessions")
            .performScrollTo()
            .assertTextEquals("00")
        composeTestRule.onNodeWithTag("progress_consistency_weekly_streak")
            .performScrollTo()
            .assertTextEquals("00 WEEKS")

        composeTestRule.onNodeWithTag("progress_discipline_empty_state")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("NO COMPLETED SESSIONS IN RANGE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("progress_discipline_ring_canvas")
            .performScrollTo()
            .assertIsDisplayed()

        Discipline.entries.forEach { discipline ->
            composeTestRule.onNodeWithTag("progress_discipline_bar_${discipline.name}")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag("progress_discipline_count_${discipline.name}")
                .assertTextEquals("0 SESSIONS")
            composeTestRule.onNodeWithTag("progress_discipline_percent_${discipline.name}")
                .assertTextEquals("0%")
        }

        composeTestRule.onNodeWithTag("progress_summary_total_time")
            .performScrollTo()
            .assertTextEquals("00:00")
        composeTestRule.onNodeWithTag("progress_summary_total_sessions")
            .performScrollTo()
            .assertTextEquals("00")
        composeTestRule.onNodeWithTag("progress_bodyweight_trend_slot")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun progressTab_filtersConsistencyDisciplineBreakdownAndSummaryStatsAcrossTimeRanges() {
        runBlocking {
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-23T07:00:00Z"),
                durationSeconds = 1_800L,
                input = SessionDraftInput(title = "Push", discipline = Discipline.CALISTHENICS),
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-20T18:00:00Z"),
                durationSeconds = 2_700L,
                input = SessionDraftInput(title = "Conditioning", discipline = Discipline.CONDITIONING),
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-11T18:00:00Z"),
                durationSeconds = 3_600L,
                input = SessionDraftInput(title = "Pull", discipline = Discipline.CALISTHENICS),
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-10-09T18:00:00Z"),
                durationSeconds = 1_800L,
                input = SessionDraftInput(title = "Run", discipline = Discipline.CARDIO),
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-05-07T18:00:00Z"),
                durationSeconds = 2_700L,
                input = SessionDraftInput(title = "Deadlift", discipline = Discipline.WEIGHT_LIFTING),
            )
            container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2025-07-11T18:00:00Z"),
                durationSeconds = 1_800L,
                input = SessionDraftInput(title = "Old Push", discipline = Discipline.CALISTHENICS),
            )
            // Excluded IN_PROGRESS draft
            container.sessionRepository.createLiveBlankSession(
                discipline = Discipline.WEIGHT_LIFTING,
                title = "Live Draft",
            )
            // Excluded soft-deleted completed Session
            val deleted = container.sessionRepository.createCompletedSession(
                startedAt = Instant.parse("2026-11-22T10:00:00Z"),
                durationSeconds = 5_400L,
                input = SessionDraftInput(title = "Deleted Cardio", discipline = Discipline.CARDIO),
            )
            container.sessionRepository.softDelete(deleted.id)
        }

        composeTestRule.onNodeWithTag("nav_tab_progress").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("06").fetchSemanticsNodes().isNotEmpty()
        }

        // ALL range
        assertMetrics(
            totalSessions = "06",
            weeklyStreak = "03 WEEKS",
            totalTime = "04:00:00",
            calisthenics = "3 SESSIONS" to "50%",
            conditioning = "1 SESSION" to "17%",
            cardio = "1 SESSION" to "17%",
            weightLifting = "1 SESSION" to "17%",
        )

        // 1Y range
        composeTestRule.onNodeWithTag("progress_range_1Y").performScrollTo().performClick()
        assertMetrics(
            totalSessions = "05",
            weeklyStreak = "03 WEEKS",
            totalTime = "03:30:00",
            calisthenics = "2 SESSIONS" to "40%",
            conditioning = "1 SESSION" to "20%",
            cardio = "1 SESSION" to "20%",
            weightLifting = "1 SESSION" to "20%",
        )

        // 3M range
        composeTestRule.onNodeWithTag("progress_range_3M").performScrollTo().performClick()
        assertMetrics(
            totalSessions = "04",
            weeklyStreak = "03 WEEKS",
            totalTime = "02:45:00",
            calisthenics = "2 SESSIONS" to "50%",
            conditioning = "1 SESSION" to "25%",
            cardio = "1 SESSION" to "25%",
            weightLifting = "0 SESSIONS" to "0%",
        )

        // 1M range
        composeTestRule.onNodeWithTag("progress_range_1M").performScrollTo().performClick()
        assertMetrics(
            totalSessions = "03",
            weeklyStreak = "03 WEEKS",
            totalTime = "02:15:00",
            calisthenics = "2 SESSIONS" to "67%",
            conditioning = "1 SESSION" to "33%",
            cardio = "0 SESSIONS" to "0%",
            weightLifting = "0 SESSIONS" to "0%",
        )

        // 1W range
        composeTestRule.onNodeWithTag("progress_range_1W").performScrollTo().performClick()
        assertMetrics(
            totalSessions = "02",
            weeklyStreak = "02 WEEKS",
            totalTime = "01:15:00",
            calisthenics = "1 SESSION" to "50%",
            conditioning = "1 SESSION" to "50%",
            cardio = "0 SESSIONS" to "0%",
            weightLifting = "0 SESSIONS" to "0%",
        )

        // Advance clock 10 days so 1W window has zero Sessions
        clock.advanceBy(Duration.ofDays(10))
        composeTestRule.onNodeWithTag("progress_range_ALL").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("progress_range_1W").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("progress_discipline_empty_state")
            .performScrollTo()
            .assertIsDisplayed()
        assertMetrics(
            totalSessions = "00",
            weeklyStreak = "00 WEEKS",
            totalTime = "00:00",
            calisthenics = "0 SESSIONS" to "0%",
            conditioning = "0 SESSIONS" to "0%",
            cardio = "0 SESSIONS" to "0%",
            weightLifting = "0 SESSIONS" to "0%",
        )
    }

    private fun assertMetrics(
        totalSessions: String,
        weeklyStreak: String,
        totalTime: String,
        calisthenics: Pair<String, String>,
        conditioning: Pair<String, String>,
        cardio: Pair<String, String>,
        weightLifting: Pair<String, String>,
    ) {
        composeTestRule.onNodeWithTag("progress_consistency_total_sessions")
            .performScrollTo()
            .assertTextEquals(totalSessions)
        composeTestRule.onNodeWithTag("progress_consistency_weekly_streak")
            .performScrollTo()
            .assertTextEquals(weeklyStreak)
        composeTestRule.onNodeWithTag("progress_summary_total_sessions")
            .performScrollTo()
            .assertTextEquals(totalSessions)
        composeTestRule.onNodeWithTag("progress_summary_total_time")
            .performScrollTo()
            .assertTextEquals(totalTime)

        val expected = mapOf(
            Discipline.CALISTHENICS to calisthenics,
            Discipline.CONDITIONING to conditioning,
            Discipline.CARDIO to cardio,
            Discipline.WEIGHT_LIFTING to weightLifting,
        )
        expected.forEach { (discipline, counts) ->
            composeTestRule.onNodeWithTag("progress_discipline_count_${discipline.name}")
                .performScrollTo()
                .assertTextEquals(counts.first)
            composeTestRule.onNodeWithTag("progress_discipline_percent_${discipline.name}")
                .performScrollTo()
                .assertTextEquals(counts.second)
        }
    }
}
