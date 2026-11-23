package com.e4eheh.e4ehehworkout.ui.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.Routine
import com.e4eheh.e4ehehworkout.domain.model.RoutineExercise
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.repository.ExerciseRepository
import com.e4eheh.e4ehehworkout.domain.repository.RoutineRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Top-level segmented section shown on the Workout tab. */
enum class WorkoutTabSection {
    ROUTINES,
    EXERCISES,
}

/** Draft slot representing an ordered Exercise inside the Routine Builder. */
data class RoutineExerciseDraft(
    val draftKey: String,
    val routineExerciseId: String?,
    val exerciseId: String,
    val defaultTargetSets: Int? = null,
)

/** Form state for creating a new Exercise inline inside the Routine Builder picker. */
data class InlineExerciseDraft(
    val name: String = "",
    val discipline: Discipline,
    val trackingMode: TrackingMode = TrackingMode.REPS_AND_LOAD,
    val nameError: String? = null,
    val isSaving: Boolean = false,
)

/** Draft state for creating or editing a Routine without leaving the Workout screen. */
data class RoutineBuilderState(
    val editingRoutineId: String? = null,
    val name: String = "",
    val discipline: Discipline = Discipline.CALISTHENICS,
    val exercises: List<RoutineExerciseDraft> = emptyList(),
    val nameError: String? = null,
    val showExercisePicker: Boolean = false,
    val pickerDisciplineFilter: Discipline? = null,
    val inlineExercise: InlineExerciseDraft? = null,
) {
    fun moveExercise(index: Int, delta: Int): RoutineBuilderState {
        val targetIndex = index + delta
        if (index !in exercises.indices || targetIndex !in exercises.indices) return this
        val mutable = exercises.toMutableList()
        val item = mutable[index]
        mutable[index] = mutable[targetIndex]
        mutable[targetIndex] = item
        return copy(exercises = mutable)
    }

    fun adjustTargetSets(index: Int, delta: Int): RoutineBuilderState {
        if (index !in exercises.indices) return this
        val current = exercises[index]
        val nextSets = when {
            delta > 0 -> current.defaultTargetSets
                ?.let { (it + delta).coerceAtMost(RoutineExercise.MAX_DEFAULT_TARGET_SETS) }
                ?: RoutineExercise.MIN_DEFAULT_TARGET_SETS
            delta < 0 -> current.defaultTargetSets?.let { currentSets ->
                val candidate = currentSets + delta
                if (candidate < RoutineExercise.MIN_DEFAULT_TARGET_SETS) null else candidate
            }
            else -> current.defaultTargetSets
        }
        val mutable = exercises.toMutableList()
        mutable[index] = current.copy(defaultTargetSets = nextSets)
        return copy(exercises = mutable)
    }

    fun removeExercise(index: Int): RoutineBuilderState {
        if (index !in exercises.indices) return this
        val mutable = exercises.toMutableList()
        mutable.removeAt(index)
        return copy(exercises = mutable)
    }

    fun appendExercise(exerciseId: String): RoutineBuilderState {
        val draft = RoutineExerciseDraft(
            draftKey = UUID.randomUUID().toString(),
            routineExerciseId = null,
            exerciseId = exerciseId,
            defaultTargetSets = null,
        )
        return copy(
            exercises = exercises + draft,
            showExercisePicker = false,
            inlineExercise = null,
        )
    }
}

/** Combined UI state for the Workout tab (Routines + Exercise library). */
data class WorkoutUiState(
    val selectedSection: WorkoutTabSection = WorkoutTabSection.ROUTINES,
    val routines: List<Routine> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val exercisesById: Map<String, Exercise> = emptyMap(),
    val exerciseEditor: ExerciseEditorState? = null,
    val pendingDeleteExercise: Exercise? = null,
    val routineBuilder: RoutineBuilderState? = null,
    val pendingDeleteRoutine: Routine? = null,
)

/** User actions dispatched from the Workout tab UI. */
sealed interface WorkoutAction {
    data class SelectSection(val section: WorkoutTabSection) : WorkoutAction

    data object OpenCreateExercise : WorkoutAction
    data class OpenEditExercise(val exercise: Exercise) : WorkoutAction
    data class UpdateExerciseName(val name: String) : WorkoutAction
    data class UpdateExerciseDiscipline(val discipline: Discipline) : WorkoutAction
    data class UpdateExerciseTrackingMode(val trackingMode: TrackingMode) : WorkoutAction
    data class UpdateExerciseDefaultNotes(val notes: String) : WorkoutAction
    data object DismissExerciseEditor : WorkoutAction
    data object SaveExercise : WorkoutAction
    data class RequestDeleteExercise(val exercise: Exercise) : WorkoutAction
    data object DismissDeleteExercise : WorkoutAction
    data object ConfirmDeleteExercise : WorkoutAction

