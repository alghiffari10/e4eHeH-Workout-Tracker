package com.e4eheh.e4ehehworkout.ui.screens.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.Routine
import com.e4eheh.e4ehehworkout.domain.model.RoutineExercise
import com.e4eheh.e4ehehworkout.ui.components.ArkhamChipSelectorSection
import com.e4eheh.e4ehehworkout.ui.components.ArkhamDeleteConfirmationDialog
import com.e4eheh.e4ehehworkout.ui.components.ArkhamExercisePickerView
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamIcons
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.components.ArkhamTopHeader
import com.e4eheh.e4ehehworkout.ui.components.arkhamTextFieldColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamShapes
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale

/** Full-width Workout sub-screen for creating/editing a Routine and picking or creating Exercises inline. */
@Composable
fun RoutineBuilderView(
    state: RoutineBuilderState,
    exercises: List<Exercise>,
    exercisesById: Map<String, Exercise>,
    onAction: (WorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.showExercisePicker) {
        val existingExerciseIds = state.exercises.mapTo(HashSet()) { it.exerciseId }
        ArkhamExercisePickerView(
            exercises = exercises,
            alreadyAddedExerciseIds = existingExerciseIds,
            alreadyAddedBadgeLabel = "IN ROUTINE",
            emptyLibraryMessage = "Create your first Exercise inline above to add it directly to this Routine.",
            disciplineFilter = state.pickerDisciplineFilter,
            inlineExercise = state.inlineExercise,
            onClosePicker = { onAction(WorkoutAction.CloseExercisePicker) },
            onSelectDisciplineFilter = { onAction(WorkoutAction.SelectPickerDisciplineFilter(it)) },
            onAddExercise = { onAction(WorkoutAction.AddExistingExerciseToRoutine(it)) },
            onOpenInlineCreator = { onAction(WorkoutAction.OpenInlineExerciseCreator) },
            onCloseInlineCreator = { onAction(WorkoutAction.CloseInlineExerciseCreator) },
            onInlineNameChange = { onAction(WorkoutAction.UpdateInlineExerciseName(it)) },
            onInlineDisciplineChange = { onAction(WorkoutAction.UpdateInlineExerciseDiscipline(it)) },
            onInlineTrackingModeChange = { onAction(WorkoutAction.UpdateInlineExerciseTrackingMode(it)) },
            onSaveInlineExercise = { onAction(WorkoutAction.SaveInlineExercise) },
            modifier = modifier,
        )
        return
    }

    BackHandler {
        onAction(WorkoutAction.DismissRoutineBuilder)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ArkhamTopHeader(
            title = if (state.editingRoutineId == null) "CREATE ROUTINE" else "EDIT ROUTINE",
            backTestTag = "routine_builder_back_button",
            onBack = { onAction(WorkoutAction.DismissRoutineBuilder) },
        )

        OutlinedTextField(
            value = state.name,
            onValueChange = { onAction(WorkoutAction.UpdateRoutineName(it)) },
            label = { Text("Routine Name") },
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
            colors = arkhamTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("routine_name_input"),
        )

        ArkhamChipSelectorSection(
            title = "PRIMARY DISCIPLINE",
            options = Discipline.entries,
            selected = state.discipline,
            labelOf = { it.displayName },
            onSelect = { onAction(WorkoutAction.UpdateRoutineDiscipline(it)) },
            testTagOf = { "routine_discipline_${it.name}" },
        )

        ArkhamSectionHeader(
            title = "EXERCISES",
            telemetryLabel = String.format(Locale.US, "%02d ASSIGNED", state.exercises.size),
        )

        ArkhamSecondaryButton(
            text = "+ ADD EXERCISE",
            onClick = { onAction(WorkoutAction.OpenExercisePicker) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("routine_add_exercise_button"),
        )

        if (state.exercises.isEmpty()) {
            ArkhamHudPanel(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "NO EXERCISES ADDED",
                    style = MaterialTheme.typography.titleMedium,
                    color = ArkhamColors.MintWhite,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Add Exercises from any Discipline or create new Exercises inline while building this Routine.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArkhamColors.MintMuted,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.exercises.forEachIndexed { index, draft ->
                    key(draft.draftKey) {
                        RoutineBuilderExerciseRow(
                            index = index,
                            lastIndex = state.exercises.lastIndex,
                            draft = draft,
                            exercise = exercisesById[draft.exerciseId],
                            onMoveUp = { onAction(WorkoutAction.MoveRoutineExerciseUp(index)) },
                            onMoveDown = { onAction(WorkoutAction.MoveRoutineExerciseDown(index)) },
                            onIncrementSets = {
                                onAction(WorkoutAction.IncrementRoutineExerciseTargetSets(index))
                            },
                            onDecrementSets = {
                                onAction(WorkoutAction.DecrementRoutineExerciseTargetSets(index))
                            },
                            onRemove = { onAction(WorkoutAction.RemoveRoutineExercise(index)) },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArkhamSecondaryButton(
                text = "Cancel",
                onClick = { onAction(WorkoutAction.DismissRoutineBuilder) },
                modifier = Modifier.weight(1f),
            )
            ArkhamPrimaryButton(
                text = "Save Routine",
                onClick = { onAction(WorkoutAction.SaveRoutine) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("routine_save_button"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineBuilderExerciseRow(
    index: Int,
    lastIndex: Int,
    draft: RoutineExerciseDraft,
    exercise: Exercise?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onIncrementSets: () -> Unit,
    onDecrementSets: () -> Unit,
    onRemove: () -> Unit,
) {
    val exerciseName = exercise?.name ?: "Archived Exercise"

    ArkhamHudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("routine_builder_item_$index"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = String.format(Locale.US, "%02d", index + 1),
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreen,
            )
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.titleMedium,
                color = ArkhamColors.MintWhite,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.testTag("routine_item_move_up_$index"),
            ) {
                Icon(
                    imageVector = ArkhamIcons.ArrowUp,
                    contentDescription = "Move $exerciseName up",
                    tint = if (index > 0) {
                        ArkhamColors.PhosphorGreenDim
                    } else {
                        ArkhamColors.MintMuted.copy(alpha = 0.35f)
                    },
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = index < lastIndex,
                modifier = Modifier.testTag("routine_item_move_down_$index"),
            ) {
                Icon(
                    imageVector = ArkhamIcons.ArrowDown,
                    contentDescription = "Move $exerciseName down",
                    tint = if (index < lastIndex) {
                        ArkhamColors.PhosphorGreenDim
                    } else {
                        ArkhamColors.MintMuted.copy(alpha = 0.35f)
                    },
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("routine_item_remove_$index"),
            ) {
                Icon(
                    imageVector = ArkhamIcons.Close,
                    contentDescription = "Remove $exerciseName",
                    tint = ArkhamColors.AlertRed,
                )
            }
        }

        if (exercise != null) {
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArkhamHudBadge(
                    label = exercise.discipline.displayName,
                    accentColor = ArkhamColors.PhosphorGreen,
                )
                ArkhamHudBadge(
                    label = exercise.trackingMode.displayName,
                    accentColor = ArkhamColors.PhosphorGreenDim,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TARGET SETS",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.MintMuted,
                )
                Text(
                    text = draft.defaultTargetSets?.let(::formatTargetSetsLabel) ?: "OPTIONAL",
                    style = ArkhamTelemetryStyle,
                    color = if (draft.defaultTargetSets != null) {
                        ArkhamColors.PhosphorGreen
                    } else {
                        ArkhamColors.MintMuted
                    },
                    modifier = Modifier.testTag("routine_item_sets_value_$index"),
                )
            }
            ArkhamSecondaryButton(
                text = "-",
                enabled = draft.defaultTargetSets != null,
                onClick = onDecrementSets,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("routine_item_sets_dec_$index"),
            )
            ArkhamSecondaryButton(
                text = "+",
                enabled = (draft.defaultTargetSets ?: 0) < RoutineExercise.MAX_DEFAULT_TARGET_SETS,
                onClick = onIncrementSets,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("routine_item_sets_inc_$index"),
            )
        }
    }
}

/** Confirmation modal before soft-deleting a Routine. */
@Composable
fun DeleteRoutineDialog(
    routine: Routine,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ArkhamDeleteConfirmationDialog(
        title = "DELETE ROUTINE?",
        itemName = routine.name,
        message = "Removes this Routine from your active list while preserving sync-ready soft-delete history.",
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

internal fun formatTargetSetsLabel(targetSets: Int): String =
    if (targetSets == 1) "1 SET" else "$targetSets SETS"
