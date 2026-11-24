package com.e4eheh.e4ehehworkout.data.local

import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory storage seam mirroring RoutineDao semantics for fast JVM repository tests. */
class InMemoryRoutineDao : RoutineDao {
    private data class StorageState(
        val routines: Map<String, RoutineEntity> = emptyMap(),
        val routineExercises: Map<String, RoutineExerciseEntity> = emptyMap(),
    )

    private val state = MutableStateFlow(StorageState())

    override fun observeActiveWithExercises(): Flow<List<RoutineWithExercises>> =
        state.map(::activeSorted)

    override suspend fun getActiveWithExercises(): List<RoutineWithExercises> = activeSorted(state.value)

    private fun activeSorted(current: StorageState): List<RoutineWithExercises> {
        val exercisesByRoutine = current.routineExercises.values.groupBy { it.routineId }
        return current.routines.values
            .filter { it.deletedAt == null }
            .sortedWith(
                compareBy<RoutineEntity>(
                    { it.name.lowercase(Locale.ROOT) },
                    { it.createdAt },
                ),
            )
            .map { routine ->
                RoutineWithExercises(
                    routine = routine,
                    exercises = exercisesByRoutine[routine.id].orEmpty(),
                )
            }
    }

    override suspend fun getWithExercisesById(id: String): RoutineWithExercises? {
        val current = state.value
        val routine = current.routines[id] ?: return null
        val exercises = current.routineExercises.values.filter { it.routineId == id }
        return RoutineWithExercises(routine = routine, exercises = exercises)
    }

    override suspend fun upsertRoutine(routine: RoutineEntity) {
        state.update { current ->
            current.copy(routines = current.routines + (routine.id to routine))
        }
    }

    override suspend fun upsertRoutineExercises(exercises: List<RoutineExerciseEntity>) {
        state.update { current ->
            val nextExercises = current.routineExercises.toMutableMap()
            exercises.forEach { entity ->
                nextExercises[entity.id] = entity
            }
            current.copy(routineExercises = nextExercises)
        }
    }

    override suspend fun upsertRoutineWithExercises(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
    ) {
        state.update { current ->
            val nextExercises = current.routineExercises.toMutableMap()
            exercises.forEach { entity ->
                nextExercises[entity.id] = entity
            }
            current.copy(
                routines = current.routines + (routine.id to routine),
                routineExercises = nextExercises,
            )
        }
    }
}
