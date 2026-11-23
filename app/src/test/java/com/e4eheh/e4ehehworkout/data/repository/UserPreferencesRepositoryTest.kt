package com.e4eheh.e4ehehworkout.data.repository

import com.e4eheh.e4ehehworkout.data.local.InMemoryUserPreferencesDao
import com.e4eheh.e4ehehworkout.domain.model.DistanceUnit
import com.e4eheh.e4ehehworkout.domain.model.UserPreferences
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesRepositoryTest {
    private val initialInstant = Instant.parse("2026-11-23T08:00:00Z")
    private val clock = FakeClock(initialInstant)
    private val dao = InMemoryUserPreferencesDao()
    private val repository = DefaultUserPreferencesRepository(dao, clock)

    @Test
    fun freshStorage_emitsAndReturnsDefaultPreferences() = runTest {
        val expected = UserPreferences(
            displayName = "OPERATOR",
            weightUnit = WeightUnit.KG,
            distanceUnit = DistanceUnit.KM,
            defaultRestTimerSeconds = 90,
        )

        assertEquals(expected, repository.getPreferences())
        assertEquals(expected, repository.observePreferences().first())
    }

    @Test
    fun updateDisplayName_trimsWhitespace_persists_andPreservesOtherPreferences() = runTest {
        repository.updateWeightUnit(WeightUnit.LB)

        val updated = repository.updateDisplayName("  Nightwing  ")

        assertEquals(
            UserPreferences(
                displayName = "Nightwing",
                weightUnit = WeightUnit.LB,
                distanceUnit = DistanceUnit.KM,
                defaultRestTimerSeconds = 90,
            ),
            updated,
        )
        assertEquals(updated, repository.observePreferences().first())
        assertEquals(updated, repository.getPreferences())
    }

    @Test(expected = IllegalArgumentException::class)
    fun updateDisplayName_rejectsBlankInput() = runTest {
        repository.updateDisplayName("   \t  ")
    }

    @Test
    fun updateWeightAndDistanceUnits_updateIndependentlyWithoutClobberingOtherFields() = runTest {
        repository.updateDisplayName("Oracle")
        repository.updateDefaultRestTimerSeconds(120)

        val afterWeight = repository.updateWeightUnit(WeightUnit.LB)
        assertEquals(
            UserPreferences(
                displayName = "Oracle",
                weightUnit = WeightUnit.LB,
                distanceUnit = DistanceUnit.KM,
                defaultRestTimerSeconds = 120,
            ),
            afterWeight,
        )

        val afterDistance = repository.updateDistanceUnit(DistanceUnit.MI)
        assertEquals(
            UserPreferences(
                displayName = "Oracle",
                weightUnit = WeightUnit.LB,
                distanceUnit = DistanceUnit.MI,
                defaultRestTimerSeconds = 120,
            ),
            afterDistance,
        )
        assertEquals(afterDistance, repository.observePreferences().first())
    }

    @Test
    fun updateDefaultRestTimerSeconds_acceptsMinAndMaxBounds() = runTest {
        val minPrefs = repository.updateDefaultRestTimerSeconds(UserPreferences.MIN_REST_TIMER_SECONDS)
        assertEquals(15, minPrefs.defaultRestTimerSeconds)

        val maxPrefs = repository.updateDefaultRestTimerSeconds(UserPreferences.MAX_REST_TIMER_SECONDS)
        assertEquals(600, maxPrefs.defaultRestTimerSeconds)
        assertEquals(maxPrefs, repository.getPreferences())
    }

    @Test(expected = IllegalArgumentException::class)
    fun updateDefaultRestTimerSeconds_rejectsZero() = runTest {
        repository.updateDefaultRestTimerSeconds(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun updateDefaultRestTimerSeconds_rejectsNegativeValue() = runTest {
        repository.updateDefaultRestTimerSeconds(-15)
    }

    @Test(expected = IllegalArgumentException::class)
    fun updateDefaultRestTimerSeconds_rejectsBelowMinimum() = runTest {
        repository.updateDefaultRestTimerSeconds(14)
    }

    @Test(expected = IllegalArgumentException::class)
    fun updateDefaultRestTimerSeconds_rejectsAboveMaximum() = runTest {
        repository.updateDefaultRestTimerSeconds(601)
    }

    @Test
    fun updates_truncateUpdatedAtToMillisecondPrecision() = runTest {
        clock.setInstant(Instant.parse("2026-11-23T08:00:00.987654321Z"))

        repository.updateDisplayName("Batman")

        assertEquals(
            Instant.parse("2026-11-23T08:00:00.987Z").toEpochMilli(),
            dao.storedEntity?.updatedAt,
        )
    }

    @Test
    fun preferencesPersistAcrossNewRepositoryInstanceOverSameStorage() = runTest {
        repository.updateDisplayName("Alfred")
        repository.updateWeightUnit(WeightUnit.LB)
        repository.updateDistanceUnit(DistanceUnit.MI)
        repository.updateDefaultRestTimerSeconds(150)

        val reloadedRepository = DefaultUserPreferencesRepository(dao, clock)
        val reloaded = reloadedRepository.getPreferences()

        assertEquals(
            UserPreferences(
                displayName = "Alfred",
                weightUnit = WeightUnit.LB,
                distanceUnit = DistanceUnit.MI,
                defaultRestTimerSeconds = 150,
            ),
            reloaded,
        )
    }
}
