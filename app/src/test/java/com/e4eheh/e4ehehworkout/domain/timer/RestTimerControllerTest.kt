package com.e4eheh.e4ehehworkout.domain.timer

import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerControllerTest {
    private val clock = FakeClock()
    private val controller = RestTimerController(clock)

    @Test
    fun start_initializesRunningStateWithEndAtAndPreservesCompletionEventId() {
        val state = controller.start(RestTimerState(), durationSeconds = 90)

        assertEquals(RestTimerStatus.RUNNING, state.status)
        assertTrue(state.isVisible)
        assertTrue(state.isRunning)
        assertFalse(state.isCompleted)
        assertEquals(90, state.totalDurationSeconds)
        assertEquals(90, state.remainingSeconds)
        assertEquals(clock.instant().plusSeconds(90), state.endAt)
        assertEquals(0L, state.completionEventId)
        assertEquals(1f, state.progressFraction, 1e-6f)
    }

    @Test
    fun tick_usesCeilingSecondRounding_andTriggersSingleCompletionEventAtZero() {
        var state = controller.start(RestTimerState(), durationSeconds = 90)

        clock.advanceBy(Duration.ofMillis(500))
        state = controller.tick(state)
        assertEquals(RestTimerStatus.RUNNING, state.status)
        assertEquals(90, state.remainingSeconds)
        assertEquals(0L, state.completionEventId)

        clock.advanceBy(Duration.ofMillis(500))
        state = controller.tick(state)
        assertEquals(RestTimerStatus.RUNNING, state.status)
        assertEquals(89, state.remainingSeconds)
        assertEquals(0L, state.completionEventId)

        clock.advanceBy(Duration.ofSeconds(88).plusMillis(500))
        state = controller.tick(state)
        assertEquals(RestTimerStatus.RUNNING, state.status)
        assertEquals(1, state.remainingSeconds)
        assertEquals(0L, state.completionEventId)

        clock.advanceBy(Duration.ofMillis(500))
        state = controller.tick(state)
        assertEquals(RestTimerStatus.COMPLETED, state.status)
        assertTrue(state.isVisible)
        assertFalse(state.isRunning)
        assertTrue(state.isCompleted)
        assertEquals(0, state.remainingSeconds)
        assertNull(state.endAt)
        assertEquals(1L, state.completionEventId)
        assertEquals(0f, state.progressFraction, 1e-6f)

        // Repeated ticks while COMPLETED must not re-fire completionEventId
        clock.advanceBy(Duration.ofSeconds(10))
        val afterRepeatedTick = controller.tick(state)
        assertEquals(state, afterRepeatedTick)
        assertEquals(1L, afterRepeatedTick.completionEventId)
    }

    @Test
    fun adjustByNegative_subtractsSeconds_clampsAtZeroWithCompletionOnce_andStaysClamped() {
        var state = controller.start(RestTimerState(), durationSeconds = 20)

        state = controller.adjustBy(state, -15)
        assertEquals(RestTimerStatus.RUNNING, state.status)
        assertEquals(5, state.remainingSeconds)
        assertEquals(0L, state.completionEventId)

        // Subtracting past 00:00 clamps at 00:00 and emits completion once
        state = controller.adjustBy(state, -15)
        assertEquals(RestTimerStatus.COMPLETED, state.status)
        assertEquals(0, state.remainingSeconds)
        assertNull(state.endAt)
        assertEquals(1L, state.completionEventId)

        // Another -15 while COMPLETED stays clamped at 00:00 without re-triggering completion
        val clampedAgain = controller.adjustBy(state, -15)
        assertEquals(RestTimerStatus.COMPLETED, clampedAgain.status)
        assertEquals(0, clampedAgain.remainingSeconds)
        assertEquals(1L, clampedAgain.completionEventId)
    }

    @Test
    fun adjustByPositive_extendsRunningTimer_andResumesFromCompletedAndExpiredRunning() {
        var state = controller.start(RestTimerState(), durationSeconds = 30)

        // Adding +15 from 30s updates totalDurationSeconds so progressFraction stays <= 1f
        state = controller.adjustBy(state, 15)
        assertEquals(RestTimerStatus.RUNNING, state.status)
        assertEquals(45, state.remainingSeconds)
        assertEquals(45, state.totalDurationSeconds)
        assertTrue(state.progressFraction <= 1f)

        // Reach completion
        clock.advanceBy(Duration.ofSeconds(45))
        state = controller.tick(state)
        assertEquals(RestTimerStatus.COMPLETED, state.status)
        assertEquals(1L, state.completionEventId)

        // +15 from COMPLETED resumes running for 15s from now, then completing increments eventId to 2
        state = controller.adjustBy(state, 15)
        assertEquals(RestTimerStatus.RUNNING, state.status)
        assertEquals(15, state.remainingSeconds)
        assertEquals(clock.instant().plusSeconds(15), state.endAt)
        assertEquals(1L, state.completionEventId)

        clock.advanceBy(Duration.ofSeconds(15))
        state = controller.tick(state)
        assertEquals(RestTimerStatus.COMPLETED, state.status)
        assertEquals(0, state.remainingSeconds)
        assertEquals(2L, state.completionEventId)

        // Running timer whose endAt already passed before tick: +15 starts from now
        var expiredRunning = controller.start(state, durationSeconds = 10)
        clock.advanceBy(Duration.ofSeconds(12))
        expiredRunning = controller.adjustBy(expiredRunning, 15)
        assertEquals(RestTimerStatus.RUNNING, expiredRunning.status)
        assertEquals(15, expiredRunning.remainingSeconds)
        assertEquals(clock.instant().plusSeconds(15), expiredRunning.endAt)
    }

    @Test
    fun skip_transitionsToIdleWithoutIncrementingCompletionEventId_andStartResetsCleanly() {
        var state = controller.start(RestTimerState(), durationSeconds = 60)
        state = controller.skip(state)

        assertEquals(RestTimerStatus.IDLE, state.status)
        assertFalse(state.isVisible)
        assertEquals(0, state.remainingSeconds)
        assertNull(state.endAt)
        assertEquals(0L, state.completionEventId)

        // Complete once, then skip from COMPLETED preserves completionEventId
        state = controller.start(state, durationSeconds = 30)
        clock.advanceBy(Duration.ofSeconds(30))
        state = controller.tick(state)
        assertEquals(1L, state.completionEventId)

        state = controller.skip(state)
        assertEquals(RestTimerStatus.IDLE, state.status)
        assertFalse(state.isVisible)
        assertEquals(1L, state.completionEventId)

        // Starting again while RUNNING or COMPLETED resets cleanly while preserving completionEventId
        val restarted = controller.start(state, durationSeconds = 45)
        assertEquals(RestTimerStatus.RUNNING, restarted.status)
        assertEquals(45, restarted.remainingSeconds)
        assertEquals(1L, restarted.completionEventId)
    }
}
