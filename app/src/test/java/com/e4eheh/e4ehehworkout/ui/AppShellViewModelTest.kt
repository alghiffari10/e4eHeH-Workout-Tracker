package com.e4eheh.e4ehehworkout.ui

import com.e4eheh.e4ehehworkout.data.local.InMemorySessionDao
import com.e4eheh.e4ehehworkout.data.repository.DefaultSessionRepository
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.repository.SessionRepository
import com.e4eheh.e4ehehworkout.testing.FakeClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppShellViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val clock = FakeClock()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startLiveBlankSession_publishesPendingNavigation_andConsumeClearsIt() =
        runTest(testDispatcher) {
            val sessionRepository = DefaultSessionRepository(InMemorySessionDao(), clock)
            val viewModel = AppShellViewModel(sessionRepository)

            viewModel.startLiveBlankSession()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isStartingSession)
            val sessionId = state.pendingSessionId
            assertNotNull(sessionId)

            val persisted = sessionRepository.getById(sessionId!!)
            assertNotNull(persisted)
            assertEquals(SessionStatus.IN_PROGRESS, persisted!!.status)

            viewModel.consumePendingSessionNavigation("other-id")
            assertEquals(sessionId, viewModel.uiState.value.pendingSessionId)

            viewModel.consumePendingSessionNavigation(sessionId)
            assertNull(viewModel.uiState.value.pendingSessionId)
        }

    @Test
    fun startLiveBlankSession_recoversFromRepositoryFailure() =
        runTest(testDispatcher) {
            val delegate = DefaultSessionRepository(InMemorySessionDao(), clock)
            val failingRepository = object : SessionRepository by delegate {
                override suspend fun createLiveBlankSession(
                    discipline: Discipline,
                    title: String,
                ): Session {
                    throw IllegalStateException("Storage failure")
                }
            }
            val viewModel = AppShellViewModel(failingRepository)

            viewModel.startLiveBlankSession()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isStartingSession)
            assertNull(state.pendingSessionId)
        }
}
