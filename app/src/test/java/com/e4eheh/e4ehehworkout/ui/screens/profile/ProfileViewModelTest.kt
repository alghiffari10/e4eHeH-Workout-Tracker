package com.e4eheh.e4ehehworkout.ui.screens.profile

import com.e4eheh.e4ehehworkout.data.local.InMemoryUserPreferencesDao
import com.e4eheh.e4ehehworkout.data.repository.DefaultUserPreferencesRepository
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.testing.FakeClock
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val clock = FakeClock()
    private val dao = InMemoryUserPreferencesDao()
    private val repository = DefaultUserPreferencesRepository(dao, clock)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun rapidBackToBackActions_serializeCleanlyWithoutLosingUpdates() = runTest(testDispatcher) {
        val viewModel = ProfileViewModel(repository)
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
        val viewModel = ProfileViewModel(repository)

        viewModel.onAction(ProfileAction.UpdateDisplayNameDraft("  Robin  "))
        viewModel.onAction(ProfileAction.SaveDisplayName)
        advanceUntilIdle()

        assertEquals("Robin", repository.getPreferences().displayName)
    }
}
