package com.e4eheh.e4ehehworkout.domain.analytics

import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/** Selectable time-range windows on the Progress tab. */
enum class ProgressTimeRange(
    val label: String,
    val telemetrySubtitle: String,
) {
    ONE_WEEK(label = "1W", telemetrySubtitle = "1W WINDOW"),
    ONE_MONTH(label = "1M", telemetrySubtitle = "1M WINDOW"),
    THREE_MONTHS(label = "3M", telemetrySubtitle = "3M WINDOW"),
    ONE_YEAR(label = "1Y", telemetrySubtitle = "1Y WINDOW"),
    ALL(label = "ALL", telemetrySubtitle = "ALL TIME"),
}

/** Per-Discipline session count and share within a selected ProgressTimeRange. */
data class DisciplineBreakdownItem(
    val discipline: Discipline,
    val sessionCount: Int,
    val fraction: Float,
    val percentage: Int,
)

/** Computed Consistency and Summary Stats metrics for a selected ProgressTimeRange. */
data class ProgressAnalytics(
    val timeRange: ProgressTimeRange,
    val totalCompletedSessions: Int,
    val activeWeeklyStreak: Int,
    val totalDurationSeconds: Long,
    val disciplineBreakdown: List<DisciplineBreakdownItem>,
)

/** Pure-Kotlin analytics calculator for Progress Consistency, Discipline Breakdown, and Summary Stats. */
object ProgressAnalyticsCalculator {
    fun empty(timeRange: ProgressTimeRange): ProgressAnalytics = ProgressAnalytics(
        timeRange = timeRange,
        totalCompletedSessions = 0,
        activeWeeklyStreak = 0,
        totalDurationSeconds = 0L,
        disciplineBreakdown = calculateDisciplineBreakdown(emptyList()),
    )

    fun filterEligibleSessions(sessions: List<Session>): List<Session> =
        sessions.filter { it.status == SessionStatus.COMPLETED && it.deletedAt == null }

    fun cutoffInstant(timeRange: ProgressTimeRange, clock: Clock): Instant? {
        val nowZoned = ZonedDateTime.ofInstant(clock.instant(), clock.zone)
        return when (timeRange) {
            ProgressTimeRange.ONE_WEEK -> nowZoned.minusWeeks(1).toInstant()
            ProgressTimeRange.ONE_MONTH -> nowZoned.minusMonths(1).toInstant()
            ProgressTimeRange.THREE_MONTHS -> nowZoned.minusMonths(3).toInstant()
            ProgressTimeRange.ONE_YEAR -> nowZoned.minusYears(1).toInstant()
            ProgressTimeRange.ALL -> null
        }
    }

    fun filterByTimeRange(
        sessions: List<Session>,
        timeRange: ProgressTimeRange,
        clock: Clock,
    ): List<Session> {
        val cutoff = cutoffInstant(timeRange, clock)
        return filterEligibleSessions(sessions).filter { session ->
            cutoff == null || !session.startedAt.isBefore(cutoff)
        }
    }

    fun calculateActiveWeeklyStreak(
        sessions: List<Session>,
        clock: Clock,
    ): Int = calculateWeeklyStreakFromEligible(filterEligibleSessions(sessions), clock)

    private fun calculateWeeklyStreakFromEligible(
        eligibleSessions: List<Session>,
        clock: Clock,
    ): Int {
        val zone = clock.zone
        val currentWeekMonday = clock.instant()
            .atZone(zone)
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val previousWeekMonday = currentWeekMonday.minusWeeks(1)

        val activeWeeks: Set<LocalDate> = eligibleSessions
            .map { session ->
                session.startedAt
                    .atZone(zone)
                    .toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            }
            .filter { !it.isAfter(currentWeekMonday) }
            .toSet()

        var cursor = when {
            currentWeekMonday in activeWeeks -> currentWeekMonday
            previousWeekMonday in activeWeeks -> previousWeekMonday
            else -> return 0
        }

        var streak = 0
        while (cursor in activeWeeks) {
            streak++
            cursor = cursor.minusWeeks(1)
        }
        return streak
    }

    fun calculateDisciplineBreakdown(sessions: List<Session>): List<DisciplineBreakdownItem> =
        calculateDisciplineBreakdownFromEligible(filterEligibleSessions(sessions))

    private fun calculateDisciplineBreakdownFromEligible(
        eligibleSessions: List<Session>,
    ): List<DisciplineBreakdownItem> {
        val total = eligibleSessions.size
        val countsByDiscipline = eligibleSessions.groupingBy { it.discipline }.eachCount()
        return Discipline.entries.map { discipline ->
            val count = countsByDiscipline[discipline] ?: 0
            val fraction = if (total > 0) count.toFloat() / total.toFloat() else 0f
            val percentage = if (total > 0) ((count * 100.0) / total).roundToInt() else 0
            DisciplineBreakdownItem(
                discipline = discipline,
                sessionCount = count,
                fraction = fraction,
                percentage = percentage,
            )
        }
    }

    fun calculate(
        sessions: List<Session>,
        timeRange: ProgressTimeRange,
        clock: Clock,
    ): ProgressAnalytics {
        val rangeSessions = filterByTimeRange(sessions, timeRange, clock)
        return ProgressAnalytics(
            timeRange = timeRange,
            totalCompletedSessions = rangeSessions.size,
            activeWeeklyStreak = calculateWeeklyStreakFromEligible(rangeSessions, clock),
            totalDurationSeconds = rangeSessions.sumOf { it.durationSeconds.coerceAtLeast(0L) },
            disciplineBreakdown = calculateDisciplineBreakdownFromEligible(rangeSessions),
        )
    }
}

private val SAME_YEAR_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val CROSS_YEAR_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d ''yy", Locale.US)

/** Formats chart axis/inspection dates consistently across Progress telemetry charts. */
internal fun formatChartDateLabel(
    timestamp: Instant,
    clock: Clock,
): String {
    val currentYear = clock.instant().atZone(clock.zone).year
    val zoned = timestamp.atZone(clock.zone)
    val formatter = if (zoned.year == currentYear) {
        SAME_YEAR_DATE_FORMATTER
    } else {
        CROSS_YEAR_DATE_FORMATTER
    }
    return zoned.format(formatter)
}
