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
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.time.Instant
import kotlin.math.abs

/** Generic data point rendered by ArkhamHudLineChart. */
internal data class HudLineChartPoint(
    val timestamp: Instant,
    val displayValue: Double,
    val dateLabel: String,
    val inspectReadout: String,
    val latestReadout: String,
)

/** Semantics test tags scoped to an ArkhamHudLineChart instance. */
internal data class HudLineChartTestTags(
    val canvas: String,
    val readout: String,
    val clearInspect: String,
    val yMax: String,
    val yMin: String,
    val xStart: String,
    val xEnd: String,
)

/**
 * Reusable custom Compose Canvas hacker-green HUD line chart with monospace axes
 * and tap/drag touch-inspection crosshairs.
 */
@Composable
internal fun ArkhamHudLineChart(
    points: List<HudLineChartPoint>,
    yMin: Double,
    yMax: Double,
    formatYTick: (Double) -> String,
    resetKey: Any,
    testTags: HudLineChartTestTags,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return

    var inspectedIndex by remember(resetKey, points.size) {
        mutableStateOf<Int?>(null)
    }

    val inspectedPoint = inspectedIndex?.let { points.getOrNull(it) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inspectedPoint != null) {
                Text(
                    text = inspectedPoint.inspectReadout,
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.PhosphorGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .testTag(testTags.readout),
                )
                Text(
                    text = "CLEAR",
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.TacticalAmber,
                    modifier = Modifier
                        .testTag(testTags.clearInspect)
                        .clickable(role = Role.Button) { inspectedIndex = null },
                )
            } else {
                val latest = points.last()
                Text(
                    text = latest.latestReadout,
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.MintWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                        .testTag(testTags.readout),
                )
                Text(
                    text = "TAP TO INSPECT",
                    style = MaterialTheme.typography.labelSmall,
                    color = ArkhamColors.MintMuted,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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
                        0 -> Modifier.testTag(testTags.yMax)
                        yTicks.lastIndex -> Modifier.testTag(testTags.yMin)
                        else -> Modifier
                    }
                    Text(
                        text = formatYTick(tickValue),
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
                    .testTag(testTags.canvas)
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
                    modifier = Modifier.testTag(testTags.xStart),
                )
            } else {
                Text(
                    text = points.first().dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = ArkhamColors.MintMuted,
                    modifier = Modifier.testTag(testTags.xStart),
                )
                Text(
                    text = points.last().dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = ArkhamColors.MintMuted,
                    modifier = Modifier.testTag(testTags.xEnd),
                )
            }
        }
    }
}

private fun computeTimeFractions(points: List<HudLineChartPoint>): List<Float> {
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
