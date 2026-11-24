package com.e4eheh.e4ehehworkout.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntry
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.unit.UnitConverter
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamShapes
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle

/** Shared HUD row displaying a Bodyweight Entry with edit and soft-delete controls. */
@Composable
fun BodyweightEntryRow(
    row: BodyweightEntryRowUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ArkhamShapes.chip,
        color = ArkhamColors.ObsidianPanel,
        border = BorderStroke(1.dp, ArkhamColors.TerminalEmerald),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = row.formattedWeight,
                    style = ArkhamTelemetryLargeStyle,
                    color = ArkhamColors.PhosphorGreen,
                    modifier = Modifier.testTag("${testTagPrefix}_weight_${row.entry.id}"),
                )
                Text(
                    text = row.dateLabel,
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.MintMuted,
                    modifier = Modifier.testTag("${testTagPrefix}_date_${row.entry.id}"),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.testTag("${testTagPrefix}_edit_${row.entry.id}"),
                ) {
                    Icon(
                        imageVector = ArkhamIcons.Edit,
                        contentDescription = "Edit Bodyweight Entry",
                        tint = ArkhamColors.PhosphorGreenDim,
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("${testTagPrefix}_delete_${row.entry.id}"),
                ) {
                    Icon(
                        imageVector = ArkhamIcons.Delete,
                        contentDescription = "Delete Bodyweight Entry",
                        tint = ArkhamColors.AlertRed,
                    )
                }
            }
        }
    }
}

/** Modal chamfered HUD dialog for logging or editing a dated Bodyweight Entry. */
@Composable
fun BodyweightEntryEditorDialog(
    state: BodyweightEntryEditorState,
    weightUnit: WeightUnit,
    onDateChange: (String) -> Unit,
    onWeightChange: (String) -> Unit,
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (state.editingEntryId == null) {
                        "LOG BODYWEIGHT ENTRY"
                    } else {
                        "EDIT BODYWEIGHT ENTRY"
                    },
                    style = ArkhamTelemetryLargeStyle,
                    color = ArkhamColors.PhosphorGreen,
                )

                Text(
                    text = "Entered in ${weightUnit.symbol} and persisted canonically in kg. Never affects Exercise load or PRs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ArkhamColors.MintMuted,
                )

                OutlinedTextField(
                    value = state.dateText,
                    onValueChange = onDateChange,
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    isError = state.dateError != null,
                    supportingText = state.dateError?.let { error ->
                        {
                            Text(
                                text = error,
                                color = ArkhamColors.AlertRed,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                    shape = ArkhamShapes.chip,
                    colors = arkhamTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bodyweight_date_input"),
                )

                OutlinedTextField(
                    value = state.weightText,
                    onValueChange = onWeightChange,
                    label = { Text("Bodyweight (${weightUnit.symbol})") },
                    singleLine = true,
                    enabled = !state.isSaving,
                    isError = state.weightError != null,
                    supportingText = state.weightError?.let { error ->
                        {
                            Text(
                                text = error,
                                color = ArkhamColors.AlertRed,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    shape = ArkhamShapes.chip,
                    colors = arkhamTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bodyweight_weight_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ArkhamSecondaryButton(
                        text = "Cancel",
                        enabled = !state.isSaving,
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bodyweight_editor_cancel_button"),
                    )
                    ArkhamPrimaryButton(
                        text = if (state.isSaving) "Saving..." else "Save Entry",
                        enabled = !state.isSaving,
                        onClick = onSave,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bodyweight_editor_save_button"),
                    )
                }
            }
        }
    }
}

/** Confirmation modal before soft-deleting a Bodyweight Entry. */
@Composable
fun DeleteBodyweightEntryDialog(
    entry: BodyweightEntry,
    dateLabel: String,
    weightUnit: WeightUnit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ArkhamDeleteConfirmationDialog(
        title = "DELETE BODYWEIGHT ENTRY?",
        itemName = "${UnitConverter.formatWeight(entry.weightKg, weightUnit)} · $dateLabel",
        message = "Soft-deletes this Bodyweight Entry while preserving sync-ready tombstone history.",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        confirmLabel = "Delete",
        confirmTestTag = "bodyweight_delete_confirm_button",
        dismissTestTag = "bodyweight_delete_cancel_button",
    )
}
