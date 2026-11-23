package com.e4eheh.e4ehehworkout.ui.screens.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.Routine
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.repository.ExerciseRepository
import com.e4eheh.e4ehehworkout.domain.repository.RoutineRepository
import com.e4eheh.e4ehehworkout.domain.repository.SessionRepository
import com.e4eheh.e4ehehworkout.domain.repository.UserPreferencesRepository
import com.e4eheh.e4ehehworkout.ui.components.InlineExerciseDraft
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which lifecycle entry point opened the shared Session editor. */
enum class SessionEditorMode {
    /** Logging a completed Session for an arbitrary past date and time. */
    CREATE_BACKDATED,

    /** Reviewing or editing an existing `COMPLETED` Session. */
    DETAIL,
}

/** Combined UI state for the backdated Session logger and completed Session detail/editor. */
data class SessionEditorUiState(
    val sessionId: String? = null,
    val routineId: String? = null,
    val mode: SessionEditorMode = SessionEditorMode.CREATE_BACKDATED,
    val isLoaded: Boolean = false,
    val isMissing: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
    val showDeleteConfirmation: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null,
    val dateText: String = "",
    val timeText: String = "",
    val durationMinText: String = "",
    val durationSecText: String = "",
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val draft: ActiveSessionDraft = ActiveSessionDraft(),
    val availableExercises: List<Exercise> = emptyList(),
    val availableRoutines: List<Routine> = emptyList(),
    val showExercisePicker: Boolean = false,
    val swappingExerciseIndex: Int? = null,
    val pickerDisciplineFilter: Discipline? = null,
    val inlineExercise: InlineExerciseDraft? = null,
) {
    val isEditable: Boolean
        get() = isLoaded && !isMissing && !isSaving && !isDeleting && !isDeleted

    val isDetail: Boolean
        get() = mode == SessionEditorMode.DETAIL
}

/** User actions dispatched from the backdated logger and completed Session detail screen. */
sealed interface SessionEditorScreenAction {
    data class Edit(val action: ActiveSessionAction) : SessionEditorScreenAction
    data class ApplyRoutine(val routineId: String) : SessionEditorScreenAction
    data class UpdateDate(val dateText: String) : SessionEditorScreenAction
    data class UpdateTime(val timeText: String) : SessionEditorScreenAction
    data class UpdateDurationMinutes(val text: String) : SessionEditorScreenAction
    data class UpdateDurationSeconds(val text: String) : SessionEditorScreenAction
    data object Save : SessionEditorScreenAction
    data object RequestDelete : SessionEditorScreenAction
    data object DismissDelete : SessionEditorScreenAction
    data object ConfirmDelete : SessionEditorScreenAction
}

/**
 * Coordinates backdated `Session` creation and completed `Session` editing/deletion.
 *
 * Exercises, Sets, notes, units, and inline Exercise creation reuse the same draft mutations as the
 * live logger, but no live workout timer or Rest Timer is ever started.
 */