    data object OpenCreateRoutine : WorkoutAction
    data class OpenEditRoutine(val routine: Routine) : WorkoutAction
    data class UpdateRoutineName(val name: String) : WorkoutAction
    data class UpdateRoutineDiscipline(val discipline: Discipline) : WorkoutAction
    data class MoveRoutineExerciseUp(val index: Int) : WorkoutAction
    data class MoveRoutineExerciseDown(val index: Int) : WorkoutAction
    data class IncrementRoutineExerciseTargetSets(val index: Int) : WorkoutAction
    data class DecrementRoutineExerciseTargetSets(val index: Int) : WorkoutAction
    data class RemoveRoutineExercise(val index: Int) : WorkoutAction
    data object OpenExercisePicker : WorkoutAction
    data object CloseExercisePicker : WorkoutAction
    data class SelectPickerDisciplineFilter(val discipline: Discipline?) : WorkoutAction
    data class AddExistingExerciseToRoutine(val exerciseId: String) : WorkoutAction
    data object OpenInlineExerciseCreator : WorkoutAction
    data object CloseInlineExerciseCreator : WorkoutAction
    data class UpdateInlineExerciseName(val name: String) : WorkoutAction
    data class UpdateInlineExerciseDiscipline(val discipline: Discipline) : WorkoutAction
    data class UpdateInlineExerciseTrackingMode(val trackingMode: TrackingMode) : WorkoutAction
    data object SaveInlineExercise : WorkoutAction
    data object DismissRoutineBuilder : WorkoutAction
    data object SaveRoutine : WorkoutAction
    data class RequestDeleteRoutine(val routine: Routine) : WorkoutAction
    data object DismissDeleteRoutine : WorkoutAction
    data object ConfirmDeleteRoutine : WorkoutAction
}

