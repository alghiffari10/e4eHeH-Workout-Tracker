package com.e4eheh.e4ehehworkout.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressAnalytics
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressAnalyticsCalculator
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressTimeRange
import com.e4eheh.e4ehehworkout.domain.repository.SessionRepository
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Combined UI state for the Progress telemetry tab. */
data class ProgressUiState(
    val selectedTimeRange: ProgressTimeRange = ProgressTimeRange.ALL,
    val analytics: ProgressAnalytics = ProgressAnalyticsCalculator.empty(ProgressTimeRange.ALL),
)

/** User actions dispatched from the Progress tab. */
sealed interface ProgressAction {
    data class SelectTimeRange(val timeRange: ProgressTimeRange) : ProgressAction
}

/** Coordinates time-range filtering and analytics computation for the Progress tab. */
class ProgressViewModel(
    sessionRepository: SessionRepository,
    private val clock: Clock,
) : ViewModel() {
    private val selectedTimeRange = MutableStateFlow(ProgressTimeRange.ALL)

    val uiState: StateFlow<ProgressUiState> = combine(
        sessionRepository.observeActiveSessions(),
        selectedTimeRange,
    ) { sessions, range ->
        ProgressUiState(
            selectedTimeRange = range,
            analytics = ProgressAnalyticsCalculator.calculate(
                sessions = sessions,
                timeRange = range,
                clock = clock,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ProgressUiState(),
    )

    fun onAction(action: ProgressAction) {
        when (action) {
            is ProgressAction.SelectTimeRange -> {
                selectedTimeRange.value = action.timeRange
            }
        }
    }

    class Factory(
        private val sessionRepository: SessionRepository,
        private val clock: Clock,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProgressViewModel::class.java))
            return ProgressViewModel(
                sessionRepository = sessionRepository,
                clock = clock,
            ) as T
        }
    }
}
