package com.e4eheh.e4ehehworkout.ui.screens.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.timer.RestTimerState
import com.e4eheh.e4ehehworkout.ui.components.ArkhamExercisePickerView
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.components.ArkhamTopHeader
import com.e4eheh.e4ehehworkout.ui.components.formatElapsedDuration
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
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