/** Coordinates Workout tab state across Routines, Exercise library, and inline Exercise creation. */
class WorkoutViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
) : ViewModel() {
    private data class DataSnapshot(
        val exercises: List<Exercise>,
        val routines: List<Routine>,
        val recentlyCreatedExercises: Map<String, Exercise>,
    )

    private data class ModalSnapshot(
        val exerciseEditor: ExerciseEditorState?,
        val pendingDeleteExercise: Exercise?,
        val routineBuilder: RoutineBuilderState?,
        val pendingDeleteRoutine: Routine?,
    )

    private val selectedSection = MutableStateFlow(WorkoutTabSection.ROUTINES)
    private val recentlyCreatedExercises = MutableStateFlow<Map<String, Exercise>>(emptyMap())
    private val exerciseEditor = MutableStateFlow<ExerciseEditorState?>(null)
    private val pendingDeleteExercise = MutableStateFlow<Exercise?>(null)
    private val routineBuilder = MutableStateFlow<RoutineBuilderState?>(null)
    private val pendingDeleteRoutine = MutableStateFlow<Routine?>(null)

    private val dataFlow = combine(
        exerciseRepository.observeActiveExercises(),
        routineRepository.observeActiveRoutines(),
        recentlyCreatedExercises,
    ) { exercises, routines, recent ->
        DataSnapshot(
            exercises = exercises,
            routines = routines,
            recentlyCreatedExercises = recent,
        )
    }

    private val modalFlow = combine(
        exerciseEditor,
        pendingDeleteExercise,
        routineBuilder,
        pendingDeleteRoutine,
    ) { exEditor, exDelete, builder, rtDelete ->
        ModalSnapshot(
            exerciseEditor = exEditor,
            pendingDeleteExercise = exDelete,
            routineBuilder = builder,
            pendingDeleteRoutine = rtDelete,
        )
    }

    val uiState: StateFlow<WorkoutUiState> = combine(
        selectedSection,
        dataFlow,
        modalFlow,
    ) { section, data, modals ->
        val activeMap = data.exercises.associateBy { it.id }
        WorkoutUiState(
            selectedSection = section,
            routines = data.routines,
            exercises = data.exercises,
            exercisesById = data.recentlyCreatedExercises + activeMap,
            exerciseEditor = modals.exerciseEditor,
            pendingDeleteExercise = modals.pendingDeleteExercise,
            routineBuilder = modals.routineBuilder,
            pendingDeleteRoutine = modals.pendingDeleteRoutine,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkoutUiState(),
    )

    fun onAction(action: WorkoutAction) {
        when (action) {
            is WorkoutAction.SelectSection -> selectedSection.value = action.section

            WorkoutAction.OpenCreateExercise -> {
                exerciseEditor.value = ExerciseEditorState()
            }

            is WorkoutAction.OpenEditExercise -> {
                val exercise = action.exercise
                exerciseEditor.value = ExerciseEditorState(
                    editingExerciseId = exercise.id,
                    name = exercise.name,
                    discipline = exercise.discipline,
                    trackingMode = exercise.trackingMode,
                    defaultNotes = exercise.defaultNotes.orEmpty(),
                )
            }

            is WorkoutAction.UpdateExerciseName -> {
                exerciseEditor.update { it?.copy(name = action.name, nameError = null) }
            }

            is WorkoutAction.UpdateExerciseDiscipline -> {
                exerciseEditor.update { it?.copy(discipline = action.discipline) }
            }

            is WorkoutAction.UpdateExerciseTrackingMode -> {
                exerciseEditor.update { it?.copy(trackingMode = action.trackingMode) }
            }

            is WorkoutAction.UpdateExerciseDefaultNotes -> {
                exerciseEditor.update { it?.copy(defaultNotes = action.notes) }
            }

            WorkoutAction.DismissExerciseEditor -> {
                exerciseEditor.value = null
            }

            WorkoutAction.SaveExercise -> saveExercise()

            is WorkoutAction.RequestDeleteExercise -> {
                pendingDeleteExercise.value = action.exercise
            }

            WorkoutAction.DismissDeleteExercise -> {
                pendingDeleteExercise.value = null
            }

            WorkoutAction.ConfirmDeleteExercise -> {
                val target = pendingDeleteExercise.value ?: return
                recentlyCreatedExercises.update { it - target.id }
                launchSoftDelete(pendingDeleteExercise, target.id) { id ->
                    exerciseRepository.softDelete(id)
                }
            }

            WorkoutAction.OpenCreateRoutine -> {
                routineBuilder.value = RoutineBuilderState()
            }

            is WorkoutAction.OpenEditRoutine -> {
                val routine = action.routine
                routineBuilder.value = RoutineBuilderState(
                    editingRoutineId = routine.id,
                    name = routine.name,
                    discipline = routine.discipline,
                    exercises = routine.exercises.map { entry ->
                        RoutineExerciseDraft(
                            draftKey = entry.id,
                            routineExerciseId = entry.id,
                            exerciseId = entry.exerciseId,
                            defaultTargetSets = entry.defaultTargetSets,
                        )
                    },
                )
            }

            is WorkoutAction.UpdateRoutineName -> {
                routineBuilder.update { it?.copy(name = action.name, nameError = null) }
            }

            is WorkoutAction.UpdateRoutineDiscipline -> {
                routineBuilder.update { it?.copy(discipline = action.discipline) }
            }

            is WorkoutAction.MoveRoutineExerciseUp -> {
                routineBuilder.update { it?.moveExercise(action.index, delta = -1) }
            }

            is WorkoutAction.MoveRoutineExerciseDown -> {
                routineBuilder.update { it?.moveExercise(action.index, delta = 1) }
            }

            is WorkoutAction.IncrementRoutineExerciseTargetSets -> {
                routineBuilder.update { it?.adjustTargetSets(action.index, delta = 1) }
            }

            is WorkoutAction.DecrementRoutineExerciseTargetSets -> {
                routineBuilder.update { it?.adjustTargetSets(action.index, delta = -1) }
            }

            is WorkoutAction.RemoveRoutineExercise -> {
                routineBuilder.update { it?.removeExercise(action.index) }
            }

            WorkoutAction.OpenExercisePicker -> {
                routineBuilder.update {
                    it?.copy(
                        showExercisePicker = true,
                        pickerDisciplineFilter = null,
                        inlineExercise = null,
                    )
                }
            }

            WorkoutAction.CloseExercisePicker -> {
                routineBuilder.update { builder ->
                    if (builder?.inlineExercise?.isSaving == true) {
                        builder
                    } else {
                        builder?.copy(
                            showExercisePicker = false,
                            inlineExercise = null,
                        )
                    }
                }
            }

            is WorkoutAction.SelectPickerDisciplineFilter -> {
                routineBuilder.update { it?.copy(pickerDisciplineFilter = action.discipline) }
            }

            is WorkoutAction.AddExistingExerciseToRoutine -> {
                routineBuilder.update { it?.appendExercise(action.exerciseId) }
            }

            WorkoutAction.OpenInlineExerciseCreator -> {
                routineBuilder.update { builder ->
                    builder?.copy(
                        inlineExercise = InlineExerciseDraft(
                            discipline = builder.pickerDisciplineFilter ?: builder.discipline,
                        ),
                    )
                }
            }

            WorkoutAction.CloseInlineExerciseCreator -> {
                routineBuilder.update { builder ->
                    if (builder?.inlineExercise?.isSaving == true) {
                        builder
                    } else {
                        builder?.copy(inlineExercise = null)
                    }
                }
            }

            is WorkoutAction.UpdateInlineExerciseName -> {
                updateInlineExercise { it.copy(name = action.name, nameError = null) }
            }

            is WorkoutAction.UpdateInlineExerciseDiscipline -> {
                updateInlineExercise { it.copy(discipline = action.discipline) }
            }

            is WorkoutAction.UpdateInlineExerciseTrackingMode -> {
                updateInlineExercise { it.copy(trackingMode = action.trackingMode) }
            }

            WorkoutAction.SaveInlineExercise -> saveInlineExercise()

            WorkoutAction.DismissRoutineBuilder -> {
                routineBuilder.value = null
            }

            WorkoutAction.SaveRoutine -> saveRoutine()

            is WorkoutAction.RequestDeleteRoutine -> {
                pendingDeleteRoutine.value = action.routine
            }

            WorkoutAction.DismissDeleteRoutine -> {
                pendingDeleteRoutine.value = null
            }

            WorkoutAction.ConfirmDeleteRoutine -> {
                val target = pendingDeleteRoutine.value ?: return
                launchSoftDelete(pendingDeleteRoutine, target.id) { id ->
                    routineRepository.softDelete(id)
                }
            }
        }
    }

    private fun updateInlineExercise(transform: (InlineExerciseDraft) -> InlineExerciseDraft) {
        routineBuilder.update { builder ->
            builder?.copy(inlineExercise = builder.inlineExercise?.let(transform))
        }
    }

    private fun <T> launchSoftDelete(
        pendingFlow: MutableStateFlow<T?>,
        id: String,
        deleteAction: suspend (String) -> Unit,
    ) {
        pendingFlow.value = null
        viewModelScope.launch {
            try {
                deleteAction(id)
            } catch (_: NoSuchElementException) {
                // Record was already removed concurrently.
            }
        }
    }

    private fun saveExercise() {
        val current = exerciseEditor.value ?: return
        val trimmedName = current.name.trim()
        if (trimmedName.isEmpty()) {
            exerciseEditor.value = current.copy(nameError = "Exercise name is required.")
            return
        }

        val input = ExerciseInput(
            name = trimmedName,
            discipline = current.discipline,
            trackingMode = current.trackingMode,
            defaultNotes = current.defaultNotes,
        )
        val editingId = current.editingExerciseId
        exerciseEditor.value = null

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

    private fun saveInlineExercise() {
        val currentBuilder = routineBuilder.value ?: return
        val inline = currentBuilder.inlineExercise ?: return
        if (inline.isSaving) return

        val trimmedName = inline.name.trim()
        if (trimmedName.isEmpty()) {
            updateInlineExercise { it.copy(nameError = "Exercise name is required.") }
            return
        }

        updateInlineExercise { it.copy(isSaving = true, nameError = null) }

        viewModelScope.launch {
            val created = exerciseRepository.create(
                ExerciseInput(
                    name = trimmedName,
                    discipline = inline.discipline,
                    trackingMode = inline.trackingMode,
                )
            )
            recentlyCreatedExercises.update { it + (created.id to created) }
            routineBuilder.update { it?.appendExercise(created.id) }
        }
    }

    private fun saveRoutine() {
        val current = routineBuilder.value ?: return
        val trimmedName = current.name.trim()
        if (trimmedName.isEmpty()) {
            routineBuilder.value = current.copy(nameError = "Routine name is required.")
            return
        }

        val input = RoutineInput(
            name = trimmedName,
            discipline = current.discipline,
            exercises = current.exercises.map { draft ->
                RoutineExerciseInput(
                    exerciseId = draft.exerciseId,
                    defaultTargetSets = draft.defaultTargetSets,
                    id = draft.routineExerciseId,
                )
            },
        )
        val editingId = current.editingRoutineId
        routineBuilder.value = null

        viewModelScope.launch {
            if (editingId == null) {
                routineRepository.create(input)
            } else {
                try {
                    routineRepository.update(editingId, input)
                } catch (_: NoSuchElementException) {
                    // Routine was deleted before the edit saved.
                }
            }
        }
    }

    class Factory(
        private val exerciseRepository: ExerciseRepository,
        private val routineRepository: RoutineRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WorkoutViewModel::class.java))
            return WorkoutViewModel(exerciseRepository, routineRepository) as T
        }
    }
}
