package com.e4eheh.e4ehehworkout.ui.screens.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.timer.RestTimerState
import com.e4eheh.e4ehehworkout.ui.components.ArkhamChipSelectorSection
import com.e4eheh.e4ehehworkout.ui.components.ArkhamExercisePickerView
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamIcons
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.components.ArkhamTopHeader
import com.e4eheh.e4ehehworkout.ui.components.arkhamSelectableColors
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

    val hapticFeedback = LocalHapticFeedback.current
    var lastHandledCompletionEventId by rememberSaveable(uiState.sessionId) {
        mutableLongStateOf(uiState.restTimer.completionEventId)
    }
    LaunchedEffect(uiState.restTimer.completionEventId) {
        val eventId = uiState.restTimer.completionEventId
        if (eventId > lastHandledCompletionEventId) {
            lastHandledCompletionEventId = eventId
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
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
            headerTitle = if (uiState.swappingExerciseIndex != null) {
                "SWAP EXERCISE"
            } else {
                "ADD EXERCISE"
            },
        )
        return
    }

    if (uiState.showUpdateRoutinePrompt) {
        UpdateRoutinePromptDialog(
            routineName = uiState.originatingRoutineName ?: uiState.draft.title,
            onConfirm = { onAction(ActiveSessionAction.ConfirmFinishAndUpdateRoutine) },
            onDecline = { onAction(ActiveSessionAction.DeclineRoutineUpdateAndFinish) },
            onDismiss = { onAction(ActiveSessionAction.DismissRoutineUpdatePrompt) },
        )
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
                enabled = uiState.isEditable,
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
                            lastExerciseIndex = uiState.draft.exercises.lastIndex,
                            exercise = exercise,
                            enabled = uiState.isEditable,
                            weightUnit = uiState.weightUnit,
                            distanceUnit = uiState.distanceUnit,
                            onAction = onAction,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (uiState.restTimer.isVisible) {
            CompactRestTimerBar(
                restTimer = uiState.restTimer,
                controlsEnabled = uiState.isEditable,
                onAdjust = { onAction(ActiveSessionAction.AdjustRestTimer(it)) },
                onSkip = { onAction(ActiveSessionAction.SkipRestTimer) },
            )
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
    enabled: Boolean,
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
                enabled = enabled,
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
                enabled = enabled,
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
                IconButton(
                    onClick = { onAction(ActiveSessionAction.MoveExerciseUp(exerciseIndex)) },
                    enabled = canMoveUp,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("session_exercise_move_up_$exerciseIndex"),
                ) {
                    Icon(
                        imageVector = ArkhamIcons.ArrowUp,
                        contentDescription = "Move ${exercise.exerciseName} up",
                        tint = if (canMoveUp) {
                            ArkhamColors.PhosphorGreenDim
                        } else {
                            ArkhamColors.MintMuted.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { onAction(ActiveSessionAction.MoveExerciseDown(exerciseIndex)) },
                    enabled = canMoveDown,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("session_exercise_move_down_$exerciseIndex"),
                ) {
                    Icon(
                        imageVector = ArkhamIcons.ArrowDown,
                        contentDescription = "Move ${exercise.exerciseName} down",
                        tint = if (canMoveDown) {
                            ArkhamColors.PhosphorGreenDim
                        } else {
                            ArkhamColors.MintMuted.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { onAction(ActiveSessionAction.OpenSwapExercisePicker(exerciseIndex)) },
                    enabled = enabled,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("session_exercise_swap_$exerciseIndex"),
                ) {
                    Icon(
                        imageVector = ArkhamIcons.Swap,
                        contentDescription = "Swap ${exercise.exerciseName}",
                        tint = ArkhamColors.PhosphorGreenDim,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { onAction(ActiveSessionAction.RemoveExercise(exerciseIndex)) },
                    enabled = enabled,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("session_exercise_remove_$exerciseIndex"),
                ) {
                    Icon(
                        imageVector = ArkhamIcons.Close,
                        contentDescription = "Remove ${exercise.exerciseName}",
                        tint = ArkhamColors.AlertRed,
                        modifier = Modifier.size(18.dp),
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
                        DurationMinSecFields(
                            exerciseIndex = exerciseIndex,
                            setIndex = setIndex,
                            durationMinText = setDraft.durationMinText,
                            durationSecText = setDraft.durationSecText,
                            fieldWeight = 0.9f,
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
                            DurationMinSecFields(
                                exerciseIndex = exerciseIndex,
                                setIndex = setIndex,
                                durationMinText = setDraft.durationMinText,
                                durationSecText = setDraft.durationSecText,
                                fieldWeight = 1f,
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
    onAction: (ActiveSessionAction) -> Unit,
) {
    CompactTelemetryField(
        value = durationMinText,
        onValueChange = {
            onAction(ActiveSessionAction.UpdateSetDurationMinutes(exerciseIndex, setIndex, it))
        },
        label = "Min",
        keyboardType = KeyboardType.Number,
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
        modifier = Modifier
            .weight(fieldWeight)
            .testTag("session_set_duration_sec_${exerciseIndex}_$setIndex"),
    )
}

@Composable
private fun SetTypeToggleChip(
    isWarmup: Boolean,
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

@Composable
private fun CompactRestTimerBar(
    restTimer: RestTimerState,
    controlsEnabled: Boolean,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
) {
    val isCompleted = restTimer.isCompleted
    val accentColor = if (isCompleted) ArkhamColors.TacticalAmber else ArkhamColors.PhosphorGreen
    val statusLabel = if (isCompleted) "REST COMPLETE" else "REST TIMER"

    ArkhamHudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("rest_timer_panel"),
        highlighted = true,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = statusLabel,
                    style = ArkhamTelemetryStyle,
                    color = if (isCompleted) ArkhamColors.TacticalAmber else ArkhamColors.PhosphorGreenDim,
                )
                Text(
                    text = formatElapsedDuration(restTimer.remainingSeconds.toLong()),
                    style = ArkhamTelemetryLargeStyle,
                    color = accentColor,
                    modifier = Modifier.testTag("rest_timer_remaining"),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(ArkhamColors.TerminalEmerald.copy(alpha = 0.4f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(restTimer.progressFraction)
                        .height(2.dp)
                        .background(accentColor),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArkhamSecondaryButton(
                    text = "-15s",
                    onClick = { onAdjust(-UserPreferences.REST_TIMER_STEP_SECONDS) },
                    enabled = controlsEnabled && restTimer.remainingSeconds > 0,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("rest_timer_decrement"),
                )
                ArkhamSecondaryButton(
                    text = "+15s",
                    onClick = { onAdjust(UserPreferences.REST_TIMER_STEP_SECONDS) },
                    enabled = controlsEnabled,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("rest_timer_increment"),
                )
                ArkhamSecondaryButton(
                    text = "Skip",
                    onClick = onSkip,
                    enabled = controlsEnabled,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("rest_timer_skip"),
                )
            }
        }
    }
}

@Composable
private fun UpdateRoutinePromptDialog(
    routineName: String,
    onConfirm: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ArkhamHudPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .testTag("update_routine_prompt_dialog"),
            highlighted = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Update Routine with these changes?",
                    style = ArkhamTelemetryLargeStyle,
                    color = ArkhamColors.PhosphorGreen,
                )
                if (routineName.isNotBlank()) {
                    Text(
                        text = routineName,
                        style = MaterialTheme.typography.titleMedium,
                        color = ArkhamColors.MintWhite,
                    )
                }
                Text(
                    text = "Your Session Exercise order or target Set counts changed relative to this Routine. Save these structural changes to the Routine?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArkhamColors.MintMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ArkhamSecondaryButton(
                        text = "Decline",
                        onClick = onDecline,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("update_routine_decline_button"),
                    )
                    ArkhamPrimaryButton(
                        text = "Update Routine",
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("update_routine_confirm_button"),
                    )
                }
            }
        }
    }
}
