package com.e4eheh.e4ehehworkout.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ArkhamDarkColorScheme = darkColorScheme(
    primary = ArkhamColors.PhosphorGreen,
    onPrimary = ArkhamColors.OnPhosphor,
    primaryContainer = ArkhamColors.TerminalEmerald,
    onPrimaryContainer = ArkhamColors.MintWhite,
    secondary = ArkhamColors.PhosphorGreenDim,
    onSecondary = ArkhamColors.OnPhosphor,
    secondaryContainer = ArkhamColors.ObsidianElevated,
    onSecondaryContainer = ArkhamColors.MintWhite,
    tertiary = ArkhamColors.TacticalAmber,
    onTertiary = ArkhamColors.ObsidianVoid,
    background = ArkhamColors.ObsidianVoid,
    onBackground = ArkhamColors.MintWhite,
    surface = ArkhamColors.ObsidianSurface,
    onSurface = ArkhamColors.MintWhite,
    surfaceVariant = ArkhamColors.ObsidianElevated,
    onSurfaceVariant = ArkhamColors.MintMuted,
    outline = ArkhamColors.TerminalEmerald,
    outlineVariant = ArkhamColors.TerminalEmeraldBright,
    error = ArkhamColors.AlertRed,
    onError = ArkhamColors.OnAlertRed,
)

/** Strictly dark-only Arkham Knight Batcomputer hacker-green Material 3 theme. */
@Composable
fun E4eHeHWorkoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArkhamDarkColorScheme,
        typography = ArkhamTypography,
        content = content,
    )
}
