package com.e4eheh.e4ehehworkout.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.repository.ExerciseRepository
import com.e4eheh.e4ehehworkout.domain.repository.SessionRepository
import java.time.Clock
import java.time.Duration
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** HUD banner summary for an auto-saved IN_PROGRESS Session on Home. */
data class ActiveSessionBannerUi(
    val sessionId: String,
    val displayTitle: String,
    val discipline: Discipline,
    val exerciseCount: Int,
    val completedSetCount: Int,
    val totalSetCount: Int,
    val elapsedSeconds: Long,
)

/** Home history summary for a completed Session that can be opened in Session Detail. */
data class CompletedSessionHistoryItem(
    val sessionId: String,
    val displayTitle: String,
    val discipline: Discipline,
    val dateTimeLabel: String,
    val durationSeconds: Long,
    val exerciseCount: Int,
)

/** Combined UI state for the Batcomputer Home screen. */
data class HomeUiState(
    val exerciseCount: Int = 0,
    val activeSessionBanner: ActiveSessionBannerUi? = null,
    val pendingDiscardSession: ActiveSessionBannerUi? = null,
    val isDiscarding: Boolean = false,
    val discardError: String? = null,
    val completedSessions: List<CompletedSessionHistoryItem> = emptyList(),
)

/** User and clock-tick actions dispatched from the Home screen. */
sealed interface HomeAction {
    data object RefreshElapsed : HomeAction
    data object RequestDiscardActiveSession : HomeAction
    data object DismissDiscardDialog : HomeAction
    data object ConfirmDiscardActiveSession : HomeAction
}

/** Coordinates Home overview telemetry and IN_PROGRESS Session resume/discard actions. */
class HomeViewModel(
    exerciseRepository: ExerciseRepository,
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) : ViewModel() {
    private data class InternalState(
        val pendingDiscardSessionId: String? = null,
        val isDiscarding: Boolean = false,
        val discardError: String? = null,
        val clockTick: Long = 0L,
    )

    private val internalState = MutableStateFlow(InternalState())

    val uiState: StateFlow<HomeUiState> = combine(
        exerciseRepository.observeActiveExercises(),
        sessionRepository.observeActiveSessions(),
        internalState,
    ) { exercises, sessions, state ->
        val now = clock.instant()
        val activeSessions = sessions.filter { it.status == SessionStatus.IN_PROGRESS }
        val activeBanner = activeSessions.firstOrNull()?.toBannerUi(now)
        val pendingDiscard = state.pendingDiscardSessionId?.let { targetId ->
            activeSessions.firstOrNull { it.id == targetId }?.toBannerUi(now)
        }
        HomeUiState(
            exerciseCount = exercises.size,
            activeSessionBanner = activeBanner,
            pendingDiscardSession = pendingDiscard,
            isDiscarding = state.isDiscarding,
            discardError = if (activeBanner != null) state.discardError else null,
            completedSessions = sessions
                .filter { it.status == SessionStatus.COMPLETED }
                .map { it.toHistoryItem(clock) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(),
    )

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.RefreshElapsed -> {
                internalState.update { it.copy(clockTick = it.clockTick + 1L) }
            }

            HomeAction.RequestDiscardActiveSession -> {
                val activeId = uiState.value.activeSessionBanner?.sessionId ?: return
                internalState.update {
                    it.copy(pendingDiscardSessionId = activeId, discardError = null)
                }
            }

            HomeAction.DismissDiscardDialog -> {
                if (internalState.value.isDiscarding) return
                internalState.update { it.copy(pendingDiscardSessionId = null) }
            }

            HomeAction.ConfirmDiscardActiveSession -> confirmDiscardActiveSession()
        }
    }

    private fun confirmDiscardActiveSession() {
        val current = internalState.value
        if (current.isDiscarding) return
        val targetId = current.pendingDiscardSessionId
            ?: uiState.value.activeSessionBanner?.sessionId
            ?: return

        internalState.update { it.copy(isDiscarding = true, discardError = null) }

        viewModelScope.launch {
            var errorMessage: String? = null
            try {
                sessionRepository.softDelete(targetId)
            } catch (_: Exception) {
                errorMessage = "Unable to discard active Session. Please try again."
            } finally {
                internalState.update {
                    it.copy(
                        isDiscarding = false,
                        pendingDiscardSessionId = null,
                        discardError = errorMessage,
                    )
                }
            }
        }
    }

    private fun Session.toHistoryItem(sessionClock: Clock): CompletedSessionHistoryItem =
        CompletedSessionHistoryItem(
            sessionId = id,
            displayTitle = resolvedDisplayTitle(),
            discipline = discipline,
            dateTimeLabel = HISTORY_DATE_TIME_FORMATTER.format(startedAt.atZone(sessionClock.zone)),
            durationSeconds = durationSeconds,
            exerciseCount = exercises.size,
        )

    private companion object {
        val HISTORY_DATE_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }

    private fun Session.toBannerUi(now: java.time.Instant): ActiveSessionBannerUi {
        val allSets = exercises.flatMap { it.sets }
        val elapsed = Duration.between(startedAt, now).seconds.coerceAtLeast(0L)
        return ActiveSessionBannerUi(
            sessionId = id,
            displayTitle = resolvedDisplayTitle(),
            discipline = discipline,
            exerciseCount = exercises.size,
            completedSetCount = allSets.count { it.isCompleted },
            totalSetCount = allSets.size,
            elapsedSeconds = elapsed,
        )
    }

    private fun Session.resolvedDisplayTitle(): String =
        title.trim().ifEmpty { "${discipline.displayName} Session" }

    class Factory(
        private val exerciseRepository: ExerciseRepository,
        private val sessionRepository: SessionRepository,
        private val clock: Clock,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java))
            return HomeViewModel(
                exerciseRepository = exerciseRepository,
                sessionRepository = sessionRepository,
                clock = clock,
            ) as T
        }
    }
}
