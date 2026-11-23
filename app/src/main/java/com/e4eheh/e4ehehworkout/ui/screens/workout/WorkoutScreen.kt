package com.e4eheh.e4ehehworkout.ui.screens.workout

import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamIcons
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamMotion
import java.util.Locale

/** Workout tab housing the athlete's personal Exercise library. */
@Composable
fun WorkoutScreen(
    uiState: ExerciseLibraryUiState,
    onAction: (ExerciseLibraryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ArkhamSectionHeader(
            title = "EXERCISE LIBRARY",
            telemetryLabel = String.format(Locale.US, "%02d CATALOGED", uiState.exercises.size),
        )

        if (uiState.exercises.isEmpty()) {
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
                        onClick = { onAction(ExerciseLibraryAction.OpenCreateDialog) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            ArkhamPrimaryButton(
                text = "+ NEW EXERCISE",
                onClick = { onAction(ExerciseLibraryAction.OpenCreateDialog) },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = uiState.exercises,
                    key = { it.id },
                ) { exercise ->
                    ExerciseCard(
                        exercise = exercise,
                        onEditClick = { onAction(ExerciseLibraryAction.OpenEditDialog(exercise)) },
                        onDeleteClick = { onAction(ExerciseLibraryAction.RequestDelete(exercise)) },
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

    uiState.editorState?.let { editorState ->
        ExerciseEditorDialog(
            state = editorState,
            onNameChange = { onAction(ExerciseLibraryAction.UpdateName(it)) },
            onDisciplineChange = { onAction(ExerciseLibraryAction.UpdateDiscipline(it)) },
            onTrackingModeChange = { onAction(ExerciseLibraryAction.UpdateTrackingMode(it)) },
            onNotesChange = { onAction(ExerciseLibraryAction.UpdateDefaultNotes(it)) },
            onDismiss = { onAction(ExerciseLibraryAction.DismissEditor) },
            onSave = { onAction(ExerciseLibraryAction.SaveExercise) },
        )
    }

    uiState.pendingDeleteExercise?.let { exercise ->
        DeleteExerciseDialog(
            exercise = exercise,
            onDismiss = { onAction(ExerciseLibraryAction.DismissDelete) },
            onConfirm = { onAction(ExerciseLibraryAction.ConfirmDelete) },
        )
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
