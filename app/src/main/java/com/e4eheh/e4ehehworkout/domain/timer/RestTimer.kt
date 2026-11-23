package com.e4eheh.e4ehehworkout.domain.timer

import java.time.Clock
import java.time.Duration
import java.time.Instant

/** Lifecycle status of the in-Session Rest Timer countdown. */
enum class RestTimerStatus {
    IDLE,
    RUNNING,
    COMPLETED,
}

/**
 * Immutable snapshot of the Batcomputer Rest Timer countdown.
 *
 * [completionEventId] increments monotonically whenever the timer transitions to `00:00`
 * so the UI can emit a single subtle haptic buzz per completion without re-firing on recomposition.
 */
data class RestTimerState(
    val status: RestTimerStatus = RestTimerStatus.IDLE,
    val totalDurationSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val endAt: Instant? = null,
    val completionEventId: Long = 0L,
) {
    val isVisible: Boolean
        get() = status != RestTimerStatus.IDLE

    val isRunning: Boolean
        get() = status == RestTimerStatus.RUNNING

    val isCompleted: Boolean
        get() = status == RestTimerStatus.COMPLETED

    val progressFraction: Float
        get() = if (totalDurationSeconds <= 0 || remainingSeconds <= 0) {
            0f
        } else {
            (remainingSeconds.toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
        }
}

/** Deterministic Clock-driven state transitions for the Rest Timer countdown. */
class RestTimerController(
    private val clock: Clock,
) {
    fun start(
        current: RestTimerState,
        durationSeconds: Int,
    ): RestTimerState {
        val safeDuration = durationSeconds.coerceAtLeast(1)
        return runningState(
            totalDurationSeconds = safeDuration,
            remainingSeconds = safeDuration,
            endAt = clock.instant().plusSeconds(safeDuration.toLong()),
            completionEventId = current.completionEventId,
        )
    }

    fun tick(current: RestTimerState): RestTimerState {
        if (current.status != RestTimerStatus.RUNNING) {
            return current
        }
        val targetEnd = current.endAt ?: return complete(current)
        val remainingMillis = Duration.between(clock.instant(), targetEnd).toMillis()
        if (remainingMillis <= 0L) {
            return complete(current)
        }
        return current.copy(remainingSeconds = ceilSeconds(remainingMillis))
    }

    fun adjustBy(
        current: RestTimerState,
        deltaSeconds: Int,
    ): RestTimerState {
        if (current.status == RestTimerStatus.IDLE || deltaSeconds == 0) {
            return current
        }
        val now = clock.instant()
        if (current.status == RestTimerStatus.COMPLETED) {
            return if (deltaSeconds > 0) {
                runningState(
                    totalDurationSeconds = maxOf(current.totalDurationSeconds, deltaSeconds),
                    remainingSeconds = deltaSeconds,
                    endAt = now.plusSeconds(deltaSeconds.toLong()),
                    completionEventId = current.completionEventId,
                )
            } else {
                current
            }
        }

        val currentEnd = current.endAt ?: now
        val remainingMillis = Duration.between(now, currentEnd).toMillis()
        val adjustedEnd = if (remainingMillis <= 0L && deltaSeconds > 0) {
            now.plusSeconds(deltaSeconds.toLong())
        } else {
            currentEnd.plusSeconds(deltaSeconds.toLong())
        }
        val adjustedMillis = Duration.between(now, adjustedEnd).toMillis()
        if (adjustedMillis <= 0L) {
            return complete(current)
        }
        val nextRemaining = ceilSeconds(adjustedMillis)
        return runningState(
            totalDurationSeconds = maxOf(current.totalDurationSeconds, nextRemaining),
            remainingSeconds = nextRemaining,
            endAt = adjustedEnd,
            completionEventId = current.completionEventId,
        )
    }

    fun skip(current: RestTimerState): RestTimerState = RestTimerState(
        status = RestTimerStatus.IDLE,
        totalDurationSeconds = 0,
        remainingSeconds = 0,
        endAt = null,
        completionEventId = current.completionEventId,
    )

    private fun runningState(
        totalDurationSeconds: Int,
        remainingSeconds: Int,
        endAt: Instant,
        completionEventId: Long,
    ): RestTimerState = RestTimerState(
        status = RestTimerStatus.RUNNING,
        totalDurationSeconds = totalDurationSeconds,
        remainingSeconds = remainingSeconds,
        endAt = endAt,
        completionEventId = completionEventId,
    )

    private fun ceilSeconds(millis: Long): Int = ((millis + 999L) / 1000L).toInt()

    private fun complete(current: RestTimerState): RestTimerState = RestTimerState(
        status = RestTimerStatus.COMPLETED,
        totalDurationSeconds = current.totalDurationSeconds,
        remainingSeconds = 0,
        endAt = null,
        completionEventId = current.completionEventId + 1L,
    )
}
