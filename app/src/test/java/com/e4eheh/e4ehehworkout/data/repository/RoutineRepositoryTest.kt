package com.e4eheh.e4ehehworkout.data.repository

import com.e4eheh.e4ehehworkout.data.local.InMemoryRoutineDao
import com.e4eheh.e4ehehworkout.data.local.RoutineEntity
import com.e4eheh.e4ehehworkout.data.local.RoutineExerciseEntity
import com.e4eheh.e4ehehworkout.domain.model.Discipline
import com.e4eheh.e4ehehworkout.domain.model.RoutineExercise
import com.e4eheh.e4ehehworkout.domain.model.RoutineExerciseInput
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import com.e4eheh.e4ehehworkout.domain.model.SyncStatus
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

class RoutineRepositoryTest {
    private val initialInstant = Instant.parse("2026-11-23T08:00:00Z")
    private val clock = FakeClock(initialInstant)
    private val dao = InMemoryRoutineDao()
    private val repository = DefaultRoutineRepository(dao, clock)

    @Test
    fun create_generatesUuids_trimsName_preservesOrderAndTargetSets_andMarksPending() = runTest {
        clock.setInstant(Instant.parse("2026-11-23T08:00:00.123456789Z"))
        val expectedInstant = Instant.parse("2026-11-23T08:00:00.123Z")

        val created = repository.create(
            RoutineInput(
                name = "  Calisthenics Push  ",
                discipline = Discipline.CALISTHENICS,
                exercises = listOf(
                    RoutineExerciseInput(exerciseId = "ex-pushup", defaultTargetSets = 3),
                    RoutineExerciseInput(exerciseId = "ex-emom", defaultTargetSets = null),
                ),
            )
        )

        assertNotNull(UUID.fromString(created.id))
        assertEquals("Calisthenics Push", created.name)
        assertEquals(Discipline.CALISTHENICS, created.discipline)
        assertEquals(expectedInstant, created.createdAt)
        assertEquals(expectedInstant, created.updatedAt)
        assertNull(created.deletedAt)
        assertEquals(SyncStatus.PENDING, created.syncStatus)

        assertEquals(2, created.exercises.size)
        val first = created.exercises[0]
        val second = created.exercises[1]
        assertNotNull(UUID.fromString(first.id))
        assertNotNull(UUID.fromString(second.id))
        assertEquals(created.id, first.routineId)
        assertEquals("ex-pushup", first.exerciseId)
        assertEquals(0, first.sortOrder)
        assertEquals(3, first.defaultTargetSets)
        assertEquals(expectedInstant, first.createdAt)
        assertEquals(expectedInstant, first.updatedAt)
        assertNull(first.deletedAt)
        assertEquals(SyncStatus.PENDING, first.syncStatus)

        assertEquals(created.id, second.routineId)
        assertEquals("ex-emom", second.exerciseId)
        assertEquals(1, second.sortOrder)
        assertNull(second.defaultTargetSets)
        assertEquals(SyncStatus.PENDING, second.syncStatus)

        assertEquals(listOf(created), repository.observeActiveRoutines().first())
        assertEquals(created, repository.getById(created.id))
    }

