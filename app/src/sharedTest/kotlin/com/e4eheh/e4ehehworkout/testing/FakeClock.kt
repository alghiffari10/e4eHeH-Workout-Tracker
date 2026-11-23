package com.e4eheh.e4ehehworkout.testing

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Deterministic controllable Clock for unit and Compose UI tests. */
class FakeClock(
    initialInstant: Instant = Instant.parse("2026-11-23T08:00:00Z"),
    private val zoneId: ZoneId = ZoneOffset.UTC,
) : Clock() {
    private var currentInstant: Instant = initialInstant

    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = FakeClock(currentInstant, zone)

    override fun instant(): Instant = currentInstant

    fun advanceBy(duration: Duration) {
        currentInstant = currentInstant.plus(duration)
    }

    fun setInstant(instant: Instant) {
        currentInstant = instant
    }
}
