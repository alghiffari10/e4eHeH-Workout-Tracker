package com.e4eheh.e4ehehworkout.domain.analytics

import com.e4eheh.e4ehehworkout.domain.model.Routine
import com.e4eheh.e4ehehworkout.domain.model.Session
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Single day cell inside the Monday–Sunday weekly calendar strip on Home. */
data class WeeklyCalendarDay(
    val date: LocalDate,
    val dayOfWeek: DayOfWeek,
    val isToday: Boolean,
    val completedSessions: List<Session>,
) {
    val hasCompletedSessions: Boolean
        get() = completedSessions.isNotEmpty()
}

/** Monday–Sunday weekly calendar window anchored to the athlete's current Clock date. */
data class WeeklyCalendarWindow(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val today: LocalDate,
    val days: List<WeeklyCalendarDay>,
) {
    val activeDayCount: Int
        get() = days.count { it.hasCompletedSessions }
}

/** Provenance of a Routine card's displayed duration on Home. */
enum class RoutineDurationSource {
    LAST_SESSION,
    ESTIMATED,
}

/** Resolved duration telemetry for a Routine card on Home. */
data class RoutineDurationInfo(
    val durationSeconds: Long,
    val source: RoutineDurationSource,
)

/** Pure-Kotlin telemetry calculators powering the Home command center. */
object HomeTelemetryCalculator {
    const val DEFAULT_ESTIMATED_SETS_PER_EXERCISE = 3
    const val ESTIMATED_SECONDS_PER_SET = 180L

    fun buildWeeklyCalendar(
        sessions: List<Session>,
        clock: Clock,
    ): WeeklyCalendarWindow {
        val zone = clock.zone
        val today = clock.instant().atZone(zone).toLocalDate()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6L)

        val sessionsByDate = ProgressAnalyticsCalculator.filterEligibleSessions(sessions)
            .sortedWith(compareByDescending<Session> { it.startedAt }.thenByDescending { it.createdAt })
            .groupBy { session -> session.startedAt.atZone(zone).toLocalDate() }

        val days = (0L..6L).map { offset ->
            val date = weekStart.plusDays(offset)
            WeeklyCalendarDay(
                date = date,
                dayOfWeek = date.dayOfWeek,
                isToday = date == today,
                completedSessions = sessionsByDate[date].orEmpty(),
            )
        }

        return WeeklyCalendarWindow(
            weekStart = weekStart,
            weekEnd = weekEnd,
            today = today,
            days = days,
        )
    }

    fun suggestNextRoutine(
        routines: List<Routine>,
        sessions: List<Session>,
    ): Routine? {
        val eligibleRoutines = routines.filter { it.deletedAt == null }
        if (eligibleRoutines.isEmpty()) return null

        val lastCompletedByRoutineId = HashMap<String, Instant>()
        ProgressAnalyticsCalculator.filterEligibleSessions(sessions).forEach { session ->
            val routineId = session.routineId ?: return@forEach
            val completedInstant = session.completedAt ?: session.startedAt
            val previous = lastCompletedByRoutineId[routineId]
            if (previous == null || completedInstant.isAfter(previous)) {
                lastCompletedByRoutineId[routineId] = completedInstant
            }
        }

        return eligibleRoutines.minWith(
            compareBy<Routine>(
                { lastCompletedByRoutineId[it.id] != null },
                { lastCompletedByRoutineId[it.id] ?: Instant.EPOCH },
                { it.createdAt },
                { it.name.lowercase(Locale.ROOT) },
                { it.id },
            ),
        )
    }

    fun orderRoutinesForPicker(
        routines: List<Routine>,
        suggestedRoutineId: String?,
    ): List<Routine> {
        if (suggestedRoutineId == null) return routines
        val suggested = routines.firstOrNull { it.id == suggestedRoutineId } ?: return routines
        return listOf(suggested) + routines.filterNot { it.id == suggestedRoutineId }
    }

    fun resolveRoutineDuration(
        routine: Routine,
        sessions: List<Session>,
    ): RoutineDurationInfo {
        val lastSession = ProgressAnalyticsCalculator.filterEligibleSessions(sessions)
            .filter { it.routineId == routine.id && it.durationSeconds > 0L }
            .maxWithOrNull(compareBy<Session> { it.startedAt }.thenBy { it.createdAt })

        if (lastSession != null) {
            return RoutineDurationInfo(
                durationSeconds = lastSession.durationSeconds,
                source = RoutineDurationSource.LAST_SESSION,
            )
        }

        val totalSets = routine.exercises
            .filter { it.deletedAt == null }
            .sumOf { (it.defaultTargetSets ?: DEFAULT_ESTIMATED_SETS_PER_EXERCISE).coerceAtLeast(1) }

        return RoutineDurationInfo(
            durationSeconds = totalSets * ESTIMATED_SECONDS_PER_SET,
            source = RoutineDurationSource.ESTIMATED,
        )
    }
}
