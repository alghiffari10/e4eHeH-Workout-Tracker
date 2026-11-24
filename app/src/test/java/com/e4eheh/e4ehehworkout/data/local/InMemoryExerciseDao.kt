package com.e4eheh.e4ehehworkout.data.local

import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory storage seam mirroring ExerciseDao semantics for fast JVM repository tests. */
class InMemoryExerciseDao : ExerciseDao {
    private val records = MutableStateFlow<Map<String, ExerciseEntity>>(emptyMap())

    override fun observeActive(): Flow<List<ExerciseEntity>> =
        records.map(::activeSorted)

    override suspend fun getActive(): List<ExerciseEntity> = activeSorted(records.value)

    private fun activeSorted(entries: Map<String, ExerciseEntity>): List<ExerciseEntity> =
        entries.values
            .filter { it.deletedAt == null }
            .sortedWith(
                compareBy<ExerciseEntity>(
                    { it.name.lowercase(Locale.ROOT) },
                    { it.createdAt },
                ),
            )

    override suspend fun getById(id: String): ExerciseEntity? = records.value[id]

    override suspend fun upsert(entity: ExerciseEntity) {
        records.value = records.value + (entity.id to entity)
    }
}
