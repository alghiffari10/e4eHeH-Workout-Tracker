package com.e4eheh.e4ehehworkout.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale

/** Blank-slate Batcomputer Home screen with zero forced onboarding wizard. */
@Composable
fun HomeScreen(
    exerciseCount: Int,
    onNavigateToWorkout: () -> Unit,
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
            title = "BATCOMPUTER // HOME",
            telemetryLabel = "SYS // STANDBY",
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "WELCOME BACK, OPERATOR",
                style = MaterialTheme.typography.headlineMedium,
                color = ArkhamColors.MintWhite,
            )
            Text(
                text = "SYSTEM ONLINE // OFFLINE MODE",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreenDim,
            )
        }
        ArkhamHudPanel(
            modifier = Modifier.fillMaxWidth(),
            highlighted = true,
        ) {
            Text(
                text = "NO SESSIONS LOGGED YET",
                style = MaterialTheme.typography.titleMedium,
                color = ArkhamColors.MintWhite,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Blank-slate telemetry initialized. Build your Exercise catalog on the Workout tab or tap + for quick actions.",
                style = MaterialTheme.typography.bodyMedium,
                color = ArkhamColors.MintMuted,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = String.format(Locale.US, "%02d EXERCISES", exerciseCount),
                style = ArkhamTelemetryLargeStyle,
                color = ArkhamColors.PhosphorGreen,
            )
            Spacer(modifier = Modifier.height(14.dp))
            ArkhamPrimaryButton(
                text = "OPEN EXERCISE LIBRARY",
                onClick = onNavigateToWorkout,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
