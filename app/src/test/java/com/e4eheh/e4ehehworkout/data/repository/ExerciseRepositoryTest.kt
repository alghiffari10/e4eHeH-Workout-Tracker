package com.e4eheh.e4ehehworkout.data.repository

import com.e4eheh.e4ehehworkout.data.local.ExerciseEntity
import com.e4eheh.e4ehehworkout.data.local.InMemoryExerciseDao
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
import com.e4eheh.e4ehehworkout.domain.model.TrackingMode
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

class ExerciseRepositoryTest {
    private val initialInstant = Instant.parse("2026-11-23T08:00:00Z")
    private val clock = FakeClock(initialInstant)
    private val dao = InMemoryExerciseDao()
    private val repository = DefaultExerciseRepository(dao, clock)

    @Test
    fun create_generatesUuid_trimsFields_setsTimestampsAndPendingSync() = runTest {
        val created = repository.create(
            ExerciseInput(
                name = "  Archer Push-up  ",
                discipline = Discipline.CALISTHENICS,
                trackingMode = TrackingMode.REPS_AND_LOAD,
                defaultNotes = "  Lock trailing elbow  ",
            )
        )

        assertNotNull(UUID.fromString(created.id))
        assertEquals("Archer Push-up", created.name)
        assertEquals(Discipline.CALISTHENICS, created.discipline)
        assertEquals(TrackingMode.REPS_AND_LOAD, created.trackingMode)
        assertEquals("Lock trailing elbow", created.defaultNotes)
        assertEquals(initialInstant, created.createdAt)
        assertEquals(initialInstant, created.updatedAt)
        assertNull(created.deletedAt)
        assertEquals(SyncStatus.PENDING, created.syncStatus)

        assertEquals(listOf(created), repository.observeActiveExercises().first())
        assertEquals(created, repository.getById(created.id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun create_rejectsBlankName() = runTest {
        repository.create(
            ExerciseInput(
                name = "   ",
                discipline = Discipline.CALISTHENICS,
                trackingMode = TrackingMode.REPS_AND_LOAD,
            )
        )
    }

    @Test
    fun create_normalizesBlankDefaultNotesToNull() = runTest {
        val created = repository.create(
            ExerciseInput(
                name = "Pull-up",
                discipline = Discipline.CALISTHENICS,
                trackingMode = TrackingMode.REPS_AND_LOAD,
                defaultNotes = "   \t  ",
            )
        )

        assertNull(created.defaultNotes)
        assertNull(repository.getById(created.id)?.defaultNotes)
    }

    @Test
    fun observeActiveExercises_supportsAllDisciplinesAndTrackingModes_sortedAlphabetically() = runTest {
        repository.create(
            ExerciseInput(
                name = "Squat",
                discipline = Discipline.WEIGHT_LIFTING,
                trackingMode = TrackingMode.REPS_AND_LOAD,
            )
        )
        repository.create(
            ExerciseInput(
                name = "5K Run",
                discipline = Discipline.CARDIO,
                trackingMode = TrackingMode.DISTANCE_AND_TIME,
            )
        )
        repository.create(
            ExerciseInput(
                name = "l-sit",
                discipline = Discipline.CALISTHENICS,
                trackingMode = TrackingMode.HOLD_DURATION,
            )
        )
        repository.create(
            ExerciseInput(
                name = "EMOM Circuit",
                discipline = Discipline.CONDITIONING,
                trackingMode = TrackingMode.CONDITIONING_ROUNDS,
            )
        )

        val active = repository.observeActiveExercises().first()

        assertEquals(
            listOf("5K Run", "EMOM Circuit", "l-sit", "Squat"),
            active.map { it.name },
        )
        assertEquals(
            listOf(
                Discipline.CARDIO,
                Discipline.CONDITIONING,
                Discipline.CALISTHENICS,
                Discipline.WEIGHT_LIFTING,
            ),
            active.map { it.discipline },
        )
        assertEquals(
            listOf(
                TrackingMode.DISTANCE_AND_TIME,
                TrackingMode.CONDITIONING_ROUNDS,
                TrackingMode.HOLD_DURATION,
                TrackingMode.REPS_AND_LOAD,
            ),
            active.map { it.trackingMode },
        )
    }

    @Test
    fun update_modifiesFields_preservesCreatedAt_advancesUpdatedAt_andTransitionsSyncedToPending() =
        runTest {
            val id = "00000000-0000-4000-8000-000000000101"
            dao.upsert(
                ExerciseEntity(
                    id = id,
                    name = "Push-up",
                    discipline = Discipline.CALISTHENICS.name,
                    trackingMode = TrackingMode.REPS_AND_LOAD.name,
                    defaultNotes = "Chest to floor",
                    createdAt = initialInstant.toEpochMilli(),
                    updatedAt = initialInstant.toEpochMilli(),
                    deletedAt = null,
                    syncStatus = SyncStatus.SYNCED.name,
                )
            )
            clock.advanceBy(Duration.ofMinutes(10))
            val expectedUpdatedAt = initialInstant.plus(Duration.ofMinutes(10))

            val updated = repository.update(
                id = id,
                input = ExerciseInput(
                    name = "  Ring Push-up  ",
                    discipline = Discipline.CONDITIONING,
                    trackingMode = TrackingMode.CONDITIONING_ROUNDS,
                    defaultNotes = "  EMOM finisher  ",
                ),
            )

            assertEquals(id, updated.id)
            assertEquals("Ring Push-up", updated.name)
            assertEquals(Discipline.CONDITIONING, updated.discipline)
            assertEquals(TrackingMode.CONDITIONING_ROUNDS, updated.trackingMode)
            assertEquals("EMOM finisher", updated.defaultNotes)
            assertEquals(initialInstant, updated.createdAt)
            assertEquals(expectedUpdatedAt, updated.updatedAt)
            assertNull(updated.deletedAt)
            assertEquals(SyncStatus.PENDING, updated.syncStatus)
            assertEquals(updated, repository.getById(id))
        }

    @Test
    fun softDelete_setsTombstone_hidesFromActiveQueries_andTransitionsSyncedToPending() =
        runTest {
            val id = "00000000-0000-4000-8000-000000000202"
            dao.upsert(
                ExerciseEntity(
                    id = id,
                    name = "Dead Hang",
                    discipline = Discipline.CALISTHENICS.name,
                    trackingMode = TrackingMode.HOLD_DURATION.name,
                    defaultNotes = null,
                    createdAt = initialInstant.toEpochMilli(),
                    updatedAt = initialInstant.toEpochMilli(),
                    deletedAt = null,
                    syncStatus = SyncStatus.SYNCED.name,
                )
            )
            clock.advanceBy(Duration.ofMinutes(30))
            val deletedAt = initialInstant.plus(Duration.ofMinutes(30))

            val tombstone = repository.softDelete(id)

            assertEquals(id, tombstone.id)
            assertEquals(initialInstant, tombstone.createdAt)
            assertEquals(deletedAt, tombstone.updatedAt)
            assertEquals(deletedAt, tombstone.deletedAt)
            assertEquals(SyncStatus.PENDING, tombstone.syncStatus)

            assertEquals(emptyList<Any>(), repository.observeActiveExercises().first())
            assertNull(repository.getById(id))
            assertEquals(tombstone, repository.getById(id, includeDeleted = true))
        }

    @Test(expected = NoSuchElementException::class)
    fun update_missingExercise_throwsNoSuchElementException() = runTest {
        repository.update(
            id = "missing-id",
            input = ExerciseInput(
                name = "Dips",
                discipline = Discipline.CALISTHENICS,
                trackingMode = TrackingMode.REPS_AND_LOAD,
            ),
        )
    }

    @Test(expected = NoSuchElementException::class)
    fun update_softDeletedExercise_throwsNoSuchElementException() = runTest {
        val created = repository.create(
            ExerciseInput(
                name = "Dips",
                discipline = Discipline.CALISTHENICS,
                trackingMode = TrackingMode.REPS_AND_LOAD,
            )
        )
        repository.softDelete(created.id)

        repository.update(
            id = created.id,
            input = ExerciseInput(
                name = "Weighted Dips",
                discipline = Discipline.CALISTHENICS,
                trackingMode = TrackingMode.REPS_AND_LOAD,
            ),
        )
    }

    @Test(expected = NoSuchElementException::class)
    fun softDelete_missingExercise_throwsNoSuchElementException() = runTest {
        repository.softDelete("missing-id")
    }

    @Test
    fun softDelete_alreadyDeletedExercise_isIdempotentAndPreservesDeletedAt() = runTest {
        val created = repository.create(
            ExerciseInput(
                name = "Plank",
                discipline = Discipline.CALISTHENICS,
                trackingMode = TrackingMode.HOLD_DURATION,
            )
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
            ExerciseInput(
                name = "Dips",
                discipline = Discipline.CALISTHENICS,
                trackingMode = TrackingMode.REPS_AND_LOAD,
            )
        )

        val expectedMillis = Instant.parse("2026-11-23T08:00:00.123Z")
        assertEquals(expectedMillis, created.createdAt)
        assertEquals(expectedMillis, created.updatedAt)
        assertEquals(created, repository.getById(created.id))
    }
}
