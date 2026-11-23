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
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.arkhamTextFieldColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamShapes
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle

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
    val fieldColors = arkhamTextFieldColors()

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

                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Exercise Name") },
                    singleLine = true,
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { error ->
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
                        .testTag("exercise_name_input"),
                )

                ArkhamChipSelectorSection(
                    title = "DISCIPLINE",
                    options = Discipline.entries,
                    selected = state.discipline,
                    labelOf = { it.displayName },
                    onSelect = onDisciplineChange,
                )

                ArkhamChipSelectorSection(
                    title = "TRACKING MODE",
                    options = TrackingMode.entries,
                    selected = state.trackingMode,
                    labelOf = { it.displayName },
                    onSelect = onTrackingModeChange,
                )

                OutlinedTextField(
                    value = state.defaultNotes,
                    onValueChange = onNotesChange,
                    label = { Text("Default Notes (Optional)") },
                    minLines = 2,
                    maxLines = 4,
                    shape = ArkhamShapes.chip,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("exercise_notes_input"),
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ArkhamHudPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            highlighted = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "DELETE EXERCISE?",
                    style = ArkhamTelemetryLargeStyle,
                    color = ArkhamColors.AlertRed,
                )
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = ArkhamColors.MintWhite,
                )
                Text(
                    text = "Removes this Exercise from your active catalog while preserving sync-ready soft-delete history.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArkhamColors.MintMuted,
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
                        text = "Delete",
                        onClick = onConfirm,
                        containerColor = ArkhamColors.AlertRed,
                        contentColor = ArkhamColors.OnAlertRed,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
