package com.e4eheh.e4ehehworkout.ui.screens.profile

import com.e4eheh.e4ehehworkout.data.backup.DefaultBackupRepository
import com.e4eheh.e4ehehworkout.data.local.InMemoryBodyweightEntryDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryExerciseDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryRoutineDao
import com.e4eheh.e4ehehworkout.data.local.InMemorySessionDao
import com.e4eheh.e4ehehworkout.data.local.InMemoryUserPreferencesDao
import com.e4eheh.e4ehehworkout.data.repository.DefaultBodyweightRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultExerciseRepository
import com.e4eheh.e4ehehworkout.data.repository.DefaultUserPreferencesRepository
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.testing.FakeClock
import com.e4eheh.e4ehehworkout.ui.components.BodyweightCrudAction
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val clock = FakeClock(initialInstant = Instant.parse("2026-11-23T08:00:00Z"))
    private val dao = InMemoryUserPreferencesDao()
    private val repository = DefaultUserPreferencesRepository(dao, clock)
    private val exerciseDao = InMemoryExerciseDao()
    private val routineDao = InMemoryRoutineDao()
    private val sessionDao = InMemorySessionDao()
    private val bodyweightDao = InMemoryBodyweightEntryDao()
    private val exerciseRepository = DefaultExerciseRepository(exerciseDao, clock)
    private val bodyweightRepository = DefaultBodyweightRepository(bodyweightDao, clock)
    private val backupRepository = DefaultBackupRepository(
        exerciseDao = exerciseDao,
        routineDao = routineDao,
        sessionDao = sessionDao,
        bodyweightEntryDao = bodyweightDao,
        clock = clock,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ProfileViewModel = ProfileViewModel(
        userPreferencesRepository = repository,
        bodyweightRepository = bodyweightRepository,
        backupRepository = backupRepository,
        clock = clock,
    )

    @Test
    fun rapidBackToBackActions_serializeCleanlyWithoutLosingUpdates() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val collectorJob = backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onAction(ProfileAction.UpdateDisplayNameDraft("Oracle"))
        viewModel.onAction(ProfileAction.SaveDisplayName)
        viewModel.onAction(ProfileAction.SelectWeightUnit(WeightUnit.LB))
        viewModel.onAction(ProfileAction.SelectDistanceUnit(DistanceUnit.MI))
        viewModel.onAction(ProfileAction.SelectRestTimer(60))
        viewModel.onAction(ProfileAction.AdjustRestTimer(15))
        advanceUntilIdle()

        val expected = UserPreferences(
            displayName = "Oracle",
            weightUnit = WeightUnit.LB,
            distanceUnit = DistanceUnit.MI,
            defaultRestTimerSeconds = 75,
        )
        assertEquals(expected, repository.getPreferences())
        assertEquals(expected, viewModel.uiState.first().preferences)
        collectorJob.cancel()
    }

    @Test
    fun saveDisplayName_beforeUiStateSubscription_readsLatestDraftOverride() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onAction(ProfileAction.UpdateDisplayNameDraft("  Robin  "))
        viewModel.onAction(ProfileAction.SaveDisplayName)
        advanceUntilIdle()

        assertEquals("Robin", repository.getPreferences().displayName)
    }

    @Test
    fun bodyweightCrud_validates_storesCanonicalKg_andUpdatesDisplayOnUnitSwitch() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            val collectorJob = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // Open create -> defaults to today's date
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.OpenCreate))
            assertEquals("2026-11-23", viewModel.uiState.value.bodyweight.editor?.dateText)

            // Rejects future date and non-positive weight
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.UpdateDate("2026-11-25")))
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.UpdateWeight("0")))
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.Save))
            assertEquals(
                "Date cannot be in the future.",
                viewModel.uiState.value.bodyweight.editor?.dateError,
            )
            assertEquals(
                "Enter a valid bodyweight greater than 0.",
                viewModel.uiState.value.bodyweight.editor?.weightError,
            )

            // Valid entry in kg
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.UpdateDate("2026-11-20")))
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.UpdateWeight("75")))
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.Save))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.bodyweight.editor)
            val created = bodyweightRepository.observeActiveEntries().first().single()
            assertEquals(75.0, created.weightKg, 1e-6)
            assertEquals(SyncStatus.PENDING, created.syncStatus)
            assertEquals("75 kg", viewModel.uiState.value.bodyweight.entries.single().formattedWeight)
            assertEquals("Nov 20", viewModel.uiState.value.bodyweight.entries.single().dateLabel)

            // Switch unit to LB -> formatted display updates, canonical weightKg stays 75.0
            viewModel.onAction(ProfileAction.SelectWeightUnit(WeightUnit.LB))
            advanceUntilIdle()
            assertEquals("165.35 lb", viewModel.uiState.value.bodyweight.entries.single().formattedWeight)
            assertEquals(75.0, bodyweightRepository.getById(created.id)!!.weightKg, 1e-6)

            // Date-only edit while in LB -> preserves exact canonical 75.0 kg without rounding drift
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.OpenEdit(created)))
            assertEquals("165.35", viewModel.uiState.value.bodyweight.editor?.weightText)
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.UpdateDate("2026-11-19")))
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.Save))
            advanceUntilIdle()

            val dateEdited = bodyweightRepository.getById(created.id)!!
            assertEquals(75.0, dateEdited.weightKg, 1e-12)
            assertEquals("Nov 19", viewModel.uiState.value.bodyweight.entries.single().dateLabel)

            // Edit weight in LB -> preserves recordedAt for same date & converts lb -> kg
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.OpenEdit(dateEdited)))
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.UpdateWeight("170")))
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.Save))
            advanceUntilIdle()

            val updated = bodyweightRepository.getById(created.id)!!
            assertEquals(dateEdited.recordedAt, updated.recordedAt)
            assertEquals(77.1107029, updated.weightKg, 1e-5)
            assertEquals("170 lb", viewModel.uiState.value.bodyweight.entries.single().formattedWeight)

            // Soft-delete with confirmation -> removed from UI, tombstone kept
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.RequestDelete(updated)))
            assertEquals(updated.id, viewModel.uiState.value.bodyweight.deletingEntry?.id)
            viewModel.onAction(ProfileAction.Bodyweight(BodyweightCrudAction.ConfirmDelete))
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.bodyweight.deletingEntry)
            assertTrue(viewModel.uiState.value.bodyweight.entries.isEmpty())
            val tombstone = bodyweightRepository.getById(updated.id, includeDeleted = true)
            assertNotNull(tombstone?.deletedAt)
            assertEquals(SyncStatus.PENDING, tombstone?.syncStatus)

            collectorJob.cancel()
        }

    @Test
    fun backupExportAndImport_updatesFeedbackOnSuccess_validationFailure_ioFailure_andDismiss() =
        runTest(testDispatcher) {
            val created = exerciseRepository.create(
                ExerciseInput("Pull-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD),
            )
            val viewModel = createViewModel()
            val collectorJob = backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            assertEquals("e4eheh-workout-backup-2026-11-23.json", viewModel.uiState.value.backup.suggestedFileName)

            // Export success
            var exportedJson = ""
            viewModel.onAction(
                ProfileAction.ExportBackup { json -> exportedJson = json },
            )
            advanceUntilIdle()

            val exportFeedback = viewModel.uiState.value.backup.feedback as? BackupFeedback.Success
            assertNotNull(exportFeedback)
            assertEquals(BackupOperation.EXPORT, exportFeedback!!.operation)
            assertTrue(exportFeedback.message.contains("1 Exercise"))
            assertTrue(exportedJson.contains(created.id))

            // Export I/O failure
            viewModel.onAction(
                ProfileAction.ExportBackup { throw IOException("Disk full") },
            )
            advanceUntilIdle()
            val exportError = viewModel.uiState.value.backup.feedback as? BackupFeedback.Error
            assertNotNull(exportError)
            assertEquals(BackupOperation.EXPORT, exportError!!.operation)
            assertEquals("Disk full", exportError.message)

            // Import invalid JSON
            viewModel.onAction(ProfileAction.ImportBackup { "{invalid" })
            advanceUntilIdle()
            val importInvalid = viewModel.uiState.value.backup.feedback as? BackupFeedback.Error
            assertNotNull(importInvalid)
            assertEquals(BackupOperation.IMPORT, importInvalid!!.operation)

            // Import I/O failure
            viewModel.onAction(ProfileAction.ImportBackup { throw IOException("Permission denied") })
            advanceUntilIdle()
            val importIoError = viewModel.uiState.value.backup.feedback as? BackupFeedback.Error
            assertNotNull(importIoError)
            assertEquals("Permission denied", importIoError!!.message)

            // Import valid JSON
            viewModel.onAction(ProfileAction.ImportBackup { exportedJson })
            advanceUntilIdle()
            val importSuccess = viewModel.uiState.value.backup.feedback as? BackupFeedback.Success
            assertNotNull(importSuccess)
            assertEquals(BackupOperation.IMPORT, importSuccess!!.operation)
            assertTrue(importSuccess.message.contains("1 Exercise"))

            // Dismiss feedback
            viewModel.onAction(ProfileAction.DismissBackupFeedback)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.backup.feedback)

            collectorJob.cancel()
        }
}
