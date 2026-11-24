package com.e4eheh.e4ehehworkout.domain.analytics

import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionSet
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.unit.UnitConverter
import java.time.Clock
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Measurement unit context passed across progression calculations. */
internal data class ProgressionUnits(
    val weightUnit: WeightUnit,
    val distanceUnit: DistanceUnit,
)

/** Selectable progression metric plotted and tracked as a Personal Record for an Exercise. */
enum class ProgressionMetric(
    val label: String,
    val prTitle: String,
    val isLowerBetter: Boolean = false,
    val isIntegerMetric: Boolean = false,
) {
    TOTAL_REPS("Total Reps", "BEST TOTAL REPS", isIntegerMetric = true) {
        override fun extractCanonical(workingSets: List<SessionSet>): Double? =
            sumPositiveInts(workingSets) { it.reps }

        override fun toDisplay(canonical: Double, units: ProgressionUnits): Double = canonical

        override fun formatValue(canonical: Double, units: ProgressionUnits): String =
            formatPluralCount(canonical.roundToInt(), singular = "rep", plural = "reps")

        override fun formatAxis(display: Double): String = formatIntegerAxis(display)

        override fun unitSuffix(units: ProgressionUnits, maxCanonical: Double): String = "REPS"
    },
    BEST_SET_REPS("Best Set Reps", "BEST SET REPS", isIntegerMetric = true) {
        override fun extractCanonical(workingSets: List<SessionSet>): Double? =
            workingSets.mapNotNull { it.reps?.takeIf { reps -> reps > 0 } }.maxOrNull()?.toDouble()

        override fun toDisplay(canonical: Double, units: ProgressionUnits): Double = canonical

        override fun formatValue(canonical: Double, units: ProgressionUnits): String =
            formatPluralCount(canonical.roundToInt(), singular = "rep", plural = "reps")

        override fun formatAxis(display: Double): String = formatIntegerAxis(display)

        override fun unitSuffix(units: ProgressionUnits, maxCanonical: Double): String = "REPS"
    },
    TOTAL_VOLUME("Total Volume", "BEST VOLUME") {
        override fun extractCanonical(workingSets: List<SessionSet>): Double? =
            workingSets
                .filter { (it.reps ?: 0) > 0 }
                .takeIf { it.isNotEmpty() }
                ?.sumOf { set -> set.reps!!.toDouble() * (set.loadKg ?: 0.0).coerceAtLeast(0.0) }

        override fun toDisplay(canonical: Double, units: ProgressionUnits): Double =
            UnitConverter.weightToDisplay(canonical, units.weightUnit)

        override fun formatValue(canonical: Double, units: ProgressionUnits): String =
            UnitConverter.formatWeight(canonical, units.weightUnit)

        override fun formatAxis(display: Double): String =
            UnitConverter.formatDecimal(display.coerceAtLeast(0.0), maxDecimals = 1)

        override fun unitSuffix(units: ProgressionUnits, maxCanonical: Double): String =
            units.weightUnit.symbol.uppercase(Locale.US)
    },
    MAX_LOAD("Max Load", "MAX LOAD") {
        override fun extractCanonical(workingSets: List<SessionSet>): Double? =
            workingSets
                .filter { (it.reps ?: 0) > 0 || (it.loadKg ?: 0.0) > 0.0 }
                .takeIf { it.isNotEmpty() }
                ?.maxOf { (it.loadKg ?: 0.0).coerceAtLeast(0.0) }

        override fun toDisplay(canonical: Double, units: ProgressionUnits): Double =
            UnitConverter.weightToDisplay(canonical, units.weightUnit)

        override fun formatValue(canonical: Double, units: ProgressionUnits): String =
            UnitConverter.formatWeight(canonical, units.weightUnit)

        override fun formatAxis(display: Double): String =
            UnitConverter.formatDecimal(display.coerceAtLeast(0.0), maxDecimals = 1)

        override fun unitSuffix(units: ProgressionUnits, maxCanonical: Double): String =
            units.weightUnit.symbol.uppercase(Locale.US)
    },
    LONGEST_HOLD("Longest Hold", "LONGEST HOLD") {
        override fun extractCanonical(workingSets: List<SessionSet>): Double? =
            workingSets.mapNotNull { it.durationSeconds?.takeIf { sec -> sec > 0 } }
                .maxOrNull()
                ?.toDouble()

        override fun toDisplay(canonical: Double, units: ProgressionUnits): Double = canonical

        override fun formatValue(canonical: Double, units: ProgressionUnits): String =
            UnitConverter.formatDurationClock(canonical.roundToLong())

        override fun formatAxis(display: Double): String = formatClockAxis(display)

        override fun unitSuffix(units: ProgressionUnits, maxCanonical: Double): String =
            durationUnitSuffix(maxCanonical)
    },
    TOTAL_HOLD_TIME("Total Hold Time", "BEST TOTAL HOLD") {
        override fun extractCanonical(workingSets: List<SessionSet>): Double? =
            sumPositiveInts(workingSets) { it.durationSeconds }

        override fun toDisplay(canonical: Double, units: ProgressionUnits): Double = canonical

        override fun formatValue(canonical: Double, units: ProgressionUnits): String =
            UnitConverter.formatDurationClock(canonical.roundToLong())

        override fun formatAxis(display: Double): String = formatClockAxis(display)

        override fun unitSuffix(units: ProgressionUnits, maxCanonical: Double): String =
            durationUnitSuffix(maxCanonical)
    },
    DISTANCE("Distance", "BEST DISTANCE") {
        override fun extractCanonical(workingSets: List<SessionSet>): Double? =
            workingSets
                .mapNotNull { it.distanceKm?.takeIf { km -> km > 0.0 } }
                .takeIf { it.isNotEmpty() }
                ?.sum()

        override fun toDisplay(canonical: Double, units: ProgressionUnits): Double =
            UnitConverter.distanceToDisplay(canonical, units.distanceUnit)

        override fun formatValue(canonical: Double, units: ProgressionUnits): String =
            UnitConverter.formatDistance(canonical, units.distanceUnit)

        override fun formatAxis(display: Double): String =
            UnitConverter.formatDecimal(display.coerceAtLeast(0.0), maxDecimals = 2)

        override fun unitSuffix(units: ProgressionUnits, maxCanonical: Double): String =
            units.distanceUnit.symbol.uppercase(Locale.US)
    },
    PACE("Pace", "FASTEST PACE", isLowerBetter = true) {
        override fun extractCanonical(workingSets: List<SessionSet>): Double? {
            val pacedSets = workingSets.filter {
                (it.distanceKm ?: 0.0) > 0.0 && (it.durationSeconds ?: 0) > 0
            }
            if (pacedSets.isEmpty()) return null
            val totalDurationSec = pacedSets.sumOf { it.durationSeconds!!.toLong() }
            val totalDistanceKm = pacedSets.sumOf { it.distanceKm!! }
            return totalDurationSec.toDouble() / totalDistanceKm
        }

        override fun toDisplay(canonical: Double, units: ProgressionUnits): Double =
            UnitConverter.paceToDisplaySecondsPerUnit(canonical, units.distanceUnit)

        override fun formatValue(canonical: Double, units: ProgressionUnits): String =
            UnitConverter.formatPace(canonical, units.distanceUnit)

        override fun formatAxis(display: Double): String = formatClockAxis(display)

        override fun unitSuffix(units: ProgressionUnits, maxCanonical: Double): String =
            "MIN/${units.distanceUnit.symbol.uppercase(Locale.US)}"
    },
    ROUNDS_COMPLETED("Rounds Completed", "MOST ROUNDS", isIntegerMetric = true) {
        override fun extractCanonical(workingSets: List<SessionSet>): Double? =
            sumPositiveInts(workingSets) { it.rounds }

        override fun toDisplay(canonical: Double, units: ProgressionUnits): Double = canonical

        override fun formatValue(canonical: Double, units: ProgressionUnits): String =
            formatPluralCount(canonical.roundToInt(), singular = "round", plural = "rounds")

        override fun formatAxis(display: Double): String = formatIntegerAxis(display)

        override fun unitSuffix(units: ProgressionUnits, maxCanonical: Double): String = "ROUNDS"
    },
    TOTAL_DURATION("Total Duration", "LONGEST DURATION") {
        override fun extractCanonical(workingSets: List<SessionSet>): Double? =
            sumPositiveInts(workingSets) { it.durationSeconds }

        override fun toDisplay(canonical: Double, units: ProgressionUnits): Double = canonical

        override fun formatValue(canonical: Double, units: ProgressionUnits): String =
            UnitConverter.formatDurationClock(canonical.roundToLong())

        override fun formatAxis(display: Double): String = formatClockAxis(display)

        override fun unitSuffix(units: ProgressionUnits, maxCanonical: Double): String =
            durationUnitSuffix(maxCanonical)
    };

    internal abstract fun extractCanonical(workingSets: List<SessionSet>): Double?
    internal abstract fun toDisplay(canonical: Double, units: ProgressionUnits): Double
    internal abstract fun formatValue(canonical: Double, units: ProgressionUnits): String
    internal abstract fun formatAxis(display: Double): String
    internal abstract fun unitSuffix(units: ProgressionUnits, maxCanonical: Double): String
}

