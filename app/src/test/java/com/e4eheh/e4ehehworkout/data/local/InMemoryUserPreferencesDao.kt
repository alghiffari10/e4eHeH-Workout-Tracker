package com.e4eheh.e4ehehworkout.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory storage seam mirroring UserPreferencesDao semantics for fast JVM repository tests. */
class InMemoryUserPreferencesDao : UserPreferencesDao {
    private val rows = MutableStateFlow<Map<String, UserPreferencesEntity>>(emptyMap())

    val storedEntity: UserPreferencesEntity?
        get() = rows.value[UserPreferencesEntity.SINGLETON_ID]

    override fun observe(id: String): Flow<UserPreferencesEntity?> =
        rows.map { it[id] }

    override suspend fun get(id: String): UserPreferencesEntity? =
        rows.value[id]

    override suspend fun upsert(entity: UserPreferencesEntity) {
        rows.value = rows.value + (entity.id to entity)
    }
}
