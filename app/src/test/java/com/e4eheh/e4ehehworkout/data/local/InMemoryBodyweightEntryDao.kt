package com.e4eheh.e4ehehworkout.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory storage seam mirroring BodyweightEntryDao semantics for fast JVM repository tests. */
class InMemoryBodyweightEntryDao : BodyweightEntryDao {
    private val records = MutableStateFlow<Map<String, BodyweightEntryEntity>>(emptyMap())

    override fun observeActive(): Flow<List<BodyweightEntryEntity>> =
        records.map { entries ->
            entries.values
                .filter { it.deletedAt == null }
                .sortedWith(
                    compareByDescending<BodyweightEntryEntity> { it.recordedAt }
                        .thenByDescending { it.createdAt },
                )
        }

    override suspend fun getById(id: String): BodyweightEntryEntity? = records.value[id]

    override suspend fun upsert(entity: BodyweightEntryEntity) {
        records.value = records.value + (entity.id to entity)
    }
}