    @Test
    fun create_allowsEmptyExerciseList() = runTest {
        val created = repository.create(
            RoutineInput(
                name = "Empty Routine",
                discipline = Discipline.CONDITIONING,
                exercises = emptyList(),
            )
        )

        assertEquals(emptyList<RoutineExercise>(), created.exercises)
        assertEquals(created, repository.getById(created.id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun create_rejectsBlankName() = runTest {
        repository.create(
            RoutineInput(
                name = "   \t  ",
                discipline = Discipline.CALISTHENICS,
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun create_rejectsTargetSetsBelowMinimum() = runTest {
        repository.create(
            RoutineInput(
                name = "Pull Day",
                discipline = Discipline.CALISTHENICS,
                exercises = listOf(RoutineExerciseInput(exerciseId = "ex-1", defaultTargetSets = 0)),
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun create_rejectsTargetSetsAboveMaximum() = runTest {
        repository.create(
            RoutineInput(
                name = "Pull Day",
                discipline = Discipline.CALISTHENICS,
                exercises = listOf(RoutineExerciseInput(exerciseId = "ex-1", defaultTargetSets = 100)),
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun create_rejectsBlankExerciseId() = runTest {
        repository.create(
            RoutineInput(
                name = "Pull Day",
                discipline = Discipline.CALISTHENICS,
                exercises = listOf(RoutineExerciseInput(exerciseId = "   ")),
            )
        )
    }

    @Test
    fun observeActiveRoutines_sortsCaseInsensitivelyByName_thenCreatedAt_andExercisesBySortOrder() =
        runTest {
            val secondCreated = repository.create(
                RoutineInput(
                    name = "beta flow",
                    discipline = Discipline.CARDIO,
                    exercises = listOf(
                        RoutineExerciseInput(exerciseId = "ex-b1"),
                        RoutineExerciseInput(exerciseId = "ex-b2"),
                    ),
                )
            )
            clock.advanceBy(Duration.ofMinutes(1))
            val firstAlpha = repository.create(
                RoutineInput(
                    name = "Alpha",
                    discipline = Discipline.CALISTHENICS,
                )
            )
            clock.advanceBy(Duration.ofMinutes(1))
            val secondAlpha = repository.create(
                RoutineInput(
                    name = "alpha",
                    discipline = Discipline.WEIGHT_LIFTING,
                )
            )

            val observed = repository.observeActiveRoutines().first()
            assertEquals(
                listOf(firstAlpha.id, secondAlpha.id, secondCreated.id),
                observed.map { it.id },
            )
            assertEquals(listOf("ex-b1", "ex-b2"), observed[2].exercises.map { it.exerciseId })
        }

    @Test
    fun update_reordersReconcilesTargetSets_addsAndSoftDeletesEntries_andTransitionsSyncedToPending() =
        runTest {
            val routineId = "00000000-0000-4000-8000-000000000301"
            val entry1Id = "00000000-0000-4000-8000-000000000311"
            val entry2Id = "00000000-0000-4000-8000-000000000312"
            val entry3Id = "00000000-0000-4000-8000-000000000313"
            val t0 = initialInstant.toEpochMilli()

            dao.upsertRoutineWithExercises(
                routine = RoutineEntity(
                    id = routineId,
                    name = "Push Day",
                    discipline = Discipline.CALISTHENICS.name,
                    createdAt = t0,
                    updatedAt = t0,
                    deletedAt = null,
                    syncStatus = SyncStatus.SYNCED.name,
                ),
                exercises = listOf(
                    RoutineExerciseEntity(
                        id = entry1Id,
                        routineId = routineId,
                        exerciseId = "ex-1",
                        sortOrder = 0,
                        defaultTargetSets = 3,
                        createdAt = t0,
                        updatedAt = t0,
                        deletedAt = null,
                        syncStatus = SyncStatus.SYNCED.name,
                    ),
                    RoutineExerciseEntity(
                        id = entry2Id,
                        routineId = routineId,
                        exerciseId = "ex-2",
                        sortOrder = 1,
                        defaultTargetSets = 4,
                        createdAt = t0,
                        updatedAt = t0,
                        deletedAt = null,
                        syncStatus = SyncStatus.SYNCED.name,
                    ),
                    RoutineExerciseEntity(
                        id = entry3Id,
                        routineId = routineId,
                        exerciseId = "ex-3",
                        sortOrder = 2,
                        defaultTargetSets = null,
                        createdAt = t0,
                        updatedAt = t0,
                        deletedAt = null,
                        syncStatus = SyncStatus.SYNCED.name,
                    ),
                ),
            )

            clock.advanceBy(Duration.ofMinutes(10))
            val t1 = clock.instant()

            // entry1 remains at index 0 unchanged -> stays SYNCED with original updatedAt
            // entry3 moves to index 1 and updates target sets -> PENDING at t1
            // entry2 is removed -> tombstoned at t1 with PENDING
            // new exercise ex-4 added at index 2 -> fresh UUID, PENDING at t1
            val updated = repository.update(
                id = routineId,
                input = RoutineInput(
                    name = "  Hybrid Push  ",
                    discipline = Discipline.CONDITIONING,
                    exercises = listOf(
                        RoutineExerciseInput(exerciseId = "ex-1", defaultTargetSets = 3, id = entry1Id),
                        RoutineExerciseInput(exerciseId = "ex-3", defaultTargetSets = 2, id = entry3Id),
                        RoutineExerciseInput(exerciseId = "ex-4", defaultTargetSets = 5, id = null),
                    ),
                ),
            )

            assertEquals(routineId, updated.id)
            assertEquals("Hybrid Push", updated.name)
            assertEquals(Discipline.CONDITIONING, updated.discipline)
            assertEquals(initialInstant, updated.createdAt)
            assertEquals(t1, updated.updatedAt)
            assertNull(updated.deletedAt)
            assertEquals(SyncStatus.PENDING, updated.syncStatus)

            assertEquals(3, updated.exercises.size)
            val keptUnchanged = updated.exercises[0]
            assertEquals(entry1Id, keptUnchanged.id)
            assertEquals(0, keptUnchanged.sortOrder)
            assertEquals(3, keptUnchanged.defaultTargetSets)
            assertEquals(initialInstant, keptUnchanged.createdAt)
            assertEquals(initialInstant, keptUnchanged.updatedAt)
            assertEquals(SyncStatus.SYNCED, keptUnchanged.syncStatus)

            val reorderedEdited = updated.exercises[1]
            assertEquals(entry3Id, reorderedEdited.id)
            assertEquals(1, reorderedEdited.sortOrder)
            assertEquals(2, reorderedEdited.defaultTargetSets)
            assertEquals(initialInstant, reorderedEdited.createdAt)
            assertEquals(t1, reorderedEdited.updatedAt)
            assertEquals(SyncStatus.PENDING, reorderedEdited.syncStatus)

            val added = updated.exercises[2]
            assertNotNull(UUID.fromString(added.id))
            assertEquals("ex-4", added.exerciseId)
            assertEquals(2, added.sortOrder)
            assertEquals(5, added.defaultTargetSets)
            assertEquals(t1, added.createdAt)
            assertEquals(t1, added.updatedAt)
            assertEquals(SyncStatus.PENDING, added.syncStatus)

            val withDeleted = repository.getById(routineId, includeDeleted = true)
            assertNotNull(withDeleted)
            val removedEntry = withDeleted!!.exercises.single { it.id == entry2Id }
            assertEquals(t1, removedEntry.deletedAt)
            assertEquals(t1, removedEntry.updatedAt)
            assertEquals(SyncStatus.PENDING, removedEntry.syncStatus)
            assertEquals(updated, repository.getById(routineId))
        }

    @Test(expected = NoSuchElementException::class)
    fun update_missingRoutine_throwsNoSuchElementException() = runTest {
        repository.update(
            id = "missing-routine",
            input = RoutineInput(name = "Test", discipline = Discipline.CARDIO),
        )
    }

    @Test(expected = NoSuchElementException::class)
    fun update_softDeletedRoutine_throwsNoSuchElementException() = runTest {
        val created = repository.create(
            RoutineInput(name = "Pull Day", discipline = Discipline.CALISTHENICS)
        )
        repository.softDelete(created.id)

        repository.update(
            id = created.id,
            input = RoutineInput(name = "Updated Pull", discipline = Discipline.CALISTHENICS),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun update_duplicateExerciseEntryId_throwsIllegalArgumentException() = runTest {
        val created = repository.create(
            RoutineInput(
                name = "Pull Day",
                discipline = Discipline.CALISTHENICS,
                exercises = listOf(RoutineExerciseInput(exerciseId = "ex-1")),
            )
        )
        val entryId = created.exercises.single().id

        repository.update(
            id = created.id,
            input = RoutineInput(
                name = "Pull Day",
                discipline = Discipline.CALISTHENICS,
                exercises = listOf(
                    RoutineExerciseInput(exerciseId = "ex-1", id = entryId),
                    RoutineExerciseInput(exerciseId = "ex-1", id = entryId),
                ),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun update_unknownExerciseEntryId_throwsIllegalArgumentException() = runTest {
        val created = repository.create(
            RoutineInput(name = "Pull Day", discipline = Discipline.CALISTHENICS)
        )

        repository.update(
            id = created.id,
            input = RoutineInput(
                name = "Pull Day",
                discipline = Discipline.CALISTHENICS,
                exercises = listOf(
                    RoutineExerciseInput(exerciseId = "ex-1", id = "unknown-entry-id")
                ),
            ),
        )
    }

    @Test
    fun softDelete_tombstonesSyncedRoutineAndActiveExercises_preservesPriorTombstones_andIsIdempotent() =
        runTest {
            val routineId = "00000000-0000-4000-8000-000000000401"
            val activeEntryId = "00000000-0000-4000-8000-000000000411"
            val priorTombstoneId = "00000000-0000-4000-8000-000000000412"
            val t0 = initialInstant.toEpochMilli()
            val priorDeleteInstant = initialInstant.plus(Duration.ofMinutes(5))

            dao.upsertRoutineWithExercises(
                routine = RoutineEntity(
                    id = routineId,
                    name = "Full Body",
                    discipline = Discipline.WEIGHT_LIFTING.name,
                    createdAt = t0,
                    updatedAt = t0,
                    deletedAt = null,
                    syncStatus = SyncStatus.SYNCED.name,
                ),
                exercises = listOf(
                    RoutineExerciseEntity(
                        id = activeEntryId,
                        routineId = routineId,
                        exerciseId = "ex-squat",
                        sortOrder = 0,
                        defaultTargetSets = 5,
                        createdAt = t0,
                        updatedAt = t0,
                        deletedAt = null,
                        syncStatus = SyncStatus.SYNCED.name,
                    ),
                    RoutineExerciseEntity(
                        id = priorTombstoneId,
                        routineId = routineId,
                        exerciseId = "ex-old",
                        sortOrder = 1,
                        defaultTargetSets = null,
                        createdAt = t0,
                        updatedAt = priorDeleteInstant.toEpochMilli(),
                        deletedAt = priorDeleteInstant.toEpochMilli(),
                        syncStatus = SyncStatus.SYNCED.name,
                    ),
                ),
            )

            clock.advanceBy(Duration.ofMinutes(30))
            val deleteInstant = clock.instant()

            val deleted = repository.softDelete(routineId)

            assertEquals(initialInstant, deleted.createdAt)
            assertEquals(deleteInstant, deleted.updatedAt)
            assertEquals(deleteInstant, deleted.deletedAt)
            assertEquals(SyncStatus.PENDING, deleted.syncStatus)

            val activeEntry = deleted.exercises.single { it.id == activeEntryId }
            assertEquals(deleteInstant, activeEntry.deletedAt)
            assertEquals(deleteInstant, activeEntry.updatedAt)
            assertEquals(SyncStatus.PENDING, activeEntry.syncStatus)

            val priorEntry = deleted.exercises.single { it.id == priorTombstoneId }
            assertEquals(priorDeleteInstant, priorEntry.deletedAt)
            assertEquals(priorDeleteInstant, priorEntry.updatedAt)
            assertEquals(SyncStatus.SYNCED, priorEntry.syncStatus)

            assertEquals(emptyList<Any>(), repository.observeActiveRoutines().first())
            assertNull(repository.getById(routineId))
            assertEquals(deleted, repository.getById(routineId, includeDeleted = true))

            clock.advanceBy(Duration.ofMinutes(15))
            val repeatDelete = repository.softDelete(routineId)
            assertEquals(deleted, repeatDelete)
        }

    @Test(expected = NoSuchElementException::class)
    fun softDelete_missingRoutine_throwsNoSuchElementException() = runTest {
        repository.softDelete("missing-routine")
    }
}
