package com.e4eheh.e4ehehworkout.ui.screens.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.Routine
import com.e4eheh.e4ehehworkout.domain.model.RoutineExercise
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionExercise
import com.e4eheh.e4ehehworkout.domain.model.SessionExerciseDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionSet
import com.e4eheh.e4ehehworkout.domain.model.SessionSetDraftInput
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.repository.ExerciseRepository
import com.e4eheh.e4ehehworkout.domain.repository.RoutineRepository
import com.e4eheh.e4ehehworkout.domain.repository.SessionRepository
import com.e4eheh.e4ehehworkout.domain.repository.UserPreferencesRepository
import com.e4eheh.e4ehehworkout.domain.timer.RestTimerController
import com.e4eheh.e4ehehworkout.domain.timer.RestTimerState
import com.e4eheh.e4ehehworkout.domain.unit.UnitConverter
import com.e4eheh.e4ehehworkout.ui.components.InlineExerciseDraft
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Draft representation of a single Set in an active live Session. */
data class ActiveSessionSetDraft(
    val id: String = UUID.randomUUID().toString(),
    val repsText: String = "",
    val loadText: String = "",
    val holdSecondsText: String = "",
    val distanceText: String = "",
    val durationMinText: String = "",
    val durationSecText: String = "",
    val roundsText: String = "",
    val loadKg: Double? = null,
    val distanceKm: Double? = null,
    val isCompleted: Boolean = false,
    val isWarmup: Boolean = false,
) {
    val isBodyweight: Boolean
        get() = (loadKg ?: 0.0) == 0.0

    fun reformatForUnits(
        trackingMode: TrackingMode,
        weightUnit: WeightUnit,
        distanceUnit: DistanceUnit,
    ): ActiveSessionSetDraft = when (trackingMode) {
        TrackingMode.REPS_AND_LOAD -> copy(
            loadText = if (loadKg != null && loadKg > 0.0) {
                UnitConverter.formatWeightValue(loadKg, weightUnit)
            } else {
                ""
            },
        )
        TrackingMode.DISTANCE_AND_TIME -> copy(
            distanceText = distanceKm?.let { UnitConverter.formatDistanceValue(it, distanceUnit) }
                .orEmpty(),
        )
        TrackingMode.HOLD_DURATION, TrackingMode.CONDITIONING_ROUNDS -> this
    }

    fun toDraftInput(trackingMode: TrackingMode): SessionSetDraftInput {
        val durationSeconds = when (trackingMode) {
            TrackingMode.HOLD_DURATION -> holdSecondsText.toIntOrNull()
            TrackingMode.DISTANCE_AND_TIME, TrackingMode.CONDITIONING_ROUNDS -> {
                if (durationMinText.isEmpty() && durationSecText.isEmpty()) {
                    null
                } else {
                    (durationMinText.toIntOrNull() ?: 0) * 60 +
                        (durationSecText.toIntOrNull() ?: 0)
                }
            }
            TrackingMode.REPS_AND_LOAD -> null
        }
        return SessionSetDraftInput(
            id = id,
            reps = repsText.toIntOrNull(),
            loadKg = loadKg,
            durationSeconds = durationSeconds,
            distanceKm = distanceKm,
            rounds = roundsText.toIntOrNull(),
            isCompleted = isCompleted,
            isWarmup = isWarmup,
        )
    }
}

/** Draft representation of an Exercise snapshot and its Sets in an active live Session. */
data class ActiveSessionExerciseDraft(
    val id: String,
    val exerciseId: String,
    val exerciseName: String,
    val exerciseDiscipline: Discipline,
    val trackingMode: TrackingMode,
    val notes: String = "",
    val sets: List<ActiveSessionSetDraft> = emptyList(),
) {
    fun reformatForUnits(
        weightUnit: WeightUnit,
        distanceUnit: DistanceUnit,
    ): ActiveSessionExerciseDraft = copy(
        sets = sets.map { it.reformatForUnits(trackingMode, weightUnit, distanceUnit) },
    )

    fun toDraftInput(): SessionExerciseDraftInput = SessionExerciseDraftInput(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        exerciseDiscipline = exerciseDiscipline,
        trackingMode = trackingMode,
        notes = notes,
        sets = sets.map { it.toDraftInput(trackingMode) },
    )

    fun targetSetCountForRoutine(): Int? =
        sets.size.takeIf { it > 0 }?.coerceAtMost(RoutineExercise.MAX_DEFAULT_TARGET_SETS)
}

