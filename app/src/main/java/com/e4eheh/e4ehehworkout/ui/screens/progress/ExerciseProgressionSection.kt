package com.e4eheh.e4ehehworkout.ui.screens.progress

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.analytics.ExercisePersonalRecord
import com.e4eheh.e4ehehworkout.domain.analytics.ExerciseProgressionReport
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressionMetric
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudChip
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSegmentTab
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale

/** Exercise Progression section showing selector, Personal Records, metric toggle, and HUD line chart. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExerciseProgressionSection(
    uiState: ProgressUiState,
    onAction: (ProgressAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val report = uiState.exerciseProgression

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArkhamSectionHeader(
            title = "EXERCISE PROGRESSION",
            telemetryLabel = report?.exercise?.trackingMode?.displayName?.uppercase(Locale.US)
                ?: "NO EXERCISES",
        )

        if (uiState.exercises.isEmpty() || report == null) {
            ArkhamHudPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_exercise_empty_library"),
            ) {
                Text(
                    text = "NO EXERCISES IN LIBRARY",
                    style = MaterialTheme.typography.titleSmall,
                    color = ArkhamColors.MintWhite,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Create Exercises on the Workout tab to track Personal Records and progression charts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArkhamColors.MintMuted,
                )
            }
            return
        }

        ArkhamHudPanel(
            highlighted = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("progress_exercise_selector")
                .clickable(role = Role.Button) {
                    onAction(ProgressAction.OpenExercisePicker)
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SELECTED EXERCISE",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.PhosphorGreenDim,
                )
                Text(
                    text = "CHANGE ▾",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.PhosphorGreen,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = report.exercise.name,
                style = MaterialTheme.typography.titleMedium,
                color = ArkhamColors.MintWhite,
                modifier = Modifier.testTag("progress_selected_exercise_name"),
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExerciseMetadataBadges(exercise = report.exercise) {
                if (report.exercise.trackingMode == TrackingMode.REPS_AND_LOAD) {
                    ArkhamHudBadge(
                        label = if (report.isWeightedRepsAndLoad) "WEIGHTED" else "BODYWEIGHT",
                        accentColor = ArkhamColors.MintMuted,
                    )
                }
            }
        }

        PersonalRecordsRow(report = report)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            report.availableMetrics.forEach { metric ->
                ArkhamSegmentTab(
                    label = metric.label,
                    selected = metric == report.selectedMetric,
                    onClick = { onAction(ProgressAction.SelectProgressionMetric(metric)) },
                    labelPrefix = "metric_${metric.name}",
                    modifier = Modifier
                        .weight(1f)
                        .testTag("progress_metric_toggle_${metric.name}"),
                )
            }
        }

        ExerciseProgressionChart(report = report)
    }

    if (uiState.isExercisePickerOpen) {
        ExerciseProgressionPickerSheet(
            exercises = uiState.exercises,
            selectedExerciseId = uiState.selectedExerciseId,
            disciplineFilter = uiState.pickerDisciplineFilter,
            onSelectDisciplineFilter = {
                onAction(ProgressAction.SelectPickerDisciplineFilter(it))
            },
            onSelectExercise = { onAction(ProgressAction.SelectExercise(it)) },
            onDismiss = { onAction(ProgressAction.DismissExercisePicker) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseMetadataBadges(
    exercise: Exercise,
    trailingBadge: (@Composable FlowRowScope.() -> Unit)? = null,
) {
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
        trailingBadge?.invoke(this)
    }
}

@Composable
private fun PersonalRecordsRow(report: ExerciseProgressionReport) {
    val prsByMetric = report.personalRecords.associateBy { it.metric }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("progress_pr_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "PERSONAL RECORDS // ALL-TIME",
            style = ArkhamTelemetryStyle,
            color = ArkhamColors.PhosphorGreenDim,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            report.availableMetrics.forEach { metric ->
                PersonalRecordCard(
                    metric = metric,
                    record = prsByMetric[metric],
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun PersonalRecordCard(
    metric: ProgressionMetric,
    record: ExercisePersonalRecord?,
    modifier: Modifier = Modifier,
) {
    ArkhamHudPanel(
        modifier = modifier.testTag("progress_pr_card_${metric.name}"),
        highlighted = record != null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = metric.prTitle,
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.MintMuted,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
            )
            ArkhamHudBadge(
                label = "PR",
                accentColor = if (record != null) {
                    ArkhamColors.TacticalAmber
                } else {
                    ArkhamColors.MintMuted
                },
                modifier = Modifier.testTag("progress_pr_badge_${metric.name}"),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = record?.formattedValue ?: "--",
            style = ArkhamTelemetryLargeStyle,
            color = if (record != null) {
                ArkhamColors.TacticalAmber
            } else {
                ArkhamColors.MintMuted
            },
            modifier = Modifier.testTag("progress_pr_value_${metric.name}"),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = record?.let { "SET ${it.dateLabel.uppercase(Locale.US)}" } ?: "NO RECORD YET",
            style = MaterialTheme.typography.labelSmall,
            color = ArkhamColors.MintMuted,
            modifier = Modifier.testTag("progress_pr_date_${metric.name}"),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExerciseProgressionPickerSheet(
    exercises: List<Exercise>,
    selectedExerciseId: String?,
    disciplineFilter: Discipline?,
    onSelectDisciplineFilter: (Discipline?) -> Unit,
    onSelectExercise: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filteredExercises = if (disciplineFilter == null) {
        exercises
    } else {
        exercises.filter { it.discipline == disciplineFilter }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ArkhamColors.ObsidianPanel,
        contentColor = ArkhamColors.MintWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("progress_exercise_picker_sheet")
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "SELECT EXERCISE",
                style = ArkhamTelemetryLargeStyle,
                color = ArkhamColors.PhosphorGreen,
            )
            Text(
                text = "INSPECT PERSONAL RECORDS & PROGRESSION TELEMETRY",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreenDim,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArkhamHudChip(
                    label = "All",
                    selected = disciplineFilter == null,
                    onClick = { onSelectDisciplineFilter(null) },
                    modifier = Modifier.testTag("progress_picker_filter_all"),
                )
                Discipline.entries.forEach { discipline ->
                    ArkhamHudChip(
                        label = discipline.displayName,
                        selected = disciplineFilter == discipline,
                        onClick = { onSelectDisciplineFilter(discipline) },
                        modifier = Modifier.testTag("progress_picker_filter_${discipline.name}"),
                    )
                }
            }

            if (filteredExercises.isEmpty()) {
                ArkhamHudPanel(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "NO EXERCISES IN THIS DISCIPLINE",
                        style = MaterialTheme.typography.titleSmall,
                        color = ArkhamColors.MintWhite,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select another Discipline filter or add Exercises on the Workout tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArkhamColors.MintMuted,
                    )
                }
            } else {
                filteredExercises.forEach { exercise ->
                    val isSelected = exercise.id == selectedExerciseId
                    ArkhamHudPanel(
                        highlighted = isSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("progress_exercise_option_${exercise.id}")
                            .clickable(role = Role.Button) {
                                onSelectExercise(exercise.id)
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = exercise.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = ArkhamColors.MintWhite,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                            )
                            if (isSelected) {
                                ArkhamHudBadge(
                                    label = "SELECTED",
                                    accentColor = ArkhamColors.PhosphorGreen,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ExerciseMetadataBadges(exercise = exercise)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            ArkhamSecondaryButton(
                text = "Dismiss",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_exercise_picker_dismiss"),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
