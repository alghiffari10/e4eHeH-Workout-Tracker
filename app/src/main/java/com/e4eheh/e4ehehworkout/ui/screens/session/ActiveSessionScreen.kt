package com.e4eheh.e4ehehworkout.ui.screens.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.ui.components.ArkhamChipSelectorSection
import com.e4eheh.e4ehehworkout.ui.components.ArkhamExercisePickerView
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamIcons
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.components.ArkhamTopHeader
import com.e4eheh.e4ehehworkout.ui.components.arkhamTextFieldColors
import com.e4eheh.e4ehehworkout.ui.components.formatElapsedDuration
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamShapes
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale
import kotlinx.coroutines.delay

/** Full-screen active live Session logger with auto-saved IN_PROGRESS drafts across all Tracking Modes. */
@Composable
fun ActiveSessionScreen(
    uiState: ActiveSessionUiState,
    onAction: (ActiveSessionAction) -> Unit,
    onExitSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.showExercisePicker) {
        val existingIds = uiState.draft.exercises.mapTo(HashSet()) { it.exerciseId }
        ArkhamExercisePickerView(
            exercises = uiState.availableExercises,
            alreadyAddedExerciseIds = existingIds,
            alreadyAddedBadgeLabel = "IN SESSION",
            emptyLibraryMessage = "Create your first Exercise inline above to add it directly to this Session.",
            disciplineFilter = uiState.pickerDisciplineFilter,
            inlineExercise = uiState.inlineExercise,
            onClosePicker = { onAction(ActiveSessionAction.CloseExercisePicker) },
            onSelectDisciplineFilter = {
                onAction(ActiveSessionAction.SelectPickerDisciplineFilter(it))
            },
            onAddExercise = { onAction(ActiveSessionAction.AddExistingExercise(it)) },
            onOpenInlineCreator = { onAction(ActiveSessionAction.OpenInlineExerciseCreator) },
            onCloseInlineCreator = { onAction(ActiveSessionAction.CloseInlineExerciseCreator) },
            onInlineNameChange = { onAction(ActiveSessionAction.UpdateInlineExerciseName(it)) },
            onInlineDisciplineChange = {
                onAction(ActiveSessionAction.UpdateInlineExerciseDiscipline(it))
            },
            onInlineTrackingModeChange = {
                onAction(ActiveSessionAction.UpdateInlineExerciseTrackingMode(it))
            },
            onSaveInlineExercise = { onAction(ActiveSessionAction.SaveInlineExercise) },
            modifier = modifier,
        )
        return
    }

    LaunchedEffect(uiState.sessionId, uiState.isLoaded, uiState.isCompleted, uiState.isMissing) {
        if (uiState.isLoaded && !uiState.isCompleted && !uiState.isMissing) {
            while (true) {
                delay(1_000L)
                onAction(ActiveSessionAction.RefreshTimer)
            }
        }
    }

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            onExitSession()
        }
    }

    BackHandler(enabled = !uiState.isFinishing) {
        onExitSession()
    }

    Column(modifier = modifier.fillMaxSize()) {
        ArkhamTopHeader(
            title = "ACTIVE SESSION",
            backTestTag = "active_session_back_button",
            onBack = onExitSession,
            backEnabled = !uiState.isFinishing,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "LIVE // AUTO-SAVED",
                        style = ArkhamTelemetryStyle,
                        color = ArkhamColors.PhosphorGreenDim,
                    )
                    Text(
                        text = formatElapsedDuration(uiState.elapsedSeconds),
                        style = ArkhamTelemetryLargeStyle,
                        color = ArkhamColors.PhosphorGreen,
                        modifier = Modifier.testTag("active_session_timer"),
                    )
                }
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
                ArkhamHudPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "SESSION NOT FOUND",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArkhamColors.MintWhite,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This Session record is no longer active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArkhamColors.MintMuted,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ArkhamSecondaryButton(
                        text = "Return",
                        onClick = onExitSession,
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
            SessionMetadataPanel(
                draft = uiState.draft,
                onTitleChange = { onAction(ActiveSessionAction.UpdateTitle(it)) },
                onDisciplineChange = { onAction(ActiveSessionAction.UpdateDiscipline(it)) },
                onNotesChange = { onAction(ActiveSessionAction.UpdateNotes(it)) },
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
                        text = "Tap + EXERCISE below to add an existing Exercise or create one inline without leaving this Session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArkhamColors.MintMuted,
                    )
                }
            } else {
                uiState.draft.exercises.forEachIndexed { exerciseIndex, exercise ->
                    key(exercise.id) {
                        SessionExerciseCard(
                            exerciseIndex = exerciseIndex,
                            exercise = exercise,
                            weightUnit = uiState.weightUnit,
                            distanceUnit = uiState.distanceUnit,
                            onAction = onAction,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ArkhamColors.ObsidianPanel,
            border = BorderStroke(1.dp, ArkhamColors.TerminalEmerald),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ArkhamSecondaryButton(
                    text = "+ EXERCISE",
                    onClick = { onAction(ActiveSessionAction.OpenExercisePicker) },
                    enabled = uiState.isLoaded && !uiState.isFinishing,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("active_session_add_exercise_button"),
                )
                ArkhamPrimaryButton(
                    text = "FINISH SESSION",
                    onClick = { onAction(ActiveSessionAction.FinishSession) },
                    enabled = !uiState.isFinishing && uiState.isLoaded,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("active_session_finish_button"),
                )
            }
        }
    }
}

