package com.e4eheh.e4ehehworkout.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.ui.components.ArkhamDeleteConfirmationDialog
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamPrimaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.components.formatElapsedDuration
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale
import kotlinx.coroutines.delay

/** Batcomputer Home screen with IN_PROGRESS Session resume banner and blank-slate overview. */
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    onResumeSession: (String) -> Unit,
    onNavigateToWorkout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(uiState.activeSessionBanner?.sessionId) {
        if (uiState.activeSessionBanner != null) {
            while (true) {
                delay(1_000L)
                onAction(HomeAction.RefreshElapsed)
            }
        }
    }

    uiState.pendingDiscardSession?.let { pending ->
        ArkhamDeleteConfirmationDialog(
            title = "DISCARD ACTIVE SESSION?",
            itemName = pending.displayTitle,
            message = "This will remove the in-progress Session draft and all logged Sets.",
            onDismiss = { onAction(HomeAction.DismissDiscardDialog) },
            onConfirm = { onAction(HomeAction.ConfirmDiscardActiveSession) },
            confirmLabel = "Discard",
            confirmTestTag = "home_discard_confirm_button",
            dismissTestTag = "home_discard_cancel_button",
        )
    }

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

        uiState.activeSessionBanner?.let { banner ->
            ActiveSessionResumeBanner(
                banner = banner,
                isDiscarding = uiState.isDiscarding,
                onResume = { onResumeSession(banner.sessionId) },
                onDiscard = { onAction(HomeAction.RequestDiscardActiveSession) },
            )
        }

        ArkhamHudPanel(
            modifier = Modifier.fillMaxWidth(),
            highlighted = uiState.activeSessionBanner == null,
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
                text = String.format(Locale.US, "%02d EXERCISES", uiState.exerciseCount),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveSessionResumeBanner(
    banner: ActiveSessionBannerUi,
    isDiscarding: Boolean,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    ArkhamHudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_resume_session_banner"),
        highlighted = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "RESUME ACTIVE SESSION",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.PhosphorGreen,
                )
                Text(
                    text = formatElapsedDuration(banner.elapsedSeconds),
                    style = ArkhamTelemetryLargeStyle,
                    color = ArkhamColors.PhosphorGreen,
                    modifier = Modifier.testTag("home_resume_session_elapsed"),
                )
            }

            Text(
                text = banner.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                color = ArkhamColors.MintWhite,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArkhamHudBadge(
                    label = banner.discipline.displayName,
                    accentColor = ArkhamColors.PhosphorGreen,
                )
                ArkhamHudBadge(
                    label = String.format(
                        Locale.US,
                        "%02d EX // %02d/%02d SETS",
                        banner.exerciseCount,
                        banner.completedSetCount,
                        banner.totalSetCount,
                    ),
                    accentColor = ArkhamColors.PhosphorGreenDim,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ArkhamPrimaryButton(
                    text = "RESUME",
                    onClick = onResume,
                    enabled = !isDiscarding,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_resume_session_button"),
                )
                ArkhamSecondaryButton(
                    text = "DISCARD",
                    onClick = onDiscard,
                    enabled = !isDiscarding,
                    contentColor = ArkhamColors.AlertRed,
                    borderColor = ArkhamColors.AlertRed,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_discard_session_button"),
                )
            }
        }
    }
}
