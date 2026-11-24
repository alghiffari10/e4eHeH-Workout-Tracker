package com.e4eheh.e4ehehworkout.data.repository

import com.e4eheh.e4ehehworkout.data.local.BodyweightEntryEntity
import com.e4eheh.e4ehehworkout.data.local.InMemoryBodyweightEntryDao
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntryInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.WeightUnit
import com.e4eheh.e4ehehworkout.domain.unit.UnitConverter
import com.e4eheh.e4ehehworkout.testing.FakeClock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class BodyweightRepositoryTest {
    private val initialInstant = Instant.parse("2026-11-23T08:00:00Z")
    private val clock = FakeClock(initialInstant)
    private val dao = InMemoryBodyweightEntryDao()
    private val repository = DefaultBodyweightRepository(dao, clock)

    @Test
    fun create_generatesUuid_storesCanonicalKg_setsTimestampsAndPendingSync() = runTest {
        val canonicalFromLb = UnitConverter.weightToCanonicalKg(165.35, WeightUnit.LB)
        val recordedAt = Instant.parse("2026-11-22T07:30:00.987654Z")

        val created = repository.create(
            BodyweightEntryInput(
                recordedAt = recordedAt,
                weightKg = canonicalFromLb,
            ),
        )

        assertNotNull(UUID.fromString(created.id))
        assertEquals(Instant.parse("2026-11-22T07:30:00.987Z"), created.recordedAt)
        assertEquals(canonicalFromLb, created.weightKg, 1e-9)
        assertEquals(initialInstant, created.createdAt)
        assertEquals(initialInstant, created.updatedAt)
        assertNull(created.deletedAt)
        assertEquals(SyncStatus.PENDING, created.syncStatus)

        assertEquals(listOf(created), repository.observeActiveEntries().first())
        assertEquals(created, repository.getById(created.id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun create_rejectsNonPositiveWeight() = runTest {
        repository.create(
            BodyweightEntryInput(
                recordedAt = initialInstant,
                weightKg = 0.0,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun create_rejectsNonFiniteWeight() = runTest {
        repository.create(
            BodyweightEntryInput(
                recordedAt = initialInstant,
                weightKg = Double.NaN,
            ),
        )
    }

    @Test
    fun observeActiveEntries_ordersByRecordedAtDescThenCreatedAtDesc() = runTest {
        val older = repository.create(
            BodyweightEntryInput(
                recordedAt = Instant.parse("2026-11-20T07:00:00Z"),
                weightKg = 74.2,
            ),
        )
        clock.advanceBy(Duration.ofMinutes(5))
        val sameDayEarlierCreate = repository.create(
            BodyweightEntryInput(
                recordedAt = Instant.parse("2026-11-22T07:00:00Z"),
                weightKg = 73.8,
            ),
        )
        clock.advanceBy(Duration.ofMinutes(5))
        val sameDayLaterCreate = repository.create(
            BodyweightEntryInput(
                recordedAt = Instant.parse("2026-11-22T07:00:00Z"),
                weightKg = 73.6,
            ),
        )

        val active = repository.observeActiveEntries().first()
        assertEquals(
            listOf(sameDayLaterCreate.id, sameDayEarlierCreate.id, older.id),
            active.map { it.id },
        )
    }

    @Test
    fun update_modifiesFields_preservesCreatedAt_advancesUpdatedAt_andTransitionsSyncedToPending() =
        runTest {
            val id = "00000000-0000-4000-8000-000000000301"
            val recordedAt = Instant.parse("2026-11-21T07:00:00Z")
            dao.upsert(
                BodyweightEntryEntity(
                    id = id,
                    recordedAt = recordedAt.toEpochMilli(),
                    weightKg = 75.0,
                    createdAt = initialInstant.toEpochMilli(),
                    updatedAt = initialInstant.toEpochMilli(),
                    deletedAt = null,
                    syncStatus = SyncStatus.SYNCED.name,
                ),
            )
            clock.advanceBy(Duration.ofMinutes(15))
            val expectedUpdatedAt = initialInstant.plus(Duration.ofMinutes(15))
            val newRecordedAt = Instant.parse("2026-11-20T07:00:00Z")

            val updated = repository.update(
                id = id,
                input = BodyweightEntryInput(
                    recordedAt = newRecordedAt,
                    weightKg = 74.4,
                ),
            )

            assertEquals(id, updated.id)
            assertEquals(newRecordedAt, updated.recordedAt)
            assertEquals(74.4, updated.weightKg, 1e-9)
            assertEquals(initialInstant, updated.createdAt)
            assertEquals(expectedUpdatedAt, updated.updatedAt)
            assertNull(updated.deletedAt)
            assertEquals(SyncStatus.PENDING, updated.syncStatus)
            assertEquals(updated, repository.getById(id))
        }

    @Test
    fun softDelete_setsTombstone_hidesFromActiveQueries_andTransitionsSyncedToPending() =
        runTest {
            val id = "00000000-0000-4000-8000-000000000302"
            dao.upsert(
                BodyweightEntryEntity(
                    id = id,
                    recordedAt = initialInstant.toEpochMilli(),
                    weightKg = 74.0,
                    createdAt = initialInstant.toEpochMilli(),
                    updatedAt = initialInstant.toEpochMilli(),
                    deletedAt = null,
                    syncStatus = SyncStatus.SYNCED.name,
                ),
            )
            clock.advanceBy(Duration.ofMinutes(20))
            val deletedAt = initialInstant.plus(Duration.ofMinutes(20))

            val tombstone = repository.softDelete(id)

            assertEquals(id, tombstone.id)
            assertEquals(initialInstant, tombstone.createdAt)
            assertEquals(deletedAt, tombstone.updatedAt)
            assertEquals(deletedAt, tombstone.deletedAt)
            assertEquals(SyncStatus.PENDING, tombstone.syncStatus)
            assertEquals(emptyList<Any>(), repository.observeActiveEntries().first())
            assertNull(repository.getById(id))
            assertEquals(tombstone, repository.getById(id, includeDeleted = true))
        }

    @Test(expected = NoSuchElementException::class)
    fun update_missingEntry_throwsNoSuchElementException() = runTest {
        repository.update(
            id = "missing-id",
            input = BodyweightEntryInput(recordedAt = initialInstant, weightKg = 74.0),
        )
    }

    @Test(expected = NoSuchElementException::class)
    fun update_softDeletedEntry_throwsNoSuchElementException() = runTest {
        val created = repository.create(
            BodyweightEntryInput(recordedAt = initialInstant, weightKg = 74.0),
        )
        repository.softDelete(created.id)

        repository.update(
            id = created.id,
            input = BodyweightEntryInput(recordedAt = initialInstant, weightKg = 73.5),
        )
    }

    @Test(expected = NoSuchElementException::class)
    fun softDelete_missingEntry_throwsNoSuchElementException() = runTest {
        repository.softDelete("missing-id")
    }

    @Test
    fun softDelete_alreadyDeletedEntry_isIdempotentAndPreservesDeletedAt() = runTest {
        val created = repository.create(
            BodyweightEntryInput(recordedAt = initialInstant, weightKg = 74.0),
        )
        clock.advanceBy(Duration.ofMinutes(5))
        val firstDelete = repository.softDelete(created.id)

        clock.advanceBy(Duration.ofMinutes(15))
        val secondDelete = repository.softDelete(created.id)

        assertEquals(firstDelete, secondDelete)
        assertEquals(initialInstant.plus(Duration.ofMinutes(5)), secondDelete.deletedAt)
        assertEquals(initialInstant.plus(Duration.ofMinutes(5)), secondDelete.updatedAt)
    }

    @Test
    fun create_truncatesSubMillisecondTimestamps_toMatchPersistedPrecision() = runTest {
        clock.setInstant(Instant.parse("2026-11-23T08:00:00.123456789Z"))

        val created = repository.create(
            BodyweightEntryInput(
                recordedAt = Instant.parse("2026-11-23T07:00:00.456789Z"),
                weightKg = 73.2,
            ),
        )

        assertEquals(Instant.parse("2026-11-23T07:00:00.456Z"), created.recordedAt)
        assertEquals(Instant.parse("2026-11-23T08:00:00.123Z"), created.createdAt)
        assertEquals(Instant.parse("2026-11-23T08:00:00.123Z"), created.updatedAt)
        assertEquals(created, repository.getById(created.id))
    }
}
