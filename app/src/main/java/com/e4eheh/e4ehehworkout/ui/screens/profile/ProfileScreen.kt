package com.e4eheh.e4ehehworkout.ui.screens.profile

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
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle

/** Blank-slate Profile tab shell awaiting preferences and backup controls. */
@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ArkhamSectionHeader(
            title = "OPERATOR PROFILE",
            telemetryLabel = "LOCAL VAULT",
        )
        ArkhamHudPanel(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "CALLSIGN: OPERATOR",
                style = MaterialTheme.typography.titleMedium,
                color = ArkhamColors.MintWhite,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "STORAGE: LOCAL OFFLINE-FIRST",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreenDim,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Preferences, canonical units, and JSON backup controls attach to this terminal.",
                style = MaterialTheme.typography.bodyMedium,
                color = ArkhamColors.MintMuted,
            )
        }
    }
}
