package com.e4eheh.e4ehehworkout.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for app-level quick actions such as starting a live Session. */
data class AppShellUiState(
    val isStartingSession: Boolean = false,
    val pendingSessionId: String? = null,
)

/** Coordinates top-level shell actions that span tabs before navigating into a destination. */
class AppShellViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppShellUiState())
    val uiState: StateFlow<AppShellUiState> = _uiState.asStateFlow()

    fun startLiveBlankSession() {
        launchSessionCreation { sessionRepository.createLiveBlankSession() }
    }

    fun startSessionFromRoutine(routineId: String) {
        launchSessionCreation { sessionRepository.createLiveSessionFromRoutine(routineId) }
    }

    private inline fun launchSessionCreation(crossinline createSession: suspend () -> Session) {
        if (_uiState.value.isStartingSession) return
        _uiState.update { it.copy(isStartingSession = true) }

        viewModelScope.launch {
            try {
                val created = createSession()
                _uiState.update {
                    it.copy(
                        isStartingSession = false,
                        pendingSessionId = created.id,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isStartingSession = false) }
            }
        }
    }

    fun consumePendingSessionNavigation(sessionId: String) {
        _uiState.update { current ->
            if (current.pendingSessionId == sessionId) {
                current.copy(pendingSessionId = null)
            } else {
                current
            }
        }
    }

    class Factory(
        private val sessionRepository: SessionRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AppShellViewModel::class.java))
            return AppShellViewModel(sessionRepository = sessionRepository) as T
        }
    }
}
