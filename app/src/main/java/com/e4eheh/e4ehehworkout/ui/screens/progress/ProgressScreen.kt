package com.e4eheh.e4ehehworkout.ui.screens.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors

/** Blank-slate Progress tab shell awaiting Session analytics integration. */
@Composable
fun ProgressScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ArkhamSectionHeader(
            title = "PROGRESS TELEMETRY",
            telemetryLabel = "RANGE // STANDBY",
        )
        ArkhamHudPanel(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "NO TRAINING TELEMETRY YET",
                style = MaterialTheme.typography.titleMedium,
                color = ArkhamColors.MintWhite,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Complete Sessions to unlock Personal Records, weekly streaks, and Discipline breakdowns.",
                style = MaterialTheme.typography.bodyMedium,
                color = ArkhamColors.MintMuted,
            )
        }
    }
}
