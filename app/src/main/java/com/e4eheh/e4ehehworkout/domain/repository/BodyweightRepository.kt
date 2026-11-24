package com.e4eheh.e4ehehworkout.domain.repository

import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntry
import com.e4eheh.e4ehehworkout.domain.model.BodyweightEntryInput
import kotlinx.coroutines.flow.Flow

/** Persistence boundary for standalone dated Bodyweight Entry records. */
interface BodyweightRepository {
    fun observeActiveEntries(): Flow<List<BodyweightEntry>>

    suspend fun getById(id: String, includeDeleted: Boolean = false): BodyweightEntry?

    suspend fun create(input: BodyweightEntryInput): BodyweightEntry

    suspend fun update(id: String, input: BodyweightEntryInput): BodyweightEntry

    suspend fun softDelete(id: String): BodyweightEntry
}
