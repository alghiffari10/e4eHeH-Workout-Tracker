package com.e4eheh.e4ehehworkout.domain.repository

import com.e4eheh.e4ehehworkout.domain.model.Exercise
import com.e4eheh.e4ehehworkout.domain.model.ExerciseInput
import kotlinx.coroutines.flow.Flow

/** Persistence boundary for managing the athlete's personal Exercise catalog. */
interface ExerciseRepository {
    fun observeActiveExercises(): Flow<List<Exercise>>

    suspend fun getById(id: String, includeDeleted: Boolean = false): Exercise?

    suspend fun create(input: ExerciseInput): Exercise

    suspend fun update(id: String, input: ExerciseInput): Exercise

    suspend fun softDelete(id: String): Exercise
}