private fun sumPositiveInts(
    workingSets: List<SessionSet>,
    selector: (SessionSet) -> Int?,
): Double? = workingSets
    .mapNotNull { selector(it)?.takeIf { v -> v > 0 } }
    .takeIf { it.isNotEmpty() }
    ?.sum()
    ?.toDouble()

private fun formatPluralCount(
    count: Int,
    singular: String,
    plural: String,
): String = if (count == 1) "1 $singular" else "$count $plural"

private fun formatIntegerAxis(display: Double): String =
    display.coerceAtLeast(0.0).roundToInt().toString()

private fun formatClockAxis(display: Double): String =
    UnitConverter.formatDurationClock(display.coerceAtLeast(0.0).roundToLong())

private fun durationUnitSuffix(maxCanonicalSeconds: Double): String =
    if (maxCanonicalSeconds >= 3_600.0) "HH:MM:SS" else "MM:SS"

/** Single chronological Session data point on an Exercise progression HUD chart. */
data class ExerciseChartPoint(
    val sessionId: String,
    val sessionTitle: String,
    val timestamp: Instant,
    val canonicalValue: Double,
    val displayValue: Double,
    val formattedValue: String,
    val dateLabel: String,
)

/** All-time Personal Record achieved for a specific ProgressionMetric of an Exercise. */
data class ExercisePersonalRecord(
    val metric: ProgressionMetric,
    val canonicalValue: Double,
    val displayValue: Double,
    val formattedValue: String,
    val achievedAt: Instant,
    val sessionId: String,
    val dateLabel: String,
)

