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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import kotlinx.coroutines.flow.first
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
class RoutineLaunchedSessionTest {
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
    fun launchRoutineSession_withoutStructuralChanges_finishesImmediately_andSnapshotSurvivesRoutineEditsAndDelete() {
        val (pushup, lsit, routine) = runBlocking {
            val a = container.exerciseRepository.create(
                ExerciseInput("Archer Push-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val b = container.exerciseRepository.create(
                ExerciseInput("L-Sit", Discipline.CALISTHENICS, TrackingMode.HOLD_DURATION)
            )
            val rt = container.routineRepository.create(
                RoutineInput(
                    name = "Calisthenics Push",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        RoutineExerciseInput(a.id, defaultTargetSets = 2),
                        RoutineExerciseInput(b.id, defaultTargetSets = 1),
                    ),
                )
            )
            Triple(a, b, rt)
        }

        composeTestRule.onNodeWithText("Workout").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("routine_start_session_${routine.id}")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag("routine_start_session_${routine.id}")
            .performScrollTo()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasTestTag("active_session_finish_button") and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Pre-populated with Routine title, Discipline, ordered Exercises, and target Sets
        composeTestRule.onNode(
            hasTestTag("active_session_title_input") and hasText("Calisthenics Push"),
        ).assertIsDisplayed()
        composeTestRule.onNode(
            hasText("Archer Push-up") and hasAnyAncestor(hasTestTag("session_exercise_card_0")),
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("session_set_0_0").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("session_set_0_1").performScrollTo().assertIsDisplayed()
        composeTestRule.onNode(
            hasText("L-Sit") and hasAnyAncestor(hasTestTag("session_exercise_card_1")),
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("session_set_1_0").performScrollTo().assertIsDisplayed()

        // Log measurements without structural changes
        composeTestRule.onNodeWithTag("session_set_reps_0_0").performScrollTo().performTextInput("10")
        composeTestRule.onNodeWithTag("session_set_reps_0_1").performScrollTo().performTextInput("8")
        composeTestRule.onNodeWithTag("session_set_hold_1_0").performScrollTo().performTextInput("30")

        clock.advanceBy(Duration.ofMinutes(20))
        composeTestRule.onNodeWithTag("active_session_finish_button").performClick()

        // Finishes immediately without showing the Update Routine prompt
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("nav_tab_workout").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("update_routine_prompt_dialog").assertDoesNotExist()

        val completedSession = runBlocking { container.sessionRepository.observeActiveSessions().first() }.single()
        assertEquals(routine.id, completedSession.routineId)
        assertEquals(SessionStatus.COMPLETED, completedSession.status)
        assertEquals("Calisthenics Push", completedSession.title)
        assertEquals(listOf(pushup.id, lsit.id), completedSession.exercises.map { it.exerciseId })
        assertEquals(10, completedSession.exercises[0].sets[0].reps)
        assertEquals(8, completedSession.exercises[0].sets[1].reps)
        assertEquals(30, completedSession.exercises[1].sets[0].durationSeconds)

        // Mutating and deleting the Routine later never alters the logged Session snapshot
        runBlocking {
            container.routineRepository.update(
                routine.id,
                RoutineInput(
                    name = "Completely Different",
                    discipline = Discipline.CARDIO,
                    exercises = listOf(RoutineExerciseInput(lsit.id, defaultTargetSets = 5)),
                ),
            )
            container.routineRepository.softDelete(routine.id)
        }

        val reloadedSession = runBlocking { container.sessionRepository.getById(completedSession.id) }!!
        assertEquals("Calisthenics Push", reloadedSession.title)
        assertEquals(Discipline.CALISTHENICS, reloadedSession.discipline)
        assertEquals(listOf("Archer Push-up", "L-Sit"), reloadedSession.exercises.map { it.exerciseName })
    }

    @Test
    fun midSessionModifications_confirmUpdateRoutine_updatesRoutineWithPendingSync() {
        val (pullup, lsit, dip, finisher, routine) = runBlocking {
            val a = container.exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val b = container.exerciseRepository.create(
                ExerciseInput("L-Sit", Discipline.CALISTHENICS, TrackingMode.HOLD_DURATION)
            )
            val c = container.exerciseRepository.create(
                ExerciseInput("Weighted Dip", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val d = container.exerciseRepository.create(
                ExerciseInput("EMOM Finisher", Discipline.CONDITIONING, TrackingMode.CONDITIONING_ROUNDS)
            )
            val rt = container.routineRepository.create(
                RoutineInput(
                    name = "Full Calisthenics",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        RoutineExerciseInput(a.id, defaultTargetSets = 2),
                        RoutineExerciseInput(b.id, defaultTargetSets = 1),
                    ),
                )
            )
            Tuple5(a, b, c, d, rt)
        }

        composeTestRule.onNodeWithText("Workout").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("routine_start_session_${routine.id}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("routine_start_session_${routine.id}")
            .performScrollTo()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_0").fetchSemanticsNodes().isNotEmpty()
        }

        // Reorder: move Pull-up (0) down -> [L-Sit, Pull-up]
        composeTestRule.onNodeWithTag("session_exercise_move_down_0").performScrollTo().performClick()
        composeTestRule.onNode(
            hasText("L-Sit") and hasAnyAncestor(hasTestTag("session_exercise_card_0")),
        ).performScrollTo().assertIsDisplayed()

        // Swap slot 0 (L-Sit) -> Weighted Dip
        composeTestRule.onNodeWithTag("session_exercise_swap_0").performScrollTo().performClick()
        composeTestRule.onNodeWithText("SWAP EXERCISE").assertIsDisplayed()
        composeTestRule.onNodeWithTag("picker_exercise_list")
            .performScrollToNode(hasTestTag("picker_exercise_${dip.id}"))
        composeTestRule.onNodeWithTag("picker_exercise_${dip.id}").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(
                hasText("Weighted Dip") and hasAnyAncestor(hasTestTag("session_exercise_card_0"))
            ).fetchSemanticsNodes().isNotEmpty()
        }

        // Add Set to Pull-up (slot 1): 2 -> 3 sets
        composeTestRule.onNodeWithTag("session_exercise_add_set_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("session_set_1_2").performScrollTo().assertIsDisplayed()

        // Add EMOM Finisher as slot 2, then remove slot 2? No, keep it added to verify Add Exercise too
        composeTestRule.onNodeWithTag("active_session_add_exercise_button").performClick()
        composeTestRule.onNodeWithTag("picker_exercise_list")
            .performScrollToNode(hasTestTag("picker_exercise_${finisher.id}"))
        composeTestRule.onNodeWithTag("picker_exercise_${finisher.id}").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_2").fetchSemanticsNodes().isNotEmpty()
        }

        clock.advanceBy(Duration.ofMinutes(25))
        composeTestRule.onNodeWithTag("active_session_finish_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("update_routine_prompt_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Update Routine with these changes?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("update_routine_confirm_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("nav_tab_workout").fetchSemanticsNodes().isNotEmpty()
        }

        val updatedRoutine = runBlocking { container.routineRepository.getById(routine.id) }!!
        assertEquals(SyncStatus.PENDING, updatedRoutine.syncStatus)
        assertEquals(
            listOf(dip.id, pullup.id, finisher.id),
            updatedRoutine.exercises.map { it.exerciseId },
        )
        assertEquals(listOf(1, 3, 1), updatedRoutine.exercises.map { it.defaultTargetSets })

        val routineWithDeleted = runBlocking {
            container.routineRepository.getById(routine.id, includeDeleted = true)!!
        }
        val tombstonedLsit = routineWithDeleted.exercises.single { it.exerciseId == lsit.id }
        assertNotNull(tombstonedLsit.deletedAt)
        assertEquals(SyncStatus.PENDING, tombstonedLsit.syncStatus)
    }

    @Test
    fun midSessionModifications_declineUpdateRoutine_completesSessionAndLeavesRoutineUntouched() {
        val (pushup, dip, routine) = runBlocking {
            val a = container.exerciseRepository.create(
                ExerciseInput("Push-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val b = container.exerciseRepository.create(
                ExerciseInput("Dip", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val rt = container.routineRepository.create(
                RoutineInput(
                    name = "Simple Push",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        RoutineExerciseInput(a.id, defaultTargetSets = 2),
                        RoutineExerciseInput(b.id, defaultTargetSets = 2),
                    ),
                )
            )
            Triple(a, b, rt)
        }

        composeTestRule.onNodeWithText("Workout").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("routine_start_session_${routine.id}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("routine_start_session_${routine.id}")
            .performScrollTo()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("session_exercise_card_1").fetchSemanticsNodes().isNotEmpty()
        }

        // Remove second Exercise (Dip) and remove a Set from Push-up
        composeTestRule.onNodeWithTag("session_exercise_remove_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("session_set_delete_0_1").performScrollTo().performClick()

        clock.advanceBy(Duration.ofMinutes(15))
        composeTestRule.onNodeWithTag("active_session_finish_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("update_routine_prompt_dialog").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Update Routine with these changes?").assertIsDisplayed()
        composeTestRule.onNodeWithTag("update_routine_decline_button").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("nav_tab_workout").fetchSemanticsNodes().isNotEmpty()
        }

        // Originating Routine remains untouched
        val storedRoutine = runBlocking { container.routineRepository.getById(routine.id) }!!
        assertEquals(routine, storedRoutine)
        assertEquals(listOf(pushup.id, dip.id), storedRoutine.exercises.map { it.exerciseId })
        assertEquals(listOf(2, 2), storedRoutine.exercises.map { it.defaultTargetSets })

        // Completed Session keeps the mid-Session modifications
        val completedSession = runBlocking { container.sessionRepository.observeActiveSessions().first() }.single()
        assertEquals(SessionStatus.COMPLETED, completedSession.status)
        assertEquals(listOf(pushup.id), completedSession.exercises.map { it.exerciseId })
        assertEquals(1, completedSession.exercises.single().sets.size)
    }

    private data class Tuple5<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
    )
}
