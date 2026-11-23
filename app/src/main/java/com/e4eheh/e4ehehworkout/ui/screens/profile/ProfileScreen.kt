package com.e4eheh.e4ehehworkout.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.unit.UnitConverter
import com.e4eheh.e4ehehworkout.ui.components.ArkhamChipSelectorSection
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.components.arkhamTextFieldColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamShapes
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale

private val REST_TIMER_PRESETS = listOf(30, 60, 90, 120, 180)

/** Profile tab housing Batcomputer identity, measurement units, Rest Timer defaults, and sync placeholder. */
@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onAction: (ProfileAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ArkhamSectionHeader(
            title = "OPERATOR PROFILE",
            telemetryLabel = "LOCAL VAULT",
        )

        OperatorIdentityPanel(
            uiState = uiState,
            onDraftChange = { onAction(ProfileAction.UpdateDisplayNameDraft(it)) },
            onSave = { onAction(ProfileAction.SaveDisplayName) },
        )

        MeasurementUnitsPanel(
            weightUnit = uiState.preferences.weightUnit,
            distanceUnit = uiState.preferences.distanceUnit,
            onSelectWeightUnit = { onAction(ProfileAction.SelectWeightUnit(it)) },
            onSelectDistanceUnit = { onAction(ProfileAction.SelectDistanceUnit(it)) },
        )

        DefaultRestTimerPanel(
            restTimerSeconds = uiState.preferences.defaultRestTimerSeconds,
            onAdjust = { onAction(ProfileAction.AdjustRestTimer(it)) },
            onSelectPreset = { onAction(ProfileAction.SelectRestTimer(it)) },
        )

        SyncPlaceholderPanel()
    }
}

@Composable
private fun OperatorIdentityPanel(
    uiState: ProfileUiState,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val statusText = when {
        uiState.displayNameSaved && !uiState.hasUnsavedDisplayName -> "CALLSIGN SAVED LOCALLY"
        uiState.hasUnsavedDisplayName -> "UNSAVED CHANGES"
        else -> "ACTIVE CALLSIGN"
    }
    val statusColor = if (uiState.hasUnsavedDisplayName) {
        ArkhamColors.MintWhite
    } else {
        ArkhamColors.PhosphorGreenDim
    }

    ArkhamHudPanel(
        modifier = Modifier.fillMaxWidth(),
        highlighted = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "CALLSIGN: ${uiState.preferences.displayName}",
                style = MaterialTheme.typography.titleMedium,
                color = ArkhamColors.MintWhite,
            )
            OutlinedTextField(
                value = uiState.displayNameDraft,
                onValueChange = onDraftChange,
                label = { Text("Display Name") },
                singleLine = true,
                isError = uiState.displayNameError != null,
                supportingText = uiState.displayNameError?.let { error ->
                    {
                        Text(
                            text = error,
                            color = ArkhamColors.AlertRed,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                shape = ArkhamShapes.chip,
                colors = arkhamTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_display_name_input"),
            )
            Text(
                text = statusText,
                style = ArkhamTelemetryStyle,
                color = statusColor,
            )
            ArkhamPrimaryButton(
                text = "SAVE CALLSIGN",
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_save_name_button"),
            )
        }
    }
}

@Composable
private fun MeasurementUnitsPanel(
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
    onSelectWeightUnit: (WeightUnit) -> Unit,
    onSelectDistanceUnit: (DistanceUnit) -> Unit,
) {
    ArkhamHudPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MEASUREMENT UNITS",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.PhosphorGreenDim,
                )
                ArkhamHudBadge(label = "CANONICAL: kg / km")
            }
            Text(
                text = "Stored canonically in kg and km. Switching units converts display and input values without mutating stored history.",
                style = MaterialTheme.typography.bodyMedium,
                color = ArkhamColors.MintMuted,
            )
            ArkhamChipSelectorSection(
                title = "WEIGHT UNIT",
                options = WeightUnit.entries,
                selected = weightUnit,
                labelOf = { it.symbol },
                onSelect = onSelectWeightUnit,
                testTagOf = { "weight_unit_${it.symbol}" },
            )
            ArkhamChipSelectorSection(
                title = "DISTANCE UNIT",
                options = DistanceUnit.entries,
                selected = distanceUnit,
                labelOf = { it.symbol },
                onSelect = onSelectDistanceUnit,
                testTagOf = { "distance_unit_${it.symbol}" },
            )
            Text(
                text = "PREVIEW: ${UnitConverter.formatWeight(100.0, weightUnit)}  //  " +
                    UnitConverter.formatDistance(5.0, distanceUnit),
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreenDim,
                modifier = Modifier.testTag("profile_unit_preview"),
            )
        }
    }
}

@Composable
private fun DefaultRestTimerPanel(
    restTimerSeconds: Int,
    onAdjust: (Int) -> Unit,
    onSelectPreset: (Int) -> Unit,
) {
    ArkhamHudPanel(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "DEFAULT REST TIMER",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.PhosphorGreenDim,
                )
                Text(
                    text = "${restTimerSeconds}s // ${formatTimerClock(restTimerSeconds)}",
                    style = ArkhamTelemetryLargeStyle,
                    color = ArkhamColors.PhosphorGreen,
                    modifier = Modifier.testTag("profile_rest_timer_value"),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ArkhamSecondaryButton(
                    text = "-15s",
                    enabled = restTimerSeconds > UserPreferences.MIN_REST_TIMER_SECONDS,
                    onClick = { onAdjust(-UserPreferences.REST_TIMER_STEP_SECONDS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("rest_timer_decrement"),
                )
                ArkhamSecondaryButton(
                    text = "+15s",
                    enabled = restTimerSeconds < UserPreferences.MAX_REST_TIMER_SECONDS,
                    onClick = { onAdjust(UserPreferences.REST_TIMER_STEP_SECONDS) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("rest_timer_increment"),
                )
            }
            ArkhamChipSelectorSection(
                title = "PRESETS",
                options = REST_TIMER_PRESETS,
                selected = restTimerSeconds,
                labelOf = { "${it}s" },
                onSelect = onSelectPreset,
                testTagOf = { "rest_timer_preset_${it}s" },
            )
        }
    }
}

@Composable
private fun SyncPlaceholderPanel() {
    ArkhamHudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_sync_placeholder"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Server & API Key Sync",
                style = MaterialTheme.typography.titleMedium,
                color = ArkhamColors.MintWhite,
                modifier = Modifier.weight(1f),
            )
            ArkhamHudBadge(
                label = "OFFLINE ONLY",
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "MODE: LOCAL OFFLINE-FIRST",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreenDim,
            )
            Text(
                text = "NETWORK: DISABLED // NO NETWORK CALLS",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.MintMuted,
            )
            Text(
                text = "ENDPOINT: NOT CONFIGURED",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.MintMuted,
            )
            Text(
                text = "API KEY: NOT CONFIGURED",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.MintMuted,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "All training records remain stored locally on this device with zero network calls. Custom backend server and API key synchronization will connect here in a future release.",
            style = MaterialTheme.typography.bodyMedium,
            color = ArkhamColors.MintMuted,
        )
    }
}

private fun formatTimerClock(totalSeconds: Int): String =
    String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