/** Complete PR summary and time-range chart series for one selected Exercise. */
data class ExerciseProgressionReport(
    val exercise: Exercise,
    val timeRange: ProgressTimeRange,
    val isWeightedRepsAndLoad: Boolean,
    val availableMetrics: List<ProgressionMetric>,
    val selectedMetric: ProgressionMetric,
    val unitSuffix: String,
    val personalRecords: List<ExercisePersonalRecord>,
    val chartPoints: List<ExerciseChartPoint>,
    val hasAnyAllTimeHistory: Boolean,
)

/** Pure-Kotlin calculator for per-Exercise Personal Records and progression chart series. */
object ExerciseProgressionCalculator {
    fun selectDefaultExercise(
        exercises: List<Exercise>,
        sessions: List<Session>,
    ): Exercise? {
        if (exercises.isEmpty()) return null
        val activeById = exercises.associateBy { it.id }
        val eligibleSortedDesc = ProgressAnalyticsCalculator.filterEligibleSessions(sessions)
            .sortedWith(compareByDescending<Session> { it.startedAt }.thenByDescending { it.createdAt })

        for (session in eligibleSortedDesc) {
            val matchedExercise = session.exercises
                .asSequence()
                .filter { it.deletedAt == null }
                .mapNotNull { activeById[it.exerciseId] }
                .firstOrNull()
            if (matchedExercise != null) return matchedExercise
        }
        return exercises.first()
    }

