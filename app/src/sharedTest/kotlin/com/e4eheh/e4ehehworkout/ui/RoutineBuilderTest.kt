package com.e4eheh.e4ehehworkout.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import java.util.UUID
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
class RoutineBuilderTest {
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
    fun routineBuilder_createsRoutineWithInlineExerciseCreation_mixedDisciplines_andTargetSets() {
        composeTestRule.onNodeWithText("Workout").performClick()
        composeTestRule.onNodeWithText("NO ROUTINES BUILT").assertIsDisplayed()

        composeTestRule.onNodeWithTag("routine_create_first_button").performClick()
        composeTestRule.onNodeWithText("CREATE ROUTINE").assertIsDisplayed()

        // Blank Routine name validation
        composeTestRule.onNodeWithTag("routine_save_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("routine_name_input").performScrollTo()
        composeTestRule.onNodeWithText("Routine name is required.").assertIsDisplayed()

        composeTestRule.onNodeWithTag("routine_name_input").performTextInput("Calisthenics Push")
        composeTestRule.onNodeWithTag("routine_discipline_CALISTHENICS").performScrollTo().performClick()

        // Create first Exercise inline from a completely blank library
        composeTestRule.onNodeWithTag("routine_add_exercise_button").performScrollTo().performClick()
        composeTestRule.onNodeWithText("EXERCISE LIBRARY EMPTY").assertIsDisplayed()
        composeTestRule.onNodeWithTag("picker_inline_create_button").performClick()

        composeTestRule.onNodeWithTag("inline_exercise_save_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("inline_exercise_name_input").performScrollTo()
        composeTestRule.onNodeWithText("Exercise name is required.").assertIsDisplayed()

        composeTestRule.onNodeWithTag("inline_exercise_name_input").performTextInput("Archer Push-up")
        composeTestRule.onNodeWithTag("inline_exercise_discipline_CALISTHENICS")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("inline_exercise_tracking_mode_REPS_AND_LOAD")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("inline_exercise_save_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Archer Push-up").fetchSemanticsNodes().isNotEmpty()
        }

        // Create second Exercise inline from a different Discipline (Conditioning finisher)
        composeTestRule.onNodeWithTag("routine_add_exercise_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("picker_inline_create_button").performClick()
        composeTestRule.onNodeWithTag("inline_exercise_name_input")
            .performScrollTo()
            .performTextInput("EMOM Finisher")
        composeTestRule.onNodeWithTag("inline_exercise_discipline_CONDITIONING")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("inline_exercise_tracking_mode_CONDITIONING_ROUNDS")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag("inline_exercise_save_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("EMOM Finisher").fetchSemanticsNodes().isNotEmpty()
        }

        // Configure target sets to 3 on slot 0 and leave slot 1 OPTIONAL
        repeat(3) {
            composeTestRule.onNodeWithTag("routine_item_sets_inc_0").performScrollTo().performClick()
        }
        composeTestRule.onNodeWithTag("routine_item_sets_value_0").performScrollTo()
        composeTestRule.onNode(
            hasText("3 SETS") and hasAnyAncestor(hasTestTag("routine_builder_item_0")),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("routine_item_sets_value_1").performScrollTo()
        composeTestRule.onNode(
            hasText("OPTIONAL") and hasAnyAncestor(hasTestTag("routine_builder_item_1")),
        ).assertIsDisplayed()

        composeTestRule.onNodeWithTag("routine_save_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Calisthenics Push").fetchSemanticsNodes().isNotEmpty()
        }

        val savedRoutine = runBlocking { container.routineRepository.observeActiveRoutines().first() }.single()
        val cardTag = "routine_card_${savedRoutine.id}"
        composeTestRule.onNode(hasText("Calisthenics Push") and hasAnyAncestor(hasTestTag(cardTag)))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasText("2 EXERCISES") and hasAnyAncestor(hasTestTag(cardTag)))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasText("Archer Push-up") and hasAnyAncestor(hasTestTag(cardTag)))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasText("3 SETS") and hasAnyAncestor(hasTestTag(cardTag)))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasText("EMOM Finisher") and hasAnyAncestor(hasTestTag(cardTag)))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasText("Conditioning") and hasAnyAncestor(hasTestTag(cardTag)))
            .performScrollTo()
            .assertIsDisplayed()

        // Verify both inline-created Exercises persisted to the Exercise Library segment
        composeTestRule.onNodeWithTag("workout_segment_exercises").performClick()
        composeTestRule.onNodeWithText("02 CATALOGED").assertIsDisplayed()
        composeTestRule.onNodeWithText("Archer Push-up").assertIsDisplayed()
        composeTestRule.onNodeWithText("EMOM Finisher").assertIsDisplayed()

        assertNotNull(UUID.fromString(savedRoutine.id))
        assertEquals(Discipline.CALISTHENICS, savedRoutine.discipline)
        assertEquals(SyncStatus.PENDING, savedRoutine.syncStatus)
        assertEquals(listOf(0, 1), savedRoutine.exercises.map { it.sortOrder })
        assertEquals(listOf(3, null), savedRoutine.exercises.map { it.defaultTargetSets })
        assertEquals(listOf(SyncStatus.PENDING, SyncStatus.PENDING), savedRoutine.exercises.map { it.syncStatus })
    }

    @Test
    fun routineBuilder_reordersAndEditsRoutine_preservingIdsAndSoftDeletingRemovedEntries() {
        val (exPullup, exLsit, exFinisher, exRow) = runBlocking {
            val a = container.exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
            )
            val b = container.exerciseRepository.create(
                ExerciseInput("L-Sit", Discipline.CALISTHENICS, TrackingMode.HOLD_DURATION)
            )
            val c = container.exerciseRepository.create(
                ExerciseInput("Burpee Circuit", Discipline.CONDITIONING, TrackingMode.CONDITIONING_ROUNDS)
            )
            val d = container.exerciseRepository.create(
                ExerciseInput("Barbell Row", Discipline.WEIGHT_LIFTING, TrackingMode.REPS_AND_LOAD)
            )
            listOf(a, b, c, d)
        }

        val initialRoutine = runBlocking {
            container.routineRepository.create(
                RoutineInput(
                    name = "Pull Day",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(
                        RoutineExerciseInput(exPullup.id, defaultTargetSets = 4),
                        RoutineExerciseInput(exLsit.id, defaultTargetSets = 3),
                        RoutineExerciseInput(exFinisher.id, defaultTargetSets = null),
                    ),
                )
            )
        }
        val originalPullupEntryId = initialRoutine.exercises[0].id
        val originalLsitEntryId = initialRoutine.exercises[1].id
        val originalFinisherEntryId = initialRoutine.exercises[2].id

        composeTestRule.onNodeWithText("Workout").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Pull Day").fetchSemanticsNodes().isNotEmpty()
        }

        clock.advanceBy(Duration.ofMinutes(25))
        composeTestRule.onNodeWithContentDescription("Edit Pull Day").performClick()
        composeTestRule.onNodeWithText("EDIT ROUTINE").assertIsDisplayed()

        composeTestRule.onNodeWithTag("routine_name_input").performScrollTo().performTextClearance()
        composeTestRule.onNodeWithTag("routine_name_input").performTextInput("Hybrid Pull")
        composeTestRule.onNodeWithTag("routine_discipline_CONDITIONING").performScrollTo().performClick()

        // Move L-Sit (index 1) up to index 0, verify move-down & back-up -> [L-Sit, Pull-up, Burpee Circuit]
        composeTestRule.onNodeWithTag("routine_item_move_up_1").performScrollTo().performClick()
        composeTestRule.onNode(
            hasText("L-Sit") and hasAnyAncestor(hasTestTag("routine_builder_item_0")),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("routine_item_move_down_0").performScrollTo().performClick()
        composeTestRule.onNode(
            hasText("Pull-up") and hasAnyAncestor(hasTestTag("routine_builder_item_0")),
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("routine_item_move_up_1").performScrollTo().performClick()
        composeTestRule.onNode(
            hasText("L-Sit") and hasAnyAncestor(hasTestTag("routine_builder_item_0")),
        ).assertIsDisplayed()

        // Adjust Pull-up (now index 1) from 4 sets to 5 sets
        composeTestRule.onNodeWithTag("routine_item_sets_inc_1").performScrollTo().performClick()
        composeTestRule.onNode(
            hasText("5 SETS") and hasAnyAncestor(hasTestTag("routine_builder_item_1")),
        ).assertIsDisplayed()

        // Remove Burpee Circuit (index 2)
        composeTestRule.onNodeWithTag("routine_item_remove_2").performScrollTo().performClick()

        // Add Barbell Row from picker using Discipline filter
        composeTestRule.onNodeWithTag("routine_add_exercise_button").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("picker_filter_WEIGHT_LIFTING").performClick()
        composeTestRule.onNodeWithTag("picker_exercise_list")
            .performScrollToNode(hasTestTag("picker_exercise_${exRow.id}"))
        composeTestRule.onNodeWithTag("picker_exercise_${exRow.id}").performClick()

        composeTestRule.onNodeWithTag("routine_save_button").performScrollTo().performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Hybrid Pull").fetchSemanticsNodes().isNotEmpty()
        }

        val cardTag = "routine_card_${initialRoutine.id}"
        composeTestRule.onNode(hasText("Hybrid Pull") and hasAnyAncestor(hasTestTag(cardTag)))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasText("3 EXERCISES") and hasAnyAncestor(hasTestTag(cardTag)))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasText("Barbell Row") and hasAnyAncestor(hasTestTag(cardTag)))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNode(hasText("5 SETS") and hasAnyAncestor(hasTestTag(cardTag)))
            .performScrollTo()
            .assertIsDisplayed()

        val activeUpdated = runBlocking { container.routineRepository.getById(initialRoutine.id) }
        assertNotNull(activeUpdated)
        assertEquals(listOf(exLsit.id, exPullup.id, exRow.id), activeUpdated!!.exercises.map { it.exerciseId })
        assertEquals(originalLsitEntryId, activeUpdated.exercises[0].id)
        assertEquals(0, activeUpdated.exercises[0].sortOrder)
        assertEquals(3, activeUpdated.exercises[0].defaultTargetSets)
        assertEquals(originalPullupEntryId, activeUpdated.exercises[1].id)
        assertEquals(1, activeUpdated.exercises[1].sortOrder)
        assertEquals(5, activeUpdated.exercises[1].defaultTargetSets)
        assertEquals(2, activeUpdated.exercises[2].sortOrder)
        assertEquals(SyncStatus.PENDING, activeUpdated.syncStatus)

        val withDeleted = runBlocking {
            container.routineRepository.getById(initialRoutine.id, includeDeleted = true)
        }!!
        val removedEntry = withDeleted.exercises.single { it.id == originalFinisherEntryId }
        assertEquals(clock.instant(), removedEntry.deletedAt)
        assertEquals(SyncStatus.PENDING, removedEntry.syncStatus)
    }

    @Test
    fun routineBuilder_softDeletesRoutineWithConfirmation_andPreservesPendingTombstones() {
        val exercise = runBlocking {
            container.exerciseRepository.create(
                ExerciseInput("Dead Hang", Discipline.CALISTHENICS, TrackingMode.HOLD_DURATION)
            )
        }
        val routine = runBlocking {
            container.routineRepository.create(
                RoutineInput(
                    name = "Grip Block",
                    discipline = Discipline.CALISTHENICS,
                    exercises = listOf(RoutineExerciseInput(exercise.id, defaultTargetSets = 2)),
                )
            )
        }

        composeTestRule.onNodeWithText("Workout").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Grip Block").fetchSemanticsNodes().isNotEmpty()
        }

        clock.advanceBy(Duration.ofMinutes(20))
        composeTestRule.onNodeWithContentDescription("Delete Grip Block").performClick()
        composeTestRule.onNodeWithText("DELETE ROUTINE?").assertIsDisplayed()
        composeTestRule.onNode(hasText("Grip Block") and hasAnyAncestor(isDialog())).assertIsDisplayed()

        composeTestRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("NO ROUTINES BUILT").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Grip Block").assertDoesNotExist()

        assertNull(runBlocking { container.routineRepository.getById(routine.id) })
        val tombstone = runBlocking {
            container.routineRepository.getById(routine.id, includeDeleted = true)
        }
        assertNotNull(tombstone)
        assertEquals(routine.createdAt, tombstone!!.createdAt)
        assertEquals(clock.instant(), tombstone.updatedAt)
        assertEquals(clock.instant(), tombstone.deletedAt)
        assertEquals(SyncStatus.PENDING, tombstone.syncStatus)
        val entryTombstone = tombstone.exercises.single()
        assertEquals(clock.instant(), entryTombstone.deletedAt)
        assertEquals(SyncStatus.PENDING, entryTombstone.syncStatus)
    }
}
