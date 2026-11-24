package com.e4eheh.e4ehehworkout.ui.screens.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.analytics.BodyweightTrendCalculator
import com.e4eheh.e4ehehworkout.domain.analytics.BodyweightTrendReport
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSecondaryButton
import com.e4eheh.e4ehehworkout.ui.components.BodyweightCrudAction
import com.e4eheh.e4ehehworkout.ui.components.BodyweightEntryRow
import com.e4eheh.e4ehehworkout.ui.components.BodyweightEntryRowUi
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle

private val BODYWEIGHT_CHART_TEST_TAGS = HudLineChartTestTags(
    canvas = "progress_bodyweight_chart_canvas",
    readout = "progress_bodyweight_chart_readout",
    clearInspect = "progress_bodyweight_chart_clear_inspect",
    yMax = "progress_bodyweight_chart_y_max",
    yMin = "progress_bodyweight_chart_y_min",
    xStart = "progress_bodyweight_chart_x_start",
    xEnd = "progress_bodyweight_chart_x_end",
)

/** Standalone Summary Stats Bodyweight Entry HUD trend chart and quick log/manage controls. */
@Composable
internal fun BodyweightTrendSection(
    report: BodyweightTrendReport,
    entries: List<BodyweightEntryRowUi>,
    isManageOpen: Boolean,
    onToggleManage: () -> Unit,
    onBodyweightAction: (BodyweightCrudAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val points = report.chartPoints

    ArkhamHudPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("progress_bodyweight_trend_slot"),
        highlighted = points.isNotEmpty(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "BODYWEIGHT TREND // ${report.unitSuffix}",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreenDim,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .testTag("progress_bodyweight_chart_header"),
            )
            ArkhamHudBadge(
                label = "${points.size} PTS",
                accentColor = if (points.isEmpty()) ArkhamColors.MintMuted else ArkhamColors.PhosphorGreen,
            )
        }

        report.formattedRangeDelta?.let { delta ->
            Spacer(modifier = Modifier.height(8.dp))
            ArkhamHudBadge(
                label = "DELTA $delta",
                accentColor = ArkhamColors.TacticalAmber,
                modifier = Modifier
                    .semantics(mergeDescendants = true) {}
                    .testTag("progress_bodyweight_range_delta"),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (points.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_bodyweight_chart_empty"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (report.hasAnyAllTimeHistory) {
                        "NO ENTRIES IN ${report.timeRange.label} WINDOW"
                    } else {
                        "NO BODYWEIGHT ENTRIES LOGGED"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = ArkhamColors.MintWhite,
                )
                Text(
                    text = if (report.hasAnyAllTimeHistory) {
                        "Widen the time range above to inspect earlier Bodyweight Entries."
                    } else {
                        "Log dated Bodyweight Entries here or on Profile to visualize your standalone trend without affecting Exercise load."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArkhamColors.MintMuted,
                )
            }
        } else {
            val (yMin, yMax) = BodyweightTrendCalculator.computeAxisBounds(
                values = points.map { it.displayWeight },
            )
            ArkhamHudLineChart(
                points = points.map { point ->
                    HudLineChartPoint(
                        timestamp = point.recordedAt,
                        displayValue = point.displayWeight,
                        dateLabel = point.dateLabel,
                        inspectReadout = "INSPECT // ${point.dateLabel} · ${point.formattedWeight}",
                        latestReadout = "LATEST // ${point.dateLabel} · ${point.formattedWeight}",
                    )
                },
                yMin = yMin,
                yMax = yMax,
                formatYTick = BodyweightTrendCalculator::formatAxisValue,
                resetKey = report.timeRange to report.weightUnit,
                testTags = BODYWEIGHT_CHART_TEST_TAGS,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ArkhamSecondaryButton(
                text = "+ LOG BODYWEIGHT",
                onClick = { onBodyweightAction(BodyweightCrudAction.OpenCreate) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("progress_log_bodyweight_button"),
            )
            if (entries.isNotEmpty()) {
                ArkhamSecondaryButton(
                    text = if (isManageOpen) "HIDE ENTRIES" else "ENTRIES (${entries.size})",
                    onClick = onToggleManage,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("progress_manage_bodyweight_button"),
                )
            }
        }

        if (isManageOpen && entries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                entries.forEach { row ->
                    BodyweightEntryRow(
                        row = row,
                        onEdit = { onBodyweightAction(BodyweightCrudAction.OpenEdit(row.entry)) },
                        onDelete = { onBodyweightAction(BodyweightCrudAction.RequestDelete(row.entry)) },
                        testTagPrefix = "progress_bodyweight_entry",
                    )
                }
            }
        }
    }
}
