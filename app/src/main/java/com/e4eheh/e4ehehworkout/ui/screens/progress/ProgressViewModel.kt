package com.e4eheh.e4ehehworkout.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.e4eheh.e4ehehworkout.domain.analytics.ExerciseProgressionCalculator
import com.e4eheh.e4ehehworkout.domain.analytics.ExerciseProgressionReport
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressAnalytics
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressAnalyticsCalculator
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressTimeRange
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressionMetric
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.repository.ExerciseRepository
import com.e4eheh.e4ehehworkout.domain.repository.SessionRepository
import com.e4eheh.e4ehehworkout.domain.repository.UserPreferencesRepository
import java.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
)

/** User actions dispatched from the Progress tab. */
sealed interface ProgressAction {
    data class SelectTimeRange(val timeRange: ProgressTimeRange) : ProgressAction
    data object OpenExercisePicker : ProgressAction
    data object DismissExercisePicker : ProgressAction
    data class SelectPickerDisciplineFilter(val discipline: Discipline?) : ProgressAction
    data class SelectExercise(val exerciseId: String) : ProgressAction
    data class SelectProgressionMetric(val metric: ProgressionMetric) : ProgressAction
}

/** Coordinates time-range filtering, analytics, and Exercise progression for the Progress tab. */
class ProgressViewModel(
    sessionRepository: SessionRepository,
    exerciseRepository: ExerciseRepository,
    userPreferencesRepository: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {
    private val selectedTimeRange = MutableStateFlow(ProgressTimeRange.ALL)
    private val selectionState = MutableStateFlow(ExerciseSelectionState())

    val uiState: StateFlow<ProgressUiState> = combine(
        sessionRepository.observeActiveSessions(),
        exerciseRepository.observeActiveExercises(),
        userPreferencesRepository.observePreferences(),
        selectedTimeRange,
        selectionState,
    ) { sessions, exercises, preferences, range, selection ->
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
        }
    }

    private data class ExerciseSelectionState(
        val selectedExerciseId: String? = null,
        val selectedMetric: ProgressionMetric? = null,
        val isPickerOpen: Boolean = false,
        val pickerDisciplineFilter: Discipline? = null,
    )

    class Factory(
        private val sessionRepository: SessionRepository,
        private val exerciseRepository: ExerciseRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val clock: Clock,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProgressViewModel::class.java))
            return ProgressViewModel(
                sessionRepository = sessionRepository,
                exerciseRepository = exerciseRepository,
                userPreferencesRepository = userPreferencesRepository,
                clock = clock,
            ) as T
        }
    }
}
