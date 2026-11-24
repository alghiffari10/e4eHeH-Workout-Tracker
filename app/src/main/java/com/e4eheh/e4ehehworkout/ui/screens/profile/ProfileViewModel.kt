package com.e4eheh.e4ehehworkout.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.e4eheh.e4ehehworkout.domain.backup.BackupFileNames
import com.e4eheh.e4ehehworkout.domain.backup.BackupImportResult
import com.e4eheh.e4ehehworkout.domain.backup.BackupRepository
import com.e4eheh.e4ehehworkout.domain.backup.BackupSummary
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.repository.BodyweightRepository
import com.e4eheh.e4ehehworkout.domain.repository.UserPreferencesRepository
import com.e4eheh.e4ehehworkout.ui.components.BodyweightCrudAction
import com.e4eheh.e4ehehworkout.ui.components.BodyweightEntryCoordinator
import com.e4eheh.e4ehehworkout.ui.components.BodyweightSectionState
import java.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Direction of a local JSON backup operation. */
enum class BackupOperation {
    EXPORT,
    IMPORT,
}

/** Feedback banner state surfaced after a JSON backup export or import attempt. */
sealed interface BackupFeedback {
    val operation: BackupOperation
    val message: String

    data class Success(
        override val operation: BackupOperation,
        override val message: String,
        val summary: BackupSummary,
    ) : BackupFeedback

    data class Error(
        override val operation: BackupOperation,
        override val message: String,
    ) : BackupFeedback
}

/** UI state for local JSON backup export and restore controls on Profile. */
data class ProfileBackupUiState(
    val suggestedFileName: String = "e4eheh-workout-backup.json",
    val isBusy: Boolean = false,
    val feedback: BackupFeedback? = null,
)

/** UI state for the Profile tab preferences, Bodyweight Entry log, backup, and sync placeholder screen. */
data class ProfileUiState(
    val preferences: UserPreferences = UserPreferences(),
    val displayNameDraft: String = UserPreferences.DEFAULT_DISPLAY_NAME,
    val displayNameError: String? = null,
    val displayNameSaved: Boolean = false,
    val bodyweight: BodyweightSectionState = BodyweightSectionState(),
    val backup: ProfileBackupUiState = ProfileBackupUiState(),
) {
    val hasUnsavedDisplayName: Boolean
        get() = displayNameDraft.trim() != preferences.displayName
}

/** User actions dispatched from the Profile preferences, Bodyweight, and Backup UI. */
sealed interface ProfileAction {
    data class UpdateDisplayNameDraft(val displayName: String) : ProfileAction
    data object SaveDisplayName : ProfileAction
    data class SelectWeightUnit(val weightUnit: WeightUnit) : ProfileAction
    data class SelectDistanceUnit(val distanceUnit: DistanceUnit) : ProfileAction
    data class AdjustRestTimer(val deltaSeconds: Int) : ProfileAction
    data class SelectRestTimer(val durationSeconds: Int) : ProfileAction
    data class Bodyweight(val action: BodyweightCrudAction) : ProfileAction
    data class ExportBackup(val writeJson: suspend (String) -> Unit) : ProfileAction
    data class ImportBackup(val readJson: suspend () -> String) : ProfileAction
    data object DismissBackupFeedback : ProfileAction
}

