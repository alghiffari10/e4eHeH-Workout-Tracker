package com.e4eheh.e4ehehworkout.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamShapes
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle

/** Form state for creating a new Exercise inline inside a picker without leaving the screen. */
data class InlineExerciseDraft(
    val name: String = "",
    val discipline: Discipline,
    val trackingMode: TrackingMode = TrackingMode.REPS_AND_LOAD,
    val nameError: String? = null,
    val isSaving: Boolean = false,
)

/** Core Exercise input fields shared between the library modal and inline Exercise creators. */
@Composable
fun ExerciseCoreFormFields(
    name: String,
    nameError: String?,
    discipline: Discipline,
    trackingMode: TrackingMode,
    onNameChange: (String) -> Unit,
    onDisciplineChange: (Discipline) -> Unit,
    onTrackingModeChange: (TrackingMode) -> Unit,
    nameTestTag: String,
    disciplineTestTagOf: ((Discipline) -> String)? = null,
    trackingModeTestTagOf: ((TrackingMode) -> String)? = null,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("Exercise Name") },
        singleLine = true,
        isError = nameError != null,
        supportingText = nameError?.let { error ->
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
            .testTag(nameTestTag),
    )

    ArkhamChipSelectorSection(
        title = "DISCIPLINE",
        options = Discipline.entries,
        selected = discipline,
        labelOf = { it.displayName },
        onSelect = onDisciplineChange,
        testTagOf = disciplineTestTagOf,
    )

    ArkhamChipSelectorSection(
        title = "TRACKING MODE",
        options = TrackingMode.entries,
        selected = trackingMode,
        labelOf = { it.displayName },
        onSelect = onTrackingModeChange,
        testTagOf = trackingModeTestTagOf,
    )
}

/** Shared Exercise picker with Discipline filters and inline Exercise creation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArkhamExercisePickerView(
    exercises: List<Exercise>,
    alreadyAddedExerciseIds: Set<String>,
    alreadyAddedBadgeLabel: String,
    emptyLibraryMessage: String,
    disciplineFilter: Discipline?,
    inlineExercise: InlineExerciseDraft?,
    onClosePicker: () -> Unit,
    onSelectDisciplineFilter: (Discipline?) -> Unit,
    onAddExercise: (String) -> Unit,
    onOpenInlineCreator: () -> Unit,
    onCloseInlineCreator: () -> Unit,
    onInlineNameChange: (String) -> Unit,
    onInlineDisciplineChange: (Discipline) -> Unit,
    onInlineTrackingModeChange: (TrackingMode) -> Unit,
    onSaveInlineExercise: () -> Unit,
    modifier: Modifier = Modifier,
    headerTitle: String = "ADD EXERCISE",
) {
    val canNavigateBack = inlineExercise?.isSaving != true
    val handleBack = {
        if (inlineExercise != null) {
            onCloseInlineCreator()
        } else {
            onClosePicker()
        }
    }

    BackHandler(enabled = canNavigateBack, onBack = handleBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ArkhamTopHeader(
            title = headerTitle,
            backTestTag = "picker_back_button",
            onBack = handleBack,
            backEnabled = canNavigateBack,
        )

        if (inlineExercise != null) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                ArkhamHudPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inline_exercise_panel"),
                    highlighted = true,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "NEW EXERCISE (INLINE)",
                            style = ArkhamTelemetryStyle,
                            color = ArkhamColors.PhosphorGreen,
                        )
                        ExerciseCoreFormFields(
                            name = inlineExercise.name,
                            nameError = inlineExercise.nameError,
                            discipline = inlineExercise.discipline,
                            trackingMode = inlineExercise.trackingMode,
                            onNameChange = onInlineNameChange,
                            onDisciplineChange = onInlineDisciplineChange,
                            onTrackingModeChange = onInlineTrackingModeChange,
                            nameTestTag = "inline_exercise_name_input",
                            disciplineTestTagOf = { "inline_exercise_discipline_${it.name}" },
                            trackingModeTestTagOf = { "inline_exercise_tracking_mode_${it.name}" },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ArkhamSecondaryButton(
                                text = "Cancel",
                                enabled = !inlineExercise.isSaving,
                                onClick = onCloseInlineCreator,
                                modifier = Modifier.weight(1f),
                            )
                            ArkhamPrimaryButton(
                                text = "Create & Add",
                                enabled = !inlineExercise.isSaving,
                                onClick = onSaveInlineExercise,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("inline_exercise_save_button"),
                            )
                        }
                    }
                }
            }
        } else {
            ArkhamPrimaryButton(
                text = "+ CREATE NEW EXERCISE INLINE",
                onClick = onOpenInlineCreator,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("picker_inline_create_button"),
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArkhamHudChip(
                    label = "All",
                    selected = disciplineFilter == null,
                    onClick = { onSelectDisciplineFilter(null) },
                    modifier = Modifier.testTag("picker_filter_all"),
                )
                Discipline.entries.forEach { discipline ->
                    ArkhamHudChip(
                        label = discipline.displayName,
                        selected = disciplineFilter == discipline,
                        onClick = { onSelectDisciplineFilter(discipline) },
                        modifier = Modifier.testTag("picker_filter_${discipline.name}"),
                    )
                }
            }

            val filteredExercises = if (disciplineFilter == null) {
                exercises
            } else {
                exercises.filter { it.discipline == disciplineFilter }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("picker_exercise_list"),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    exercises.isEmpty() -> {
                        item(key = "empty_library") {
                            ArkhamHudPanel(
                                modifier = Modifier.fillMaxWidth(),
                                highlighted = true,
                            ) {
                                Text(
                                    text = "EXERCISE LIBRARY EMPTY",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ArkhamColors.MintWhite,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = emptyLibraryMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ArkhamColors.MintMuted,
                                )
                            }
                        }
                    }

                    filteredExercises.isEmpty() -> {
                        item(key = "empty_filter") {
                            ArkhamHudPanel(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "NO EXERCISES IN THIS DISCIPLINE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ArkhamColors.MintWhite,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Select another Discipline filter or create a new Exercise inline.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ArkhamColors.MintMuted,
                                )
                            }
                        }
                    }

                    else -> {
                        items(
                            items = filteredExercises,
                            key = { it.id },
                        ) { exercise ->
                            PickerExerciseRow(
                                exercise = exercise,
                                alreadyAdded = exercise.id in alreadyAddedExerciseIds,
                                alreadyAddedBadgeLabel = alreadyAddedBadgeLabel,
                                onAdd = { onAddExercise(exercise.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickerExerciseRow(
    exercise: Exercise,
    alreadyAdded: Boolean,
    alreadyAddedBadgeLabel: String,
    onAdd: () -> Unit,
) {
    ArkhamHudPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = ArkhamColors.MintWhite,
                )
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
                    if (alreadyAdded) {
                        ArkhamHudBadge(
                            label = alreadyAddedBadgeLabel,
                            accentColor = ArkhamColors.MintWhite,
                        )
                    }
                }
            }
            ArkhamSecondaryButton(
                text = "ADD",
                onClick = onAdd,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag("picker_exercise_${exercise.id}"),
            )
        }
    }
}
