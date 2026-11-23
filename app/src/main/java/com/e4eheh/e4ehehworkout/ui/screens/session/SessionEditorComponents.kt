package com.e4eheh.e4ehehworkout.ui.screens.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.ui.components.ArkhamChipSelectorSection
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamIcons
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.arkhamSelectableColors
import com.e4eheh.e4ehehworkout.ui.components.arkhamTextFieldColors
import com.e4eheh.e4ehehworkout.ui.components.formatElapsedDuration
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamShapes
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale

/** Editable Session metadata shared by live logging and completed Session detail. */
@Composable
internal fun SessionMetadataPanel(
    draft: ActiveSessionDraft,
    enabled: Boolean,
    onTitleChange: (String) -> Unit,
    onDisciplineChange: (Discipline) -> Unit,
    onNotesChange: (String) -> Unit,
    testTagPrefix: String = "active_session",
) {
    ArkhamHudPanel(
        modifier = Modifier.fillMaxWidth(),
        highlighted = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                enabled = enabled,
                label = { Text("Session Title") },
                placeholder = { Text("e.g., Calisthenics Push") },
                singleLine = true,
                shape = ArkhamShapes.chip,
                colors = arkhamTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_title_input"),
            )

            ArkhamChipSelectorSection(
                title = "PRIMARY DISCIPLINE",
                options = Discipline.entries,
                selected = draft.discipline,
                labelOf = { it.displayName },
                onSelect = onDisciplineChange,
                testTagOf = { "${testTagPrefix}_discipline_${it.name}" },
            )

            OutlinedTextField(
                value = draft.notes,
                onValueChange = onNotesChange,
                enabled = enabled,
                label = { Text("Session Notes (Optional)") },
                minLines = 2,
                maxLines = 4,
                shape = ArkhamShapes.chip,
                colors = arkhamTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_notes_input"),
            )
        }
    }
}