/** Mutable in-progress Session draft holding raw input text alongside canonical metric values. */
data class ActiveSessionDraft(
    val title: String = "",
    val discipline: Discipline = Discipline.CALISTHENICS,
    val notes: String = "",
    val exercises: List<ActiveSessionExerciseDraft> = emptyList(),
) {
    fun addExercise(exercise: Exercise): ActiveSessionDraft {
        val entry = ActiveSessionExerciseDraft(
            id = UUID.randomUUID().toString(),
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            exerciseDiscipline = exercise.discipline,
            trackingMode = exercise.trackingMode,
            notes = "",
            sets = listOf(ActiveSessionSetDraft()),
        )
        return copy(exercises = exercises + entry)
    }

    fun moveExercise(exerciseIndex: Int, delta: Int): ActiveSessionDraft {
        val targetIndex = exerciseIndex + delta
        if (exerciseIndex !in exercises.indices || targetIndex !in exercises.indices) return this
        val mutable = exercises.toMutableList()
        val moved = mutable[exerciseIndex]
        mutable[exerciseIndex] = mutable[targetIndex]
        mutable[targetIndex] = moved
        return copy(exercises = mutable)
    }

    fun swapExercise(exerciseIndex: Int, newExercise: Exercise): ActiveSessionDraft {
        if (exerciseIndex !in exercises.indices) return this
        val existing = exercises[exerciseIndex]
        if (existing.exerciseId == newExercise.id) return this
        val replacement = existing.copy(
            exerciseId = newExercise.id,
            exerciseName = newExercise.name,
            exerciseDiscipline = newExercise.discipline,
            trackingMode = newExercise.trackingMode,
            notes = "",
            sets = List(existing.sets.size) { ActiveSessionSetDraft() },
        )
        val mutable = exercises.toMutableList()
        mutable[exerciseIndex] = replacement
        return copy(exercises = mutable)
    }

    fun removeExercise(exerciseIndex: Int): ActiveSessionDraft {
        if (exerciseIndex !in exercises.indices) return this
        val mutable = exercises.toMutableList()
        mutable.removeAt(exerciseIndex)
        return copy(exercises = mutable)
    }

    fun updateExerciseNotes(exerciseIndex: Int, notes: String): ActiveSessionDraft =
        updateExercise(exerciseIndex) { it.copy(notes = notes) }

    fun addSet(exerciseIndex: Int): ActiveSessionDraft =
        updateExercise(exerciseIndex) { it.copy(sets = it.sets + ActiveSessionSetDraft()) }

    fun removeSet(exerciseIndex: Int, setIndex: Int): ActiveSessionDraft =
        updateExercise(exerciseIndex) { exercise ->
            if (setIndex !in exercise.sets.indices) return@updateExercise exercise
            val mutable = exercise.sets.toMutableList()
            mutable.removeAt(setIndex)
            exercise.copy(sets = mutable)
        }

    fun findSet(exerciseIndex: Int, setIndex: Int): ActiveSessionSetDraft? =
        exercises.getOrNull(exerciseIndex)?.sets?.getOrNull(setIndex)

    fun updateSet(
        exerciseIndex: Int,
        setIndex: Int,
        transform: (ActiveSessionSetDraft) -> ActiveSessionSetDraft,
    ): ActiveSessionDraft = updateExercise(exerciseIndex) { exercise ->
        if (setIndex !in exercise.sets.indices) return@updateExercise exercise
        val mutable = exercise.sets.toMutableList()
        mutable[setIndex] = transform(mutable[setIndex])
        exercise.copy(sets = mutable)
    }

    fun reformatForUnits(
        weightUnit: WeightUnit,
        distanceUnit: DistanceUnit,
    ): ActiveSessionDraft = copy(
        exercises = exercises.map { it.reformatForUnits(weightUnit, distanceUnit) },
    )

    fun toDraftInput(): SessionDraftInput = SessionDraftInput(
        title = title,
        discipline = discipline,
        notes = notes,
        exercises = exercises.map { it.toDraftInput() },
    )

    private inline fun updateExercise(
        exerciseIndex: Int,
        transform: (ActiveSessionExerciseDraft) -> ActiveSessionExerciseDraft,
    ): ActiveSessionDraft {
        if (exerciseIndex !in exercises.indices) return this
        val mutable = exercises.toMutableList()
        mutable[exerciseIndex] = transform(mutable[exerciseIndex])
        return copy(exercises = mutable)
    }
}

