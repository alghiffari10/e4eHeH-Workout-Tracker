package com.e4eheh.e4ehehworkout.ui.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.e4eheh.e4ehehworkout.domain.analytics.DisciplineBreakdownItem
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressAnalytics
import com.e4eheh.e4ehehworkout.domain.analytics.ProgressTimeRange
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudBadge
import com.e4eheh.e4ehehworkout.ui.components.ArkhamHudPanel
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSectionHeader
import com.e4eheh.e4ehehworkout.ui.components.ArkhamSegmentTab
import com.e4eheh.e4ehehworkout.ui.components.BodyweightCrudAction
import com.e4eheh.e4ehehworkout.ui.components.BodyweightEntryEditorDialog
import com.e4eheh.e4ehehworkout.ui.components.DeleteBodyweightEntryDialog
import com.e4eheh.e4ehehworkout.ui.components.formatElapsedDuration
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamColors
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryLargeStyle
import com.e4eheh.e4ehehworkout.ui.theme.ArkhamTelemetryStyle
import java.util.Locale

/** Progress telemetry tab displaying Consistency, Discipline Breakdown, and Summary Stats. */
@Composable
fun ProgressScreen(
    uiState: ProgressUiState,
    onAction: (ProgressAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val analytics = uiState.analytics
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("progress_screen_scroll")
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ArkhamSectionHeader(
            title = "PROGRESS TELEMETRY",
            telemetryLabel = "RANGE // ${uiState.selectedTimeRange.label}",
        )

        TimeRangeSwitcher(
            selected = uiState.selectedTimeRange,
            onSelect = { onAction(ProgressAction.SelectTimeRange(it)) },
        )

        ConsistencySection(analytics = analytics)

        ExerciseProgressionSection(
            uiState = uiState,
            onAction = onAction,
        )

        SummaryStatsSection(
            uiState = uiState,
            onAction = onAction,
        )
    }

    uiState.bodyweightEditor?.let { editorState ->
        BodyweightEntryEditorDialog(
            state = editorState,
            weightUnit = uiState.weightUnit,
            onDateChange = { onAction(ProgressAction.Bodyweight(BodyweightCrudAction.UpdateDate(it))) },
            onWeightChange = { onAction(ProgressAction.Bodyweight(BodyweightCrudAction.UpdateWeight(it))) },
            onDismiss = { onAction(ProgressAction.Bodyweight(BodyweightCrudAction.DismissEditor)) },
            onSave = { onAction(ProgressAction.Bodyweight(BodyweightCrudAction.Save)) },
        )
    }

    uiState.deletingBodyweightEntry?.let { entry ->
        val dateLabel = uiState.bodyweightEntries
            .firstOrNull { it.entry.id == entry.id }
            ?.dateLabel
            .orEmpty()
        DeleteBodyweightEntryDialog(
            entry = entry,
            dateLabel = dateLabel,
            weightUnit = uiState.weightUnit,
            onDismiss = { onAction(ProgressAction.Bodyweight(BodyweightCrudAction.DismissDelete)) },
            onConfirm = { onAction(ProgressAction.Bodyweight(BodyweightCrudAction.ConfirmDelete)) },
        )
    }
}

@Composable
private fun TimeRangeSwitcher(
    selected: ProgressTimeRange,
    onSelect: (ProgressTimeRange) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProgressTimeRange.entries.forEach { range ->
            ArkhamSegmentTab(
                label = range.label,
                selected = selected == range,
                onClick = { onSelect(range) },
                labelPrefix = "range_${range.label}",
                modifier = Modifier
                    .weight(1f)
                    .testTag("progress_range_${range.label}"),
            )
        }
    }
}

@Composable
private fun ConsistencySection(analytics: ProgressAnalytics) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ArkhamSectionHeader(
            title = "CONSISTENCY",
            telemetryLabel = analytics.timeRange.telemetrySubtitle,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HudStatPanel(
                label = "COMPLETED SESSIONS",
                value = String.format(Locale.US, "%02d", analytics.totalCompletedSessions),
                subtitle = "SESSIONS IN RANGE",
                highlighted = analytics.totalCompletedSessions > 0,
                panelTestTag = "progress_consistency_sessions_panel",
                valueTestTag = "progress_consistency_total_sessions",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            HudStatPanel(
                label = "ACTIVE WEEKLY STREAK",
                value = formatWeeklyStreak(analytics.activeWeeklyStreak),
                subtitle = "MON-SUN CADENCE",
                highlighted = analytics.activeWeeklyStreak > 0,
                panelTestTag = "progress_consistency_streak_panel",
                valueTestTag = "progress_consistency_weekly_streak",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }

        DisciplineBreakdownPanel(analytics = analytics)
    }
}

