package com.e4eheh.e4ehehworkout.domain.analytics

import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntry
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionExercise
import com.e4eheh.e4ehehworkout.domain.model.SessionSet
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyweightTrendCalculatorTest {
    private val clock = FakeClock(initialInstant = Instant.parse("2026-11-23T08:00:00Z"))

    @Test
    fun emptyEntries_returnsEmptyReportWithNoHistory() {
        val report = BodyweightTrendCalculator.calculate(
            entries = emptyList(),
            timeRange = ProgressTimeRange.ALL,
            weightUnit = WeightUnit.KG,
            clock = clock,
        )

        assertEquals(ProgressTimeRange.ALL, report.timeRange)
        assertEquals(WeightUnit.KG, report.weightUnit)
        assertEquals("KG", report.unitSuffix)
        assertTrue(report.chartPoints.isEmpty())
        assertNull(report.latestPoint)
        assertNull(report.rangeDeltaDisplay)
        assertNull(report.formattedRangeDelta)
        assertFalse(report.hasAnyAllTimeHistory)
    }

    @Test
    fun calculate_sortsChronologically_excludesTombstones_andFiltersTimeRanges() {
        val entries = listOf(
            entry(
                id = "bw-1y-old",
                recordedAt = "2025-10-01T08:00:00Z",
                weightKg = 80.0,
            ),
            entry(
                id = "bw-3m",
                recordedAt = "2026-09-10T08:00:00Z",
                weightKg = 78.0,
            ),
            entry(
                id = "bw-1m",
                recordedAt = "2026-11-05T08:00:00Z",
                weightKg = 76.5,
            ),
            entry(
                id = "bw-1w-boundary",
                recordedAt = "2026-11-16T08:00:00Z", // exactly 1W cutoff -> included
                weightKg = 75.5,
            ),
            entry(
                id = "bw-same-day-later-create",
                recordedAt = "2026-11-22T08:00:00Z",
                createdAt = "2026-11-22T09:00:00Z",
                weightKg = 74.8,
            ),
            entry(
                id = "bw-same-day-earlier-create",
                recordedAt = "2026-11-22T08:00:00Z",
                createdAt = "2026-11-22T08:05:00Z",
                weightKg = 75.0,
            ),
            entry(
                id = "bw-deleted",
                recordedAt = "2026-11-23T07:00:00Z",
                weightKg = 99.0,
                deletedAt = "2026-11-23T07:30:00Z",
            ),
        )

        val allReport = BodyweightTrendCalculator.calculate(
            entries = entries,
            timeRange = ProgressTimeRange.ALL,
            weightUnit = WeightUnit.KG,
            clock = clock,
        )
        assertEquals(
            listOf(
                "bw-1y-old",
                "bw-3m",
                "bw-1m",
                "bw-1w-boundary",
                "bw-same-day-earlier-create",
                "bw-same-day-later-create",
            ),
            allReport.chartPoints.map { it.entryId },
        )
        assertEquals("Oct 1 '25", allReport.chartPoints.first().dateLabel)
        assertEquals("Nov 22", allReport.chartPoints.last().dateLabel)
        assertEquals("74.8 kg", allReport.latestPoint?.formattedWeight)
        assertEquals("-5.2 kg", allReport.formattedRangeDelta)
        assertTrue(allReport.hasAnyAllTimeHistory)

        val oneYear = BodyweightTrendCalculator.calculate(
            entries = entries,
            timeRange = ProgressTimeRange.ONE_YEAR,
            weightUnit = WeightUnit.KG,
            clock = clock,
        )
        assertEquals(5, oneYear.chartPoints.size)
        assertEquals("-3.2 kg", oneYear.formattedRangeDelta)

        val threeMonths = BodyweightTrendCalculator.calculate(
            entries = entries,
            timeRange = ProgressTimeRange.THREE_MONTHS,
            weightUnit = WeightUnit.KG,
            clock = clock,
        )
        assertEquals(5, threeMonths.chartPoints.size)

        val oneMonth = BodyweightTrendCalculator.calculate(
            entries = entries,
            timeRange = ProgressTimeRange.ONE_MONTH,
            weightUnit = WeightUnit.KG,
            clock = clock,
        )
        assertEquals(4, oneMonth.chartPoints.size)
        assertEquals("-1.7 kg", oneMonth.formattedRangeDelta)

        val oneWeek = BodyweightTrendCalculator.calculate(
            entries = entries,
            timeRange = ProgressTimeRange.ONE_WEEK,
            weightUnit = WeightUnit.KG,
            clock = clock,
        )
        assertEquals(
            listOf("bw-1w-boundary", "bw-same-day-earlier-create", "bw-same-day-later-create"),
            oneWeek.chartPoints.map { it.entryId },
        )
        assertEquals("-0.7 kg", oneWeek.formattedRangeDelta)

        // Advance clock past 1W -> empty window, but all-time history remains true
        clock.advanceBy(Duration.ofDays(10))
        val emptyOneWeek = BodyweightTrendCalculator.calculate(
            entries = entries,
            timeRange = ProgressTimeRange.ONE_WEEK,
            weightUnit = WeightUnit.KG,
            clock = clock,
        )
        assertTrue(emptyOneWeek.chartPoints.isEmpty())
        assertNull(emptyOneWeek.latestPoint)
        assertNull(emptyOneWeek.formattedRangeDelta)
        assertTrue(emptyOneWeek.hasAnyAllTimeHistory)
    }

    @Test
    fun calculate_convertsUnitsToLb_computesPositiveZeroNegativeDeltas_andZoomsAxisBounds() {
        val entries = listOf(
            entry(id = "e1", recordedAt = "2026-11-10T08:00:00Z", weightKg = 70.0),
            entry(id = "e2", recordedAt = "2026-11-20T08:00:00Z", weightKg = 72.5),
        )

        val lbReport = BodyweightTrendCalculator.calculate(
            entries = entries,
            timeRange = ProgressTimeRange.ALL,
            weightUnit = WeightUnit.LB,
            clock = clock,
        )
        assertEquals(WeightUnit.LB, lbReport.weightUnit)
        assertEquals("LB", lbReport.unitSuffix)
        assertEquals(70.0, lbReport.chartPoints[0].weightKg, 1e-9)
        assertEquals(72.5, lbReport.chartPoints[1].weightKg, 1e-9)
        assertEquals("154.32 lb", lbReport.chartPoints[0].formattedWeight)
        assertEquals("159.84 lb", lbReport.chartPoints[1].formattedWeight)
        assertEquals("+5.51 lb", lbReport.formattedRangeDelta)

        // Zero delta formats cleanly without +/- sign
        val flatReport = BodyweightTrendCalculator.calculate(
            entries = listOf(
                entry(id = "f1", recordedAt = "2026-11-10T08:00:00Z", weightKg = 70.0),
                entry(id = "f2", recordedAt = "2026-11-20T08:00:00Z", weightKg = 70.0),
            ),
            timeRange = ProgressTimeRange.ALL,
            weightUnit = WeightUnit.KG,
            clock = clock,
        )
        assertEquals("0 kg", flatReport.formattedRangeDelta)

        val (singleMin, singleMax) = BodyweightTrendCalculator.computeAxisBounds(listOf(75.0))
        assertTrue(singleMin > 65.0 && singleMin < 75.0)
        assertTrue(singleMax > 75.0 && singleMax < 85.0)

        val (multiMin, multiMax) = BodyweightTrendCalculator.computeAxisBounds(listOf(74.0, 76.0))
        assertEquals(73.5, multiMin, 1e-6)
        assertEquals(76.5, multiMax, 1e-6)
    }

    @Test
    fun bodyweightEntries_neverAlterBodyweightExerciseProgressionOrPrs() {
        val pullUps = Exercise(
            id = "ex-pullup",
            name = "Pull-up",
            discipline = Discipline.CALISTHENICS,
            trackingMode = TrackingMode.REPS_AND_LOAD,
            defaultNotes = null,
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            deletedAt = null,
            syncStatus = SyncStatus.PENDING,
        )
        val sessions = listOf(
            Session(
                id = "s-1",
                routineId = null,
                title = "Pull Day",
                discipline = Discipline.CALISTHENICS,
                status = SessionStatus.COMPLETED,
                startedAt = Instant.parse("2026-11-20T08:00:00Z"),
                completedAt = Instant.parse("2026-11-20T08:30:00Z"),
                durationSeconds = 1_800L,
                notes = null,
                exercises = listOf(
                    SessionExercise(
                        id = "se-1",
                        sessionId = "s-1",
                        exerciseId = pullUps.id,
                        exerciseName = pullUps.name,
                        exerciseDiscipline = pullUps.discipline,
                        trackingMode = pullUps.trackingMode,
                        sortOrder = 0,
                        notes = null,
                        sets = listOf(
                            SessionSet(
                                id = "set-1",
                                sessionExerciseId = "se-1",
                                sortOrder = 0,
                                reps = 12,
                                loadKg = 0.0,
                                durationSeconds = null,
                                distanceKm = null,
                                rounds = null,
                                isCompleted = true,
                                isWarmup = false,
                                createdAt = clock.instant(),
                                updatedAt = clock.instant(),
                                deletedAt = null,
                                syncStatus = SyncStatus.PENDING,
                            ),
                        ),
                        createdAt = clock.instant(),
                        updatedAt = clock.instant(),
                        deletedAt = null,
                        syncStatus = SyncStatus.PENDING,
                    ),
                ),
                createdAt = clock.instant(),
                updatedAt = clock.instant(),
                deletedAt = null,
                syncStatus = SyncStatus.PENDING,
            ),
        )

        val progression = ExerciseProgressionCalculator.calculate(
            exercise = pullUps,
            sessions = sessions,
            timeRange = ProgressTimeRange.ALL,
            requestedMetric = null,
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.KM,
            clock = clock,
        )

        assertFalse(progression.isWeightedRepsAndLoad)
        assertEquals(
            listOf(ProgressionMetric.TOTAL_REPS, ProgressionMetric.BEST_SET_REPS),
            progression.availableMetrics,
        )
        assertEquals(12.0, progression.chartPoints.single().canonicalValue, 0.0)
        assertEquals("12 reps", progression.personalRecords.first().formattedValue)
    }

    private fun entry(
        id: String,
        recordedAt: String,
        weightKg: Double,
        createdAt: String = recordedAt,
        deletedAt: String? = null,
    ): BodyweightEntry {
        val created = Instant.parse(createdAt)
        return BodyweightEntry(
            id = id,
            recordedAt = Instant.parse(recordedAt),
            weightKg = weightKg,
            createdAt = created,
            updatedAt = created,
            deletedAt = deletedAt?.let(Instant::parse),
            syncStatus = SyncStatus.PENDING,
        )
    }
}
