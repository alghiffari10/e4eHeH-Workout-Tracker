package com.e4eheh.e4ehehworkout.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle

/** Two-tap START deployment sheet listing saved Routines (suggested next first) and blank Session launch. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeStartPickerSheet(
    routineCards: List<HomeRoutineCardUi>,
    isStartingSession: Boolean,
    onStartRoutine: (String) -> Unit,
    onStartBlankSession: () -> Unit,
    onNavigateToWorkout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
                .testTag("home_start_picker_sheet")
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "START SESSION",
                style = ArkhamTelemetryLargeStyle,
                color = ArkhamColors.PhosphorGreen,
            )
            Text(
                text = "SELECT ROUTINE OR BLANK DEPLOY",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreenDim,
            )

            ArkhamSectionHeader(
                title = "SAVED ROUTINES",
                telemetryLabel = "${routineCards.size} AVAILABLE",
            )

            if (routineCards.isEmpty()) {
                ArkhamHudPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_start_picker_empty_routines"),
                ) {
                    Text(
                        text = "NO ROUTINES SAVED YET",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArkhamColors.MintWhite,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Create reusable Routines on the Workout tab to launch them in two taps from Home.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArkhamColors.MintMuted,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ArkhamPrimaryButton(
                        text = "OPEN WORKOUT TAB",
                        onClick = onNavigateToWorkout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_start_picker_go_workout"),
                    )
                }
            } else {
                routineCards.forEach { card ->
                    ArkhamHudPanel(
                        highlighted = card.isSuggestedNext,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_start_picker_routine_${card.routineId}")
                            .clickable(
                                enabled = !isStartingSession,
                                role = Role.Button,
                                onClick = { onStartRoutine(card.routineId) },
                            ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = card.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = ArkhamColors.MintWhite,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                            )
                            if (card.isSuggestedNext) {
                                ArkhamHudBadge(
                                    label = "SUGGESTED NEXT",
                                    accentColor = ArkhamColors.TacticalAmber,
                                    modifier = Modifier.testTag(
                                        "home_start_picker_suggested_badge_${card.routineId}",
                                    ),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HomeRoutineMetadataBadges(card = card)
                    }
                }
            }

            ArkhamSectionHeader(title = "FREEFORM DEPLOY")
            ArkhamHudPanel(
                highlighted = routineCards.isEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_start_picker_blank_session")
                    .clickable(
                        enabled = !isStartingSession,
                        role = Role.Button,
                        onClick = onStartBlankSession,
                    ),
            ) {
                Text(
                    text = "Start Live Blank Session",
                    style = MaterialTheme.typography.titleMedium,
                    color = ArkhamColors.MintWhite,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "LIVE TIMER // AUTO-SAVED DRAFT",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.PhosphorGreen,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            ArkhamSecondaryButton(
                text = "Dismiss",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_start_picker_dismiss"),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeRoutineMetadataBadges(
    card: HomeRoutineCardUi,
    showDisciplineBadge: Boolean = true,
    exerciseCountTestTag: String? = null,
    durationTestTag: String? = null,
) {
    val exerciseCountLabel = if (card.exerciseCount == 1) {
        "1 EXERCISE"
    } else {
        "${card.exerciseCount} EXERCISES"
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showDisciplineBadge) {
            ArkhamHudBadge(
                label = card.discipline.displayName,
                accentColor = ArkhamColors.PhosphorGreen,
            )
        }
        ArkhamHudBadge(
            label = exerciseCountLabel,
            accentColor = ArkhamColors.PhosphorGreenDim,
            modifier = exerciseCountTestTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
        ArkhamHudBadge(
            label = card.durationLabel,
            accentColor = ArkhamColors.MintMuted,
            modifier = durationTestTag?.let { Modifier.testTag(it) } ?: Modifier,
        )
    }
}
