package com.e4eheh.e4ehehworkout.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory storage seam mirroring UserPreferencesDao semantics for fast JVM repository tests. */
class InMemoryUserPreferencesDao : UserPreferencesDao {
    private val row = MutableStateFlow<UserPreferencesEntity?>(null)

    override fun observe(): Flow<UserPreferencesEntity?> = row

    override suspend fun get(): UserPreferencesEntity? = row.value

    override suspend fun upsert(entity: UserPreferencesEntity) {
        row.value = entity
    }
}
