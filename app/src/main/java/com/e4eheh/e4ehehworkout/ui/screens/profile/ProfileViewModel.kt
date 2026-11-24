package com.e4eheh.e4ehehworkout.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntry
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.repository.BodyweightRepository
import com.e4eheh.e4ehehworkout.domain.repository.UserPreferencesRepository
import com.e4eheh.e4ehehworkout.ui.components.BodyweightCrudAction
import com.e4eheh.e4ehehworkout.ui.components.BodyweightEntryCoordinator
import com.e4eheh.e4ehehworkout.ui.components.BodyweightEntryEditorState
import com.e4eheh.e4ehehworkout.ui.components.BodyweightEntryRowUi
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI state for the Profile tab preferences, Bodyweight Entry log, and sync placeholder screen. */
data class ProfileUiState(
    val preferences: UserPreferences = UserPreferences(),
    val displayNameDraft: String = UserPreferences.DEFAULT_DISPLAY_NAME,
    val displayNameError: String? = null,
    val displayNameSaved: Boolean = false,
    val bodyweightEntries: List<BodyweightEntryRowUi> = emptyList(),
    val bodyweightEditor: BodyweightEntryEditorState? = null,
    val deletingBodyweightEntry: BodyweightEntry? = null,
) {
    val hasUnsavedDisplayName: Boolean
        get() = displayNameDraft.trim() != preferences.displayName
}

/** User actions dispatched from the Profile preferences and Bodyweight UI. */
sealed interface ProfileAction {
    data class UpdateDisplayNameDraft(val displayName: String) : ProfileAction
    data object SaveDisplayName : ProfileAction
    data class SelectWeightUnit(val weightUnit: WeightUnit) : ProfileAction
    data class SelectDistanceUnit(val distanceUnit: DistanceUnit) : ProfileAction
    data class AdjustRestTimer(val deltaSeconds: Int) : ProfileAction
    data class SelectRestTimer(val durationSeconds: Int) : ProfileAction
    data class Bodyweight(val action: BodyweightCrudAction) : ProfileAction
}

/** Coordinates Profile preference observations, Bodyweight Entries, and updates. */
class ProfileViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    bodyweightRepository: BodyweightRepository,
    private val clock: Clock,
) : ViewModel() {
    private val displayNameDraftOverride = MutableStateFlow<String?>(null)
    private val displayNameError = MutableStateFlow<String?>(null)
    private val displayNameSaved = MutableStateFlow(false)
    private val latestWeightUnit = MutableStateFlow(WeightUnit.KG)

    private val bodyweightCoordinator = BodyweightEntryCoordinator(
        repository = bodyweightRepository,
        clock = clock,
        scope = viewModelScope,
        currentWeightUnit = { latestWeightUnit.value },
    )

    private val nameDraftState = combine(
        displayNameDraftOverride,
        displayNameError,
        displayNameSaved,
    ) { draftOverride, error, saved ->
        Triple(draftOverride, error, saved)
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        userPreferencesRepository.observePreferences().onEach { persisted ->
            latestWeightUnit.value = persisted.weightUnit
            displayNameDraftOverride.update { draft ->
                if (draft == persisted.displayName) null else draft
            }
        },
        nameDraftState,
        bodyweightRepository.observeActiveEntries(),
        bodyweightCoordinator.modalState,
    ) { preferences, (draftOverride, error, saved), entries, modalState ->
        ProfileUiState(
            preferences = preferences,
            displayNameDraft = draftOverride ?: preferences.displayName,
            displayNameError = error,
            displayNameSaved = saved,
            bodyweightEntries = entries.map { entry ->
                BodyweightEntryCoordinator.toRowUi(entry, preferences.weightUnit, clock)
            },
            bodyweightEditor = modalState.editor,
            deletingBodyweightEntry = modalState.deletingEntry,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
    )

    fun onAction(action: ProfileAction) {
        when (action) {
            is ProfileAction.UpdateDisplayNameDraft -> {
                displayNameDraftOverride.value = action.displayName
                displayNameError.value = null
                displayNameSaved.value = false
            }

            ProfileAction.SaveDisplayName -> saveDisplayName()

            is ProfileAction.SelectWeightUnit -> {
                viewModelScope.launch {
                    userPreferencesRepository.updateWeightUnit(action.weightUnit)
                }
            }

            is ProfileAction.SelectDistanceUnit -> {
                viewModelScope.launch {
                    userPreferencesRepository.updateDistanceUnit(action.distanceUnit)
                }
            }

            is ProfileAction.AdjustRestTimer -> {
                viewModelScope.launch {
                    userPreferencesRepository.adjustDefaultRestTimerSeconds(action.deltaSeconds)
                }
            }

            is ProfileAction.SelectRestTimer -> {
                viewModelScope.launch {
                    userPreferencesRepository.updateDefaultRestTimerSeconds(action.durationSeconds)
                }
            }

            is ProfileAction.Bodyweight -> bodyweightCoordinator.onAction(action.action)
        }
    }

    private fun saveDisplayName() {
        val currentDraft = displayNameDraftOverride.value ?: uiState.value.preferences.displayName
        val trimmed = currentDraft.trim()
        if (trimmed.isEmpty()) {
            displayNameError.value = "Display name is required."
            displayNameSaved.value = false
            return
        }

        displayNameError.value = null
        displayNameDraftOverride.value = trimmed
        viewModelScope.launch {
            userPreferencesRepository.updateDisplayName(trimmed)
            displayNameSaved.value = true
        }
    }

    class Factory(
        private val userPreferencesRepository: UserPreferencesRepository,
        private val bodyweightRepository: BodyweightRepository,
        private val clock: Clock,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
            return ProfileViewModel(
                userPreferencesRepository = userPreferencesRepository,
                bodyweightRepository = bodyweightRepository,
                clock = clock,
            ) as T
        }
    }
}
