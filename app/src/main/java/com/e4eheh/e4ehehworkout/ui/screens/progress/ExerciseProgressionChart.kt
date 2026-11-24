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
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.analytics.ExerciseProgressionCalculator
import com.e4eheh.e4ehehworkout.domain.analytics.ExerciseProgressionReport
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressionMetric
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale

private val EXERCISE_CHART_TEST_TAGS = HudLineChartTestTags(
    canvas = "progress_exercise_chart_canvas",
    readout = "progress_chart_inspection_readout",
    clearInspect = "progress_chart_clear_inspect",
    yMax = "progress_chart_y_max",
    yMin = "progress_chart_y_min",
    xStart = "progress_chart_x_start",
    xEnd = "progress_chart_x_end",
)

/** Custom Compose Canvas hacker-green HUD line chart with touch-inspection crosshairs and monospace axes. */
@Composable
internal fun ExerciseProgressionChart(
    report: ExerciseProgressionReport,
    modifier: Modifier = Modifier,
) {
    val points = report.chartPoints

    ArkhamHudPanel(
        modifier = modifier
            .fillMaxWidth()
            .testTag("progress_exercise_chart_panel"),
        highlighted = points.isNotEmpty(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${report.selectedMetric.label.uppercase(Locale.US)} // ${report.unitSuffix}",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreenDim,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .testTag("progress_chart_metric_header"),
            )
            ArkhamHudBadge(
                label = if (report.selectedMetric.isLowerBetter && points.isNotEmpty()) {
                    "${points.size} PTS · LOWER FASTER"
                } else {
                    "${points.size} PTS"
                },
                accentColor = when {
                    points.isEmpty() -> ArkhamColors.MintMuted
                    report.selectedMetric.isLowerBetter -> ArkhamColors.TacticalAmber
                    else -> ArkhamColors.PhosphorGreen
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (points.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_exercise_chart_empty"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (report.hasAnyAllTimeHistory) {
                        "NO DATA IN ${report.timeRange.label} WINDOW"
                    } else {
                        "NO WORKING SETS LOGGED"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = ArkhamColors.MintWhite,
                )
                Text(
                    text = if (report.hasAnyAllTimeHistory) {
                        "Widen the time range above to inspect earlier Working Sets for ${report.exercise.name}."
                    } else {
                        "Complete Sessions with non-warm-up Working Sets for ${report.exercise.name} to plot progression telemetry."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArkhamColors.MintMuted,
                )
            }
            return@ArkhamHudPanel
        }

        val (yMin, yMax) = computeAxisBounds(
            values = points.map { it.displayValue },
            metric = report.selectedMetric,
        )
        ArkhamHudLineChart(
            points = points.map { point ->
                HudLineChartPoint(
                    timestamp = point.timestamp,
                    displayValue = point.displayValue,
                    dateLabel = point.dateLabel,
                    inspectReadout = "INSPECT // ${point.dateLabel} · ${point.formattedValue} · ${point.sessionTitle}",
                    latestReadout = "LATEST // ${point.dateLabel} · ${point.formattedValue}",
                )
            },
            yMin = yMin,
            yMax = yMax,
            formatYTick = { tickValue ->
                ExerciseProgressionCalculator.formatAxisValue(
                    metric = report.selectedMetric,
                    displayValue = tickValue,
                )
            },
            resetKey = Triple(report.exercise.id, report.selectedMetric, report.timeRange),
            testTags = EXERCISE_CHART_TEST_TAGS,
        )
    }
}

private fun computeAxisBounds(
    values: List<Double>,
    metric: ProgressionMetric,
): Pair<Double, Double> {
    val rawMin = values.min()
    val rawMax = values.max()
    val (paddedMin, paddedMax) = if (rawMin == rawMax) {
        if (rawMax == 0.0) {
            0.0 to 1.0
        } else {
            val pad = (rawMax * 0.15).coerceAtLeast(1.0)
            (rawMin - pad).coerceAtLeast(0.0) to (rawMax + pad)
        }
    } else {
        val pad = (rawMax - rawMin) * 0.12
        (rawMin - pad).coerceAtLeast(0.0) to (rawMax + pad)
    }

    if (metric.isIntegerMetric && (paddedMax - paddedMin) < 3.0) {
        val flooredMin = kotlin.math.floor(paddedMin).coerceAtLeast(0.0)
        return flooredMin to (flooredMin + 3.0)
    }
    return paddedMin to paddedMax
}
