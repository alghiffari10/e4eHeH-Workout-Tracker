package com.e4eheh.e4ehehworkout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamMotion
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamShapes
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle

/** Full-screen obsidian HUD backdrop with subtle Batcomputer grid and scanline texture. */
@Composable
fun ArkhamHudBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ArkhamColors.ObsidianVoid),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridStep = 28.dp.toPx()
            val gridColor = ArkhamColors.TerminalEmerald.copy(alpha = 0.10f)
            var x = 0f
            while (x <= size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
                x += gridStep
            }
            drawHorizontalLines(stepPx = gridStep, color = gridColor)
            drawHorizontalLines(
                stepPx = 4.dp.toPx(),
                color = ArkhamColors.TerminalEmerald.copy(alpha = 0.06f),
            )
        }
        content()
    }
}

private fun DrawScope.drawHorizontalLines(
    stepPx: Float,
    color: Color,
) {
    var y = 0f
    while (y <= size.height) {
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        y += stepPx
    }
}

/** Chamfered 1dp-bordered HUD panel surface. */
@Composable
fun ArkhamHudPanel(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    shape: Shape = ArkhamShapes.panel,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) ArkhamColors.PhosphorGreen else ArkhamColors.TerminalEmerald,
        animationSpec = tween(durationMillis = ArkhamMotion.STATE_MS),
        label = "hudPanelBorder",
    )
    val containerColor by animateColorAsState(
        targetValue = if (highlighted) ArkhamColors.ObsidianElevated else ArkhamColors.ObsidianSurface,
        animationSpec = tween(durationMillis = ArkhamMotion.STATE_MS),
        label = "hudPanelContainer",
    )

    Surface(
        modifier = modifier,
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/** Selectable chamfered tactical chip for Discipline and Tracking Mode selectors. */
@Composable
fun ArkhamHudChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) ArkhamColors.PhosphorGreen else ArkhamColors.TerminalEmerald,
        animationSpec = tween(durationMillis = ArkhamMotion.STATE_MS),
        label = "hudChipBorder",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            ArkhamColors.TerminalEmerald.copy(alpha = 0.45f)
        } else {
            ArkhamColors.ObsidianPanel
        },
        animationSpec = tween(durationMillis = ArkhamMotion.STATE_MS),
        label = "hudChipContainer",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) ArkhamColors.PhosphorGreen else ArkhamColors.MintMuted,
        animationSpec = tween(durationMillis = ArkhamMotion.STATE_MS),
        label = "hudChipText",
    )

    Surface(
        modifier = modifier.selectable(
            selected = selected,
            onClick = onClick,
            role = Role.RadioButton,
        ),
        shape = ArkhamShapes.chip,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** Compact monospace HUD metadata badge. */
@Composable
fun ArkhamHudBadge(
    label: String,
    modifier: Modifier = Modifier,
    accentColor: Color = ArkhamColors.PhosphorGreenDim,
) {
    Surface(
        modifier = modifier,
        shape = ArkhamShapes.chip,
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
    ) {
        Text(
            text = label,
            style = ArkhamTelemetryStyle,
            color = accentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Monospace section header with optional right-aligned telemetry readout. */
@Composable
fun ArkhamSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    telemetryLabel: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "// $title",
            style = ArkhamTelemetryStyle,
            color = ArkhamColors.PhosphorGreenDim,
        )
        if (telemetryLabel != null) {
            Text(
                text = telemetryLabel,
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.MintMuted,
            )
        }
    }
}

@Composable
fun ArkhamPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = ArkhamColors.PhosphorGreen,
    contentColor: Color = ArkhamColors.OnPhosphor,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ArkhamShapes.chip,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun ArkhamSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    val borderColor = if (enabled) {
        ArkhamColors.TerminalEmeraldBright
    } else {
        ArkhamColors.TerminalEmerald.copy(alpha = 0.45f)
    }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ArkhamShapes.chip,
        contentPadding = contentPadding,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = ArkhamColors.MintWhite,
            disabledContentColor = ArkhamColors.MintMuted.copy(alpha = 0.5f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Shared Arkham HUD text field color palette. */
@Composable
fun arkhamTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ArkhamColors.PhosphorGreen,
    unfocusedBorderColor = ArkhamColors.TerminalEmerald,
    errorBorderColor = ArkhamColors.AlertRed,
    focusedLabelColor = ArkhamColors.PhosphorGreen,
    unfocusedLabelColor = ArkhamColors.MintMuted,
    cursorColor = ArkhamColors.PhosphorGreen,
    focusedTextColor = ArkhamColors.MintWhite,
    unfocusedTextColor = ArkhamColors.MintWhite,
    focusedContainerColor = ArkhamColors.ObsidianPanel,
    unfocusedContainerColor = ArkhamColors.ObsidianPanel,
)

/** Labeled FlowRow of selectable tactical chips for enums and preset choices. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ArkhamChipSelectorSection(
    title: String,
    options: List<T>,
    selected: T?,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagOf: ((T) -> String)? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = ArkhamTelemetryStyle,
            color = ArkhamColors.PhosphorGreenDim,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val chipModifier = testTagOf?.invoke(option)?.let { Modifier.testTag(it) } ?: Modifier
                ArkhamHudChip(
                    label = labelOf(option),
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    modifier = chipModifier,
                )
            }
        }
    }
}

/** Shared destructive confirmation modal used across Exercise and Routine soft-deletes. */
@Composable
fun ArkhamDeleteConfirmationDialog(
    title: String,
    itemName: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ArkhamHudPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            highlighted = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = title,
                    style = ArkhamTelemetryLargeStyle,
                    color = ArkhamColors.AlertRed,
                )
                Text(
                    text = itemName,
                    style = MaterialTheme.typography.titleMedium,
                    color = ArkhamColors.MintWhite,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArkhamColors.MintMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ArkhamSecondaryButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    ArkhamPrimaryButton(
                        text = "Delete",
                        onClick = onConfirm,
                        containerColor = ArkhamColors.AlertRed,
                        contentColor = ArkhamColors.OnAlertRed,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
