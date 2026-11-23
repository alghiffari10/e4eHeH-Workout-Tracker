package com.e4eheh.e4ehehworkout.ui.screens.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.ui.components.ArkhamChipSelectorSection
import com.e4eheh.e4ehehworkout.ui.components.ArkhamDeleteConfirmationDialog
import com.e4eheh.e4ehehworkout.ui.components.ArkhamExercisePickerView
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.components.ArkhamTopHeader
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale

/**
 * Backdated completed-Session logger and completed-Session detail editor.
 *
 * The screen never runs the live workout timer or Rest Timer: Sets are recorded directly as completed
 * history entries and saved with explicit date, time, and duration.
 */
@Composable
fun SessionEditorScreen(
    uiState: SessionEditorUiState,
    onAction: (SessionEditorScreenAction) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onExit()
        }
    }

    if (uiState.showExercisePicker) {
        val existingIds = uiState.draft.exercises.mapTo(HashSet()) { it.exerciseId }
        ArkhamExercisePickerView(
            exercises = uiState.availableExercises,
            alreadyAddedExerciseIds = existingIds,
            alreadyAddedBadgeLabel = "IN SESSION",
            emptyLibraryMessage = "Create your first Exercise inline above to add it directly to this Session.",
            disciplineFilter = uiState.pickerDisciplineFilter,
            inlineExercise = uiState.inlineExercise,
            onClosePicker = {
                onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.CloseExercisePicker))
            },
            onSelectDisciplineFilter = {
                onAction(
                    SessionEditorScreenAction.Edit(
                        ActiveSessionAction.SelectPickerDisciplineFilter(it)
                    )
                )
            },
            onAddExercise = {
                onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.AddExistingExercise(it)))
            },
            onOpenInlineCreator = {
                onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.OpenInlineExerciseCreator))
            },
            onCloseInlineCreator = {
                onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.CloseInlineExerciseCreator))
            },
            onInlineNameChange = {
                onAction(
                    SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateInlineExerciseName(it))
                )
            },
            onInlineDisciplineChange = {
                onAction(
                    SessionEditorScreenAction.Edit(
                        ActiveSessionAction.UpdateInlineExerciseDiscipline(it)
                    )
                )
            },
            onInlineTrackingModeChange = {
                onAction(
                    SessionEditorScreenAction.Edit(
                        ActiveSessionAction.UpdateInlineExerciseTrackingMode(it)
                    )
                )
            },
            onSaveInlineExercise = {
                onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.SaveInlineExercise))
            },
            modifier = modifier,
            headerTitle = if (uiState.swappingExerciseIndex != null) {
                "SWAP EXERCISE"
            } else {
                "ADD EXERCISE"
            },
        )
        return
    }

    if (uiState.showDeleteConfirmation) {
        ArkhamDeleteConfirmationDialog(
            title = "DELETE SESSION?",
            itemName = uiState.draft.title.ifBlank { "Completed Session" },
            message = "This will soft-delete the Session and all logged Exercise Sets.",
            onDismiss = { onAction(SessionEditorScreenAction.DismissDelete) },
            onConfirm = { onAction(SessionEditorScreenAction.ConfirmDelete) },
            confirmLabel = "Delete",
            confirmTestTag = "session_detail_delete_confirm_button",
            dismissTestTag = "session_detail_delete_cancel_button",
        )
    }

    BackHandler(enabled = !uiState.isSaving && !uiState.isDeleting) {
        onExit()
    }

    val actionsEnabled = uiState.isLoaded && !uiState.isMissing &&
        !uiState.isSaving && !uiState.isDeleting

    Column(modifier = modifier.fillMaxSize()) {
        ArkhamTopHeader(
            title = if (uiState.isDetail) "SESSION DETAIL" else "BACKDATED SESSION",
            backTestTag = "session_editor_back_button",
            onBack = onExit,
            backEnabled = !uiState.isSaving && !uiState.isDeleting,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            trailingContent = {
                Text(
                    text = "COMPLETED // LOCAL",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.PhosphorGreenDim,
                )
            },
        )

        if (uiState.isLoaded && uiState.isMissing) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ArkhamHudPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_detail_missing_panel"),
                ) {
                    Text(
                        text = "SESSION NOT FOUND",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArkhamColors.MintWhite,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This completed Session record is no longer available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArkhamColors.MintMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ArkhamSecondaryButton(
                        text = "Return",
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SessionTimingPanel(
                dateText = uiState.dateText,
                timeText = uiState.timeText,
                durationMinText = uiState.durationMinText,
                durationSecText = uiState.durationSecText,
                enabled = actionsEnabled,
                onAction = onAction,
            )

            if (!uiState.isDetail && uiState.availableRoutines.isNotEmpty()) {
                ArkhamHudPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_detail_routine_panel"),
                ) {
                    ArkhamChipSelectorSection(
                        title = "START FROM ROUTINE (OPTIONAL)",
                        options = uiState.availableRoutines,
                        selected = uiState.availableRoutines.firstOrNull { it.id == uiState.routineId },
                        labelOf = { it.name },
                        onSelect = {
                            if (actionsEnabled) {
                                onAction(SessionEditorScreenAction.ApplyRoutine(it.id))
                            }
                        },
                        testTagOf = { "session_detail_routine_${it.id}" },
                    )
                }
            }

            SessionMetadataPanel(
                draft = uiState.draft,
                enabled = actionsEnabled,
                onTitleChange = {
                    onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateTitle(it)))
                },
                onDisciplineChange = {
                    onAction(
                        SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateDiscipline(it))
                    )
                },
                onNotesChange = {
                    onAction(SessionEditorScreenAction.Edit(ActiveSessionAction.UpdateNotes(it)))
                },
                testTagPrefix = "session_detail",
            )

            ArkhamSectionHeader(
                title = "EXERCISES",
                telemetryLabel = String.format(Locale.US, "%02d LOGGED", uiState.draft.exercises.size),
            )

            if (uiState.draft.exercises.isEmpty()) {
                ArkhamHudPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "NO EXERCISES LOGGED YET",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArkhamColors.MintWhite,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap + EXERCISE below to add an existing Exercise or create one inline for this completed Session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArkhamColors.MintMuted,
                    )
                }
            } else {
                uiState.draft.exercises.forEachIndexed { exerciseIndex, exercise ->
                    key(exercise.id) {
                        SessionExerciseCard(
                            exerciseIndex = exerciseIndex,
                            lastExerciseIndex = uiState.draft.exercises.lastIndex,
                            exercise = exercise,
                            enabled = actionsEnabled,
                            weightUnit = uiState.weightUnit,
                            distanceUnit = uiState.distanceUnit,
                            onAction = {
                                onAction(SessionEditorScreenAction.Edit(it))
                            },
                        )
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = ArkhamColors.AlertRed,
                    modifier = Modifier.testTag("session_detail_error"),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ArkhamColors.ObsidianPanel,
            border = BorderStroke(1.dp, ArkhamColors.TerminalEmerald),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ArkhamSecondaryButton(
                        text = "+ EXERCISE",
                        onClick = {
                            onAction(
                                SessionEditorScreenAction.Edit(ActiveSessionAction.OpenExercisePicker)
                            )
                        },
                        enabled = actionsEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("session_detail_add_exercise_button"),
                    )
                    ArkhamPrimaryButton(
                        text = when {
                            uiState.isSaving -> "SAVING..."
                            uiState.isDetail -> "SAVE CHANGES"
                            else -> "SAVE SESSION"
                        },
                        onClick = { onAction(SessionEditorScreenAction.Save) },
                        enabled = actionsEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("session_detail_save_button"),
                    )
                }

                if (uiState.isDetail) {
                    ArkhamSecondaryButton(
                        text = if (uiState.isDeleting) "DELETING..." else "DELETE SESSION",
                        onClick = { onAction(SessionEditorScreenAction.RequestDelete) },
                        enabled = actionsEnabled,
                        contentColor = ArkhamColors.AlertRed,
                        borderColor = ArkhamColors.AlertRed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("session_detail_delete_button"),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionTimingPanel(
    dateText: String,
    timeText: String,
    durationMinText: String,
    durationSecText: String,
    enabled: Boolean,
    onAction: (SessionEditorScreenAction) -> Unit,
) {
    ArkhamHudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_detail_timing_panel"),
        highlighted = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "SESSION TIMING",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreenDim,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactTelemetryField(
                    value = dateText,
                    onValueChange = { onAction(SessionEditorScreenAction.UpdateDate(it)) },
                    label = "Date (YYYY-MM-DD)",
                    keyboardType = KeyboardType.Ascii,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("session_detail_date_input"),
                )
                CompactTelemetryField(
                    value = timeText,
                    onValueChange = { onAction(SessionEditorScreenAction.UpdateTime(it)) },
                    label = "Time (HH:MM)",
                    keyboardType = KeyboardType.Ascii,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("session_detail_time_input"),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompactTelemetryField(
                    value = durationMinText,
                    onValueChange = {
                        onAction(SessionEditorScreenAction.UpdateDurationMinutes(it))
                    },
                    label = "Duration (min)",
                    keyboardType = KeyboardType.Number,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("session_detail_duration_min_input"),
                )
                CompactTelemetryField(
                    value = durationSecText,
                    onValueChange = {
                        onAction(SessionEditorScreenAction.UpdateDurationSeconds(it))
                    },
                    label = "Sec",
                    keyboardType = KeyboardType.Number,
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("session_detail_duration_sec_input"),
                )
            }
        }
    }
}
