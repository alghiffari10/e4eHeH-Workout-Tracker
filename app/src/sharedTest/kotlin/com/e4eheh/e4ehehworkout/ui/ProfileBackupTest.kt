package com.e4eheh.e4ehehworkout.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.e4eheh.e4ehehworkout.di.AppContainer
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntryInput
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.io.File
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileBackupTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeSafRegistryOwner(
        private val onCreateDocument: (suggestedName: String) -> Uri?,
        private val onOpenDocument: (mimeTypes: Array<String>) -> Uri?,
    ) : ActivityResultRegistryOwner {
        override val activityResultRegistry: ActivityResultRegistry =
            object : ActivityResultRegistry() {
                @Suppress("UNCHECKED_CAST")
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    val result: Any? = when (input) {
                        is String -> onCreateDocument(input)
                        is Array<*> -> onOpenDocument(
                            input.filterIsInstance<String>().toTypedArray(),
                        )
                        else -> null
                    }
                    dispatchResult(requestCode, result as O)
                }
            }
    }

    @Test
    fun profileTab_exportsBackupAndRestoresIntoFreshDatabase_excludingSoftDeletedRecords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = FakeClock(Instant.parse("2026-11-23T08:00:00Z"))
        val sourceContainer = AppContainer.createInMemory(context, clock)
        val targetContainer = AppContainer.createInMemory(context, clock)
        val backupFile = File(context.cacheDir, "test-export-backup.json")
        backupFile.delete()

        try {
            val activeExercise = runBlocking {
                val active = sourceContainer.exerciseRepository.create(
                    ExerciseInput(
                        name = "Weighted Dip",
                        discipline = Discipline.CALISTHENICS,
                        trackingMode = TrackingMode.REPS_AND_LOAD,
                    ),
                )
                val deleted = sourceContainer.exerciseRepository.create(
                    ExerciseInput(
                        name = "Deleted Kipping Pull-up",
                        discipline = Discipline.CALISTHENICS,
                        trackingMode = TrackingMode.REPS_AND_LOAD,
                    ),
                )
                sourceContainer.exerciseRepository.softDelete(deleted.id)

                val routine = sourceContainer.routineRepository.create(
                    RoutineInput(
                        name = "Push Vault",
                        discipline = Discipline.CALISTHENICS,
                        exercises = listOf(RoutineExerciseInput(active.id, 3)),
                    ),
                )
                sourceContainer.sessionRepository.createCompletedSession(
                    startedAt = Instant.parse("2026-11-22T09:00:00Z"),
                    durationSeconds = 1800L,
                    routineId = routine.id,
                    input = SessionDraftInput(
                        title = "Push Vault",
                        discipline = Discipline.CALISTHENICS,
                        exercises = listOf(
                            SessionExerciseDraftInput(
                                exerciseId = active.id,
                                exerciseName = active.name,
                                exerciseDiscipline = active.discipline,
                                trackingMode = active.trackingMode,
                                sets = listOf(
                                    SessionSetDraftInput(
                                        reps = 10,
                                        loadKg = 15.0,
                                        isCompleted = true,
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
                sourceContainer.bodyweightRepository.create(
                    BodyweightEntryInput(
                        recordedAt = Instant.parse("2026-11-23T07:00:00Z"),
                        weightKg = 74.5,
                    ),
                )
                active
            }

            var activeContainer = sourceContainer
            var appSession by mutableIntStateOf(0)
            var capturedSuggestedName: String? = null
            val registryOwner = FakeSafRegistryOwner(
                onCreateDocument = { suggestedName ->
                    capturedSuggestedName = suggestedName
                    Uri.fromFile(backupFile)
                },
                onOpenDocument = {
                    Uri.fromFile(backupFile)
                },
            )

            composeTestRule.setContent {
                key(appSession) {
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                        E4eHeHWorkoutApp(appContainer = activeContainer)
                    }
                }
            }

            composeTestRule.onNodeWithTag("nav_tab_profile").performClick()
            composeTestRule.onNodeWithTag("profile_backup_panel").performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithTag("profile_export_backup_button").performScrollTo().performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("BACKUP EXPORTED")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            assertEquals("e4eheh-workout-backup-2026-11-23.json", capturedSuggestedName)
            composeTestRule.onNodeWithTag("profile_backup_feedback_message")
                .performScrollTo()
                .assertTextContains(
                    "Exported 1 Exercise, 1 Routine, 1 Session, 1 Bodyweight Entry (JSON v1).",
                )

            assertTrue(backupFile.exists())
            val exportedText = backupFile.readText()
            assertTrue(exportedText.contains(activeExercise.id))
            assertFalse(exportedText.contains("Deleted Kipping Pull-up"))

            // Switch to fresh target database and import via SAF OpenDocument
            activeContainer = targetContainer
            composeTestRule.runOnIdle {
                appSession += 1
            }

            composeTestRule.onNodeWithTag("nav_tab_profile").performClick()
            composeTestRule.onNodeWithTag("profile_import_backup_button").performScrollTo().performClick()

            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("BACKUP RESTORED")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithTag("profile_backup_feedback_message")
                .performScrollTo()
                .assertTextContains(
                    "Restored 1 Exercise, 1 Routine, 1 Session, 1 Bodyweight Entry (JSON v1).",
                )

            // Verify restored entities in target database + UI
            runBlocking {
                val restoredEx = targetContainer.exerciseRepository.getById(activeExercise.id)!!
                assertEquals(activeExercise.createdAt, restoredEx.createdAt)
                assertEquals(SyncStatus.PENDING, restoredEx.syncStatus)
                assertEquals(1, targetContainer.routineRepository.observeActiveRoutines().first().size)
                val restoredSession = targetContainer.sessionRepository.observeActiveSessions().first().single()
                assertEquals(15.0, restoredSession.exercises.single().sets.single().loadKg!!, 1e-9)
                assertEquals(SyncStatus.PENDING, restoredSession.syncStatus)
                val restoredBw = targetContainer.bodyweightRepository.observeActiveEntries().first().single()
                assertEquals(74.5, restoredBw.weightKg, 1e-9)
                assertEquals(SyncStatus.PENDING, restoredBw.syncStatus)
            }
            composeTestRule.onNodeWithText("74.5 kg").performScrollTo().assertIsDisplayed()
        } finally {
            backupFile.delete()
            sourceContainer.close()
            targetContainer.close()
        }
    }

    @Test
    fun profileTab_invalidOrUnsupportedBackupFile_showsErrorWithoutCorruptingExistingData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = FakeClock(Instant.parse("2026-11-23T08:00:00Z"))
        val container = AppContainer.createInMemory(context, clock)
        val invalidFile = File(context.cacheDir, "test-invalid-backup.json")

        try {
            val existingExercise = runBlocking {
                container.exerciseRepository.create(
                    ExerciseInput(
                        name = "Dead Hang",
                        discipline = Discipline.CALISTHENICS,
                        trackingMode = TrackingMode.HOLD_DURATION,
                    ),
                )
            }

            var currentPayload = "{not valid json"
            val registryOwner = FakeSafRegistryOwner(
                onCreateDocument = { null },
                onOpenDocument = {
                    invalidFile.writeText(currentPayload)
                    Uri.fromFile(invalidFile)
                },
            )

            composeTestRule.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                    E4eHeHWorkoutApp(appContainer = container)
                }
            }

            composeTestRule.onNodeWithTag("nav_tab_profile").performClick()

            // Malformed JSON
            composeTestRule.onNodeWithTag("profile_import_backup_button").performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("RESTORE FAILED")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithTag("profile_backup_feedback_message")
                .performScrollTo()
                .assertTextContains("Backup file is not valid JSON.")

            // Unsupported schemaVersion
            currentPayload = """{"schemaVersion":99,"exportedAt":"2026-11-23T08:00:00Z","exercises":[],"routines":[],"sessions":[],"bodyweightEntries":[]}"""
            composeTestRule.onNodeWithTag("profile_import_backup_button").performScrollTo().performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000) {
                composeTestRule.onAllNodesWithText("Unsupported backup schema version 99", substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            // Dismiss feedback
            composeTestRule.onNodeWithTag("profile_backup_feedback_dismiss").performScrollTo().performClick()
            assertNull(
                composeTestRule.onAllNodesWithText("RESTORE FAILED")
                    .fetchSemanticsNodes()
                    .firstOrNull(),
            )

            // Existing data remains uncorrupted
            val active = runBlocking { container.exerciseRepository.observeActiveExercises().first() }
            assertEquals(listOf(existingExercise.id), active.map { it.id })
        } finally {
            invalidFile.delete()
            container.close()
        }
    }
}
