package com.e4eheh.e4ehehworkout.domain.analytics

import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressAnalyticsCalculatorTest {
    private val clock = FakeClock(initialInstant = Instant.parse("2026-11-23T08:00:00Z")) // Monday

    @Test
    fun emptySessions_returnsZeroedMetricsAndAllFourDisciplines() {
        val result = ProgressAnalyticsCalculator.calculate(
            sessions = emptyList(),
            timeRange = ProgressTimeRange.ALL,
            clock = clock,
        )

        assertEquals(ProgressTimeRange.ALL, result.timeRange)
        assertEquals(0, result.totalCompletedSessions)
        assertEquals(0, result.activeWeeklyStreak)
        assertEquals(0L, result.totalDurationSeconds)
        assertEquals(Discipline.entries.size, result.disciplineBreakdown.size)
        result.disciplineBreakdown.forEach { item ->
            assertEquals(0, item.sessionCount)
            assertEquals(0f, item.fraction, 0.0001f)
            assertEquals(0, item.percentage)
        }
    }

    @Test
    fun inProgressAndSoftDeletedSessions_areStrictlyExcludedFromAllMetrics() {
        val sessions = listOf(
            session(
                startedAt = "2026-11-23T07:00:00Z",
                durationSeconds = 1_800L,
                discipline = Discipline.CALISTHENICS,
                status = SessionStatus.COMPLETED,
            ),
            session(
                startedAt = "2026-11-23T07:30:00Z",
                durationSeconds = 3_600L,
                discipline = Discipline.WEIGHT_LIFTING,
                status = SessionStatus.IN_PROGRESS,
            ),
            session(
                startedAt = "2026-11-16T18:00:00Z",
                durationSeconds = 5_400L,
                discipline = Discipline.CARDIO,
                status = SessionStatus.COMPLETED,
                deletedAt = Instant.parse("2026-11-17T09:00:00Z"),
            ),
        )

        val result = ProgressAnalyticsCalculator.calculate(
            sessions = sessions,
            timeRange = ProgressTimeRange.ALL,
            clock = clock,
        )

        assertEquals(1, result.totalCompletedSessions)
        assertEquals(1, result.activeWeeklyStreak)
        assertEquals(1_800L, result.totalDurationSeconds)
        val byDiscipline = result.disciplineBreakdown.associateBy { it.discipline }
        assertEquals(1, byDiscipline.getValue(Discipline.CALISTHENICS).sessionCount)
        assertEquals(100, byDiscipline.getValue(Discipline.CALISTHENICS).percentage)
        assertEquals(0, byDiscipline.getValue(Discipline.WEIGHT_LIFTING).sessionCount)
        assertEquals(0, byDiscipline.getValue(Discipline.CARDIO).sessionCount)
        assertEquals(0, byDiscipline.getValue(Discipline.CONDITIONING).sessionCount)
    }

    @Test
    fun timeRangeFiltering_includesSessionsAtOrAfterCutoffAcrossAllRanges() {
        val sessions = listOf(
            // Exact 1W cutoff: 2026-11-16T08:00:00Z
            session(startedAt = "2026-11-23T07:00:00Z", durationSeconds = 1_200L, discipline = Discipline.CALISTHENICS),
            session(startedAt = "2026-11-16T08:00:00Z", durationSeconds = 1_800L, discipline = Discipline.CONDITIONING),
            // Just outside 1W, inside 1M (cutoff 2026-10-23T08:00:00Z)
            session(startedAt = "2026-11-16T07:59:59Z", durationSeconds = 2_400L, discipline = Discipline.CARDIO),
            // Outside 1M, inside 3M (cutoff 2026-08-23T08:00:00Z)
            session(startedAt = "2026-10-01T10:00:00Z", durationSeconds = 3_000L, discipline = Discipline.WEIGHT_LIFTING),
            // Outside 3M, inside 1Y (cutoff 2025-11-23T08:00:00Z)
            session(startedAt = "2026-05-01T10:00:00Z", durationSeconds = 3_600L, discipline = Discipline.CALISTHENICS),
            // Outside 1Y, inside ALL
            session(startedAt = "2025-11-23T07:59:59Z", durationSeconds = 4_200L, discipline = Discipline.CONDITIONING),
        )

        val oneWeek = ProgressAnalyticsCalculator.calculate(sessions, ProgressTimeRange.ONE_WEEK, clock)
        assertEquals(2, oneWeek.totalCompletedSessions)
        assertEquals(3_000L, oneWeek.totalDurationSeconds)

        val oneMonth = ProgressAnalyticsCalculator.calculate(sessions, ProgressTimeRange.ONE_MONTH, clock)
        assertEquals(3, oneMonth.totalCompletedSessions)
        assertEquals(5_400L, oneMonth.totalDurationSeconds)

        val threeMonths = ProgressAnalyticsCalculator.calculate(sessions, ProgressTimeRange.THREE_MONTHS, clock)
        assertEquals(4, threeMonths.totalCompletedSessions)
        assertEquals(8_400L, threeMonths.totalDurationSeconds)

        val oneYear = ProgressAnalyticsCalculator.calculate(sessions, ProgressTimeRange.ONE_YEAR, clock)
        assertEquals(5, oneYear.totalCompletedSessions)
        assertEquals(12_000L, oneYear.totalDurationSeconds)

        val all = ProgressAnalyticsCalculator.calculate(sessions, ProgressTimeRange.ALL, clock)
        assertEquals(6, all.totalCompletedSessions)
        assertEquals(16_200L, all.totalDurationSeconds)
    }

    @Test
    fun activeWeeklyStreak_handlesMondaySundayBoundariesMultipleSessionsAndGaps() {
        // Clock is Monday 2026-11-23T08:00:00Z.
        // Previous week (Mon Nov 16 - Sun Nov 22) has a Sunday night session -> streak = 1 even with no session today yet.
        val previousWeekOnly = listOf(
            session(startedAt = "2026-11-22T22:00:00Z", durationSeconds = 1_800L),
        )
        assertEquals(
            1,
            ProgressAnalyticsCalculator.calculateActiveWeeklyStreak(previousWeekOnly, clock),
        )

        // Adding a session in current week (Mon Nov 23) + multiple sessions in week of Nov 16 + Sunday Nov 15 (week of Nov 9)
        // counts 3 consecutive Monday-Sunday weeks (Nov 23, Nov 16, Nov 9).
        val threeWeeks = listOf(
            session(startedAt = "2026-11-23T07:00:00Z", durationSeconds = 1_800L), // Week of Nov 23
            session(startedAt = "2026-11-22T22:00:00Z", durationSeconds = 1_800L), // Week of Nov 16 (Sun)
            session(startedAt = "2026-11-16T00:05:00Z", durationSeconds = 1_800L), // Week of Nov 16 (Mon)
            session(startedAt = "2026-11-15T23:55:00Z", durationSeconds = 1_800L), // Week of Nov 9 (Sun)
        )
        assertEquals(
            3,
            ProgressAnalyticsCalculator.calculateActiveWeeklyStreak(threeWeeks, clock),
        )

        // Skipping week of Nov 9 breaks before Nov 2
        val withGap = threeWeeks + listOf(
            session(startedAt = "2026-11-01T12:00:00Z", durationSeconds = 1_800L), // Week of Oct 26 (gap at Nov 2 & Nov 9? Wait: Nov 9 is present, Nov 2 is missing)
        )
        assertEquals(
            3,
            ProgressAnalyticsCalculator.calculateActiveWeeklyStreak(withGap, clock),
        )

        // Neither current week (Nov 23) nor previous week (Nov 16) active -> streak is 0
        val staleStreak = listOf(
            session(startedAt = "2026-11-15T23:59:00Z", durationSeconds = 1_800L),
            session(startedAt = "2026-11-08T12:00:00Z", durationSeconds = 1_800L),
        )
        assertEquals(
            0,
            ProgressAnalyticsCalculator.calculateActiveWeeklyStreak(staleStreak, clock),
        )
    }

    @Test
    fun disciplineBreakdown_computesCountsFractionsAndPercentagesInCanonicalOrder() {
        val sessions = listOf(
            session(startedAt = "2026-11-23T07:00:00Z", durationSeconds = 1_800L, discipline = Discipline.CALISTHENICS),
            session(startedAt = "2026-11-22T07:00:00Z", durationSeconds = 1_800L, discipline = Discipline.CALISTHENICS),
            session(startedAt = "2026-11-21T07:00:00Z", durationSeconds = 1_800L, discipline = Discipline.CALISTHENICS),
            session(startedAt = "2026-11-20T07:00:00Z", durationSeconds = 1_800L, discipline = Discipline.CONDITIONING),
            session(startedAt = "2026-11-19T07:00:00Z", durationSeconds = 1_800L, discipline = Discipline.CARDIO),
            session(startedAt = "2026-11-18T07:00:00Z", durationSeconds = 1_800L, discipline = Discipline.WEIGHT_LIFTING),
        )

        val breakdown = ProgressAnalyticsCalculator.calculateDisciplineBreakdown(sessions)
        assertEquals(
            listOf(
                Discipline.CALISTHENICS,
                Discipline.CONDITIONING,
                Discipline.CARDIO,
                Discipline.WEIGHT_LIFTING,
            ),
            breakdown.map { it.discipline },
        )

        assertEquals(3, breakdown[0].sessionCount)
        assertEquals(0.5f, breakdown[0].fraction, 0.001f)
        assertEquals(50, breakdown[0].percentage)

        assertEquals(1, breakdown[1].sessionCount)
        assertEquals(1f / 6f, breakdown[1].fraction, 0.001f)
        assertEquals(17, breakdown[1].percentage)

        assertEquals(1, breakdown[2].sessionCount)
        assertEquals(17, breakdown[2].percentage)

        assertEquals(1, breakdown[3].sessionCount)
        assertEquals(17, breakdown[3].percentage)
    }

    private fun session(
        startedAt: String,
        durationSeconds: Long,
        discipline: Discipline = Discipline.CALISTHENICS,
        status: SessionStatus = SessionStatus.COMPLETED,
        deletedAt: Instant? = null,
    ): Session {
        val start = Instant.parse(startedAt)
        val completed = if (status == SessionStatus.COMPLETED) start.plusSeconds(durationSeconds) else null
        return Session(
            id = UUID.randomUUID().toString(),
            routineId = null,
            title = "${discipline.displayName} Session",
            discipline = discipline,
            status = status,
            startedAt = start,
            completedAt = completed,
            durationSeconds = durationSeconds,
            notes = null,
            exercises = emptyList(),
            createdAt = start,
            updatedAt = start,
            deletedAt = deletedAt,
            syncStatus = SyncStatus.PENDING,
        )
    }
}
