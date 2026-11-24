package com.e4eheh.e4ehehworkout.domain.analytics

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
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseProgressionCalculatorTest {
    private val clock = FakeClock(initialInstant = Instant.parse("2026-11-23T08:00:00Z"))

    @Test
    fun repsAndLoad_bodyweight_computesTotalRepsBestSetRepsPrsAndFallsBackMetric() {
        val pullUps = exercise(
            id = "ex-pullup",
            name = "Pull-up",
            discipline = Discipline.CALISTHENICS,
            trackingMode = TrackingMode.REPS_AND_LOAD,
        )
        val sessions = listOf(
            sessionWithSets(
                id = "s-1",
                title = "Pull Day 1",
                startedAt = "2026-11-10T08:00:00Z",
                exercise = pullUps,
                sets = listOf(
                    set(reps = 10, loadKg = 0.0),
                    set(reps = 8, loadKg = 0.0),
                ),
            ),
            sessionWithSets(
                id = "s-2",
                title = "Pull Day 2",
                startedAt = "2026-11-20T08:00:00Z",
                exercise = pullUps,
                sets = listOf(
                    set(reps = 12, loadKg = 0.0),
                    set(reps = 5, loadKg = 0.0),
                ),
            ),
        )

        val totalRepsReport = ExerciseProgressionCalculator.calculate(
            exercise = pullUps,
            sessions = sessions,
            timeRange = ProgressTimeRange.ALL,
            requestedMetric = ProgressionMetric.MAX_LOAD, // Invalid for bodyweight -> fallback
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.KM,
            clock = clock,
        )

        assertFalse(totalRepsReport.isWeightedRepsAndLoad)
        assertEquals(
            listOf(ProgressionMetric.TOTAL_REPS, ProgressionMetric.BEST_SET_REPS),
            totalRepsReport.availableMetrics,
        )
        assertEquals(ProgressionMetric.TOTAL_REPS, totalRepsReport.selectedMetric)
        assertEquals("REPS", totalRepsReport.unitSuffix)
        assertTrue(totalRepsReport.hasAnyAllTimeHistory)
        assertEquals(listOf(18.0, 17.0), totalRepsReport.chartPoints.map { it.displayValue })
        assertEquals(listOf("18 reps", "17 reps"), totalRepsReport.chartPoints.map { it.formattedValue })
        assertEquals(listOf("Nov 10", "Nov 20"), totalRepsReport.chartPoints.map { it.dateLabel })

        val prsByMetric = totalRepsReport.personalRecords.associateBy { it.metric }
        assertEquals(18.0, prsByMetric.getValue(ProgressionMetric.TOTAL_REPS).canonicalValue, 0.0)
        assertEquals("18 reps", prsByMetric.getValue(ProgressionMetric.TOTAL_REPS).formattedValue)
        assertEquals("s-1", prsByMetric.getValue(ProgressionMetric.TOTAL_REPS).sessionId)
        assertEquals(12.0, prsByMetric.getValue(ProgressionMetric.BEST_SET_REPS).canonicalValue, 0.0)
        assertEquals("12 reps", prsByMetric.getValue(ProgressionMetric.BEST_SET_REPS).formattedValue)
        assertEquals("s-2", prsByMetric.getValue(ProgressionMetric.BEST_SET_REPS).sessionId)

        val bestSetReport = ExerciseProgressionCalculator.calculate(
            exercise = pullUps,
            sessions = sessions,
            timeRange = ProgressTimeRange.ALL,
            requestedMetric = ProgressionMetric.BEST_SET_REPS,
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.KM,
            clock = clock,
        )
        assertEquals(ProgressionMetric.BEST_SET_REPS, bestSetReport.selectedMetric)
        assertEquals(listOf(10.0, 12.0), bestSetReport.chartPoints.map { it.displayValue })
    }

    @Test
    fun repsAndLoad_weighted_computesTotalVolumeMaxLoadConvertsUnitsAndIgnoresBlankRows() {
        val bench = exercise(
            id = "ex-bench",
            name = "Bench Press",
            discipline = Discipline.WEIGHT_LIFTING,
            trackingMode = TrackingMode.REPS_AND_LOAD,
        )
        val sessions = listOf(
            sessionWithSets(
                id = "s-1",
                title = "Upper 1",
                startedAt = "2026-11-10T08:00:00Z",
                exercise = bench,
                sets = listOf(
                    set(reps = 5, loadKg = 80.0),
                    set(reps = 5, loadKg = 100.0),
                    // Blank Routine target Set must not create a 0 load point
                    set(reps = null, loadKg = 0.0),
                ),
            ),
            sessionWithSets(
                id = "s-2",
                title = "Upper 2",
                startedAt = "2026-11-20T08:00:00Z",
                exercise = bench,
                sets = listOf(
                    // Session with only an unlogged Set is skipped below
                    set(reps = 8, loadKg = 90.0),
                ),
            ),
            sessionWithSets(
                id = "s-blank",
                title = "Unlogged Routine Slot",
                startedAt = "2026-11-21T08:00:00Z",
                exercise = bench,
                sets = listOf(set(reps = null, loadKg = 0.0)),
            ),
        )

        val kgReport = ExerciseProgressionCalculator.calculate(
            exercise = bench,
            sessions = sessions,
            timeRange = ProgressTimeRange.ALL,
            requestedMetric = ProgressionMetric.TOTAL_VOLUME,
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.KM,
            clock = clock,
        )

        assertTrue(kgReport.isWeightedRepsAndLoad)
        assertEquals(
            listOf(ProgressionMetric.TOTAL_VOLUME, ProgressionMetric.MAX_LOAD),
            kgReport.availableMetrics,
        )
        assertEquals("KG", kgReport.unitSuffix)
        assertEquals(listOf("s-1", "s-2"), kgReport.chartPoints.map { it.sessionId })
        assertEquals(listOf(900.0, 720.0), kgReport.chartPoints.map { it.canonicalValue })
        assertEquals(listOf("900 kg", "720 kg"), kgReport.chartPoints.map { it.formattedValue })

        val kgPrs = kgReport.personalRecords.associateBy { it.metric }
        assertEquals("900 kg", kgPrs.getValue(ProgressionMetric.TOTAL_VOLUME).formattedValue)
        assertEquals("100 kg", kgPrs.getValue(ProgressionMetric.MAX_LOAD).formattedValue)

        val lbMaxLoadReport = ExerciseProgressionCalculator.calculate(
            exercise = bench,
            sessions = sessions,
            timeRange = ProgressTimeRange.ALL,
            requestedMetric = ProgressionMetric.MAX_LOAD,
            weightUnit = WeightUnit.LB,
            distanceUnit = DistanceUnit.KM,
            clock = clock,
        )
        assertEquals("LB", lbMaxLoadReport.unitSuffix)
        assertEquals(220.46226, lbMaxLoadReport.chartPoints[0].displayValue, 1e-4)
        assertEquals("220.46 lb", lbMaxLoadReport.chartPoints[0].formattedValue)
        assertEquals(
            "220.46 lb",
            lbMaxLoadReport.personalRecords.first { it.metric == ProgressionMetric.MAX_LOAD }.formattedValue,
        )
    }

    @Test
    fun holdDuration_computesLongestHoldAndTotalHoldTime() {
        val lSit = exercise(
            id = "ex-lsit",
            name = "L-Sit",
            discipline = Discipline.CALISTHENICS,
            trackingMode = TrackingMode.HOLD_DURATION,
        )
        val sessions = listOf(
            sessionWithSets(
                id = "s-1",
                title = "Skill 1",
                startedAt = "2026-11-12T08:00:00Z",
                exercise = lSit,
                sets = listOf(set(durationSeconds = 35), set(durationSeconds = 30)),
            ),
            sessionWithSets(
                id = "s-2",
                title = "Skill 2",
                startedAt = "2026-11-19T08:00:00Z",
                exercise = lSit,
                sets = listOf(set(durationSeconds = 45), set(durationSeconds = 15)),
            ),
        )

        val longestReport = ExerciseProgressionCalculator.calculate(
            exercise = lSit,
            sessions = sessions,
            timeRange = ProgressTimeRange.ALL,
            requestedMetric = ProgressionMetric.LONGEST_HOLD,
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.KM,
            clock = clock,
        )
        assertEquals(
            listOf(ProgressionMetric.LONGEST_HOLD, ProgressionMetric.TOTAL_HOLD_TIME),
            longestReport.availableMetrics,
        )
        assertEquals("MM:SS", longestReport.unitSuffix)
        assertEquals(listOf(35.0, 45.0), longestReport.chartPoints.map { it.displayValue })
        assertEquals(listOf("00:35", "00:45"), longestReport.chartPoints.map { it.formattedValue })

        val prs = longestReport.personalRecords.associateBy { it.metric }
        assertEquals("00:45", prs.getValue(ProgressionMetric.LONGEST_HOLD).formattedValue)
        assertEquals("01:05", prs.getValue(ProgressionMetric.TOTAL_HOLD_TIME).formattedValue)

        val totalReport = ExerciseProgressionCalculator.calculate(
            exercise = lSit,
            sessions = sessions,
            timeRange = ProgressTimeRange.ALL,
            requestedMetric = ProgressionMetric.TOTAL_HOLD_TIME,
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.KM,
            clock = clock,
        )
        assertEquals(listOf(65.0, 60.0), totalReport.chartPoints.map { it.displayValue })
    }

    @Test
    fun distanceAndTime_computesDistanceAndFastestPacePrWithUnitConversion() {
        val run = exercise(
            id = "ex-run",
            name = "5K Run",
            discipline = Discipline.CARDIO,
            trackingMode = TrackingMode.DISTANCE_AND_TIME,
        )
        val sessions = listOf(
            sessionWithSets(
                id = "s-slow-long",
                title = "Long Run",
                startedAt = "2026-11-10T08:00:00Z",
                exercise = run,
                sets = listOf(set(distanceKm = 8.0, durationSeconds = 2_880)), // 6:00 /km
            ),
            sessionWithSets(
                id = "s-fast-5k",
                title = "Tempo 5K",
                startedAt = "2026-11-20T08:00:00Z",
                exercise = run,
                sets = listOf(set(distanceKm = 5.0, durationSeconds = 1_500)), // 5:00 /km
            ),
        )

        val paceKmReport = ExerciseProgressionCalculator.calculate(
            exercise = run,
            sessions = sessions,
            timeRange = ProgressTimeRange.ALL,
            requestedMetric = ProgressionMetric.PACE,
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.KM,
            clock = clock,
        )
        assertEquals(
            listOf(ProgressionMetric.DISTANCE, ProgressionMetric.PACE),
            paceKmReport.availableMetrics,
        )
        assertEquals("MIN/KM", paceKmReport.unitSuffix)
        assertEquals(listOf(360.0, 300.0), paceKmReport.chartPoints.map { it.displayValue })
        assertEquals(listOf("06:00 /km", "05:00 /km"), paceKmReport.chartPoints.map { it.formattedValue })

        val kmPrs = paceKmReport.personalRecords.associateBy { it.metric }
        assertEquals("8 km", kmPrs.getValue(ProgressionMetric.DISTANCE).formattedValue)
        assertEquals("05:00 /km", kmPrs.getValue(ProgressionMetric.PACE).formattedValue)
        assertEquals("s-fast-5k", kmPrs.getValue(ProgressionMetric.PACE).sessionId)

        val paceMiReport = ExerciseProgressionCalculator.calculate(
            exercise = run,
            sessions = sessions,
            timeRange = ProgressTimeRange.ALL,
            requestedMetric = ProgressionMetric.PACE,
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.MI,
            clock = clock,
        )
        assertEquals("MIN/MI", paceMiReport.unitSuffix)
        assertEquals("08:03 /mi", paceMiReport.chartPoints[1].formattedValue)
        val miPrs = paceMiReport.personalRecords.associateBy { it.metric }
        assertEquals("4.97 mi", miPrs.getValue(ProgressionMetric.DISTANCE).formattedValue)
        assertEquals("08:03 /mi", miPrs.getValue(ProgressionMetric.PACE).formattedValue)
    }

    @Test
    fun conditioningRounds_computesRoundsCompletedAndTotalDuration() {
        val emom = exercise(
            id = "ex-emom",
            name = "EMOM Circuit",
            discipline = Discipline.CONDITIONING,
            trackingMode = TrackingMode.CONDITIONING_ROUNDS,
        )
        val sessions = listOf(
            sessionWithSets(
                id = "s-1",
                title = "EMOM A",
                startedAt = "2026-11-11T08:00:00Z",
                exercise = emom,
                sets = listOf(set(rounds = 6, durationSeconds = 900)),
            ),
            sessionWithSets(
                id = "s-2",
                title = "EMOM B",
                startedAt = "2026-11-21T08:00:00Z",
                exercise = emom,
                sets = listOf(set(rounds = 8, durationSeconds = 720)),
            ),
        )

        val report = ExerciseProgressionCalculator.calculate(
            exercise = emom,
            sessions = sessions,
            timeRange = ProgressTimeRange.ALL,
            requestedMetric = ProgressionMetric.ROUNDS_COMPLETED,
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.KM,
            clock = clock,
        )
        assertEquals(
            listOf(ProgressionMetric.ROUNDS_COMPLETED, ProgressionMetric.TOTAL_DURATION),
            report.availableMetrics,
        )
        assertEquals("ROUNDS", report.unitSuffix)
        assertEquals(listOf(6.0, 8.0), report.chartPoints.map { it.displayValue })
        assertEquals(listOf("6 rounds", "8 rounds"), report.chartPoints.map { it.formattedValue })

        val prs = report.personalRecords.associateBy { it.metric }
        assertEquals("8 rounds", prs.getValue(ProgressionMetric.ROUNDS_COMPLETED).formattedValue)
        assertEquals("15:00", prs.getValue(ProgressionMetric.TOTAL_DURATION).formattedValue)
    }

    @Test
    fun exclusionsAndTimeRanges_strictlyExcludeWarmupsDraftsAndTombstonesWhileKeepingAllTimePrs() {
        val pullUps = exercise(
            id = "ex-pullup",
            name = "Pull-up",
            discipline = Discipline.CALISTHENICS,
            trackingMode = TrackingMode.REPS_AND_LOAD,
        )
        val deletedTime = Instant.parse("2026-11-22T09:00:00Z")
        val sessions = listOf(
            // Old completed session (outside 1W, inside ALL) - holds PR
            sessionWithSets(
                id = "s-old-pr",
                title = "Old PR",
                startedAt = "2026-10-01T08:00:00Z",
                exercise = pullUps,
                sets = listOf(set(reps = 15), set(reps = 20, isWarmup = true)),
            ),
            // Recent session inside 1W with warmup + soft-deleted set
            sessionWithSets(
                id = "s-recent",
                title = "Recent",
                startedAt = "2026-11-22T08:00:00Z",
                exercise = pullUps,
                sets = listOf(
                    set(reps = 10),
                    set(reps = 99, isWarmup = true),
                    set(reps = 88, deletedAt = deletedTime),
                ),
            ),
            // Warm-up only session -> excluded completely
            sessionWithSets(
                id = "s-warmup-only",
                title = "Warmup Only",
                startedAt = "2026-11-23T07:00:00Z",
                exercise = pullUps,
                sets = listOf(set(reps = 50, isWarmup = true)),
            ),
            // IN_PROGRESS draft -> excluded
            sessionWithSets(
                id = "s-draft",
                title = "Draft",
                startedAt = "2026-11-23T07:30:00Z",
                exercise = pullUps,
                sets = listOf(set(reps = 40)),
                status = SessionStatus.IN_PROGRESS,
            ),
            // Soft-deleted session -> excluded
            sessionWithSets(
                id = "s-deleted-session",
                title = "Deleted Session",
                startedAt = "2026-11-23T06:00:00Z",
                exercise = pullUps,
                sets = listOf(set(reps = 30)),
                sessionDeletedAt = deletedTime,
            ),
            // Session with soft-deleted SessionExercise -> excluded
            sessionWithSets(
                id = "s-deleted-ex",
                title = "Deleted Exercise",
                startedAt = "2026-11-23T05:00:00Z",
                exercise = pullUps,
                sets = listOf(set(reps = 25)),
                sessionExerciseDeletedAt = deletedTime,
            ),
        )

        val oneWeekReport = ExerciseProgressionCalculator.calculate(
            exercise = pullUps,
            sessions = sessions,
            timeRange = ProgressTimeRange.ONE_WEEK,
            requestedMetric = ProgressionMetric.TOTAL_REPS,
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.KM,
            clock = clock,
        )

        // Chart only includes s-recent working set (10 reps)
        assertEquals(listOf("s-recent"), oneWeekReport.chartPoints.map { it.sessionId })
        assertEquals(listOf(10.0), oneWeekReport.chartPoints.map { it.displayValue })
        // PRs are all-time across eligible completed sessions (s-old-pr = 15 reps)
        assertTrue(oneWeekReport.hasAnyAllTimeHistory)
        val prs = oneWeekReport.personalRecords.associateBy { it.metric }
        assertEquals("15 reps", prs.getValue(ProgressionMetric.TOTAL_REPS).formattedValue)
        assertEquals("15 reps", prs.getValue(ProgressionMetric.BEST_SET_REPS).formattedValue)

        // Advance clock so 1W chart is empty, but all-time PRs & history remain
        val laterClock = FakeClock(initialInstant = Instant.parse("2026-12-10T08:00:00Z"))
        val emptyWindowReport = ExerciseProgressionCalculator.calculate(
            exercise = pullUps,
            sessions = sessions,
            timeRange = ProgressTimeRange.ONE_WEEK,
            requestedMetric = ProgressionMetric.TOTAL_REPS,
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.KM,
            clock = laterClock,
        )
        assertTrue(emptyWindowReport.chartPoints.isEmpty())
        assertTrue(emptyWindowReport.hasAnyAllTimeHistory)
        assertEquals(2, emptyWindowReport.personalRecords.size)
    }

    @Test
    fun selectDefaultExercise_prefersMostRecentlyLoggedActiveExerciseOrFirstActive() {
        val ex1 = exercise("ex-1", "Push-up", Discipline.CALISTHENICS, TrackingMode.REPS_AND_LOAD)
        val ex2 = exercise("ex-2", "L-Sit", Discipline.CALISTHENICS, TrackingMode.HOLD_DURATION)
        val sessions = listOf(
            sessionWithSets(
                id = "s-older",
                title = "Older",
                startedAt = "2026-11-10T08:00:00Z",
                exercise = ex1,
                sets = listOf(set(reps = 10)),
            ),
            sessionWithSets(
                id = "s-newer",
                title = "Newer",
                startedAt = "2026-11-22T08:00:00Z",
                exercise = ex2,
                sets = listOf(set(durationSeconds = 30)),
            ),
        )

        assertEquals(ex2, ExerciseProgressionCalculator.selectDefaultExercise(listOf(ex1, ex2), sessions))
        assertEquals(ex1, ExerciseProgressionCalculator.selectDefaultExercise(listOf(ex1, ex2), emptyList()))
        assertNull(ExerciseProgressionCalculator.selectDefaultExercise(emptyList(), sessions))
    }

    @Test
    fun formatAxisValue_andDurationSuffix_formatMonospaceTicksAndHourPlusDurations() {
        assertEquals("12", ExerciseProgressionCalculator.formatAxisValue(ProgressionMetric.TOTAL_REPS, 12.4))
        assertEquals("8", ExerciseProgressionCalculator.formatAxisValue(ProgressionMetric.ROUNDS_COMPLETED, 8.0))
        assertEquals("220.5", ExerciseProgressionCalculator.formatAxisValue(ProgressionMetric.MAX_LOAD, 220.46))
        assertEquals("3.11", ExerciseProgressionCalculator.formatAxisValue(ProgressionMetric.DISTANCE, 3.1068))
        assertEquals("05:00", ExerciseProgressionCalculator.formatAxisValue(ProgressionMetric.PACE, 300.0))
        assertEquals("01:05:00", ExerciseProgressionCalculator.formatAxisValue(ProgressionMetric.TOTAL_DURATION, 3_900.0))

        val emom = exercise("ex-emom", "EMOM", Discipline.CONDITIONING, TrackingMode.CONDITIONING_ROUNDS)
        val longReport = ExerciseProgressionCalculator.calculate(
            exercise = emom,
            sessions = listOf(
                sessionWithSets(
                    id = "s-long",
                    title = "Long Conditioning",
                    startedAt = "2026-11-20T08:00:00Z",
                    exercise = emom,
                    sets = listOf(set(rounds = 20, durationSeconds = 3_900)),
                ),
            ),
            timeRange = ProgressTimeRange.ALL,
            requestedMetric = ProgressionMetric.TOTAL_DURATION,
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.KM,
            clock = clock,
        )
        assertEquals("HH:MM:SS", longReport.unitSuffix)
    }

    private fun exercise(
        id: String,
        name: String,
        discipline: Discipline,
        trackingMode: TrackingMode,
    ): Exercise {
        val now = Instant.parse("2026-11-01T08:00:00Z")
        return Exercise(
            id = id,
            name = name,
            discipline = discipline,
            trackingMode = trackingMode,
            defaultNotes = null,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            syncStatus = SyncStatus.PENDING,
        )
    }

    private fun set(
        reps: Int? = null,
        loadKg: Double? = null,
        durationSeconds: Int? = null,
        distanceKm: Double? = null,
        rounds: Int? = null,
        isWarmup: Boolean = false,
        deletedAt: Instant? = null,
    ): SessionSet {
        val now = Instant.parse("2026-11-01T08:00:00Z")
        return SessionSet(
            id = UUID.randomUUID().toString(),
            sessionExerciseId = "se-1",
            sortOrder = 0,
            reps = reps,
            loadKg = loadKg,
            durationSeconds = durationSeconds,
            distanceKm = distanceKm,
            rounds = rounds,
            isCompleted = true,
            isWarmup = isWarmup,
            createdAt = now,
            updatedAt = now,
            deletedAt = deletedAt,
            syncStatus = SyncStatus.PENDING,
        )
    }

    private fun sessionWithSets(
        id: String,
        title: String,
        startedAt: String,
        exercise: Exercise,
        sets: List<SessionSet>,
        status: SessionStatus = SessionStatus.COMPLETED,
        sessionDeletedAt: Instant? = null,
        sessionExerciseDeletedAt: Instant? = null,
    ): Session {
        val start = Instant.parse(startedAt)
        val se = SessionExercise(
            id = "se-$id",
            sessionId = id,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            exerciseDiscipline = exercise.discipline,
            trackingMode = exercise.trackingMode,
            sortOrder = 0,
            notes = null,
            sets = sets,
            createdAt = start,
            updatedAt = start,
            deletedAt = sessionExerciseDeletedAt,
            syncStatus = SyncStatus.PENDING,
        )
        return Session(
            id = id,
            routineId = null,
            title = title,
            discipline = exercise.discipline,
            status = status,
            startedAt = start,
            completedAt = if (status == SessionStatus.COMPLETED) start.plusSeconds(1_800L) else null,
            durationSeconds = 1_800L,
            notes = null,
            exercises = listOf(se),
            createdAt = start,
            updatedAt = start,
            deletedAt = sessionDeletedAt,
            syncStatus = SyncStatus.PENDING,
        )
    }
}
