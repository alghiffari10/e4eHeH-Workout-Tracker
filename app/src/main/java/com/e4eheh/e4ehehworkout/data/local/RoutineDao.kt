package com.e4eheh.e4ehehworkout.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Data access object for locally persisted Routine and RoutineExercise records. */
@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE ASC, createdAt ASC")
    fun observeActiveWithExercises(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    suspend fun getWithExercisesById(id: String): RoutineWithExercises?

    @Upsert
    suspend fun upsertRoutine(routine: RoutineEntity)

    @Upsert
    suspend fun upsertRoutineExercises(exercises: List<RoutineExerciseEntity>)

    @Transaction
    suspend fun upsertRoutineWithExercises(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
    ) {
        upsertRoutine(routine)
        if (exercises.isNotEmpty()) {
            upsertRoutineExercises(exercises)
        }
    }
}
