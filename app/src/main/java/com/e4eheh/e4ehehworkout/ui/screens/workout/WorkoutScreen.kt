package com.e4eheh.e4ehehworkout.ui.screens.workout

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.Routine
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamIcons
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamMotion
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamShapes
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale

/** Workout tab housing reusable Routines and the athlete's personal Exercise library. */
@Composable
fun WorkoutScreen(
    uiState: WorkoutUiState,
    onAction: (WorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val builder = uiState.routineBuilder
    if (builder != null) {
        RoutineBuilderView(
            state = builder,
            exercises = uiState.exercises,
            exercisesById = uiState.exercisesById,
            onAction = onAction,
            modifier = modifier,
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WorkoutSectionSwitcher(
            selected = uiState.selectedSection,
            onSelect = { onAction(WorkoutAction.SelectSection(it)) },
        )

        when (uiState.selectedSection) {
            WorkoutTabSection.ROUTINES -> RoutinesSection(
                routines = uiState.routines,
                exercisesById = uiState.exercisesById,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )

            WorkoutTabSection.EXERCISES -> ExerciseLibrarySection(
                exercises = uiState.exercises,
                onAction = onAction,
                modifier = Modifier.weight(1f),
            )
        }
    }

    uiState.exerciseEditor?.let { editorState ->
        ExerciseEditorDialog(
            state = editorState,
            onNameChange = { onAction(WorkoutAction.UpdateExerciseName(it)) },
            onDisciplineChange = { onAction(WorkoutAction.UpdateExerciseDiscipline(it)) },
            onTrackingModeChange = { onAction(WorkoutAction.UpdateExerciseTrackingMode(it)) },
            onNotesChange = { onAction(WorkoutAction.UpdateExerciseDefaultNotes(it)) },
            onDismiss = { onAction(WorkoutAction.DismissExerciseEditor) },
            onSave = { onAction(WorkoutAction.SaveExercise) },
        )
    }

    uiState.pendingDeleteExercise?.let { exercise ->
        DeleteExerciseDialog(
            exercise = exercise,
            onDismiss = { onAction(WorkoutAction.DismissDeleteExercise) },
            onConfirm = { onAction(WorkoutAction.ConfirmDeleteExercise) },
        )
    }

    uiState.pendingDeleteRoutine?.let { routine ->
        DeleteRoutineDialog(
            routine = routine,
            onDismiss = { onAction(WorkoutAction.DismissDeleteRoutine) },
            onConfirm = { onAction(WorkoutAction.ConfirmDeleteRoutine) },
        )
    }
}

@Composable
private fun WorkoutSectionSwitcher(
    selected: WorkoutTabSection,
    onSelect: (WorkoutTabSection) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WorkoutSegmentTab(
            label = "ROUTINES",
            selected = selected == WorkoutTabSection.ROUTINES,
            onClick = { onSelect(WorkoutTabSection.ROUTINES) },
            modifier = Modifier
                .weight(1f)
                .testTag("workout_segment_routines"),
        )
        WorkoutSegmentTab(
            label = "EXERCISES",
            selected = selected == WorkoutTabSection.EXERCISES,
            onClick = { onSelect(WorkoutTabSection.EXERCISES) },
            modifier = Modifier
                .weight(1f)
                .testTag("workout_segment_exercises"),
        )
    }
}

@Composable
private fun WorkoutSegmentTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) ArkhamColors.PhosphorGreen else ArkhamColors.TerminalEmerald,
        animationSpec = tween(durationMillis = ArkhamMotion.STATE_MS),
        label = "segmentBorder",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            ArkhamColors.TerminalEmerald.copy(alpha = 0.5f)
        } else {
            ArkhamColors.ObsidianPanel
        },
        animationSpec = tween(durationMillis = ArkhamMotion.STATE_MS),
        label = "segmentContainer",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) ArkhamColors.PhosphorGreen else ArkhamColors.MintMuted,
        animationSpec = tween(durationMillis = ArkhamMotion.STATE_MS),
        label = "segmentText",
    )

    Surface(
        modifier = modifier.selectable(
            selected = selected,
            onClick = onClick,
            role = Role.Tab,
        ),
        shape = ArkhamShapes.chip,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
            )
        }
    }
}

