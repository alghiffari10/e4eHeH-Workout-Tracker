package com.e4eheh.e4ehehworkout.domain.repository

import com.e4eheh.e4ehehworkout.domain.model.Routine
import com.e4eheh.e4ehehworkout.domain.model.RoutineInput
import kotlinx.coroutines.flow.Flow

/** Persistence boundary for managing reusable Routines and their ordered Exercises. */
interface RoutineRepository {
    fun observeActiveRoutines(): Flow<List<Routine>>

    suspend fun getById(id: String, includeDeleted: Boolean = false): Routine?

    suspend fun create(input: RoutineInput): Routine

    suspend fun update(id: String, input: RoutineInput): Routine

    suspend fun softDelete(id: String): Routine
}
