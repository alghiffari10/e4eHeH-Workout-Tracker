package com.e4eheh.e4ehehworkout.ui.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.analytics.ExerciseChartPoint
import com.e4eheh.e4ehehworkout.domain.analytics.ExerciseProgressionCalculator
import com.e4eheh.e4ehehworkout.domain.analytics.ExerciseProgressionReport
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressionMetric
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale
import kotlin.math.abs

/** Custom Compose Canvas hacker-green HUD line chart with touch-inspection crosshairs and monospace axes. */
@Composable
internal fun ExerciseProgressionChart(
    report: ExerciseProgressionReport,
    modifier: Modifier = Modifier,
) {
    val points = report.chartPoints
    var inspectedIndex by remember(
        report.exercise.id,
        report.selectedMetric,
        report.timeRange,
        points.size,
    ) {
        mutableStateOf<Int?>(null)
    }

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

        val inspectedPoint = inspectedIndex?.let { points.getOrNull(it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inspectedPoint != null) {
                Text(
                    text = "INSPECT // ${inspectedPoint.dateLabel} · ${inspectedPoint.formattedValue} · ${inspectedPoint.sessionTitle}",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.PhosphorGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .testTag("progress_chart_inspection_readout"),
                )
                Text(
                    text = "CLEAR",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.TacticalAmber,
                    modifier = Modifier
                        .testTag("progress_chart_clear_inspect")
                        .clickable(role = Role.Button) { inspectedIndex = null },
                )
            } else {
                val latest = points.last()
                Text(
                    text = "LATEST // ${latest.dateLabel} · ${latest.formattedValue}",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.MintWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .testTag("progress_chart_inspection_readout"),
                )
                Text(
                    text = "TAP TO INSPECT",
                    style = MaterialTheme.typography.labelSmall,
                    color = ArkhamColors.MintMuted,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val (yMin, yMax) = computeAxisBounds(
            values = points.map { it.displayValue },
            metric = report.selectedMetric,
        )
        val span = (yMax - yMin).coerceAtLeast(1e-6)
        val yTicks = listOf(yMax, yMax - span / 3.0, yMin + span / 3.0, yMin)
        val xFractions = remember(points) { computeTimeFractions(points) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                yTicks.forEachIndexed { index, tickValue ->
                    val tagModifier = when (index) {
                        0 -> Modifier.testTag("progress_chart_y_max")
                        yTicks.lastIndex -> Modifier.testTag("progress_chart_y_min")
                        else -> Modifier
                    }
                    Text(
                        text = ExerciseProgressionCalculator.formatAxisValue(
                            metric = report.selectedMetric,
                            displayValue = tickValue,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = ArkhamColors.MintMuted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = tagModifier,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("progress_exercise_chart_canvas")
                    .pointerInput(xFractions) {
                        detectTapGestures { offset ->
                            inspectedIndex = nearestPointIndex(offset.x, xFractions)
                        }
                    }
                    .pointerInput(xFractions) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                inspectedIndex = nearestPointIndex(offset.x, xFractions)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                inspectedIndex = nearestPointIndex(change.position.x, xFractions)
                            },
                        )
                    },
            ) {
                val plotLeft = 12.dp.toPx()
                val plotRight = (size.width - 12.dp.toPx()).coerceAtLeast(plotLeft + 1f)
                val plotTop = 12.dp.toPx()
                val plotBottom = (size.height - 12.dp.toPx()).coerceAtLeast(plotTop + 1f)
                val plotWidth = plotRight - plotLeft
                val plotHeight = plotBottom - plotTop

                drawRect(color = ArkhamColors.ObsidianPanel)

                val gridFractions = listOf(0f, 1f / 3f, 2f / 3f, 1f)
                gridFractions.forEach { fraction ->
                    val y = plotTop + fraction * plotHeight
                    drawLine(
                        color = ArkhamColors.TerminalEmerald.copy(alpha = 0.45f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                    val x = plotLeft + fraction * plotWidth
                    drawLine(
                        color = ArkhamColors.TerminalEmerald.copy(alpha = 0.25f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val coords = points.mapIndexed { index, point ->
                    val x = plotLeft + xFractions[index] * plotWidth
                    val normY = ((point.displayValue - yMin) / span).toFloat().coerceIn(0f, 1f)
                    val y = plotBottom - normY * plotHeight
                    Offset(x, y)
                }

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                if (coords.size >= 2) {
                    val linePath = Path().apply {
                        moveTo(coords.first().x, coords.first().y)
                        for (i in 1 until coords.size) {
                            lineTo(coords[i].x, coords[i].y)
                        }
                    }
                    val areaPath = Path().apply {
                        addPath(linePath)
                        lineTo(coords.last().x, plotBottom)
                        lineTo(coords.first().x, plotBottom)
                        close()
                    }
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ArkhamColors.PhosphorGreen.copy(alpha = 0.24f),
                                Color.Transparent,
                            ),
                            startY = plotTop,
                            endY = plotBottom,
                        ),
                    )
                    drawPath(
                        path = linePath,
                        color = ArkhamColors.PhosphorGreen.copy(alpha = 0.26f),
                        style = Stroke(
                            width = 6.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                    drawPath(
                        path = linePath,
                        color = ArkhamColors.PhosphorGreen,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                } else if (coords.size == 1) {
                    val singleY = coords.first().y
                    drawLine(
                        color = ArkhamColors.PhosphorGreen.copy(alpha = 0.45f),
                        start = Offset(plotLeft, singleY),
                        end = Offset(plotRight, singleY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = dashEffect,
                    )
                }

                val activeCrosshair = inspectedIndex?.let { coords.getOrNull(it) }
                if (activeCrosshair != null) {
                    drawLine(
                        color = ArkhamColors.PhosphorGreenDim.copy(alpha = 0.85f),
                        start = Offset(activeCrosshair.x, 0f),
                        end = Offset(activeCrosshair.x, size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect,
                    )
                    drawLine(
                        color = ArkhamColors.PhosphorGreenDim.copy(alpha = 0.85f),
                        start = Offset(0f, activeCrosshair.y),
                        end = Offset(size.width, activeCrosshair.y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashEffect,
                    )
                }

                coords.forEachIndexed { index, pt ->
                    if (index == inspectedIndex) {
                        drawCircle(
                            color = ArkhamColors.TacticalAmber,
                            radius = 10.dp.toPx(),
                            center = pt,
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                        drawCircle(
                            color = ArkhamColors.PhosphorGreen,
                            radius = 5.dp.toPx(),
                            center = pt,
                        )
                    } else {
                        drawCircle(
                            color = ArkhamColors.ObsidianVoid,
                            radius = 3.5.dp.toPx(),
                            center = pt,
                        )
                        drawCircle(
                            color = ArkhamColors.PhosphorGreen,
                            radius = 3.5.dp.toPx(),
                            center = pt,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }

                drawRect(
                    color = ArkhamColors.TerminalEmerald,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 62.dp),
            horizontalArrangement = if (points.size == 1) {
                Arrangement.Center
            } else {
                Arrangement.SpaceBetween
            },
        ) {
            if (points.size == 1) {
                Text(
                    text = points.first().dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = ArkhamColors.MintMuted,
                    modifier = Modifier.testTag("progress_chart_x_start"),
                )
            } else {
                Text(
                    text = points.first().dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = ArkhamColors.MintMuted,
                    modifier = Modifier.testTag("progress_chart_x_start"),
                )
                Text(
                    text = points.last().dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = ArkhamColors.MintMuted,
                    modifier = Modifier.testTag("progress_chart_x_end"),
                )
            }
        }
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

private fun computeTimeFractions(points: List<ExerciseChartPoint>): List<Float> {
    if (points.size <= 1) return listOf(0.5f)
    val startMs = points.first().timestamp.toEpochMilli()
    val endMs = points.last().timestamp.toEpochMilli()
    val durationMs = endMs - startMs
    if (durationMs <= 0L) {
        return List(points.size) { index ->
            index.toFloat() / (points.size - 1).toFloat()
        }
    }
    return points.map { point ->
        ((point.timestamp.toEpochMilli() - startMs).toDouble() / durationMs.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    }
}

private fun PointerInputScope.nearestPointIndex(
    touchX: Float,
    xFractions: List<Float>,
): Int {
    if (xFractions.size <= 1) return 0
    val plotLeft = 12.dp.toPx()
    val plotRight = (size.width.toFloat() - 12.dp.toPx()).coerceAtLeast(plotLeft + 1f)
    val plotWidth = plotRight - plotLeft
    val normalizedTouch = ((touchX - plotLeft) / plotWidth).coerceIn(0f, 1f)
    return xFractions.indices.minByOrNull { index -> abs(xFractions[index] - normalizedTouch) } ?: 0
}
