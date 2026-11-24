package com.e4eheh.e4ehehworkout.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.e4eheh.e4ehehworkout.domain.analytics.BodyweightTrendCalculator
import com.e4eheh.e4ehehworkout.domain.analytics.BodyweightTrendReport
import com.e4eheh.e4ehehworkout.domain.analytics.ExerciseProgressionCalculator
import com.e4eheh.e4ehehworkout.domain.analytics.ExerciseProgressionReport
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressAnalytics
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressAnalyticsCalculator
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressTimeRange
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressionMetric
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntry
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.repository.BodyweightRepository
import com.e4eheh.e4ehehworkout.domain.repository.ExerciseRepository
import com.e4eheh.e4ehehworkout.domain.repository.SessionRepository
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

/** Combined UI state for the Progress telemetry tab. */
data class ProgressUiState(
    val selectedTimeRange: ProgressTimeRange = ProgressTimeRange.ALL,
    val analytics: ProgressAnalytics = ProgressAnalyticsCalculator.empty(ProgressTimeRange.ALL),
    val exercises: List<Exercise> = emptyList(),
    val selectedExerciseId: String? = null,
    val exerciseProgression: ExerciseProgressionReport? = null,
    val isExercisePickerOpen: Boolean = false,
    val pickerDisciplineFilter: Discipline? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val bodyweightTrend: BodyweightTrendReport = BodyweightTrendCalculator.empty(ProgressTimeRange.ALL),
    val bodyweightEntries: List<BodyweightEntryRowUi> = emptyList(),
    val isManageBodyweightOpen: Boolean = false,
    val bodyweightEditor: BodyweightEntryEditorState? = null,
    val deletingBodyweightEntry: BodyweightEntry? = null,
)

/** User actions dispatched from the Progress tab. */
sealed interface ProgressAction {
    data class SelectTimeRange(val timeRange: ProgressTimeRange) : ProgressAction
    data object OpenExercisePicker : ProgressAction
    data object DismissExercisePicker : ProgressAction
    data class SelectPickerDisciplineFilter(val discipline: Discipline?) : ProgressAction
    data class SelectExercise(val exerciseId: String) : ProgressAction
    data class SelectProgressionMetric(val metric: ProgressionMetric) : ProgressAction
    data object ToggleManageBodyweight : ProgressAction
    data class Bodyweight(val action: BodyweightCrudAction) : ProgressAction
}