/** Combined UI state for the full-screen active Session logger. */
data class ActiveSessionUiState(
    val sessionId: String = "",
    val routineId: String? = null,
    val isLoaded: Boolean = false,
    val isMissing: Boolean = false,
    val isCompleted: Boolean = false,
    val isFinishing: Boolean = false,
    val startedAt: Instant? = null,
    val elapsedSeconds: Long = 0L,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val defaultRestTimerSeconds: Int = UserPreferences.DEFAULT_REST_TIMER_SECONDS,
    val restTimer: RestTimerState = RestTimerState(),
    val draft: ActiveSessionDraft = ActiveSessionDraft(),
    val availableExercises: List<Exercise> = emptyList(),
    val showExercisePicker: Boolean = false,
    val swappingExerciseIndex: Int? = null,
    val pickerDisciplineFilter: Discipline? = null,
    val inlineExercise: InlineExerciseDraft? = null,
    val showUpdateRoutinePrompt: Boolean = false,
    val originatingRoutineName: String? = null,
) {
    val isEditable: Boolean
        get() = isLoaded && !isMissing && !isCompleted && !isFinishing && !showUpdateRoutinePrompt
}

/** User actions dispatched from the active Session logger and inline Exercise picker. */
sealed interface ActiveSessionAction {
    data class UpdateTitle(val title: String) : ActiveSessionAction
    data class UpdateDiscipline(val discipline: Discipline) : ActiveSessionAction
    data class UpdateNotes(val notes: String) : ActiveSessionAction

    data object OpenExercisePicker : ActiveSessionAction
    data class OpenSwapExercisePicker(val exerciseIndex: Int) : ActiveSessionAction
    data object CloseExercisePicker : ActiveSessionAction
    data class SelectPickerDisciplineFilter(val discipline: Discipline?) : ActiveSessionAction
    data class AddExistingExercise(val exerciseId: String) : ActiveSessionAction

    data object OpenInlineExerciseCreator : ActiveSessionAction
    data object CloseInlineExerciseCreator : ActiveSessionAction
    data class UpdateInlineExerciseName(val name: String) : ActiveSessionAction
    data class UpdateInlineExerciseDiscipline(val discipline: Discipline) : ActiveSessionAction
    data class UpdateInlineExerciseTrackingMode(val trackingMode: TrackingMode) : ActiveSessionAction
    data object SaveInlineExercise : ActiveSessionAction

    data class MoveExerciseUp(val exerciseIndex: Int) : ActiveSessionAction
    data class MoveExerciseDown(val exerciseIndex: Int) : ActiveSessionAction
    data class RemoveExercise(val exerciseIndex: Int) : ActiveSessionAction
    data class UpdateExerciseNotes(val exerciseIndex: Int, val notes: String) : ActiveSessionAction
    data class AddSet(val exerciseIndex: Int) : ActiveSessionAction
    data class RemoveSet(val exerciseIndex: Int, val setIndex: Int) : ActiveSessionAction
    data class ToggleSetCompleted(val exerciseIndex: Int, val setIndex: Int) : ActiveSessionAction
    data class ToggleSetWarmup(val exerciseIndex: Int, val setIndex: Int) : ActiveSessionAction
    data class UpdateSetReps(val exerciseIndex: Int, val setIndex: Int, val text: String) : ActiveSessionAction
    data class UpdateSetLoad(val exerciseIndex: Int, val setIndex: Int, val text: String) : ActiveSessionAction
    data class UpdateSetHoldSeconds(val exerciseIndex: Int, val setIndex: Int, val text: String) : ActiveSessionAction
    data class UpdateSetDistance(val exerciseIndex: Int, val setIndex: Int, val text: String) : ActiveSessionAction
    data class UpdateSetDurationMinutes(val exerciseIndex: Int, val setIndex: Int, val text: String) : ActiveSessionAction
    data class UpdateSetDurationSeconds(val exerciseIndex: Int, val setIndex: Int, val text: String) : ActiveSessionAction
    data class UpdateSetRounds(val exerciseIndex: Int, val setIndex: Int, val text: String) : ActiveSessionAction

    data object RefreshTimer : ActiveSessionAction
    data class AdjustRestTimer(val deltaSeconds: Int) : ActiveSessionAction
    data object SkipRestTimer : ActiveSessionAction
    data object FinishSession : ActiveSessionAction
    data object ConfirmFinishAndUpdateRoutine : ActiveSessionAction
    data object DeclineRoutineUpdateAndFinish : ActiveSessionAction
    data object DismissRoutineUpdatePrompt : ActiveSessionAction
}

