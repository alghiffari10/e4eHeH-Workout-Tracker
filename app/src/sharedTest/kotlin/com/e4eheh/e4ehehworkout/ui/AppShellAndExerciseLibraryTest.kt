package com.e4eheh.e4ehehworkout.ui

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppShellAndExerciseLibraryTest {
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
    fun launch_opensDirectlyToBlankSlateHome_andNavigatesAllFourTabsAndQuickActionSheet() {
        composeTestRule.onNodeWithText("WELCOME BACK, OPERATOR").assertIsDisplayed()
        composeTestRule.onNodeWithText("NO SESSIONS LOGGED YET").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("00 EXERCISES").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_workout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_fab_add").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_progress").assertIsDisplayed()
        composeTestRule.onNodeWithTag("nav_tab_profile").assertIsDisplayed()

        composeTestRule.onNodeWithText("Workout").performClick()
        composeTestRule.onNodeWithText("// ROUTINES").assertIsDisplayed()
        composeTestRule.onNodeWithTag("workout_segment_exercises").performClick()
        composeTestRule.onNodeWithText("// EXERCISE LIBRARY").assertIsDisplayed()

        composeTestRule.onNodeWithText("Progress").performClick()
        composeTestRule.onNodeWithText("// PROGRESS TELEMETRY").assertIsDisplayed()

        composeTestRule.onNodeWithText("Profile").performClick()
        composeTestRule.onNodeWithText("// OPERATOR PROFILE").assertIsDisplayed()

        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.onNodeWithText("WELCOME BACK, OPERATOR").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Quick Action").performClick()
        composeTestRule.onNodeWithText("QUICK ACTION").assertIsDisplayed()
        composeTestRule.onNodeWithText("Start Live Blank Session").assertIsDisplayed()
        composeTestRule.onNodeWithText("Log Backdated Session").assertIsDisplayed()

        composeTestRule.onNodeWithText("Dismiss").performClick()
        composeTestRule.onNodeWithText("QUICK ACTION").assertDoesNotExist()
    }

    @Test
    fun workoutTab_createsAndDisplaysExercisesWithDisciplineTrackingModeAndNotes() {
        composeTestRule.onNodeWithText("Workout").performClick()
        composeTestRule.onNodeWithTag("workout_segment_exercises").performClick()
        composeTestRule.onNodeWithText("NO EXERCISES CATALOGED").assertIsDisplayed()

        composeTestRule.onNodeWithText("ADD FIRST EXERCISE").performClick()
        composeTestRule.onNodeWithText("CREATE EXERCISE").assertIsDisplayed()

        composeTestRule.onNodeWithText("Save Exercise").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Exercise name is required.").assertIsDisplayed()

        composeTestRule.onNodeWithTag("exercise_name_input").performScrollTo().performTextInput("Archer Push-up")
        composeTestRule.onNode(hasText("Calisthenics") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        composeTestRule.onNode(hasText("Reps & Load") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        composeTestRule.onNodeWithTag("exercise_notes_input").performScrollTo().performTextInput("Keep straight arm locked")
        composeTestRule.onNodeWithText("Save Exercise").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Archer Push-up").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Archer Push-up").assertIsDisplayed()
        composeTestRule.onNodeWithText("Calisthenics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reps & Load").assertIsDisplayed()
        composeTestRule.onNodeWithText("Keep straight arm locked").assertIsDisplayed()

        composeTestRule.onNodeWithText("+ NEW EXERCISE").performClick()
        composeTestRule.onNodeWithTag("exercise_name_input").performScrollTo().performTextInput("L-Sit")
        composeTestRule.onNode(hasText("Calisthenics") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        composeTestRule.onNode(hasText("Hold / Duration") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        composeTestRule.onNodeWithText("Save Exercise").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("L-Sit").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("L-Sit").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Hold / Duration").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("02 CATALOGED").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Calisthenics").assertCountEquals(2)

        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.onNodeWithText("02 EXERCISES").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun workoutTab_editsExerciseAndPersistsPendingSyncMetadata() {
        val created = runBlocking {
            container.exerciseRepository.create(
                ExerciseInput(
                    name = "Archer Push-up",
                    discipline = Discipline.CALISTHENICS,
                    trackingMode = TrackingMode.REPS_AND_LOAD,
                    defaultNotes = "Lock trailing arm",
                )
            )
        }

        composeTestRule.onNodeWithText("Workout").performClick()
        composeTestRule.onNodeWithTag("workout_segment_exercises").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Archer Push-up").fetchSemanticsNodes().isNotEmpty()
        }

        clock.advanceBy(Duration.ofMinutes(15))
        composeTestRule.onNodeWithContentDescription("Edit Archer Push-up").performClick()
        composeTestRule.onNodeWithText("EDIT EXERCISE").assertIsDisplayed()

        composeTestRule.onNodeWithTag("exercise_name_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("exercise_name_input").performTextInput("Ring Archer Push-up")
        composeTestRule.onNode(hasText("Conditioning") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        composeTestRule.onNode(hasText("Conditioning / Rounds") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        composeTestRule.onNodeWithTag("exercise_notes_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("exercise_notes_input").performTextInput("10 min EMOM")
        composeTestRule.onNodeWithText("Save Exercise").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Ring Archer Push-up").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Ring Archer Push-up").assertIsDisplayed()
        composeTestRule.onNodeWithText("Conditioning").assertIsDisplayed()
        composeTestRule.onNodeWithText("Conditioning / Rounds").assertIsDisplayed()
        composeTestRule.onNodeWithText("10 min EMOM").assertIsDisplayed()

        val updated = runBlocking { container.exerciseRepository.getById(created.id) }
        assertNotNull(updated)
        assertEquals(created.createdAt, updated!!.createdAt)
        assertEquals(clock.instant(), updated.updatedAt)
        assertEquals(SyncStatus.PENDING, updated.syncStatus)
    }

    @Test
    fun workoutTab_softDeletesExerciseWithConfirmation_andPreservesPendingTombstone() {
        val created = runBlocking {
            container.exerciseRepository.create(
                ExerciseInput(
                    name = "Dead Hang",
                    discipline = Discipline.CALISTHENICS,
                    trackingMode = TrackingMode.HOLD_DURATION,
                )
            )
        }

        composeTestRule.onNodeWithText("Workout").performClick()
        composeTestRule.onNodeWithTag("workout_segment_exercises").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Dead Hang").fetchSemanticsNodes().isNotEmpty()
        }

        clock.advanceBy(Duration.ofMinutes(20))
        composeTestRule.onNodeWithContentDescription("Delete Dead Hang").performClick()
        composeTestRule.onNodeWithText("DELETE EXERCISE?").assertIsDisplayed()
        composeTestRule.onNode(hasText("Dead Hang") and hasAnyAncestor(isDialog())).assertIsDisplayed()

        composeTestRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("NO EXERCISES CATALOGED").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Dead Hang").assertDoesNotExist()

        assertNull(runBlocking { container.exerciseRepository.getById(created.id) })
        val tombstone = runBlocking { container.exerciseRepository.getById(created.id, includeDeleted = true) }
        assertNotNull(tombstone)
        assertEquals(created.createdAt, tombstone!!.createdAt)
        assertEquals(clock.instant(), tombstone.updatedAt)
        assertEquals(clock.instant(), tombstone.deletedAt)
        assertEquals(SyncStatus.PENDING, tombstone.syncStatus)
    }
}