/** Coordinates Profile preference observations, Bodyweight Entries, JSON backup/restore, and updates. */
class ProfileViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    bodyweightRepository: BodyweightRepository,
    private val backupRepository: BackupRepository,
    private val clock: Clock,
) : ViewModel() {
    private val displayNameDraftOverride = MutableStateFlow<String?>(null)
    private val displayNameError = MutableStateFlow<String?>(null)
    private val displayNameSaved = MutableStateFlow(false)
    private val latestWeightUnit = MutableStateFlow(WeightUnit.KG)
    private val backupBusy = MutableStateFlow(false)
    private val backupFeedback = MutableStateFlow<BackupFeedback?>(null)

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

    private val backupState = combine(
        backupBusy,
        backupFeedback,
    ) { isBusy, feedback ->
        ProfileBackupUiState(
            suggestedFileName = BackupFileNames.suggestedFileName(clock),
            isBusy = isBusy,
            feedback = feedback,
        )
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
        backupState,
    ) { preferences, (draftOverride, error, saved), entries, modalState, backup ->
        ProfileUiState(
            preferences = preferences,
            displayNameDraft = draftOverride ?: preferences.displayName,
            displayNameError = error,
            displayNameSaved = saved,
            bodyweight = BodyweightEntryCoordinator.buildSectionState(
                entries = entries,
                modalState = modalState,
                weightUnit = preferences.weightUnit,
                clock = clock,
            ),
            backup = backup,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(
            backup = ProfileBackupUiState(
                suggestedFileName = BackupFileNames.suggestedFileName(clock),
            ),
        ),
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

            is ProfileAction.ExportBackup -> exportBackup(action.writeJson)

            is ProfileAction.ImportBackup -> importBackup(action.readJson)

            ProfileAction.DismissBackupFeedback -> backupFeedback.value = null
        }
    }

    private fun exportBackup(writeJson: suspend (String) -> Unit) {
        if (backupBusy.value) return
        backupBusy.value = true
        backupFeedback.value = null
        viewModelScope.launch {
            try {
                val payload = backupRepository.exportBackup()
                writeJson(payload.json)
                backupFeedback.value = BackupFeedback.Success(
                    operation = BackupOperation.EXPORT,
                    message = "Exported ${formatSummaryCounts(payload.summary)} " +
                        "(JSON v${payload.summary.schemaVersion}).",
                    summary = payload.summary,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                backupFeedback.value = BackupFeedback.Error(
                    operation = BackupOperation.EXPORT,
                    message = error.message?.takeIf { it.isNotBlank() }
                        ?: "Failed to write backup file.",
                )
            } finally {
                backupBusy.value = false
            }
        }
    }

    private fun importBackup(readJson: suspend () -> String) {
        if (backupBusy.value) return
        backupBusy.value = true
        backupFeedback.value = null
        viewModelScope.launch {
            try {
                val rawJson = readJson()
                when (val result = backupRepository.importBackup(rawJson)) {
                    is BackupImportResult.Success -> {
                        backupFeedback.value = BackupFeedback.Success(
                            operation = BackupOperation.IMPORT,
                            message = "Restored ${formatSummaryCounts(result.summary)} " +
                                "(JSON v${result.summary.schemaVersion}).",
                            summary = result.summary,
                        )
                    }
                    is BackupImportResult.Failure -> {
                        backupFeedback.value = BackupFeedback.Error(
                            operation = BackupOperation.IMPORT,
                            message = result.reason,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                backupFeedback.value = BackupFeedback.Error(
                    operation = BackupOperation.IMPORT,
                    message = error.message?.takeIf { it.isNotBlank() }
                        ?: "Failed to read backup file.",
                )
            } finally {
                backupBusy.value = false
            }
        }
    }

    private fun formatSummaryCounts(summary: BackupSummary): String = listOf(
        formatCount(summary.exerciseCount, "Exercise", "Exercises"),
        formatCount(summary.routineCount, "Routine", "Routines"),
        formatCount(summary.sessionCount, "Session", "Sessions"),
        formatCount(summary.bodyweightEntryCount, "Bodyweight Entry", "Bodyweight Entries"),
    ).joinToString(", ")

    private fun formatCount(count: Int, singular: String, plural: String): String =
        "$count ${if (count == 1) singular else plural}"

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
        private val backupRepository: BackupRepository,
        private val clock: Clock,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
            return ProfileViewModel(
                userPreferencesRepository = userPreferencesRepository,
                bodyweightRepository = bodyweightRepository,
                backupRepository = backupRepository,
                clock = clock,
            ) as T
        }
    }
}
