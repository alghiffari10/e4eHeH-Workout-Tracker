package com.e4eheh.e4ehehworkout.ui.screens.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.ui.components.ArkhamChipSelectorSection
import com.e4eheh.e4ehehworkout.ui.components.ArkhamDeleteConfirmationDialog
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.arkhamTextFieldColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamShapes
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle

/** Form state for creating or editing an Exercise on the Workout tab. */
data class ExerciseEditorState(
    val editingExerciseId: String? = null,
    val name: String = "",
    val discipline: Discipline = Discipline.CALISTHENICS,
    val trackingMode: TrackingMode = TrackingMode.REPS_AND_LOAD,
    val defaultNotes: String = "",
    val nameError: String? = null,
)

/** Reusable Exercise input fields shared between the library modal and inline Routine picker creator. */
@Composable
fun ExerciseFormFields(
    name: String,
    nameError: String?,
    discipline: Discipline,
    trackingMode: TrackingMode,
    defaultNotes: String,
    showNotesField: Boolean,
    onNameChange: (String) -> Unit,
    onDisciplineChange: (Discipline) -> Unit,
    onTrackingModeChange: (TrackingMode) -> Unit,
    onNotesChange: (String) -> Unit,
    nameTestTag: String,
    notesTestTag: String? = null,
    disciplineTestTagOf: ((Discipline) -> String)? = null,
    trackingModeTestTagOf: ((TrackingMode) -> String)? = null,
) {
    val fieldColors = arkhamTextFieldColors()

    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("Exercise Name") },
        singleLine = true,
        isError = nameError != null,
        supportingText = nameError?.let { error ->
            {
                Text(
                    text = error,
                    color = ArkhamColors.AlertRed,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        },
        shape = ArkhamShapes.chip,
        colors = fieldColors,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(nameTestTag),
    )

    ArkhamChipSelectorSection(
        title = "DISCIPLINE",
        options = Discipline.entries,
        selected = discipline,
        labelOf = { it.displayName },
        onSelect = onDisciplineChange,
        testTagOf = disciplineTestTagOf,
    )

    ArkhamChipSelectorSection(
        title = "TRACKING MODE",
        options = TrackingMode.entries,
        selected = trackingMode,
        labelOf = { it.displayName },
        onSelect = onTrackingModeChange,
        testTagOf = trackingModeTestTagOf,
    )

    if (showNotesField) {
        OutlinedTextField(
            value = defaultNotes,
            onValueChange = onNotesChange,
            label = { Text("Default Notes (Optional)") },
            minLines = 2,
            maxLines = 4,
            shape = ArkhamShapes.chip,
            colors = fieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .let { base -> if (notesTestTag != null) base.testTag(notesTestTag) else base },
        )
    }
}

/** Modal chamfered HUD dialog for creating or editing an Exercise. */
@Composable
fun ExerciseEditorDialog(
    state: ExerciseEditorState,
    onNameChange: (String) -> Unit,
    onDisciplineChange: (Discipline) -> Unit,
    onTrackingModeChange: (TrackingMode) -> Unit,
    onNotesChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ArkhamHudPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            highlighted = true,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (state.editingExerciseId == null) "CREATE EXERCISE" else "EDIT EXERCISE",
                    style = ArkhamTelemetryLargeStyle,
                    color = ArkhamColors.PhosphorGreen,
                )

                ExerciseFormFields(
                    name = state.name,
                    nameError = state.nameError,
                    discipline = state.discipline,
                    trackingMode = state.trackingMode,
                    defaultNotes = state.defaultNotes,
                    showNotesField = true,
                    onNameChange = onNameChange,
                    onDisciplineChange = onDisciplineChange,
                    onTrackingModeChange = onTrackingModeChange,
                    onNotesChange = onNotesChange,
                    nameTestTag = "exercise_name_input",
                    notesTestTag = "exercise_notes_input",
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ArkhamSecondaryButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    ArkhamPrimaryButton(
                        text = "Save Exercise",
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Confirmation modal before soft-deleting an Exercise from the library. */
@Composable
fun DeleteExerciseDialog(
    exercise: Exercise,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ArkhamDeleteConfirmationDialog(
        title = "DELETE EXERCISE?",
        itemName = exercise.name,
        message = "Removes this Exercise from your active catalog while preserving sync-ready soft-delete history.",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}