@Composable
private fun RoutinesSection(
    routines: List<Routine>,
    exercisesById: Map<String, Exercise>,
    onAction: (WorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArkhamSectionHeader(
            title = "ROUTINES",
            telemetryLabel = String.format(Locale.US, "%02d BUILT", routines.size),
        )

        if (routines.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                ArkhamHudPanel(
                    modifier = Modifier.fillMaxWidth(),
                    highlighted = true,
                ) {
                    Text(
                        text = "NO ROUTINES BUILT",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArkhamColors.MintWhite,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Build reusable Routines under a primary Discipline and order Exercises from any Discipline, even creating new Exercises inline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArkhamColors.MintMuted,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ArkhamPrimaryButton(
                        text = "CREATE FIRST ROUTINE",
                        onClick = { onAction(WorkoutAction.OpenCreateRoutine) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("routine_create_first_button"),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("routines_list"),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "create_routine_action") {
                    ArkhamPrimaryButton(
                        text = "+ NEW ROUTINE",
                        onClick = { onAction(WorkoutAction.OpenCreateRoutine) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("routine_create_button"),
                    )
                }
                items(
                    items = routines,
                    key = { it.id },
                ) { routine ->
                    RoutineCard(
                        routine = routine,
                        exercisesById = exercisesById,
                        onEditClick = { onAction(WorkoutAction.OpenEditRoutine(routine)) },
                        onDeleteClick = { onAction(WorkoutAction.RequestDeleteRoutine(routine)) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(ArkhamMotion.TRANSITION_MS),
                            fadeOutSpec = tween(ArkhamMotion.STATE_MS),
                            placementSpec = tween(ArkhamMotion.TRANSITION_MS),
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineCard(
    routine: Routine,
    exercisesById: Map<String, Exercise>,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val countLabel = if (routine.exercises.size == 1) {
        "1 EXERCISE"
    } else {
        "${routine.exercises.size} EXERCISES"
    }

    ArkhamHudPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("routine_card_${routine.id}"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = routine.name,
                style = MaterialTheme.typography.titleMedium,
                color = ArkhamColors.MintWhite,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = ArkhamIcons.Edit,
                        contentDescription = "Edit ${routine.name}",
                        tint = ArkhamColors.PhosphorGreenDim,
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = ArkhamIcons.Delete,
                        contentDescription = "Delete ${routine.name}",
                        tint = ArkhamColors.AlertRed,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ArkhamHudBadge(
                label = routine.discipline.displayName,
                accentColor = ArkhamColors.PhosphorGreen,
            )
            ArkhamHudBadge(
                label = countLabel,
                accentColor = ArkhamColors.PhosphorGreenDim,
            )
        }
        if (routine.exercises.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                routine.exercises.forEachIndexed { index, entry ->
                    val exercise = exercisesById[entry.exerciseId]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d", index + 1),
                            style = ArkhamTelemetryStyle,
                            color = ArkhamColors.PhosphorGreenDim,
                        )
                        Text(
                            text = exercise?.name ?: "Archived Exercise",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ArkhamColors.MintWhite,
                            modifier = Modifier.weight(1f),
                        )
                        if (exercise != null) {
                            ArkhamHudBadge(
                                label = exercise.discipline.displayName,
                                accentColor = ArkhamColors.MintMuted,
                            )
                        }
                        entry.defaultTargetSets?.let { sets ->
                            ArkhamHudBadge(
                                label = if (sets == 1) "1 SET" else "$sets SETS",
                                accentColor = ArkhamColors.PhosphorGreen,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseLibrarySection(
    exercises: List<Exercise>,
    onAction: (WorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArkhamSectionHeader(
            title = "EXERCISE LIBRARY",
            telemetryLabel = String.format(Locale.US, "%02d CATALOGED", exercises.size),
        )

        if (exercises.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                ArkhamHudPanel(
                    modifier = Modifier.fillMaxWidth(),
                    highlighted = true,
                ) {
                    Text(
                        text = "NO EXERCISES CATALOGED",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArkhamColors.MintWhite,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Build your personal movement catalog from scratch. Model each progression with its own Discipline and Tracking Mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArkhamColors.MintMuted,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    ArkhamPrimaryButton(
                        text = "ADD FIRST EXERCISE",
                        onClick = { onAction(WorkoutAction.OpenCreateExercise) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "create_exercise_action") {
                    ArkhamPrimaryButton(
                        text = "+ NEW EXERCISE",
                        onClick = { onAction(WorkoutAction.OpenCreateExercise) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                items(
                    items = exercises,
                    key = { it.id },
                ) { exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        onEditClick = { onAction(WorkoutAction.OpenEditExercise(exercise)) },
                        onDeleteClick = { onAction(WorkoutAction.RequestDeleteExercise(exercise)) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(ArkhamMotion.TRANSITION_MS),
                            fadeOutSpec = tween(ArkhamMotion.STATE_MS),
                            placementSpec = tween(ArkhamMotion.TRANSITION_MS),
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    exercise: Exercise,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ArkhamHudPanel(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = ArkhamColors.MintWhite,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = ArkhamIcons.Edit,
                        contentDescription = "Edit ${exercise.name}",
                        tint = ArkhamColors.PhosphorGreenDim,
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = ArkhamIcons.Delete,
                        contentDescription = "Delete ${exercise.name}",
                        tint = ArkhamColors.AlertRed,
                    )
                }
            }
        }
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
        exercise.defaultNotes?.let { notes ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                color = ArkhamColors.MintMuted,
            )
        }
    }
}