/** Coordinates live Session logging, immediate IN_PROGRESS auto-save, unit conversion, and completion. */
class ActiveSessionViewModel(
    private val sessionId: String,
    private val sessionRepository: SessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val clock: Clock,
    private val routineRepository: RoutineRepository? = null,
) : ViewModel() {
    private val persistMutex = Mutex()
    private var lastPersistedInput: SessionDraftInput? = null
    private val restTimerController = RestTimerController(clock)
    private val internalState = MutableStateFlow(ActiveSessionUiState(sessionId = sessionId))

    val uiState: StateFlow<ActiveSessionUiState> = combine(
        internalState,
        exerciseRepository.observeActiveExercises(),
    ) { state, exercises ->
        state.copy(availableExercises = exercises)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ActiveSessionUiState(sessionId = sessionId),
    )

    init {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.getPreferences()
            val session = sessionRepository.getById(sessionId)
            internalState.update { current ->
                hydrateState(current, session, prefs)
            }

            userPreferencesRepository.observePreferences().collect { updatedPrefs ->
                internalState.update { current ->
                    if (!current.isLoaded ||
                        (current.weightUnit == updatedPrefs.weightUnit &&
                            current.distanceUnit == updatedPrefs.distanceUnit)
                    ) {
                        current.copy(
                            weightUnit = updatedPrefs.weightUnit,
                            distanceUnit = updatedPrefs.distanceUnit,
                            defaultRestTimerSeconds = updatedPrefs.defaultRestTimerSeconds,
                        )
                    } else {
                        current.copy(
                            weightUnit = updatedPrefs.weightUnit,
                            distanceUnit = updatedPrefs.distanceUnit,
                            defaultRestTimerSeconds = updatedPrefs.defaultRestTimerSeconds,
                            draft = current.draft.reformatForUnits(
                                updatedPrefs.weightUnit,
                                updatedPrefs.distanceUnit,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun onAction(action: ActiveSessionAction) {
        when (action) {
            is ActiveSessionAction.UpdateTitle ->
                mutateDraftAndAutoSave { it.copy(title = action.title) }

            is ActiveSessionAction.UpdateDiscipline ->
                mutateDraftAndAutoSave { it.copy(discipline = action.discipline) }

            is ActiveSessionAction.UpdateNotes ->
                mutateDraftAndAutoSave { it.copy(notes = action.notes) }

            ActiveSessionAction.OpenExercisePicker -> {
                if (!internalState.value.isEditable) return
                internalState.update {
                    it.copy(
                        showExercisePicker = true,
                        swappingExerciseIndex = null,
                        pickerDisciplineFilter = null,
                        inlineExercise = null,
                    )
                }
            }

            is ActiveSessionAction.OpenSwapExercisePicker -> {
                val state = internalState.value
                if (!state.isEditable || action.exerciseIndex !in state.draft.exercises.indices) return
                internalState.update {
                    it.copy(
                        showExercisePicker = true,
                        swappingExerciseIndex = action.exerciseIndex,
                        pickerDisciplineFilter = null,
                        inlineExercise = null,
                    )
                }
            }

            ActiveSessionAction.CloseExercisePicker -> {
                internalState.update { state ->
                    if (state.inlineExercise?.isSaving == true) {
                        state
                    } else {
                        state.copy(
                            showExercisePicker = false,
                            swappingExerciseIndex = null,
                            inlineExercise = null,
                        )
                    }
                }
            }

            is ActiveSessionAction.SelectPickerDisciplineFilter -> {
                internalState.update { it.copy(pickerDisciplineFilter = action.discipline) }
            }

            is ActiveSessionAction.AddExistingExercise -> {
                if (!internalState.value.isEditable) return
                val exercise = uiState.value.availableExercises.firstOrNull { it.id == action.exerciseId }
                    ?: return
                mutateStateAndAutoSave { state -> applyPickedExercise(state, exercise) }
            }

            ActiveSessionAction.OpenInlineExerciseCreator -> {
                if (!internalState.value.isEditable) return
                internalState.update { state ->
                    state.copy(
                        inlineExercise = InlineExerciseDraft(
                            discipline = state.pickerDisciplineFilter ?: state.draft.discipline,
                        ),
                    )
                }
            }

            ActiveSessionAction.CloseInlineExerciseCreator -> {
                internalState.update { state ->
                    if (state.inlineExercise?.isSaving == true) {
                        state
                    } else {
                        state.copy(inlineExercise = null)
                    }
                }
            }

            is ActiveSessionAction.UpdateInlineExerciseName -> {
                updateInlineExercise { it.copy(name = action.name, nameError = null) }
            }

            is ActiveSessionAction.UpdateInlineExerciseDiscipline -> {
                updateInlineExercise { it.copy(discipline = action.discipline) }
            }

            is ActiveSessionAction.UpdateInlineExerciseTrackingMode -> {
                updateInlineExercise { it.copy(trackingMode = action.trackingMode) }
            }

            ActiveSessionAction.SaveInlineExercise -> saveInlineExercise()

            is ActiveSessionAction.MoveExerciseUp ->
                mutateDraftAndAutoSave { it.moveExercise(action.exerciseIndex, delta = -1) }

            is ActiveSessionAction.MoveExerciseDown ->
                mutateDraftAndAutoSave { it.moveExercise(action.exerciseIndex, delta = 1) }

            is ActiveSessionAction.RemoveExercise ->
                mutateDraftAndAutoSave { it.removeExercise(action.exerciseIndex) }

            is ActiveSessionAction.UpdateExerciseNotes ->
                mutateDraftAndAutoSave { it.updateExerciseNotes(action.exerciseIndex, action.notes) }

            is ActiveSessionAction.AddSet ->
                mutateDraftAndAutoSave { it.addSet(action.exerciseIndex) }

            is ActiveSessionAction.RemoveSet ->
                mutateDraftAndAutoSave { it.removeSet(action.exerciseIndex, action.setIndex) }

            is ActiveSessionAction.ToggleSetCompleted -> {
                mutateStateAndAutoSave { state ->
                    val targetSet = state.draft.findSet(action.exerciseIndex, action.setIndex)
                        ?: return@mutateStateAndAutoSave state
                    val becomingCompleted = !targetSet.isCompleted
                    val updatedDraft = state.draft.updateSet(action.exerciseIndex, action.setIndex) { set ->
                        set.copy(isCompleted = !set.isCompleted)
                    }
                    val nextRestTimer = if (becomingCompleted) {
                        restTimerController.start(state.restTimer, state.defaultRestTimerSeconds)
                    } else {
                        state.restTimer
                    }
                    state.copy(
                        draft = updatedDraft,
                        restTimer = nextRestTimer,
                    )
                }
            }

            is ActiveSessionAction.ToggleSetWarmup ->
                mutateSetAndAutoSave(action.exerciseIndex, action.setIndex) { _, set ->
                    set.copy(isWarmup = !set.isWarmup)
                }

            is ActiveSessionAction.UpdateSetReps ->
                mutateSetAndAutoSave(action.exerciseIndex, action.setIndex) { _, set ->
                    set.copy(repsText = sanitizeNonNegativeInt(action.text))
                }

            is ActiveSessionAction.UpdateSetLoad ->
                mutateSetAndAutoSave(action.exerciseIndex, action.setIndex) { state, set ->
                    val sanitized = sanitizeNonNegativeDecimal(action.text)
                    val parsed = sanitized.toDoubleOrNull()
                    val canonicalKg = parsed?.let {
                        UnitConverter.weightToCanonicalKg(it, state.weightUnit)
                    }
                    set.copy(loadText = sanitized, loadKg = canonicalKg)
                }

            is ActiveSessionAction.UpdateSetHoldSeconds ->
                mutateSetAndAutoSave(action.exerciseIndex, action.setIndex) { _, set ->
                    set.copy(holdSecondsText = sanitizeNonNegativeInt(action.text))
                }

            is ActiveSessionAction.UpdateSetDistance ->
                mutateSetAndAutoSave(action.exerciseIndex, action.setIndex) { state, set ->
                    val sanitized = sanitizeNonNegativeDecimal(action.text)
                    val parsed = sanitized.toDoubleOrNull()
                    val canonicalKm = parsed?.let {
                        UnitConverter.distanceToCanonicalKm(it, state.distanceUnit)
                    }
                    set.copy(distanceText = sanitized, distanceKm = canonicalKm)
                }

            is ActiveSessionAction.UpdateSetDurationMinutes ->
                mutateSetAndAutoSave(action.exerciseIndex, action.setIndex) { _, set ->
                    set.copy(durationMinText = sanitizeNonNegativeInt(action.text))
                }

            is ActiveSessionAction.UpdateSetDurationSeconds ->
                mutateSetAndAutoSave(action.exerciseIndex, action.setIndex) { _, set ->
                    set.copy(durationSecText = sanitizeNonNegativeInt(action.text))
                }

            is ActiveSessionAction.UpdateSetRounds ->
                mutateSetAndAutoSave(action.exerciseIndex, action.setIndex) { _, set ->
                    set.copy(roundsText = sanitizeNonNegativeInt(action.text))
                }

            ActiveSessionAction.RefreshTimer -> {
                internalState.update { state ->
                    if (!state.isLoaded || state.isCompleted) {
                        state
                    } else {
                        state.copy(
                            elapsedSeconds = computeElapsedSeconds(state.startedAt),
                            restTimer = restTimerController.tick(state.restTimer),
                        )
                    }
                }
            }

            is ActiveSessionAction.AdjustRestTimer -> updateRestTimerIfEditable {
                restTimerController.adjustBy(it, action.deltaSeconds)
            }

            ActiveSessionAction.SkipRestTimer -> updateRestTimerIfEditable {
                restTimerController.skip(it)
            }

            ActiveSessionAction.FinishSession -> requestFinishSession()

            ActiveSessionAction.ConfirmFinishAndUpdateRoutine ->
                resolveRoutinePromptAndFinish(updateOriginatingRoutine = true)

            ActiveSessionAction.DeclineRoutineUpdateAndFinish ->
                resolveRoutinePromptAndFinish(updateOriginatingRoutine = false)

            ActiveSessionAction.DismissRoutineUpdatePrompt -> {
                internalState.update { state ->
                    if (state.isFinishing) state else state.copy(showUpdateRoutinePrompt = false)
                }
            }
        }
    }

    private inline fun updateRestTimerIfEditable(
        crossinline transform: (RestTimerState) -> RestTimerState,
    ) {
        if (!internalState.value.isEditable) return
        internalState.update { state ->
            if (!state.isEditable) {
                state
            } else {
                state.copy(restTimer = transform(state.restTimer))
            }
        }
    }

    private fun updateInlineExercise(transform: (InlineExerciseDraft) -> InlineExerciseDraft) {
        internalState.update { state ->
            state.copy(inlineExercise = state.inlineExercise?.let(transform))
        }
    }

    private inline fun mutateDraftAndAutoSave(
        crossinline transform: (ActiveSessionDraft) -> ActiveSessionDraft,
    ) {
        mutateStateAndAutoSave { state ->
            state.copy(draft = transform(state.draft))
        }
    }

    private inline fun mutateSetAndAutoSave(
        exerciseIndex: Int,
        setIndex: Int,
        crossinline transform: (ActiveSessionUiState, ActiveSessionSetDraft) -> ActiveSessionSetDraft,
    ) {
        mutateStateAndAutoSave { state ->
            state.copy(
                draft = state.draft.updateSet(exerciseIndex, setIndex) { set ->
                    transform(state, set)
                },
            )
        }
    }

    private inline fun mutateStateAndAutoSave(
        crossinline transform: (ActiveSessionUiState) -> ActiveSessionUiState,
    ) {
        if (!internalState.value.isEditable) return
        internalState.update { current ->
            if (!current.isEditable) {
                current
            } else {
                transform(current).copy(
                    elapsedSeconds = computeElapsedSeconds(current.startedAt),
                )
            }
        }
        launchAutoSave()
    }

    private fun launchAutoSave() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                persistMutex.withLock {
                    val snapshot = internalState.value
                    if (!snapshot.isEditable) {
                        return@withLock
                    }
                    val draftInput = snapshot.draft.toDraftInput()
                    if (draftInput == lastPersistedInput) {
                        return@withLock
                    }
                    try {
                        sessionRepository.saveDraft(sessionId, draftInput)
                        lastPersistedInput = draftInput
                    } catch (_: NoSuchElementException) {
                        // Session was deleted concurrently.
                    } catch (_: IllegalStateException) {
                        // Session was completed concurrently.
                    }
                }
            }
        }
    }

    private fun applyPickedExercise(
        state: ActiveSessionUiState,
        exercise: Exercise,
    ): ActiveSessionUiState {
        val swapIndex = state.swappingExerciseIndex
        val nextDraft = if (swapIndex != null) {
            state.draft.swapExercise(swapIndex, exercise)
        } else {
            state.draft.addExercise(exercise)
        }
        return state.copy(
            draft = nextDraft,
            showExercisePicker = false,
            swappingExerciseIndex = null,
            inlineExercise = null,
        )
    }

    private fun saveInlineExercise() {
        if (!internalState.value.isEditable) return
        val inline = internalState.value.inlineExercise ?: return
        if (inline.isSaving) return

        val trimmedName = inline.name.trim()
        if (trimmedName.isEmpty()) {
            updateInlineExercise { it.copy(nameError = "Exercise name is required.") }
            return
        }

        updateInlineExercise { it.copy(isSaving = true, nameError = null) }

        viewModelScope.launch {
            val created = try {
                exerciseRepository.create(
                    ExerciseInput(
                        name = trimmedName,
                        discipline = inline.discipline,
                        trackingMode = inline.trackingMode,
                    )
                )
            } catch (_: Exception) {
                updateInlineExercise { it.copy(isSaving = false) }
                return@launch
            }
            var shouldAutoSave = false
            internalState.update { current ->
                shouldAutoSave = current.isEditable
                if (!current.isEditable) {
                    current.copy(inlineExercise = null, swappingExerciseIndex = null)
                } else {
                    applyPickedExercise(current, created).copy(
                        elapsedSeconds = computeElapsedSeconds(current.startedAt),
                    )
                }
            }
            if (shouldAutoSave) {
                launchAutoSave()
            }
        }
    }

    private fun requestFinishSession() {
        if (!internalState.value.isEditable) return
        internalState.update { it.copy(isFinishing = true) }

        viewModelScope.launch {
            withContext(NonCancellable) {
                persistMutex.withLock {
                    val snapshot = internalState.value
                    if (snapshot.isCompleted) {
                        internalState.update { it.copy(isFinishing = false) }
                        return@withLock
                    }
                    val routineId = snapshot.routineId
                    if (routineId != null && routineRepository != null) {
                        val routine = routineRepository.getById(routineId)
                        if (routine != null && hasRoutineStructureChanged(routine, snapshot.draft)) {
                            internalState.update {
                                it.copy(
                                    isFinishing = false,
                                    showUpdateRoutinePrompt = true,
                                    originatingRoutineName = routine.name,
                                )
                            }
                            return@withLock
                        }
                    }
                    completeSessionLocked(snapshot, updateOriginatingRoutine = false)
                }
            }
        }
    }

    private fun resolveRoutinePromptAndFinish(updateOriginatingRoutine: Boolean) {
        val current = internalState.value
        if (!current.showUpdateRoutinePrompt || current.isFinishing) return
        internalState.update { it.copy(isFinishing = true, showUpdateRoutinePrompt = false) }

        viewModelScope.launch {
            withContext(NonCancellable) {
                persistMutex.withLock {
                    val snapshot = internalState.value
                    completeSessionLocked(snapshot, updateOriginatingRoutine = updateOriginatingRoutine)
                }
            }
        }
    }

    private suspend fun completeSessionLocked(
        snapshot: ActiveSessionUiState,
        updateOriginatingRoutine: Boolean,
    ) {
        if (snapshot.isCompleted) {
            internalState.update { it.copy(isFinishing = false, showUpdateRoutinePrompt = false) }
            return
        }
        try {
            if (updateOriginatingRoutine && snapshot.routineId != null && routineRepository != null) {
                val routine = routineRepository.getById(snapshot.routineId)
                if (routine != null) {
                    try {
                        routineRepository.update(
                            id = routine.id,
                            input = buildUpdatedRoutineInput(routine, snapshot.draft),
                        )
                    } catch (_: NoSuchElementException) {
                        // Originating Routine was deleted concurrently; still complete Session.
                    }
                }
            }
            val completed = sessionRepository.completeSession(
                id = sessionId,
                input = snapshot.draft.toDraftInput(),
            )
            internalState.update { current ->
                current.copy(
                    isFinishing = false,
                    isCompleted = true,
                    showUpdateRoutinePrompt = false,
                    elapsedSeconds = completed.durationSeconds,
                    restTimer = restTimerController.skip(current.restTimer),
                    draft = completed.toDraft(current.weightUnit, current.distanceUnit),
                )
            }
        } catch (_: NoSuchElementException) {
            internalState.update {
                it.copy(isFinishing = false, isMissing = true, showUpdateRoutinePrompt = false)
            }
        } catch (_: IllegalStateException) {
            internalState.update { current ->
                current.copy(
                    isFinishing = false,
                    isCompleted = true,
                    showUpdateRoutinePrompt = false,
                    restTimer = restTimerController.skip(current.restTimer),
                )
            }
        }
    }

    private fun hasRoutineStructureChanged(routine: Routine, draft: ActiveSessionDraft): Boolean {
        val routineStructure = routine.exercises.map { it.exerciseId to it.defaultTargetSets }
        val sessionStructure = draft.exercises.map {
            it.exerciseId to it.targetSetCountForRoutine()
        }
        return routineStructure != sessionStructure
    }

    private fun buildUpdatedRoutineInput(routine: Routine, draft: ActiveSessionDraft): RoutineInput {
        val existingIdsByExerciseId = HashMap<String, ArrayDeque<String>>()
        routine.exercises.forEach { entry ->
            existingIdsByExerciseId.getOrPut(entry.exerciseId) { ArrayDeque() }.addLast(entry.id)
        }
        val updatedExercises = draft.exercises.map { exerciseDraft ->
            val reusedId = existingIdsByExerciseId[exerciseDraft.exerciseId]?.removeFirstOrNull()
            RoutineExerciseInput(
                exerciseId = exerciseDraft.exerciseId,
                defaultTargetSets = exerciseDraft.targetSetCountForRoutine(),
                id = reusedId,
            )
        }
        return RoutineInput(
            name = routine.name,
            discipline = routine.discipline,
            exercises = updatedExercises,
        )
    }

    private fun hydrateState(
        current: ActiveSessionUiState,
        session: Session?,
        prefs: UserPreferences,
    ): ActiveSessionUiState {
        if (session == null) {
            return current.copy(
                isLoaded = true,
                isMissing = true,
                weightUnit = prefs.weightUnit,
                distanceUnit = prefs.distanceUnit,
                defaultRestTimerSeconds = prefs.defaultRestTimerSeconds,
            )
        }
        val completed = session.status == SessionStatus.COMPLETED
        val elapsed = if (completed) {
            session.durationSeconds
        } else {
            computeElapsedSeconds(session.startedAt)
        }
        return current.copy(
            routineId = session.routineId,
            isLoaded = true,
            isMissing = false,
            isCompleted = completed,
            startedAt = session.startedAt,
            elapsedSeconds = elapsed,
            weightUnit = prefs.weightUnit,
            distanceUnit = prefs.distanceUnit,
            defaultRestTimerSeconds = prefs.defaultRestTimerSeconds,
            draft = session.toDraft(prefs.weightUnit, prefs.distanceUnit),
        )
    }

    private fun Session.toDraft(
        weightUnit: WeightUnit,
        distanceUnit: DistanceUnit,
    ): ActiveSessionDraft = ActiveSessionDraft(
        title = title,
        discipline = discipline,
        notes = notes.orEmpty(),
        exercises = exercises.map { it.toDraft(weightUnit, distanceUnit) },
    )

    private fun SessionExercise.toDraft(
        weightUnit: WeightUnit,
        distanceUnit: DistanceUnit,
    ): ActiveSessionExerciseDraft = ActiveSessionExerciseDraft(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        exerciseDiscipline = exerciseDiscipline,
        trackingMode = trackingMode,
        notes = notes.orEmpty(),
        sets = sets.map { it.toDraft(trackingMode, weightUnit, distanceUnit) },
    )

    private fun SessionSet.toDraft(
        trackingMode: TrackingMode,
        weightUnit: WeightUnit,
        distanceUnit: DistanceUnit,
    ): ActiveSessionSetDraft {
        val showMinutesAndSeconds = trackingMode == TrackingMode.DISTANCE_AND_TIME ||
            trackingMode == TrackingMode.CONDITIONING_ROUNDS
        val totalDuration = if (showMinutesAndSeconds) durationSeconds else null
        return ActiveSessionSetDraft(
            id = id,
            repsText = reps?.toString().orEmpty(),
            holdSecondsText = if (trackingMode == TrackingMode.HOLD_DURATION) {
                durationSeconds?.toString().orEmpty()
            } else {
                ""
            },
            durationMinText = totalDuration?.let { (it / 60).toString() }.orEmpty(),
            durationSecText = totalDuration?.let { (it % 60).toString() }.orEmpty(),
            roundsText = rounds?.toString().orEmpty(),
            loadKg = loadKg,
            distanceKm = distanceKm,
            isCompleted = isCompleted,
            isWarmup = isWarmup,
        ).reformatForUnits(trackingMode, weightUnit, distanceUnit)
    }

    private fun computeElapsedSeconds(startedAt: Instant?): Long =
        startedAt?.let { Duration.between(it, clock.instant()).seconds.coerceAtLeast(0L) } ?: 0L

    private fun sanitizeNonNegativeInt(raw: String): String =
        raw.filter { it.isDigit() }.take(6)

    private fun sanitizeNonNegativeDecimal(raw: String): String {
        val builder = StringBuilder()
        var seenDecimal = false
        for (ch in raw) {
            when {
                ch.isDigit() -> builder.append(ch)
                ch == '.' && !seenDecimal -> {
                    seenDecimal = true
                    builder.append(ch)
                }
            }
            if (builder.length >= 9) break
        }
        return builder.toString()
    }

    class Factory(
        private val sessionId: String,
        private val sessionRepository: SessionRepository,
        private val exerciseRepository: ExerciseRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val clock: Clock,
        private val routineRepository: RoutineRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ActiveSessionViewModel::class.java))
            return ActiveSessionViewModel(
                sessionId = sessionId,
                sessionRepository = sessionRepository,
                exerciseRepository = exerciseRepository,
                userPreferencesRepository = userPreferencesRepository,
                clock = clock,
                routineRepository = routineRepository,
            ) as T
        }
    }
}
