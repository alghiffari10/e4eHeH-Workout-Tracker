package com.e4eheh.e4ehehworkout.ui.screens.session

import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionExercise
import com.e4eheh.e4ehehworkout.domain.model.SessionSet
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit

/** Maps a persisted Session snapshot into the editable HUD draft used by live logging and Session detail. */
internal fun Session.toEditorDraft(
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
): ActiveSessionDraft = ActiveSessionDraft(
    title = title,
    discipline = discipline,
    notes = notes.orEmpty(),
    exercises = exercises.map { it.toEditorDraft(weightUnit, distanceUnit) },
)

internal fun SessionExercise.toEditorDraft(
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
): ActiveSessionExerciseDraft = ActiveSessionExerciseDraft(
    id = id,
    exerciseId = exerciseId,
    exerciseName = exerciseName,
    exerciseDiscipline = exerciseDiscipline,
    trackingMode = trackingMode,
    notes = notes.orEmpty(),
    sets = sets.map { it.toEditorDraft(trackingMode, weightUnit, distanceUnit) },
)

internal fun SessionSet.toEditorDraft(
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
