package com.e4eheh.e4ehehworkout.ui.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Form state for creating or editing an Exercise on the Workout tab. */
data class ExerciseEditorState(
    val editingExerciseId: String? = null,
    val name: String = "",
    val discipline: Discipline = Discipline.CALISTHENICS,
    val trackingMode: TrackingMode = TrackingMode.REPS_AND_LOAD,
    val defaultNotes: String = "",
    val nameError: String? = null,
)

/** UI state for the Workout tab Exercise library screen. */
data class ExerciseLibraryUiState(
    val exercises: List<Exercise> = emptyList(),
    val editorState: ExerciseEditorState? = null,
    val pendingDeleteExercise: Exercise? = null,
)

/** User actions dispatched from the Workout Exercise library UI. */
sealed interface ExerciseLibraryAction {
    data object OpenCreateDialog : ExerciseLibraryAction
    data class OpenEditDialog(val exercise: Exercise) : ExerciseLibraryAction
    data class UpdateName(val name: String) : ExerciseLibraryAction
    data class UpdateDiscipline(val discipline: Discipline) : ExerciseLibraryAction
    data class UpdateTrackingMode(val trackingMode: TrackingMode) : ExerciseLibraryAction
    data class UpdateDefaultNotes(val notes: String) : ExerciseLibraryAction
    data object DismissEditor : ExerciseLibraryAction
    data object SaveExercise : ExerciseLibraryAction
    data class RequestDelete(val exercise: Exercise) : ExerciseLibraryAction
    data object DismissDelete : ExerciseLibraryAction
    data object ConfirmDelete : ExerciseLibraryAction
}

/** Coordinates Exercise library observations and CRUD operations. */
class ExerciseLibraryViewModel(
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {
    private val editorState = MutableStateFlow<ExerciseEditorState?>(null)
    private val pendingDeleteExercise = MutableStateFlow<Exercise?>(null)

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        exerciseRepository.observeActiveExercises(),
        editorState,
        pendingDeleteExercise,
    ) { exercises, editor, pendingDelete ->
        ExerciseLibraryUiState(
            exercises = exercises,
            editorState = editor,
            pendingDeleteExercise = pendingDelete,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ExerciseLibraryUiState(),
    )

    fun onAction(action: ExerciseLibraryAction) {
        when (action) {
            ExerciseLibraryAction.OpenCreateDialog -> {
                editorState.value = ExerciseEditorState()
            }

            is ExerciseLibraryAction.OpenEditDialog -> {
                val exercise = action.exercise
                editorState.value = ExerciseEditorState(
                    editingExerciseId = exercise.id,
                    name = exercise.name,
                    discipline = exercise.discipline,
                    trackingMode = exercise.trackingMode,
                    defaultNotes = exercise.defaultNotes.orEmpty(),
                )
            }

            is ExerciseLibraryAction.UpdateName -> {
                editorState.update { it?.copy(name = action.name, nameError = null) }
            }

            is ExerciseLibraryAction.UpdateDiscipline -> {
                editorState.update { it?.copy(discipline = action.discipline) }
            }

            is ExerciseLibraryAction.UpdateTrackingMode -> {
                editorState.update { it?.copy(trackingMode = action.trackingMode) }
            }

            is ExerciseLibraryAction.UpdateDefaultNotes -> {
                editorState.update { it?.copy(defaultNotes = action.notes) }
            }

            ExerciseLibraryAction.DismissEditor -> {
                editorState.value = null
            }

            ExerciseLibraryAction.SaveExercise -> saveExercise()

            is ExerciseLibraryAction.RequestDelete -> {
                pendingDeleteExercise.value = action.exercise
            }

            ExerciseLibraryAction.DismissDelete -> {
                pendingDeleteExercise.value = null
            }

            ExerciseLibraryAction.ConfirmDelete -> {
                val target = pendingDeleteExercise.value ?: return
                pendingDeleteExercise.value = null
                viewModelScope.launch {
                    try {
                        exerciseRepository.softDelete(target.id)
                    } catch (_: NoSuchElementException) {
                        // Exercise was already removed concurrently.
                    }
                }
            }
        }
    }

    private fun saveExercise() {
        val current = editorState.value ?: return
        val trimmedName = current.name.trim()
        if (trimmedName.isEmpty()) {
            editorState.value = current.copy(nameError = "Exercise name is required.")
            return
        }

        val input = ExerciseInput(
            name = trimmedName,
            discipline = current.discipline,
            trackingMode = current.trackingMode,
            defaultNotes = current.defaultNotes,
        )
        val editingId = current.editingExerciseId
        editorState.value = null

        viewModelScope.launch {
            if (editingId == null) {
                exerciseRepository.create(input)
            } else {
                try {
                    exerciseRepository.update(editingId, input)
                } catch (_: NoSuchElementException) {
                    // Exercise was deleted before the edit saved.
                }
            }
        }
    }

    class Factory(
        private val exerciseRepository: ExerciseRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ExerciseLibraryViewModel::class.java))
            return ExerciseLibraryViewModel(exerciseRepository) as T
        }
    }
}