@Composable
private fun SessionMetadataPanel(
    draft: ActiveSessionDraft,
    onTitleChange: (String) -> Unit,
    onDisciplineChange: (Discipline) -> Unit,
    onNotesChange: (String) -> Unit,
) {
    ArkhamHudPanel(
        modifier = Modifier.fillMaxWidth(),
        highlighted = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitleChange,
                label = { Text("Session Title") },
                placeholder = { Text("e.g., Calisthenics Push") },
                singleLine = true,
                shape = ArkhamShapes.chip,
                colors = arkhamTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_session_title_input"),
            )

            ArkhamChipSelectorSection(
                title = "PRIMARY DISCIPLINE",
                options = Discipline.entries,
                selected = draft.discipline,
                labelOf = { it.displayName },
                onSelect = onDisciplineChange,
                testTagOf = { "active_session_discipline_${it.name}" },
            )

            OutlinedTextField(
                value = draft.notes,
                onValueChange = onNotesChange,
                label = { Text("Session Notes (Optional)") },
                minLines = 2,
                maxLines = 4,
                shape = ArkhamShapes.chip,
                colors = arkhamTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_session_notes_input"),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionExerciseCard(
    exerciseIndex: Int,
    exercise: ActiveSessionExerciseDraft,
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
    onAction: (ActiveSessionAction) -> Unit,
) {
    ArkhamHudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_exercise_card_$exerciseIndex"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onAction(ActiveSessionAction.RemoveExercise(exerciseIndex)) },
                    modifier = Modifier.testTag("session_exercise_remove_$exerciseIndex"),
                ) {
                    Icon(
                        imageVector = ArkhamIcons.Close,
                        contentDescription = "Remove ${exercise.exerciseName}",
                        tint = ArkhamColors.AlertRed,
                    )
                }
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
                        weightUnit = weightUnit,
                        distanceUnit = distanceUnit,
                        onAction = onAction,
                    )
                }
            }

            ArkhamSecondaryButton(
                text = "+ ADD SET",
                onClick = { onAction(ActiveSessionAction.AddSet(exerciseIndex)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_exercise_add_set_$exerciseIndex"),
            )
        }
    }
}

@Composable
private fun SessionSetRow(
    exerciseIndex: Int,
    setIndex: Int,
    trackingMode: TrackingMode,
    setDraft: ActiveSessionSetDraft,
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
                    onToggle = { onAction(ActiveSessionAction.ToggleSetWarmup(exerciseIndex, setIndex)) },
                    modifier = Modifier.testTag("session_set_type_${exerciseIndex}_$setIndex"),
                )
                Spacer(modifier = Modifier.weight(1f))
                SetCheckButton(
                    checked = setDraft.isCompleted,
                    setNumber = setIndex + 1,
                    onToggle = {
                        onAction(ActiveSessionAction.ToggleSetCompleted(exerciseIndex, setIndex))
                    },
                    modifier = Modifier.testTag("session_set_check_${exerciseIndex}_$setIndex"),
                )
                IconButton(
                    onClick = { onAction(ActiveSessionAction.RemoveSet(exerciseIndex, setIndex)) },
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("session_set_delete_${exerciseIndex}_$setIndex"),
                ) {
                    Icon(
                        imageVector = ArkhamIcons.Close,
                        contentDescription = "Delete Set ${setIndex + 1}",
                        tint = ArkhamColors.AlertRed,
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
                            modifier = Modifier
                                .weight(1f)
                                .testTag("session_set_load_${exerciseIndex}_$setIndex"),
                        )
                    }
                    if ((setDraft.loadKg ?: 0.0) == 0.0) {
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
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("session_set_distance_${exerciseIndex}_$setIndex"),
                        )
                        CompactTelemetryField(
                            value = setDraft.durationMinText,
                            onValueChange = {
                                onAction(
                                    ActiveSessionAction.UpdateSetDurationMinutes(
                                        exerciseIndex,
                                        setIndex,
                                        it,
                                    )
                                )
                            },
                            label = "Min",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier
                                .weight(0.9f)
                                .testTag("session_set_duration_min_${exerciseIndex}_$setIndex"),
                        )
                        CompactTelemetryField(
                            value = setDraft.durationSecText,
                            onValueChange = {
                                onAction(
                                    ActiveSessionAction.UpdateSetDurationSeconds(
                                        exerciseIndex,
                                        setIndex,
                                        it,
                                    )
                                )
                            },
                            label = "Sec",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier
                                .weight(0.9f)
                                .testTag("session_set_duration_sec_${exerciseIndex}_$setIndex"),
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
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("session_set_reps_${exerciseIndex}_$setIndex"),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CompactTelemetryField(
                                value = setDraft.durationMinText,
                                onValueChange = {
                                    onAction(
                                        ActiveSessionAction.UpdateSetDurationMinutes(
                                            exerciseIndex,
                                            setIndex,
                                            it,
                                        )
                                    )
                                },
                                label = "Min",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("session_set_duration_min_${exerciseIndex}_$setIndex"),
                            )
                            CompactTelemetryField(
                                value = setDraft.durationSecText,
                                onValueChange = {
                                    onAction(
                                        ActiveSessionAction.UpdateSetDurationSeconds(
                                            exerciseIndex,
                                            setIndex,
                                            it,
                                        )
                                    )
                                },
                                label = "Sec",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("session_set_duration_sec_${exerciseIndex}_$setIndex"),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetTypeToggleChip(
    isWarmup: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isWarmup) ArkhamColors.TerminalEmerald else ArkhamColors.TerminalEmeraldBright
    val containerColor = if (isWarmup) {
        ArkhamColors.ObsidianPanel
    } else {
        ArkhamColors.TerminalEmerald.copy(alpha = 0.25f)
    }
    val textColor = if (isWarmup) ArkhamColors.MintMuted else ArkhamColors.PhosphorGreenDim

    Surface(
        modifier = modifier.toggleable(
            value = isWarmup,
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
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .size(34.dp)
            .toggleable(
                value = checked,
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
private fun CompactTelemetryField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
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
