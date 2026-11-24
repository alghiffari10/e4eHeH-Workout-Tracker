package com.e4eheh.e4ehehworkout.ui.screens.session

import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.unit.UnitConverter
import com.e4eheh.e4ehehworkout.ui.components.sanitizeNonNegativeDecimal
import com.e4eheh.e4ehehworkout.ui.components.sanitizeNonNegativeInt

/**
 * Applies draft-mutating editor actions shared by live Session logging and completed Session detail.
 *
 * Actions that depend on screen state (Exercise picker, auto-save, timers, persistence) are ignored so
 * callers can handle those separately; the same draft instance is returned when nothing changed.
 */
internal fun ActiveSessionDraft.applyEditorAction(
    action: ActiveSessionAction,
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
    newSetsCompleted: Boolean = false,
): ActiveSessionDraft = when (action) {
    is ActiveSessionAction.UpdateTitle -> copy(title = action.title)
    is ActiveSessionAction.UpdateDiscipline -> copy(discipline = action.discipline)
    is ActiveSessionAction.UpdateNotes -> copy(notes = action.notes)

    is ActiveSessionAction.MoveExerciseUp -> moveExercise(action.exerciseIndex, moveUp = true)
    is ActiveSessionAction.MoveExerciseDown -> moveExercise(action.exerciseIndex, moveUp = false)
    is ActiveSessionAction.RemoveExercise -> removeExercise(action.exerciseIndex)
    is ActiveSessionAction.UpdateExerciseNotes ->
        updateExerciseNotes(action.exerciseIndex, action.notes)

    is ActiveSessionAction.AddSet -> addSet(action.exerciseIndex, isCompleted = newSetsCompleted)
    is ActiveSessionAction.RemoveSet -> removeSet(action.exerciseIndex, action.setIndex)
    is ActiveSessionAction.ToggleSetCompleted ->
        updateSet(action.exerciseIndex, action.setIndex) { it.copy(isCompleted = !it.isCompleted) }
    is ActiveSessionAction.ToggleSetWarmup ->
        updateSet(action.exerciseIndex, action.setIndex) { it.copy(isWarmup = !it.isWarmup) }

    is ActiveSessionAction.UpdateSetReps ->
        updateSet(action.exerciseIndex, action.setIndex) {
            it.copy(repsText = sanitizeNonNegativeInt(action.text))
        }
    is ActiveSessionAction.UpdateSetLoad ->
        updateSet(action.exerciseIndex, action.setIndex) { set ->
            val sanitized = sanitizeNonNegativeDecimal(action.text)
            set.copy(
                loadText = sanitized,
                loadKg = sanitized.toDoubleOrNull()?.let {
                    UnitConverter.weightToCanonicalKg(it, weightUnit)
                },
            )
        }
    is ActiveSessionAction.UpdateSetHoldSeconds ->
        updateSet(action.exerciseIndex, action.setIndex) {
            it.copy(holdSecondsText = sanitizeNonNegativeInt(action.text))
        }
    is ActiveSessionAction.UpdateSetDistance ->
        updateSet(action.exerciseIndex, action.setIndex) { set ->
            val sanitized = sanitizeNonNegativeDecimal(action.text)
            set.copy(
                distanceText = sanitized,
                distanceKm = sanitized.toDoubleOrNull()?.let {
                    UnitConverter.distanceToCanonicalKm(it, distanceUnit)
                },
            )
        }
    is ActiveSessionAction.UpdateSetDurationMinutes ->
        updateSet(action.exerciseIndex, action.setIndex) {
            it.copy(durationMinText = sanitizeNonNegativeInt(action.text))
        }
    is ActiveSessionAction.UpdateSetDurationSeconds ->
        updateSet(action.exerciseIndex, action.setIndex) {
            it.copy(durationSecText = sanitizeNonNegativeInt(action.text))
        }
    is ActiveSessionAction.UpdateSetRounds ->
        updateSet(action.exerciseIndex, action.setIndex) {
            it.copy(roundsText = sanitizeNonNegativeInt(action.text))
        }

    else -> this
}
