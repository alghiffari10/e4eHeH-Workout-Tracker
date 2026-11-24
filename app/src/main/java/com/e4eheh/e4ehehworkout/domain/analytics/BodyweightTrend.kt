package com.e4eheh.e4ehehworkout.domain.analytics

import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntry
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.unit.UnitConverter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

/** Single chronological Bodyweight Entry data point plotted on the Progress Summary Stats HUD chart. */
data class BodyweightChartPoint(
    val entryId: String,
    val recordedAt: Instant,
    val weightKg: Double,
    val displayWeight: Double,
    val formattedWeight: String,
    val dateLabel: String,
)

/** Time-range filtered Bodyweight Entry trend series and delta summary converted to the active WeightUnit. */
data class BodyweightTrendReport(
    val timeRange: ProgressTimeRange,
    val weightUnit: WeightUnit,
    val unitSuffix: String,
    val chartPoints: List<BodyweightChartPoint>,
    val latestPoint: BodyweightChartPoint?,
    val rangeDeltaDisplay: Double?,
    val formattedRangeDelta: String?,
    val hasAnyAllTimeHistory: Boolean,
)

/** Pure-Kotlin calculator for standalone Bodyweight Entry trend charts and unit conversion. */
object BodyweightTrendCalculator {
    fun empty(
        timeRange: ProgressTimeRange,
        weightUnit: WeightUnit = WeightUnit.KG,
    ): BodyweightTrendReport = BodyweightTrendReport(
        timeRange = timeRange,
        weightUnit = weightUnit,
        unitSuffix = weightUnit.symbol.uppercase(Locale.US),
        chartPoints = emptyList(),
        latestPoint = null,
        rangeDeltaDisplay = null,
        formattedRangeDelta = null,
        hasAnyAllTimeHistory = false,
    )

    fun calculate(
        entries: List<BodyweightEntry>,
        timeRange: ProgressTimeRange,
        weightUnit: WeightUnit,
        clock: Clock,
    ): BodyweightTrendReport {
        val activeAsc = entries
            .filter { it.deletedAt == null }
            .sortedWith(compareBy<BodyweightEntry>({ it.recordedAt }, { it.createdAt }, { it.id }))

        val cutoff = ProgressAnalyticsCalculator.cutoffInstant(timeRange, clock)
        val filtered = activeAsc.filter { entry ->
            cutoff == null || !entry.recordedAt.isBefore(cutoff)
        }

        val chartPoints = filtered.map { entry ->
            BodyweightChartPoint(
                entryId = entry.id,
                recordedAt = entry.recordedAt,
                weightKg = entry.weightKg,
                displayWeight = UnitConverter.weightToDisplay(entry.weightKg, weightUnit),
                formattedWeight = UnitConverter.formatWeight(entry.weightKg, weightUnit),
                dateLabel = formatChartDateLabel(entry.recordedAt, clock),
            )
        }

        val latestPoint = chartPoints.lastOrNull()
        val rangeDeltaDisplay = if (chartPoints.size >= 2) {
            chartPoints.last().displayWeight - chartPoints.first().displayWeight
        } else {
            null
        }
        val formattedRangeDelta = rangeDeltaDisplay?.let { formatSignedDelta(it, weightUnit) }

        return BodyweightTrendReport(
            timeRange = timeRange,
            weightUnit = weightUnit,
            unitSuffix = weightUnit.symbol.uppercase(Locale.US),
            chartPoints = chartPoints,
            latestPoint = latestPoint,
            rangeDeltaDisplay = rangeDeltaDisplay,
            formattedRangeDelta = formattedRangeDelta,
            hasAnyAllTimeHistory = activeAsc.isNotEmpty(),
        )
    }

    fun formatAxisValue(displayWeight: Double): String =
        UnitConverter.formatDecimal(displayWeight.coerceAtLeast(0.0), maxDecimals = 1)

    fun computeAxisBounds(values: List<Double>): Pair<Double, Double> {
        val rawMin = values.min()
        val rawMax = values.max()
        val pad = if (rawMin == rawMax) {
            (rawMax * 0.05).coerceAtLeast(1.0)
        } else {
            ((rawMax - rawMin) * 0.2).coerceAtLeast(0.5)
        }
        return (rawMin - pad).coerceAtLeast(0.0) to (rawMax + pad)
    }

    private fun formatSignedDelta(
        deltaDisplay: Double,
        weightUnit: WeightUnit,
    ): String {
        val roundedAbs = BigDecimal.valueOf(abs(deltaDisplay))
            .setScale(2, RoundingMode.HALF_UP)
        if (roundedAbs.compareTo(BigDecimal.ZERO) == 0) {
            return "0 ${weightUnit.symbol}"
        }
        val sign = if (deltaDisplay > 0.0) "+" else "-"
        return "$sign${roundedAbs.stripTrailingZeros().toPlainString()} ${weightUnit.symbol}"
    }
}