/** Coordinates time-range filtering, analytics, Exercise progression, and Bodyweight trend for Progress. */
class ProgressViewModel(
    sessionRepository: SessionRepository,
    exerciseRepository: ExerciseRepository,
    userPreferencesRepository: UserPreferencesRepository,
    bodyweightRepository: BodyweightRepository,
    private val clock: Clock,
) : ViewModel() {
    private val selectedTimeRange = MutableStateFlow(ProgressTimeRange.ALL)
    private val selectionState = MutableStateFlow(ExerciseSelectionState())
    private val isManageBodyweightOpen = MutableStateFlow(false)
    private val latestWeightUnit = MutableStateFlow(WeightUnit.KG)

    private val bodyweightCoordinator = BodyweightEntryCoordinator(
        repository = bodyweightRepository,
        clock = clock,
        scope = viewModelScope,
        currentWeightUnit = { latestWeightUnit.value },
    )

    private val bodyweightState = combine(
        bodyweightRepository.observeActiveEntries(),
        isManageBodyweightOpen,
        bodyweightCoordinator.modalState,
    ) { entries, manageOpen, modalState ->
        Triple(entries, manageOpen, modalState)
    }

    private val controlsState = combine(
        selectedTimeRange,
        selectionState,
        bodyweightState,
    ) { range, selection, bodyweight ->
        ControlsSnapshot(
            timeRange = range,
            selection = selection,
            bodyweightEntries = bodyweight.first,
            isManageBodyweightOpen = bodyweight.second,
            bodyweightModal = bodyweight.third,
        )
    }

    val uiState: StateFlow<ProgressUiState> = combine(
        sessionRepository.observeActiveSessions(),
        exerciseRepository.observeActiveExercises(),
        userPreferencesRepository.observePreferences().onEach { prefs ->
            latestWeightUnit.value = prefs.weightUnit
        },
        controlsState,
    ) { sessions, exercises, preferences, controls ->
        val range = controls.timeRange
        val selection = controls.selection
        val selectedExercise = exercises.firstOrNull { it.id == selection.selectedExerciseId }
            ?: ExerciseProgressionCalculator.selectDefaultExercise(exercises, sessions)

        val exerciseProgression = selectedExercise?.let { exercise ->
            ExerciseProgressionCalculator.calculate(
                exercise = exercise,
                sessions = sessions,
                timeRange = range,
                requestedMetric = selection.selectedMetric,
                weightUnit = preferences.weightUnit,
                distanceUnit = preferences.distanceUnit,
                clock = clock,
            )
        }

        val bodyweightTrend = BodyweightTrendCalculator.calculate(
            entries = controls.bodyweightEntries,
            timeRange = range,
            weightUnit = preferences.weightUnit,
            clock = clock,
        )

        val bodyweightRows = controls.bodyweightEntries.map { entry ->
            BodyweightEntryCoordinator.toRowUi(entry, preferences.weightUnit, clock)
        }

        ProgressUiState(
            selectedTimeRange = range,
            analytics = ProgressAnalyticsCalculator.calculate(
                sessions = sessions,
                timeRange = range,
                clock = clock,
            ),
            exercises = exercises,
            selectedExerciseId = selectedExercise?.id,
            exerciseProgression = exerciseProgression,
            isExercisePickerOpen = selection.isPickerOpen,
            pickerDisciplineFilter = selection.pickerDisciplineFilter,
            weightUnit = preferences.weightUnit,
            distanceUnit = preferences.distanceUnit,
            bodyweightTrend = bodyweightTrend,
            bodyweightEntries = bodyweightRows,
            isManageBodyweightOpen = controls.isManageBodyweightOpen,
            bodyweightEditor = controls.bodyweightModal.editor,
            deletingBodyweightEntry = controls.bodyweightModal.deletingEntry,
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

            ProgressAction.OpenExercisePicker -> {
                selectionState.update { it.copy(isPickerOpen = true) }
            }

            ProgressAction.DismissExercisePicker -> {
                selectionState.update { it.copy(isPickerOpen = false) }
            }

            is ProgressAction.SelectPickerDisciplineFilter -> {
                selectionState.update { it.copy(pickerDisciplineFilter = action.discipline) }
            }

            is ProgressAction.SelectExercise -> {
                selectionState.update {
                    it.copy(
                        selectedExerciseId = action.exerciseId,
                        selectedMetric = null,
                        isPickerOpen = false,
                    )
                }
            }

            is ProgressAction.SelectProgressionMetric -> {
                selectionState.update { it.copy(selectedMetric = action.metric) }
            }

            ProgressAction.ToggleManageBodyweight -> {
                isManageBodyweightOpen.update { !it }
            }

            is ProgressAction.Bodyweight -> bodyweightCoordinator.onAction(action.action)
        }
    }

    private data class ExerciseSelectionState(
        val selectedExerciseId: String? = null,
        val selectedMetric: ProgressionMetric? = null,
        val isPickerOpen: Boolean = false,
        val pickerDisciplineFilter: Discipline? = null,
    )

    private data class ControlsSnapshot(
        val timeRange: ProgressTimeRange,
        val selection: ExerciseSelectionState,
        val bodyweightEntries: List<BodyweightEntry>,
        val isManageBodyweightOpen: Boolean,
        val bodyweightModal: com.e4eheh.e4ehehworkout.ui.components.BodyweightModalState,
    )

    class Factory(
        private val sessionRepository: SessionRepository,
        private val exerciseRepository: ExerciseRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val bodyweightRepository: BodyweightRepository,
        private val clock: Clock,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProgressViewModel::class.java))
            return ProgressViewModel(
                sessionRepository = sessionRepository,
                exerciseRepository = exerciseRepository,
                userPreferencesRepository = userPreferencesRepository,
                bodyweightRepository = bodyweightRepository,
                clock = clock,
            ) as T
        }
    }
}