@Composable
private fun DisciplineBreakdownPanel(analytics: ProgressAnalytics) {
    val isEmpty = analytics.totalCompletedSessions == 0
    ArkhamHudPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("progress_discipline_breakdown_panel"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DISCIPLINE BREAKDOWN",
                style = ArkhamTelemetryStyle,
                color = ArkhamColors.PhosphorGreenDim,
            )
            ArkhamHudBadge(
                label = String.format(Locale.US, "%02d LOGGED", analytics.totalCompletedSessions),
                accentColor = if (isEmpty) ArkhamColors.MintMuted else ArkhamColors.PhosphorGreen,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            DisciplineTacticalRingCanvas(
                breakdown = analytics.disciplineBreakdown,
                totalSessions = analytics.totalCompletedSessions,
                modifier = Modifier
                    .size(148.dp)
                    .testTag("progress_discipline_ring_canvas"),
            )
        }

        if (isEmpty) {
            Spacer(modifier = Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("progress_discipline_empty_state"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "NO COMPLETED SESSIONS IN RANGE",
                    style = MaterialTheme.typography.titleSmall,
                    color = ArkhamColors.MintWhite,
                )
                Text(
                    text = "Complete Sessions in this time window to populate Discipline rings and HUD telemetry bars.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ArkhamColors.MintMuted,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            analytics.disciplineBreakdown.forEach { item ->
                DisciplineBreakdownBarRow(item = item)
            }
        }
    }
}