class SessionEditorViewModel(
    private val sessionId: String?,
    private val sessionRepository: SessionRepository,
    private val exerciseRepository: ExerciseRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val clock: Clock,
    private val routineRepository: RoutineRepository? = null,
) : ViewModel() {
    private val internalState = MutableStateFlow(SessionEditorUiState(sessionId = sessionId))

    val uiState: StateFlow<SessionEditorUiState> = combine(
        internalState,
        exerciseRepository.observeActiveExercises(),
        routineRepository?.observeActiveRoutines() ?: flowOf(emptyList()),
    ) { state, exercises, routines ->
        state.copy(
            availableExercises = exercises,
            availableRoutines = routines,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SessionEditorUiState(sessionId = sessionId),
    )

    init {
        viewModelScope.launch {
            val prefs = userPreferencesRepository.getPreferences()
            val session = sessionId?.let { sessionRepository.getById(it) }
            internalState.update { current -> hydrateState(current, session, prefs) }

            userPreferencesRepository.observePreferences().collect { updatedPrefs ->
                internalState.update { current ->
                    if (!current.isLoaded ||
                        (current.weightUnit == updatedPrefs.weightUnit &&
                            current.distanceUnit == updatedPrefs.distanceUnit)
                    ) {
                        current.copy(
                            weightUnit = updatedPrefs.weightUnit,
                            distanceUnit = updatedPrefs.distanceUnit,
                        )
                    } else {
                        current.copy(
                            weightUnit = updatedPrefs.weightUnit,
                            distanceUnit = updatedPrefs.distanceUnit,
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

    fun onAction(action: SessionEditorScreenAction) {
        when (action) {
            is SessionEditorScreenAction.Edit -> onEditAction(action.action)

            is SessionEditorScreenAction.ApplyRoutine -> applyRoutine(action.routineId)

            is SessionEditorScreenAction.UpdateDate ->
                updateIfEditable { it.copy(dateText = sanitizeDateText(action.dateText)) }

            is SessionEditorScreenAction.UpdateTime ->
                updateIfEditable { it.copy(timeText = sanitizeTimeText(action.timeText)) }

            is SessionEditorScreenAction.UpdateDurationMinutes ->
                updateIfEditable {
                    it.copy(durationMinText = sanitizeNonNegativeInt(action.text))
                }

            is SessionEditorScreenAction.UpdateDurationSeconds ->
                updateIfEditable {
                    it.copy(durationSecText = sanitizeNonNegativeInt(action.text))
                }

            SessionEditorScreenAction.Save -> saveSession()

            SessionEditorScreenAction.RequestDelete -> {
                val state = internalState.value
                if (state.isEditable && state.sessionId != null) {
                    internalState.update { it.copy(showDeleteConfirmation = true) }
                }
            }

            SessionEditorScreenAction.DismissDelete -> {
                if (!internalState.value.isDeleting) {
                    internalState.update { it.copy(showDeleteConfirmation = false) }
                }
            }

            SessionEditorScreenAction.ConfirmDelete -> deleteSession()
        }
    }

    private fun onEditAction(action: ActiveSessionAction) {
        if (!internalState.value.isEditable) return

        when (action) {
            ActiveSessionAction.OpenExercisePicker -> {
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
                if (action.exerciseIndex !in state.draft.exercises.indices) return
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
                val exercise = uiState.value.availableExercises
                    .firstOrNull { it.id == action.exerciseId }
                    ?: return
                updateIfEditable { applyPickedExercise(it, exercise) }
            }

            ActiveSessionAction.OpenInlineExerciseCreator -> {
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

            else -> updateIfEditable { state ->
                state.copy(
                    draft = state.draft.applyEditorAction(
                        action = action,
                        weightUnit = state.weightUnit,
                        distanceUnit = state.distanceUnit,
                        newSetsCompleted = true,
                    ),
                    errorMessage = null,
                )
            }
        }
    }

    private fun applyRoutine(routineId: String) {
        val currentState = uiState.value
        if (!currentState.isEditable || currentState.isDetail) return
        if (currentState.routineId == routineId) {
            updateIfEditable { state ->
                state.copy(
                    routineId = null,
                    draft = state.draft.copy(title = "", exercises = emptyList()),
                )
            }
            return
        }
        val routine = currentState.availableRoutines.firstOrNull { it.id == routineId } ?: return
        val exercisesById = currentState.availableExercises.associateBy { it.id }
        val draftExercises = routine.exercises.mapNotNull { entry ->
            val exercise = exercisesById[entry.exerciseId] ?: return@mapNotNull null
            val setCount = entry.defaultTargetSets ?: 0
            ActiveSessionExerciseDraft(
                id = UUID.randomUUID().toString(),
                exerciseId = exercise.id,
                exerciseName = exercise.name,
                exerciseDiscipline = exercise.discipline,
                trackingMode = exercise.trackingMode,
                notes = "",
                sets = List(setCount) { ActiveSessionSetDraft(isCompleted = true) },
            )
        }
        updateIfEditable { state ->
            state.copy(
                routineId = routine.id,
                draft = state.draft.copy(
                    title = routine.name,
                    discipline = routine.discipline,
                    exercises = draftExercises,
                ),
            )
        }
    }

    private fun saveSession() {
        val state = internalState.value
        if (!state.isEditable) return

        val startedAt = resolveStartedAt(state) ?: return
        val durationSeconds = resolveDurationSeconds(state)
        val input = state.draft.toDraftInput()
        internalState.update { it.copy(isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val saved = if (state.sessionId == null) {
                    sessionRepository.createCompletedSession(
                        startedAt = startedAt,
                        durationSeconds = durationSeconds,
                        input = input,
                        routineId = state.routineId,
                    )
                } else {
                    sessionRepository.updateCompletedSession(
                        id = state.sessionId,
                        startedAt = startedAt,
                        durationSeconds = durationSeconds,
                        input = input,
                    )
                }
                internalState.update { current ->
                    hydrateDetail(current, saved).copy(isSaving = false, errorMessage = null)
                }
            } catch (_: Exception) {
                internalState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = "Unable to save Session. Please try again.",
                    )
                }
            }
        }
    }

    private fun deleteSession() {
        val state = internalState.value
        val id = state.sessionId ?: return
        if (state.isDeleting) return
        internalState.update {
            it.copy(isDeleting = true, showDeleteConfirmation = false, errorMessage = null)
        }

        viewModelScope.launch {
            try {
                sessionRepository.softDelete(id)
                internalState.update { it.copy(isDeleting = false, isDeleted = true) }
            } catch (_: Exception) {
                internalState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = "Unable to delete Session. Please try again.",
                    )
                }
            }
        }
    }

    private fun resolveStartedAt(state: SessionEditorUiState): Instant? {
        val date = try {
            LocalDate.parse(state.dateText.trim(), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            null
        }
        if (date == null) {
            internalState.update { it.copy(errorMessage = "Enter a valid date (YYYY-MM-DD).") }
            return null
        }

        val time = try {
            LocalTime.parse(state.timeText.trim(), TIME_FORMATTER)
        } catch (_: DateTimeParseException) {
            null
        }
        if (time == null) {
            internalState.update { it.copy(errorMessage = "Enter a valid time (HH:MM).") }
            return null
        }

        val startedAt = LocalDateTime.of(date, time).atZone(clock.zone).toInstant()
        if (startedAt.isAfter(clock.instant())) {
            internalState.update { it.copy(errorMessage = "Session date/time cannot be in the future.") }
            return null
        }
        return startedAt
    }

    private fun resolveDurationSeconds(state: SessionEditorUiState): Long {
        val minutes = state.durationMinText.toIntOrNull() ?: 0
        val seconds = state.durationSecText.toIntOrNull() ?: 0
        return minutes * 60L + seconds
    }

    private fun applyPickedExercise(
        state: SessionEditorUiState,
        exercise: Exercise,
    ): SessionEditorUiState {
        val swapIndex = state.swappingExerciseIndex
        val nextDraft = if (swapIndex != null) {
            state.draft.swapExercise(swapIndex, exercise, setsCompleted = true)
        } else {
            state.draft.addExercise(exercise, setsCompleted = true)
        }
        return state.copy(
            draft = nextDraft,
            showExercisePicker = false,
            swappingExerciseIndex = null,
            inlineExercise = null,
            errorMessage = null,
        )
    }

    private fun saveInlineExercise() {
        val state = internalState.value
        if (!state.isEditable) return
        val inline = state.inlineExercise ?: return
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
            internalState.update { current ->
                if (!current.isEditable) {
                    current.copy(inlineExercise = null, swappingExerciseIndex = null)
                } else {
                    applyPickedExercise(current, created)
                }
            }
        }
    }

    private fun updateInlineExercise(transform: (InlineExerciseDraft) -> InlineExerciseDraft) {
        internalState.update { state ->
            state.copy(inlineExercise = state.inlineExercise?.let(transform))
        }
    }

    private inline fun updateIfEditable(
        crossinline transform: (SessionEditorUiState) -> SessionEditorUiState,
    ) {
        internalState.update { state ->
            if (state.isEditable) {
                transform(state).copy(errorMessage = null)
            } else {
                state
            }
        }
    }

    private fun hydrateState(
        current: SessionEditorUiState,
        session: Session?,
        prefs: UserPreferences,
    ): SessionEditorUiState {
        val base = current.copy(
            weightUnit = prefs.weightUnit,
            distanceUnit = prefs.distanceUnit,
        )
        if (sessionId == null) {
            val now = clock.instant().atZone(clock.zone)
            return base.copy(
                mode = SessionEditorMode.CREATE_BACKDATED,
                isLoaded = true,
                isMissing = false,
                dateText = now.toLocalDate().toString(),
                timeText = formatLocalTime(now.toLocalTime()),
                durationMinText = "",
                durationSecText = "",
                draft = ActiveSessionDraft(),
            )
        }
        if (session == null || session.status != SessionStatus.COMPLETED) {
            return base.copy(
                mode = SessionEditorMode.DETAIL,
                isLoaded = true,
                isMissing = true,
            )
        }
        return hydrateDetail(base, session)
    }

    private fun hydrateDetail(
        current: SessionEditorUiState,
        session: Session,
    ): SessionEditorUiState {
        val localStart = session.startedAt.atZone(clock.zone)
        return current.copy(
            sessionId = session.id,
            routineId = session.routineId,
            mode = SessionEditorMode.DETAIL,
            isLoaded = true,
            isMissing = false,
            dateText = localStart.toLocalDate().toString(),
            timeText = formatLocalTime(localStart.toLocalTime()),
            durationMinText = (session.durationSeconds / 60L).toString(),
            durationSecText = (session.durationSeconds % 60L).toString(),
            draft = session.toEditorDraft(current.weightUnit, current.distanceUnit),
            showExercisePicker = false,
            swappingExerciseIndex = null,
            inlineExercise = null,
        )
    }

    private fun formatLocalTime(time: LocalTime): String =
        String.format(Locale.US, "%02d:%02d", time.hour, time.minute)

    private fun sanitizeDateText(raw: String): String =
        raw.filter { it.isDigit() || it == '-' }.take(10)

    private fun sanitizeTimeText(raw: String): String =
        raw.filter { it.isDigit() || it == ':' }.take(5)

    class Factory(
        private val sessionId: String?,
        private val sessionRepository: SessionRepository,
        private val exerciseRepository: ExerciseRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val clock: Clock,
        private val routineRepository: RoutineRepository? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SessionEditorViewModel::class.java))
            return SessionEditorViewModel(
                sessionId = sessionId,
                sessionRepository = sessionRepository,
                exerciseRepository = exerciseRepository,
                userPreferencesRepository = userPreferencesRepository,
                clock = clock,
                routineRepository = routineRepository,
            ) as T
        }
    }

    private companion object {
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
    }
}