    fun calculate(
        exercise: Exercise,
        sessions: List<Session>,
        timeRange: ProgressTimeRange,
        requestedMetric: ProgressionMetric?,
        weightUnit: WeightUnit,
        distanceUnit: DistanceUnit,
        clock: Clock,
    ): ExerciseProgressionReport {
        val units = ProgressionUnits(weightUnit = weightUnit, distanceUnit = distanceUnit)
        val eligibleSessionsAsc = ProgressAnalyticsCalculator.filterEligibleSessions(sessions)
            .sortedWith(compareBy<Session>({ it.startedAt }, { it.createdAt }, { it.id }))

        val sessionEntries = eligibleSessionsAsc.mapNotNull { session ->
            val workingSets = session.exercises
                .filter { it.exerciseId == exercise.id && it.deletedAt == null }
                .flatMap { it.sets }
                .filter { it.deletedAt == null && !it.isWarmup }
            if (workingSets.isEmpty()) null else SessionWorkingSets(session, workingSets)
        }

        val allWorkingSets = sessionEntries.flatMap { it.workingSets }
        val isWeightedRepsAndLoad = if (exercise.trackingMode == TrackingMode.REPS_AND_LOAD) {
            if (allWorkingSets.isNotEmpty()) {
                allWorkingSets.any { (it.loadKg ?: 0.0) > 0.0 }
            } else {
                exercise.discipline == Discipline.WEIGHT_LIFTING
            }
        } else {
            false
        }

        val availableMetrics = availableMetricsFor(
            trackingMode = exercise.trackingMode,
            isWeightedRepsAndLoad = isWeightedRepsAndLoad,
        )
        val selectedMetric = requestedMetric?.takeIf { it in availableMetrics } ?: availableMetrics.first()

        val allPointsByMetric: Map<ProgressionMetric, List<ExerciseChartPoint>> =
            availableMetrics.associateWith { metric ->
                sessionEntries.mapNotNull { entry ->
                    val canonical = metric.extractCanonical(entry.workingSets) ?: return@mapNotNull null
                    ExerciseChartPoint(
                        sessionId = entry.session.id,
                        sessionTitle = entry.session.title.ifBlank {
                            "${entry.session.discipline.displayName} Session"
                        },
                        timestamp = entry.session.startedAt,
                        canonicalValue = canonical,
                        displayValue = metric.toDisplay(canonical, units),
                        formattedValue = metric.formatValue(canonical, units),
                        dateLabel = formatChartDateLabel(entry.session.startedAt, clock),
                    )
                }
            }

        val personalRecords = availableMetrics.mapNotNull { metric ->
            val metricPoints = allPointsByMetric[metric].orEmpty()
            val bestPoint = if (metric.isLowerBetter) {
                metricPoints.minByOrNull { it.canonicalValue }
            } else {
                metricPoints.maxByOrNull { it.canonicalValue }
            } ?: return@mapNotNull null

            ExercisePersonalRecord(
                metric = metric,
                canonicalValue = bestPoint.canonicalValue,
                displayValue = bestPoint.displayValue,
                formattedValue = bestPoint.formattedValue,
                achievedAt = bestPoint.timestamp,
                sessionId = bestPoint.sessionId,
                dateLabel = bestPoint.dateLabel,
            )
        }

        val cutoff = ProgressAnalyticsCalculator.cutoffInstant(timeRange, clock)
        val selectedChartPoints = allPointsByMetric[selectedMetric].orEmpty().filter { point ->
            cutoff == null || !point.timestamp.isBefore(cutoff)
        }
        val maxCanonicalInChart = selectedChartPoints.maxOfOrNull { it.canonicalValue } ?: 0.0

        return ExerciseProgressionReport(
            exercise = exercise,
            timeRange = timeRange,
            isWeightedRepsAndLoad = isWeightedRepsAndLoad,
            availableMetrics = availableMetrics,
            selectedMetric = selectedMetric,
            unitSuffix = selectedMetric.unitSuffix(units, maxCanonicalInChart),
            personalRecords = personalRecords,
            chartPoints = selectedChartPoints,
            hasAnyAllTimeHistory = personalRecords.isNotEmpty(),
        )
    }

    fun formatAxisValue(
        metric: ProgressionMetric,
        displayValue: Double,
    ): String = metric.formatAxis(displayValue)

    private fun availableMetricsFor(
        trackingMode: TrackingMode,
        isWeightedRepsAndLoad: Boolean,
    ): List<ProgressionMetric> = when (trackingMode) {
        TrackingMode.REPS_AND_LOAD -> if (isWeightedRepsAndLoad) {
            listOf(ProgressionMetric.TOTAL_VOLUME, ProgressionMetric.MAX_LOAD)
        } else {
            listOf(ProgressionMetric.TOTAL_REPS, ProgressionMetric.BEST_SET_REPS)
        }
        TrackingMode.HOLD_DURATION -> listOf(
            ProgressionMetric.LONGEST_HOLD,
            ProgressionMetric.TOTAL_HOLD_TIME,
        )
        TrackingMode.DISTANCE_AND_TIME -> listOf(
            ProgressionMetric.DISTANCE,
            ProgressionMetric.PACE,
        )
        TrackingMode.CONDITIONING_ROUNDS -> listOf(
            ProgressionMetric.ROUNDS_COMPLETED,
            ProgressionMetric.TOTAL_DURATION,
        )
    }

    private data class SessionWorkingSets(
        val session: Session,
        val workingSets: List<SessionSet>,
    )
}