/** Ordered Exercise snapshot card with per-Set editing controls for all four Tracking Modes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionExerciseCard(
    exerciseIndex: Int,
    lastExerciseIndex: Int,
    exercise: ActiveSessionExerciseDraft,
    enabled: Boolean,
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
    onAction: (ActiveSessionAction) -> Unit,
) {
    val canMoveUp = enabled && exerciseIndex > 0
    val canMoveDown = enabled && exerciseIndex < lastExerciseIndex

    ArkhamHudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_exercise_card_$exerciseIndex"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = String.format(Locale.US, "%02d", exerciseIndex + 1),
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.PhosphorGreen,
                )
                Text(
                    text = exercise.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    color = ArkhamColors.MintWhite,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp),
                )
                SessionExerciseHeaderIconButton(
                    icon = ArkhamIcons.ArrowUp,
                    contentDescription = "Move ${exercise.exerciseName} up",
                    enabled = canMoveUp,
                    testTag = "session_exercise_move_up_$exerciseIndex",
                    onClick = { onAction(ActiveSessionAction.MoveExerciseUp(exerciseIndex)) },
                )
                SessionExerciseHeaderIconButton(
                    icon = ArkhamIcons.ArrowDown,
                    contentDescription = "Move ${exercise.exerciseName} down",
                    enabled = canMoveDown,
                    testTag = "session_exercise_move_down_$exerciseIndex",
                    onClick = { onAction(ActiveSessionAction.MoveExerciseDown(exerciseIndex)) },
                )
                SessionExerciseHeaderIconButton(
                    icon = ArkhamIcons.Swap,
                    contentDescription = "Swap ${exercise.exerciseName}",
                    enabled = enabled,
                    testTag = "session_exercise_swap_$exerciseIndex",
                    onClick = { onAction(ActiveSessionAction.OpenSwapExercisePicker(exerciseIndex)) },
                )
                SessionExerciseHeaderIconButton(
                    icon = ArkhamIcons.Close,
                    contentDescription = "Remove ${exercise.exerciseName}",
                    enabled = enabled,
                    activeTint = ArkhamColors.AlertRed,
                    testTag = "session_exercise_remove_$exerciseIndex",
                    onClick = { onAction(ActiveSessionAction.RemoveExercise(exerciseIndex)) },
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArkhamHudBadge(
                    label = exercise.exerciseDiscipline.displayName,
                    accentColor = ArkhamColors.PhosphorGreen,
                )
                ArkhamHudBadge(
                    label = exercise.trackingMode.displayName,
                    accentColor = ArkhamColors.PhosphorGreenDim,
                )
            }

            OutlinedTextField(
                value = exercise.notes,
                onValueChange = {
                    onAction(ActiveSessionAction.UpdateExerciseNotes(exerciseIndex, it))
                },
                enabled = enabled,
                label = { Text("Exercise Notes (Optional)") },
                maxLines = 3,
                shape = ArkhamShapes.chip,
                colors = arkhamTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_exercise_notes_$exerciseIndex"),
            )

            exercise.sets.forEachIndexed { setIndex, setDraft ->
                key(setDraft.id) {
                    SessionSetRow(
                        exerciseIndex = exerciseIndex,
                        setIndex = setIndex,
                        trackingMode = exercise.trackingMode,
                        setDraft = setDraft,
                        enabled = enabled,
                        weightUnit = weightUnit,
                        distanceUnit = distanceUnit,
                        onAction = onAction,
                    )
                }
            }

            ArkhamSecondaryButton(
                text = "+ ADD SET",
                onClick = { onAction(ActiveSessionAction.AddSet(exerciseIndex)) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_exercise_add_set_$exerciseIndex"),
            )
        }
    }
}

/** Single Set row with Tracking Mode-specific telemetry fields and Warm-up/Working toggle. */
@Composable
internal fun SessionSetRow(
    exerciseIndex: Int,
    setIndex: Int,
    trackingMode: TrackingMode,
    setDraft: ActiveSessionSetDraft,
    enabled: Boolean = true,
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
    onAction: (ActiveSessionAction) -> Unit,
) {
    ArkhamHudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_set_${exerciseIndex}_$setIndex"),
        highlighted = setDraft.isCompleted,
        contentPadding = PaddingValues(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = String.format(Locale.US, "SET %02d", setIndex + 1),
                    style = ArkhamTelemetryStyle,
                    color = if (setDraft.isCompleted) {
                        ArkhamColors.PhosphorGreen
                    } else {
                        ArkhamColors.MintWhite
                    },
                )
                SetTypeToggleChip(
                    isWarmup = setDraft.isWarmup,
                    enabled = enabled,
                    onToggle = { onAction(ActiveSessionAction.ToggleSetWarmup(exerciseIndex, setIndex)) },
                    modifier = Modifier.testTag("session_set_type_${exerciseIndex}_$setIndex"),
                )
                Spacer(modifier = Modifier.weight(1f))
                SetCheckButton(
                    checked = setDraft.isCompleted,
                    setNumber = setIndex + 1,
                    enabled = enabled,
                    onToggle = {
                        onAction(ActiveSessionAction.ToggleSetCompleted(exerciseIndex, setIndex))
                    },
                    modifier = Modifier.testTag("session_set_check_${exerciseIndex}_$setIndex"),
                )
                IconButton(
                    onClick = { onAction(ActiveSessionAction.RemoveSet(exerciseIndex, setIndex)) },
                    enabled = enabled,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("session_set_delete_${exerciseIndex}_$setIndex"),
                ) {
                    Icon(
                        imageVector = ArkhamIcons.Close,
                        contentDescription = "Delete Set ${setIndex + 1}",
                        tint = if (enabled) {
                            ArkhamColors.AlertRed
                        } else {
                            ArkhamColors.MintMuted.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            when (trackingMode) {
                TrackingMode.REPS_AND_LOAD -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CompactTelemetryField(
                            value = setDraft.repsText,
                            onValueChange = {
                                onAction(ActiveSessionAction.UpdateSetReps(exerciseIndex, setIndex, it))
                            },
                            label = "Reps",
                            keyboardType = KeyboardType.Number,
                            enabled = enabled,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("session_set_reps_${exerciseIndex}_$setIndex"),
                        )
                        CompactTelemetryField(
                            value = setDraft.loadText,
                            onValueChange = {
                                onAction(ActiveSessionAction.UpdateSetLoad(exerciseIndex, setIndex, it))
                            },
                            label = "Load (${weightUnit.symbol})",
                            placeholder = "BW (0)",
                            keyboardType = KeyboardType.Decimal,
                            enabled = enabled,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("session_set_load_${exerciseIndex}_$setIndex"),
                        )
                    }
                    if (setDraft.isBodyweight) {
                        Text(
                            text = "MODE: BODYWEIGHT",
                            style = ArkhamTelemetryStyle,
                            color = ArkhamColors.MintMuted,
                        )
                    }
                }

                TrackingMode.HOLD_DURATION -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CompactTelemetryField(
                            value = setDraft.holdSecondsText,
                            onValueChange = {
                                onAction(
                                    ActiveSessionAction.UpdateSetHoldSeconds(
                                        exerciseIndex,
                                        setIndex,
                                        it,
                                    )
                                )
                            },
                            label = "Hold (s)",
                            keyboardType = KeyboardType.Number,
                            enabled = enabled,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("session_set_hold_${exerciseIndex}_$setIndex"),
                        )
                        val holdSeconds = setDraft.holdSecondsText.toIntOrNull() ?: 0
                        if (holdSeconds > 0) {
                            Text(
                                text = formatElapsedDuration(holdSeconds.toLong()),
                                style = ArkhamTelemetryStyle,
                                color = ArkhamColors.PhosphorGreenDim,
                            )
                        }
                    }
                }

                TrackingMode.DISTANCE_AND_TIME -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CompactTelemetryField(
                            value = setDraft.distanceText,
                            onValueChange = {
                                onAction(
                                    ActiveSessionAction.UpdateSetDistance(
                                        exerciseIndex,
                                        setIndex,
                                        it,
                                    )
                                )
                            },
                            label = "Dist (${distanceUnit.symbol})",
                            keyboardType = KeyboardType.Decimal,
                            enabled = enabled,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("session_set_distance_${exerciseIndex}_$setIndex"),
                        )
                        DurationMinSecFields(
                            exerciseIndex = exerciseIndex,
                            setIndex = setIndex,
                            durationMinText = setDraft.durationMinText,
                            durationSecText = setDraft.durationSecText,
                            fieldWeight = 0.9f,
                            enabled = enabled,
                            onAction = onAction,
                        )
                    }
                }

                TrackingMode.CONDITIONING_ROUNDS -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CompactTelemetryField(
                                value = setDraft.roundsText,
                                onValueChange = {
                                    onAction(
                                        ActiveSessionAction.UpdateSetRounds(
                                            exerciseIndex,
                                            setIndex,
                                            it,
                                        )
                                    )
                                },
                                label = "Rounds",
                                keyboardType = KeyboardType.Number,
                                enabled = enabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("session_set_rounds_${exerciseIndex}_$setIndex"),
                            )
                            CompactTelemetryField(
                                value = setDraft.repsText,
                                onValueChange = {
                                    onAction(
                                        ActiveSessionAction.UpdateSetReps(
                                            exerciseIndex,
                                            setIndex,
                                            it,
                                        )
                                    )
                                },
                                label = "Reps (Opt)",
                                keyboardType = KeyboardType.Number,
                                enabled = enabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("session_set_reps_${exerciseIndex}_$setIndex"),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DurationMinSecFields(
                                exerciseIndex = exerciseIndex,
                                setIndex = setIndex,
                                durationMinText = setDraft.durationMinText,
                                durationSecText = setDraft.durationSecText,
                                fieldWeight = 1f,
                                enabled = enabled,
                                onAction = onAction,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.DurationMinSecFields(
    exerciseIndex: Int,
    setIndex: Int,
    durationMinText: String,
    durationSecText: String,
    fieldWeight: Float,
    enabled: Boolean,
    onAction: (ActiveSessionAction) -> Unit,
) {
    CompactTelemetryField(
        value = durationMinText,
        onValueChange = {
            onAction(ActiveSessionAction.UpdateSetDurationMinutes(exerciseIndex, setIndex, it))
        },
        label = "Min",
        keyboardType = KeyboardType.Number,
        enabled = enabled,
        modifier = Modifier
            .weight(fieldWeight)
            .testTag("session_set_duration_min_${exerciseIndex}_$setIndex"),
    )
    CompactTelemetryField(
        value = durationSecText,
        onValueChange = {
            onAction(ActiveSessionAction.UpdateSetDurationSeconds(exerciseIndex, setIndex, it))
        },
        label = "Sec",
        keyboardType = KeyboardType.Number,
        enabled = enabled,
        modifier = Modifier
            .weight(fieldWeight)
            .testTag("session_set_duration_sec_${exerciseIndex}_$setIndex"),
    )
}

@Composable
private fun SetTypeToggleChip(
    isWarmup: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (borderColor, containerColor, textColor) = arkhamSelectableColors(
        selected = !isWarmup,
        selectedContainerAlpha = 0.25f,
        labelPrefix = "setTypeChip",
    )

    Surface(
        modifier = modifier.toggleable(
            value = isWarmup,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = { onToggle() },
        ),
        shape = ArkhamShapes.chip,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = if (isWarmup) "WARM-UP" else "WORKING",
            style = ArkhamTelemetryStyle,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SetCheckButton(
    checked: Boolean,
    setNumber: Int,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(34.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            ),
        shape = ArkhamShapes.chip,
        color = if (checked) ArkhamColors.PhosphorGreen else ArkhamColors.ObsidianPanel,
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) ArkhamColors.PhosphorGreen else ArkhamColors.TerminalEmeraldBright,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = ArkhamIcons.Check,
                contentDescription = if (checked) {
                    "Uncheck Set $setNumber"
                } else {
                    "Check Set $setNumber"
                },
                tint = if (checked) {
                    ArkhamColors.OnPhosphor
                } else {
                    ArkhamColors.MintMuted.copy(alpha = 0.4f)
                },
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun CompactTelemetryField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        textStyle = ArkhamTelemetryStyle.copy(color = ArkhamColors.MintWhite),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = ArkhamShapes.chip,
        colors = arkhamTextFieldColors(),
        modifier = modifier,
    )
}

@Composable
private fun SessionExerciseHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
    activeTint: Color = ArkhamColors.PhosphorGreenDim,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(36.dp)
            .testTag(testTag),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) activeTint else ArkhamColors.MintMuted.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp),
        )
    }
}