@Composable
private fun DisciplineTacticalRingCanvas(
    breakdown: List<DisciplineBreakdownItem>,
    totalSessions: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 14.dp.toPx()
            val outerReticlePadding = 4.dp.toPx()
            val ringPadding = strokePx / 2f + 12.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = (minOf(size.width, size.height) / 2f) - outerReticlePadding
            val ringSize = Size(
                width = size.width - ringPadding * 2f,
                height = size.height - ringPadding * 2f,
            )
            val topLeft = Offset(ringPadding, ringPadding)

            // Outer tactical HUD reticle and cardinal tick marks
            drawCircle(
                color = ArkhamColors.TerminalEmerald.copy(alpha = 0.55f),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
            val tickLength = 6.dp.toPx()
            drawLine(
                color = ArkhamColors.PhosphorGreenDim,
                start = Offset(center.x, center.y - outerRadius - 2.dp.toPx()),
                end = Offset(center.x, center.y - outerRadius + tickLength),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawLine(
                color = ArkhamColors.PhosphorGreenDim,
                start = Offset(center.x, center.y + outerRadius - tickLength),
                end = Offset(center.x, center.y + outerRadius + 2.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawLine(
                color = ArkhamColors.PhosphorGreenDim,
                start = Offset(center.x - outerRadius - 2.dp.toPx(), center.y),
                end = Offset(center.x - outerRadius + tickLength, center.y),
                strokeWidth = 1.5.dp.toPx(),
            )
            drawLine(
                color = ArkhamColors.PhosphorGreenDim,
                start = Offset(center.x + outerRadius - tickLength, center.y),
                end = Offset(center.x + outerRadius + 2.dp.toPx(), center.y),
                strokeWidth = 1.5.dp.toPx(),
            )

            // Base ring track
            drawArc(
                color = ArkhamColors.TerminalEmerald.copy(alpha = 0.25f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = ringSize,
                style = Stroke(width = strokePx),
            )

            if (totalSessions == 0) {
                val gapDegrees = 10f
                val segmentSweep = (360f - gapDegrees * 4f) / 4f
                repeat(4) { index ->
                    drawArc(
                        color = ArkhamColors.TerminalEmerald.copy(alpha = 0.6f),
                        startAngle = -90f + index * (segmentSweep + gapDegrees) + gapDegrees / 2f,
                        sweepAngle = segmentSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = ringSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                    )
                }
            } else {
                val activeItems = breakdown.filter { it.sessionCount > 0 }
                val gapDegrees = if (activeItems.size > 1) 5f else 0f
                val availableDegrees = 360f - gapDegrees * activeItems.size
                val minSweep = if (activeItems.size > 1) 2f else 0f
                val slackDegrees = (availableDegrees - minSweep * activeItems.size).coerceAtLeast(0f)
                var startAngle = -90f
                activeItems.forEach { item ->
                    val sweep = minSweep + slackDegrees * item.fraction
                    drawArc(
                        color = disciplineHudColor(item.discipline),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = ringSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Butt),
                    )
                    startAngle += sweep + gapDegrees
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(Locale.US, "%02d", totalSessions),
                style = ArkhamTelemetryLargeStyle,
                color = if (totalSessions > 0) ArkhamColors.PhosphorGreen else ArkhamColors.MintMuted,
            )
            Text(
                text = if (totalSessions == 1) "SESSION" else "SESSIONS",
                style = MaterialTheme.typography.labelSmall,
                color = ArkhamColors.MintMuted,
            )
        }
    }
}

@Composable
private fun DisciplineBreakdownBarRow(item: DisciplineBreakdownItem) {
    val accentColor = disciplineHudColor(item.discipline)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("progress_discipline_row_${item.discipline.name}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Canvas(modifier = Modifier.size(8.dp)) {
                    drawRect(
                        color = if (item.sessionCount > 0) accentColor else ArkhamColors.TerminalEmerald,
                    )
                }
                Text(
                    text = item.discipline.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.sessionCount > 0) ArkhamColors.MintWhite else ArkhamColors.MintMuted,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatSessionCount(item.sessionCount),
                    style = ArkhamTelemetryStyle,
                    color = ArkhamColors.MintMuted,
                    modifier = Modifier.testTag("progress_discipline_count_${item.discipline.name}"),
                )
                Text(
                    text = "${item.percentage}%",
                    style = ArkhamTelemetryStyle,
                    color = if (item.sessionCount > 0) accentColor else ArkhamColors.MintMuted,
                    modifier = Modifier.testTag("progress_discipline_percent_${item.discipline.name}"),
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .testTag("progress_discipline_bar_${item.discipline.name}"),
        ) {
            val borderStroke = 1.dp.toPx()
            drawRect(color = ArkhamColors.ObsidianPanel)

            val fillWidth = (size.width * item.fraction.coerceIn(0f, 1f))
            if (fillWidth > 0f) {
                drawRect(
                    color = accentColor,
                    size = Size(width = fillWidth, height = size.height),
                )
            }

            // Tactical quartile tick marks
            val tickWidth = 1.dp.toPx()
            listOf(0.25f, 0.5f, 0.75f).forEach { mark ->
                val x = size.width * mark
                drawLine(
                    color = ArkhamColors.ObsidianVoid.copy(alpha = 0.75f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = tickWidth,
                )
            }

            drawRect(
                color = ArkhamColors.TerminalEmerald,
                style = Stroke(width = borderStroke),
            )
        }
    }
}

@Composable
private fun SummaryStatsSection(
    uiState: ProgressUiState,
    onAction: (ProgressAction) -> Unit,
) {
    val analytics = uiState.analytics
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ArkhamSectionHeader(
            title = "SUMMARY STATS",
            telemetryLabel = analytics.timeRange.telemetrySubtitle,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HudStatPanel(
                label = "TOTAL TRAINING TIME",
                value = formatElapsedDuration(analytics.totalDurationSeconds),
                subtitle = "${analytics.totalDurationSeconds / 60L} MIN LOGGED",
                highlighted = analytics.totalDurationSeconds > 0L,
                panelTestTag = "progress_summary_time_panel",
                valueTestTag = "progress_summary_total_time",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            HudStatPanel(
                label = "TOTAL SESSIONS",
                value = String.format(Locale.US, "%02d", analytics.totalCompletedSessions),
                subtitle = "COMPLETED IN RANGE",
                highlighted = analytics.totalCompletedSessions > 0,
                panelTestTag = "progress_summary_sessions_panel",
                valueTestTag = "progress_summary_total_sessions",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }

        BodyweightTrendSection(
            report = uiState.bodyweightTrend,
            entries = uiState.bodyweightEntries,
            isManageOpen = uiState.isManageBodyweightOpen,
            onToggleManage = { onAction(ProgressAction.ToggleManageBodyweight) },
            onBodyweightAction = { onAction(ProgressAction.Bodyweight(it)) },
        )
    }
}

@Composable
private fun HudStatPanel(
    label: String,
    value: String,
    subtitle: String,
    highlighted: Boolean,
    panelTestTag: String,
    valueTestTag: String,
    modifier: Modifier = Modifier,
) {
    ArkhamHudPanel(
        modifier = modifier.testTag(panelTestTag),
        highlighted = highlighted,
    ) {
        Text(
            text = label,
            style = ArkhamTelemetryStyle,
            color = ArkhamColors.MintMuted,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = ArkhamTelemetryLargeStyle,
            color = if (highlighted) ArkhamColors.PhosphorGreen else ArkhamColors.MintWhite,
            modifier = Modifier.testTag(valueTestTag),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = ArkhamColors.PhosphorGreenDim,
        )
    }
}

private fun disciplineHudColor(discipline: Discipline): Color = when (discipline) {
    Discipline.CALISTHENICS -> ArkhamColors.PhosphorGreen
    Discipline.CONDITIONING -> ArkhamColors.PhosphorGreenDim
    Discipline.CARDIO -> ArkhamColors.TerminalEmeraldBright
    Discipline.WEIGHT_LIFTING -> ArkhamColors.MintWhite
}

private fun formatWeeklyStreak(weeks: Int): String =
    if (weeks == 1) "01 WEEK" else String.format(Locale.US, "%02d WEEKS", weeks)

private fun formatSessionCount(count: Int): String =
    if (count == 1) "1 SESSION" else "$count SESSIONS"
