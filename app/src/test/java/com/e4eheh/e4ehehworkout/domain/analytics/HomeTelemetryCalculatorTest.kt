package com.e4eheh.e4ehehworkout.domain.analytics

import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.Routine
import com.e4eheh.e4ehehworkout.domain.model.RoutineExercise
import com.e4eheh.e4ehehworkout.domain.model.Session
import com.e4eheh.e4ehehworkout.domain.model.SessionStatus
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTelemetryCalculatorTest {
    @Test
    fun buildWeeklyCalendar_anchorsMondayToSundayWindow_acrossMondayWednesdayAndSundayClocks() {
        val clocks = listOf(
            FakeClock(Instant.parse("2026-11-23T08:00:00Z")) to LocalDate.of(2026, 11, 23),
            FakeClock(Instant.parse("2026-11-25T15:30:00Z")) to LocalDate.of(2026, 11, 25),
            FakeClock(Instant.parse("2026-11-29T23:45:00Z")) to LocalDate.of(2026, 11, 29),
        )

        clocks.forEach { (clock, expectedToday) ->
            val window = HomeTelemetryCalculator.buildWeeklyCalendar(
                sessions = emptyList(),
                clock = clock,
            )

            assertEquals(LocalDate.of(2026, 11, 23), window.weekStart)
            assertEquals(LocalDate.of(2026, 11, 29), window.weekEnd)
            assertEquals(expectedToday, window.today)
            assertEquals(7, window.days.size)
            assertEquals(
                listOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                    DayOfWeek.SATURDAY,
                    DayOfWeek.SUNDAY,
                ),
                window.days.map { it.dayOfWeek },
            )
            assertEquals(0, window.activeDayCount)
            assertEquals(1, window.days.count { it.isToday })
            assertTrue(window.days.single { it.date == expectedToday }.isToday)
        }
    }

    @Test
    fun buildWeeklyCalendar_marksOnlyCompletedActiveSessionsInCurrentWeek_groupedNewestFirst() {
        val clock = FakeClock(Instant.parse("2026-11-25T12:00:00Z")) // Wednesday
        val monMorning = session(
            title = "Monday Morning",
            startedAt = "2026-11-23T06:30:00Z",
        )
        val wedEarly = session(
            title = "Wednesday Early",
            startedAt = "2026-11-25T07:00:00Z",
        )
        val wedEvening = session(
            title = "Wednesday Evening",
            startedAt = "2026-11-25T19:00:00Z",
        )
        val sunNight = session(
            title = "Sunday Night",
            startedAt = "2026-11-29T22:00:00Z",
        )
        val prevSunday = session(
            title = "Previous Week Sunday",
            startedAt = "2026-11-22T23:59:59Z",
        )
        val nextMonday = session(
            title = "Next Week Monday",
            startedAt = "2026-11-30T00:00:00Z",
        )
        val tuesdayDraft = session(
            title = "Tuesday Draft",
            startedAt = "2026-11-24T10:00:00Z",
            status = SessionStatus.IN_PROGRESS,
        )
        val thursdayDeleted = session(
            title = "Thursday Deleted",
            startedAt = "2026-11-26T10:00:00Z",
            deletedAt = Instant.parse("2026-11-26T12:00:00Z"),
        )

        val window = HomeTelemetryCalculator.buildWeeklyCalendar(
            sessions = listOf(
                monMorning,
                wedEarly,
                wedEvening,
                sunNight,
                prevSunday,
                nextMonday,
                tuesdayDraft,
                thursdayDeleted,
            ),
            clock = clock,
        )

        assertEquals(3, window.activeDayCount)
        val byDate = window.days.associateBy { it.date }
        assertTrue(byDate.getValue(LocalDate.of(2026, 11, 23)).hasCompletedSessions)
        assertEquals(listOf(monMorning.id), byDate.getValue(LocalDate.of(2026, 11, 23)).completedSessions.map { it.id })

        assertFalse(byDate.getValue(LocalDate.of(2026, 11, 24)).hasCompletedSessions)
        assertTrue(byDate.getValue(LocalDate.of(2026, 11, 24)).completedSessions.isEmpty())

        assertTrue(byDate.getValue(LocalDate.of(2026, 11, 25)).hasCompletedSessions)
        assertEquals(
            listOf(wedEvening.id, wedEarly.id),
            byDate.getValue(LocalDate.of(2026, 11, 25)).completedSessions.map { it.id },
        )

        assertFalse(byDate.getValue(LocalDate.of(2026, 11, 26)).hasCompletedSessions)
        assertTrue(byDate.getValue(LocalDate.of(2026, 11, 29)).hasCompletedSessions)
        assertEquals(listOf(sunNight.id), byDate.getValue(LocalDate.of(2026, 11, 29)).completedSessions.map { it.id })
    }

    @Test
    fun buildWeeklyCalendar_respectsClockTimeZoneForDayBoundaries() {
        // 2026-11-24T02:00:00Z is still Monday 2026-11-23 21:00 in America/New_York (UTC-5)
        val nyZone = ZoneId.of("America/New_York")
        val clock = FakeClock(
            initialInstant = Instant.parse("2026-11-24T02:00:00Z"),
            zoneId = nyZone,
        )
        val lateSundayNy = session(
            title = "Late Sunday NY",
            // 2026-11-23T03:00:00Z is 2026-11-22 22:00 in NY (previous week)
            startedAt = "2026-11-23T03:00:00Z",
        )
        val mondayEveningNy = session(
            title = "Monday Evening NY",
            // 2026-11-24T01:30:00Z is 2026-11-23 20:30 in NY (Monday of current week)
            startedAt = "2026-11-24T01:30:00Z",
        )

        val window = HomeTelemetryCalculator.buildWeeklyCalendar(
            sessions = listOf(lateSundayNy, mondayEveningNy),
            clock = clock,
        )

        assertEquals(LocalDate.of(2026, 11, 23), window.weekStart)
        assertEquals(LocalDate.of(2026, 11, 23), window.today)
        assertEquals(1, window.activeDayCount)
        assertEquals(
            listOf(mondayEveningNy.id),
            window.days.first().completedSessions.map { it.id },
        )
    }

    @Test
    fun suggestNextRoutine_returnsNullWhenNoActiveRoutinesExist() {
        val deletedRoutine = routine(
            name = "Deleted",
            createdAt = "2026-11-01T08:00:00Z",
            deletedAt = Instant.parse("2026-11-02T08:00:00Z"),
        )

        assertNull(HomeTelemetryCalculator.suggestNextRoutine(emptyList(), emptyList()))
        assertNull(HomeTelemetryCalculator.suggestNextRoutine(listOf(deletedRoutine), emptyList()))
    }

    @Test
    fun suggestNextRoutine_rotatesThroughRoutinesLeastRecentlyCompleted_andIgnoresDraftsBlankAndDeletedSessions() {
        val push = routine(name = "Push", createdAt = "2026-11-01T08:00:00Z")
        val pull = routine(name = "Pull", createdAt = "2026-11-01T09:00:00Z")
        val legs = routine(name = "Legs", createdAt = "2026-11-01T10:00:00Z")
        val conditioning = routine(
            name = "Conditioning",
            discipline = Discipline.CONDITIONING,
            createdAt = "2026-11-01T11:00:00Z",
        )
        val routines = listOf(conditioning, legs, pull, push)

        // Blank-slate history picks earliest-created routine
        assertEquals(push.id, HomeTelemetryCalculator.suggestNextRoutine(routines, emptyList())?.id)

        // Blank sessions, IN_PROGRESS drafts, and soft-deleted completed sessions do not advance rotation
        val ignoredSessions = listOf(
            session(startedAt = "2026-11-20T08:00:00Z", routineId = null),
            session(startedAt = "2026-11-21T08:00:00Z", routineId = push.id, status = SessionStatus.IN_PROGRESS),
            session(
                startedAt = "2026-11-22T08:00:00Z",
                routineId = push.id,
                deletedAt = Instant.parse("2026-11-22T09:00:00Z"),
            ),
        )
        assertEquals(push.id, HomeTelemetryCalculator.suggestNextRoutine(routines, ignoredSessions)?.id)

        // Complete Push -> Pull
        val history = mutableListOf(
            session(startedAt = "2026-11-20T08:00:00Z", routineId = push.id),
        )
        assertEquals(pull.id, HomeTelemetryCalculator.suggestNextRoutine(routines, history)?.id)

        // Complete Pull -> Legs
        history += session(startedAt = "2026-11-21T08:00:00Z", routineId = pull.id)
        assertEquals(legs.id, HomeTelemetryCalculator.suggestNextRoutine(routines, history)?.id)

        // Skip Legs and do Conditioning instead -> still suggests overdue Legs
        history += session(startedAt = "2026-11-22T08:00:00Z", routineId = conditioning.id)
        assertEquals(legs.id, HomeTelemetryCalculator.suggestNextRoutine(routines, history)?.id)

        // Complete Legs -> cycles back to Push (oldest completion)
        history += session(startedAt = "2026-11-23T08:00:00Z", routineId = legs.id)
        assertEquals(push.id, HomeTelemetryCalculator.suggestNextRoutine(routines, history)?.id)

        // Adding a brand-new unperformed routine surfaces it next
        val cardio = routine(name = "Tempo Run", discipline = Discipline.CARDIO, createdAt = "2026-11-24T08:00:00Z")
        assertEquals(cardio.id, HomeTelemetryCalculator.suggestNextRoutine(routines + cardio, history)?.id)

        // orderRoutinesForPicker puts suggested routine first while keeping remaining input order
        val ordered = HomeTelemetryCalculator.orderRoutinesForPicker(routines, push.id)
        assertEquals(listOf(push.id, conditioning.id, legs.id, pull.id), ordered.map { it.id })
    }

    @Test
    fun resolveRoutineDuration_prefersLatestPositiveCompletedSession_andFallsBackToEstimatedSets() {
        val routineWithTargets = routine(
            name = "Calisthenics Push",
            createdAt = "2026-11-01T08:00:00Z",
            targetSets = listOf(4, null), // 4 + default 3 = 7 sets * 180s = 1260s
        )
        val emptyRoutine = routine(
            name = "Empty Routine",
            createdAt = "2026-11-01T09:00:00Z",
            targetSets = emptyList(),
        )

        val estimated = HomeTelemetryCalculator.resolveRoutineDuration(routineWithTargets, emptyList())
        assertEquals(RoutineDurationSource.ESTIMATED, estimated.source)
        assertEquals(1_260L, estimated.durationSeconds)

        val zeroEstimate = HomeTelemetryCalculator.resolveRoutineDuration(emptyRoutine, emptyList())
        assertEquals(RoutineDurationSource.ESTIMATED, zeroEstimate.source)
        assertEquals(0L, zeroEstimate.durationSeconds)

        val sessions = listOf(
            // Older completed session for this routine
            session(
                startedAt = "2026-11-18T08:00:00Z",
                durationSeconds = 1_500L,
                routineId = routineWithTargets.id,
            ),
            // Newer completed session with positive duration -> wins
            session(
                startedAt = "2026-11-21T08:00:00Z",
                durationSeconds = 2_400L,
                routineId = routineWithTargets.id,
            ),
            // Ignored: zero duration, IN_PROGRESS, and soft-deleted
            session(
                startedAt = "2026-11-22T08:00:00Z",
                durationSeconds = 0L,
                routineId = routineWithTargets.id,
            ),
            session(
                startedAt = "2026-11-23T08:00:00Z",
                durationSeconds = 3_600L,
                routineId = routineWithTargets.id,
                status = SessionStatus.IN_PROGRESS,
            ),
            session(
                startedAt = "2026-11-24T08:00:00Z",
                durationSeconds = 4_200L,
                routineId = routineWithTargets.id,
                deletedAt = Instant.parse("2026-11-24T09:00:00Z"),
            ),
        )

        val fromLast = HomeTelemetryCalculator.resolveRoutineDuration(routineWithTargets, sessions)
        assertEquals(RoutineDurationSource.LAST_SESSION, fromLast.source)
        assertEquals(2_400L, fromLast.durationSeconds)
    }

    private fun routine(
        name: String,
        discipline: Discipline = Discipline.CALISTHENICS,
        createdAt: String,
        targetSets: List<Int?> = listOf(3),
        deletedAt: Instant? = null,
    ): Routine {
        val routineId = UUID.randomUUID().toString()
        val created = Instant.parse(createdAt)
        val exercises = targetSets.mapIndexed { index, sets ->
            RoutineExercise(
                id = UUID.randomUUID().toString(),
                routineId = routineId,
                exerciseId = "ex-$index",
                sortOrder = index,
                defaultTargetSets = sets,
                createdAt = created,
                updatedAt = created,
                deletedAt = null,
                syncStatus = SyncStatus.PENDING,
            )
        }
        return Routine(
            id = routineId,
            name = name,
            discipline = discipline,
            exercises = exercises,
            createdAt = created,
            updatedAt = created,
            deletedAt = deletedAt,
            syncStatus = SyncStatus.PENDING,
        )
    }

    private fun session(
        title: String = "Session",
        startedAt: String,
        durationSeconds: Long = 1_800L,
        routineId: String? = null,
        discipline: Discipline = Discipline.CALISTHENICS,
        status: SessionStatus = SessionStatus.COMPLETED,
        deletedAt: Instant? = null,
        completedAt: Instant? = null,
    ): Session {
        val start = Instant.parse(startedAt)
        val resolvedCompleted = completedAt ?: if (status == SessionStatus.COMPLETED) {
            start.plusSeconds(durationSeconds)
        } else {
            null
        }
        return Session(
            id = UUID.randomUUID().toString(),
            routineId = routineId,
            title = title,
            discipline = discipline,
            status = status,
            startedAt = start,
            completedAt = resolvedCompleted,
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
